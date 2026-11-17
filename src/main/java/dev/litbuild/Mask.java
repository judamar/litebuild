package dev.litbuild;

import java.util.ArrayList;
import java.util.List;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.block.Block;

/** Estado de la ventana Tool Masks y su conversión a {@link MaskSpec}. */
public final class Mask {
    public static final String[] TYPES = {
        "Block", "Above", "Below", "Surface", "Can See Sky", "In Selection", "Y", "Neighbour", "Adjacent"
    };

    public static final class Rule {
        public final ImInt type = new ImInt(MaskSpec.BLOCK);
        public final ImBoolean not = new ImBoolean(false);
        public final ImString id = new ImString("minecraft:stone", 128);
        public final int[] meta = {-1};
        public final int[] y = {0, 255};

        /** ¿Usa un bloque este tipo de regla? */
        public boolean needsBlock() {
            int t = type.get();
            return t == MaskSpec.BLOCK || t == MaskSpec.ABOVE || t == MaskSpec.BELOW || t == MaskSpec.NEIGHBOUR
                    || t == MaskSpec.ADJACENT;
        }
    }

    public static final ImBoolean enabled = new ImBoolean(false);
    public static final List<Rule> rules = new ArrayList<Rule>();
    /** true si la última {@link #snapshot()} falló (hay un bloque desconocido): no hay que pintar. */
    public static boolean error;

    private Mask() {
    }

    /** La máscara activa, o null si está desactivada, vacía o inválida (en ese caso error = true). */
    public static MaskSpec snapshot() {
        error = false;
        if (!enabled.get() || rules.isEmpty()) {
            return null;
        }
        int n = rules.size();
        int[] type = new int[n];
        boolean[] not = new boolean[n];
        Block[] block = new Block[n];
        int[] meta = new int[n];
        int[] y1 = new int[n];
        int[] y2 = new int[n];
        for (int i = 0; i < n; i++) {
            Rule r = rules.get(i);
            type[i] = r.type.get();
            not[i] = r.not.get();
            meta[i] = r.meta[0];
            y1[i] = Math.min(r.y[0], r.y[1]);
            y2[i] = Math.max(r.y[0], r.y[1]);
            if (r.needsBlock()) {
                block[i] = Block.getBlockFromName(r.id.get().trim());
                if (block[i] == null) {
                    EditEngine.status = "Tool Masks: bloque desconocido en la regla " + (i + 1);
                    error = true;
                    return null;
                }
            }
        }
        int[] box = Selection.active
                ? new int[] {Selection.min(0), Selection.min(1), Selection.min(2),
                    Selection.max(0), Selection.max(1), Selection.max(2)}
                : null;
        return new MaskSpec(type, not, block, meta, y1, y2, box, Selection.mask);
    }
}
