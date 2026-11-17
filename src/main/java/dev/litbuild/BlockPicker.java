package dev.litbuild;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/**
 * Selector de bloques como el inventario creativo: cuadrícula de iconos con búsqueda, filtro por
 * pestaña creativa y los últimos bloques usados. {@link #button} muestra el icono del bloque de un
 * campo y abre la ventana; al elegir se escribe el id y la meta del campo.
 */
public final class BlockPicker {
    private static final class Entry {
        final ItemStack stack;
        final String name;
        final String lower;
        final String reg;
        final int meta;
        final String key;

        Entry(ItemStack stack, String name, String reg, int meta) {
            this.stack = stack;
            this.name = name;
            this.lower = (name + " " + reg).toLowerCase(Locale.ROOT);
            this.reg = reg;
            this.meta = meta;
            this.key = Item.REGISTRY.getNameForObject(stack.getItem()) + ":" + stack.getMetadata();
        }
    }

    private static final int MAX_RECENT = 14;

    public static final ImBoolean open = new ImBoolean(false);
    private static final ImString search = new ImString(64);
    private static final ImInt tab = new ImInt(0);
    private static final List<Entry> recent = new ArrayList<Entry>();
    private static List<Entry> all;
    private static String[] tabNames = {"All"};
    private static final List<Set<String>> tabKeys = new ArrayList<Set<String>>();
    private static ImString targetId;
    private static int[] targetMeta;
    private static boolean justOpened;
    /** Se ejecuta al elegir un bloque (null = solo escribir en los campos). */
    private static Runnable onPick;

    private BlockPicker() {
    }

    /** El estado de bloque que describen un id y una meta (null si el id no existe). */
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

    /** Botón cuadrado con el icono del bloque de (id, meta); al pulsarlo se abre el selector. */
    public static void button(String label, ImString id, int[] meta) {
        float size = ImGui.getFrameHeight() * 1.9f;
        ImGui.pushID(label);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##pick", size, size);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + size, y + size, ImGui.getColorU32(hovered ? ImGuiCol.FrameBgHovered : ImGuiCol.FrameBg), 4f);
        IBlockState st = stateOf(id, meta);
        if (st != null) {
            Icons.drawState(dl, st, x + size / 2, y + size / 2, size - 8f);
        } else {
            dl.addText(x + size / 2 - ImGui.calcTextSizeX("?") / 2, y + size / 2 - ImGui.getTextLineHeight() / 2,
                    ImGui.getColorU32(ImGuiCol.TextDisabled), "?");
        }
        if (hovered) {
            Txt.tooltip("Elegir bloque");
        }
        ImGui.popID();
        if (clicked) {
            openWith(id, meta, null);
        }
    }

    /** Abre el selector: al elegir se escribe en id y meta y después se ejecuta after (puede ser null). Con Ctrl se pueden elegir varios. */
    public static void openWith(ImString id, int[] meta, Runnable after) {
        targetId = id;
        targetMeta = meta;
        onPick = after;
        open.set(true);
        justOpened = true;
    }

