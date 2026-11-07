#pragma once

#include <stdint.h>
#include "mqtt_client.h"

void frame_monitor_start(void);
void frame_monitor_set_channel(uint8_t channel);
void frame_monitor_set_mqtt_client(esp_mqtt_client_handle_t client);
