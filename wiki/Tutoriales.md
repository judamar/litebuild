# Tutoriales

Seis proyectos cortos, de menos a más. Todos usan solo lo que viene en el mod. Si te pierdes con un término, está explicado en su página de referencia. Antes de empezar, **copia la carpeta de tu mundo** o usa uno de pruebas.

---

## 1. Una torre en cinco minutos

*Practica: Box Select, Fill, Replace, Hollow, Undo.*

1. En **Active Block** elige `minecraft:stone_bricks` (clic central sobre uno, o con el selector).
2. **B** (Box Select). Con el botón derecho marca una caja de **7 × 20 × 7** en el suelo. Ajusta con *Pos 1 / Pos 2* o con el panel *Selection* (*Amount* + Up/Down/North/South/West/East estiran una cara).
3. **Operations > Fill**. Ya tienes un bloque macizo.
4. **Operations > Hollow**: vacía el interior y deja las paredes.
5. Cambia el Active Block a `minecraft:cobblestone`. En *Selection*, rellena el campo **Replace** con `minecraft:stone_bricks` y pulsa *Replace*: cambia solo ese bloque. (*Meta* `-1` significa "cualquiera".)
6. Algo salió mal: **Ctrl+Z** las veces que haga falta. *Window > History* te deja saltar varios pasos de golpe.

**Para ir más allá:** *Fill Walls*, *Fill Top* y *Fill Bottom* del menú Operations rellenan solo esa parte de la caja.

---

## 2. Tres copias de la torre con Placement

*Practica: copiar, pegar, gizmo, merge.*

1. Con la torre seleccionada, **Ctrl+C**.
2. **Ctrl+V**. Mueve el cursor; la caja naranja y la vista previa siguen. **Clic derecho** para fijarla.
3. Arrastra una **flecha de color** con el botón izquierdo para moverla de bloque en bloque por ese eje. El **cubo blanco** la mueve libremente.
4. Con los **anillos** la giras (con **Ctrl** salta de 15° en 15°). Con los **cubitos claros** la escalas.
5. **Snap to ground** (panel Placement) la baja hasta apoyarla.
6. **Enter** pega. Repite Ctrl+V para la siguiente copia.

**Tip:** *Paste Copy* mantiene el Placement tras pegar para colocar más copias.

---

## 3. Remodelar una ladera

*Practica: Smooth, Elevation, Noise Painter, Tool Masks.*

1. **U** (Smooth). Radio 8, *Strength* 2. Mantén el botón derecho y pasa por una zona escarpada: el terreno se suaviza conservando el número de bloques.
2. **E** (Elevation). Modo *Raise*, radio 10. Arrastra para levantar una colina; con *Flatten* aplanas.
3. **O** (Noise Painter). Añade tres bloques a la lista con pesos `6 / 3 / 1` (por ejemplo hierba, tierra, grava). *Scale* 6, *Octaves* 3. Pasa sobre la colina: los pesos son porcentajes del volumen pintado.
4. Abre **Window > Tool Masks**, marca **Enabled** y añade una regla *Surface* (el bloque tiene algún vecino no sólido, es decir, solo la superficie). Repite el paso 3 y compara.

Más pinceles en [Pinceles de terreno](Pinceles.md) y máscaras en [Tool Masks y presets](Tool-Masks-y-Presets.md).

---

## 4. Un bosque con Stamp

*Practica: portapapeles + Stamp.*

1. Construye (o localiza) un árbol y cópialo: caja de selección alrededor, **Ctrl+C**.
2. Elige **Stamp**. Ajusta *Spacing* (distancia entre árboles), *Scatter*, *Chance* y activa el giro y volteo al azar.
3. Arrastra con el botón derecho sobre el terreno: copia el árbol a lo largo del trazo, apoyado en la superficie.
4. *Keep Existing* evita sustituir lo que ya hay.

> Stamp no copia *tile entities* (cofres, carteles).

---

## 5. Un lago con Fluid Ball

*Practica: Fluid Ball y sus opciones.*

1. Cava un hoyo con **Painter** y el Active Block en `air`, o con *Fill* de aire sobre una selección.
2. Elige **Fluid Ball**, tipo **Water**, radio 6.
3. Prueba combinaciones:
   - *Infinite Sources* activado: toda la esfera son fuentes.
   - *Flow Length* 5: el agua se extiende en horizontal y baja un nivel por bloque.
   - *Fill Edges*: rellena las esquinas de la orilla y deja plano un recipiente cerrado.
   - *Quality* 1 a 3: la mancha pasa de rombo a más redonda.
4. **Ctrl+Z** quita todo lo colocado, también el flujo.

---

## 6. Tu primer script (Script Brush)

*Practica: Lua en el editor.*

1. Elige **Script Brush**. En la lista de ejemplos carga **Texturing**: cambia parte de la hierba por tierra, en manchas.
2. Pulsa **Check syntax** y pinta con el botón derecho.
3. Cambia el umbral con el control que generó solo (`$float(Umbral, 0.6, 0, 1)$` crea un slider).
4. Prueba un script propio:

```lua
-- Convierte las piedras expuestas al aire en musgosas, con ruido.
if getBlock(x, y, z) == blocks.stone and getBlock(x, y + 1, z) == blocks.air then
  if getSimplexNoise(x / 5, y / 5, z / 5, 3) > 0.55 then
    return blocks.mossy_cobblestone
  end
end
```

5. La referencia completa de funciones está en [Script Brush y Lua](Script-Brush-Lua.md).

---

## Más ideas

- **Modelling:** crea un tejado con *Smart Surface* a partir de unos pocos puntos ([Modelling y Modify](Modelling-y-Modify.md)).
- **Autoshade:** sombrea una pared o un terreno con una paleta de oscuro a claro ([Operaciones](Operaciones.md)).
- **Builder Mode:** prueba *Replace Mode*, *Tinker* y *Infinite Reach* paseando por el mundo ([Builder Mode](Builder-Mode.md)).
