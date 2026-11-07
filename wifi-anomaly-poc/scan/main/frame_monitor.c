#include "sdkconfig.h"

#ifdef CONFIG_FRAME_MONITOR
#include <inttypes.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_mac.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "mqtt_client.h"

#include "frame_monitor.h"

#define WINDOW_MS 5000

static const char *TAG = "frame_monitor";
static portMUX_TYPE capture_lock = portMUX_INITIALIZER_UNLOCKED;
static _Atomic(esp_mqtt_client_handle_t) connected_client;
static uint8_t target_bssid[6];
static uint8_t channel;
static uint32_t connection_generation;

typedef struct {
    uint32_t beacon;
    uint32_t deauth;
    uint32_t disassoc;
    uint32_t retry;
} frame_counts_t;

static frame_counts_t counts;

void frame_monitor_set_channel(uint8_t connected_channel)
{
    portENTER_CRITICAL(&capture_lock);
    channel = connected_channel;
    ++connection_generation;
    counts = (frame_counts_t){0};
    portEXIT_CRITICAL(&capture_lock);
}

void frame_monitor_set_mqtt_client(esp_mqtt_client_handle_t client)
{
    atomic_store(&connected_client, client);
}

static void receive_frame(void *buffer, wifi_promiscuous_pkt_type_t type)
{
    if (type != WIFI_PKT_MGMT) {
        return;
    }

    const wifi_promiscuous_pkt_t *packet = buffer;
    const uint8_t *frame = packet->payload;
    if (packet->rx_ctrl.rx_state != 0 || packet->rx_ctrl.sig_len < 28
        || memcmp(&frame[16], target_bssid, sizeof(target_bssid)) != 0) {
        return;
    }

    const uint8_t subtype = frame[0] >> 4;
    if (subtype != 8 && subtype != 10 && subtype != 12) {
        return;
    }

    portENTER_CRITICAL(&capture_lock);
    if (channel != 0 && channel == packet->rx_ctrl.channel) {
        if (subtype == 8) {
            ++counts.beacon;
        } else {
            if (subtype == 10) {
                ++counts.disassoc;
            } else {
                ++counts.deauth;
            }
            if ((frame[1] & 0x08) != 0) {
                ++counts.retry;
            }
        }
    }
    portEXIT_CRITICAL(&capture_lock);
}

static void publish_window(esp_mqtt_client_handle_t client, frame_counts_t sample,
                           uint8_t sample_channel, int duration_ms)
{
    // Keep the same ID for all MQTT retransmissions of this measurement.
    uint32_t id[4];
    esp_fill_random(id, sizeof(id));

    char payload[512];
    int length = snprintf(payload, sizeof(payload),
        "{\"schemaVersion\":1,\"windowId\":\"%08" PRIx32 "%08" PRIx32 "%08" PRIx32 "%08" PRIx32
        "\",\"sensorId\":\"%s\",\"bssid\":\"" MACSTR "\",\"channel\":%u,"
        "\"durationMs\":%d,\"beacon\":%" PRIu32 ",\"deauth\":%" PRIu32
        ",\"disassoc\":%" PRIu32 ",\"retry\":%" PRIu32 "}",
        id[0], id[1], id[2], id[3], CONFIG_SENSOR_ID, MAC2STR(target_bssid),
        (unsigned int)sample_channel, duration_ms, sample.beacon, sample.deauth, sample.disassoc, sample.retry);

    if (length < 0 || (size_t)length >= sizeof(payload)) {
        ESP_LOGW(TAG, "Nie udało się zbudować komunikatu okna");
        return;
    }

    if (esp_mqtt_client_enqueue(client, CONFIG_MQTT_WINDOW_TOPIC, payload, length, 1, 0, true) < 0) {
        ESP_LOGW(TAG, "Nie udało się zakolejkować okna MQTT");
    } else {
        ESP_LOGI(TAG, "%s", payload);
    }
}

static void collect_windows(void *argument)
{
    (void)argument;
    uint32_t previous_generation = 0;
    int64_t start_us = esp_timer_get_time();

    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(WINDOW_MS));

        portENTER_CRITICAL(&capture_lock);
        int64_t now_us = esp_timer_get_time();
        uint32_t generation = connection_generation;
        uint8_t sample_channel = channel;
        frame_counts_t sample = counts;
        counts = (frame_counts_t){0};
        portEXIT_CRITICAL(&capture_lock);

        int duration_ms = (int)((now_us - start_us) / 1000);
        esp_mqtt_client_handle_t client = atomic_load(&connected_client);
        if (sample_channel != 0 && generation == previous_generation
            && duration_ms >= 4500 && duration_ms <= 5500 && client != NULL) {
            publish_window(client, sample, sample_channel, duration_ms);
        }

        previous_generation = generation;
        start_us = now_us;
    }
}

void frame_monitor_start(void)
{
    unsigned int octets[6];
    int end = 0;
    if (sscanf(CONFIG_MONITOR_BSSID, "%2x:%2x:%2x:%2x:%2x:%2x%n",
               &octets[0], &octets[1], &octets[2], &octets[3], &octets[4], &octets[5], &end) != 6
        || end != 17 || strlen(CONFIG_MONITOR_BSSID) != 17) {
        ESP_LOGE(TAG, "Podaj BSSID sieci testowej w menuconfig");
        ESP_ERROR_CHECK(ESP_ERR_INVALID_ARG);
    }

    for (size_t i = 0; i < sizeof(target_bssid); ++i) {
        target_bssid[i] = (uint8_t)octets[i];
    }

    for (const char *p = CONFIG_SENSOR_ID; *p != '\0'; ++p) {
        if (!((*p >= '0' && *p <= '9') || (*p >= 'A' && *p <= 'Z')
            || (*p >= 'a' && *p <= 'z') || *p == '_' || *p == '-')) {
            ESP_LOGE(TAG, "Identyfikator sensora może zawierać litery ASCII, cyfry, _ i -");
            ESP_ERROR_CHECK(ESP_ERR_INVALID_ARG);
        }
    }
    if (strlen(CONFIG_SENSOR_ID) == 0 || strlen(CONFIG_SENSOR_ID) > 64) {
        ESP_ERROR_CHECK(ESP_ERR_INVALID_ARG);
    }

    wifi_config_t config;
    ESP_ERROR_CHECK(esp_wifi_get_config(WIFI_IF_STA, &config));
    config.sta.bssid_set = true;
    memcpy(config.sta.bssid, target_bssid, sizeof(target_bssid));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &config));
    ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));

    const wifi_promiscuous_filter_t filter = {.filter_mask = WIFI_PROMIS_FILTER_MASK_MGMT};
    ESP_ERROR_CHECK(esp_wifi_set_promiscuous_filter(&filter));
    ESP_ERROR_CHECK(esp_wifi_set_promiscuous_rx_cb(receive_frame));
    ESP_ERROR_CHECK(esp_wifi_set_promiscuous(true));
    if (xTaskCreate(collect_windows, "frame_windows", 4096, NULL, 5, NULL) != pdPASS) {
        ESP_ERROR_CHECK(ESP_ERR_NO_MEM);
    }
}
#endif
