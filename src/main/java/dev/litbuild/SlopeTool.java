package dev.litbuild;

import imgui.type.ImInt;
import net.minecraft.util.math.BlockPos;

/** Estado del Slope: el bloque objetivo que se fija con el primer clic y los ajustes. */
public final class SlopeTool {
    public static final String[] MODES = {"Plane", "Cone"};
    public static final ImInt mode = new ImInt(0);
    public static final int[] height = {16};
    public static final float[] sharpness = {2f};
    public static BlockPos target;

    private SlopeTool() {
    }

    public static boolean cone() {
        return mode.get() == 1;
    }
}
