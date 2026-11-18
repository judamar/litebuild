package dev.litebuild;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Voxeliza las superficies de la herramienta Modelling a partir de puntos 3D agrupados en filas. Sin dependencias
 * de Minecraft. Las coordenadas son continuas (el centro del bloque x es x + 0.5; el voxel de un punto es floor).
 */
public final class Mesh {
    public static final String[] SHAPES = {"Loft (rows)", "Triangle Strip", "Triangle Fan", "Convex Hull"};
    public static final String[] SURFACES = {"Flat", "Catmull-Rom", "Bezier", "Smart Surface"};
    public static final int LOFT = 0, STRIP = 1, FAN = 2, HULL = 3;
    public static final int FLAT = 0, CATMULL = 1, BEZIER = 2, SMART = 3;
    /** Separación máxima entre muestras: menos de medio bloque para que la superficie no deje huecos. */
    private static final double STEP = 0.35;

    private Mesh() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int[] unpack(long k) {
        int x = (int) (k >> 38), z = (int) ((k >> 12) & 0x3FFFFFF), y = (int) (k & 0xFFF);
        if (x >= 0x2000000) {
            x -= 0x4000000;
        }
        if (z >= 0x2000000) {
            z -= 0x4000000;
        }
        return new int[] {x, y, z};
    }

    /**
     * Los voxeles de la superficie. rows = filas de puntos (cada una una lista de {x, y, z}); null si pasa de limit
     * voxeles o la forma no se puede construir (por ejemplo menos de 2 filas en Loft).
     */
    public static Set<Long> voxelize(List<List<double[]>> rows, int shape, int surface, int limit) {
        Set<Long> out = new HashSet<Long>();
        List<double[]> flat = new ArrayList<double[]>();
        for (List<double[]> r : rows) {
            flat.addAll(r);
        }
        if (flat.isEmpty()) {
            return out;
        }
        if (shape == LOFT) {
            if (surface == SMART ? !smart(flat, out, limit) : !loft(rows, surface, out, limit)) {
                return null;
            }
        } else if (shape == STRIP) {
            for (int i = 0; i + 2 < flat.size(); i++) {
                if (!triangle(flat.get(i), flat.get(i + 1), flat.get(i + 2), out, limit)) {
                    return null;
                }
            }
        } else if (shape == FAN) {
            for (int i = 1; i + 1 < flat.size(); i++) {
                if (!triangle(flat.get(0), flat.get(i), flat.get(i + 1), out, limit)) {
                    return null;
                }
            }
        } else {
            double[][] pts = new double[flat.size()][];
            Random jitter = new Random(99);
            for (int i = 0; i < pts.length; i++) {
                double[] q = flat.get(i);
                pts[i] = new double[] {q[0] + jitter.nextDouble() * 1e-5, q[1] + jitter.nextDouble() * 1e-5,
                    q[2] + jitter.nextDouble() * 1e-5};
            }
            List<int[]> tris = Hull3D.triangles(pts);
            if (tris == null) {
                return null;
            }
            for (int[] t : tris) {
                if (!triangle(pts[t[0]], pts[t[1]], pts[t[2]], out, limit)) {
                    return null;
                }
            }
        }
        return out;
    }

