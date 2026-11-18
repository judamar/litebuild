# Desarrollo

Notas para quien compile o modifique LiteBuild.

## Requisitos

- **Java 8** (el wrapper de Gradle es 4.9, que no corre en Java 17).
- **Gradle 4.9 + ForgeGradle 2.3**: el proyecto se compila con los nombres de MCP (`snapshot_20170919`) y se reofusca al final.
- Una instancia de Prism para probar (en `gradle.properties`, `prismInstance`).

## Compilar e instalar

```sh
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk
./gradlew installMod     # compila, reofusca y copia mod-litebuild-*.litemod a la instancia
./gradlew build          # compila y pasa los tests; el jar queda en build/libs
./gradlew test           # solo los tests
```

`installMod` borra las versiones anteriores del `.litemod` y los jars con los nombres antiguos (`mod-litbuild-*`, `mod-axiomlite-*`). **No lo ejecutes con el juego abierto**: cambiaría el jar en uso.

## Tests

Pruebas JUnit 4 de las clases que no dependen de Minecraft (`src/test/java`): `FluidFlow`, `VoxelScale`, `VoxelRay`, `Euler`, `Mesh`, `Hull3D`, `LuaHighlight` e `I18n`. Lo que toca el juego (render, mixins, gizmo en pantalla) solo se prueba dentro de él, con la lista de comprobación en el juego de cada versión.

## Estructura

Paquete `dev.litebuild` (en `src/main/java`):

| Área | Clases clave |
|---|---|
| Entrada del mod | `LiteModLiteBuild` (versión, teclas, ticks, migración de config) |
| Interfaz | `EditorScreen` (ventanas, menús, herramientas), `ImGuiBackend` (renderizador propio para LWJGL 2), `BuilderMenuScreen`, `Txt` (envoltorios de texto que escapan `%`) |
| Edición | `EditEngine` (historial y ediciones), `Selection`, `Clipboard`, `Placement`, `BoxGizmo`, `PlacementPreview` |
| Pinceles | `Terrain`, `Brush`, `FluidFlow`, `FluidBall`, `Noise`, `Mesh`, `Modelling` |
| Scripts | `ScriptBrush`, `ScriptRunner` (LuaJ aislado), `Mask` / `MaskSpec`, `LuaHighlight` |
| Builder Mode | `BuilderMode`, `BuilderTools`, `NoClip`, `BlockHighlights`, `VoxelRay` |
| Idiomas | `I18n` + `src/main/resources/lang/en.tsv` |
| Mixins | `dev.litebuild.mixin`: `MixinMinecraft`, `MixinInventoryPlayer`, `MixinPlayerControllerMP`, `MixinEntity`, `MixinEntityPlayerSP`, `MixinBlockLiquid`, `MixinBlockFalling`, `MixinBlockFarmland` |

### Mixins

- Mixin 0.7 con un *refmap* escrito a mano (`mixins.litebuild.refmap.json`) y `-proc:none` (sin procesador de anotaciones).
- La configuración tiene `required: false`: si un mixin no se aplica, el juego sigue y la función simplemente no actúa.
- `MixinBlockLiquid` solo actúa en el hilo del cliente.

### Interfaz con imgui-java

- Usa siempre los envoltorios `Txt.*`: el texto de imgui-java pasa por `printf`, así que hay que escapar `%`.
- Los arrays de herramientas (nombres, iconos, teclas) están en paralelo: mantenlos alineados.
- Evita las lambdas (se usan clases anónimas).

### Idiomas

El código está escrito en **español**; el inglés sale del catálogo `lang/en.tsv` (`español<TAB>inglés`, con huecos `{0}`, `{1}` para mensajes con números). Lo que no esté en el catálogo se muestra tal cual. `I18nTest` comprueba el formato.

## Integración continua

En `.github/workflows`:

- **`ci.yml`:** en cada push y PR, con Java 8 (Temurin), cachea Gradle, ejecuta `./gradlew build` y sube el informe de tests y el `.litemod`.
- **`rebuild-desde-cero.yml`:** semanal, sin caché.
- **`release.yml`:** al subir una etiqueta (ver abajo).

## Releases

1. Sube `version` en `build.gradle` **y** en `LiteModLiteBuild.getVersion()`.
2. Añade la sección `## <versión>` al `CHANGELOG.md` (son las notas de la release).
3. Haz commit en `main` y súbelo.
4. Etiqueta: `git tag v<versión> && git push origin v<versión>`.

El workflow comprueba que la etiqueta es `v` + la versión de `build.gradle` y que el commit está en `main`, compila, pasa los tests y publica `mod-litebuild-<versión>-mc1.12.2.litemod` y `SHA256SUMS.txt`. Una versión con guion (`1.0.0-rc1`) se marca como *prerelease*. *Run workflow* a mano hace todo menos publicar.

## Reglas del proyecto

- **Reimplementación de sala limpia.** No decompilar ni examinar el `.jar` de Axiom (su EULA lo prohíbe): todo se escribe a partir de su documentación pública. No copiar código, recursos ni texturas de Axiom.
- **Licencia:** todos los derechos reservados (`LICENSE`); los componentes empaquetados (imgui-java, Dear ImGui, LuaJ) son MIT (`THIRD-PARTY-NOTICES.md`).
- No añadas al repositorio resúmenes ni traducciones de la documentación de Axiom: tiene sus propios derechos de autor. Si los tienes en local, una carpeta `docs/` queda ignorada por git.
