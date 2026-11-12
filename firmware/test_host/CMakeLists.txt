# Pruebas de la lógica pura del firmware ejecutadas en el host (sin hardware).
cmake_minimum_required(VERSION 3.16)
project(pagobox_host_tests C)

set(CJSON_DIR "$ENV{IDF_PATH}/components/json/cJSON")
add_executable(pagobox_host_tests
    test_main.c
    ../main/protocol.c
    ../main/display_state.c
    ${CJSON_DIR}/cJSON.c
)
target_include_directories(pagobox_host_tests PRIVATE ../main ${CJSON_DIR})
target_compile_options(pagobox_host_tests PRIVATE -Wall -Wextra -Werror)
target_link_libraries(pagobox_host_tests m)
