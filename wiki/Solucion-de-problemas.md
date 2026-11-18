# Solución de problemas y preguntas frecuentes

## Antes de nada: mira el log

`.minecraft/logs/latest.log` (y `crash-reports/` si el juego se cerró de golpe). Debe contener, al arrancar:

```
Successfully added mod LiteBuild version 1.0.0-rc1
LiteBuild cargado
imgui-java: librería nativa cargada
```

El log del mod está en **español** aunque la interfaz esté en inglés. Los `ERROR ... security seal for path net.java.games.input` del principio y el aviso del *Narrator* (`fliteWrapper`) son ruido habitual de Minecraft/Prism y no vienen del mod.

## El editor no se abre

| Síntoma | Posible causa y solución |
|---|---|
| Right Shift no hace nada | Estás en el menú principal o en otra pantalla: entra en un mundo de **un jugador**. Comprueba en *Controles > LiteBuild* que *Open editor* sigue en Right Shift (puede haber otro mod que use la tecla) |
| No sale `LiteBuild cargado` en el log | El `.litemod` no está en `mods/` (o en `mods/1.12.2/`) de la instancia, o la instancia no tiene LiteLoader |
| Falta `imgui-java: librería nativa cargada` / error al cargar la librería | Sistema no soportado (solo Linux y Windows de 64 bits) o Java que no es de 64 bits |
| El juego no arranca con LiteLoader | La instancia debe ser 1.12.2 y usar **Java 8** |
| Nada de lo del mod funciona y no hay errores | Revisa que no tengas dos versiones de LiteBuild en `mods/` |

## Cierre o error al usar el mod

Si el juego se cierra de golpe, busca en el log:

- `Dear ImGui Assertion Failed` + `Assertion Located At`: es un error de la interfaz. Anota la ventana que salía en `In window '...'` y la línea del `StackTrace` con `dev.litebuild`.
- `java.lang.NoClassDefFoundError` / `MixinApplyError`: un mixin no pudo aplicarse. Pasa si hay otro mod que modifica las mismas clases.

> En 1.0.0-rc1 está corregido un cierre que ocurría al **activar una capability** del menú de Builder (por ejemplo Tinker). Si te pasa en una versión anterior, actualiza.

**Para reportar un fallo**, incluye: la versión de LiteBuild, qué hacías, qué esperabas, qué pasó, el texto exacto de cualquier mensaje y `latest.log` (y el crash report si lo hay).

## Cosas que parecen fallos pero no lo son

| Qué ves | Por qué |
|---|---|
| Los **cofres y carteles** no salen en la vista previa del Placement | La vista previa no dibuja *tile entities*. Al pegar sí se colocan |
| La vista previa tiene luz máxima | Se renderiza con luz plena y bioma llanura |
| Una copia grande (más de 20 000 bloques) solo muestra la caja | Es el tope de la vista previa; al pegar se pega entera |
| Mientras arrastras un anillo o un cubito de escala solo se ve la caja | Es a propósito para ir fluido; los bloques vuelven al soltar |
| El **fluido** cambia de nivel después de colocarlo | El juego sigue simulando el flujo y puede retocar los niveles |
| Con los anillos, el *yaw* sale en otro valor (p. ej. 150° en vez de -30°) | Se vuelve a pasar a yaw/pitch/roll y se queda entre -90° y 90°; el resultado es el mismo giro |
| La traducción al inglés suena rara | Es automática; las frases que no están en el catálogo se ven en español |
| Algunos mensajes siguen en español con el idioma en inglés | El log y algunos textos sin traducir se quedan en español |
| *Min Brightness* sigue subido tras cerrar el juego | Se restaura al arrancar con `config/litebuild/gamma.txt`; abre el juego una vez |
| *No Clip* no atraviesa | Solo actúa **volando**. Activa el vuelo (doble salto) |

## Una herramienta no hace lo esperado

- **El pincel no pinta:** comprueba el Active Block, que **Tool Masks** no esté activada con reglas que lo excluyan, y la barra de estado (a veces explica el motivo).
- **Una máscara Lua no deja pasar nada:** el script tiene un error de sintaxis o se pasó de las 200 000 instrucciones; la barra de estado lo dice. Con *NOT* tampoco deja pasar si el script falla.
- **`isSolid` siempre da el mismo resultado:** recibe un **id de estado**, no coordenadas. Usa `isSolid(getBlock(x, y, z))`.
- **La operación no se aplica:** admite hasta 500 000 bloques y no toca chunks sin cargar. Acércate o reduce la selección.
- **Magic Select o Lasso dejan huecos:** ver las opciones *Range*, *Depth* e *Include Non-Solid* en [Selección](Seleccion.md).
- **Smart Surface da un error:** necesita 3 puntos que no estén en línea.
- **El Placement pega algo distinto de lo que veo:** con Merge Blocks, las losas y la nieve se combinan con lo que ya hay en el mundo.

## Preguntas frecuentes

**¿Es Axiom?** No. LiteBuild es una reimplementación propia, a partir de la documentación pública de Axiom, sin código ni recursos suyos y sin relación con su autor. Axiom es un producto de otra empresa y su licencia prohíbe copiarlo o hacer obras derivadas de él.

**¿Funciona con Forge?** No: es un mod de LiteLoader (`.litemod`).

**¿Y en servidores o en multijugador?** No. Edita el mundo del servidor integrado, así que solo en un jugador. Tampoco hay licencia comercial ni los comandos `/axiom`.

**¿Funciona en 1.8, 1.16, 1.20...?** No, solo en **1.12.2**.

**¿Funciona en macOS?** No hay librerías nativas de imgui-java para macOS en el `.litemod`.

**¿Puedo cambiar los atajos?** Los del editor, en *Window > Keybinds*; los del juego (Open editor, Builder menu...), en *Opciones > Controles > LiteBuild*.

**¿Cómo deshago algo?** Ctrl+Z y Ctrl+Y en el editor; la ventana *History* para saltar varios pasos. Algunas cosas **no** entran en el historial: las colocaciones de las *capabilities* de Builder (como Replace Mode o Force Place), los cambios de forma de la selección y *Clear ALL Annotations*.

**¿Dónde se guarda lo que creo?** Mira [Configuración y archivos](Configuracion-y-archivos.md).

**¿Puedo usar mis schematics de WorldEdit?** Sí: *File > Schematics...* importa `.schematic`, `.schem` (Sponge v1, v2 y v3) y `.nbt`. Los bloques que no existen en 1.12 entran como aire.

**¿Hay `.bp` de Axiom?** No, ese formato es cerrado. Los blueprints de LiteBuild son `.schem` con metadatos.

## Funciones que no existen en 1.12

Marker Entities y Display Entities (son de 1.17 y 1.19.4 en adelante), Show Light Blocks, el slot 10 de la hotbar, multijugador, Commercial License, configuración HOCON y los comandos `/axiom...`.
