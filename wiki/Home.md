# LiteBuild: wiki y tutorial

**LiteBuild** es un mod de **cliente** para **Minecraft 1.12.2** con **LiteLoader** que añade un editor de mundo con interfaz gráfica (Dear ImGui) y un modo de construcción para usar en el propio juego. Está inspirado en la funcionalidad de Axiom, pero es una reimplementación propia: no contiene código ni recursos de Axiom y no está relacionado con su autor.

> Funciona solo en **mundos de un jugador** (edita el mundo del servidor integrado). Versión de esta wiki: **1.0.0**.

## Por dónde empezar

| Si quieres... | Ve a |
|---|---|
| Instalarlo y comprobar que carga | [Instalación](Instalacion.md) |
| Una visita guiada de 10 minutos | [Primeros pasos](Primeros-pasos.md) |
| Aprender haciendo proyectos | [Tutoriales](Tutoriales.md) |
| Saber qué hace cada ventana y atajo | [Interfaz y controles](Interfaz-y-controles.md) |
| Algo concreto que no funciona | [Solución de problemas](Solucion-de-problemas.md) |

## Contenido

**Editor (Right Shift)**
1. [Interfaz y controles](Interfaz-y-controles.md): ventanas, cámara, atajos, temas, idiomas.
2. [Selección](Seleccion.md): Box, Magic, Lasso y Freehand Select, formas y exportación.
3. [Operaciones](Operaciones.md): rellenar, reemplazar, vaciar, gravedad, nieve, Autoshade...
4. [Portapapeles y Placement](Portapapeles-y-Placement.md): copiar, pegar, gizmo, giros libres, escala, schematics y blueprints.
5. [Pinceles de terreno](Pinceles.md): Painter, Smooth, Elevation, Sculpt, Slope, Fluid Ball, Biome Painter, Symmetry...
6. [Tool Masks y presets](Tool-Masks-y-Presets.md): restringir dónde actúa un pincel y guardar ajustes.
7. [Script Brush y Lua](Script-Brush-Lua.md): pinceles programables, referencia de la API y máscaras Lua.
8. [Modelling y Modify](Modelling-y-Modify.md): superficies a partir de puntos, copias en serie, revolve y twist.
9. [Planificación](Planificacion.md): anotaciones, imagen de referencia, regla y vistas.

**En el mundo, sin abrir el editor**
10. [Builder Mode](Builder-Mode.md): herramientas Move/Clone/Stack/Smear..., capabilities, hitboxes, hotbars y selector de color.
11. [Jugador y mundo](Jugador-y-mundo.md): vuelo, No Clip, reglas del mundo y opciones de vista.

**Referencia**
12. [Configuración y archivos](Configuracion-y-archivos.md): dónde se guarda todo.
13. [Solución de problemas](Solucion-de-problemas.md): fallos habituales, límites y preguntas frecuentes.
14. [Desarrollo](Desarrollo.md): compilar, tests, arquitectura y releases.

## Qué incluye, en una frase por área

- **32 herramientas** de editor (selección, pinceles, modelado, anotación, scripts).
- **Placement** con gizmo (mover, girar, escalar), vista previa con bloques reales, Smooth Scale y RotSprite.
- **Fluid Ball** con flujo calculado, **Biome Painter**, **Autoshade**, **Animated Rebuild**.
- **Script Brush** y **máscaras** en Lua (LuaJ, aislado).
- **Builder Mode**: capabilities (Tinker, Replace Mode, Infinite Reach, No Clip...), hitboxes ampliadas, malla de colisión.
- Interfaz en **español e inglés**.

## Licencia

Todos los derechos reservados (uso interno). Los componentes empaquetados (imgui-java, Dear ImGui, LuaJ) son MIT; ver `THIRD-PARTY-NOTICES.md`.
