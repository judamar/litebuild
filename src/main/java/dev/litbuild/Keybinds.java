package dev.litbuild;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import imgui.ImGui;
import imgui.flag.ImGuiKey;
import net.minecraft.client.Minecraft;

/**
 * Atajos del editor, reasignables desde la ventana Keybinds y guardados en config/litbuild/keybinds.properties.
 * Cada atajo es una tecla de ImGui más Ctrl y Shift exactos. Cada uno vale en un contexto (normal, durante un
 * Placement o los dos) y la ventana marca los que chocan dentro del mismo contexto.
 */
public final class Keybinds {
    public static final int NORMAL = 0, PLACEMENT = 1, BOTH = 2, CONTEXTUAL = 3;

    public static final class Bind {
        public final String id, label, category;
        public final int context, defKey;
        public final boolean defCtrl, defShift;
        public int key;
        public boolean ctrl, shift;

        Bind(String id, String label, String category, int context, int key, boolean ctrl, boolean shift) {
            this.id = id;
            this.label = label;
            this.category = category;
            this.context = context;
            this.defKey = key;
            this.defCtrl = ctrl;
            this.defShift = shift;
            reset();
        }

        public void reset() {
            key = defKey;
            ctrl = defCtrl;
            shift = defShift;
        }
    }

    private static final List<Bind> ALL = new ArrayList<Bind>();
    private static final Map<String, Bind> BY_ID = new HashMap<String, Bind>();
    /** El atajo que se está reasignando (el siguiente toque de tecla lo cambia). */
    public static Bind capturing;
    private static boolean ready;

    private Keybinds() {
    }

    public static List<Bind> all() {
        return ALL;
    }

    private static void add(String id, String label, String category, int context, int key, boolean ctrl) {
        Bind b = new Bind(id, label, category, context, key, ctrl, false);
        ALL.add(b);
        BY_ID.put(id, b);
    }

    private static int letter(String s) {
        return s.length() == 1 && s.charAt(0) >= 'A' && s.charAt(0) <= 'Z' ? ImGuiKey.A + (s.charAt(0) - 'A') : ImGuiKey.None;
    }

    /** Registra todos los atajos (una vez) y lee los guardados. toolKeys[i] es la letra por defecto de la herramienta i. */
    public static void init(String[] toolNames, String[] toolKeys) {
        if (ready) {
            return;
        }
        ready = true;
        add("undo", "Undo", "Edit", BOTH, ImGuiKey.Z, true);
        add("redo", "Redo", "Edit", BOTH, ImGuiKey.Y, true);
        add("copy", "Copy", "Edit", NORMAL, ImGuiKey.C, true);
        add("cut", "Cut", "Edit", NORMAL, ImGuiKey.X, true);
        add("paste", "Paste / confirm placement", "Edit", BOTH, ImGuiKey.V, true);
        add("delete", "Delete selection", "Edit", NORMAL, ImGuiKey.Delete, false);
        add("saveblueprint", "Save Blueprint", "Edit", NORMAL, ImGuiKey.P, true);
        add("quickfill", "Quick Fill", "Operations", NORMAL, ImGuiKey.F, true);
        add("quickreplace", "Quick Replace", "Operations", NORMAL, ImGuiKey.R, true);
        add("pl.confirm", "Confirm placement", "Placement", PLACEMENT, ImGuiKey.Enter, false);
        add("pl.cancel", "Cancel placement", "Placement", PLACEMENT, ImGuiKey.Delete, false);
        add("pl.rotate", "Rotate placement", "Placement", PLACEMENT, ImGuiKey.R, true);
        add("pl.flip", "Flip placement", "Placement", PLACEMENT, ImGuiKey.F, true);
        add("swaplast", "Swap To Last Tool", "Tools", NORMAL, ImGuiKey.None, false);
        add("extrudepoint", "Extrude Point (Modelling)", "Tools", CONTEXTUAL, ImGuiKey.E, false);
        add("showbiomes", "Show Biomes", "View", NORMAL, ImGuiKey.B, true);
        for (int i = 0; i < toolNames.length; i++) {
            add("tool." + i, toolNames[i], "Tools", NORMAL, i < toolKeys.length ? letter(toolKeys[i]) : ImGuiKey.None, false);
        }
        load();
    }

