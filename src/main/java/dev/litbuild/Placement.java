package dev.litbuild;

import imgui.type.ImBoolean;
import net.minecraft.util.math.BlockPos;

/**
 * Pegado en curso: una copia flotante del portapapeles que se coloca antes de confirmar.
 * La esquina mínima está en pos; sigue al cursor hasta que se fija con el clic derecho.
 */
public final class Placement {
    public static boolean active;
    public static boolean follow = true;
    public static Clipboard shape;
    /** La forma tal como empezó el Placement, para poder deshacer giros y escalas libres. */
    public static Clipboard original;
    /** Giro libre en grados (yaw, pitch, roll) y escala por eje sobre original; enlazados a ImGui. */
    public static final float[] angles = new float[3];
    public static final float[] scale = {1f, 1f, 1f};
    /** Esquina mínima (x, y, z); array para enlazarlo a los campos de ImGui. */
    public static final int[] pos = new int[3];
    public static final ImBoolean KEEP_EXISTING = new ImBoolean(false);
    public static final ImBoolean PASTE_AIR = new ImBoolean(false);

    private Placement() {
    }

    public static void start(Clipboard c) {
        shape = c;
        original = c;
        resetTransform();
        active = true;
        follow = true;
    }

    public static void cancel() {
        active = false;
        shape = null;
    }

    public static void rotate() {
        original = original.rotated();
        refresh();
    }

    public static void flip(boolean alongX) {
        original = original.mirrored(alongX);
        refresh();
    }

    public static void resetTransform() {
        angles[0] = angles[1] = angles[2] = 0f;
        scale[0] = scale[1] = scale[2] = 1f;
    }

    /** Cada bloque pasa a ser un cubo de f x f x f. false si no cabe en el tope de bloques. */
    public static boolean scaleUp(int f) {
        Clipboard n = original.scaled(f);
        if (n == null) {
            return false;
        }
        original = n;
        return refresh();
    }

    /**
     * Recalcula shape desde original con el giro y la escala actuales (el centro se mantiene si está fijada).
     * false si el resultado pasaría del tope de bloques; shape queda como estaba.
     */
    public static boolean refresh() {
        boolean identity = angles[0] == 0f && angles[1] == 0f && angles[2] == 0f
                && scale[0] == 1f && scale[1] == 1f && scale[2] == 1f;
        Clipboard n = identity ? original
                : original.transformed(angles[0], angles[1], angles[2], scale[0], scale[1], scale[2]);
        if (n == null) {
            return false;
        }
        Clipboard old = shape;
        shape = n;
        if (!follow && old != null) {
            pos[0] += (old.sx - n.sx) / 2;
            pos[2] += (old.sz - n.sz) / 2;
        }
        return true;
    }

    /** Apoya la copia sobre base: centrada en X/Z y con su cara inferior en base.y. */
    public static void moveTo(BlockPos base) {
        pos[0] = base.getX() - shape.sx / 2;
        pos[1] = base.getY();
        pos[2] = base.getZ() - shape.sz / 2;
    }

    public static void nudge(int dx, int dy, int dz) {
        follow = false;
        pos[0] += dx;
        pos[1] += dy;
        pos[2] += dz;
    }
}
