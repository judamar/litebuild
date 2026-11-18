# Jugador y mundo

## World Properties

Ventana **World Properties** (abajo a la derecha por defecto): hora y reglas del mundo.

- **Hora** del día.
- **Gamerules:** ciclo de día, mobs, fuego, tick aleatorio, *Block drops* (`doTileDrops`)...

## Player

En *World Properties > Player*:

| Ajuste | Efecto |
|---|---|
| **Flight speed** | Velocidad de vuelo, del 100 al 999 % |
| **No Clip** | Atraviesas bloques mientras vuelas (ver abajo) |
| **Invulnerable** | No recibes daño |
| **Block gravity** | Desactivado: la arena y la grava no caen |
| **Trample farmland** | Desactivado: no se pisa la tierra de cultivo (el daño por caída sigue) |
| **Block drops** | La regla `doTileDrops`: si los bloques sueltan objetos al romperse |
| **Creative / Survival / Spectator** | Cambian el modo de juego; **necesitan permisos de comandos** |

## No Clip

Casilla en *World Properties > Player*, o la tecla **Toggle No Clip** de *Controles > LiteBuild* (sin asignar por defecto). También está entre las *capabilities* del [menú de Builder](Builder-Mode.md).

- Atraviesas bloques sin asfixia ni la textura del bloque tapando la pantalla.
- Al activarlo **empiezas a volar** (hace falta poder volar: creativo).
- **Solo atraviesa mientras vuelas.** Si dejas de volar (doble salto), vuelves a chocar con los bloques; al volver a volar, atraviesas otra vez. Sin vuelo la gravedad te haría caer por el suelo.
- Funciona con mixins sobre `Entity.move`, `pushOutOfBlocks` e `isEntityInsideOpaqueBlock`. Si no se aplicaran, el juego sigue normal y la ventana avisa de que "aún no ha actuado".

## Opciones de vista

Menú **View**:

| Opción | Efecto |
|---|---|
| **Show Biomes** (Ctrl+B) | Colorea los biomas del terreno de alrededor |
| **Fluid Opacity** (0-1) | Cambia el alfa de las texturas del **agua**, quieta y en movimiento. *Reset Fluid Opacity* vuelve al de vanilla. Se guarda |
| **Min Brightness** (0-1) | Sube el gamma del juego, también fuera del editor. Apunta el original en `config/litebuild/gamma.txt` por si el juego guarda el gamma alto: al arrancar se restaura |
| **Show Annotations / Selections / Ruler** | Mostrar u ocultar |
| **Show Key Presses** | Teclas y botones pulsados |
| **Invert Camera Rotate** | Invierte el giro de la cámara |

## Vuelo en Builder Mode

En la *Toolbox* del menú de Builder: **Flight direction** (*Horizontal* o *Camera*) y **Flight speed**.

## Límites

- Funciona solo en **mundos de un jugador** (edita el mundo del servidor integrado).
- Los botones de modo de juego usan comandos: el mundo debe permitir trucos.
