# Changelog

## 1.0.0-rc1

Primera versión candidata a 1.0.0. Minecraft 1.12.2 con LiteLoader, solo cliente (un jugador). Se probó en el juego la 0.31.1 entera (arranque, idiomas, Placement, vista previa, Fluid Ball, máscaras Lua, Smart Surface, menú de Builder y estabilidad del editor); lo único que cambia después es el script por defecto de las reglas Lua y la licencia.

### Qué incluye
- Editor de mundo con interfaz Dear ImGui (**Right Shift**): herramientas de pincel, selección, modelado, fluidos, Script Brush en Lua, Tool Masks, presets, historial, schematics, Placement con gizmo y atajos configurables.
- Menú de Builder (**B**): herramientas Move, Clone, Stack, Smear, Extrude, Erase y Symmetry, y capabilities (Tinker, No Updates, Force Place, Replace Mode, Bulldozer, Infinite Reach, Fast Place, Angel Placement, No Clip), hitboxes de fluidos y expandidas, malla de colisión.
- Interfaz en **español e inglés** (View > Language).
- La lista completa de funciones está en el README.

### Cambios desde la 0.31.0
- **No Clip** ya no obliga a volar en cada tick: al activarlo empiezas a volar, pero puedes dejar de volar (doble salto) y vuelves a chocar con los bloques; solo atraviesa mientras vuelas.
- Corregido un cierre del juego al activar una capability del menú de Builder (por ejemplo Tinker): el estilo del texto se restauraba según el valor del checkbox *después* del clic.
- El script por defecto de una regla Lua en Tool Masks usaba mal `isSolid`; ahora es `return isSolid(getBlock(x, y, z)) and y > 64`.
- Se añaden `LICENSE` (todos los derechos reservados) y `THIRD-PARTY-NOTICES.md` (imgui-java, Dear ImGui y LuaJ, todos MIT), y van dentro del `.litemod` en `META-INF/`.

### Historial de versiones anteriores
- **0.31.0**: idiomas español e inglés; el mod pasa a llamarse LiteBuild (la configuración se migra sola desde `config/litbuild` o `config/axiomlite`).
- **0.30.x**: vista previa del Placement con bloques reales translúcidos; arreglos de la revisión de código; tests de JUnit.
- **0.29.0**: gizmo Local del Placement y Show Collision Mesh.
- **0.28.0**: gizmo completo del Placement (escala y anillos), Smooth Scale, RotSprite, Smart Surface, Fluid Ball con Quality y Fill Edges, Expand Hitboxes.
- **0.27.0**: gizmo del Placement, Merge Blocks, hitboxes de fluidos sólidas, flujo de Fluid Ball y máscaras Lua.
- **0.26.x**: Builder Mode, herramientas del editor que faltaban y menú de Builder.
- **0.25.0**: primer editor de mundo para 1.12.2.
