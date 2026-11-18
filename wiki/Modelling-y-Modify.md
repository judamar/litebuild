# Modelling y Modify

Dos herramientas para crear formas que no salen de un pincel.

## Modelling

Construyes **superficies a partir de puntos 3D** y las conviertes en bloques.

### Colocar puntos

- **Botón derecho** añade un punto a la **fila actual**. *Offset target point* lo coloca en el bloque contiguo a la cara apuntada.
- **New row** empieza una fila nueva; **Remove last** quita el último punto; **Clear** lo quita todo.
- La lista **Points** permite editar las coordenadas de cada punto.
- **E** duplica el último punto (es contextual: no choca con Elevation).

### Formas (Shape)

| Forma | Qué construye |
|---|---|
| **Loft** | Une filas contiguas punto a punto. *Surface*: **Flat**, **Catmull-Rom** (pasa por todos los puntos), **Bezier** (aproxima) o **Smart Surface** |
| **Triangle Strip** | Tira de triángulos con los puntos en orden |
| **Triangle Fan** | Abanico de triángulos desde el primer punto |
| **Convex Hull** | La superficie del casco convexo de todos los puntos |

### Smart Surface

Ignora las filas: proyecta todos los puntos en el plano de sus dos ejes más largos, los triangula con **Delaunay** y rellena cada triángulo en 3D. La superficie sigue la **altura de cada punto**. Hacen falta al menos **3 puntos que no estén en línea**; con menos, da un mensaje de error en vez de fallar.

### Build

**Build** voxeliza la superficie con el Active Block (muestreo cada 0,35 bloques, sin huecos) y la deja como **Placement** en su sitio, lista para confirmar con **Enter**. Desde ahí valen el [gizmo y todas las opciones de Placement](Portapapeles-y-Placement.md).

### Ejemplo: un tejado de tienda

1. Elige *Modelling*, *Shape* = Loft, *Surface* = Smart Surface.
2. Pon un punto en cada una de las cuatro esquinas del cuadrado de 10 × 10 y uno más alto en el centro.
3. *Build* y confirma con Enter: sale una superficie en forma de tienda, sin huecos.

## Modify

Transforma la **selección** con **Apply** (se deshace con Ctrl+Z).

| Modo | Qué hace |
|---|---|
| **Translate Copies** | *Count* copias con un desplazamiento (relativo al tamaño de la selección o absoluto) |
| **Rotate Copies** | *Count* copias giradas alrededor de un eje (X/Y/Z) que pasa por el pivote. *Equidistant* (360° / (Count+1)) o *Custom Angle*; *Add Translation* para hacer una espiral |
| **Revolve** | Enrolla la selección en un arco: su longitud es el ángulo y su profundidad el radio desde *Inner radius*. *Angle* hasta 360°, *Add Translation* para espiral, *Remove original* |
| **Twist** | Gira cada capa más que la anterior; *Angle* es el giro total de abajo a arriba |

- **Pivote:** el centro de la selección o un **bloque fijado con Ctrl + botón derecho** (caja amarilla).
- Todo se calcula por **muestreo inverso**, sin huecos.
- Con el eje Y, los bloques con orientación giran en cuartos de vuelta.
- No se copian los *tile entities*.
- Modify no tiene presets todavía.
