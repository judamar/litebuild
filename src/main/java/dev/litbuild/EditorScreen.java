package dev.litbuild;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiSliderFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Editor: pantalla que aloja la interfaz ImGui y reparte la entrada.
 * Botón izquierdo arrastrado = cámara, botón derecho = herramienta (o fijar el Placement),
 * botón central = copiar bloque, WASD/Espacio/Shift = volar. Right Shift o Escape cierran.
 */
public class EditorScreen extends GuiScreen {
    // Las primeras IMPLEMENTED herramientas funcionan; el resto es el plan (ver docs/).
    private static final String[] TOOLS = {
        "Box Select", "Magic Select", "Painter", "Noise Painter", "Freehand Draw", "Shape", "Smooth", "Melt",
        "Weld", "Extrude", "Ruler", "Elevation", "Distort", "Text", "Symmetry", "Roughen", "Shatter", "Rock",
        "Floodfill", "Path", "Script Brush", "Gradient Painter", "Freehand Select", "Stamp",
        "Lasso Select", "Sculpt Draw", "Slope", "Modify", "Biome Painter", "Annotation", "Fluid Ball", "Modelling"
    };
    private static final int IMPLEMENTED = 32;
    private static final int BOX = 0, MAGIC = 1, PAINTER = 2, NOISE = 3, DRAW = 4, SHAPE = 5, SMOOTH = 6, MELT = 7,
            WELD = 8, EXTRUDE = 9, RULER = 10, ELEVATION = 11, DISTORT = 12, TEXT = 13, SYMMETRY = 14,
            ROUGHEN = 15, SHATTER = 16, ROCK = 17, FLOODFILL = 18, PATH = 19, SCRIPT = 20, GRADIENT = 21, FSELECT = 22, STAMP = 23, LASSO = 24, SCULPT = 25, SLOPE = 26, MODIFY = 27, BIOME = 28, ANNOT = 29, FLUID = 30, MODEL = 31;
    /** Radio máximo de los pinceles de terreno (el desenfoque lee un cubo bastante mayor). */
    private static final int TERRAIN_MAX_RADIUS = 16;
    private static final EnumFacing[] DIRS = {EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH,
        EnumFacing.WEST, EnumFacing.EAST};
    private static final String[] DIR_LABELS = {"Up", "Down", "North", "South", "West", "East"};

    /** Una fila de la lista de bloques del Noise Painter. */
    private static final class NoiseRow {
        final ImString id;
        final int[] meta = {0};
        final int[] weight = {1};

        NoiseRow(String id) {
            this.id = new ImString(id, 128);
        }
    }

    private final ImBoolean showDemo = new ImBoolean(false);
    private final ImBoolean showSchem = new ImBoolean(false);
    private final ImBoolean showBlueprints = new ImBoolean(false);
    private final ImBoolean showPalette = new ImBoolean(true);
    private final ImBoolean showTextAnn = new ImBoolean(false);
    private final ImBoolean showStyle = new ImBoolean(false);
    private final ImBoolean showRef = new ImBoolean(false);
    private final ImString refSource = new ImString("", 512);
    private final float[] refOpacity = {1f};
    private final ImBoolean refNearest = new ImBoolean(false);
    private ImageCache.Entry refEntry;
    private final ImBoolean visualizeBiome = new ImBoolean(true);
    private final ImInt shadeLightsMode = new ImInt(0);
    private final ImBoolean shadeSun = new ImBoolean(true);
    private final List<float[]> shadeLights = new ArrayList<float[]>();
    private final float[] shadeLightIntensity = {1f}, shadeLightRadius = {24f}, shadeBounceStrength = {0.5f};
    private final int[] shadeBounces = {0};
    private final ImString shadeInclude = new ImString("", 128), shadeExclude = new ImString("", 128);
    private final ImBoolean showKeybinds = new ImBoolean(false);
    private final ImString presetName = new ImString("", 48);
    private final ImString keybindFilter = new ImString("", 32);
    private int lastTool, prevTool;
    private float savedGamma = -1f;
    private final ImInt shadeFrom = new ImInt(0);
    private final ImInt shadePalette = new ImInt(0);
    private final float[] shadeAz = {135f}, shadeEl = {45f}, shadeAmbient = {0.25f}, shadeAo = {0.5f},
            shadeDither = {0.3f}, shadeRadius = {32f};
    private final ImInt shadeSeed = new ImInt(1);
    private final ImInt fluidType = new ImInt(0);
    private final ImString paletteName = new ImString("", 32);
    private final ImString palPickId = new ImString("minecraft:stone", 128);
    private final int[] palPickMeta = {0};
    private String lastRecentKey = "";
    private final ImString bpSearch = new ImString("", 64);
    private final ImBoolean bpRecursive = new ImBoolean(false);
    private final ImInt bpTagPick = new ImInt(0);
    private String[] bpTagNames = {"(any tag)"};
    private String bpFolder = "";
    private boolean bpDirty = true;
    private boolean bpOpenDialog;
    private final ImString bpName = new ImString("", 64);
    private final ImString bpAuthors = new ImString("", 128);
    private final ImString bpSaveFolder = new ImString("", 128);
    private final ImString bpNewTag = new ImString("", 32);
    private List<String> bpTags = new ArrayList<String>();
    private boolean[] bpTagOn = new boolean[0];
    private int bpRot;
    private int bpThumbTex;
    private final ImBoolean showMasks = new ImBoolean(false);
    private final ImBoolean showHistory = new ImBoolean(false);
    private final ImString activeId = new ImString("minecraft:stone", 128);
    private final int[] activeMeta = {0};
    private final ImString replaceId = new ImString("minecraft:dirt", 128);
    private final int[] replaceMeta = {-1};
    private final int[] expandAmount = {1};
    private final ImBoolean shrink = new ImBoolean(false);
    private final int[] moveAmount = {1};
    private final int[] stackCount = {2};
    private final int[] stackGap = {0};
    private final ImInt shapeType = new ImInt(Shapes.SPHERE);
    private final int[] shapeSize = {9, 9, 9};
    private final ImBoolean shapeHollow = new ImBoolean(false);
    private final ImBoolean showAnalysis = new ImBoolean(true);
    private final List<NoiseRow> noiseRows = new ArrayList<NoiseRow>();
    private final float[] noiseScale = {8f};
    private final int[] noiseOctaves = {2};
    private final ImInt noiseSeed = new ImInt(1337);
    private final ImBoolean noise3d = new ImBoolean(true);
    private final ImBoolean noiseWhite = new ImBoolean(false);
    private final int[] smoothStrength = {2};
    private final int[] smoothRatio = {100};
    private final int[] meltRatio = {70};
    private final int[] weldRatio = {130};
    private final int[] extrudeCount = {1};
    private final ImInt extrudeCompare = new ImInt(MagicSelect.BLOCK);
    private final ImBoolean extrudeShrink = new ImBoolean(false);
    private final ImString schemName = new ImString("build", 64);
    private final ImInt themeUi = new ImInt(0);
    private final int[] fontUi = {16};
    private List<String> schemFiles = new ArrayList<String>();
    private IBlockState activeState;
    private int tool;
    private boolean dragging;
    private boolean stroking;
    private BlockPos lastStamp;
    private StatePicker spec;
    private MaskSpec maskSpec;
    private final ImInt elevMode = new ImInt(Terrain.RAISE);
    private final int[] elevAmount = {1};
    private final float[] distScale = {8f};
    private final float[] distDistance = {2f};
    private final ImInt distSeed = new ImInt(1337);
    private Noise distortNoise;
    private final ImString textStr = new ImString("Hola", 256);
    private final ImInt textFont = new ImInt(0);
    private final int[] textSize = {16};
    private final ImBoolean textBold = new ImBoolean(false);
    private final ImBoolean textItalic = new ImBoolean(false);
    private final ImBoolean textFlat = new ImBoolean(false);
    private final float[] roughAmount = {0.6f};
    private final ImInt roughSeed = new ImInt(1337);
    private final float[] shatterScale = {16f};
    private final float[] shatterWidth = {0.8f};
    private final ImInt shatterSeed = new ImInt(1337);
    private final ImBoolean shatterFill = new ImBoolean(false);
    private final int[] rockRadius = {4};
    private final float[] rockNoise = {0.5f};
    private final float[] rockScale = {5f};
    private final ImBoolean rockRandom = new ImBoolean(true);
    private final ImInt rockSeed = new ImInt(1337);
    private final int[] floodLimit = {5000};
    private final ImBoolean floodUp = new ImBoolean(false);
    private final ImBoolean floodDown = new ImBoolean(true);
    private final ImBoolean floodHoriz = new ImBoolean(true);
    private final ImBoolean floodCorners = new ImBoolean(false);
    private final ImString viewName = new ImString("Vista", 64);
    private final List<NoiseRow> gradRows = new ArrayList<NoiseRow>();
    private final ImInt gradDir = new ImInt(2);
    private final int[] gradLength = {24};
    private final ImInt gradInterp = new ImInt(GradientSpec.LINEAR);
    private final ImBoolean gradClamp = new ImBoolean(false);
    private final ImBoolean gradFromStart = new ImBoolean(true);
    private final int[] gradOrigin = new int[3];
    private final ImInt fsMode = new ImInt(Selection.ADD);
    private final ImBoolean fsSolid = new ImBoolean(true);
    private Set<Long> freeAcc;
    private final int[] stampSpacing = {8};
    private final int[] stampScatter = {4};
    private final int[] stampChance = {100};
    private final ImBoolean stampRotate = new ImBoolean(true);
    private final ImBoolean stampFlip = new ImBoolean(true);
    private final ImBoolean stampKeep = new ImBoolean(false);
    private final int[] stampDrop = {0};
    private Clipboard[] stampVariants;
    private java.util.Random stampRandom;
    private BlockPos strokeStart;
    private final ImInt formatPick = new ImInt(0);
    private final ImString filterId = new ImString("minecraft:stone", 128);
    private final int[] filterMeta = {-1};
    private final int[] selSigma = {2};
    private final float[] selThreshold = {0.5f};
    private final float[] selDistScale = {8f};
    private final float[] selDistAmount = {3f};
    private final ImInt selDistSeed = new ImInt(1);
    private final int[] selMove = {1};
    private final int[] snowLayers = {3};
    private final int[] snowRadius = {2};
    private String[] biomeNames;
    private int[] biomeIds;
    private final ImInt biomePick = new ImInt(0);
    private boolean biomeActive;
    private final float[] sculptStrength = {1f};
    private final ImBoolean sculptInvert = new ImBoolean(false);
    private final ImBoolean sculptMaskY = new ImBoolean(false);
    private final ImBoolean sculptDenoise = new ImBoolean(true);
    private BlockPos slopeFrom;
    private boolean slopeConeDone;
    private final ImString scriptCode = new ImString(ScriptBrush.EXAMPLES[0], 16384);
    private ScriptBrush.Spec scriptSpec;
    private String scriptCheck;
    private boolean settingsLoaded;
    private boolean needLayout;

    public EditorScreen() {
        noiseRows.add(new NoiseRow("minecraft:stone"));
        noiseRows.add(new NoiseRow("minecraft:cobblestone"));
        gradRows.add(new NoiseRow("minecraft:stone"));
        gradRows.add(new NoiseRow("minecraft:cobblestone"));
        gradRows.add(new NoiseRow("minecraft:gravel"));
        Mask.rules.add(new Mask.Rule());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        schemFiles = Schematics.list();
        Icons.clear();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        Blueprints.free(bpThumbTex);
        bpThumbTex = 0;
        if (savedGamma >= 0f) {
            mc.gameSettings.gammaSetting = savedGamma;
            savedGamma = -1f;
        }
        BiomeView.visualize = false;
        if (stroking) {
            endStroke();
        }
        Brush.preview = false;
        BoxGizmo.reset();
        Targeting.hover = null;
        KeyBinding.unPressAllKeys();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Cada tick: los controles de movimiento de Minecraft siguen funcionando con la pantalla abierta. */
    @Override
    public void updateScreen() {
        boolean allowed = !ImGuiBackend.isReady() || !ImGui.getIO().getWantTextInput();
        KeyBinding[] keys = {
            mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack, mc.gameSettings.keyBindLeft,
            mc.gameSettings.keyBindRight, mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak,
            mc.gameSettings.keyBindSprint
        };
        for (KeyBinding k : keys) {
            int code = k.getKeyCode();
            KeyBinding.setKeyBindState(code, allowed && code > 0 && Keyboard.isKeyDown(code));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ImGuiBackend.frame(new Runnable() {
            @Override
            public void run() {
                ui();
            }
        });
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        int key = Keyboard.getEventKey();
        boolean down = Keyboard.getEventKeyState();
        ImGuiBackend.key(key, down, Keyboard.getEventCharacter());
        if (down && (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RSHIFT)
                && !ImGui.getIO().getWantTextInput() && Keybinds.capturing == null) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        ImGuiBackend.mouse(Mouse.getEventX(), Mouse.getEventY(), Mouse.getEventButton(),
                Mouse.getEventButtonState(), Mouse.getEventDWheel());
    }

    // ---- Ayudas de interfaz ------------------------------------------------------------------

    /** Texto de ayuda atenuado y con salto de línea. */
    private static void hint(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.52f, 0.55f, 0.62f, 1f);
        Txt.wrapped(text);
        ImGui.popStyleColor();
    }

    private static void tip(String text) {
        if (ImGui.isItemHovered()) {
            Txt.tooltip(text);
        }
    }

    /** Fila de botones del mismo ancho que ocupa todo el ancho disponible; devuelve el pulsado o -1. */
    private static int buttonRow(String... labels) {
        float spacing = ImGui.getStyle().getItemSpacingX();
        float w = (ImGui.getContentRegionAvailX() - spacing * (labels.length - 1)) / labels.length;
        int clicked = -1;
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            if (ImGui.button(labels[i], w, 0)) {
                clicked = i;
            }
        }
        return clicked;
    }

