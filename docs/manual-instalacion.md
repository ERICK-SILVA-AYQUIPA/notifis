# Manual de instalación

## 1. App Android

**Requisitos:** Android 8.0 o superior y una voz en español instalada (Ajustes → Sistema → Idioma → Texto a voz).

1. Descarga el APK de pruebas desde GitHub: pestaña **Actions** → última ejecución de **CI** → artefacto
   `pagobox-debug-apk`.
2. Instálalo en el celular (permite «Instalar apps desconocidas» para el navegador o gestor de archivos).
3. Abre PAGOBOX y sigue la **Configuración inicial**:
   - Concede el **acceso a notificaciones** (obligatorio).
   - Permite que funcione **sin restricción de batería** (recomendado; en Xiaomi, Huawei, Oppo y Samsung revisa
     además el «inicio automático» y el «ahorro de batería» del fabricante).
   - Concede **Bluetooth** si usarás el equipo PAGOBOX.

### Compilar la app

```bash
git clone https://github.com/ERICK-SILVA-AYQUIPA/notifis.git
cd notifis
./gradlew test                 # motor de pagos y pruebas
./gradlew :app:assembleDebug   # requiere Android SDK (Android Studio)
```

## 2. Equipo PAGOBOX (firmware)

**Requisitos:** ESP-IDF 5.3 y la placa armada según [hardware.md](hardware.md).

```bash
. $IDF_PATH/export.sh
cd firmware
idf.py set-target esp32s3
idf.py menuconfig      # opcional: PAGOBOX hardware → pines y tamaño de pantalla
idf.py build flash monitor
```

También puedes grabar los binarios compilados por GitHub (artefacto `pagobox-firmware`):

```bash
esptool.py --chip esp32s3 write_flash 0x0 bootloader.bin 0x8000 partition-table.bin \
  0xf000 ota_data_initial.bin 0x20000 pagobox_device.bin
```

## 3. Vincular el equipo

1. Enciende el equipo: la pantalla muestra «Sin conexión» y el LED se pone amarillo.
2. En la app: **Equipo → Buscar equipos → Vincular**.
3. El equipo muestra un **código de 6 dígitos**; escríbelo en el diálogo de Android.
4. El LED se pone verde. En **Equipo → Wi-Fi** ingresa la red del local para que el equipo reproduzca la voz.
   Sin Wi-Fi, el equipo anuncia con un tono y muestra el importe.
5. En **Ajustes → Dónde se anuncian los pagos** elige «Solo en el equipo» o «Celular y equipo».
