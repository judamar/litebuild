# Instalación

## Requisitos

| Qué | Detalle |
|---|---|
| Minecraft | **1.12.2** (Java Edition) |
| Cargador | **LiteLoader** para 1.12.2 (el mod es un `.litemod`; no usa Forge) |
| Java | **Java 8** |
| Sistema | **Linux** o **Windows** de 64 bits. El `.litemod` solo trae las librerías nativas de imgui-java para esos dos; para macOS no hay librerías nativas incluidas, así que la interfaz no cargaría |
| Gráficos | Los que ya sirvan para Minecraft 1.12.2: el editor dibuja con la tubería fija de OpenGL a través de LWJGL 2 |
| Modo de juego | Mundo de **un jugador**. No hay versión para servidores ni multijugador |

## Instalar el mod

1. Crea (o abre) una instancia de **Minecraft 1.12.2 con LiteLoader**. En **Prism Launcher**: crear instancia → versión 1.12.2 → *Editar instancia* → *Versión* → *Instalar LiteLoader*. Comprueba que usa Java 8.
2. Descarga `mod-litebuild-<versión>-mc1.12.2.litemod` de la página de *Releases* del repositorio (y, si quieres, comprueba su hash con `SHA256SUMS.txt`).
3. Cópialo en la carpeta de mods de LiteLoader de la instancia:
   - Prism: `<instancia>/.minecraft/mods/`
   - Alternativa: `mods/1.12.2/` dentro de esa misma carpeta.
4. Arranca el juego. **No hace falta instalar nada más**: el mod lleva dentro imgui-java, sus librerías nativas y el intérprete de Lua.

> Usa un `.litemod` por mod y una sola versión de LiteBuild a la vez. Si actualizas, borra la anterior.

## Comprobar que ha cargado

Entra en un mundo de un jugador y pulsa **Right Shift**: se abre el editor. Si no:

1. Abre `.minecraft/logs/latest.log` y busca:
   - `Successfully added mod LiteBuild version 1.0.0`
   - `LiteBuild cargado`
   - `imgui-java: librería nativa cargada`
2. En *Opciones > Controles* debe haber una categoría **LiteBuild** con *Open editor* (Right Shift), *Toggle No Clip*, *Builder menu* (B) y una tecla por cada capability.

Si falta algo de lo anterior, mira [Solución de problemas](Solucion-de-problemas.md).

## Primera vez: lo que verás

- Un aviso del log sobre `config/litebuild`: si venías de versiones anteriores (`config/litbuild` o `config/axiomlite`), la carpeta se renombra sola y se conservan presets, paletas, hotbars y atajos.
- El editor abre con un diseño por defecto con ventanas acopladas (ver [Interfaz y controles](Interfaz-y-controles.md)). El idioma sigue el del juego (*Auto*); se cambia en **View > Language**.

## Actualizar y desinstalar

- **Actualizar:** sustituye el `.litemod` por el nuevo. La configuración se mantiene.
- **Desinstalar:** borra el `.litemod`. La carpeta `config/litebuild` y los archivos `saves/<mundo>/litebuild-annotations.txt` se quedan donde estaban; puedes borrarlos si no los quieres.
- **Copias de seguridad:** el editor trabaja directamente sobre el mundo. Antes de ediciones grandes, copia la carpeta del mundo. Ctrl+Z deshace las ediciones del editor, pero no es una copia de seguridad.
