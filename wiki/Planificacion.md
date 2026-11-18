# Planificación

Herramientas para medir, anotar y guardar posiciones. Ninguna cambia bloques.

## Ruler

Botón derecho añade puntos. En *Tool Options*:

- Distancia **euclídea** y **Manhattan**.
- Tamaño de la caja que contiene los puntos.
- *Yaw* y *pitch* entre dos puntos.
- **Select bounds:** convierte los puntos en una selección.

*Select > Clear Ruler* los quita; *View > Show Ruler* los dibuja.

## Annotation

Dibuja sobre el terreno para planificar. Elige el **Mode**:

| Modo | Qué hace |
|---|---|
| **Draw** | Línea pegada a la superficie: botón derecho arrastrando; *Width* |
| **Outline** | *Freehand*: resalta los bloques por los que pasas. *Box*: entre dos bloques resalta todos los que no son aire (hasta 5000) |
| **Text** | Texto flotante con *Shadow*; se ve con el editor abierto y se edita, se visita o se borra en *Window > Text Annotations* |
| **Erase** | Borra lo que queda dentro de *Radius* |
| **Image** | Pega una imagen por URL http/https o ruta de archivo, con *Width*, de cara a la superficie. Si no carga se ve una textura rosa y negra |
| **Move** | Arrastra el texto o la imagen más cercana (a menos de 4 bloques) a otra superficie |

- **Color** con selector.
- Tiene su **propio deshacer**: con la herramienta activa, Ctrl+Z y Ctrl+Y actúan sobre las anotaciones (Draw, Outline, Text, Erase, Image y Move).
- Se guardan **por mundo** en `saves/<mundo>/litebuild-annotations.txt`.
- *View > Show Annotations* las oculta; **Clear ALL Annotations** las borra sin posibilidad de deshacer.

## Reference Image

*File > Open Reference Image...* (o *Window > Reference Image*): ventana flotante con una imagen por **URL** o **ruta de archivo** (la carga va en segundo plano). **Clic derecho** en la ventana:

- *Close Window*
- *Filtering: Nearest* (píxeles nítidos)
- **Show In Game UI:** la imagen sigue sobre el juego, en el mismo sitio y tamaño, con el editor **cerrado**
- **Borderless** y *Opacity*

## Views

Ventana **Views**: guarda la **posición y la mirada** actuales con un nombre y vuelve a ella con **Go** (misma dimensión). Se guardan en `config/litebuild/views.properties`.

## Target Info

Muestra el bloque, el bioma y la distancia bajo el cursor. Útil para saber qué id y meta tiene un bloque antes de escribirlo en un campo.
