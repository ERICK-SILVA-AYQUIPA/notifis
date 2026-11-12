# Limitaciones conocidas

1. **Ninguna entidad bancaria está habilitada todavía.** Las reglas de Yape, Plin y los bancos se escriben
   a partir de notificaciones reales recopiladas con autorización (fase 1, ver
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
