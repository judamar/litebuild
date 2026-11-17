package dev.litbuild;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.util.math.BlockPos;

/**
 * Path: una lista de nodos unidos por una línea o una curva. Se muestrea la curva en puntos separados como
 * mucho medio bloque y cada punto marca los bloques de una esfera de radio r, de modo que el camino no
 * deja huecos.
 */
public final class PathTool {
    public static final String[] TYPES = {"Line", "Catmull-Rom", "Bezier"};
    public static final int LINE = 0, CATMULL_ROM = 1, BEZIER = 2;

    public static final List<BlockPos> nodes = new ArrayList<BlockPos>();
    public static final ImInt type = new ImInt(LINE);
    public static final int[] radius = {0};
    public static final ImBoolean looped = new ImBoolean(false);
    public static final ImBoolean keepExisting = new ImBoolean(false);

    private static final double STEP = 0.5;

    private PathTool() {
    }

    /** Puntos a lo largo del camino (centros de bloque), consecutivos a menos de STEP bloques. */
    public static List<double[]> samples() {
        List<double[]> out = new ArrayList<double[]>();
        int n = nodes.size();
        if (n == 0) {
            return out;
        }
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) {
            p[i] = new double[] {nodes.get(i).getX() + 0.5, nodes.get(i).getY() + 0.5, nodes.get(i).getZ() + 0.5};
        }
        if (n == 1) {
            out.add(p[0]);
        } else if (type.get() == CATMULL_ROM) {
            catmullRom(p, out);
        } else if (type.get() == BEZIER) {
            bezier(p, out);
        } else {
            line(p, out);
        }
        return out;
    }

    private static void line(double[][] p, List<double[]> out) {
        int segments = looped.get() ? p.length : p.length - 1;
        out.add(p[0]);
        for (int i = 0; i < segments; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            int steps = Math.max(1, (int) Math.ceil(dist(a, b) / STEP));
            for (int k = 1; k <= steps; k++) {
                double t = k / (double) steps;
                out.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
            }
        }
    }

    /** Spline de Catmull-Rom: pasa por todos los nodos. En los extremos repite el nodo (o cierra el bucle). */
    private static void catmullRom(double[][] p, List<double[]> out) {
        int n = p.length;
        int segments = looped.get() ? n : n - 1;
        out.add(p[0]);
        for (int i = 0; i < segments; i++) {
            double[] p0 = looped.get() ? p[(i - 1 + n) % n] : p[Math.max(0, i - 1)];
            double[] p1 = p[i];
            double[] p2 = p[(i + 1) % n];
            double[] p3 = looped.get() ? p[(i + 2) % n] : p[Math.min(n - 1, i + 2)];
            int steps = Math.max(2, (int) Math.ceil(dist(p1, p2) * 2 / STEP));
            for (int k = 1; k <= steps; k++) {
                double t = k / (double) steps, t2 = t * t, t3 = t2 * t;
                double[] q = new double[3];
                for (int c = 0; c < 3; c++) {
                    q[c] = 0.5 * (2 * p1[c] + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2
                            + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3);
                }
                out.add(q);
            }
        }
    }

    /** Curva de Bézier con todos los nodos como polígono de control: solo toca el primero y el último. */
    private static void bezier(double[][] p, List<double[]> out) {
        int n = looped.get() ? p.length + 1 : p.length;
        double[][] ctrl = new double[n][];
        for (int i = 0; i < n; i++) {
            ctrl[i] = p[i % p.length];
        }
        double length = 0;
        for (int i = 1; i < n; i++) {
            length += dist(ctrl[i - 1], ctrl[i]);
        }
        int steps = Math.max(8, (int) Math.ceil(length / STEP) * 2);
        for (int k = 0; k <= steps; k++) {
            double t = k / (double) steps;
            double[][] w = new double[n][];
            for (int i = 0; i < n; i++) {
                w[i] = ctrl[i].clone();
            }
            for (int level = n - 1; level > 0; level--) {
                for (int i = 0; i < level; i++) {
                    for (int c = 0; c < 3; c++) {
                        w[i][c] = w[i][c] + (w[i + 1][c] - w[i][c]) * t;
                    }
                }
            }
            out.add(w[0]);
        }
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Longitud aproximada del camino en bloques. */
    public static double length() {
        List<double[]> s = samples();
        double len = 0;
        for (int i = 1; i < s.size(); i++) {
            len += dist(s.get(i - 1), s.get(i));
        }
        return len;
    }

    /** Los bloques del camino (claves de BlockPos.toLong), sin repetidos. */
    public static long[] voxels() {
        int r = radius[0];
        double lim = (r + 0.5) * (r + 0.5);
        Set<Long> out = new LinkedHashSet<Long>();
        for (double[] q : samples()) {
            int cx = (int) Math.floor(q[0]), cy = (int) Math.floor(q[1]), cz = (int) Math.floor(q[2]);
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (dx * dx + dy * dy + dz * dz <= lim) {
                            out.add(new BlockPos(cx + dx, cy + dy, cz + dz).toLong());
                        }
                    }
                }
            }
        }
        long[] arr = new long[out.size()];
        int i = 0;
        for (Long k : out) {
            arr[i++] = k;
        }
        return arr;
    }
}
