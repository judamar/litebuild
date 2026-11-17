package dev.litbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

/**
 * Colour Picker y Gradient Helper del Builder Mode. Trabajan con el color medio de la textura de cada bloque
 * ({@link BlockColors}) y miden distancias en OKLab, que se parece más a cómo vemos las diferencias de color que RGB.
 */
public final class ColourTools {
    /** Quien recibe un bloque elegido (la mano, el Active Block, una paleta...). */
    public interface Sink {
        void accept(IBlockState state);
    }

    /** Quien recibe un degradado entero (Copy to Hotbar, Export to Editor). */
    public interface ListSink {
        void accept(List<IBlockState> states);
    }

    private static final class Candidate {
        final IBlockState state;
        final int stateId;
        final String name;
        final double[] lab;
        final boolean full, solid, opaque, uniform;

        Candidate(IBlockState state, int rgb) {
            this.state = state;
            this.stateId = Block.getStateId(state);
            this.name = Block.REGISTRY.getNameForObject(state.getBlock()) + (state.getBlock().getMetaFromState(state) != 0
                    ? ":" + state.getBlock().getMetaFromState(state) : "");
            this.lab = oklab(rgb);
            this.full = state.isFullCube();
            this.solid = state.getMaterial().isSolid();
            this.opaque = state.isOpaqueCube();
            this.uniform = BlockColors.isUniform(stateId);
        }
    }

    // Estado de las ventanas
    private static final ImString hex = new ImString("#7F7F7F", 16);
    private static final int[] rgb = {127, 127, 127};
    private static final float[] hsb = {0f, 0f, 0.5f};
    private static final ImBoolean fFull = new ImBoolean(true), fSolid = new ImBoolean(false), fOpaque = new ImBoolean(false),
            fSame = new ImBoolean(false);
    private static final int[] gradCount = {5};
    private static final List<ImString> gradIds = new ArrayList<ImString>();
    private static final List<int[]> gradMetas = new ArrayList<int[]>();
    private static final List<Integer> gradShift = new ArrayList<Integer>();
    private static List<Candidate> all;
    private static List<IBlockState> gradOut = new ArrayList<IBlockState>();

    private ColourTools() {
    }

    // ---- Color ----------------------------------------------------------------------------------

