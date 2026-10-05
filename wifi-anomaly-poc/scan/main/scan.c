#include <assert.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "cJSON.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_mac.h"
#include "esp_netif.h"
#include "esp_random.h"
#include "esp_wifi.h"
#include "mqtt_client.h"
#include "nvs_flash.h"

#define DEFAULT_SCAN_LIST_SIZE CONFIG_EXAMPLE_SCAN_LIST_SIZE
#define WIFI_MAX_RETRIES 5

static const char *TAG = "wifi_scan";

static esp_mqtt_client_handle_t mqtt_client = NULL;
static bool mqtt_started = false;
static unsigned int wifi_retry_count = 0;

/*
 * JSON jest tworzony zaraz po skanie, ale wysyłany dopiero po połączeniu MQTT.
 * Pozostaje w pamięci aż broker potwierdzi publikację QoS 1.
 */
static char *pending_scan_json = NULL;
static int pending_publish_id = -1;

static void create_scan_id(char output[33])
{
    const uint32_t part_1 = esp_random();
    const uint32_t part_2 = esp_random();
    const uint32_t part_3 = esp_random();
    const uint32_t part_4 = esp_random();

    snprintf(output, 33, "%08" PRIx32 "%08" PRIx32 "%08" PRIx32 "%08" PRIx32,
             part_1, part_2, part_3, part_4);
}

static char *build_scan_json(const wifi_ap_record_t *ap_info,
                             uint16_t records_stored,
                             uint16_t total_detected)
{
    char scan_id[33];
    create_scan_id(scan_id);

    cJSON *root = cJSON_CreateObject();
    cJSON *access_points = cJSON_CreateArray();

    if (root == NULL || access_points == NULL) {
        cJSON_Delete(root);
        cJSON_Delete(access_points);
        return NULL;
    }

    /* Najpierw przekazujemy własność tablicy do root. Od tego miejsca
     * pojedyncze cJSON_Delete(root) zwalnia całe budowane drzewo. */
    if (!cJSON_AddItemToObject(root, "accessPoints", access_points)) {
        cJSON_Delete(access_points);
        cJSON_Delete(root);
        return NULL;
    }

    if (cJSON_AddNumberToObject(root, "schemaVersion", 1) == NULL ||
        cJSON_AddStringToObject(root, "scanId", scan_id) == NULL ||
        cJSON_AddStringToObject(root, "sensorId", CONFIG_SENSOR_ID) == NULL ||
        cJSON_AddNumberToObject(root, "totalDetected", total_detected) == NULL ||
        cJSON_AddNumberToObject(root, "resultsStored", records_stored) == NULL) {
        cJSON_Delete(root);
        return NULL;
    }

    for (uint16_t i = 0; i < records_stored; ++i) {
        char bssid[18];
        snprintf(bssid, sizeof(bssid), MACSTR, MAC2STR(ap_info[i].bssid));

        cJSON *access_point = cJSON_CreateObject();
        if (access_point == NULL ||
            cJSON_AddStringToObject(access_point, "ssid",
                                    (const char *)ap_info[i].ssid) == NULL ||
            cJSON_AddStringToObject(access_point, "bssid", bssid) == NULL ||
            cJSON_AddNumberToObject(access_point, "rssi", ap_info[i].rssi) == NULL ||
            cJSON_AddNumberToObject(access_point, "channel", ap_info[i].primary) == NULL ||
            cJSON_AddNumberToObject(access_point, "authMode",
                                    (int)ap_info[i].authmode) == NULL ||
            !cJSON_AddItemToArray(access_points, access_point)) {
            cJSON_Delete(access_point);
            cJSON_Delete(root);
            return NULL;
        }
    }

    char *json = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    return json;
}

static void publish_pending_scan(esp_mqtt_client_handle_t client)
{
    if (pending_scan_json == NULL) {
        ESP_LOGW(TAG, "Brak przygotowanego skanu do wysłania");
        return;
    }

    if (pending_publish_id >= 0) {
        ESP_LOGI(TAG, "Skan oczekuje już na potwierdzenie, id: %d",
                 pending_publish_id);
        return;
    }

    const int message_id = esp_mqtt_client_publish(
        client,
        CONFIG_MQTT_SCAN_TOPIC,
        pending_scan_json,
        0,
        1,
        0);

    if (message_id < 0) {
        ESP_LOGE(TAG, "Nie udało się zlecić publikacji skanu");
        return;
    }

    pending_publish_id = message_id;
    ESP_LOGI(TAG, "Zlecono publikację skanu na %s, id: %d",
             CONFIG_MQTT_SCAN_TOPIC, message_id);
}

