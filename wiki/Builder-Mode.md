# Builder Mode

Lo que se usa **en el mundo, sin abrir el editor** (solo en un mundo de un jugador). Mientras no actives nada, el juego se comporta como siempre.

## Abrir el menú de Builder

- Tecla **B**, o **tocar Alt** (pulsar y soltar sin rueda ni clics). La tecla se cambia en *Controles > LiteBuild > Builder menu*.
- Contiene: herramientas, *capabilities*, Toolbox, Edit Block Attributes, cambio de modo de juego, velocidad de vuelo, Hotbar Swapper, Colour Picker y Gradient Helper, y el selector de idioma.

> Lo implementan mixins sobre los clics del ratón, la rueda y el alcance (`MixinMinecraft`, `MixinInventoryPlayer`, `MixinPlayerControllerMP`). Si no se aplican, el juego sigue normal y simplemente no actúan.

## Builder Tools

**Move, Clone, Stack, Smear, Extrude, Erase** y **Setup Symmetry**. Se eligen en el menú o con **Alt + rueda** (la lista sale en pantalla). Comparten la selección del editor.

### Flujo común

1. **Clic izquierdo** = 1ª esquina; **clic derecho** = 2ª esquina. (El clic central selecciona un bloque.)
2. La **rueda empuja** el destino 1 bloque hacia donde miras. **X / Y / Z** fijan el eje.
3. El **clic central** lleva la esquina de la selección al bloque apuntado.
4. El **clic derecho confirma**.
5. **Ctrl+Z / Ctrl+Y** deshacen y rehacen (entran en el historial del editor).

Encima de la hotbar se ve la herramienta, el tamaño de la selección y el *offset*.

### Cada herramienta

| Herramienta | Comportamiento |
|---|---|
| **Move** | Mueve el contenido de la selección |
| **Clone** | Copia; se repite tras cada confirmación y el clic izquierdo la termina |
| **Stack** | Añade una copia por cada giro de rueda |
| **Smear** | Repite la selección de bloque en bloque a lo largo del vector y se corta al chocar con un sólido |
| **Extrude** | Sin selección: derecho extruye la cara y izquierdo la encoge |
| **Erase** | **Supr** o **Retroceso** borran el cuboide; el clic derecho sin selección borra los **128** bloques conectados más cercanos del mismo estado |
| **Setup Symmetry** | Derecho = nodo (se quita solo a más de 256 bloques); **Ctrl+F** voltea sobre el eje que miras; **Ctrl+R** rota; **Supr** lo quita |

*Keep Existing* y *Copy Air* valen para Move y Clone.

## Capabilities

Casillas en el menú de Builder (**en verde las activas**) y una tecla por cada una en *Controles > LiteBuild* (Replace Mode es **R** por defecto).

| Capability | Qué hace |
|---|---|
| **Tinker** | Clic derecho con la **mano vacía**: en muros, vallas y barras activa o apaga el lado que apuntas; en lo demás cicla la propiedad más relevante (abierto, orientación, eje, mitad, forma, encendido...). Con **Ctrl** convierte bloque → escalera → losa → bloque del mismo material |
| **No Updates** | Romper y colocar sin avisar a los vecinos |
| **Force Place** | Coloca sin comprobar soporte ni entidades |
| **Replace Mode** | El bloque apuntado pasa a ser el de la mano, heredando sus propiedades |
| **Bulldozer** | Con el botón izquierdo mantenido y tras un instante, rompe cada tick |
| **Infinite Reach** | Apuntar, romper y colocar hasta el *límite* (5-100). Más allá de 5,5 bloques se hace directamente en el mundo porque el servidor lo rechazaría |
| **Fast Place** | Coloca sin pausa |
| **Angel Placement** | Coloca en el aire a la distancia de la Toolbox, sin bloque de apoyo |
| **No Clip** | Atraviesas bloques mientras vuelas (ver [Jugador y mundo](Jugador-y-mundo.md#no-clip)) |

Replace Mode, Force Place y el resto de colocaciones propias **no entran en el historial**, como en Axiom.

## Toolbox

| Ajuste | Efecto |
|---|---|
| **Infinite reach limit** | Alcance máximo de Infinite Reach (5-100) |
| **Angel placement distance** | Distancia del Angel Placement |
| **Type replace** | Con Replace Mode, sustituye por variante: con tablones de abeto, las escaleras, losas, muros y vallas pasan a ser de abeto |
| **Flight direction** | *Horizontal* o *Camera* (al volar hacia delante, sube o baja según la cámara) |
| **Flight speed** | Velocidad de vuelo (%) |
| **Liquid opacity** | Opacidad del agua |
| **Minimum brightness** | Sube el brillo mínimo del juego |
| **Show key hints** | Muestra las teclas y botones pulsados, abajo a la derecha |

## Edit Block Attributes

| Ajuste | Efecto |
|---|---|
| **Show structure void blocks** | Hace visibles los structure void |
| **Show collision mesh** | Contorno **rojo** con la caja de colisión real de barreras, vallas, puertas de valla, muros, paneles de cristal y barras de hierro cercanos (brazos incluidos; cubo de 20 bloques alrededor del jugador) |
| **Prevent interactions** | Con un bloque en la mano, el derecho sobre un cofre o puerta **coloca** en vez de abrir |
| **Make fluid hitboxes solid** | Apuntas al agua y la lava como a cualquier bloque: se pueden romper con el puño y colocar bloques contra su superficie. Solo afecta al cliente: flechas, mobs y demás del servidor siguen pasando por el agua |
| **Expand hitboxes to full cube** | Se apunta a todos los bloques (menos el aire y, sin *Make fluid hitboxes solid*, los fluidos) como si fueran cubos completos (losas, botones, flores, antorchas), con un **contorno blanco** que muestra el cubo. La caja de colisión real no cambia; si hay una entidad más cerca, se apunta a la entidad |

*Show Light Blocks* no existe (los light blocks no existen en 1.12).

## Hotbar Swapper

*Save hotbar* guarda las **9 ranuras** con un nombre; cada hotbar guardada tiene **Load** y **X**. Sin límite y compartidas entre mundos (`config/litebuild/hotbars.dat`). En creativo se sincroniza con el servidor.

## Colour Picker

Ventana del menú de Builder y de *Window* en el editor.

- Campo **Hex**, sliders **RGB** y **HSB**.
- **Eye Dropper:** el color del bloque en la mano o del Active Block.
- Los **30 bloques más parecidos** en **OKLab**, con filtros **Full Cube**, **Solid**, **Opaque** y **Same Texture** (textura casi de un solo color).
- Clic = a la mano (Builder) o Active Block (editor).

## Gradient Helper

- 2 o más bloques de **entrada**, 2-9 de **salida** con degradado en OKLab.
- `<` y `>` eligen la mejor coincidencia anterior o siguiente de cada casilla.
- **Copy to Hotbar** y **Export to Editor** (lista de bloques del Gradient Painter).

## Lo que no hay

Marker Entities y Display Entities (son de 1.17 y 1.19.4 en adelante), Show Light Blocks y el slot 10 de la hotbar no existen en 1.12.
