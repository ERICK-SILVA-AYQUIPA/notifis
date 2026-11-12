# Firmware PAGOBOX Device (ESP32-S3)

ESP-IDF 5.3 · FreeRTOS · NimBLE · LVGL 9 (vía `esp_lvgl_port`).

## Compilar y grabar

```bash
. $IDF_PATH/export.sh
cd firmware
idf.py set-target esp32s3
idf.py menuconfig      # PAGOBOX hardware: pines y tamaño de pantalla
idf.py build flash monitor
```

Pruebas de la lógica en el host (sin hardware):

```bash
cd firmware/test_host && cmake -B build -S . && cmake --build build && ./build/pagobox_host_tests
```

## Módulos

| Carpeta | Responsabilidad |
|---|---|
| `main/main.c` | Tareas de eventos e interfaz; flujo ACK → pantalla → audio → ACK de reproducción |
| `main/protocol.c` | Mensajes JSON, ACK, STATUS, control de secuencia, texto y formato de importes |
| `main/bluetooth/` | Servicio GATT con vinculación LE Secure Connections (código en pantalla) |
| `main/display/` | Estados de pantalla y UI LVGL en ST7789 |
| `main/audio/` | Salida I2S al MAX98357, volumen, tono de aviso y lectura de WAV |
| `main/net/` | Wi-Fi local, servidor HTTP para audio y OTA con token |
| `main/power/` | Batería (ADC calibrado), carga, botones y LED RGB |
| `main/storage/` | Configuración persistente en NVS |
| `main/diagnostics/` | Último error para pantalla y celular |

Conexiones y componentes: [docs/hardware.md](../docs/hardware.md).