    private static String[] suffixed(String[] labels, String suffix) {
        String[] out = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            out[i] = labels[i] + "##" + suffix;
        }
        return out;
    }

    // ---- UI ------------------------------------------------------------------------------

    private void ui() {
        ImGuiIO io = ImGui.getIO();
        if (!settingsLoaded) {
            settingsLoaded = true;
            themeUi.set(UiSettings.theme);
            fontUi[0] = Math.round(UiSettings.fontSize);
            needLayout = UiSettings.layout < UiSettings.LAYOUT_VERSION;
        }
        resolveActive();
        trackRecent();
        menuBar();
        float bar = ImGui.getFrameHeight() + 2 * ImGui.getStyle().getWindowPaddingY();
        int dock = dockHost(bar);
        if (needLayout) {
            buildLayout(dock, bar);
        }
        toolsWindow();
        toolOptionsWindow();
        selectionWindow();
        if (Selection.active) {
            transformWindow();
        }
        activeBlockWindow();
        clipboardWindow();
        inventoryWindow();
        viewsWindow();
        if (showSchem.get()) {
            schematicsWindow();
        }
        if (showPalette.get()) {
            paletteWindow();
        }
        if (showTextAnn.get()) {
            textAnnotationsWindow();
        }
        if (tool != prevTool) {
            lastTool = prevTool;
            prevTool = tool;
        }
        if (showKeybinds.get()) {
            keybindsWindow();
        }
        if (showRef.get()) {
            referenceWindow();
        }
        BiomeView.visualize = tool == BIOME && visualizeBiome.get();
        FluidOpacity.tick(UiSettings.fluidOpacity);
        if (showStyle.get()) {
            if (ImGui.begin("Style Editor", showStyle)) {
                ImGui.showStyleEditor();
            }
            ImGui.end();
        }
        applyBrightness();
        drawAnnotationTexts();
        if (showBlueprints.get()) {
            blueprintsWindow();
        }
        blueprintDialog();
        if (showMasks.get()) {
            masksWindow();
        }
        if (showHistory.get()) {
            historyWindow();
        }
        if (Placement.active) {
            placementWindow();
        }
        WorldPanels.targetInfo();
        WorldPanels.worldProperties();
        if (EditEngine.analysis != null && showAnalysis.get()) {
            WorldPanels.analysis(showAnalysis);
        }
        if (showDemo.get()) {
            ImGui.showDemoWindow(showDemo);
        }
        BlockPicker.window();
        statusBar(bar);
        shortcuts(io);
        viewport(io);
    }

    /** Ventana transparente que aloja el docking y deja libre el centro (el mundo) y la barra de estado. */
    private int dockHost(float barHeight) {
        ImGuiViewport vp = ImGui.getMainViewport();
        ImGui.setNextWindowPos(vp.getWorkPosX(), vp.getWorkPosY());
        ImGui.setNextWindowSize(vp.getWorkSizeX(), vp.getWorkSizeY() - barHeight);
        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoBackground;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.begin("##host", flags);
        ImGui.popStyleVar(3);
        int id = ImGui.getID("MainDock");
        ImGui.dockSpace(id, 0f, 0f, ImGuiDockNodeFlags.PassthruCentralNode);
        ImGui.end();
        return id;
    }

    /** Diseño por defecto: herramientas a la izquierda, opciones arriba a la derecha, información abajo. */
    private void buildLayout(int id, float barHeight) {
        needLayout = false;
        ImGuiViewport vp = ImGui.getMainViewport();
        float w = vp.getWorkSizeX();
        float h = vp.getWorkSizeY() - barHeight;
        float scale = UiSettings.fontSize / 16f;
        float leftRatio = Math.max(0.15f, Math.min(0.32f, 340f * scale / w));
        float rightRatio = Math.max(0.18f, Math.min(0.36f, 400f * scale / (w * (1f - leftRatio))));
        imgui.internal.ImGui.dockBuilderRemoveNode(id);
        imgui.internal.ImGui.dockBuilderAddNode(id, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
        imgui.internal.ImGui.dockBuilderSetNodeSize(id, w, h);
        ImInt left = new ImInt();
        ImInt rest = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(id, ImGuiDir.Left, leftRatio, left, rest);
        ImInt right = new ImInt();
        ImInt center = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(rest.get(), ImGuiDir.Right, rightRatio, right, center);
        ImInt top = new ImInt();
        ImInt bottom = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(right.get(), ImGuiDir.Up, 0.55f, top, bottom);
        for (String n : new String[] {"Tools", "Selection", "Active Block", "Palette", "Clipboard", "Inventory", "Move / Stack"}) {
            imgui.internal.ImGui.dockBuilderDockWindow(n, left.get());
        }
        for (String n : new String[] {"Tool Options", "Tool Masks", "Placement"}) {
            imgui.internal.ImGui.dockBuilderDockWindow(n, top.get());
        }
        for (String n : new String[] {"Target Info", "World Properties", "Views", "History", "Schematics", "Analyze", "Keybinds"}) {
            imgui.internal.ImGui.dockBuilderDockWindow(n, bottom.get());
        }
        imgui.internal.ImGui.dockBuilderFinish(id);
        UiSettings.layout = UiSettings.LAYOUT_VERSION;
        UiSettings.save();
    }

    private void statusBar(float height) {
        ImGuiViewport vp = ImGui.getMainViewport();
        ImGui.setNextWindowPos(vp.getWorkPosX(), vp.getWorkPosY() + vp.getWorkSizeY() - height);
        ImGui.setNextWindowSize(vp.getWorkSizeX(), height);
        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoFocusOnAppearing | ImGuiWindowFlags.NoNavFocus;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        if (ImGui.begin("##status", flags)) {
            Txt.text(TOOLS[tool]);
            ImGui.sameLine();
            Txt.disabled("|");
            ImGui.sameLine();
            Txt.text(Selection.active
                    ? Selection.size(0) + " x " + Selection.size(1) + " x " + Selection.size(2)
                        + (Selection.mask != null ? " (máscara)" : "")
                    : "Sin selección");
            ImGui.sameLine();
            Txt.disabled("|");
            ImGui.sameLine();
            String fps = String.format(Locale.ROOT, "%.0f FPS", ImGui.getIO().getFramerate());
            float fpsX = ImGui.getWindowWidth() - ImGui.calcTextSizeX(fps) - ImGui.getStyle().getWindowPaddingX();
            String msg = EditEngine.status;
            float avail = fpsX - ImGui.getCursorPosX() - 2 * ImGui.getStyle().getItemSpacingX();
            String shown = msg;
            while (shown.length() > 4 && ImGui.calcTextSizeX(shown) > avail) {
                shown = shown.substring(0, shown.length() - 1);
            }
            Txt.text(shown.length() < msg.length() ? shown + "..." : shown);
            if (shown.length() < msg.length()) {
                tip(msg);
            }
            ImGui.sameLine(fpsX);
            Txt.disabled(fps);
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void menuBar() {
        if (!ImGui.beginMainMenuBar()) {
            return;
        }
        if (ImGui.beginMenu("File")) {
            ImGui.menuItem("Schematics...", "", showSchem);
            ImGui.menuItem("Blueprint Browser...", "", showBlueprints);
            ImGui.menuItem("Open Reference Image...", "", showRef);
            if (ImGui.menuItem("Save Blueprint...", "Ctrl+P", false, Clipboard.current != null)) {
                bpOpenDialog = true;
            }
            ImGui.separator();
            if (ImGui.menuItem("Export Selection as Sponge (.schem)", "", false, Selection.active)) {
                exportSelection(".schem");
            }
            if (ImGui.menuItem("Export Selection as Structure NBT", "", false, Selection.active)) {
                exportSelection(".nbt");
            }
            if (ImGui.menuItem("Save Selection as CSV", "", false, Selection.active)) {
                exportSelection(".csv");
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Edit")) {
            String u = EditEngine.undoLabel;
            String r = EditEngine.redoLabel;
            if (ImGui.menuItem(u == null ? "Undo" : "Undo: " + u, "Ctrl+Z", false, u != null)) {
                EditEngine.undo();
            }
            if (ImGui.menuItem(r == null ? "Redo" : "Redo: " + r, "Ctrl+Y", false, r != null)) {
                EditEngine.redo();
            }
            ImGui.separator();
            if (ImGui.menuItem("Cut", "Ctrl+X", false, Selection.active)) {
                EditEngine.copy(true);
            }
            if (ImGui.menuItem("Copy", "Ctrl+C", false, Selection.active)) {
                EditEngine.copy(false);
            }
            if (ImGui.menuItem("Paste", "Ctrl+V", false, Clipboard.current != null && !Placement.active)) {
                startPaste();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Select")) {
            if (ImGui.menuItem("Clear", "", false, Selection.active)) {
                Selection.clear();
            }
            if (ImGui.menuItem("Convex Hull", "", false, Selection.active && Selection.mask != null)) {
                SelectionOps.convexHull();
            }
            if (ImGui.menuItem("Bounding Box", "", false, Selection.active && Selection.mask != null)) {
                SelectionOps.boundingBox();
            }
            if (ImGui.menuItem("Clear Ruler", "", false, !Ruler.points.isEmpty())) {
                Ruler.clear();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Operations")) {
            if (ImGui.menuItem("Fill", "Ctrl+F", false, Selection.active)) {
                fill();
            }
            if (ImGui.menuItem("Fill Outline", "", false, Selection.active)) {
                fillMode("Fill Outline", EditEngine.OUTLINE);
            }
            if (ImGui.menuItem("Fill Walls", "", false, Selection.active)) {
                fillMode("Fill Walls", EditEngine.WALLS);
            }
            if (ImGui.menuItem("Fill Shell", "", false, Selection.active)) {
                fillMode("Fill Shell", EditEngine.SHELL);
            }
            if (ImGui.menuItem("Fill Top", "", false, Selection.active)) {
                fillMode("Fill Top", EditEngine.TOP);
            }
            if (ImGui.menuItem("Fill Bottom", "", false, Selection.active)) {
                fillMode("Fill Bottom", EditEngine.BOTTOM);
            }
            ImGui.separator();
            if (ImGui.menuItem("Replace", "", false, Selection.active)) {
                replace();
            }
            if (ImGui.menuItem("Hollow", "", false, Selection.active)) {
                EditEngine.fill("Hollow", Blocks.AIR.getDefaultState(), EditEngine.HOLLOW);
            }
            if (ImGui.menuItem("Simulate Gravity", "", false, Selection.active)) {
                EditEngine.gravity();
            }
            if (ImGui.menuItem("Fill Gaps", "", false, Selection.active)) {
                if (activeState == null) {
                    EditEngine.status = "El bloque activo no es válido";
                } else {
                    EditEngine.fillGaps(activeState);
                }
            }
            if (ImGui.menuItem("Type Replace", "", false, Selection.active)) {
                if (activeState == null) {
                    EditEngine.status = "El bloque activo no es válido";
                } else {
                    EditEngine.typeReplace(activeState);
                }
            }
            if (ImGui.menuItem("Fill Nearest", "", false, Selection.active)) {
                EditEngine.fillNearest();
            }
            if (ImGui.menuItem("Set Biome", "", false, Selection.active)) {
                setBiomeSelection();
            }
            if (ImGui.menuItem("Animated Rebuild", "", false, Selection.active && !AnimatedRebuild.active())) {
                EditEngine.animatedRebuild();
            }
            if (ImGui.menuItem("Cancel Animated Rebuild", "", false, AnimatedRebuild.active())) {
                AnimatedRebuild.cancel();
            }
            if (ImGui.menuItem("Autoshade", "", false, Selection.active)) {
                autoshade();
            }
            if (ImGui.menuItem("Waterlog", "", false, Selection.active)) {
                EditEngine.waterlog();
            }
            if (ImGui.menuItem("Generate Colour Field", "", false, Selection.active)) {
                colourField();
            }
            if (ImGui.menuItem("Smoothsnow", "", false, Selection.active)) {
                EditEngine.smoothSnow(snowLayers[0], snowRadius[0]);
            }
            if (ImGui.menuItem("Trigger Updates", "", false, Selection.active)) {
                EditEngine.triggerUpdates();
            }
            if (ImGui.menuItem("Drain", "", false, Selection.active)) {
                EditEngine.drain();
            }
            if (ImGui.menuItem("Analyze", "", false, Selection.active)) {
                showAnalysis.set(true);
                EditEngine.analyze();
            }
            if (ImGui.menuItem("Delete", "Del", false, Selection.active)) {
                deleteSelection();
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("View")) {
            ImGui.setNextItemWidth(160);
            if (ImGui.combo("Theme", themeUi, Theme.NAMES)) {
                UiSettings.theme = themeUi.get();
                Theme.apply(UiSettings.theme);
                UiSettings.save();
            }
            ImGui.setNextItemWidth(160);
            ImGui.sliderInt("UI size", fontUi, 12, 28);
            if (ImGui.isItemDeactivatedAfterEdit()) {
                UiSettings.fontSize = fontUi[0];
                ImGuiBackend.setFontSize(fontUi[0]);
                UiSettings.save();
            }
            ImGui.menuItem("Show Biomes", "", BiomeView.show);
            float[] fo = {UiSettings.fluidOpacity < 0 ? 0.7f : UiSettings.fluidOpacity};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Fluid Opacity", fo, 0f, 1f, "%.2f")) {
                UiSettings.fluidOpacity = fo[0];
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                UiSettings.save();
            }
            if (UiSettings.fluidOpacity >= 0 && ImGui.menuItem("Reset Fluid Opacity")) {
                UiSettings.fluidOpacity = -1f;
                UiSettings.save();
            }
            float[] mb = {UiSettings.minBrightness};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Min Brightness", mb, 0f, 1f, "%.2f")) {
                UiSettings.minBrightness = mb[0];
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                UiSettings.save();
            }
            ImGui.separator();
            if (ImGui.menuItem("Show Annotations", "", Annotations.show)) {
                Annotations.save();
            }
            if (ImGui.menuItem("Reset layout")) {
                needLayout = true;
            }
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Window")) {
            ImGui.menuItem("Tool Masks", "", showMasks);
            ImGui.menuItem("History", "", showHistory);
            ImGui.menuItem("Schematics", "", showSchem);
            ImGui.menuItem("Blueprint Browser", "", showBlueprints);
            ImGui.menuItem("Palette", "", showPalette);
            ImGui.menuItem("Reference Image", "", showRef);
            ImGui.menuItem("Text Annotations", "", showTextAnn);
            ImGui.menuItem("Style Editor", "", showStyle);
            ImGui.menuItem("Keybinds", "", showKeybinds);
            ImGui.menuItem("ImGui demo", "", showDemo);
            ImGui.endMenu();
        }
        ImGui.endMainMenuBar();
    }

    // ---- Ventanas ----------------------------------------------------------------------------

    private static final String[][] TOOL_GROUPS = {
        {"Selection", "0", "1", "22", "24"},
        {"Paint", "2", "3", "21", "20", "28"},
        {"Draw", "4", "5", "13", "17", "19", "23", "25", "31"},
        {"Terrain", "6", "7", "8", "9", "11", "12", "15", "16", "26", "27"},
        {"Fluid", "18", "30"},
        {"Utility", "10", "14", "29"},
    };
    private static final String[] TOOL_KEYS = {"B", "M", "P", "O", "G", "", "U", "K", "J", "Z", "", "E", "", "", "",
        "", "", "H", "", "", "", "", "N", "", "L", "", "", "", "", "", "", ""};

    private void toolsWindow() {
        if (ImGui.begin("Tools")) {
            float size = ImGui.getFrameHeight() * 2.1f;
            for (String[] group : TOOL_GROUPS) {
                ImGui.separatorText(group[0]);
                float avail = ImGui.getContentRegionAvailX();
                float spacing = ImGui.getStyle().getItemSpacingX();
                int perRow = Math.max(1, (int) ((avail + spacing) / (size + spacing)));
                for (int g = 1; g < group.length; g++) {
                    int i = Integer.parseInt(group[g]);
                    if ((g - 1) % perRow != 0) {
                        ImGui.sameLine();
                    }
                    Keybinds.init(TOOLS, TOOL_KEYS);
                    String key = Keybinds.toolKey(i);
                    if (iconButton("tool" + i, TOOLS[i], size, tool == i, key.isEmpty() ? TOOLS[i] : TOOLS[i] + " (" + key + ")")) {
                        tool = i;
                    }
                }
            }
            if (IMPLEMENTED < TOOLS.length && ImGui.collapsingHeader("Planned")) {
                for (int i = IMPLEMENTED; i < TOOLS.length; i++) {
                    ImGui.selectable(TOOLS[i] + "##tool" + i, false, ImGuiSelectableFlags.Disabled);
                }
            }
        }
        ImGui.end();
    }

    /** Botón cuadrado con un icono vectorial; destaca si está seleccionado y muestra un tooltip. */
    private static boolean iconButton(String id, String icon, float size, boolean selected, String tooltip) {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, size, size);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        int bg = selected ? ImGui.getColorU32(ImGuiCol.Button)
                : ImGui.getColorU32(hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg);
        dl.addRectFilled(x, y, x + size, y + size, bg, 6f);
        if (selected) {
            dl.addRect(x, y, x + size, y + size, ImGui.getColorU32(ImGuiCol.SliderGrab), 6f, 0, 2f);
        }
        float pad = size * 0.16f;
        ToolIcons.draw(dl, icon, x + pad, y + pad, size - 2 * pad, ImGui.getColorU32(ImGuiCol.Text),
                ImGui.getColorU32(ImGuiCol.SliderGrab));
        if (hovered) {
            Txt.tooltip(tooltip);
        }
        return clicked;
    }

    /** Fila de iconos para elegir entre varias opciones; devuelve la elegida (o la actual). */
    private static int iconRow(String id, String[] names, int current) {
        float size = ImGui.getFrameHeight() * 1.9f;
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            if (iconButton(id + i, names[i], size, current == i, names[i])) {
                current = i;
            }
        }
        return current;
    }

    private void brushShapeIcons() {
        Brush.shape.set(iconRow("bshape", Brush.SHAPES, Brush.shape.get()));
        ImGui.sameLine();
        Txt.text(Brush.SHAPES[Brush.shape.get()]);
    }

    private void shapeIcons() {
        shapeType.set(iconRow("shape", Shapes.NAMES, shapeType.get()));
        Txt.text(Shapes.NAMES[shapeType.get()]);
    }

    /** Campo de bloque: icono (abre el selector) y, al lado, el id y la meta editables a mano. */
    private static void blockField(String key, ImString id, int[] meta, int minMeta) {
        BlockPicker.button(key, id, meta);
        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputText("##id" + key, id);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.sliderInt("##meta" + key, meta, minMeta, 15);
        ImGui.endGroup();
    }

    private void toolOptionsWindow() {
        if (ImGui.begin("Tool Options")) {
            Txt.text(TOOLS[tool]);
            ImGui.separator();
            if (tool == PAINTER || tool == DRAW || tool == NOISE) {
                ImGui.sliderInt("Radius", Brush.radius, 1, 32);
                brushShapeIcons();
                if (tool != DRAW) {
                    ImGui.checkbox("Mask Surface", Brush.maskSurface);
                    tip("Solo cambia los bloques que tocan algo no sólido.");
                }
                if (tool == NOISE) {
                    noiseOptions();
                } else if (tool == PAINTER) {
                    hint("Mantén el botón derecho para pintar con el Active Block solo los bloques sólidos.");
                } else {
                    hint("Mantén el botón derecho para colocar el Active Block en todo el volumen del pincel.");
                }
            } else if (tool == SMOOTH || tool == MELT || tool == WELD) {
                terrainOptions();
            } else if (tool == ELEVATION) {
                elevationOptions();
            } else if (tool == DISTORT) {
                distortOptions();
            } else if (tool == TEXT) {
                textOptions();
            } else if (tool == SYMMETRY) {
                symmetryOptions();
            } else if (tool == ROUGHEN) {
                roughenOptions();
            } else if (tool == SHATTER) {
                shatterOptions();
            } else if (tool == ROCK) {
                rockOptions();
            } else if (tool == FLOODFILL) {
                floodOptions();
            } else if (tool == PATH) {
                pathOptions();
            } else if (tool == SCRIPT) {
                scriptOptions();
            } else if (tool == GRADIENT) {
                gradientOptions();
            } else if (tool == LASSO) {
                lassoOptions();
            } else if (tool == MODIFY) {
                modifyOptions();
            } else if (tool == BIOME) {
                biomeOptions();
            } else if (tool == ANNOT) {
                annotationOptions();
            } else if (tool == FLUID) {
                fluidOptions();
            } else if (tool == MODEL) {
                modellingOptions();
            } else if (tool == SCULPT) {
                sculptOptions();
            } else if (tool == SLOPE) {
                slopeOptions();
            } else if (tool == FSELECT) {
                freeSelectOptions();
            } else if (tool == STAMP) {
                stampOptions();
            } else if (tool == EXTRUDE) {
                ImGui.sliderInt("Count", extrudeCount, 1, 16);
                ImGui.combo("Compare", extrudeCompare, MagicSelect.COMPARE);
                ImGui.checkbox("Shrink", extrudeShrink);
                hint("Botón derecho sobre una cara: la superficie de bloques iguales crece hacia fuera. Con Shrink (o Shift) se come capas.");
            } else if (tool == RULER) {
                rulerOptions();
            } else if (tool == SHAPE) {
                shapeIcons();
                ImGui.sliderInt3("Size (X Y Z)", shapeSize, 1, 128);
                ImGui.checkbox("Hollow", shapeHollow);
                if (ImGui.button("Create Placement", ImGui.getContentRegionAvailX(), 0)) {
                    createShape();
                }
                hint("Crea la forma con el Active Block como Placement: se coloca como un pegado.");
            } else if (tool == MAGIC) {
                ImGui.combo("Compare", MagicSelect.compare, MagicSelect.COMPARE);
                ImGui.sliderInt("Limit", MagicSelect.limit, 1, (int) EditEngine.MAX_BLOCKS);
                ImGui.sliderInt("Range", MagicSelect.range, 1, 4);
                ImGui.checkbox("Corners", MagicSelect.corners);
                ImGui.sameLine();
                ImGui.checkbox("Surface Only", MagicSelect.surfaceOnly);
                ImGui.combo("Mode", MagicSelect.op, MagicSelect.OPS);
                hint("Botón derecho sobre un bloque: selecciona los conectados que cumplan la comparación.");
            } else {
                hint("Botón derecho: clic en un bloque y arrastra hasta otro. Después arrastra con el botón izquierdo los cubos de color de las caras para estirar o encoger la caja.");
            }
            presetBlock(TOOLS[tool], presetHolders(tool));
        }
        ImGui.end();
    }

    private void scriptOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, TERRAIN_MAX_RADIUS);
        brushShapeIcons();
        ImGui.separatorText("Script (Lua)");
        int ex = buttonRow(suffixed(ScriptBrush.EXAMPLE_NAMES, "ex"));
        if (ex >= 0) {
            scriptCode.set(ScriptBrush.EXAMPLES[ex]);
            scriptCheck = null;
        }
        ImGui.inputTextMultiline("##script", scriptCode, ImGui.getContentRegionAvailX(), ImGui.getFrameHeight() * 11);
        java.util.List<ScriptBrush.Var> vars = ScriptBrush.vars(scriptCode.get());
        if (!vars.isEmpty()) {
            ImGui.separatorText("Variables");
        }
        for (ScriptBrush.Var v : vars) {
            Object val = v.value();
            ImGui.pushID(v.key());
            if (val instanceof int[]) {
                ImGui.sliderInt(v.title, (int[]) val, v.min(), v.max());
            } else if (val instanceof float[]) {
                ImGui.sliderFloat(v.title, (float[]) val, v.minF(), v.maxF());
            } else if (val instanceof ImBoolean) {
                ImGui.checkbox(v.title, (ImBoolean) val);
            } else {
                Object[] pair = (Object[]) val;
                Txt.text(v.title);
                blockField("var", (ImString) pair[0], (int[]) pair[1], 0);
            }
            ImGui.popID();
        }
        if (ImGui.button("Check syntax", ImGui.getContentRegionAvailX(), 0)) {
            String error = ScriptBrush.Spec.check(scriptCode.get());
            scriptCheck = error == null ? "Sintaxis correcta" : error;
        }
        if (scriptCheck != null) {
            hint(scriptCheck);
        }
        hint("Mantén el botón derecho. El script corre por cada bloque del pincel con sus coordenadas x, y, z; si devuelve un id de bloque (blocks.stone...) se coloca ahí. Con $once$ corre una vez por sello.");
        if (ImGui.collapsingHeader("Reference")) {
            hint("blocks.nombre (nombre de 1.12, o nombre:meta) · getBlock(x,y,z) · getBlockState(x,y,z) · setBlock(x,y,z,id) · isSolid(id) · getHighestBlockYAt(x,z) · withBlockProperty(id,'prop=valor') · getBlockProperty(id,'prop') · isBlockTagged(id,'logs') · getBlockRGB(id) · findClosestBlockToRGB(rgb,flags,índice) · getSimplexNoise(x,y,z,semilla) · getVoroniEdgeNoise(x,y,z,semilla) · print(...)");
            hint("Variables: $int(Título, valor, mín, máx)$ · $float(...)$ · $boolean(Título, valor)$ · $blockState(Título, minecraft:stone)$ · $once$. Sin io, os ni load; cada sello tiene un presupuesto de instrucciones.");
        }
    }

    private void roughenOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, TERRAIN_MAX_RADIUS);
        brushShapeIcons();
        ImGui.sliderInt("Strength", smoothStrength, 1, 4);
        ImGui.sliderFloat("Roughness", roughAmount, 0.1f, 2f);
        ImGui.inputInt("Seed", roughSeed);
        hint("Mantén el botón derecho: deja el terreno dentado (lo contrario de Smooth) conservando el número de bloques.");
    }

    private void shatterOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, TERRAIN_MAX_RADIUS);
        brushShapeIcons();
        ImGui.sliderFloat("Scale", shatterScale, 4f, 64f);
        ImGui.sliderFloat("Width", shatterWidth, 0.5f, 4f);
        ImGui.inputInt("Seed", shatterSeed);
        ImGui.checkbox("Fill cracks with Active Block", shatterFill);
        hint("Mantén el botón derecho: abre grietas de tipo Voronoi en los bloques sólidos (o las rellena con el Active Block). Scale es la distancia típica entre grietas y Width su grosor; con Scale pequeña y Width grande se rompe casi todo.");
    }

    private void rockOptions() {
        ImGui.sliderInt("Radius", rockRadius, 1, 16);
        ImGui.sliderFloat("Noisiness", rockNoise, 0f, 1.5f);
        ImGui.sliderFloat("Noise scale", rockScale, 2f, 16f);
        ImGui.checkbox("Random seed", rockRandom);
        if (!rockRandom.get()) {
            ImGui.inputInt("Seed", rockSeed);
        }
        hint("Botón derecho: coloca una roca irregular con el Active Block, medio hundida en el terreno.");
    }

    private void floodOptions() {
        ImGui.sliderInt("Limit", floodLimit, 1, (int) EditEngine.MAX_BLOCKS);
        ImGui.checkbox("Horizontal", floodHoriz);
        ImGui.sameLine();
        ImGui.checkbox("Down", floodDown);
        ImGui.sameLine();
        ImGui.checkbox("Up", floodUp);
        ImGui.checkbox("Corners", floodCorners);
        hint("Botón derecho sobre una cara: rellena con el Active Block el hueco conectado (aire, agua, plantas...) avanzando en las direcciones marcadas.");
    }

    private void pathOptions() {
        ImGui.combo("Type", PathTool.type, PathTool.TYPES);
        ImGui.sliderInt("Radius", PathTool.radius, 0, 8);
        ImGui.checkbox("Looped", PathTool.looped);
        ImGui.sameLine();
        ImGui.checkbox("Keep Existing", PathTool.keepExisting);
        Txt.text("Nodos: " + PathTool.nodes.size() + "   Longitud: "
                + String.format(Locale.ROOT, "%.1f", PathTool.length()));
        if (ImGui.collapsingHeader("Nodes")) {
            int remove = -1;
            for (int i = 0; i < PathTool.nodes.size(); i++) {
                BlockPos p = PathTool.nodes.get(i);
                ImGui.pushID(i);
                if (ImGui.button("X")) {
                    remove = i;
                }
                ImGui.sameLine();
                Txt.text((i + 1) + ": " + p.getX() + ", " + p.getY() + ", " + p.getZ());
                ImGui.popID();
            }
            if (remove >= 0) {
                PathTool.nodes.remove(remove);
            }
        }
        int c = buttonRow("Apply", "Remove last", "Clear");
        if (c == 0) {
            applyPath();
        } else if (c == 1 && !PathTool.nodes.isEmpty()) {
            PathTool.nodes.remove(PathTool.nodes.size() - 1);
        } else if (c == 2) {
            PathTool.nodes.clear();
        }
        hint("Botón derecho: añade un nodo. Line une los nodos con rectas, Catmull-Rom con una curva que pasa por todos y Bezier con una curva que solo toca el primero y el último. Apply la coloca con el Active Block.");
    }

    private void elevationOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, TERRAIN_MAX_RADIUS);
        ImGui.combo("Mode", elevMode, Terrain.ELEVATION_MODES);
        ImGui.sliderInt("Amount", elevAmount, 1, 6);
        hint("Mantén el botón derecho y arrastra: sube, baja o aplana el terreno por columnas. El efecto es máximo en el centro y se acumula mientras te mueves.");
    }

    private void distortOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, TERRAIN_MAX_RADIUS);
        brushShapeIcons();
        ImGui.sliderFloat("Scale", distScale, 2f, 32f);
        ImGui.sliderFloat("Distance", distDistance, 0.5f, 8f);
        ImGui.inputInt("Seed", distSeed);
        hint("Mantén el botón derecho: deforma el terreno con ruido. Scale es el tamaño de las ondulaciones y Distance cuánto desplaza.");
    }

    private void textOptions() {
        ImGui.inputText("Text", textStr);
        ImGui.combo("Font", textFont, TextTool.FONTS);
        ImGui.sliderInt("Size", textSize, 6, 64);
        ImGui.checkbox("Bold", textBold);
        ImGui.sameLine();
        ImGui.checkbox("Italic", textItalic);
        ImGui.sameLine();
        ImGui.checkbox("Flat", textFlat);
        if (ImGui.button("Create Placement", ImGui.getContentRegionAvailX(), 0)) {
            createText();
        }
        hint("Escribe el texto con el Active Block. De pie se lee desde el sur (Ctrl+R lo gira); con Flat queda tumbado, legible desde arriba.");
    }

    private void symmetryOptions() {
        ImGui.checkbox("Enabled", Symmetry.enabled);
        if (!Symmetry.nodeSet) {
            hint("Botón derecho sobre el mundo para colocar el nodo: queda en el punto exacto que apuntas (cara, arista o vértice).");
        } else if (ImGui.inputInt3("Node (half blocks)", Symmetry.node)) {
            Symmetry.nodeSet = true;
        }
        ImGui.checkbox("Mirror X", Symmetry.mirrorX);
        ImGui.sameLine();
        ImGui.checkbox("Y", Symmetry.mirrorY);
        ImGui.sameLine();
        ImGui.checkbox("Z", Symmetry.mirrorZ);
        ImGui.checkbox("Rotate 180", Symmetry.rotate180);
        ImGui.sameLine();
        if (Symmetry.canRotate90()) {
            ImGui.checkbox("Rotate 90", Symmetry.rotate90);
        } else {
            Txt.disabled("Rotate 90 (necesita Nx + Nz par)");
        }
        hint("Los pinceles se aplican también en las posiciones reflejadas o giradas alrededor del nodo.");
    }

    private void terrainOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, TERRAIN_MAX_RADIUS);
        brushShapeIcons();
        ImGui.sliderInt("Strength", smoothStrength, 1, 4);
        int[] ratio = tool == MELT ? meltRatio : tool == WELD ? weldRatio : smoothRatio;
        ImGui.sliderInt("Block ratio %", ratio, 50, 150);
        if (tool == WELD) {
            hint("Weld rellena con el Active Block las zonas cóncavas.");
        } else if (tool == MELT) {
            hint("Melt quita masa en las puntas y los salientes.");
        } else {
            hint("Smooth reparte los bloques del pincel y conserva su número con Block ratio 100 %.");
        }
        hint("Mantén el botón derecho. Radio máximo " + TERRAIN_MAX_RADIUS + ".");
    }

    private void rulerOptions() {
        int n = Ruler.points.size();
        Txt.text("Puntos: " + n);
        if (n >= 2) {
            Txt.text(String.format(Locale.ROOT, "Euclidean: %.2f", Ruler.euclidean()));
            Txt.text("Manhattan: " + Ruler.manhattan());
            BlockPos lo = Ruler.corner(false);
            BlockPos hi = Ruler.corner(true);
            Txt.text("Bounding size: " + (hi.getX() - lo.getX() + 1) + " x " + (hi.getY() - lo.getY() + 1)
                    + " x " + (hi.getZ() - lo.getZ() + 1));
            if (n == 2) {
                Txt.text(String.format(Locale.ROOT, "Yaw: %.1f  Pitch: %.1f", Ruler.yaw(), Ruler.pitch()));
            }
            if (ImGui.button("Select bounds", ImGui.getContentRegionAvailX(), 0)) {
                Selection.set(lo, hi);
            }
        }
        if (n > 0) {
            int c = buttonRow("Remove last", "Clear");
            if (c == 0) {
                Ruler.removeLast();
            } else if (c == 1) {
                Ruler.clear();
            }
        }
        hint("Botón derecho: añade un punto en el bloque bajo el cursor.");
    }

    private void noiseOptions() {
        ImGui.sliderFloat("Scale", noiseScale, 1f, 64f);
        ImGui.sliderInt("Octaves", noiseOctaves, 1, 4);
        ImGui.inputInt("Seed", noiseSeed);
        ImGui.checkbox("3D", noise3d);
        ImGui.sameLine();
        ImGui.checkbox("White noise", noiseWhite);
        ImGui.separatorText("Blocks");
        blockRows(noiseRows, "noise");
        hint("Mantén el botón derecho: reparte los bloques por ruido según su peso.");
    }

    /** Lista editable de bloques con peso (Noise Painter, Gradient Painter). */
    private void blockRows(List<NoiseRow> rows, String key) {
        for (int i = 0; i < rows.size(); i++) {
            NoiseRow row = rows.get(i);
            ImGui.pushID(key + i);
            blockField(key, row.id, row.meta, 0);
            ImGui.sliderInt("Weight", row.weight, 1, 100);
            boolean remove = rows.size() > 1 && ImGui.button("Remove");
            ImGui.popID();
            ImGui.separator();
            if (remove) {
                rows.remove(i);
                break;
            }
        }
        if (rows.size() < 8 && ImGui.button("Add block##" + key, ImGui.getContentRegionAvailX(), 0)) {
            rows.add(new NoiseRow("minecraft:dirt"));
        }
    }

    private void gradientOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, 32);
        brushShapeIcons();
        ImGui.checkbox("Mask Surface", Brush.maskSurface);
        ImGui.separatorText("Gradient");
        ImGui.combo("Direction", gradDir, GradientSpec.DIRECTIONS);
        ImGui.sliderInt("Length", gradLength, 1, 256);
        ImGui.combo("Interpolation", gradInterp, GradientSpec.INTERPOLATIONS);
        ImGui.checkbox("Clamp", gradClamp);
        tip("Solo pinta dentro de la longitud del degradado.");
        ImGui.checkbox("Origin = stroke start", gradFromStart);
        if (!gradFromStart.get()) {
            ImGui.inputInt3("Origin", gradOrigin);
            hint("Ctrl + botón derecho sobre un bloque fija el origen.");
        }
        ImGui.separatorText("Blocks");
        blockRows(gradRows, "grad");
        hint("Mantén el botón derecho: pinta los bloques sólidos repartiendo la lista a lo largo de la dirección (o desde el origen, en Spherical). El peso es el tamaño de cada banda.");
    }

    private void modifyOptions() {
        ImGui.combo("Mode", Modify.mode, Modify.MODES);
        int m = Modify.mode.get();
        if (m == Modify.TRANSLATE) {
            ImGui.sliderInt("Count", Modify.count, 1, 32);
            ImGui.inputInt3("Offset", Modify.offset);
            ImGui.checkbox("Relative", Modify.relative);
            tip("Con Relative el desplazamiento se multiplica por el tamaño de la selección en cada eje.");
        } else {
            ImGui.combo("Axis", Modify.axis, Modify.AXES);
            if (m == Modify.ROTATE) {
                ImGui.sliderInt("Count", Modify.count, 1, 32);
                ImGui.combo("Type", Modify.type, Modify.TYPES);
                if (Modify.type.get() == 1) {
                    ImGui.sliderFloat("Angle", Modify.angle, -360f, 360f, "%.0f");
                }
                ImGui.inputInt3("Add Translation", Modify.translation);
            } else if (m == Modify.REVOLVE) {
                ImGui.sliderInt("Inner radius", Modify.innerRadius, 0, 128);
                ImGui.sliderFloat("Angle", Modify.angle, 1f, 360f, "%.0f");
                ImGui.sliderInt("Add Translation", Modify.spiral, -64, 64);
                ImGui.checkbox("Remove original", Modify.removeOriginal);
            } else {
                ImGui.sliderFloat("Angle", Modify.angle, -720f, 720f, "%.0f");
                ImGui.checkbox("Remove original", Modify.removeOriginal);
            }
            ImGui.separatorText("Pivot");
            if (ImGui.inputInt3("Pivot", Modify.pivot)) {
                Modify.pivotSet = true;
            }
            if (ImGui.button(Modify.pivotSet ? "Use selection center" : "Selection center (active)",
                    ImGui.getContentRegionAvailX(), 0)) {
                Modify.pivotSet = false;
            }
        }
        if (ImGui.button("Apply", ImGui.getContentRegionAvailX(), 0)) {
            if (!Selection.active) {
                EditEngine.status = "Modify: no hay selección";
            } else {
                EditEngine.modify(Modify.snapshot());
            }
        }
        hint(m == Modify.TRANSLATE
                ? "Copia la selección Count veces con ese desplazamiento (como Stack, pero en cualquier dirección)."
                : m == Modify.ROTATE
                ? "Copias giradas alrededor del eje que pasa por el pivote; Equidistant reparte Count copias más la original en 360°. Add Translation desplaza la última copia (en Y da una espiral)."
                : m == Modify.REVOLVE
                ? "Enrolla la selección en un arco: su longitud (eje " + Modify.AXES[(Modify.axis.get() + 2) % 3]
                    + ") es el ángulo y su profundidad (eje " + Modify.AXES[(Modify.axis.get() + 1) % 3]
                    + ") el radio, desde Inner radius. Add Translation hace una espiral."
                : "Gira cada capa más que la anterior alrededor del eje que pasa por el pivote: Angle es el giro total de abajo a arriba.");
        if (m != Modify.TRANSLATE) {
            hint("Ctrl + botón derecho sobre un bloque fija el pivote. Los bloques con orientación solo giran en cuartos de vuelta y con el eje Y; no se copian los tile entities.");
        }
    }

    private void ensureBiomes() {
        if (biomeNames == null) {
            java.util.List<net.minecraft.world.biome.Biome> all = new ArrayList<net.minecraft.world.biome.Biome>();
            for (net.minecraft.world.biome.Biome b : net.minecraft.world.biome.Biome.REGISTRY) {
                all.add(b);
            }
            java.util.Collections.sort(all, new java.util.Comparator<net.minecraft.world.biome.Biome>() {
                @Override
                public int compare(net.minecraft.world.biome.Biome x, net.minecraft.world.biome.Biome y) {
                    return x.getBiomeName().compareToIgnoreCase(y.getBiomeName());
                }
            });
            biomeNames = new String[all.size()];
            biomeIds = new int[all.size()];
            for (int i = 0; i < biomeNames.length; i++) {
                biomeNames[i] = all.get(i).getBiomeName();
                biomeIds[i] = net.minecraft.world.biome.Biome.getIdForBiome(all.get(i));
                if (biomeNames[i].equals("Plains")) {
                    biomePick.set(i);
                }
            }
        }
    }

    private void biomeOptions() {
        ensureBiomes();
        ImGui.sliderInt("Radius", Brush.radius, 1, 32);
        ImGui.combo("Biome", biomePick, biomeNames);
        ImGui.checkbox("Visualize Biome", visualizeBiome);
        tip("Pinta de colores los biomas del terreno de alrededor mientras esta herramienta está activa (View > Show Biomes lo deja siempre).");
        hint("Mantén el botón derecho: pinta el bioma en columnas enteras (en 1.12 el bioma es por columna, no por bloque), así que cambia el color de la hierba, las hojas y el agua. Se deshace con Ctrl+Z.");
    }

    private void annotationOptions() {
        ImGui.combo("Mode", Annotations.mode, Annotations.MODES);
        int m = Annotations.mode.get();
        if (m != Annotations.ERASE && m != Annotations.MOVE && m != Annotations.IMAGE) {
            ImGui.colorEdit3("Color", Annotations.color);
        }
        if (m == Annotations.LINE) {
            ImGui.sliderFloat("Width", Annotations.width, 1f, 8f, "%.1f");
            hint("Mantén el botón derecho y arrastra sobre el terreno: dibuja una línea pegada a la superficie.");
        } else if (m == Annotations.OUTLINE) {
            ImGui.combo("Style", Annotations.outlineMode, Annotations.OUTLINE_MODES);
            hint(Annotations.outlineMode.get() == 0
                    ? "Mantén el botón derecho y pasa por encima de los bloques: se resaltan (hasta 5000)."
                    : "Botón derecho entre dos bloques: se resaltan todos los que no son aire de la caja (hasta 5000).");
        } else if (m == Annotations.TEXT) {
            ImGui.inputText("Text", Annotations.text);
            ImGui.checkbox("Shadow", Annotations.shadow);
            hint("Botón derecho sobre un bloque: deja el texto flotando. Los textos solo se ven con el editor abierto; se editan en Window > Text Annotations.");
        } else if (m == Annotations.IMAGE) {
            ImGui.inputText("Image URL", Annotations.imageUrl);
            ImGui.sliderFloat("Width", Annotations.imageWidth, 1f, 64f, "%.1f blocks");
            hint("Botón derecho sobre una superficie: pega la imagen (URL http/https o ruta de archivo) de cara a ella. Si no carga, se ve una textura rosa y negra.");
        } else if (m == Annotations.MOVE) {
            hint("Mantén el botón derecho cerca de un texto o una imagen (a menos de 4 bloques) y arrastra: lo lleva a otra superficie. Ctrl+Z lo devuelve.");
        } else {
            ImGui.sliderFloat("Radius", Annotations.eraseRadius, 0.5f, 16f, "%.1f");
            hint("Mantén el botón derecho: borra las anotaciones que queden dentro del radio. Ctrl+Z las devuelve.");
        }
        if (ImGui.button("Clear ALL Annotations", ImGui.getContentRegionAvailX(), 0)) {
            Annotations.clearAll();
        }
        tip("No se puede deshacer.");
        Txt.disabled(Annotations.items.size() + " anotaciones en este mundo");
    }

    /** Modelling: puntos en filas, forma y superficie; Build deja la superficie como Placement. */
    private void modellingOptions() {
        ImGui.combo("Shape", Modelling.shape, Mesh.SHAPES);
        if (Modelling.shape.get() == Mesh.LOFT) {
            ImGui.combo("Surface", Modelling.surface, Mesh.SURFACES);
        }
        ImGui.checkbox("Offset target point", Modelling.offsetTarget);
        tip("Coloca el punto en el bloque contiguo a la cara que apuntas, no dentro del bloque apuntado.");
        Txt.text(Modelling.pointCount() + " puntos en " + Modelling.rows.size() + " filas (fila actual: "
                + (Modelling.currentRow + 1) + ")");
        float w = (ImGui.getContentRegionAvailX() - 2 * ImGui.getStyle().getItemSpacingX()) / 3f;
        if (ImGui.button("New row", w, 0)) {
            Modelling.newRow();
        }
        ImGui.sameLine();
        if (ImGui.button("Remove last", w, 0)) {
            Modelling.removeLast();
        }
        ImGui.sameLine();
        if (ImGui.button("Clear", w, 0)) {
            Modelling.clear();
        }
        if (ImGui.button("Build (Create Placement)", ImGui.getContentRegionAvailX(), 0)) {
            createModel();
        }
        if (ImGui.collapsingHeader("Points")) {
            int delRow = -1, delPoint = -1;
            for (int r = 0; r < Modelling.rows.size(); r++) {
                ImGui.pushID(r);
                if (ImGui.selectable("Row " + (r + 1) + (r == Modelling.currentRow ? " (current)" : ""), r == Modelling.currentRow)) {
                    Modelling.currentRow = r;
                }
                for (int i = 0; i < Modelling.rows.get(r).size(); i++) {
                    double[] pt = Modelling.rows.get(r).get(i);
                    int[] c = {(int) Math.floor(pt[0]), (int) Math.floor(pt[1]), (int) Math.floor(pt[2])};
                    ImGui.pushID(i);
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 40f);
                    if (ImGui.inputInt3("##pt", c)) {
                        pt[0] = c[0] + 0.5;
                        pt[1] = c[1] + 0.5;
                        pt[2] = c[2] + 0.5;
                    }
                    ImGui.sameLine();
                    if (ImGui.button("X")) {
                        delRow = r;
                        delPoint = i;
                    }
                    ImGui.popID();
                }
                ImGui.popID();
            }
            if (delRow >= 0) {
                Modelling.rows.get(delRow).remove(delPoint);
            }
        }
        hint("Botón derecho: añade un punto a la fila actual. Loft une filas contiguas punto a punto (con Flat, Catmull-Rom o Bezier); Triangle Strip y Fan usan todos los puntos en orden; Convex Hull cubre el casco convexo. E duplica el último punto (luego se edita en Points).");
    }

    /** Voxeliza la superficie con el Active Block y la deja como Placement donde está (sin seguir al cursor). */
    private void createModel() {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        Modelling.Result r = Modelling.build(activeState);
        if (r == null) {
            return;
        }
        Placement.start(r.clip);
        Placement.follow = false;
        Placement.pos[0] = r.min[0];
        Placement.pos[1] = r.min[1];
        Placement.pos[2] = r.min[2];
    }

    /** Ventana flotante con una imagen de referencia (URL o archivo). */
    private void referenceWindow() {
        ImGui.setNextWindowSize(360, 300, imgui.flag.ImGuiCond.FirstUseEver);
        if (ImGui.begin("Reference Image", showRef)) {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 70f);
            ImGui.inputText("##refsrc", refSource);
            ImGui.sameLine();
            if (ImGui.button("Load") && !refSource.get().trim().isEmpty()) {
                ImageCache.forget(refSource.get().trim());
                refEntry = ImageCache.get(refSource.get().trim());
            }
            ImGui.sliderFloat("Opacity", refOpacity, 0.05f, 1f, "%.2f");
            ImGui.checkbox("Filtering: Nearest", refNearest);
            if (refEntry == null) {
                hint("Escribe una URL (http/https) o la ruta de un archivo de imagen y pulsa Load.");
            } else if (refEntry.loading) {
                Txt.disabled("Cargando...");
            } else {
                if (refEntry.error != null) {
                    Txt.colored(1f, 0.4f, 0.35f, 1f, "No se pudo cargar: " + refEntry.error);
                }
                int tex = ImageCache.texture(refEntry);
                ImageCache.setNearest(refEntry, refNearest.get());
                if (tex != 0) {
                    float aw = ImGui.getContentRegionAvailX();
                    float scale = aw / Math.max(1, refEntry.w);
                    float h = refEntry.h * scale;
                    float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
                    ImGui.dummy(aw, h);
                    int alpha = Math.round(refOpacity[0] * 255f);
                    ImGui.getWindowDrawList().addImage(tex, x, y, x + aw, y + h, 0f, 0f, 1f, 1f, (alpha << 24) | 0xFFFFFF);
                }
            }
        }
        ImGui.end();
    }

    private void fluidOptions() {
        ImGui.combo("Fluid", fluidType, FluidBall.TYPES);
        ImGui.sliderInt("Radius", Brush.radius, 1, 24);
        hint(fluidType.get() == FluidBall.SNOW
                ? "Mantén el botón derecho: capas de nieve sobre la superficie, más gruesas en el centro."
                : "Mantén el botón derecho: una esfera de fuentes que reemplaza aire y bloques no sólidos; el fluido fluye solo, como en vanilla. Ctrl+Z quita las fuentes y el flujo se retira.");
    }

    /** Ventana con los textos de las anotaciones: editarlos, ir hasta ellos o borrarlos. */
    /** Sección "Presets": guardar los ajustes con un nombre y cargar o borrar los guardados. */
    private void presetBlock(String toolName, Map<String, Object> holders) {
        if (holders == null) {
            return;
        }
        ImGui.pushID("presets" + toolName);
        ImGui.separatorText("Presets");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 110f);
        ImGui.inputText("##presetname", presetName);
        ImGui.sameLine();
        if (ImGui.button("Save preset") && !presetName.get().trim().isEmpty()) {
            Presets.save(toolName, presetName.get().trim(), holders);
            EditEngine.status = "Preset guardado: " + presetName.get().trim();
        }
        List<Presets.Preset> list = Presets.list(toolName);
        int remove = -1;
        for (int i = 0; i < list.size(); i++) {
            ImGui.pushID(i);
            if (ImGui.button("Load")) {
                Presets.apply(list.get(i), holders);
                presetName.set(list.get(i).name);
                EditEngine.status = "Preset cargado: " + list.get(i).name;
            }
            ImGui.sameLine();
            if (ImGui.button("X")) {
                remove = i;
            }
            ImGui.sameLine();
            Txt.text(list.get(i).name);
            ImGui.popID();
        }
        if (remove >= 0) {
            Presets.delete(toolName, remove);
        }
        if (list.isEmpty()) {
            Txt.disabled("Sin presets guardados para esta herramienta.");
        }
        ImGui.popID();
    }

    /** Las filas de bloques de Noise Painter / Gradient Painter como un ajuste de preset (id|meta|peso;...). */
    private Presets.Custom rowsHolder(final List<NoiseRow> rows) {
        return new Presets.Custom() {
            @Override
            public String save() {
                StringBuilder sb = new StringBuilder();
                for (NoiseRow r : rows) {
                    sb.append(sb.length() == 0 ? "" : ";").append(r.id.get()).append('|').append(r.meta[0]).append('|')
                            .append(r.weight[0]);
                }
                return sb.toString();
            }

            @Override
            public void load(String text) {
                rows.clear();
                for (String row : text.split(";")) {
                    String[] t = row.split("\\|");
                    if (t.length < 3) {
                        continue;
                    }
                    NoiseRow r = new NoiseRow(t[0]);
                    r.meta[0] = Integer.parseInt(t[1]);
                    r.weight[0] = Integer.parseInt(t[2]);
                    rows.add(r);
                }
                if (rows.isEmpty()) {
                    rows.add(new NoiseRow("minecraft:stone"));
                }
            }
        };
    }

    /** Los ajustes que guarda un preset de la herramienta t (null si no tiene). */
    private Map<String, Object> presetHolders(int t) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        switch (t) {
            case PAINTER:
            case DRAW:
            case NOISE:
                brushHolders(m, true);
                if (t == NOISE) {
                    m.put("rows", rowsHolder(noiseRows));
                    m.put("scale", noiseScale);
                    m.put("octaves", noiseOctaves);
                    m.put("seed", noiseSeed);
                    m.put("3d", noise3d);
                    m.put("white", noiseWhite);
                }
                break;
            case SMOOTH:
            case MELT:
            case WELD:
                brushHolders(m, false);
                m.put("strength", smoothStrength);
                m.put("ratio", t == MELT ? meltRatio : t == WELD ? weldRatio : smoothRatio);
                break;
            case ELEVATION:
                brushHolders(m, false);
                m.put("mode", elevMode);
                m.put("amount", elevAmount);
                break;
            case DISTORT:
                brushHolders(m, false);
                m.put("scale", distScale);
                m.put("distance", distDistance);
                m.put("seed", distSeed);
                break;
            case ROUGHEN:
                brushHolders(m, false);
                m.put("amount", roughAmount);
                m.put("seed", roughSeed);
                break;
            case SHATTER:
                brushHolders(m, false);
                m.put("scale", shatterScale);
                m.put("width", shatterWidth);
                m.put("seed", shatterSeed);
                m.put("fill", shatterFill);
                break;
            case ROCK:
                m.put("radius", rockRadius);
                m.put("noise", rockNoise);
                m.put("scale", rockScale);
                m.put("random", rockRandom);
                m.put("seed", rockSeed);
                break;
            case SCRIPT:
                brushHolders(m, false);
                m.put("code", scriptCode);
                break;
            case GRADIENT:
                brushHolders(m, true);
                m.put("rows", rowsHolder(gradRows));
                m.put("direction", gradDir);
                m.put("length", gradLength);
                m.put("interpolation", gradInterp);
                m.put("clamp", gradClamp);
                m.put("fromStart", gradFromStart);
                break;
            case SCULPT:
                brushHolders(m, false);
                m.put("strength", sculptStrength);
                m.put("invert", sculptInvert);
                m.put("maskY", sculptMaskY);
                m.put("denoise", sculptDenoise);
                break;
            case SLOPE:
                m.put("radius", Brush.radius);
                m.put("mode", SlopeTool.mode);
                m.put("height", SlopeTool.height);
                m.put("sharpness", SlopeTool.sharpness);
                break;
            case STAMP:
                m.put("spacing", stampSpacing);
                m.put("scatter", stampScatter);
                m.put("chance", stampChance);
                m.put("rotate", stampRotate);
                m.put("flip", stampFlip);
                m.put("keep", stampKeep);
                m.put("drop", stampDrop);
                break;
            case FLUID:
                m.put("radius", Brush.radius);
                m.put("fluid", fluidType);
                break;
            case FLOODFILL:
                m.put("limit", floodLimit);
                m.put("up", floodUp);
                m.put("down", floodDown);
                m.put("horizontal", floodHoriz);
                m.put("corners", floodCorners);
                break;
            case PATH:
                m.put("type", PathTool.type);
                m.put("radius", PathTool.radius);
                m.put("looped", PathTool.looped);
                m.put("keep", PathTool.keepExisting);
                break;
            case EXTRUDE:
                m.put("count", extrudeCount);
                m.put("compare", extrudeCompare);
                m.put("shrink", extrudeShrink);
                break;
            case MAGIC:
                m.put("compare", MagicSelect.compare);
                m.put("limit", MagicSelect.limit);
                m.put("range", MagicSelect.range);
                m.put("corners", MagicSelect.corners);
                m.put("surfaceOnly", MagicSelect.surfaceOnly);
                m.put("mode", MagicSelect.op);
                break;
            case TEXT:
                m.put("text", textStr);
                m.put("font", textFont);
                m.put("size", textSize);
                m.put("bold", textBold);
                m.put("italic", textItalic);
                m.put("flat", textFlat);
                break;
            case SHAPE:
                m.put("type", shapeType);
                m.put("size", shapeSize);
                m.put("hollow", shapeHollow);
                break;
            case MODEL:
                m.put("shape", Modelling.shape);
                m.put("surface", Modelling.surface);
                m.put("offset", Modelling.offsetTarget);
                break;
            case LASSO:
                m.put("mode", fsMode);
                m.put("depth", Lasso.depth);
                m.put("nonSolid", Lasso.nonSolid);
                break;
            case FSELECT:
                m.put("mode", fsMode);
                m.put("solid", fsSolid);
                m.put("radius", Brush.radius);
                break;
            default:
                return null;
        }
        return m;
    }

    private static void brushHolders(Map<String, Object> m, boolean maskSurface) {
        m.put("radius", Brush.radius);
        m.put("shape", Brush.shape);
        if (maskSurface) {
            m.put("maskSurface", Brush.maskSurface);
        }
    }

    /** Lista de atajos del editor: cambiar, restablecer o quitar; los que chocan se marcan en rojo. */
    private void keybindsWindow() {
        Keybinds.init(TOOLS, TOOL_KEYS);
        if (ImGui.begin("Keybinds", showKeybinds)) {
            Keybinds.pollCapture();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 120f);
            ImGui.inputText("Filter", keybindFilter);
            ImGui.sameLine();
            if (ImGui.button("Reset all")) {
                Keybinds.resetAll();
            }
            if (Keybinds.capturing != null) {
                Txt.colored(1f, 0.85f, 0.3f, 1f, "Pulsa la nueva tecla para " + Keybinds.capturing.label
                        + " (con Ctrl o Shift si los quieres; Esc cancela)");
            }
            String q = keybindFilter.get().trim().toLowerCase(Locale.ROOT);
            String cat = null;
            for (Keybinds.Bind b : Keybinds.all()) {
                if (!q.isEmpty() && !b.label.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                if (!b.category.equals(cat)) {
                    cat = b.category;
                    ImGui.separatorText(cat);
                }
                ImGui.pushID(b.id);
                Txt.text(b.label);
                ImGui.sameLine(ImGui.getWindowWidth() * 0.36f);
                boolean waiting = Keybinds.capturing == b;
                String shown = waiting ? "..." : Keybinds.describe(b);
                if (Keybinds.conflicts(b)) {
                    Txt.colored(1f, 0.4f, 0.35f, 1f, shown);
                    if (ImGui.isItemHovered()) {
                        Txt.tooltip("Otro atajo usa la misma combinación en este contexto.");
                    }
                } else {
                    Txt.text(shown);
                }
                ImGui.sameLine(ImGui.getWindowWidth() * 0.6f);
                if (ImGui.smallButton("Set")) {
                    Keybinds.capturing = b;
                }
                ImGui.sameLine();
                if (ImGui.smallButton("Reset")) {
                    b.reset();
                    Keybinds.save();
                }
                ImGui.sameLine();
                if (ImGui.smallButton("Clear")) {
                    b.key = imgui.flag.ImGuiKey.None;
                    Keybinds.save();
                }
                ImGui.popID();
            }
            hint("Fijos: flechas / RePág / AvPág mueven un Placement (y RePág / AvPág estiran la selección; con Shift la encogen), Backspace también cancela un Placement, clic central copia un bloque, Escape o Right Shift cierran el editor.");
        }
        ImGui.end();
    }

    private void textAnnotationsWindow() {
        Annotations.ensureLoaded();
        if (ImGui.begin("Text Annotations", showTextAnn)) {
            int remove = -1;
            int n = 0;
            for (int i = 0; i < Annotations.items.size(); i++) {
                Annotations.Item it = Annotations.items.get(i);
                if (it.type != Annotations.TEXT) {
                    continue;
                }
                ImGui.pushID(i);
                ImString t = new ImString(it.text, 200);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 130f);
                if (ImGui.inputText("##t", t)) {
                    it.text = t.get();
                }
                if (ImGui.isItemDeactivatedAfterEdit()) {
                    Annotations.save();
                }
                ImGui.sameLine();
                if (ImGui.button("Go")) {
                    mc.player.setPositionAndUpdate(it.p[0], it.p[1] + 1, it.p[2]);
                }
                ImGui.sameLine();
                if (ImGui.button("X")) {
                    remove = i;
                }
                ImGui.popID();
                n++;
            }
            if (remove >= 0) {
                Annotations.items.remove(remove);
                Annotations.save();
            }
            if (n == 0) {
                hint("Sin textos. Usa la herramienta Annotation en modo Text.");
            }
        }
        ImGui.end();
    }

    /** Dibuja los textos de las anotaciones sobre el mundo, siempre de cara a la pantalla. */
    private void drawAnnotationTexts() {
        if (!Annotations.show.get() || Annotations.items.isEmpty()) {
            return;
        }
        imgui.ImDrawList dl = ImGui.getBackgroundDrawList();
        for (Annotations.Item it : Annotations.items) {
            if (it.type != Annotations.TEXT || it.p.length < 3) {
                continue;
            }
            double dx = it.p[0] - Targeting.camX, dy = it.p[1] - Targeting.camY, dz = it.p[2] - Targeting.camZ;
            if (dx * dx + dy * dy + dz * dz > 160 * 160) {
                continue;
            }
            float[] s = Targeting.project(it.p[0], it.p[1], it.p[2]);
            if (s == null) {
                continue;
            }
            float x = s[0] - ImGui.calcTextSizeX(it.text) / 2f, y = s[1] - ImGui.getTextLineHeight() / 2f;
            int abgr = 0xFF000000 | (it.color & 255) << 16 | (it.color >> 8 & 255) << 8 | (it.color >> 16 & 255);
            if (it.shadow) {
                dl.addText(x + 1.5f, y + 1.5f, 0xFF000000, it.text);
            }
            dl.addText(x, y, abgr, it.text);
        }
    }

    private void lassoOptions() {
        ImGui.combo("Mode", fsMode, MagicSelect.OPS);
        ImGui.sliderInt("Depth", Lasso.depth, 1, 64);
        tip("Cuántos bloques se adentra la selección en la vista, desde la superficie.");
        ImGui.checkbox("Include Non-Solid", Lasso.nonSolid);
        hint("Mantén el botón derecho y dibuja un bucle en pantalla: al soltar, el contorno se proyecta sobre los bloques visibles (hasta Depth).");
    }

    private void sculptOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, TERRAIN_MAX_RADIUS);
        ImGui.sliderFloat("Strength", sculptStrength, 0.05f, 5f, "%.2f", ImGuiSliderFlags.Logarithmic);
        ImGui.checkbox("Invert", sculptInvert);
        ImGui.sameLine();
        ImGui.checkbox("Mask Y", sculptMaskY);
        ImGui.sameLine();
        ImGui.checkbox("Denoise", sculptDenoise);
        hint("Mantén el botón derecho: levanta una protuberancia siguiendo la normal de la superficie (Invert la hunde). Mask Y la empuja siempre hacia arriba; Denoise la deja más lisa. No usa el Active Block: copia los bloques vecinos.");
    }

    private void slopeOptions() {
        ImGui.combo("Mode", SlopeTool.mode, SlopeTool.MODES);
        ImGui.sliderInt("Radius", Brush.radius, 1, 32);
        ImGui.sliderInt("Height", SlopeTool.height, 1, 64);
        ImGui.sliderFloat("Sharpness", SlopeTool.sharpness, 0.5f, 8f, "%.1f");
        BlockPos t = SlopeTool.target;
        Txt.text(t == null ? "Sin objetivo" : "Objetivo: " + t.getX() + " " + t.getY() + " " + t.getZ());
        if (t != null && ImGui.button("Clear target", ImGui.getContentRegionAvailX(), 0)) {
            SlopeTool.target = null;
        }
        hint("1) Botón derecho en un bloque: fija el objetivo (la caja magenta). 2) Mantén el botón derecho desde otro bloque y arrastra: el terreno se lleva a la recta inicio-objetivo. Cone es una pendiente radial alrededor del inicio (sube si el objetivo está más alto, baja si está más bajo). Ctrl + clic fija otro objetivo. Height limita el cambio por columna.");
    }

    private void freeSelectOptions() {
        ImGui.sliderInt("Radius", Brush.radius, 1, 32);
        brushShapeIcons();
        ImGui.combo("Mode", fsMode, MagicSelect.OPS);
        ImGui.checkbox("Solid blocks only", fsSolid);
        hint("Mantén el botón derecho y pinta sobre el terreno: los bloques bajo el pincel se suman a la selección (o se restan, según el modo).");
    }

    private void stampOptions() {
        Clipboard c = Clipboard.current;
        Txt.text(c == null ? "Portapapeles vacío (Copy primero)" : "Stamp: " + c.sx + " x " + c.sy + " x " + c.sz);
        ImGui.sliderInt("Spacing", stampSpacing, 1, 64);
        ImGui.sliderInt("Scatter", stampScatter, 0, 32);
        ImGui.sliderInt("Chance %", stampChance, 1, 100);
        ImGui.sliderInt("Drop", stampDrop, -8, 8);
        ImGui.checkbox("Random rotation", stampRotate);
        ImGui.sameLine();
        ImGui.checkbox("Random flip", stampFlip);
        ImGui.checkbox("Keep Existing", stampKeep);
        hint("Mantén el botón derecho y arrastra: copia el portapapeles cada Spacing bloques, con dispersión, giro y volteo al azar, apoyado en el terreno. Ideal para bosques o rocas. No copia tile entities.");
    }

    /** Brillo mínimo: sube el gamma mientras el editor está abierto y lo devuelve al cerrarlo. */
    private void applyBrightness() {
        float want = UiSettings.minBrightness;
        if (want > 0f) {
            if (savedGamma < 0f) {
                savedGamma = mc.gameSettings.gammaSetting;
            }
            mc.gameSettings.gammaSetting = savedGamma + want * 100f;
        } else if (savedGamma >= 0f) {
            mc.gameSettings.gammaSetting = savedGamma;
            savedGamma = -1f;
        }
    }

    private void autoshadeRow() {
        ImGui.checkbox("Sun", shadeSun);
        if (shadeSun.get()) {
            ImGui.sliderFloat("Sun azimuth", shadeAz, 0f, 360f, "%.0f");
            tip("0 = sol al sur, 90 = al oeste, 180 = al norte, 270 = al este.");
            ImGui.sliderFloat("Sun elevation", shadeEl, 1f, 90f, "%.0f");
        }
        String[] from = {"No lights", "Player position", "Custom positions"};
        ImGui.combo("Lights", shadeLightsMode, from);
        if (shadeLightsMode.get() > 0) {
            ImGui.sliderFloat("Light intensity", shadeLightIntensity, 0.1f, 2f, "%.2f");
            ImGui.sliderFloat("Light radius", shadeLightRadius, 4f, 128f, "%.0f");
        }
        if (shadeLightsMode.get() == 2) {
            if (ImGui.button("Add light at my position")) {
                shadeLights.add(new float[] {(float) mc.player.posX, (float) (mc.player.posY + mc.player.getEyeHeight()),
                    (float) mc.player.posZ, shadeLightIntensity[0], shadeLightRadius[0]});
            }
            int remove = -1;
            for (int i = 0; i < shadeLights.size(); i++) {
                float[] l = shadeLights.get(i);
                ImGui.pushID("light" + i);
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.55f);
                ImGui.dragFloat3("##pos", l, 0.5f);
                ImGui.sameLine();
                ImGui.setNextItemWidth(60f);
                float[] inten = {l[3]};
                if (ImGui.dragFloat("##int", inten, 0.01f)) {
                    l[3] = Math.max(0f, inten[0]);
                }
                ImGui.sameLine();
                if (ImGui.button("X")) {
                    remove = i;
                }
                ImGui.popID();
            }
            if (remove >= 0) {
                shadeLights.remove(remove);
            }
            if (shadeLights.isEmpty()) {
                Txt.disabled("Sin luces: añade una en tu posición y muévela con las coordenadas.");
            }
        }
        ImGui.sliderFloat("Ambient", shadeAmbient, 0f, 1f, "%.2f");
        ImGui.sliderFloat("Ambient occlusion", shadeAo, 0f, 1f, "%.2f");
        ImGui.sliderInt("Global illumination bounces", shadeBounces, 0, 4);
        if (shadeBounces[0] > 0) {
            ImGui.sliderFloat("Bounce strength", shadeBounceStrength, 0.05f, 1f, "%.2f");
        }
        ImGui.sliderFloat("Dither", shadeDither, 0f, 1f, "%.2f");
        ImGui.inputInt("Seed##shade", shadeSeed);
        Palettes.ensureLoaded();
        String[] names = new String[Palettes.list.size() + 1];
        names[0] = "(Automatic: blocks of the selection)";
        for (int i = 0; i < Palettes.list.size(); i++) {
            names[i + 1] = Palettes.list.get(i).name;
        }
        if (shadePalette.get() >= names.length) {
            shadePalette.set(0);
        }
        ImGui.combo("Palette##shade", shadePalette, names);
        tip("Orden de oscuro a claro. Automática: los bloques de la selección ordenados por luminosidad. Con una paleta propia, el orden es el de la ventana Palette.");
        if (shadePalette.get() == 0) {
            ImGui.inputText("Include##shade", shadeInclude);
            tip("Solo bloques cuyo nombre contenga alguna de estas palabras (separadas por comas). Vacío = todos.");
            ImGui.inputText("Exclude##shade", shadeExclude);
            tip("Quita los bloques cuyo nombre contenga alguna de estas palabras (por ejemplo: planks, log).");
        }
        if (ImGui.button("Autoshade##run", ImGui.getContentRegionAvailX(), 0)) {
            autoshade();
        }
        Map<String, Object> h = new LinkedHashMap<String, Object>();
        h.put("sun", shadeSun);
        h.put("azimuth", shadeAz);
        h.put("elevation", shadeEl);
        h.put("lightsMode", shadeLightsMode);
        h.put("lightIntensity", shadeLightIntensity);
        h.put("lightRadius", shadeLightRadius);
        h.put("ambient", shadeAmbient);
        h.put("ao", shadeAo);
        h.put("bounces", shadeBounces);
        h.put("bounceStrength", shadeBounceStrength);
        h.put("dither", shadeDither);
        h.put("seed", shadeSeed);
        h.put("include", shadeInclude);
        h.put("exclude", shadeExclude);
        h.put("lights", new Presets.Custom() {
            @Override
            public String save() {
                StringBuilder sb = new StringBuilder();
                for (float[] l : shadeLights) {
                    sb.append(sb.length() == 0 ? "" : ";").append(l[0]).append(',').append(l[1]).append(',').append(l[2])
                            .append(',').append(l[3]).append(',').append(l[4]);
                }
                return sb.toString();
            }

            @Override
            public void load(String text) {
                shadeLights.clear();
                for (String row : text.split(";")) {
                    String[] t = row.split(",");
                    if (t.length >= 5) {
                        shadeLights.add(new float[] {Float.parseFloat(t[0]), Float.parseFloat(t[1]), Float.parseFloat(t[2]),
                            Float.parseFloat(t[3]), Float.parseFloat(t[4])});
                    }
                }
            }
        });
        presetBlock("Autoshade", h);
    }

    private void autoshade() {
        BlockColors.ensureBuilt();
        Autoshade.Params p = new Autoshade.Params();
        p.sunOn = shadeSun.get();
        p.sun = Autoshade.sunVector(shadeAz[0], shadeEl[0]);
        if (shadeLightsMode.get() == 1) {
            p.lights.add(new double[] {mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ,
                shadeLightIntensity[0], shadeLightRadius[0]});
        } else if (shadeLightsMode.get() == 2) {
            for (float[] l : shadeLights) {
                p.lights.add(new double[] {l[0], l[1], l[2], l[3], l[4]});
            }
        }
        p.ambient = shadeAmbient[0];
        p.ao = shadeAo[0];
        p.dither = shadeDither[0];
        p.bounces = shadeBounces[0];
        p.bounceStrength = shadeBounceStrength[0];
        p.include = shadeInclude.get();
        p.exclude = shadeExclude.get();
        p.seed = shadeSeed.get();
        if (shadePalette.get() > 0) {
            Palettes.Palette pal = Palettes.list.get(shadePalette.get() - 1);
            List<Integer> ids = new ArrayList<Integer>();
            for (String e : pal.blocks) {
                IBlockState st = Palettes.resolve(e);
                if (st != null) {
                    ids.add(Block.getStateId(st));
                }
            }
            p.palette = new int[ids.size()];
            for (int i = 0; i < p.palette.length; i++) {
                p.palette[i] = ids.get(i);
            }
        }
        EditEngine.autoshade(p);
    }

    private void animatedRebuildRow() {
        ImGui.sliderFloat("Start delay", AnimatedRebuild.startDelay, 0f, 200f, "%.0f ticks");
        ImGui.sliderFloat("Air block delay", AnimatedRebuild.airDelay, 0f, 2f, "%.2f ticks");
        ImGui.sliderFloat("Same block delay", AnimatedRebuild.sameDelay, 0f, 2f, "%.2f ticks");
        ImGui.sliderFloat("Different block delay", AnimatedRebuild.differentDelay, 0f, 5f, "%.2f ticks");
        ImGui.sliderFloat("Random extra delay", AnimatedRebuild.randomExtra, 0f, 5f, "%.2f ticks");
        ImGui.sliderFloat("Max delay", AnimatedRebuild.maxDelay, 0.1f, 20f, "%.1f ticks");
        ImGui.sliderFloat("Horizontal multiplier", AnimatedRebuild.horizontal, 0.1f, 4f, "%.2f");
        ImGui.sliderFloat("Vertical multiplier", AnimatedRebuild.vertical, 0.1f, 4f, "%.2f");
        tip("Los bloques se vuelven a colocar del centro hacia fuera. Los multiplicadores estiran la distancia en horizontal o en vertical (más de 1 = esa dirección avanza antes). 20 ticks = 1 segundo.");
        float w = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2f;
        if (ImGui.button("Start", w, 0) && !AnimatedRebuild.active()) {
            EditEngine.animatedRebuild();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel", w, 0)) {
            AnimatedRebuild.cancel();
        }
        tip("Ctrl+Z cancela lo que quede por colocar y devuelve el original. Rehacer solo vuelve a vaciar la zona.");
    }

    private void colourField() {
        BlockColors.ensureBuilt();
        EditEngine.colourField();
    }

    private void setBiomeRow() {
        ensureBiomes();
        ImGui.combo("Biome##setb", biomePick, biomeNames);
        if (ImGui.button("Set Biome##setb", ImGui.getContentRegionAvailX(), 0)) {
            setBiomeSelection();
        }
        tip("Cambia el bioma de todas las columnas de la selección (en 1.12 el bioma es por columna).");
    }

    private void setBiomeSelection() {
        ensureBiomes();
        EditEngine.setBiomeSelection(biomeIds[biomePick.get()], biomeNames[biomePick.get()]);
    }

    private void smoothSnowRow() {
        ImGui.sliderInt("Max layers", snowLayers, 1, 8);
        ImGui.sliderInt("Smooth radius", snowRadius, 1, 6);
        if (ImGui.button("Smoothsnow##ss", ImGui.getContentRegionAvailX(), 0)) {
            EditEngine.smoothSnow(snowLayers[0], snowRadius[0]);
        }
        tip("Capas de nieve sobre el terreno: más gruesas en huecos y escalones para que la cobertura sea continua. Sustituye aire y plantas bajas, no bloques.");
    }

    private void selectionWindow() {
        if (ImGui.begin("Selection")) {
            if (!Selection.active) {
                hint("Sin selección. Box Select: clic derecho y arrastra entre dos bloques. Magic Select: clic derecho en un bloque.");
            } else {
                boolean edited = ImGui.inputInt3("Pos 1", Selection.p1);
                edited |= ImGui.inputInt3("Pos 2", Selection.p2);
                if (edited) {
                    Selection.mask = null;
                }
                Txt.text(Selection.size(0) + " x " + Selection.size(1) + " x " + Selection.size(2)
                        + " = " + Selection.volume() + " bloques" + (Selection.mask != null ? " (máscara)" : ""));
                if (ImGui.collapsingHeader("Resize", ImGuiTreeNodeFlags.DefaultOpen)) {
                    ImGui.sliderInt("Amount", expandAmount, 1, 64);
                    ImGui.checkbox("Shrink", shrink);
                    int n = shrink.get() ? -expandAmount[0] : expandAmount[0];
                    int c = buttonRow(suffixed(DIR_LABELS, "rs"));
                    if (c >= 0) {
                        Selection.expand(DIRS[c], n);
                    }
                    if (ImGui.button("Full height (0-255)", ImGui.getContentRegionAvailX(), 0)) {
                        Selection.fullHeight();
                    }
                    tip("RePág / AvPág estiran hacia arriba / abajo (con Shift, encogen).");
                }
                if (ImGui.collapsingHeader("Actions", ImGuiTreeNodeFlags.DefaultOpen)) {
                    int c = buttonRow("Fill", "Delete", "Copy", "Cut", "Clear");
                    if (c == 0) {
                        fill();
                    } else if (c == 1) {
                        deleteSelection();
                    } else if (c == 2) {
                        EditEngine.copy(false);
                    } else if (c == 3) {
                        EditEngine.copy(true);
                    } else if (c == 4) {
                        Selection.clear();
                    }
                }
                if (ImGui.collapsingHeader("Replace")) {
                    blockField("rep", replaceId, replaceMeta, -1);
                    hint("Meta -1 = cualquiera.");
                    if (ImGui.button("Replace with Active Block", ImGui.getContentRegionAvailX(), 0)) {
                        replace();
                    }
                }
                if (ImGui.collapsingHeader("More operations")) {
                    if (ImGui.button("Fill Nearest", ImGui.getContentRegionAvailX(), 0)) {
                        EditEngine.fillNearest();
                    }
                    tip("Cada bloque de aire de la selección toma el estado del bloque no aire más cercano.");
                    if (ImGui.button("Trigger Updates", ImGui.getContentRegionAvailX(), 0)) {
                        EditEngine.triggerUpdates();
                    }
                    tip("Quita los bloques mal colocados (carteles o antorchas flotantes...). No suelta objetos.");
                    if (ImGui.button("Waterlog (air to water)", ImGui.getContentRegionAvailX(), 0)) {
                        EditEngine.waterlog();
                    }
                    if (ImGui.button("Generate Colour Field", ImGui.getContentRegionAvailX(), 0)) {
                        colourField();
                    }
                    tip("Rellena la caja con un degradado CIELAB: L en X, a (verde a rojo) en Y, b (azul a amarillo) en Z, con bloques completos y opacos.");
                    ImGui.separatorText("Autoshade");
                    autoshadeRow();
                    ImGui.separatorText("Animated Rebuild");
                    animatedRebuildRow();
                    ImGui.separatorText("Set Biome");
                    setBiomeRow();
                    ImGui.separatorText("Smoothsnow");
                    smoothSnowRow();
                }
                if (ImGui.collapsingHeader("Shape")) {
                    hint("Cambian la forma de la selección, no los bloques.");
                    if (ImGui.button("Bounding Box", ImGui.getContentRegionAvailX(), 0)) {
                        SelectionOps.boundingBox();
                    }
                    if (ImGui.button("Convex Hull", ImGui.getContentRegionAvailX(), 0)) {
                        SelectionOps.convexHull();
                    }
                    tip("Rellena los huecos y entrantes: se queda con todo lo que cae dentro del casco convexo de la selección.");
                    ImGui.separatorText("Filter");
                    blockField("filter", filterId, filterMeta, -1);
                    if (ImGui.button("Keep only this block", ImGui.getContentRegionAvailX(), 0)) {
                        Block b = Block.getBlockFromName(filterId.get().trim());
                        if (b == null) {
                            EditEngine.status = "Filter: bloque desconocido";
                        } else {
                            SelectionOps.filter(b, filterMeta[0]);
                        }
                    }
                    ImGui.separatorText("Smooth");
                    ImGui.sliderInt("StdDev", selSigma, 1, 6);
                    ImGui.sliderFloat("Threshold", selThreshold, 0.05f, 0.95f, "%.2f");
                    if (ImGui.button("Smooth selection", ImGui.getContentRegionAvailX(), 0)) {
                        SelectionOps.smooth(selSigma[0], selThreshold[0]);
                    }
                    ImGui.separatorText("Distort");
                    ImGui.sliderFloat("Radius##seld", selDistScale, 1f, 32f, "%.1f");
                    ImGui.sliderFloat("Distance", selDistAmount, 0.5f, 16f, "%.1f");
                    ImGui.inputInt("Seed##seld", selDistSeed);
                    if (ImGui.button("Distort selection", ImGui.getContentRegionAvailX(), 0)) {
                        SelectionOps.distort(selDistScale[0], selDistAmount[0], selDistSeed.get());
                    }
                    ImGui.separatorText("Move selection");
                    ImGui.sliderInt("Amount##selm", selMove, 1, 64);
                    int mv = buttonRow(suffixed(DIR_LABELS, "selm"));
                    if (mv >= 0) {
                        Selection.shift(DIRS[mv].getFrontOffsetX() * selMove[0], DIRS[mv].getFrontOffsetY() * selMove[0],
                                DIRS[mv].getFrontOffsetZ() * selMove[0]);
                    }
                    tip("Mueve solo la selección, no los bloques.");
                }
            }
        }
        ImGui.end();
    }

    /** Move y Stack: reubican o repiten el contenido de la selección. */
    private void transformWindow() {
        if (ImGui.begin("Move / Stack")) {
            ImGui.separatorText("Move");
            ImGui.sliderInt("Distance", moveAmount, 1, 64);
            int m = buttonRow(suffixed(DIR_LABELS, "mv"));
            if (m >= 0) {
                EnumFacing f = DIRS[m];
                int d = moveAmount[0];
                if (EditEngine.move(f.getFrontOffsetX() * d, f.getFrontOffsetY() * d, f.getFrontOffsetZ() * d)) {
                    Selection.shift(f.getFrontOffsetX() * d, f.getFrontOffsetY() * d, f.getFrontOffsetZ() * d);
                }
            }
            ImGui.separatorText("Stack");
            ImGui.sliderInt("Copies", stackCount, 1, 64);
            ImGui.sliderInt("Gap", stackGap, 0, 64);
            int s = buttonRow(suffixed(DIR_LABELS, "st"));
            if (s >= 0) {
                EditEngine.stack(DIRS[s], stackCount[0], stackGap[0]);
            }
        }
        ImGui.end();
    }

    /** Tool Masks: reglas que restringen dónde actúan los pinceles (todas deben cumplirse). */
    private void masksWindow() {
        if (ImGui.begin("Tool Masks", showMasks)) {
            ImGui.checkbox("Enabled", Mask.enabled);
            ImGui.separator();
            for (int i = 0; i < Mask.rules.size(); i++) {
                Mask.Rule r = Mask.rules.get(i);
                ImGui.pushID(i);
                ImGui.combo("Type", r.type, Mask.TYPES);
                ImGui.checkbox("NOT", r.not);
                if (r.needsBlock()) {
                    blockField("mask", r.id, r.meta, -1);
                } else if (r.type.get() == MaskSpec.Y) {
                    ImGui.sliderInt2("Y range", r.y, 0, 255);
                }
                boolean remove = ImGui.button("Remove");
                ImGui.popID();
                ImGui.separator();
                if (remove) {
                    Mask.rules.remove(i);
                    break;
                }
            }
            if (ImGui.button("Add rule", ImGui.getContentRegionAvailX(), 0)) {
                Mask.rules.add(new Mask.Rule());
            }
            hint("Se aplica a Painter, Noise Painter, Freehand Draw, Smooth, Melt y Weld. Todas las reglas deben cumplirse.");
            Map<String, Object> maskHolders = new LinkedHashMap<String, Object>();
            maskHolders.put("enabled", Mask.enabled);
            maskHolders.put("rules", new Presets.Custom() {
                @Override
                public String save() {
                    StringBuilder sb = new StringBuilder();
                    for (Mask.Rule r : Mask.rules) {
                        sb.append(sb.length() == 0 ? "" : ";").append(r.type.get()).append(',').append(r.not.get()).append(',')
                                .append(r.id.get()).append(',').append(r.meta[0]).append(',').append(r.y[0]).append(',')
                                .append(r.y[1]);
                    }
                    return sb.toString();
                }

                @Override
                public void load(String text) {
                    Mask.rules.clear();
                    for (String row : text.split(";")) {
                        String[] t = row.split(",");
                        if (t.length < 6) {
                            continue;
                        }
                        Mask.Rule r = new Mask.Rule();
                        r.type.set(Integer.parseInt(t[0]));
                        r.not.set(Boolean.parseBoolean(t[1]));
                        r.id.set(t[2]);
                        r.meta[0] = Integer.parseInt(t[3]);
                        r.y[0] = Integer.parseInt(t[4]);
                        r.y[1] = Integer.parseInt(t[5]);
                        Mask.rules.add(r);
                    }
                    if (Mask.rules.isEmpty()) {
                        Mask.rules.add(new Mask.Rule());
                    }
                }
            });
            presetBlock("Tool Masks", maskHolders);
        }
        ImGui.end();
    }

    /** Lista de ediciones: pulsar una deshace (o rehace) hasta ella. */
    private void historyWindow() {
        if (ImGui.begin("History", showHistory)) {
            String[] redo = EditEngine.redoNames;
            String[] undo = EditEngine.undoNames;
            if (redo.length > 0) {
                ImGui.separatorText("Rehacer");
                for (int i = redo.length - 1; i >= 0; i--) {
                    if (ImGui.selectable(redo[i] + "##r" + i)) {
                        EditEngine.redoMany(i + 1);
                    }
                }
            }
            ImGui.separatorText("Deshacer");
            if (undo.length == 0) {
                Txt.disabled("Vacío");
            }
            for (int i = 0; i < undo.length; i++) {
                if (ImGui.selectable(undo[i] + "##u" + i, i == 0)) {
                    EditEngine.undoMany(i + 1);
                }
            }
        }
        ImGui.end();
    }

    /** Guarda en los recientes cada bloque válido que pasa a ser el Active Block. */
    private void trackRecent() {
        if (activeState == null) {
            return;
        }
        String key = Palettes.entry(activeId.get().trim(), activeMeta[0]);
        if (!key.equals(lastRecentKey)) {
            lastRecentKey = key;
            Palettes.pushRecent(activeId.get().trim(), activeMeta[0]);
        }
    }

    private void useEntry(String entry) {
        activeId.set(Palettes.idOf(entry));
        activeMeta[0] = Palettes.metaOf(entry);
    }

    /** Hace que el último elemento se pueda arrastrar a una paleta (payload = "id|meta"). */
    private void dragSource(String entry) {
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("axiom_block", entry);
            Txt.text(Palettes.idOf(entry));
            ImGui.endDragDropSource();
        }
    }

    /** El último elemento acepta bloques soltados: devuelve su entrada "id|meta" o null. */
    private String dropTarget() {
        String got = null;
        if (ImGui.beginDragDropTarget()) {
            got = ImGui.acceptDragDropPayload("axiom_block", String.class);
            ImGui.endDragDropTarget();
        }
        return got;
    }

    /** Una fila de iconos de bloque; devuelve el índice pulsado (-1 ninguno) y deja en rightClicked el pulsado con el derecho. */
    private int blockCells(List<String> entries, String idPrefix, int[] rightClicked) {
        float cell = ImGui.getFrameHeight() * 1.7f;
        float avail = ImGui.getContentRegionAvailX();
        int perRow = Math.max(1, (int) (avail / (cell + ImGui.getStyle().getItemSpacingX())));
        ImDrawList dl = ImGui.getWindowDrawList();
        int clicked = -1;
        rightClicked[0] = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (i % perRow != 0) {
                ImGui.sameLine();
            }
            ImGui.pushID(idPrefix + i);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY();
            boolean click = ImGui.invisibleButton("##cell", cell, cell);
            boolean hovered = ImGui.isItemHovered();
            dl.addRectFilled(x, y, x + cell, y + cell,
                    ImGui.getColorU32(hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg), 4f);
            IBlockState st = Palettes.resolve(entries.get(i));
            if (st != null) {
                Icons.drawState(dl, st, x + cell / 2, y + cell / 2, cell - 8f);
            }
            if (hovered) {
                Txt.tooltip(Palettes.idOf(entries.get(i)) + (Palettes.metaOf(entries.get(i)) != 0 ? " (meta " + Palettes.metaOf(entries.get(i)) + ")" : ""));
            }
            if (click) {
                clicked = i;
            }
            if (hovered && ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                rightClicked[0] = i;
            }
            dragSource(entries.get(i));
            ImGui.popID();
        }
        return clicked;
    }

    /** Paleta: los 16 bloques más recientes y paletas propias (clic = Active Block, clic derecho = quitar). */
    private void paletteWindow() {
        Palettes.ensureLoaded();
        if (ImGui.begin("Palette", showPalette)) {
            int[] right = {-1};
            ImGui.separatorText("Recent");
            if (Palettes.recent.isEmpty()) {
                hint("Los bloques que uses como Active Block aparecen aquí.");
            } else {
                int c = blockCells(Palettes.recent, "rc", right);
                if (c >= 0) {
                    useEntry(Palettes.recent.get(c));
                }
            }
            ImGui.separatorText("Palettes");
            int remove = -1;
            for (int pi = 0; pi < Palettes.list.size(); pi++) {
                Palettes.Palette p = Palettes.list.get(pi);
                ImGui.pushID("pal" + pi);
                boolean open = ImGui.collapsingHeader(p.name + "##" + pi, ImGuiTreeNodeFlags.DefaultOpen);
                String dropped = dropTarget();
                if (open) {
                    ImGui.beginGroup();
                    if (p.blocks.isEmpty()) {
                        hint("Vacía: arrastra bloques aquí, o usa Add block...");
                    } else {
                        int c = blockCells(p.blocks, "b", right);
                        if (c >= 0) {
                            useEntry(p.blocks.get(c));
                        }
                        if (right[0] >= 0) {
                            p.blocks.remove(right[0]);
                            Palettes.save();
                        }
                    }
                    if (ImGui.button("Add block...")) {
                        final Palettes.Palette target = p;
                        BlockPicker.openWith(palPickId, palPickMeta, new Runnable() {
                            @Override
                            public void run() {
                                target.blocks.add(Palettes.entry(palPickId.get().trim(), palPickMeta[0]));
                                Palettes.save();
                            }
                        });
                    }
                    tip("Abre el selector de bloques; con Ctrl pulsado puedes elegir varios seguidos.");
                    ImGui.sameLine();
                    if (ImGui.button("Add Active Block") && activeState != null) {
                        p.blocks.add(Palettes.entry(activeId.get().trim(), activeMeta[0]));
                        Palettes.save();
                    }
                    ImGui.sameLine();
                    if (ImGui.button("Fill selection (random)")) {
                        StatePicker picker = Palettes.picker(p, System.nanoTime());
                        if (picker == null) {
                            EditEngine.status = "La paleta está vacía";
                        } else {
                            EditEngine.fillPicker("Fill palette " + p.name, picker);
                        }
                    }
                    ImGui.sameLine();
                    if (ImGui.button("Delete palette")) {
                        remove = pi;
                    }
                    ImGui.endGroup();
                    if (dropped == null) {
                        dropped = dropTarget();
                    }
                }
                if (dropped != null) {
                    p.blocks.add(dropped);
                    Palettes.save();
                }
                ImGui.popID();
            }
            if (remove >= 0) {
                Palettes.list.remove(remove);
                Palettes.save();
            }
            ImGui.setNextItemWidth(160);
            ImGui.inputText("##palname", paletteName);
            ImGui.sameLine();
            if (ImGui.button("New palette")) {
                Palettes.add(paletteName.get());
                paletteName.set("");
            }
            hint("Clic: usar como Active Block. Clic derecho sobre un bloque de una paleta: quitarlo. Arrastra bloques de Recent, de otra paleta o del Inventory hasta una paleta para añadirlos.");
        }
        ImGui.end();
    }

    private void activeBlockWindow() {
        if (ImGui.begin("Active Block")) {
            blockField("active", activeId, activeMeta, 0);
            if (activeState == null) {
                Txt.colored(1f, 0.4f, 0.4f, 1f, "Bloque desconocido");
            } else {
                Txt.wrapped(activeState.toString());
            }
            hint("Clic central sobre un bloque del mundo: copiarlo aquí.");
        }
        ImGui.end();
    }

    /** Tu inventario: pulsar un bloque lo copia al Active Block. */
    private void inventoryWindow() {
        if (ImGui.begin("Inventory")) {
            NonNullList<ItemStack> inv = mc.player.inventory.mainInventory;
            float cell = ImGui.getFrameHeight() * 1.7f;
            ImDrawList dl = ImGui.getWindowDrawList();
            int[] rows = {1, 2, 3, 0};
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < 9; c++) {
                    int slot = rows[r] * 9 + c;
                    ItemStack st = inv.get(slot);
                    if (c > 0) {
                        ImGui.sameLine();
                    }
                    ImGui.pushID(slot);
                    float x = ImGui.getCursorScreenPosX();
                    float y = ImGui.getCursorScreenPosY();
                    boolean clicked = ImGui.invisibleButton("##slot", cell, cell);
                    boolean hovered = ImGui.isItemHovered();
                    dl.addRectFilled(x, y, x + cell, y + cell,
                            ImGui.getColorU32(hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg), 4f);
                    if (!st.isEmpty()) {
                        Icons.draw(dl, st, x + cell / 2, y + cell / 2, cell - 8f);
                        if (st.getCount() > 1) {
                            String n = Integer.toString(st.getCount());
                            dl.addText(x + cell - ImGui.calcTextSizeX(n) - 3f,
                                    y + cell - ImGui.getTextLineHeight() - 1f, ImGui.getColorU32(ImGuiCol.Text), n);
                        }
                        if (hovered) {
                            Txt.tooltip(st.getDisplayName());
                        }
                        if (clicked && st.getItem() instanceof ItemBlock) {
                            ItemBlock ib = (ItemBlock) st.getItem();
                            activeId.set(String.valueOf(Block.REGISTRY.getNameForObject(ib.getBlock())));
                            activeMeta[0] = ib.getMetadata(st.getMetadata());
                        }
                        if (st.getItem() instanceof ItemBlock) {
                            ItemBlock ib = (ItemBlock) st.getItem();
                            dragSource(Palettes.entry(String.valueOf(Block.REGISTRY.getNameForObject(ib.getBlock())),
                                    ib.getMetadata(st.getMetadata())));
                        }
                    }
                    ImGui.popID();
                }
                if (r == 2) {
                    ImGui.spacing();
                }
            }
            hint("Clic en un bloque: usarlo como Active Block.");
        }
        ImGui.end();
    }

    /** Posiciones guardadas con nombre a las que se vuelve con un clic. */
    private void viewsWindow() {
        Views.ensureLoaded();
        if (ImGui.begin("Views")) {
            ImGui.inputText("Name", viewName);
            if (ImGui.button("Save current position", ImGui.getContentRegionAvailX(), 0)) {
                Views.add(viewName.get());
            }
            ImGui.separator();
            int remove = -1;
            for (int i = 0; i < Views.list.size(); i++) {
                Views.View v = Views.list.get(i);
                ImGui.pushID(i);
                if (ImGui.button("Go") && !Views.teleport(v)) {
                    EditEngine.status = "Esa vista está en otra dimensión";
                }
                ImGui.sameLine();
                if (ImGui.button("X")) {
                    remove = i;
                }
                ImGui.sameLine();
                Txt.text(v.name);
                if (ImGui.isItemHovered()) {
                    Txt.tooltip(String.format(Locale.ROOT, "%.0f, %.0f, %.0f", v.x, v.y, v.z));
                }
                ImGui.popID();
            }
            if (remove >= 0) {
                Views.remove(remove);
            }
            if (Views.list.isEmpty()) {
                hint("Sin vistas guardadas.");
            }
        }
        ImGui.end();
    }

    private void clipboardWindow() {
        if (ImGui.begin("Clipboard")) {
            Clipboard c = Clipboard.current;
            if (c == null) {
                hint("Vacío. Selecciona una zona y pulsa Ctrl+C.");
            } else {
                Txt.text(c.sx + " x " + c.sy + " x " + c.sz + " (" + c.solidCount() + " bloques)");
                if (!Placement.active && ImGui.button("Paste (Ctrl+V)", ImGui.getContentRegionAvailX(), 0)) {
                    startPaste();
                }
                if (ImGui.button("Save as Blueprint (Ctrl+P)", ImGui.getContentRegionAvailX(), 0)) {
                    bpOpenDialog = true;
                }
            }
        }
        ImGui.end();
    }

    /** Importar y exportar .schematic (formato MCEdit/WorldEdit) en config/litbuild/schematics. */
    private void schematicsWindow() {
        if (ImGui.begin("Schematics", showSchem)) {
            ImGui.inputText("Name", schemName);
            ImGui.combo("Format", formatPick, Schematics.FORMATS);
            int c = buttonRow("Export clipboard", "Refresh");
            if (c == 0) {
                exportSchematic();
            } else if (c == 1) {
                schemFiles = Schematics.list();
            }
            hint(Schematics.dir().getPath());
            ImGui.separator();
            if (schemFiles.isEmpty()) {
                hint("No hay archivos .schematic, .schem ni .nbt en esa carpeta.");
            }
            for (String name : schemFiles) {
                ImGui.pushID(name);
                if (ImGui.button("Import")) {
                    importSchematic(name);
                }
                ImGui.sameLine();
                Txt.text(name);
                ImGui.popID();
            }
        }
        ImGui.end();
    }

    /** Navegador de blueprints: miniaturas con búsqueda, etiquetas y subcarpetas. Clic = cargar en el Clipboard. */
    private void blueprintsWindow() {
        if (ImGui.begin("Blueprint Browser", showBlueprints)) {
            if (bpDirty) {
                bpDirty = false;
                bpTags = Blueprints.allTags();
                bpTagNames = new String[bpTags.size() + 1];
                bpTagNames[0] = "(any tag)";
                for (int i = 0; i < bpTags.size(); i++) {
                    bpTagNames[i + 1] = bpTags.get(i);
                }
                if (bpTagPick.get() >= bpTagNames.length) {
                    bpTagPick.set(0);
                }
                Blueprints.refresh(bpFolder, bpRecursive.get());
            }
            ImGui.inputText("Search", bpSearch);
            ImGui.combo("Tag", bpTagPick, bpTagNames);
            if (ImGui.checkbox("Search inside folders", bpRecursive)) {
                bpDirty = true;
            }
            ImGui.sameLine();
            if (ImGui.button("Refresh")) {
                bpDirty = true;
            }
            ImGui.sameLine();
            if (ImGui.button("Save clipboard...") && Clipboard.current != null) {
                bpOpenDialog = true;
            }
            Txt.disabled("/" + bpFolder);
            if (!bpFolder.isEmpty()) {
                if (ImGui.button("..")) {
                    int cut = bpFolder.lastIndexOf('/');
                    bpFolder = cut < 0 ? "" : bpFolder.substring(0, cut);
                    bpDirty = true;
                }
                ImGui.sameLine();
            }
            for (String sub : Blueprints.subfolders(bpFolder)) {
                if (ImGui.button(sub + "/")) {
                    bpFolder = bpFolder.isEmpty() ? sub : bpFolder + "/" + sub;
                    bpDirty = true;
                }
                ImGui.sameLine();
            }
            ImGui.newLine();
            ImGui.separator();
            if (ImGui.beginChild("bpgrid")) {
                String q = bpSearch.get().trim().toLowerCase(Locale.ROOT);
                String tag = bpTagPick.get() > 0 ? bpTagNames[bpTagPick.get()] : null;
                float cell = Blueprints.THUMB + 16;
                int perRow = Math.max(1, (int) (ImGui.getContentRegionAvailX() / cell));
                int shown = 0;
                for (Blueprints.Entry e : Blueprints.entries) {
                    if (!q.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(q)
                            && !e.authors.toLowerCase(Locale.ROOT).contains(q)) {
                        continue;
                    }
                    if (tag != null && !e.tags.contains(tag)) {
                        continue;
                    }
                    ImGui.pushID(e.rel);
                    ImGui.beginGroup();
                    int tex = Blueprints.texture(e);
                    boolean clicked;
                    if (tex != 0) {
                        clicked = ImGui.imageButton("##bp", tex, Blueprints.THUMB, Blueprints.THUMB);
                    } else {
                        clicked = ImGui.button(e.error != null ? "Error" : "...", Blueprints.THUMB + 8, Blueprints.THUMB + 8);
                    }
                    Txt.text(e.name.length() > 14 ? e.name.substring(0, 13) + "~" : e.name);
                    ImGui.endGroup();
                    if (ImGui.isItemHovered()) {
                        if (e.error != null) {
                            Txt.tooltip(e.rel + "\n" + e.error);
                        } else {
                            Txt.tooltip(e.name + (e.authors.isEmpty() ? "" : "\npor " + e.authors) + "\n" + e.sx + " x " + e.sy
                                    + " x " + e.sz + " (" + e.blocks + " bloques)"
                                    + (e.tags.isEmpty() ? "" : "\n" + String.join(", ", e.tags)) + "\n" + e.rel);
                        }
                    }
                    if (ImGui.beginPopupContextItem("bpctx")) {
                        if (ImGui.menuItem("Load into Clipboard")) {
                            loadBlueprint(e, false);
                        }
                        if (ImGui.menuItem("Load and place")) {
                            loadBlueprint(e, true);
                        }
                        ImGui.endPopup();
                    }
                    if (clicked) {
                        loadBlueprint(e, false);
                    }
                    ImGui.popID();
                    shown++;
                    if (shown % perRow != 0) {
                        ImGui.sameLine();
                    }
                }
                if (shown == 0) {
                    hint(Blueprints.scanning ? "Leyendo blueprints..." : "No hay blueprints aquí. Copia algo (Ctrl+C) y pulsa Ctrl+P para guardar uno.");
                }
                hint(Blueprints.dir().getPath());
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private void loadBlueprint(Blueprints.Entry e, boolean place) {
        try {
            Clipboard c = Blueprints.load(e);
            Clipboard.current = c;
            EditEngine.status = "Blueprint '" + e.name + "' cargado en el Clipboard (" + c.solidCount() + " bloques)"
                    + (Schematics.unknown > 0 ? "; " + Schematics.unknown + " bloques desconocidos quedan como aire" : "");
            if (place && !Placement.active) {
                startPaste();
            }
        } catch (IOException ex) {
            EditEngine.status = "No se pudo abrir el blueprint: " + ex.getMessage();
        }
    }

    /** Ventana modal para guardar el Clipboard como blueprint (Ctrl+P). */
    private void blueprintDialog() {
        if (bpOpenDialog) {
            bpOpenDialog = false;
            if (Clipboard.current != null) {
                bpTags = Blueprints.allTags();
                bpTagOn = new boolean[bpTags.size()];
                bpRot = 0;
                bpFolder = bpFolder == null ? "" : bpFolder;
                bpSaveFolder.set(bpFolder);
                newBlueprintThumb();
                ImGui.openPopup("Create Blueprint");
            }
        }
        if (!ImGui.beginPopupModal("Create Blueprint", ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        Clipboard c = Clipboard.current;
        if (c == null) {
            ImGui.closeCurrentPopup();
            ImGui.endPopup();
            return;
        }
        ImGui.inputText("Name", bpName);
        ImGui.inputText("Authors", bpAuthors);
        ImGui.inputText("Folder", bpSaveFolder);
        tip("Carpeta dentro de la carpeta de blueprints; se crea si no existe. Vacío = raíz.");
        if (bpThumbTex != 0) {
            ImGui.image(bpThumbTex, Blueprints.THUMB, Blueprints.THUMB);
            ImGui.sameLine();
        }
        ImGui.beginGroup();
        Txt.text(c.sx + " x " + c.sy + " x " + c.sz + " (" + c.solidCount() + " bloques)");
        if (ImGui.button("Rotate thumbnail")) {
            bpRot++;
            newBlueprintThumb();
        }
        ImGui.endGroup();
        ImGui.separatorText("Tags");
        for (int i = 0; i < bpTags.size(); i++) {
            if (ImGui.checkbox(bpTags.get(i), bpTagOn[i])) {
                bpTagOn[i] = !bpTagOn[i];
            }
            if (i % 4 != 3 && i + 1 < bpTags.size()) {
                ImGui.sameLine(((i % 4) + 1) * 140f);
            }
        }
        ImGui.setNextItemWidth(160);
        ImGui.inputText("##newtag", bpNewTag);
        ImGui.sameLine();
        if (ImGui.button("Add tag") && !bpNewTag.get().trim().isEmpty()) {
            String added = bpNewTag.get().trim();
            Blueprints.addTag(added);
            List<String> old = bpTags;
            boolean[] oldOn = bpTagOn;
            bpTags = Blueprints.allTags();
            bpTagOn = new boolean[bpTags.size()];
            for (int i = 0; i < bpTags.size(); i++) {
                bpTagOn[i] = bpTags.get(i).equalsIgnoreCase(added) || old.contains(bpTags.get(i)) && oldOn[old.indexOf(bpTags.get(i))];
            }
            bpNewTag.set("");
            bpDirty = true;
        }
        ImGui.separator();
        boolean close = false;
        if (ImGui.button("Save", 120, 0)) {
            String name = bpName.get().trim();
            if (name.isEmpty()) {
                EditEngine.status = "Pon un nombre al blueprint";
            } else {
                List<String> chosen = new ArrayList<String>();
                for (int i = 0; i < bpTags.size(); i++) {
                    if (bpTagOn[i]) {
                        chosen.add(bpTags.get(i));
                    }
                }
                try {
                    File f = Blueprints.save(c, name, bpAuthors.get(), chosen, bpRot, bpSaveFolder.get());
                    EditEngine.status = "Blueprint guardado: " + f.getPath();
                    bpDirty = true;
                    close = true;
                } catch (IOException ex) {
                    EditEngine.status = "No se pudo guardar el blueprint: " + ex.getMessage();
                }
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel", 120, 0)) {
            close = true;
        }
        if (close) {
            Blueprints.free(bpThumbTex);
            bpThumbTex = 0;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void newBlueprintThumb() {
        Blueprints.free(bpThumbTex);
        BlockColors.ensureBuilt();
        bpThumbTex = Blueprints.upload(Blueprints.thumbnail(Clipboard.current, bpRot));
    }

    private void placementWindow() {
        if (ImGui.begin("Placement")) {
            Clipboard s = Placement.shape;
            Txt.text(s.sx + " x " + s.sy + " x " + s.sz);
            if (ImGui.inputInt3("Pos", Placement.pos)) {
                Placement.follow = false;
            }
            hint(Placement.follow
                    ? "Sigue al cursor. Clic derecho: fijar."
                    : "Fijada. Flechas / AvPág / RePág: mover. Clic derecho: volver a seguir el cursor.");
            ImGui.checkbox("Keep Existing", Placement.KEEP_EXISTING);
            ImGui.sameLine();
            ImGui.checkbox("Paste Air", Placement.PASTE_AIR);
            int t = buttonRow("Rotate", "Flip X", "Flip Z");
            if (t == 0) {
                Placement.rotate();
            } else if (t == 1) {
                Placement.flip(true);
            } else if (t == 2) {
                Placement.flip(false);
            }
            if (ImGui.collapsingHeader("Free transform")) {
                boolean changed = ImGui.sliderFloat3("Yaw Pitch Roll", Placement.angles, -180f, 180f, "%.0f");
                changed |= ImGui.sliderFloat3("Scale", Placement.scale, 0.1f, 8f, "%.2f");
                if (changed && !Placement.refresh()) {
                    EditEngine.status = "Placement: demasiado grande";
                    Placement.resetTransform();
                    Placement.refresh();
                }
                int f = buttonRow("Scale x2", "Scale x3", "Reset");
                if (f == 0 || f == 1) {
                    if (!Placement.scaleUp(f + 2)) {
                        EditEngine.status = "Placement: demasiado grande para escalar";
                    }
                } else if (f == 2) {
                    Placement.resetTransform();
                    Placement.refresh();
                }
                hint("Los giros que no son múltiplos de 90° usan el bloque más cercano; la orientación de escaleras o troncos solo sigue el yaw en cuartos de vuelta.");
            }
            int p = buttonRow("Paste", "Paste Copy", "Cancel");
            if (p == 0) {
                confirmPaste(false);
            } else if (p == 1) {
                confirmPaste(true);
            } else if (p == 2) {
                Placement.cancel();
            }
            hint("Ctrl+R rotar · Ctrl+F voltear · Enter pegar · Supr cancelar");
        }
        ImGui.end();
    }

    private void shortcuts(ImGuiIO io) {
        Keybinds.init(TOOLS, TOOL_KEYS);
        if (io.getWantTextInput() || Keybinds.capturing != null) {
            return;
        }
        if (Keybinds.pressed("undo")) {
            if (tool == ANNOT && Annotations.canUndo()) {
                Annotations.undo();
            } else {
                EditEngine.undo();
            }
        } else if (Keybinds.pressed("redo")) {
            if (tool == ANNOT && Annotations.canRedo()) {
                Annotations.redo();
            } else {
                EditEngine.redo();
            }
        } else if (Placement.active) {
            placementKeys();
        } else if (tool == MODEL && Keybinds.pressed("extrudepoint")) {
            Modelling.extrude();
        } else if (!shortcutTool() && !normalShortcut()) {
            if (Selection.active && ImGui.isKeyPressed(ImGuiKey.PageUp, true)) {
                // AvPág / RePág: estirar la selección hacia arriba / abajo (con Shift, encogerla).
                Selection.expand(EnumFacing.UP, io.getKeyShift() ? -expandAmount[0] : expandAmount[0]);
            } else if (Selection.active && ImGui.isKeyPressed(ImGuiKey.PageDown, true)) {
                Selection.expand(EnumFacing.DOWN, io.getKeyShift() ? -expandAmount[0] : expandAmount[0]);
            }
        }
    }

    /** Cambia de herramienta si se pulsó la tecla de alguna (o Swap To Last Tool). */
    private boolean shortcutTool() {
        for (int i = 0; i < IMPLEMENTED; i++) {
            if (Keybinds.pressed("tool." + i)) {
                tool = i;
                return true;
            }
        }
        if (Keybinds.pressed("swaplast")) {
            tool = lastTool;
            return true;
        }
        return false;
    }

    /** Operaciones y edición con el teclado; false si no se pulsó ninguna. */
    private boolean normalShortcut() {
        if (Keybinds.pressed("showbiomes")) {
            BiomeView.show.set(!BiomeView.show.get());
        } else if (Keybinds.pressed("quickfill")) {
            fill();
        } else if (Keybinds.pressed("quickreplace")) {
            replace();
        } else if (Keybinds.pressed("saveblueprint")) {
            if (Clipboard.current == null) {
                EditEngine.status = "Copia algo antes de guardar un blueprint";
            } else {
                bpOpenDialog = true;
            }
        } else if (Keybinds.pressed("copy")) {
            EditEngine.copy(false);
        } else if (Keybinds.pressed("cut")) {
            EditEngine.copy(true);
        } else if (Keybinds.pressed("paste")) {
            startPaste();
        } else if (Keybinds.pressed("delete")) {
            deleteSelection();
        } else {
            return false;
        }
        return true;
    }

    private void placementKeys() {
        EnumFacing f = mc.player.getHorizontalFacing();
        if (Keybinds.pressed("pl.confirm") || Keybinds.pressed("paste")) {
            confirmPaste(false);
        } else if (Keybinds.pressed("pl.cancel") || ImGui.isKeyPressed(ImGuiKey.Backspace, false)) {
            Placement.cancel();
        } else if (Keybinds.pressed("pl.rotate")) {
            Placement.rotate();
        } else if (Keybinds.pressed("pl.flip")) {
            Placement.flip(f.getAxis() == EnumFacing.Axis.X);
        } else if (ImGui.isKeyPressed(ImGuiKey.UpArrow, true)) {
            Placement.nudge(f.getFrontOffsetX(), 0, f.getFrontOffsetZ());
        } else if (ImGui.isKeyPressed(ImGuiKey.DownArrow, true)) {
            Placement.nudge(-f.getFrontOffsetX(), 0, -f.getFrontOffsetZ());
        } else if (ImGui.isKeyPressed(ImGuiKey.RightArrow, true)) {
            EnumFacing r = f.rotateY();
            Placement.nudge(r.getFrontOffsetX(), 0, r.getFrontOffsetZ());
        } else if (ImGui.isKeyPressed(ImGuiKey.LeftArrow, true)) {
            EnumFacing l = f.rotateYCCW();
            Placement.nudge(l.getFrontOffsetX(), 0, l.getFrontOffsetZ());
        } else if (ImGui.isKeyPressed(ImGuiKey.PageUp, true)) {
            Placement.nudge(0, 1, 0);
        } else if (ImGui.isKeyPressed(ImGuiKey.PageDown, true)) {
            Placement.nudge(0, -1, 0);
        }
    }

    // ---- Entrada sobre el mundo ------------------------------------------------------------

    /** Herramientas de terreno: el radio está limitado y trabajan sobre el mundo leído alrededor del pincel. */
    private boolean isTerrain() {
        return tool == SMOOTH || tool == MELT || tool == WELD || tool == ELEVATION || tool == DISTORT
                || tool == ROUGHEN || tool == SHATTER || tool == SCRIPT || tool == SCULPT;
    }

    private boolean isSmooth() {
        return tool == SMOOTH || tool == MELT || tool == WELD;
    }

    private void viewport(ImGuiIO io) {
        boolean overUi = io.getWantCaptureMouse();
        boolean terrain = isTerrain();
        boolean brush = tool == PAINTER || tool == DRAW || tool == NOISE || terrain || tool == GRADIENT
                || tool == FSELECT || tool == SLOPE || tool == BIOME || tool == FLUID;
        BlockPos pos = null;
        BlockPos base = null;
        EnumFacing side = null;
        Vec3d hitVec = null;
        if (!overUi) {
            RayTraceResult hit = Targeting.pick(io.getMousePosX(), io.getMousePosY(), 256);
            if (hit != null) {
                pos = hit.getBlockPos();
                side = hit.sideHit;
                hitVec = hit.hitVec;
                base = pos.offset(side);
            }
        }
        Targeting.hover = pos;
        if (terrain && Brush.radius[0] > TERRAIN_MAX_RADIUS) {
            Brush.radius[0] = TERRAIN_MAX_RADIUS;
        }

        // Manejadores de Box Select: arrastrar una cara con el botón izquierdo la estira o la encoge.
        float mx = io.getMousePosX(), my = io.getMousePosY();
        boolean gizmoUsable = tool == BOX && !Placement.active && Selection.active && Selection.mask == null
                && (!overUi || BoxGizmo.dragging);
        boolean leftNow = ImGui.isMouseDown(ImGuiMouseButton.Left);
        BoxGizmo.update(gizmoUsable, !leftNow || ImGui.isMouseClicked(ImGuiMouseButton.Left, false), mx, my,
                ImGui.getFrameHeight() * 0.9f);
        if (BoxGizmo.dragging) {
            if (leftNow) {
                BoxGizmo.drag(mx, my);
            } else {
                BoxGizmo.end();
            }
        } else if (BoxGizmo.hovered >= 0 && ImGui.isMouseClicked(ImGuiMouseButton.Left, false)) {
            BoxGizmo.begin(mx, my);
        }

        if (!overUi) {
            // Cámara: arrastrar con el botón izquierdo (misma sensibilidad que el ratón normal).
            if (ImGui.isMouseDown(ImGuiMouseButton.Left) && !BoxGizmo.dragging && BoxGizmo.hovered < 0) {
                float s = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
                float k = s * s * s * 8f;
                mc.player.turn(io.getMouseDeltaX() * k, -io.getMouseDeltaY() * k);
            }
            boolean useTool = pos != null && ImGui.isMouseClicked(ImGuiMouseButton.Right, false);
            if (Placement.active) {
                // Con un Placement en curso el botón derecho lo fija o lo suelta.
                if (Placement.follow && base != null) {
                    Placement.moveTo(base);
                }
                if (ImGui.isMouseClicked(ImGuiMouseButton.Right, false)) {
                    Placement.follow = !Placement.follow;
                }
            } else if (tool == BOX) {
                // Box Select: botón derecho, arrastrar entre dos bloques.
                if (useTool) {
                    Selection.set(pos, pos);
                    dragging = true;
                } else if (dragging && pos != null && ImGui.isMouseDown(ImGuiMouseButton.Right)) {
                    Selection.setSecond(pos);
                }
            } else if (tool == MAGIC) {
                if (useTool) {
                    MagicSelect.select(pos);
                }
            } else if (tool == EXTRUDE) {
                if (useTool) {
                    EditEngine.extrude(pos, side, extrudeCount[0], extrudeShrink.get() || io.getKeyShift(),
                            extrudeCompare.get());
                }
            } else if (tool == RULER) {
                if (useTool) {
                    Ruler.add(pos);
                }
            } else if (tool == SYMMETRY) {
                if (useTool && hitVec != null) {
                    Symmetry.setNode(hitVec);
                }
            } else if (tool == ROCK) {
                if (useTool) {
                    placeRock(base);
                }
            } else if (tool == FLOODFILL) {
                if (useTool) {
                    floodFill(base);
                }
            } else if (tool == PATH) {
                if (useTool) {
                    PathTool.nodes.add(base);
                }
            } else if (tool == MODEL) {
                if (useTool) {
                    Modelling.add(Modelling.offsetTarget.get() ? base : pos);
                }
            } else if (tool == ANNOT) {
                Annotations.handle(useTool, ImGui.isMouseDown(ImGuiMouseButton.Right), pos, side, hitVec);
            } else if (tool == LASSO) {
                if (ImGui.isMouseClicked(ImGuiMouseButton.Right, false)) {
                    Lasso.begin(mx, my);
                }
            } else if (tool == MODIFY) {
                if (useTool && io.getKeyCtrl()) {
                    Modify.pivot[0] = pos.getX();
                    Modify.pivot[1] = pos.getY();
                    Modify.pivot[2] = pos.getZ();
                    Modify.pivotSet = true;
                }
            } else if (tool == SLOPE) {
                if (useTool) {
                    if (SlopeTool.target == null || io.getKeyCtrl()) {
                        SlopeTool.target = pos;
                        EditEngine.status = "Slope: objetivo fijado; arrastra desde otro bloque";
                    } else {
                        strokeStart = pos;
                        startStroke();
                    }
                }
            } else if (tool == GRADIENT && useTool && !gradFromStart.get() && io.getKeyCtrl()) {
                gradOrigin[0] = pos.getX();
                gradOrigin[1] = pos.getY();
                gradOrigin[2] = pos.getZ();
            } else if ((brush || tool == STAMP) && useTool && !stroking) {
                strokeStart = pos;
                startStroke();
            }
            if (pos != null && ImGui.isMouseClicked(ImGuiMouseButton.Middle, false)) {
                pickBlock(pos);
            }
        }
        if (stroking) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Right)) {
                int r = tool == STAMP ? stampScatter[0] : Brush.radius[0];
                int spacing = tool == STAMP ? stampSpacing[0] : Math.max(1, r / 2);
                if (pos != null && (lastStamp == null || lastStamp.distanceSq(pos) >= spacing * spacing)) {
                    stamp(pos, r);
                    lastStamp = pos;
                }
            } else {
                endStroke();
            }
        }
        if (Lasso.active) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Right)) {
                Lasso.add(mx, my);
            } else {
                finishLasso();
            }
        }
        if (Lasso.active && Lasso.points.size() > 1) {
            drawLasso();
        }
        Modify.showPivot = tool == MODIFY && Modify.pivotSet && Modify.mode.get() != Modify.TRANSLATE;
        Brush.preview = !overUi && pos != null && !Placement.active && brush;
        if (!ImGui.isMouseDown(ImGuiMouseButton.Right)) {
            dragging = false;
        }
    }

    /** Empieza un trazo de pincel: resuelve la máscara y el ruido y abre la edición deshacible. */
    private void startStroke() {
        maskSpec = Mask.snapshot();
        if (Mask.error) {
            return;
        }
        if (tool == SLOPE) {
            BlockPos t = SlopeTool.target;
            double dx = t.getX() - strokeStart.getX(), dz = t.getZ() - strokeStart.getZ();
            double dy = t.getY() - strokeStart.getY();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 1) {
                EditEngine.status = "Slope: el inicio está en la misma columna que el objetivo";
                return;
            }
            slopeFrom = strokeStart;
            slopeConeDone = false;
            EditEngine.status = String.format(Locale.ROOT, "Slope: diferencia de altura %d, ángulo %.1f°, distancia %.1f",
                    (int) dy, Math.toDegrees(Math.atan2(Math.abs(dy), dist)), Math.sqrt(dist * dist + dy * dy));
        }
        if (tool == BIOME) {
            biomeActive = true;
            EditEngine.biomeStrokeStart();
            stroking = true;
            lastStamp = null;
            return;
        }
        if (tool == FSELECT) {
            freeAcc = new HashSet<Long>();
            stroking = true;
            lastStamp = null;
            return;
        }
        if (tool == STAMP && !prepareStamp()) {
            return;
        }
        spec = tool == NOISE ? buildSpec() : tool == GRADIENT ? buildGradient() : null;
        boolean needsBlock = tool == PAINTER || tool == DRAW || tool == WELD || (tool == SHATTER && shatterFill.get());
        if (tool == NOISE || tool == GRADIENT ? spec == null : needsBlock && activeState == null) {
            if (tool != NOISE && tool != GRADIENT) {
                EditEngine.status = "El bloque activo no es válido";
            }
            return;
        }
        distortNoise = tool == DISTORT ? new Noise(distSeed.get()) : null;
        scriptSpec = tool == SCRIPT ? ScriptBrush.build(scriptCode.get()) : null;
        stroking = true;
        lastStamp = null;
        // Elevation es acumulativa: puede tocar varias veces el mismo bloque en un trazo.
        EditEngine.strokeStart(TOOLS[tool] + (needsBlock ? " " + activeId.get().trim() : ""),
                tool != ELEVATION && tool != SCULPT && tool != SLOPE);
    }

    /** Suelta el lazo: los bloques que cubre se combinan con la selección según el modo. */
    private void finishLasso() {
        Set<Long> found = Lasso.finish();
        if (found.isEmpty()) {
            EditEngine.status = "Lasso: no hay bloques dentro del lazo";
        } else if (!Selection.combine(found, fsMode.get())) {
            EditEngine.status = "Lasso: la selección actual es demasiado grande para combinarla";
        } else {
            EditEngine.status = "Lasso: " + found.size() + " bloques";
        }
    }

    /** El contorno del lazo sobre la pantalla, con la línea de cierre más tenue. */
    private void drawLasso() {
        imgui.ImDrawList dl = ImGui.getForegroundDrawList();
        java.util.List<float[]> pts = Lasso.points;
        for (int i = 1; i < pts.size(); i++) {
            dl.addLine(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1], 0xFFFFD040, 2f);
        }
        float[] a = pts.get(pts.size() - 1), b = pts.get(0);
        dl.addLine(a[0], a[1], b[0], b[1], 0x80FFD040, 1f);
    }

    /** Cierra el trazo en curso: confirma la edición deshacible (o, en Freehand Select, suelta el acumulado). */
    private void endStroke() {
        if (freeAcc != null) {
            freeAcc = null;
        } else if (biomeActive) {
            biomeActive = false;
            EditEngine.biomeStrokeEnd();
        } else {
            EditEngine.strokeEnd();
        }
        stroking = false;
    }

    /** Un sello del pincel actual en pos y en todas sus posiciones simétricas. */
    private void stamp(BlockPos pos, int r) {
        if (tool == FSELECT) {
            freeSelectStamp(pos, r);
            return;
        }
        if (tool == SLOPE) {
            stampAt(pos, r);
            return;
        }
        for (BlockPos p : Symmetry.expand(pos)) {
            stampAt(p, r);
        }
    }

    private void stampAt(BlockPos pos, final int r) {
        final int cx = pos.getX(), cy = pos.getY(), cz = pos.getZ();
        final MaskSpec mask = maskSpec;
        if (isSmooth()) {
            int ratio = tool == MELT ? meltRatio[0] : tool == WELD ? weldRatio[0] : smoothRatio[0];
            EditEngine.strokeTerrain(pos, r, Brush.shape.get(), smoothStrength[0], ratio,
                    tool == WELD ? activeState : null, mask, 0, 0);
        } else if (tool == FLUID) {
            final int type = fluidType.get();
            EditEngine.strokeOp(new EditEngine.TerrainOp() {
                @Override
                public void apply(World w, Terrain.Setter out) {
                    FluidBall.apply(w, cx, cy, cz, r, type, out);
                }
            });
        } else if (tool == ROUGHEN) {
            EditEngine.strokeTerrain(pos, r, Brush.shape.get(), smoothStrength[0], 100, null, mask, roughAmount[0],
                    roughSeed.get());
        } else if (tool == SHATTER) {
            final int shape = Brush.shape.get();
            final double scale = shatterScale[0];
            final double width = shatterWidth[0];
            final long seed = shatterSeed.get();
            final IBlockState fill = shatterFill.get() ? activeState : null;
            EditEngine.strokeOp(new EditEngine.TerrainOp() {
                @Override
                public void apply(World w, Terrain.Setter out) {
                    Terrain.shatter(w, cx, cy, cz, r, shape, scale, width, seed, fill, mask, out);
                }
            });
        } else if (tool == STAMP) {
            stampAtPos(pos);
        } else if (tool == BIOME) {
            EditEngine.biomePaint(cx, cz, r, biomeIds[biomePick.get()]);
        } else if (tool == SCULPT) {
            final double strength = sculptStrength[0];
            final boolean invert = sculptInvert.get(), maskY = sculptMaskY.get(), denoise = sculptDenoise.get();
            EditEngine.strokeOp(new EditEngine.TerrainOp() {
                @Override
                public void apply(World w, Terrain.Setter out) {
                    Terrain.sculpt(w, cx, cy, cz, r, strength, invert, maskY, denoise, mask, out);
                }
            });
        } else if (tool == SLOPE) {
            final boolean cone = SlopeTool.cone();
            if (cone && slopeConeDone) {
                return;
            }
            slopeConeDone = true;
            final BlockPos from = slopeFrom, to = SlopeTool.target;
            final int height = SlopeTool.height[0];
            final double sharp = SlopeTool.sharpness[0];
            final int centerX = cone ? from.getX() : cx, centerZ = cone ? from.getZ() : cz;
            EditEngine.strokeOp(new EditEngine.TerrainOp() {
                @Override
                public void apply(World w, Terrain.Setter out) {
                    Terrain.slope(w, centerX, centerZ, r, from.getX(), from.getY(), from.getZ(), to.getX(),
                            to.getY(), to.getZ(), cone, height, sharp, mask, out);
                }
            });
        } else if (tool == SCRIPT) {
            final ScriptBrush.Spec script = scriptSpec;
            final int shape = Brush.shape.get();
            EditEngine.strokeOp(new EditEngine.TerrainOp() {
                @Override
                public void apply(World w, Terrain.Setter out) {
                    script.run(w, cx, cy, cz, r, shape, mask, out);
                }
            });
        } else if (tool == ELEVATION) {
            final int mode = elevMode.get();
            final int amount = elevAmount[0];
            EditEngine.strokeOp(new EditEngine.TerrainOp() {
                @Override
                public void apply(World w, Terrain.Setter out) {
                    Terrain.elevate(w, cx, cy, cz, r, mode, amount, mask, out);
                }
            });
        } else if (tool == DISTORT) {
            final int shape = Brush.shape.get();
            final double scale = distScale[0];
            final double distance = distDistance[0];
            final Noise noise = distortNoise;
            EditEngine.strokeOp(new EditEngine.TerrainOp() {
                @Override
                public void apply(World w, Terrain.Setter out) {
                    Terrain.distort(w, cx, cy, cz, r, shape, scale, distance, noise, mask, out);
                }
            });
        } else {
            EditEngine.Stamp s = new EditEngine.Stamp();
            s.draw = tool == DRAW;
            s.center = pos;
            s.radius = r;
            s.shape = Brush.shape.get();
            s.maskSurface = Brush.maskSurface.get();
            s.state = activeState;
            s.noise = spec;
            s.mask = mask;
            EditEngine.strokeBrush(s);
        }
    }

    private void placeRock(BlockPos at) {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        MaskSpec mask = Mask.snapshot();
        if (Mask.error) {
            return;
        }
        long seed = rockRandom.get() ? System.nanoTime() : rockSeed.get();
        EditEngine.rock(at, rockRadius[0], rockNoise[0], rockScale[0], seed, activeState, mask);
    }

    private void floodFill(BlockPos start) {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        EditEngine.floodFill(start, activeState, floodLimit[0], floodUp.get(), floodDown.get(), floodHoriz.get(),
                floodCorners.get());
    }

    private void applyPath() {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        if (PathTool.nodes.isEmpty()) {
            EditEngine.status = "Path: no hay nodos";
            return;
        }
        EditEngine.applyPositions("Path " + PathTool.TYPES[PathTool.type.get()], PathTool.voxels(), activeState,
                PathTool.keepExisting.get());
    }

    /** Escribe el texto con el bloque activo y lo deja como Placement. */
    private void createText() {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        Clipboard c = TextTool.render(textStr.get(), TextTool.FONTS[textFont.get()], textSize[0], textBold.get(),
                textItalic.get(), textFlat.get(), activeState);
        if (c == null) {
            EditEngine.status = "Text: no hay nada que dibujar o es demasiado grande";
            return;
        }
        Placement.start(c);
        Placement.moveTo(mc.player.getPosition());
        EditEngine.status = "Text: " + c.sx + " x " + c.sy + " x " + c.sz;
    }

    private void pickBlock(BlockPos pos) {
        IBlockState s = mc.world.getBlockState(pos);
        Block b = s.getBlock();
        activeId.set(String.valueOf(Block.REGISTRY.getNameForObject(b)));
        activeMeta[0] = b.getMetaFromState(s);
    }

    private void resolveActive() {
        Block b = Block.getBlockFromName(activeId.get().trim());
        activeState = null;
        if (b != null) {
            try {
                activeState = b.getStateFromMeta(activeMeta[0]);
            } catch (RuntimeException e) {
                activeState = b.getDefaultState();
            }
        }
    }

    /** Rellena ids y pesos desde una lista de filas; false (con mensaje) si algún bloque no existe. */
    private boolean resolveRows(List<NoiseRow> rows, int[] ids, int[] weights, String label) {
        for (int i = 0; i < rows.size(); i++) {
            NoiseRow row = rows.get(i);
            Block b = Block.getBlockFromName(row.id.get().trim());
            if (b == null) {
                EditEngine.status = label + ": bloque desconocido en la fila " + (i + 1);
                return false;
            }
            IBlockState s;
            try {
                s = b.getStateFromMeta(row.meta[0]);
            } catch (RuntimeException e) {
                s = b.getDefaultState();
            }
            ids[i] = Block.getStateId(s);
            weights[i] = row.weight[0];
        }
        return true;
    }

    /** Resuelve la lista de bloques del Noise Painter; null (con mensaje) si alguno no existe. */
    private NoiseSpec buildSpec() {
        int n = noiseRows.size();
        int[] ids = new int[n];
        int[] weights = new int[n];
        if (!resolveRows(noiseRows, ids, weights, "Noise Painter")) {
            return null;
        }
        return new NoiseSpec(ids, weights, noiseScale[0], noiseOctaves[0], noise3d.get(), noiseWhite.get(),
                noiseSeed.get());
    }

    /** Resuelve el Gradient Painter; el origen es el primer bloque del trazo o el fijado a mano. */
    private GradientSpec buildGradient() {
        int n = gradRows.size();
        int[] ids = new int[n];
        int[] weights = new int[n];
        if (!resolveRows(gradRows, ids, weights, "Gradient Painter")) {
            return null;
        }
        BlockPos origin = gradFromStart.get() ? strokeStart : new BlockPos(gradOrigin[0], gradOrigin[1], gradOrigin[2]);
        return new GradientSpec(ids, weights, origin, gradDir.get(), gradLength[0], gradInterp.get(),
                gradClamp.get(), 4242L);
    }

    /** Freehand Select: suma al acumulado los bloques bajo el pincel y actualiza la selección en vivo. */
    private void freeSelectStamp(BlockPos c, int r) {
        net.minecraft.world.World w = mc.world;
        int shape = Brush.shape.get();
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!Brush.contains(shape, dx, dy, dz, r) || freeAcc.size() >= EditEngine.MAX_BLOCKS) {
                        continue;
                    }
                    BlockPos p = c.add(dx, dy, dz);
                    if (p.getY() < 0 || p.getY() > 255) {
                        continue;
                    }
                    if (!fsSolid.get() || w.getBlockState(p).getMaterial().isSolid()) {
                        freeAcc.add(p.toLong());
                    }
                }
            }
        }
        if (!Selection.combine(new HashSet<Long>(freeAcc), fsMode.get())) {
            EditEngine.status = "Freehand Select: la selección actual es demasiado grande para combinarla";
        }
    }

    /** Stamp: prepara las 8 variantes (4 giros x volteo) del portapapeles. false si no hay nada que estampar. */
    private boolean prepareStamp() {
        Clipboard c = Clipboard.current;
        if (c == null) {
            EditEngine.status = "Stamp: copia algo primero (Copy)";
            return false;
        }
        stampVariants = new Clipboard[8];
        Clipboard v = c;
        for (int q = 0; q < 4; q++) {
            stampVariants[q] = v;
            stampVariants[4 + q] = v.mirrored(true);
            v = v.rotated();
        }
        stampRandom = new java.util.Random();
        return true;
    }

    /** Un Stamp: una variante al azar apoyada en el terreno cerca de pos. */
    private void stampAtPos(BlockPos pos) {
        final java.util.Random rnd = stampRandom;
        if (rnd.nextInt(100) >= stampChance[0]) {
            return;
        }
        final int variant = (stampRotate.get() ? rnd.nextInt(4) : 0) + (stampFlip.get() && rnd.nextBoolean() ? 4 : 0);
        final int scatter = stampScatter[0];
        final int sx = pos.getX() + (scatter == 0 ? 0 : rnd.nextInt(2 * scatter + 1) - scatter);
        final int sz = pos.getZ() + (scatter == 0 ? 0 : rnd.nextInt(2 * scatter + 1) - scatter);
        final int hintY = pos.getY();
        final int drop = stampDrop[0];
        final boolean keep = stampKeep.get();
        final Clipboard c = stampVariants[variant];
        final int air = Clipboard.AIR;
        EditEngine.strokeOp(new EditEngine.TerrainOp() {
            @Override
            public void apply(World w, Terrain.Setter out) {
                int top = Terrain.surfaceY(w, sx, sz, hintY + 12, hintY - 12);
                if (top < 0) {
                    return;
                }
                int ox = sx - c.sx / 2, oy = top + 1 - drop, oz = sz - c.sz / 2;
                for (int y = 0; y < c.sy; y++) {
                    for (int z = 0; z < c.sz; z++) {
                        for (int x = 0; x < c.sx; x++) {
                            int id = c.states[c.index(x, y, z)];
                            if (id == air) {
                                continue;
                            }
                            int wx = ox + x, wy = oy + y, wz = oz + z;
                            if (wy < 0 || wy > 255) {
                                continue;
                            }
                            if (keep && !w.isAirBlock(new BlockPos(wx, wy, wz))) {
                                continue;
                            }
                            out.set(wx, wy, wz, Block.getStateById(id));
                        }
                    }
                }
            }
        });
    }

    // ---- Operaciones -----------------------------------------------------------------------

    private void fill() {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        EditEngine.fill("Fill " + activeId.get().trim(), activeState);
    }

    private void replace() {
        Block from = Block.getBlockFromName(replaceId.get().trim());
        if (from == null) {
            EditEngine.status = "El bloque a reemplazar no es válido";
            return;
        }
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        EditEngine.replace("Replace " + replaceId.get().trim(), from, replaceMeta[0], activeState);
    }

    private void deleteSelection() {
        EditEngine.fill("Delete", Blocks.AIR.getDefaultState());
    }

    private void fillMode(String name, int mode) {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        EditEngine.fill(name + " " + activeId.get().trim(), activeState, mode);
    }

    /** Genera la forma con el bloque activo y la deja como Placement. */
    private void createShape() {
        if (activeState == null) {
            EditEngine.status = "El bloque activo no es válido";
            return;
        }
        long volume = (long) shapeSize[0] * shapeSize[1] * shapeSize[2];
        if (volume > EditEngine.MAX_BLOCKS) {
            EditEngine.status = "Forma demasiado grande: " + volume + " bloques (máximo " + EditEngine.MAX_BLOCKS + ")";
            return;
        }
        Placement.start(Shapes.create(shapeType.get(), shapeSize[0], shapeSize[1], shapeSize[2],
                shapeHollow.get(), activeState));
        Placement.moveTo(mc.player.getPosition());
    }

    private void exportSchematic() {
        Clipboard c = Clipboard.current;
        if (c == null) {
            EditEngine.status = "El portapapeles está vacío";
            return;
        }
        File f = Schematics.file(schemName.get(), formatPick.get());
        try {
            Schematics.export(c, f);
            EditEngine.status = "Exportado: " + f.getName();
            schemFiles = Schematics.list();
        } catch (IOException e) {
            LiteModLitBuild.LOG.error("No se pudo exportar el schematic", e);
            EditEngine.status = "No se pudo exportar: " + e.getMessage();
        }
    }

    /** Exporta la selección con la extensión dada (.schem, .nbt o .csv) a la carpeta de schematics. */
    private void exportSelection(String ext) {
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new java.util.Date());
        File f = new File(Schematics.dir(), "selection-" + stamp + ext);
        if (EditEngine.exportSelection(f)) {
            schemFiles = Schematics.list();
        }
    }

    private void importSchematic(String name) {
        try {
            Clipboard c = Schematics.read(new File(Schematics.dir(), name));
            Clipboard.current = c;
            EditEngine.status = "Importado: " + name + " (" + c.sx + " x " + c.sy + " x " + c.sz + ")"
                    + (Schematics.unknown > 0 ? ", " + Schematics.unknown + " bloques desconocidos en 1.12 (como aire)" : "");
            Placement.start(c);
            Placement.moveTo(mc.player.getPosition());
        } catch (IOException e) {
            LiteModLitBuild.LOG.error("No se pudo importar el schematic", e);
            EditEngine.status = "No se pudo importar: " + e.getMessage();
        }
    }

    private void startPaste() {
        Clipboard c = Clipboard.current;
        if (c == null) {
            EditEngine.status = "El portapapeles está vacío";
            return;
        }
        Placement.start(c);
        Placement.moveTo(mc.player.getPosition());
    }

    /** Pega y, salvo en Paste Copy, termina el Placement. */
    private void confirmPaste(boolean keepPlacement) {
        EditEngine.paste("Paste");
        if (!keepPlacement) {
            Placement.cancel();
        }
    }
}