    /** La ventana del selector; llamar una vez por frame. */
    public static void window() {
        if (!open.get()) {
            return;
        }
        ImGui.setNextWindowSize(480, 460, ImGuiCond.Appearing);
        ImGui.setNextWindowPos(ImGui.getMainViewport().getWorkSizeX() / 2 - 240,
                ImGui.getMainViewport().getWorkSizeY() / 2 - 230, ImGuiCond.Appearing);
        if (ImGui.begin("Block Picker", open, ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoCollapse)) {
            ensureBuilt();
            if (justOpened) {
                ImGui.setKeyboardFocusHere();
                justOpened = false;
            }
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 170f);
            ImGui.inputText("##search", search);
            ImGui.sameLine();
            ImGui.setNextItemWidth(160f);
            ImGui.combo("##tab", tab, tabNames);
            if (!recent.isEmpty()) {
                Txt.disabled("Recientes");
                float cell = ImGui.getFrameHeight() * 1.7f;
                for (int i = 0; i < recent.size(); i++) {
                    if (i > 0) {
                        ImGui.sameLine();
                    }
                    cell("recent" + i, recent.get(i), cell);
                }
            }
            ImGui.separator();
            grid();
        }
        ImGui.end();
    }

    private static void grid() {
        List<Entry> shown = filtered();
        float cell = ImGui.getFrameHeight() * 1.7f;
        float gap = 4f;
        ImGui.beginChild("##grid", 0, 0, false);
        float avail = ImGui.getContentRegionAvailX();
        int cols = Math.max(1, (int) ((avail + gap) / (cell + gap)));
        int rows = (shown.size() + cols - 1) / cols;
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();
        // Solo se dibujan las filas visibles: la lista completa tiene más de mil bloques.
        float scroll = ImGui.getScrollY();
        float view = ImGui.getWindowSizeY();
        int first = Math.max(0, (int) (scroll / (cell + gap)) - 1);
        int last = Math.min(rows - 1, (int) ((scroll + view) / (cell + gap)) + 1);
        for (int r = first; r <= last; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (idx >= shown.size()) {
                    break;
                }
                ImGui.setCursorPos(startX + c * (cell + gap), startY + r * (cell + gap));
                cell("grid" + idx, shown.get(idx), cell);
            }
        }
        ImGui.setCursorPos(startX, startY + rows * (cell + gap));
        ImGui.dummy(1f, 1f);
        if (shown.isEmpty()) {
            Txt.disabled("Ningún bloque coincide.");
        }
        ImGui.endChild();
    }

    /** Una casilla de la cuadrícula: icono, resalte y tooltip; al pulsarla se elige el bloque. */
    private static void cell(String id, Entry e, float size) {
        ImGui.pushID(id);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##c", size, size);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + size, y + size, ImGui.getColorU32(hovered ? ImGuiCol.ButtonHovered : ImGuiCol.FrameBg), 4f);
        Icons.draw(dl, e.stack, x + size / 2, y + size / 2, size - 8f);
        if (hovered) {
            ImGui.beginTooltip();
            Txt.text(e.name);
            Txt.disabled(e.reg + (e.meta != 0 ? ":" + e.meta : ""));
            ImGui.endTooltip();
        }
        ImGui.popID();
        if (clicked) {
            pick(e);
        }
    }

    private static void pick(Entry e) {
        if (targetId != null) {
            targetId.set(e.reg);
            targetMeta[0] = e.meta;
            if (onPick != null) {
                onPick.run();
            }
        }
        recent.remove(e);
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (recent.get(i).key.equals(e.key)) {
                recent.remove(i);
            }
        }
        recent.add(0, e);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        if (!ImGui.getIO().getKeyCtrl()) {
            open.set(false);
        }
    }

    private static List<Entry> filtered() {
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        Set<String> inTab = tab.get() > 0 && tab.get() <= tabKeys.size() ? tabKeys.get(tab.get() - 1) : null;
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : all) {
            if ((inTab == null || inTab.contains(e.key)) && (q.isEmpty() || e.lower.contains(q))) {
                out.add(e);
            }
        }
        return out;
    }

    /** Construye la lista de bloques (los ItemBlock del inventario creativo) la primera vez. */
    private static void ensureBuilt() {
        if (all != null) {
            return;
        }
        all = new ArrayList<Entry>();
        try {
            Set<String> seen = new HashSet<String>();
            for (Item item : Item.REGISTRY) {
                if (!(item instanceof ItemBlock)) {
                    continue;
                }
                NonNullList<ItemStack> sub = NonNullList.create();
                item.getSubItems(CreativeTabs.SEARCH, sub);
                for (ItemStack s : sub) {
                    Block b = ((ItemBlock) item).getBlock();
                    int meta = item.getMetadata(s.getMetadata());
                    Entry e = new Entry(s, s.getDisplayName(), String.valueOf(Block.REGISTRY.getNameForObject(b)), meta);
                    if (seen.add(e.key)) {
                        all.add(e);
                    }
                }
            }
            List<String> names = new ArrayList<String>();
            names.add("All");
            for (CreativeTabs t : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (t == CreativeTabs.SEARCH || t == CreativeTabs.HOTBAR || t == CreativeTabs.INVENTORY) {
                    continue;
                }
                NonNullList<ItemStack> list = NonNullList.create();
                t.displayAllRelevantItems(list);
                Set<String> keys = new HashSet<String>();
                boolean hasBlocks = false;
                for (ItemStack s : list) {
                    String k = Item.REGISTRY.getNameForObject(s.getItem()) + ":" + s.getMetadata();
                    keys.add(k);
                    hasBlocks |= s.getItem() instanceof ItemBlock;
                }
                if (hasBlocks) {
                    names.add(I18n.format(t.getTranslatedTabLabel()));
                    tabKeys.add(keys);
                }
            }
            tabNames = names.toArray(new String[0]);
        } catch (RuntimeException e) {
            LiteModLitBuild.LOG.error("No se pudo construir la lista de bloques", e);
        }
    }
}
