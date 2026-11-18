# Pinceles de terreno

Casi todas las herramientas que dibujan o remodelan funcionan igual: eliges la herramienta, ajustas sus opciones en **Tool Options** y **mantienes el botón derecho** sobre el mundo mientras arrastras.

- **Radius** (máx. 16 en los pinceles de terreno) y **Shape** (*Sphere, Cube, Octahedron*). **Ctrl + rueda** cambia el radio sin abrir la ventana.
- **Un trazo entero se deshace de una vez** con Ctrl+Z.
- Los pinceles respetan las [Tool Masks](Tool-Masks-y-Presets.md) (los que ahí se indican) y la **Symmetry** si está activada.
- Cada herramienta guarda sus ajustes como [presets](Tool-Masks-y-Presets.md#presets).
- Una operación admite hasta 500 000 bloques y no toca chunks sin cargar.

## Pintar

| Herramienta (tecla) | Qué hace | Opciones principales |
|---|---|---|
| **Painter** (P) | Cambia solo bloques **sólidos** por el Active Block | Radius, Shape, *Mask Surface* |
| **Noise Painter** (O) | Pinta con una **lista de bloques con peso**; los pesos se cumplen como porcentajes del volumen pintado | Lista con peso, *Scale, Octaves, Seed*, *3D*, *White noise* |
| **Freehand Draw** (G) | Rellena todo el volumen del pincel (incluido aire) | Radius, Shape |
| **Gradient Painter** | Reparte una lista de bloques (con pesos = tamaño de banda) a lo largo de una dirección (±X, ±Y, ±Z) o desde un punto (*Spherical*) | *Interpolation*: *Nearest* (bandas), *Linear* o *Bezier* (mezcla con ruido blanco); *Clamp*, *Length*. El origen es el primer bloque del trazo o uno fijo (**Ctrl + botón derecho**) |
| **Floodfill** | Botón derecho sobre una cara: rellena con el Active Block el hueco conectado (aire, agua, plantas...) en horizontal, hacia abajo y/o arriba según los permisos | *Limit* de bloques |
| **Extrude** (Z) | Botón derecho en una cara: la superficie de bloques iguales (según *Compare*) crece hacia fuera N capas; con *Shrink* o Shift se come capas | *Compare*, capas |

## Remodelar el terreno

| Herramienta (tecla) | Qué hace | Opciones principales |
|---|---|---|
| **Smooth** (U) | Desenfoque gaussiano del terreno dentro del pincel, conserva el número de bloques con *Block ratio* 100 % | Radius, Shape, *Strength* (1-4), *Block ratio* |
| **Melt** (K) | Como Smooth pero quita masa en salientes | Las mismas |
| **Weld** (J) | Rellena zonas cóncavas con el Active Block | Las mismas |
| **Roughen** | Lo contrario de Smooth: deja el terreno dentado conservando el número de bloques | Radius, Shape, *Strength, Roughness, Seed* |
| **Elevation** (E) | Sube (*Raise*), baja (*Lower*) o aplana (*Flatten*) el terreno por columnas en el círculo del pincel; el efecto es máximo en el centro y se acumula al arrastrar | *Mode*, *Amount*, **Heightmap** (imagen cuyo brillo fija cuánto cambia cada punto; *Clear* la quita) |
| **Distort** | Deforma el terreno con ruido: cada bloque pasa a ser el de una posición desplazada | *Scale, Distance, Seed* |
| **Shatter** | Abre grietas tipo Voronoi en los bloques sólidos, o las rellena con el Active Block (*Fill cracks*) | *Scale* (distancia entre grietas), *Width* (grosor) |
| **Sculpt Draw** | Levanta una protuberancia siguiendo la **normal de la superficie** (o siempre hacia arriba con *Mask Y*); *Invert* la hunde. No usa el Active Block: los bloques nuevos copian a sus vecinos | *Strength* (0,05-5, logarítmica), *Denoise* |
| **Slope** | 1) Botón derecho en un bloque fija el **objetivo** (caja magenta). 2) Mantén el botón derecho desde otro bloque y arrastra: el terreno se lleva a la recta inicio-objetivo. La barra de estado muestra diferencia de altura, ángulo y distancia | *Plane* (rampa) o *Cone* (pendiente radial), *Height*, *Sharpness*. Ctrl + clic fija otro objetivo; *Clear target* lo quita. No usa simetría |
| **Rock** (H) | Botón derecho coloca una roca irregular (esfera deformada por ruido) con el Active Block, medio hundida. Una edición por roca | Radius, *Noisiness, Noise scale, Seed* |

