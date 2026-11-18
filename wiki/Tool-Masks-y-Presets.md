# Tool Masks y presets

## Tool Masks

**Window > Tool Masks.** Una *máscara* es una lista de reglas que decide **en qué bloques** puede actuar un pincel. Se aplica a **Painter, Noise Painter, Freehand Draw, Smooth, Melt y Weld**.

1. Marca **Enabled**.
2. **Add rule** y elige el *Type* de cada regla. Todas deben cumplirse.
3. **NOT** invierte una regla. **Remove** la quita.

### Tipos de regla

| Tipo | Se cumple si... |
|---|---|
| **Block** | El bloque es el indicado (id + meta; meta `-1` = cualquiera) |
| **Above** | El bloque de **encima** es el indicado |
| **Below** | El bloque de **debajo** es el indicado |
| **Surface** | Algún vecino por las 6 caras es **no sólido** (la superficie) |
| **Can See Sky** | Hay cielo abierto encima |
| **In Selection** | El bloque está dentro de la selección actual |
| **Y** | La altura está en el rango (*Y range*) |
| **Neighbour** | Algún vecino por las 6 caras es el bloque indicado |
| **Adjacent** | Algún vecino **horizontal** (4 lados) es el bloque indicado |
| **Lua** | Un script de Lua devuelve verdadero (ver abajo) |

### Ejemplos

- *Pintar solo hierba:* Block = `grass`.
- *Pintar solo en la superficie y sobre los 70:* Surface + Y range `70..255`.
- *No tocar el agua:* Block = `water` con **NOT**.
- *Pintar la arena junto al agua:* Block = `sand` + Adjacent = `water`.

### Máscaras Lua

El tipo **Lua** ejecuta un script **en cada bloque del pincel**, con las variables `x`, `y`, `z` y las funciones del Script Brush (`getBlock`, `isSolid`, `blocks`...). Si devuelve verdadero, el bloque pasa.

```lua
-- Solo sólidos por encima de Y 70.
return isSolid(getBlock(x, y, z)) and y > 70
```

```lua
-- Solo bloques con aire a su lado (cualquier cara horizontal).
return getBlock(x + 1, y, z) == blocks.air or getBlock(x - 1, y, z) == blocks.air
    or getBlock(x, y, z + 1) == blocks.air or getBlock(x, y, z - 1) == blocks.air
```

Reglas del sistema:

- Máximo **200 000 instrucciones por bloque**.
- Si el script falla (sintaxis, error o demasiado largo), **la regla no deja pasar nada** (incluso con NOT) y la barra de estado explica el motivo.
- Debajo del editor, *Highlighted preview* muestra el código coloreado (el campo de texto de ImGui no admite colores).

> `isSolid` recibe un **id de estado**, no coordenadas: `isSolid(getBlock(x, y, z))`.

La referencia completa de funciones está en [Script Brush y Lua](Script-Brush-Lua.md#referencia-de-la-api).

## Presets

Al final de **Tool Options** (y de la ventana **Tool Masks**) hay una sección **Presets**:

- **Save preset:** guarda los ajustes actuales con un nombre (si ya existe, lo sustituye).
- **Load:** carga un preset.
- **X:** lo borra.

Valen para: Painter, Noise Painter, Freehand Draw, Smooth, Melt, Weld, Elevation, Distort, Roughen, Shatter, Rock, **Script Brush** (con su código Lua), Gradient Painter, Sculpt Draw, Slope, Stamp, Fluid Ball, Floodfill, Path, Extrude, Magic Select, Text, Shape, Lasso y Freehand Select, además de **Tool Masks** (con sus reglas). Noise y Gradient guardan también la lista de bloques con sus pesos.

- Se guardan por herramienta en `config/litebuild/presets/<herramienta>.txt`. Puedes copiar esos archivos a otra instalación.
- **No entran:** el Active Block, la selección ni los bloques de variantes del Stamp.
- **Sin presets todavía:** Modify, Biome Painter y Annotation.
- Autoshade tiene su propia sección de presets en *Selection > More operations*.
