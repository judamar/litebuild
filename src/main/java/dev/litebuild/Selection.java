package dev.litebuild;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * Selección global del editor: una caja (esquinas inclusivas) y, opcionalmente, una máscara que la
 * limita a un subconjunto de posiciones (Magic Select). La máscara es inmutable: se sustituye entera.
 */
public final class Selection {
    /** Arrays para poder enlazarlos directamente a los campos de ImGui. */
    public static final int[] p1 = new int[3];
    public static final int[] p2 = new int[3];
    public static boolean active;
    /** null = toda la caja; si no, solo estas posiciones (claves de {@link #key}). */
    public static Set<Long> mask;

    public static final int REPLACE = 0, ADD = 1, SUBTRACT = 2, INTERSECT = 3;

    private Selection() {
    }

    public static long key(int x, int y, int z) {
        return new BlockPos(x, y, z).toLong();
    }

    public static void set(BlockPos a, BlockPos b) {
        p1[0] = a.getX();
        p1[1] = a.getY();
        p1[2] = a.getZ();
        p2[0] = b.getX();
        p2[1] = b.getY();
        p2[2] = b.getZ();
        active = true;
        mask = null;
    }

    public static void setSecond(BlockPos b) {
        p2[0] = b.getX();
        p2[1] = b.getY();
        p2[2] = b.getZ();
        mask = null;
    }

    public static void clear() {
        active = false;
        mask = null;
    }

    /**
     * Mueve la cara de la selección que mira hacia f: n > 0 la aleja, n < 0 la acerca (sin colapsar
     * la caja a menos de 1 bloque). La altura se mantiene dentro de 0-255. Descarta la máscara.
     */
    public static void expand(EnumFacing f, int n) {
        if (!active) {
            return;
        }
        int a = f.getAxis().ordinal();
        if (n < 0) {
            n = -Math.min(-n, size(a) - 1);
        }
        if (f.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) {
            (p1[a] >= p2[a] ? p1 : p2)[a] += n;
        } else {
            (p1[a] <= p2[a] ? p1 : p2)[a] -= n;
        }
        p1[1] = Math.max(0, Math.min(255, p1[1]));
        p2[1] = Math.max(0, Math.min(255, p2[1]));
        mask = null;
    }

    /** Estira la selección de la capa más baja a la más alta del mundo. */
    public static void fullHeight() {
        p1[1] = 0;
        p2[1] = 255;
        mask = null;
    }

    /** Desplaza la selección (caja y máscara). */
    public static void shift(int dx, int dy, int dz) {
        p1[0] += dx;
        p1[1] += dy;
        p1[2] += dz;
        p2[0] += dx;
        p2[1] += dy;
        p2[2] += dz;
        if (mask != null) {
            Set<Long> moved = new HashSet<Long>();
            for (Long k : mask) {
                BlockPos p = BlockPos.fromLong(k);
                moved.add(key(p.getX() + dx, p.getY() + dy, p.getZ() + dz));
            }
            mask = moved;
        }
    }

    /**
     * Combina un conjunto de posiciones con la selección actual (REPLACE, ADD, SUBTRACT, INTERSECT).
     * Devuelve false si la selección actual es demasiado grande para convertirla en conjunto.
     */
    public static boolean combine(Set<Long> incoming, int op) {
        Set<Long> out;
        if (op == REPLACE || !active) {
            out = op == SUBTRACT || op == INTERSECT ? new HashSet<Long>() : incoming;
        } else {
            Set<Long> cur = currentSet();
            if (cur == null) {
                return false;
            }
            out = new HashSet<Long>(cur);
            if (op == ADD) {
                out.addAll(incoming);
            } else if (op == SUBTRACT) {
                out.removeAll(incoming);
            } else {
                out.retainAll(incoming);
            }
        }
        if (out.isEmpty()) {
            clear();
            return true;
        }
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Long k : out) {
            BlockPos p = BlockPos.fromLong(k);
            lo[0] = Math.min(lo[0], p.getX());
            lo[1] = Math.min(lo[1], p.getY());
            lo[2] = Math.min(lo[2], p.getZ());
            hi[0] = Math.max(hi[0], p.getX());
            hi[1] = Math.max(hi[1], p.getY());
            hi[2] = Math.max(hi[2], p.getZ());
        }
        System.arraycopy(lo, 0, p1, 0, 3);
        System.arraycopy(hi, 0, p2, 0, 3);
        mask = out;
        active = true;
        return true;
    }

    /** La selección actual como conjunto (null si es una caja demasiado grande). */
    private static Set<Long> currentSet() {
        if (mask != null) {
            return mask;
        }
        if (boxVolume() > EditEngine.MAX_BLOCKS) {
            return null;
        }
        Set<Long> s = new HashSet<Long>();
        for (int y = min(1); y <= max(1); y++) {
            for (int z = min(2); z <= max(2); z++) {
                for (int x = min(0); x <= max(0); x++) {
                    s.add(key(x, y, z));
                }
            }
        }
        return s;
    }

    /** ¿Está la posición en la selección (en la máscara, o dentro de la caja si no hay máscara)? */
    public static boolean contains(int x, int y, int z) {
        if (!active) {
            return false;
        }
        if (mask != null) {
            return mask.contains(key(x, y, z));
        }
        return x >= min(0) && x <= max(0) && y >= min(1) && y <= max(1) && z >= min(2) && z <= max(2);
    }

    public static int min(int axis) {
        return Math.min(p1[axis], p2[axis]);
    }

    public static int max(int axis) {
        return Math.max(p1[axis], p2[axis]);
    }

    public static int size(int axis) {
        return max(axis) - min(axis) + 1;
    }

    /** Nº de bloques de la caja envolvente (Long.MAX_VALUE si se desborda). */
    public static long boxVolume() {
        if (!active) {
            return 0;
        }
        double v = 1;
        for (int a = 0; a < 3; a++) {
            v *= (max(a) - (double) min(a) + 1);
        }
        return v > 1e18 ? Long.MAX_VALUE : (long) v;
    }

    /** Nº de bloques seleccionados: los de la máscara si la hay, si no toda la caja. */
    public static long volume() {
        return mask != null ? mask.size() : boxVolume();
    }
}