### Heightmap de Elevation

*Heightmap* acepta una ruta, una URL o una imagen de `config/litebuild/heightmaps` (la que viene de fuera se copia ahí). Las imágenes en color se pasan a blanco y negro.

## Caminos, copias y colocaciones

| Herramienta | Qué hace |
|---|---|
| **Path** | Botón derecho añade nodos; *Line* los une con rectas, *Catmull-Rom* con una curva que pasa por todos y *Bezier* con una que solo toca el primero y el último. *Radius* 0-8, *Looped*, *Keep Existing*. *Apply* coloca el camino con el Active Block |
| **Stamp** | Copia el portapapeles a lo largo de un trazo cada *Spacing* bloques, con *Scatter*, *Chance*, giro y volteo al azar, *Drop* y *Keep Existing*, apoyado en la superficie. Pensado para bosques y rocas. No copia tile entities |

## Text y Shape

- **Text:** escribe un texto con el Active Block usando una fuente de Java (*SansSerif, Serif, Monospaced, Dialog*; tamaño, negrita, cursiva). *Create Placement* lo coloca como un pegado; de pie se lee desde el sur (**Ctrl+R** lo gira) y con *Flat* queda tumbado.
- **Shape:** tipo (*Sphere, Cuboid, Cylinder, Cone, Pyramid, Octahedron, Disk*), tamaño X Y Z y *Hollow*. *Create Placement* genera la forma con el Active Block y se coloca como un pegado.

## Fluid Ball

Mantén el botón derecho para colocar una esfera de **Water**, **Lava** o **Snow** (capas sobre la superficie, más gruesas en el centro). Con agua o lava:

| Opción | Efecto |
|---|---|
| **Infinite Sources** | La región inicial es toda de fuentes. Desactivado, es una sola fuente en el centro |
| **Flow Length** (0-7) | Cuántos bloques se extiende el fluido en horizontal desde las fuentes, bajando un nivel por bloque (dos la lava), y cayendo por los huecos hasta que algo lo frena. 0 = solo las fuentes |
| **Require Support To Flow** | Solo fluye sobre bloques sólidos: sin cascadas |
| **Remove Fluids Without Sources** | Quita el agua o lava que fluye alrededor antes de colocar la nueva |
| **Fill Edges** | Rellena las esquinas sueltas de la orilla y, si el fluido llena un recipiente cerrado que contiene una fuente, lo deja plano (todo fuentes) |
| **Quality** (1-3) | 1 extiende en cuatro direcciones (el rombo de vanilla), 2 añade diagonales y 3 los saltos de caballo; el nivel sale de la distancia recorrida, así que la mancha sale más redonda |

El flujo se calcula de antemano con las reglas de vanilla y luego el juego lo sigue simulando, así que puede retocar los niveles. **Ctrl+Z** quita todo lo colocado.

## Biome Painter

Mantén el botón derecho para pintar un **bioma** (lista alfabética) con el radio del pincel.

- En 1.12 el bioma es **por columna**: siempre se pinta la columna entera (*Fill Vertically*). Cambia el color de hierba, hojas y agua; el servidor reenvía los chunks para que se repinten.
- Entra en el historial (Ctrl+Z lo deshace).
- **Visualize Biome:** pinta de colores los biomas del terreno de alrededor mientras la herramienta está activa. **View > Show Biomes** (**Ctrl+B**) lo deja siempre. El color es el bioma real de la columna.

## Symmetry

- **Botón derecho** coloca el **nodo** en el punto exacto que apuntas (cara, arista o vértice).
- Con **Enabled**, los pinceles también se aplican en las posiciones reflejadas (*Mirror X / Y / Z*) o giradas 180° o 90° alrededor del nodo.
- Se dibujan el nodo y los planos de espejo.

## Paletas

**Window > Palette** guarda los 16 bloques usados más recientemente y tus paletas propias (`config/litebuild/palettes.txt`).

- **Clic** = Active Block; **clic derecho** en un bloque de una paleta = quitarlo.
- Añadir bloques: *Add block...* (abre el selector; con **Ctrl** pulsado eliges varios seguidos), *Add Active Block*, o arrastrar desde *Recent*, desde otra paleta o desde el *Inventory*.
- **Fill selection (random)** rellena la selección con bloques al azar de esa paleta.

## Hotbars

El **Hotbar Swapper** del menú de Builder guarda las 9 ranuras con un nombre. Ver [Builder Mode](Builder-Mode.md#hotbar-swapper).
