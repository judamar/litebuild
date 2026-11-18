# Operaciones

Se lanzan desde el menú **Operations** o desde la ventana **Selection** (*More operations*), siempre sobre la selección actual. Todas entran en el historial (Ctrl+Z), con la excepción indicada en *Trigger Updates*.

> Una operación admite hasta **500 000 bloques** y no toca chunks sin cargar.

## Rellenar y reemplazar

| Operación | Atajo | Qué hace |
|---|---|---|
| **Fill** | Ctrl+F | Rellena la selección con el Active Block |
| **Fill Outline / Walls / Shell / Top / Bottom** | | Rellena solo esa parte de la caja |
| **Replace** | Ctrl+R | Cambia un bloque por el Active Block (campos *Replace* y *Meta* de Selection; meta `-1` = cualquiera) |
| **Type Replace** | | Con un Active Block de la misma clase (p. ej. escaleras), sustituye las de la selección conservando orientación y mitad |
| **Delete** | Supr | Borra la selección (aire) |
| **Hollow** | | Vacía el interior y deja el casco |
| **Fill Nearest** | | Cada bloque de aire pasa a ser el bloque no aire más cercano de la selección |
| **Fill Gaps** | | Rellena con el Active Block el espacio no sólido al que no se llega desde fuera. Solo con selección en caja |
| **Waterlog** | | El aire de la selección pasa a agua (en 1.12 no hay bloques con agua dentro) |

## Física y fluidos

| Operación | Qué hace |
|---|---|
| **Simulate Gravity** | Los bloques de cada columna de la caja caen conservando su orden hasta apoyarse en el primer bloque sólido bajo la caja. Solo con selección en caja |
| **Drain** | Quita agua y lava |
| **Trigger Updates** | Avisa a cada bloque de que sus vecinos cambiaron; desaparecen los que no pueden sostenerse (carteles, antorchas flotantes), sin soltar objetos. Ignora fluidos y bloques con gravedad. Lo que se rompa en cadena **fuera** de la selección no entra en el historial |

## Superficie y estética

| Operación | Qué hace |
|---|---|
| **Smoothsnow** | Capas de nieve (*Max layers* 1-8) sobre la superficie de cada columna; la altura sigue una media del terreno en *Smooth radius*, así que huecos y escalones se rellenan con más capas. Solo ocupa aire y plantas bajas; repetirla no acumula |
| **Generate Colour Field** | Llena la caja con un degradado CIELAB (L en X, a de verde a rojo en Y, b de azul a amarillo en Z) usando el bloque completo y opaco de color medio más cercano |
| **Set Biome** | Cambia el bioma de todas las columnas de la selección (en 1.12 es por columna). Ctrl+Z lo deshace |
| **Analyze** | Cuenta los bloques de la selección |

## Autoshade

Sustituye los bloques de la **superficie** de la selección (cubos completos y opacos con alguna cara expuesta; el interior no se toca) por una paleta ordenada de oscuro a claro, según la luz que reciben.

- **From:** *Sun* (*azimuth* y *elevation*) o *Player position* (luz puntual con *Light radius*).
- La luz directa es el coseno entre la normal y la luz, con **sombras por rayo** (el terreno de alrededor también tapa).
- **Ambient:** luz base. **Ambient occlusion:** oscurece los rincones (cuenta los vecinos opacos de los 26, descontando el suelo plano).
- **Dither:** mezcla bloques vecinos de la paleta en las transiciones.
- **Palette:** *Automatic* (los bloques de la selección ordenados por luminosidad CIELAB, con filtros *Include* y *Exclude* por nombre) o una paleta de la ventana *Palette*, en su orden.
- **Lights:** *Sun* se puede apagar; *Lights* añade luces puntuales: *Player position* (una en tu posición) o *Custom positions* (varias, con coordenadas, intensidad y alcance).
- **Global illumination bounces** (0-4) con *Bounce strength*: cada bloque recoge luz de su entorno.
- Admite presets (sección *Presets* de Selection > More operations).

## Animated Rebuild

Vacía la selección (una edición deshacible) y la vuelve a construir **bloque a bloque del centro hacia fuera**.

| Opción | Efecto |
|---|---|
| *Start delay* | Espera inicial |
| *Air / Same / Different block delay* | Retardo según lo que haya en cada celda |
| *Random extra delay* | Aleatoriedad |
| *Max delay* | Tope |
| *Horizontal / Vertical* | Multiplicadores de la distancia (en ticks; 20 = 1 s) |

Muestra la duración estimada. *Cancel Animated Rebuild* o **Ctrl+Z** cancelan lo que quede por colocar (Undo devuelve el original; Redo solo vuelve a vaciar). Si hay **structure blocks** dentro de la selección, cada bloque empieza en el más cercano.

## Move y Stack

Ventana **Move / Stack** (con selección):

- **Move:** desplaza el contenido y la selección.
- **Stack:** repite *N* copias en una dirección con una separación (*Gap*).

Una sola edición deshacible.

## Operaciones en la paleta

La ventana **Palette** (*Window > Palette*) tiene **Fill selection (random)**: rellena la selección con bloques al azar de esa paleta. Detalles en [Pinceles de terreno](Pinceles.md#paletas).
