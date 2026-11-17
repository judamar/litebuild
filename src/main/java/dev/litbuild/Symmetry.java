package dev.litbuild;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import imgui.type.ImBoolean;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Simetría de los pinceles: un nodo en el mundo y planos de espejo en X, Y o Z, más giros de 180° o 90°
 * alrededor del eje vertical. El nodo se guarda en medios bloques, así que puede quedar en el centro de
 * un bloque, en una cara, en una arista o en un vértice.
 */
public final class Symmetry {
    public static final ImBoolean enabled = new ImBoolean(false);
    public static final ImBoolean mirrorX = new ImBoolean(true);
    public static final ImBoolean mirrorY = new ImBoolean(false);
    public static final ImBoolean mirrorZ = new ImBoolean(false);
    public static final ImBoolean rotate180 = new ImBoolean(false);
    public static final ImBoolean rotate90 = new ImBoolean(false);
    /** Posición del nodo en medios bloques (2 * coordenada). */
    public static final int[] node = {0, 64, 0};
    public static boolean nodeSet;

    private Symmetry() {
    }

    /** Coloca el nodo en el punto de impacto del rayo, redondeado al medio bloque más cercano. */
    public static void setNode(Vec3d hit) {
        node[0] = (int) Math.round(hit.x * 2);
        node[1] = (int) Math.round(hit.y * 2);
        node[2] = (int) Math.round(hit.z * 2);
        nodeSet = true;
        enabled.set(true);
    }

    /** ¿Se puede girar 90° con este nodo? Hace falta que Nx + Nz sea par para caer en centros de bloque. */
    public static boolean canRotate90() {
        return Math.floorMod(node[0] + node[2], 2) == 0;
    }

    /** Todas las posiciones equivalentes a p según la simetría activa (p primero, sin repetidas). */
    public static List<BlockPos> expand(BlockPos p) {
        List<int[]> cur = new ArrayList<int[]>();
        cur.add(new int[] {p.getX(), p.getY(), p.getZ()});
        if (enabled.get() && nodeSet) {
            if (mirrorX.get()) {
                cur = mirror(cur, 0);
            }
            if (mirrorY.get()) {
                cur = mirror(cur, 1);
            }
            if (mirrorZ.get()) {
                cur = mirror(cur, 2);
            }
            if (rotate90.get() && canRotate90()) {
                cur = rotations(cur, 4);
            } else if (rotate180.get()) {
                cur = rotations(cur, 2);
            }
        }
        Set<Long> seen = new HashSet<Long>();
        List<BlockPos> out = new ArrayList<BlockPos>();
        for (int[] q : cur) {
            BlockPos b = new BlockPos(q[0], q[1], q[2]);
            if (seen.add(b.toLong())) {
                out.add(b);
            }
        }
        return out;
    }

    /** Añade a cada punto su reflejo respecto al plano del nodo en el eje dado. */
    private static List<int[]> mirror(List<int[]> in, int axis) {
        List<int[]> out = new ArrayList<int[]>(in);
        for (int[] q : in) {
            int[] m = q.clone();
            m[axis] = node[axis] - q[axis] - 1;
            out.add(m);
        }
        return out;
    }

    /** Añade a cada punto sus n - 1 giros alrededor del eje vertical que pasa por el nodo. */
    private static List<int[]> rotations(List<int[]> in, int n) {
        List<int[]> out = new ArrayList<int[]>(in);
        for (int[] q : in) {
            int[] r = q;
            for (int k = 1; k < n; k++) {
                r = n == 2 ? turn180(r) : turn90(r);
                out.add(r);
            }
        }
        return out;
    }

    private static int[] turn180(int[] q) {
        return new int[] {node[0] - q[0] - 1, q[1], node[2] - q[2] - 1};
    }

    /** Giro de 90° en sentido horario visto desde arriba, en medios bloques respecto al nodo. */
    private static int[] turn90(int[] q) {
        int cx = 2 * q[0] + 1 - node[0];
        int cz = 2 * q[2] + 1 - node[2];
        return new int[] {(node[0] - cz - 1) / 2, q[1], (node[2] + cx - 1) / 2};
    }
}