static void mqtt_event_handler(void *arg,
                               esp_event_base_t event_base,
                               int32_t event_id,
                               void *event_data)
{
    (void)arg;
    (void)event_base;

    esp_mqtt_event_handle_t event = event_data;

    switch ((esp_mqtt_event_id_t)event_id) {
    case MQTT_EVENT_CONNECTED:
        ESP_LOGI(TAG, "Połączono z brokerem MQTT");
        publish_pending_scan(event->client);
        break;

    case MQTT_EVENT_PUBLISHED:
        ESP_LOGI(TAG, "Broker potwierdził publikację, id: %d", event->msg_id);

        if (event->msg_id == pending_publish_id) {
            cJSON_free(pending_scan_json);
            pending_scan_json = NULL;
            pending_publish_id = -1;
            ESP_LOGI(TAG, "Skan dostarczony do brokera i usunięty z RAM");
        }
        break;

    case MQTT_EVENT_DISCONNECTED:
        ESP_LOGW(TAG, "Rozłączono z brokerem MQTT");
        break;

    case MQTT_EVENT_ERROR:
        ESP_LOGE(TAG, "Błąd klienta MQTT");
        break;

    default:
        break;
    }
}

static void mqtt_start(void)
{
    if (mqtt_started) {
        return;
    }

    const esp_mqtt_client_config_t mqtt_config = {
        .broker.address.uri = CONFIG_MQTT_BROKER_URI,
    };

    mqtt_client = esp_mqtt_client_init(&mqtt_config);
    if (mqtt_client == NULL) {
        ESP_LOGE(TAG, "Nie udało się utworzyć klienta MQTT");
        return;
    }

    ESP_ERROR_CHECK(esp_mqtt_client_register_event(
        mqtt_client, ESP_EVENT_ANY_ID, mqtt_event_handler, NULL));
    ESP_ERROR_CHECK(esp_mqtt_client_start(mqtt_client));
    mqtt_started = true;
}

static void wifi_event_handler(void *arg,
                               esp_event_base_t event_base,
                               int32_t event_id,
                               void *event_data)
{
    (void)arg;

    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        const wifi_event_sta_disconnected_t *event = event_data;
        ESP_LOGW(TAG, "Rozłączono z Wi-Fi, kod przyczyny: %u", event->reason);

        if (wifi_retry_count < WIFI_MAX_RETRIES) {
            ++wifi_retry_count;
            ESP_LOGI(TAG, "Ponowna próba połączenia: %u/%u",
                     wifi_retry_count, (unsigned int)WIFI_MAX_RETRIES);
            ESP_ERROR_CHECK(esp_wifi_connect());
        } else {
            ESP_LOGE(TAG, "Przekroczono limit prób połączenia z Wi-Fi");
        }
    } else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        const ip_event_got_ip_t *event = event_data;
        wifi_retry_count = 0;

        ESP_LOGI(TAG, "Połączono z Wi-Fi. Adres IP: " IPSTR,
                 IP2STR(&event->ip_info.ip));
        mqtt_start();
    }
}

static void wifi_scan_and_connect(void)
{
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    esp_netif_t *sta_netif = esp_netif_create_default_wifi_sta();
    assert(sta_netif != NULL);

    wifi_init_config_t wifi_init_config = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&wifi_init_config));

    ESP_ERROR_CHECK(esp_event_handler_register(
        WIFI_EVENT, WIFI_EVENT_STA_DISCONNECTED, wifi_event_handler, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(
        IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event_handler, NULL));

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_start());

    wifi_scan_config_t scan_config = {
        .show_hidden = true,
    };
    ESP_ERROR_CHECK(esp_wifi_scan_start(&scan_config, true));

    uint16_t total_detected = 0;
    uint16_t records_stored = DEFAULT_SCAN_LIST_SIZE;
    wifi_ap_record_t ap_info[DEFAULT_SCAN_LIST_SIZE];
    memset(ap_info, 0, sizeof(ap_info));

    ESP_ERROR_CHECK(esp_wifi_scan_get_ap_num(&total_detected));
    ESP_ERROR_CHECK(esp_wifi_scan_get_ap_records(&records_stored, ap_info));

    ESP_LOGI(TAG, "Wykryto BSS: %u, zapisano w wiadomości: %u",
             total_detected, records_stored);

    for (uint16_t i = 0; i < records_stored; ++i) {
        ESP_LOGI(TAG,
                 "SSID=%s BSSID=" MACSTR " RSSI=%d auth=%d channel=%u",
                 (const char *)ap_info[i].ssid,
                 MAC2STR(ap_info[i].bssid),
                 ap_info[i].rssi,
                 (int)ap_info[i].authmode,
                 ap_info[i].primary);
    }

    pending_scan_json = build_scan_json(ap_info, records_stored, total_detected);
    if (pending_scan_json == NULL) {
        ESP_LOGE(TAG, "Nie udało się zbudować JSON-a skanu");
        ESP_ERROR_CHECK(ESP_ERR_NO_MEM);
    }

    ESP_LOGI(TAG, "Przygotowany JSON: %s", pending_scan_json);

    wifi_config_t wifi_config = {
        .sta = {
            .ssid = CONFIG_WIFI_SSID,
            .password = CONFIG_WIFI_PASSWORD,
        },
    };

    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_LOGI(TAG, "Łączenie z siecią: %s", CONFIG_WIFI_SSID);
    ESP_ERROR_CHECK(esp_wifi_connect());
}

void app_main(void)
{
    esp_err_t result = nvs_flash_init();

    if (result == ESP_ERR_NVS_NO_FREE_PAGES ||
        result == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        result = nvs_flash_init();
    }

    ESP_ERROR_CHECK(result);
    wifi_scan_and_connect();
}