    private static void add(Set<Long> out, double[] p) {
        out.add(pack((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2])));
    }

    /** Rellena un triángulo de voxeles por muestreo baricéntrico; false si se pasa del límite. */
    private static boolean triangle(double[] a, double[] b, double[] c, Set<Long> out, int limit) {
        double edge = Math.max(dist(a, b), Math.max(dist(b, c), dist(a, c)));
        int n = Math.max(1, (int) Math.ceil(edge / STEP));
        double[] p = new double[3];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n - i; j++) {
                for (int k = 0; k < 3; k++) {
                    p[k] = a[k] + (b[k] - a[k]) * i / n + (c[k] - a[k]) * j / n;
                }
                add(out, p);
            }
            if (out.size() > limit) {
                return false;
            }
        }
        return true;
    }

    /**
     * Smart Surface: no usa las filas, solo los puntos. Se proyectan en el plano de sus dos ejes más largos, se triangulan
     * con Delaunay (Bowyer-Watson) y cada triángulo se rellena en 3D, así que la superficie sigue la altura que das a
     * cada punto. false si hay menos de 3 puntos distintos o están en línea.
     */
    private static boolean smart(List<double[]> pts, Set<Long> out, int limit) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : pts) {
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], p[k]);
                hi[k] = Math.max(hi[k], p[k]);
            }
        }
        int drop = 0;
        for (int k = 1; k < 3; k++) {
            if (hi[k] - lo[k] < hi[drop] - lo[drop]) {
                drop = k;
            }
        }
        int u = (drop + 1) % 3, v = (drop + 2) % 3;
        List<double[]> uniq = new ArrayList<double[]>();
        Set<Long> seen = new HashSet<Long>();
        for (double[] p : pts) {
            if (seen.add(pack((int) Math.floor(p[u]), (int) Math.floor(p[v]), 0))) {
                uniq.add(p);
            }
        }
        int n = uniq.size();
        if (n < 3) {
            return false;
        }
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = uniq.get(i)[u];
            ys[i] = uniq.get(i)[v];
        }
        List<int[]> tris = delaunay(xs, ys, n);
        double area = 0;
        for (int[] t : tris) {
            area += Math.abs((xs[t[1]] - xs[t[0]]) * (ys[t[2]] - ys[t[0]]) - (ys[t[1]] - ys[t[0]]) * (xs[t[2]] - xs[t[0]]));
        }
        if (tris.isEmpty() || area < 1e-6) {
            return false;
        }
        for (int[] t : tris) {
            if (!triangle(uniq.get(t[0]), uniq.get(t[1]), uniq.get(t[2]), out, limit)) {
                return false;
            }
        }
        return true;
    }

    /** Triangulación de Delaunay en 2D (Bowyer-Watson, O(n^2): sobra para unos pocos puntos). */
    static List<int[]> delaunay(double[] xs, double[] ys, int n) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        double d = Math.max(Math.max(maxX - minX, maxY - minY), 1);
        double mx = (minX + maxX) / 2, my = (minY + maxY) / 2;
        double[] x = java.util.Arrays.copyOf(xs, n + 3), y = java.util.Arrays.copyOf(ys, n + 3);
        x[n] = mx - 20 * d;
        y[n] = my - d;
        x[n + 1] = mx;
        y[n + 1] = my + 20 * d;
        x[n + 2] = mx + 20 * d;
        y[n + 2] = my - d;
        List<int[]> tris = new ArrayList<int[]>();
        tris.add(new int[] {n, n + 1, n + 2});
        for (int i = 0; i < n; i++) {
            List<int[]> bad = new ArrayList<int[]>();
            for (int[] t : tris) {
                if (inCircle(x, y, t, i)) {
                    bad.add(t);
                }
            }
            java.util.Map<Long, Integer> edges = new java.util.HashMap<Long, Integer>();
            for (int[] t : bad) {
                for (int e = 0; e < 3; e++) {
                    int a = t[e], b = t[(e + 1) % 3];
                    long k = (long) Math.min(a, b) * (n + 3) + Math.max(a, b);
                    Integer c = edges.get(k);
                    edges.put(k, c == null ? 1 : c + 1);
                }
            }
            tris.removeAll(bad);
            for (java.util.Map.Entry<Long, Integer> e : edges.entrySet()) {
                if (e.getValue() == 1) {
                    tris.add(new int[] {(int) (e.getKey() / (n + 3)), (int) (e.getKey() % (n + 3)), i});
                }
            }
        }
        List<int[]> result = new ArrayList<int[]>();
        for (int[] t : tris) {
            if (t[0] < n && t[1] < n && t[2] < n) {
                result.add(t);
            }
        }
        return result;
    }

    private static boolean inCircle(double[] x, double[] y, int[] t, int p) {
        double ax = x[t[0]] - x[p], ay = y[t[0]] - y[p];
        double bx = x[t[1]] - x[p], by = y[t[1]] - y[p];
        double cx = x[t[2]] - x[p], cy = y[t[2]] - y[p];
        double det = (ax * ax + ay * ay) * (bx * cy - cx * by) - (bx * bx + by * by) * (ax * cy - cx * ay)
                + (cx * cx + cy * cy) * (ax * by - bx * ay);
        double orient = (x[t[1]] - x[t[0]]) * (y[t[2]] - y[t[0]]) - (y[t[1]] - y[t[0]]) * (x[t[2]] - x[t[0]]);
        return orient > 0 ? det > 0 : det < 0;
    }

    private static boolean loft(List<List<double[]>> rows, int surface, Set<Long> out, int limit) {
        List<List<double[]>> use = new ArrayList<List<double[]>>();
        for (List<double[]> r : rows) {
            if (!r.isEmpty()) {
                use.add(r);
            }
        }
        if (use.size() < 2) {
            return false;
        }
        // Tamaño aproximado para elegir cuántas muestras: la fila más larga y lo que separan las filas.
        double along = 0;
        for (List<double[]> r : use) {
            along = Math.max(along, polyLength(r));
        }
        int nt = Math.max(2, (int) Math.ceil(along / STEP) + 1);
        double across = 0;
        for (int s = 0; s <= 2; s++) {
            List<double[]> col = new ArrayList<double[]>();
            for (List<double[]> r : use) {
                col.add(curve(r, surface, s / 2.0));
            }
            across = Math.max(across, polyLength(col));
        }
        int nu = Math.max(2, (int) Math.ceil(across / STEP) + 1);
        if ((long) nt * nu > 6_000_000L) {
            return false;
        }
        for (int i = 0; i < nt; i++) {
            double t = i / (double) (nt - 1);
            List<double[]> col = new ArrayList<double[]>();
            for (List<double[]> r : use) {
                col.add(curve(r, surface, t));
            }
            for (int j = 0; j < nu; j++) {
                add(out, curve(col, surface, j / (double) (nu - 1)));
            }
            if (out.size() > limit) {
                return false;
            }
        }
        return true;
    }

    /** El punto de la curva que define pts en t (0..1): polilínea, Catmull-Rom (pasa por todos) o Bezier (control). */
    static double[] curve(List<double[]> pts, int type, double t) {
        int n = pts.size();
        if (n == 1) {
            return pts.get(0).clone();
        }
        double s = Math.max(0, Math.min(1, t)) * (n - 1);
        if (type == BEZIER) {
            double[][] w = new double[n][];
            for (int i = 0; i < n; i++) {
                w[i] = pts.get(i).clone();
            }
            double u = Math.max(0, Math.min(1, t));
            for (int level = n - 1; level > 0; level--) {
                for (int i = 0; i < level; i++) {
                    for (int k = 0; k < 3; k++) {
                        w[i][k] = w[i][k] * (1 - u) + w[i + 1][k] * u;
                    }
                }
            }
            return w[0];
        }
        int i = Math.min(n - 2, (int) Math.floor(s));
        double f = s - i;
        double[] p1 = pts.get(i), p2 = pts.get(i + 1);
        if (type == FLAT) {
            return new double[] {p1[0] + (p2[0] - p1[0]) * f, p1[1] + (p2[1] - p1[1]) * f, p1[2] + (p2[2] - p1[2]) * f};
        }
        double[] p0 = pts.get(Math.max(0, i - 1)), p3 = pts.get(Math.min(n - 1, i + 2));
        double[] r = new double[3];
        for (int k = 0; k < 3; k++) {
            r[k] = 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * f + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * f * f
                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * f * f * f);
        }
        return r;
    }

    private static double polyLength(List<double[]> pts) {
        double l = 0;
        for (int i = 1; i < pts.size(); i++) {
            l += dist(pts.get(i - 1), pts.get(i));
        }
        return l;
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
