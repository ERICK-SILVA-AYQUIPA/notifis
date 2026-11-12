idf_component_register(
    SRCS
        "main.c"
        "protocol.c"
        "audio/audio_out.c"
        "audio/wav.c"
        "bluetooth/ble_service.c"
        "display/display.c"
        "display/display_state.c"
        "net/wifi_audio.c"
        "power/power.c"
        "power/battery_math.c"
        "storage/settings_store.c"
        "diagnostics/diag.c"
    INCLUDE_DIRS "." "display"
    REQUIRES
        nvs_flash json esp_timer bt esp_wifi esp_netif esp_event esp_http_server
        app_update esp_adc esp_driver_i2s esp_driver_gpio esp_driver_spi esp_lcd esp_app_format
)
