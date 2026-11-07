idf_component_register(
    SRCS "scan.c" "frame_monitor.c"
    PRIV_REQUIRES esp_wifi nvs_flash esp_netif esp_event esp_hw_support esp_timer espressif__mqtt espressif__cjson
    INCLUDE_DIRS "."
)
