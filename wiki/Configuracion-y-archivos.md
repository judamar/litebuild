# Configuración y archivos

Todo se guarda dentro de la carpeta de la instancia (`.minecraft`):

## `config/litebuild/`

| Ruta | Contenido |
|---|---|
| `ui.properties` | Tema, idioma, tamaño de la interfaz y otros ajustes de vista |
| `imgui.ini` | Disposición de las ventanas (docking) |
| `keybinds.properties` | Atajos del editor reasignados (se crea al cambiar alguno) |
| `views.properties` | Posiciones guardadas de la ventana *Views* |
| `presets/<herramienta>.txt` | Presets de cada herramienta (y de Tool Masks) |
| `palettes.txt` | Paletas de bloques |
| `hotbars.dat` | Hotbars guardadas del Hotbar Swapper |
| `schematics/` | Exportaciones del portapapeles y de la selección (`.schematic`, `.schem`, `.nbt`, `.csv`) |
| `blueprints/` | Blueprints (`.schem` con metadatos y miniatura) |
| `heightmaps/` | Imágenes para el *Heightmap* de Elevation |
| `gamma.txt` | El brillo original, por si el juego guarda el gamma alto de *Min Brightness* |

Algunos archivos solo aparecen cuando usas la función correspondiente.

## Dentro de cada mundo

| Ruta | Contenido |
|---|---|
| `saves/<mundo>/litebuild-annotations.txt` | Las anotaciones de ese mundo |

## Atajos del juego

*Opciones > Controles > LiteBuild*: **Open editor** (Right Shift), **Toggle No Clip**, **Builder menu** (B) y una tecla por capability (**R** para Replace Mode). Estas teclas las guarda Minecraft con el resto de controles, no `keybinds.properties`.

## Copias de seguridad y traslados

- Para llevar tus presets, paletas y hotbars a otra instalación, copia `config/litebuild` (incluida la subcarpeta `presets`).
- Para compartir un diseño, usa un **schematic** (`.schem`) o un **blueprint**: ambos se abren también en WorldEdit y otras herramientas.
- Las anotaciones viajan con la carpeta del mundo.

## Migración desde nombres anteriores

Si existe `config/litbuild` o `config/axiomlite` (nombres anteriores del mod), la carpeta se **renombra sola** a `config/litebuild` al arrancar y se conserva todo. En el log sale *Configuración migrada de config/litbuild a config/litebuild*. Los tres atajos de Controles (`Open editor`, `Toggle No Clip`, `Builder menu`) se restablecen a sus valores por defecto al cambiar de nombre.

## Restablecer

- **Disposición:** *View > Reset layout* o *Window > Restore Default Layout*.
- **Todo:** cierra el juego y borra `config/litebuild`. Se vuelve a crear con los valores por defecto.
