# Portapapeles y Placement

## Copiar

- **Ctrl+C** copia la selección; **Ctrl+X** la corta. La copia queda en la ventana **Clipboard**.
- **Edit > Copy With...** elige qué más copiar:
  - **Entities:** entidades (no jugadores), con su NBT. Se giran y espejan con el Placement.
  - **Air:** el aire también se guarda y, al pegar, sustituye.
  - **Entities and Air:** las dos.
- **Duplicate** (**Ctrl+J**): la selección como Placement, encima de la original.
- Se copian también los **tile entities** (cofres, carteles) y se restauran al deshacer. *Stamp* es la excepción: no los copia.

## Pegar con Placement

**Ctrl+V** abre un **Placement**: una caja naranja que sigue al cursor, con una **vista previa translúcida de los bloques**.

| Acción | Entrada |
|---|---|
| Fijar / seguir el cursor | Clic derecho |
| Mover (fijado) | Flechas, RePág / AvPág |
| Rotar | Ctrl+R |
| Voltear según hacia dónde mires | Ctrl+F |
| Pegar | Enter o Ctrl+V |
| Cancelar | Supr o Retroceso |

Botones del panel **Placement**: **Paste**, **Paste Copy** (pega y mantiene el Placement para más copias), **Paste and Select** (pega y deja como selección la caja pegada) y **Cancel**.

Otras casillas:

| Casilla | Efecto |
|---|---|
| **Keep Existing** | No sustituye los bloques que ya hay |
| **Paste Air** | El aire de la copia también se pega |
| **Paste Entities** | Pega las entidades copiadas |
| **Snap to ground** | Baja la copia hasta apoyarse; si está dentro del terreno, primero sube |
| **Merge Blocks** | Dos losas del mismo material en mitades opuestas forman la losa doble; las capas de **nieve** se suman (hasta 8) en vez de que gane el bloque nuevo |
| **Preview blocks** | Vista previa con bloques reales. Si falla, se desactiva sola y queda la caja |

## El gizmo

Con el Placement **fijado** (clic derecho) aparece el gizmo:

| Elemento | Acción (botón izquierdo) |
|---|---|
| **Flechas** de colores (X rojo, Y verde, Z azul) | Mueven la copia a lo largo de ese eje, de bloque en bloque |
| **Cubo blanco** central | La mueve libremente, manteniendo la distancia a la cámara |
| **Cuadraditos** de colores | La mueven en dos ejes a la vez (uno por plano) |
| **Tres anillos** | La giran: rojo = *pitch*, verde = *yaw*, azul = *roll*. Con **Ctrl** salta de 15° en 15° |
| **Cubitos claros** (junto a las flechas positivas) | La escalan en ese eje; con **Ctrl**, uniforme. Entre 0,1 y 8 |

Al girar o escalar se conserva el centro.

**Local gizmo** (casilla del panel Placement): *Global* (por defecto) usa los ejes del mundo; *Local* alinea flechas, planos y anillos con los ejes propios de la copia ya girada. En ambos casos, un anillo compone su giro con el actual y el resultado se vuelve a pasar a Yaw/Pitch/Roll, que puede salir con otros valores equivalentes (el yaw queda entre -90° y 90°). Los cubitos de escala siguen siempre los ejes propios.

## Transformación libre

En el panel Placement:

- **Yaw / Pitch / Roll** (cualquier ángulo) y **Scale** por eje (0,1 a 8).
- **Scale x2 / x3:** cada bloque pasa a ser un cubo mayor.
- **Smooth x2 / x3:** la versión 3D de Scale2x / Scale3x. Los escalones se alisan y las esquinas se redondean (las aristas exteriores quedan achaflanadas; Scale3x deja intactos los centros de las caras).
- **RotSprite:** al girar con ángulos libres, amplía x2 con ese alisado, gira con el vecino más cercano y reduce a la mitad (si lo ampliado pasa del tope de bloques, gira como siempre). Mientras arrastras un anillo no se aplica, solo al soltar.
- **Reset transform:** vuelve a la forma original.

> Sin RotSprite se usa el bloque más cercano, así que giros que no son de 90° dejan huecos en piezas finas. La orientación de escaleras o troncos solo sigue el yaw en cuartos de vuelta.

## Vista previa con bloques

La copia se dibuja con sus bloques reales, translúcidos, con el renderizador de vanilla (luz máxima, bioma llanura). Mover o fijar la copia no cuesta nada; girar, escalar o cambiar la forma la vuelve a renderizar.

- Hasta **20 000 bloques**; con más solo se ve la caja.
- Los *tile entities* (cofres, carteles) no se dibujan.
- Mientras arrastras un anillo o un cubito de escala solo se ve la caja, para ir fluido.

## Schematics

**File > Schematics...** (o *Window > Schematics*):

- **Export clipboard:** guarda el portapapeles en `config/litebuild/schematics` en uno de tres formatos:
  - **Schematic** (`.schematic`, MCEdit / WorldEdit clásico)
  - **Sponge** (`.schem`, v2; se leen también v1 y v3)
  - **Structure NBT** (`.nbt`, el de los structure blocks)
- **Import:** detecta el formato por la extensión y lo carga como Placement. Los bloques que no existen en 1.12 (de versiones nuevas) entran como aire y se avisa de cuántos.

## Blueprints

Un *blueprint* es un `.schem` con metadatos y miniatura.

- **Guardar:** *File > Save Blueprint...* o **Ctrl+P** (también en la ventana Clipboard). Pide nombre, autores, carpeta y etiquetas (24 por defecto; se pueden añadir más). Se guarda en `config/litebuild/blueprints` con esos datos en el compuesto *Metadata*, y se abre también en WorldEdit. No es el formato `.bp` de Axiom, que es cerrado.
- **Navegar:** *File > Blueprint Browser...* lee las miniaturas en segundo plano y ofrece búsqueda por nombre o autor, filtro por etiqueta, subcarpetas y *Search inside folders*.
- **Clic** = cargar en el Clipboard; **clic derecho** = cargar y colocar. *Rotate thumbnail* gira la miniatura isométrica.

## Texto y formas como Placement

Las herramientas **Text** y **Shape** generan su resultado con *Create Placement*: se coloca como un pegado (y admite todo lo anterior). Ver [Pinceles de terreno](Pinceles.md#text-y-shape).
