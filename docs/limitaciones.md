# Limitaciones conocidas

1. **Ninguna entidad bancaria anuncia pagos todavía.** Las 7 entidades tienen adaptador propio, pero están en
   «Esperando muestras»: sus formatos y paquetes se escribirán con notificaciones reales anonimizadas (ver
   [compatibilidad-bancaria.md](compatibilidad-bancaria.md)). Hasta entonces la app muestra «Sin supervisión».
2. **Los pagos detectados no están verificados.** Una notificación puede ser falsificada por otra app con acceso
   a notificaciones o por una captura mostrada al cajero. La verificación oficial requiere integraciones (API o
   webhooks) que dependen de cada entidad y del convenio del comercio.
3. **Android puede detener el lector.** Algunos fabricantes cierran apps en segundo plano. La app lo detecta y lo
   informa, pero no puede impedirlo si el usuario fuerza la detención o revoca el permiso.
4. **Voz:** las voces masculina y femenina dependen del motor TTS instalado. Si la voz requiere Internet, la app
   lo indica; se recomienda instalar los datos de voz sin conexión.
5. **Audio en el equipo:** la voz se transmite por Wi-Fi local. Sin Wi-Fi, el equipo reproduce un tono de aviso
   y muestra el importe; no tiene todavía un paquete de frases pregrabadas.
6. **Pantalla del equipo:** la fuente no incluye tildes; los nombres se muestran sin acentos («Jose Nunez»).
7. **Hora en el equipo:** se toma del evento enviado por el celular; el equipo no tiene reloj propio.
8. **Hardware:** el firmware compila y sus partes lógicas tienen pruebas automáticas, pero el prototipo físico
   (pantalla, audio, batería, BLE + Wi-Fi simultáneos) debe validarse en banco antes de fabricar (sección 15).
9. **Un equipo por celular** en esta versión.
10. **Paquetes asignados desde el celular:** la app confía en la app asignada solo mientras su firma coincida con
    la registrada. Una app no oficial con la misma firma no es posible, pero el usuario podría asignar por error
    una app equivocada; por eso se muestra el instalador y la asignación no activa anuncios por sí sola.
11. **Otra app con acceso a notificaciones** puede leer las mismas notificaciones; PAGOBOX no puede impedirlo.

## Problemas pendientes

| # | Pendiente | Bloqueado por | Prioridad |
|---|---|---|---|
| P1 | Formatos y paquetes oficiales de Yape, Plin, BCP, Interbank, BBVA, Scotiabank y Banco de la Nación | Muestras reales anonimizadas (diagnóstico de notificaciones) | Crítica |
| P2 | Ejecutar el protocolo de pruebas físicas Android (AND-01 a AND-13) en la matriz mínima de celulares | Celulares físicos | Crítica |
| P3 | Validar el prototipo de hardware (HW-01 a HW-08, BLE-01 a BLE-03) y medir latencias y autonomía | Prototipo armado | Crítica |
| P4 | Comprobar la apertura de la base de datos al actualizar desde la versión 1 en un celular real | Celular con la versión anterior instalada | Alta |
| P5 | Firma de la app para distribución (keystore de producción) | Decisión del comercio | Alta |
| P6 | Frases pregrabadas en el equipo para anunciar sin Wi-Fi | Diseño de audio | Media |
| P7 | Fuente con tildes en la pantalla del equipo | Generar fuente LVGL | Baja |
| P8 | Verificación oficial de pagos (API/webhook por entidad) | Convenios con las entidades | Futuro |
