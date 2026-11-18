# Script Brush y Lua

El **Script Brush** es un pincel programable en **Lua** (intérprete LuaJ). Tu script se ejecuta **una vez por cada bloque** del pincel, con sus coordenadas, y puede colocar bloques. También se usa Lua en las [máscaras Lua](Tool-Masks-y-Presets.md).

## Primer uso

1. Elige **Script Brush** en *Tools*.
2. En la lista de **ejemplos** carga uno (*Texturing, Spikes, Noise*) o escribe el tuyo.
3. Pulsa **Check syntax** para ver errores sin ejecutar.
4. **Mantén el botón derecho** sobre el mundo. Cada sello ejecuta el script en cada bloque del pincel (o una vez con `$once$`).

Debajo del editor, **Highlighted preview** muestra el código coloreado.

## Cómo funciona

- Variables disponibles: **`x`, `y`, `z`** = el bloque que se está procesando.
- Si el script **devuelve un id de bloque** (`return blocks.stone`), ese bloque se coloca en `x, y, z`.
- También puedes llamar a `setBlock(x, y, z, id)` para colocar en cualquier sitio.
- Un **id de estado** es `id_del_bloque * 16 + meta` (el estado de 1.12).
- Corre **aislado**: sin `io`, `os`, `load` ni `require`.
- Cada sello tiene un presupuesto de **10 millones de instrucciones** (corta los bucles infinitos) y un tope de **200 000 bloques** colocados.
- Un error detiene el script durante el resto del trazo y la barra de estado dice por qué.

## Variables de plantilla (controles automáticos)

Escribe una variable entre `$` y el editor genera un control:

| Plantilla | Control |
|---|---|
| `$int(Título, valor, mín, máx)$` | Slider entero |
| `$float(Título, valor, mín, máx)$` | Slider decimal |
| `$boolean(Título, valor)$` | Casilla |
| `$blockState(Título, minecraft:stone)$` | Selector de bloque (se sustituye por su id de estado) |
| `$once$` | El script corre **una sola vez por sello**, en el bloque apuntado |

```lua
$once$
-- Una columna sobre el bloque apuntado, una vez por clic.
local altura = $int(Altura, 6, 1, 20)$
local bloque = $blockState(Bloque, minecraft:stone)$
for i = 1, altura do
  setBlock(x, y + i, z, bloque)
end
```

## Referencia de la API

### Leer y escribir bloques

| Función | Devuelve | Descripción |
|---|---|---|
| `getBlock(x, y, z)` | id de estado | **Qué bloque** hay: el estado por defecto de ese bloque, sin variante ni propiedades (0 si no está cargado). Sirve para comparar con `blocks.xxx` |
| `getBlockState(x, y, z)` | id de estado | El **estado completo** (variante, orientación, mitad...). Úsalo con `getBlockProperty` / `withBlockProperty` |
| `setBlock(x, y, z, id)` | nada | Coloca el id de estado en esa posición |
| `getHighestBlockYAt(x, z)` | número | Y del bloque no aire más alto de la columna; `-1` si está vacía |
| `isSolid(id)` | booleano | Si el material del estado es sólido |
| `print(...)` | nada | Muestra el texto en la barra de estado, con el prefijo `Script:` |

### Propiedades y etiquetas

| Función | Devuelve | Descripción |
|---|---|---|
| `withBlockProperty(id, "prop=valor")` | id de estado | El estado con esa propiedad cambiada (si no existe, el mismo) |
| `getBlockProperty(id, "prop")` | texto o `nil` | El valor de esa propiedad |
| `isBlockTagged(id, "tag")` | booleano | Si el bloque cumple el *tag* (aproximado en 1.12, ver abajo) |

`isBlockTagged` reconoce `fences, fence_gates, logs, leaves, slabs, stairs, walls, doors, buttons, sand, saplings, flowers` por la clase del bloque; para cualquier otro texto compara con el nombre del bloque (coincidencia exacta o parcial).

### Color

| Función | Devuelve | Descripción |
|---|---|---|
| `getBlockRGB(id)` | entero `0xRRGGBB` o `nil` | Color medio del bloque |
| `findClosestBlockToRGB(rgb, flags, índice)` | id de estado | El bloque cuyo color medio más se parece a `rgb`. `índice` 0 = el más cercano, 1 = el segundo... |

Los *flags* de `findClosestBlockToRGB` se suman: `1` sólido, `2` opaco, `4` cubo completo, `16` sin minerales, `32` sin terracota vidriada, `64` sin tile entities.

### Ruido

| Función | Devuelve | Descripción |
|---|---|---|
| `getSimplexNoise(x, y, z, semilla)` | 0 a 1 | Ruido simplex 3D con semilla opcional (`0` por defecto) |
| `getVoroniEdgeNoise(x, y, z, semilla)` | 0 a 1 | Distancia a los bordes de las celdas de Voronoi (grietas) |

Divide las coordenadas para cambiar la escala: `getSimplexNoise(x / 8, y / 8, z / 8, 1)`.

### La tabla `blocks`

`blocks.nombre` da el id de estado de un bloque (meta 0), con los **nombres de 1.12** (`blocks.stone`, `blocks.grass`, `blocks.cobblestone`, `blocks.air`...). Para una variante: `blocks["dirt:1"]`. Hay alias de nombres de versiones nuevas: `coarse_dirt`, `podzol`, `grass_block`, `dirt_path`, `granite`, `diorite`, `andesite` (y sus pulidos), `oak_planks`... `dark_oak_planks`, `oak_log`... `dark_oak_log`, entre otros.

## Ejemplos

**Mancha de ruido sobre hierba** (el ejemplo *Texturing*):

```lua
local umbral = $float(Umbral, 0.6, 0, 1)$
if getBlock(x, y, z) == blocks.grass then
  if getSimplexNoise(x / 6, y / 6, z / 6, 1) > umbral then
    return blocks.dirt
  end
end
```

**Sólidos a adoquín donde el ruido es alto** (el ejemplo *Noise*):

```lua
local n = getSimplexNoise(x / 8, y / 8, z / 8, 7)
if isSolid(getBlock(x, y, z)) and n > 0.55 then
  return blocks.cobblestone
end
```

**Orientar las escaleras al norte** (cambiar una propiedad del estado completo):

```lua
local id = getBlockState(x, y, z)
if isBlockTagged(id, "stairs") then
  return withBlockProperty(id, "facing=north")
end
```

**Sustituir los sólidos por el bloque más parecido a un gris** (usa el color real de los bloques):

```lua
if isSolid(getBlock(x, y, z)) then
  return findClosestBlockToRGB(0x808080, 1 + 2 + 4, 0)
end
```

## Diferencias con Axiom

Este Script Brush se basa en el de Axiom, adaptado a 1.12.2:

- Un id de estado es `id_del_bloque * 16 + meta` en lugar del id global de versiones modernas.
- `getBlock` devuelve el bloque sin su variante (por eso `getBlock(x, y, z) == blocks.stone` también se cumple con granito o andesita); para distinguirlas usa `getBlockState`.
- `blocks.xxx` usa los nombres de 1.12 (con algunos alias de los nuevos).
- Los *tags* se aproximan por clase o nombre del bloque.

## Consejos

- Prueba primero con un radio pequeño y mira la barra de estado: los errores salen ahí.
- Si el pincel parece "no hacer nada", comprueba que el script **devuelve** un id (`return ...`) o llama a `setBlock`.
- Los scripts de ejemplo se cargan en el idioma elegido en ese momento.
- Guarda tus scripts como [preset](Tool-Masks-y-Presets.md#presets): el código Lua se guarda con el preset.
