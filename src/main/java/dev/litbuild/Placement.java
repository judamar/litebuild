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
    /** Pega también las entidades copiadas con Copy With Entities. */
    public static final ImBoolean PASTE_ENTITIES = new ImBoolean(true);
    /** Merge Blocks: al pegar, losas contrarias forman losa doble y la nieve se suma. */
    public static final ImBoolean MERGE = new ImBoolean(false);
    /** Previsualiza el Placement con los bloques (translúcidos) y no solo con la caja. */
    public static final ImBoolean PREVIEW = new ImBoolean(true);
    /** El gizmo usa los ejes propios de la copia girada (Local) en vez de los del mundo (Global). */
    public static final ImBoolean LOCAL = new ImBoolean(false);
    /** Al girar con ángulos libres: RotSprite (ampliar con bordes, girar y reducir) en vez del vecino más cercano. */
    public static final ImBoolean ROTSPRITE = new ImBoolean(false);

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
        PlacementPreview.release();
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

    /**
     * Cada bloque pasa a ser un cubo de f x f x f, o con smooth se amplía alisando las esquinas (Scale2x / Scale3x).
     * false si no cabe en el tope de bloques.
     */
    public static boolean scaleUp(int f, boolean smooth) {
        Clipboard n = smooth ? original.smoothScaled(f) : original.scaled(f);
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
        boolean turned = angles[0] != 0f || angles[1] != 0f || angles[2] != 0f;
        Clipboard n = identity ? original
                : ROTSPRITE.get() && turned
                        ? original.rotSprite(angles[0], angles[1], angles[2], scale[0], scale[1], scale[2])
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

    /**
     * Snap to ground: baja la copia hasta que la capa inferior de cada columna se apoya en un bloque sólido del mundo (si empieza
     * dentro del terreno, primero sube hasta quedar libre). false si no encontró un sitio.
     */
    public static boolean snapToGround(net.minecraft.world.World w) {
        if (shape == null) {
            return false;
        }
        int cols = shape.sx * shape.sz;
        int[] lowest = new int[cols];
        java.util.Arrays.fill(lowest, -1);
        for (int y = shape.sy - 1; y >= 0; y--) {
            for (int z = 0; z < shape.sz; z++) {
                for (int x = 0; x < shape.sx; x++) {
                    if (shape.states[shape.index(x, y, z)] != Clipboard.AIR) {
                        lowest[z * shape.sx + x] = y;
                    }
                }
            }
        }
        int y = pos[1];
        for (int up = 0; up < 128 && !fits(w, lowest, y); up++) {
            y++;
        }
        if (!fits(w, lowest, y)) {
            return false;
        }
        while (y > 0 && fits(w, lowest, y - 1)) {
            y--;
        }
        pos[1] = y;
        follow = false;
        return true;
    }

    /** ¿Caben las celdas más bajas de cada columna con la base en y, sin tocar bloques sólidos? */
    private static boolean fits(net.minecraft.world.World w, int[] lowest, int y) {
        net.minecraft.util.math.BlockPos.MutableBlockPos q = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        for (int z = 0; z < shape.sz; z++) {
            for (int x = 0; x < shape.sx; x++) {
                int low = lowest[z * shape.sx + x];
                if (low < 0) {
                    continue;
                }
                int wy = y + low;
                q.setPos(pos[0] + x, wy, pos[2] + z);
                if (wy < 0 || wy > 255 || !w.isBlockLoaded(q) || w.getBlockState(q).getMaterial().isSolid()) {
                    return false;
                }
            }
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