    /** true si el atajo id se acaba de pulsar con exactamente sus modificadores. */
    public static boolean pressed(String id) {
        Bind b = BY_ID.get(id);
        if (b == null || b.key == ImGuiKey.None || !ImGui.isKeyPressed(b.key, false)) {
            return false;
        }
        imgui.ImGuiIO io = ImGui.getIO();
        return io.getKeyCtrl() == b.ctrl && io.getKeyShift() == b.shift;
    }

    public static String describe(Bind b) {
        if (b.key == ImGuiKey.None) {
            return "-";
        }
        return (b.ctrl ? "Ctrl+" : "") + (b.shift ? "Shift+" : "") + ImGui.getKeyName(b.key);
    }

    /** El texto del atajo de una herramienta para los tooltips ("" si no tiene). */
    public static String toolKey(int tool) {
        Bind b = BY_ID.get("tool." + tool);
        return b == null || b.key == ImGuiKey.None ? "" : describe(b);
    }

    /** true si otro atajo del mismo contexto usa la misma combinación. */
    public static boolean conflicts(Bind b) {
        if (b.key == ImGuiKey.None) {
            return false;
        }
        if (b.context == CONTEXTUAL) {
            return false;
        }
        for (Bind o : ALL) {
            if (o != b && o.context != CONTEXTUAL && o.key == b.key && o.ctrl == b.ctrl && o.shift == b.shift
                    && (o.context == b.context || o.context == BOTH || b.context == BOTH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mientras se reasigna un atajo: asigna la primera tecla que no sea un modificador, con los Ctrl y Shift que haya
     * pulsados. Escape cancela. Llamar cada frame.
     */
    public static void pollCapture() {
        if (capturing == null) {
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
            capturing = null;
            return;
        }
        for (int k = ImGuiKey.Tab; k <= ImGuiKey.KeypadEqual; k++) {
            boolean modifier = k >= ImGuiKey.LeftCtrl && k <= ImGuiKey.Menu;
            if (!modifier && k != ImGuiKey.Escape && ImGui.isKeyPressed(k, false)) {
                imgui.ImGuiIO io = ImGui.getIO();
                capturing.key = k;
                capturing.ctrl = io.getKeyCtrl();
                capturing.shift = io.getKeyShift();
                capturing = null;
                save();
                return;
            }
        }
    }

    public static void resetAll() {
        for (Bind b : ALL) {
            b.reset();
        }
        save();
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/litbuild/keybinds.properties");
    }

    private static void load() {
        File f = file();
        if (!f.isFile()) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
            for (Bind b : ALL) {
                String v = p.getProperty(b.id);
                if (v != null) {
                    String[] t = v.split(",");
                    b.key = Integer.parseInt(t[0]);
                    b.ctrl = Boolean.parseBoolean(t[1]);
                    b.shift = Boolean.parseBoolean(t[2]);
                }
            }
        } catch (IOException | RuntimeException e) {
            LiteModLitBuild.LOG.warn("No se pudo leer keybinds.properties; se usan los atajos por defecto", e);
            for (Bind b : ALL) {
                b.reset();
            }
        }
    }

    public static void save() {
        File f = file();
        f.getParentFile().mkdirs();
        Properties p = new Properties();
        for (Bind b : ALL) {
            p.setProperty(b.id, b.key + "," + b.ctrl + "," + b.shift);
        }
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "LitBuild keybinds (tecla de ImGui, ctrl, shift)");
        } catch (IOException e) {
            LiteModLitBuild.LOG.warn("No se pudo guardar keybinds.properties", e);
        }
    }
}
