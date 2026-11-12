# Hardware del equipo PAGOBOX

## Lista de componentes (prototipo)

| # | Componente | Especificación sugerida | Cant. |
|---|---|---|---|
| 1 | Microcontrolador | Módulo ESP32-S3-WROOM-1 N8R8 (8 MB flash, 8 MB PSRAM) o placa DevKitC-1 | 1 |
| 2 | Pantalla | TFT IPS 1.54" 240×240, controlador ST7789, SPI | 1 |
| 3 | Amplificador | MAX98357A, I2S, clase D, 3 W | 1 |
| 4 | Parlante | 4 Ω, 3 W, 40 mm (según diseño acústico de la carcasa) | 1 |
| 5 | Batería | Li-ion 18650 o LiPo 2000 mAh con protección (PCM) | 1 |
| 6 | Cargador | TP4056 con protección, o IP5306 (carga + boost), entrada USB-C | 1 |
| 7 | Regulador | LDO 3.3 V ≥ 600 mA de baja caída (p. ej. ME6211, AP2112K) | 1 |
| 8 | Divisor de tensión | 2 × 100 kΩ 1 % (medición de batería, relación 1:2) | 1 |
| 9 | LED RGB | Cátodo común 5 mm + 3 resistencias 330 Ω | 1 |
| 10 | Botones | Pulsador táctil 6×6 mm (volumen +, volumen −, silencio) | 3 |
| 11 | Conector | USB-C hembra 16 pines (alimentación y programación) | 1 |
| 12 | Interruptor | Deslizante de encendido | 1 |
| 13 | Carcasa | Impresión 3D / inyección con rejilla para el parlante | 1 |

## Conexiones (valores por defecto de `idf.py menuconfig` → *PAGOBOX hardware*)

| Señal | GPIO ESP32-S3 | Destino |
|---|---|---|
| LCD SCLK | 12 | ST7789 SCL |
| LCD MOSI | 11 | ST7789 SDA |
| LCD CS | 10 | ST7789 CS |
| LCD DC | 9 | ST7789 DC |
| LCD RST | 8 | ST7789 RES |
| LCD retroiluminación | 7 | ST7789 BLK |
| I2S BCLK | 4 | MAX98357 BCLK |
| I2S WS | 5 | MAX98357 LRC |
| I2S DOUT | 6 | MAX98357 DIN |
| Amplificador SD | 15 | MAX98357 SD (alto = encendido) |
| Batería (ADC) | 1 | Punto medio del divisor 100k/100k entre B+ y GND |
| Estado de carga | 2 | Salida CHRG del cargador (activa en bajo) |
| Botón volumen + | 16 | a GND (pull-up interno) |
| Botón volumen − | 17 | a GND (pull-up interno) |
| Botón silencio | 18 | a GND (pull-up interno) |
| LED R / G / B | 38 / 39 / 40 | LED RGB con resistencias de 330 Ω |

Alimentación: batería → cargador/boost → LDO 3.3 V → ESP32-S3, pantalla y lógica.
El MAX98357 se alimenta de la línea de 5 V (boost) o directamente de la batería para mayor potencia.

## Comportamiento de botones y LED

| Acción | Resultado |
|---|---|
| Volumen + / − | Sube o baja 10 % y lo guarda |
| Silencio (pulsación corta) | Silencia o reactiva los anuncios (el pago se sigue mostrando) |
| Silencio (mantener 1,5 s) | Vuelve a mostrar el último pago, sin volver a anunciarlo |

| LED | Significado |
|---|---|
| Verde | Conectado con el celular |
| Cian | Mostrando un pago nuevo |
| Amarillo | Esperando conexión con el celular |
| Azul | Vinculación en curso (mira el código en pantalla) |
| Rojo | Error |

> El esquema electrónico definitivo y el diseño de la carcasa se cierran después de validar el prototipo
> (sección 15 de la especificación). Esta tabla es la referencia para armar el prototipo.