    /** sRGB 0xRRGGBB a OKLab {L, a, b}. */
    public static double[] oklab(int c) {
        double r = lin(((c >> 16) & 255) / 255.0), g = lin(((c >> 8) & 255) / 255.0), b = lin((c & 255) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] {0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    /** OKLab a sRGB 0xRRGGBB (recortado al rango). */
    public static int fromOklab(double[] lab) {
        double l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2];
        double m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2];
        double s = lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2];
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        return (to8(r) << 16) | (to8(g) << 8) | to8(b);
    }

    private static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int to8(double linear) {
        double c = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
    }

    private static double dist2(double[] a, double[] b) {
        double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
        return dl * dl + da * da + db * db;
    }

    private static void ensureBuilt() {
        if (all != null) {
            return;
        }
        BlockColors.ensureBuilt();
        all = new ArrayList<Candidate>();
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        for (Block b : Block.REGISTRY) {
            for (int meta = 0; meta < 16; meta++) {
                IBlockState s;
                try {
                    s = b.getStateFromMeta(meta);
                } catch (RuntimeException e) {
                    continue;
                }
                int id = Block.getStateId(s);
                int c = BlockColors.get(id);
                if (c >= 0 && seen.add(id)) {
                    all.add(new Candidate(s, c));
                }
            }
        }
    }

    private static boolean passes(Candidate c) {
        return (!fFull.get() || c.full) && (!fSolid.get() || c.solid) && (!fOpaque.get() || c.opaque)
                && (!fSame.get() || c.uniform);
    }

    /** Los n bloques más parecidos a lab que cumplen los filtros, del más cercano al más lejano. */
    private static List<Candidate> nearest(final double[] lab, int n) {
        ensureBuilt();
        List<Candidate> list = new ArrayList<Candidate>();
        for (Candidate c : all) {
            if (passes(c)) {
                list.add(c);
            }
        }
        Collections.sort(list, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(dist2(a.lab, lab), dist2(b.lab, lab));
            }
        });
        return list.size() > n ? new ArrayList<Candidate>(list.subList(0, n)) : list;
    }

    private static void setColour(int c) {
        rgb[0] = (c >> 16) & 255;
        rgb[1] = (c >> 8) & 255;
        rgb[2] = c & 255;
        syncFromRgb(true);
    }

    /** Pone hex y HSB a partir de rgb (hsbToo) o hex y RGB a partir de hsb. */
    private static void syncFromRgb(boolean hsbToo) {
        hex.set(String.format(Locale.ROOT, "#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
        if (hsbToo) {
            java.awt.Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], hsb);
        }
    }

    private static void filterRow() {
        ImGui.checkbox("Full Cube (F)", fFull);
        ImGui.sameLine();
        ImGui.checkbox("Solid (S)", fSolid);
        ImGui.checkbox("Opaque (O)", fOpaque);
        ImGui.sameLine();
        ImGui.checkbox("Same Texture (T)", fSame);
    }

    /** Un icono de bloque pulsable; true si se pulsó. */
    private static boolean cell(String id, IBlockState state, float size, String tip) {
        ImGui.pushID(id);
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##c", size, size);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + size, y + size, ImGui.getColorU32(hovered ? ImGuiCol.ButtonHovered : ImGuiCol.FrameBg), 4f);
        Icons.drawState(dl, state, x + size / 2, y + size / 2, size - 8f);
        if (hovered) {
            Txt.tooltip(tip);
        }
        ImGui.popID();
        return clicked;
    }

    // ---- Colour Picker -------------------------------------------------------------------------------

    /** La ventana del Colour Picker. held = estado de la mano o del Active Block (para el Eye Dropper); null si no hay. */
    public static void pickerWindow(ImBoolean open, IBlockState held, Sink sink, String sinkLabel) {
        if (!ImGui.begin("Colour Picker", open)) {
            ImGui.end();
            return;
        }
        BlockColors.ensureBuilt();
        if (ImGui.inputText("Hex", hex)) {
            String t = hex.get().trim().replace("#", "");
            try {
                setColourNoHex(Integer.parseInt(t.length() > 6 ? t.substring(0, 6) : t, 16));
            } catch (NumberFormatException e) {
                // se sigue escribiendo
            }
        }
        if (ImGui.sliderInt3("RGB", rgb, 0, 255)) {
            syncFromRgb(true);
        }
        if (ImGui.sliderFloat3("HSB", hsb, 0f, 1f, "%.2f")) {
            int c = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            rgb[0] = (c >> 16) & 255;
            rgb[1] = (c >> 8) & 255;
            rgb[2] = c & 255;
            syncFromRgb(false);
        }
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        ImGui.dummy(ImGui.getContentRegionAvailX(), ImGui.getFrameHeight());
        dl.addRectFilled(x, y, x + ImGui.getItemRectSizeX(), y + ImGui.getItemRectSizeY(),
                0xFF000000 | rgb[2] << 16 | rgb[1] << 8 | rgb[0], 4f);
        if (held != null && ImGui.button("Eye Dropper: color of the block in hand")) {
            int c = BlockColors.get(Block.getStateId(held));
            if (c >= 0) {
                setColour(c);
            }
        }
        filterRow();
        ImGui.separatorText("Similar blocks");
        double[] target = oklab(rgb[0] << 16 | rgb[1] << 8 | rgb[2]);
        List<Candidate> near = nearest(target, 30);
        float cellSize = ImGui.getFrameHeight() * 1.7f;
        int perRow = Math.max(1, (int) (ImGui.getContentRegionAvailX() / (cellSize + ImGui.getStyle().getItemSpacingX())));
        for (int i = 0; i < near.size(); i++) {
            if (i % perRow != 0) {
                ImGui.sameLine();
            }
            Candidate c = near.get(i);
            if (cell("sim" + i, c.state, cellSize, c.name + String.format(Locale.ROOT, "  (distancia %.3f)",
                    Math.sqrt(dist2(c.lab, target)))) && sink != null) {
                sink.accept(c.state);
            }
        }
        if (sinkLabel != null) {
            Txt.disabled("Clic en un bloque: " + sinkLabel);
        }
        ImGui.end();
    }

    private static void setColourNoHex(int c) {
        rgb[0] = (c >> 16) & 255;
        rgb[1] = (c >> 8) & 255;
        rgb[2] = c & 255;
        java.awt.Color.RGBtoHSB(rgb[0], rgb[1], rgb[2], hsb);
    }

    // ---- Gradient Helper -----------------------------------------------------------------------------

    private static void ensureGradInputs() {
        while (gradIds.size() < 2) {
            gradIds.add(new ImString(gradIds.isEmpty() ? "minecraft:coal_block" : "minecraft:quartz_block", 128));
            gradMetas.add(new int[] {0});
        }
    }

    private static IBlockState stateOf(ImString id, int[] meta) {
        Block b = Block.getBlockFromName(id.get().trim());
        if (b == null) {
            return null;
        }
        try {
            return b.getStateFromMeta(Math.max(0, meta[0]));
        } catch (RuntimeException e) {
            return b.getDefaultState();
        }
    }

    /**
     * La ventana del Gradient Helper: degradado en OKLab entre 2 o más bloques de entrada, con el bloque más cercano
     * a cada color intermedio. hotbar recibe la lista con Copy to Hotbar; editor (puede ser null) con Export to Editor.
     */
    public static void gradientWindow(ImBoolean open, ListSink hotbar, ListSink editor) {
        if (!ImGui.begin("Gradient Helper", open)) {
            ImGui.end();
            return;
        }
        BlockColors.ensureBuilt();
        ensureGradInputs();
        ImGui.separatorText("Input blocks");
        int remove = -1;
        for (int i = 0; i < gradIds.size(); i++) {
            ImGui.pushID("in" + i);
            BlockPicker.button("grad" + i, gradIds.get(i), gradMetas.get(i));
            ImGui.sameLine();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 40f);
            ImGui.inputText("##id", gradIds.get(i));
            if (gradIds.size() > 2) {
                ImGui.sameLine();
                if (ImGui.button("X")) {
                    remove = i;
                }
            }
            ImGui.popID();
        }
        if (remove >= 0) {
            gradIds.remove(remove);
            gradMetas.remove(remove);
        }
        if (ImGui.button("Add input")) {
            gradIds.add(new ImString("minecraft:stone", 128));
            gradMetas.add(new int[] {0});
        }
        ImGui.sliderInt("Output blocks", gradCount, 2, 9);
        filterRow();
        List<double[]> inputs = new ArrayList<double[]>();
        for (int i = 0; i < gradIds.size(); i++) {
            IBlockState s = stateOf(gradIds.get(i), gradMetas.get(i));
            int c = s == null ? -1 : BlockColors.get(Block.getStateId(s));
            if (c >= 0) {
                inputs.add(oklab(c));
            }
        }
        if (inputs.size() < 2) {
            Txt.colored(1f, 0.4f, 0.35f, 1f, "Hacen falta al menos 2 bloques de entrada con color.");
        } else {
            int n = gradCount[0];
            while (gradShift.size() < n) {
                gradShift.add(0);
            }
            gradOut = new ArrayList<IBlockState>();
            ImGui.separatorText("Gradient");
            float cellSize = ImGui.getFrameHeight() * 1.9f;
            for (int k = 0; k < n; k++) {
                double t = n == 1 ? 0 : (double) k / (n - 1) * (inputs.size() - 1);
                int seg = Math.min(inputs.size() - 2, (int) Math.floor(t));
                double f = t - seg;
                double[] a = inputs.get(seg), b = inputs.get(seg + 1);
                double[] mix = {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
                List<Candidate> near = nearest(mix, 12);
                if (near.isEmpty()) {
                    continue;
                }
                int shift = Math.floorMod(gradShift.get(k), near.size());
                Candidate c = near.get(shift);
                gradOut.add(c.state);
                ImGui.pushID("out" + k);
                if (k > 0) {
                    ImGui.sameLine();
                }
                ImGui.beginGroup();
                cell("o", c.state, cellSize, c.name);
                if (ImGui.smallButton("<")) {
                    gradShift.set(k, gradShift.get(k) - 1);
                }
                ImGui.sameLine();
                if (ImGui.smallButton(">")) {
                    gradShift.set(k, gradShift.get(k) + 1);
                }
                ImGui.endGroup();
                ImGui.popID();
            }
            ImGui.spacing();
            if (hotbar != null && ImGui.button("Copy to Hotbar") && !gradOut.isEmpty()) {
                hotbar.accept(gradOut);
            }
            if (editor != null) {
                ImGui.sameLine();
                if (ImGui.button("Export to Editor") && !gradOut.isEmpty()) {
                    editor.accept(gradOut);
                }
            }
            Txt.disabled("< > elige la mejor coincidencia anterior o siguiente de cada casilla.");
        }
        ImGui.end();
    }
}
