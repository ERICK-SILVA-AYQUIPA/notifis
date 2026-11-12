# Actualización y recuperación

## App Android

- Instala el nuevo APK sobre el anterior: los datos (historial, frases, equipo vinculado) se conservan.
- Las migraciones de la base de datos se versionan en `app/schemas/` y se agregan en
  `PagoboxDatabase.MIGRATIONS`; nunca se borra la base para migrar.
- Tras actualizar, abre **Diagnóstico** y confirma que el acceso a notificaciones sigue concedido.

## Firmware del equipo (OTA)

1. Compila o descarga `pagobox_device.bin` (artefacto `pagobox-firmware` de GitHub Actions) y cópialo al celular.
2. Con el equipo conectado por Bluetooth y al Wi-Fi, abre **Equipo → Actualizar firmware (.bin)** y elige el archivo.
3. La app envía la imagen por la red local usando el token entregado por el enlace BLE cifrado.
4. El equipo reinicia con la nueva versión y la confirma al arrancar. Si no arranca correctamente, el
   bootloader vuelve automáticamente a la versión anterior.

## Recuperación

| Problema | Solución |
|---|---|
| La app dice «Sin supervisión» | Diagnóstico → Acceso a notificaciones; desactiva y vuelve a activar PAGOBOX |
| El equipo no se reconecta | Equipo → Reconectar; si persiste, Desvincular y vincular de nuevo |
| El celular olvidó el vínculo | Vincula otra vez: el equipo reemplaza el vínculo anterior automáticamente |
| Wi-Fi cambió | Equipo → Wi-Fi y envía la nueva red |
| Equipo bloqueado | Mantén el interruptor apagado 10 s; el watchdog también lo reinicia si una tarea se cuelga |
| Restaurar de fábrica | `idf.py erase-flash` y volver a grabar el firmware (borra vínculos, Wi-Fi y volumen) |
