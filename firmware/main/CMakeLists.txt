idf_component_register(
    SRCS "main.c" "protocol.c" "display_state.c"
    INCLUDE_DIRS "."
    REQUIRES nvs_flash json esp_timer
)
