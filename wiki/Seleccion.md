# Selección

Casi todo lo que hace el editor (rellenar, copiar, operaciones, Modify...) actúa sobre la **selección**. Hay selecciones en **caja** (cuboides) y de **máscara** (formas irregulares). Las operaciones respetan la forma de cualquiera de las dos.

## Box Select (B)

1. Elige *Box Select*.
2. **Botón derecho**: clic en un bloque y arrastra hasta otro. Se dibuja la caja.
3. Ajusta con los campos **Pos 1 / Pos 2** de la ventana *Selection*.

### Estirar y encoger

- **Manejadores:** con una selección en caja aparece un cubito de color en el centro de cada cara (rojo X, verde Y, azul Z). Arrástralo con el botón **izquierdo** para estirar o encoger esa cara; no puede cruzar a la opuesta (queda al menos 1 bloque).
- **Ventana Selection:** *Amount* + los botones **Up, Down, North, South, West, East** estiran esa cara (con *Shrink*, la encogen). *Full height* la lleva de Y 0 a 255.
- **Atajos:** RePág / AvPág estiran hacia arriba / abajo (con Shift, encogen).
- **Select > Expand / Shrink:** en todas direcciones (en selecciones irregulares, por las 6 caras).

## Magic Select (M)

**Botón derecho** sobre un bloque: selecciona por relleno los bloques conectados.

| Opción | Efecto |
|---|---|
| **Compare** | Qué cuenta como "igual": *Block, BlockState, Solid, Any* |
| **Limit** | Máximo de bloques |
| **Range** | Salta huecos de esa anchura |
| **Corners** | Incluye vecinos en diagonal |
| **Surface Only** | Solo la superficie |
| **Mode** | *Replace, Add, Subtract, Intersect* |

La selección resultante **no es una caja**; todas las operaciones respetan su forma.

## Lasso Select (L)

**Mantén el botón derecho** y dibuja un bucle en pantalla. Al soltar se lanzan rayos desde una rejilla dentro del contorno (hasta 30 000) y los bloques visibles pasan a la selección según el modo (*Replace / Add / Subtract / Intersect*).

- **Depth:** los extiende hacia dentro de la vista.
- **Include Non-Solid:** cuenta también hierba y flores.
- En lazos muy grandes, los bloques lejanos pueden quedar con huecos.

## Freehand Select (N)

Seleccionas **pintando** con un pincel (radio y forma). Los bloques bajo el pincel se suman a la selección en vivo. Modos *Replace / Add / Subtract / Intersect* y **Solid blocks only**.

## Cambiar la forma de la selección

*Selection > Shape* y *Select > Bounding Box* cambian la forma sin tocar bloques:

| Opción | Qué hace |
|---|---|
| **Bounding Box** | De máscara a caja |
| **Filter** | Se queda con un bloque, con o sin meta |
| **Smooth** | Desenfoque gaussiano de la forma (*StdDev*, *Threshold*): redondea esquinas y rellena agujeros interiores |
| **Distort** | Ruido (*Radius*, *Distance*, *Seed*) |
| **Move selection** | Mueve solo la selección, no los bloques |
| **Convex Hull** | Convierte una selección cóncava en todo lo que cae dentro de su casco convexo (una esfera de radio 40 tarda ~0,5 s). Si ya es convexa, avisa |

> Cambiar la forma **no se deshace con Ctrl+Z**: la selección no es una edición del mundo.

## Exportar la selección

*File > Export Selection as Sponge (.schem)*, *Export Selection as Structure NBT* y *Save Selection as CSV* (`x,y,z,bloque` de los bloques no aire) guardan la selección con su máscara en la carpeta de schematics (`selection-<fecha>`). No hace falta copiarla antes.

## Límites

- Una operación admite **hasta 500 000 bloques** y no toca chunks sin cargar.
- *Select > Clear* quita la selección.
- Los modos *Add / Subtract / Intersect* solo existen en las herramientas de selección por máscara.
