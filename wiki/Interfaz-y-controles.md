# Interfaz y controles

## Las ventanas

El editor usa **docking**: cada ventana se arrastra por su título y se acopla a otro sitio o flota. El diseño por defecto es:

| Zona | Ventanas |
|---|---|
| Izquierda (pestañas) | **Tools**, **Selection**, **Active Block**, **Clipboard** |
| Arriba a la derecha | **Tool Options**, **Tool Masks**, **Placement** |
| Abajo a la derecha | **Target Info**, **World Properties**, **History**, ... |
| Centro | Libre: deja pasar el ratón al mundo |
| Barra inferior | Herramienta activa, tamaño de la selección, último mensaje y FPS |

Otras ventanas se abren desde el menú **Window**: *Tool Masks, History, Schematics, Blueprint Browser, Palette, Reference Image, Text Annotations, Style Editor, Keybinds, Colour Picker, Gradient Helper*. También hay *Inventory* y *Views*.

| Ventana | Para qué sirve |
|---|---|
| **Tools** | Cuadrícula de iconos con las 32 herramientas; el tooltip muestra el nombre y el atajo |
| **Tool Options** | Los ajustes de la herramienta activa y, al final, sus *Presets* |
| **Selection** | Posiciones Pos 1 / Pos 2, estirar y encoger caras, *Replace*, operaciones (*More operations*) |
| **Active Block** | El bloque con el que se pinta; icono de inventario = *Block Picker* |
| **Clipboard** | Lo copiado; guardar como Blueprint |
| **Placement** | Panel de la copia que vas a pegar: giro, escala, snap, merge, vista previa |
| **Target Info** | Bloque, bioma y distancia bajo el cursor |
| **World Properties** | Hora, reglas del mundo y [ajustes del jugador](Jugador-y-mundo.md) |
| **History** | Lista de ediciones: pulsar una deshace o rehace hasta ella |
| **Inventory** | Tu inventario con iconos; un clic copia al Active Block |
| **Palette** | Los 16 bloques usados más recientemente y paletas propias |
| **Views** | Posiciones guardadas con nombre |

### Menús

- **File:** *Schematics...*, *Blueprint Browser...*, *Open Reference Image...*, *Save Blueprint...*, exportar la selección (Sponge, Structure NBT, CSV).
- **Edit:** *Cut, Copy, Copy With... (Entities / Air / Entities and Air), Duplicate, Paste*.
- **Select:** *Clear, Expand, Shrink, Convex Hull, Bounding Box, Clear Ruler*.
- **Operations:** todas las [operaciones](Operaciones.md).
- **View:** *Theme, Language, UI size, Show Selections / Ruler / Key Presses / Biomes / Annotations, Invert Camera Rotate, Fluid Opacity, Min Brightness, Reset layout*.
- **Window:** las ventanas, *Store Current Layout as Default*, *Restore Default Layout*, *Export / Import Layout* (por portapapeles).
- **Help:** *Style Editor, Keybinds, About LiteBuild*.

### Temas, idioma y tamaño

- **View > Theme:** *Axiom Dark*, *Light*, *Classic* y *ImGui Dark*. **Window > Style Editor** es el editor de estilo de ImGui; puedes exportar e importar un tema por el portapapeles.
- **View > Language:** *Auto* sigue el idioma del juego (español para cualquier `es_*`, inglés en el resto). El cambio es inmediato y se guarda. Se traducen los textos explicativos, mensajes y HUD; los nombres de herramientas y ajustes siguen en inglés.
- **View > UI size:** tamaño de la fuente, de 12 a 28 px.
- **Reset layout** vuelve al diseño por defecto.

## Cámara y movimiento

| Acción | Entrada |
|---|---|
| Girar la cámara | Arrastrar con el botón izquierdo sobre el mundo |
| Moverse / volar | W A S D, Espacio, Shift (vuelo creativo: doble Espacio) |
| Arcball alrededor del punto apuntado | **Ctrl + arrastrar** |
| Desplazar la cámara | **Shift + arrastrar** |
| Velocidad de vuelo | Rueda |
| Radio del pincel | **Ctrl + rueda** |
| Invertir el giro de la cámara | View > *Invert Camera Rotate* |

> En Axiom el desplazamiento es Ctrl + botón derecho; aquí es Shift + arrastrar porque el botón derecho lo usan las herramientas.

## Ratón en las herramientas

| Botón | Hace |
|---|---|
| **Derecho** | Acción de la herramienta: marcar, pintar (mantenido), colocar |
| **Izquierdo** | Cámara; en los *manejadores* y el gizmo, arrastrar |
| **Central** | Copiar el bloque apuntado al Active Block |

## Atajos

Todos son reasignables en **Window > Keybinds** (*Set*, pulsa la tecla, con Ctrl o Shift si los quieres; Esc cancela). Los que chocan con otro del mismo contexto salen en rojo. Se guardan en `config/litebuild/keybinds.properties`.

### Edición

| Atajo | Acción |
|---|---|
| Ctrl+Z / Ctrl+Y | Deshacer / rehacer |
| Ctrl+C / Ctrl+X | Copiar / cortar |
| Ctrl+V | Pegar (abre el Placement; dentro del Placement, confirmar) |
| Supr | Borrar la selección |
| Ctrl+F | Quick Fill (rellenar con el Active Block) |
| Ctrl+R | Quick Replace |
| Ctrl+J | Duplicate |
| Ctrl+P | Guardar Blueprint |
| Ctrl+B | Show Biomes |

### Placement

| Atajo | Acción |
|---|---|
| Clic derecho | Fijar / volver a seguir el cursor |
| Flechas, RePág / AvPág | Mover la copia si está fijada |
| Ctrl+R | Rotar |
| Ctrl+F | Voltear según hacia dónde mires |
| Enter / Ctrl+V | Pegar |
| Supr / Retroceso | Cancelar |

### Herramientas

| Tecla | Herramienta | Tecla | Herramienta |
|---|---|---|---|
| B | Box Select | E | Elevation |
| M | Magic Select | Z | Extrude |
| N | Freehand Select | H | Rock |
| L | Lasso Select | U | Smooth |
| P | Painter | K | Melt |
| O | Noise Painter | J | Weld |
| G | Freehand Draw | | |

Las demás no tienen tecla por defecto. Hay también *Swap To Last Tool* (sin tecla) y *Extrude Point* (E, contextual, en Modelling). Los atajos fijos no se pueden cambiar: flechas, RePág/AvPág, Retroceso en el Placement, clic central, Escape y Right Shift.

> Fuera del editor, **B** o *tocar Alt* abren el [menú de Builder](Builder-Mode.md). No chocan: en el editor, B es Box Select.

## Ayudas en pantalla

- **Show Ruler** y **Show Selections** dibujan la regla y las selecciones.
- **Show Key Presses** muestra las teclas y botones pulsados (abajo a la derecha).
- Casi toda opción tiene un **tooltip** al pasar el ratón.
