package dev.litbuild;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Operaciones del menú Select: cambian la forma de la selección, no los bloques. Trabajan sobre el conjunto de
 * posiciones (Selection.mask) y dejan el resultado con Selection.combine(..., REPLACE).
 */
public final class SelectionOps {
    /** Tope del volumen de la rejilla de trabajo (Smooth y Distort). */
    private static final long MAX_GRID = 8_000_000L;

    private SelectionOps() {
    }

    /** Convierte una selección irregular en su caja envolvente. */
    public static void boundingBox() {
        if (!Selection.active) {
            return;
        }
        if (Selection.mask == null) {
            EditEngine.status = "La selección ya es un cuboide";
        } else {
            Selection.mask = null;
            EditEngine.status = "Bounding Box: " + Selection.volume() + " bloques";
        }
    }

    /** Expand (n > 0) o Shrink (n < 0): ensancha o estrecha la selección n bloques en todas las direcciones (por las 6 caras en máscaras). */
    public static void offset(int n) {
        if (!Selection.active || n == 0) {
            return;
        }
        if (Selection.mask == null) {
            int[] lo = {Selection.min(0), Math.max(0, Selection.min(1)), Selection.min(2)};
            int[] hi = {Selection.max(0), Math.min(255, Selection.max(1)), Selection.max(2)};
            for (int a = 0; a < 3; a++) {
                if (n < 0 && hi[a] - lo[a] + 1 + 2 * n < 1) {
                    EditEngine.status = "Shrink: la selección es demasiado pequeña";
                    return;
                }
                lo[a] -= n;
                hi[a] += n;
            }
            Selection.set(new BlockPos(lo[0], Math.max(0, lo[1]), lo[2]), new BlockPos(hi[0], Math.min(255, hi[1]), hi[2]));
            EditEngine.status = (n > 0 ? "Expand" : "Shrink") + ": " + Selection.volume() + " bloques";
            return;
        }
        Set<Long> cur = new HashSet<Long>(Selection.mask);
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int step = 0; step < Math.abs(n); step++) {
            Set<Long> next = new HashSet<Long>();
            if (n > 0) {
                for (long k : cur) {
                    next.add(k);
                    BlockPos p = BlockPos.fromLong(k);
                    for (int[] o : d) {
                        int y = p.getY() + o[1];
                        if (y >= 0 && y <= 255) {
                            next.add(Selection.key(p.getX() + o[0], y, p.getZ() + o[2]));
                        }
                    }
                }
            } else {
                for (long k : cur) {
                    BlockPos p = BlockPos.fromLong(k);
                    boolean keep = true;
                    for (int[] o : d) {
                        if (!cur.contains(Selection.key(p.getX() + o[0], p.getY() + o[1], p.getZ() + o[2]))) {
                            keep = false;
                            break;
                        }
                    }
                    if (keep) {
                        next.add(k);
                    }
                }
            }
            if (next.size() > EditEngine.MAX_BLOCKS) {
                EditEngine.status = "Expand: el resultado es demasiado grande";
                return;
            }
            cur = next;
        }
        finish(n > 0 ? "Expand" : "Shrink", cur);
    }

    /** Convierte una selección cóncava en su cierre convexo (todos los bloques dentro del casco de la selección). */
    public static void convexHull() {
        if (!Selection.active) {
            return;
        }
        if (Selection.mask == null) {
            EditEngine.status = "La selección ya es convexa (una caja)";
            return;
        }
        if (Selection.boxVolume() > EditEngine.MAX_BLOCKS) {
            EditEngine.status = "Convex Hull: la caja de la selección es demasiado grande";
            return;
        }
        // Solo cuentan los bloques de la superficie de la selección; el ruido de 1e-5 evita puntos coplanares exactos.
        java.util.Random jitter = new java.util.Random(12345);
        java.util.List<double[]> pts = new java.util.ArrayList<double[]>();
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (long k : Selection.mask) {
            BlockPos q = BlockPos.fromLong(k);
            boolean edge = false;
            for (int[] o : d) {
                if (!Selection.mask.contains(Selection.key(q.getX() + o[0], q.getY() + o[1], q.getZ() + o[2]))) {
                    edge = true;
                    break;
                }
            }
            if (edge) {
                pts.add(new double[] {q.getX() + jitter.nextDouble() * 1e-5, q.getY() + jitter.nextDouble() * 1e-5,
                    q.getZ() + jitter.nextDouble() * 1e-5});
            }
        }
        java.util.List<double[]> planes = Hull3D.planes(pts.toArray(new double[0][]));
        if (planes == null) {
            EditEngine.status = "Convex Hull: la selección es demasiado pequeña o está en línea recta";
            return;
        }
        long columns = (long) Selection.size(0) * Selection.size(2);
        if (columns * planes.size() > 400_000_000L) {
            EditEngine.status = "Convex Hull: demasiado complejo; reduce la selección";
            return;
        }
        Set<Long> out = new HashSet<Long>();
        int y0 = Math.max(0, Selection.min(1)), y1 = Math.min(255, Selection.max(1));
        for (int z = Selection.min(2); z <= Selection.max(2); z++) {
            for (int x = Selection.min(0); x <= Selection.max(0); x++) {
                double[] r = Hull3D.column(planes, x, z, 1e-4);
                if (r == null) {
                    continue;
                }
                int lo = (int) Math.max(y0, Math.ceil(r[0] - 1e-9)), hi = (int) Math.min(y1, Math.floor(r[1] + 1e-9));
                for (int y = lo; y <= hi; y++) {
                    out.add(Selection.key(x, y, z));
                }
            }
        }
        if (out.size() == Selection.mask.size()) {
            EditEngine.status = "Convex Hull: la selección ya era convexa";
            return;
        }
        finish("Convex Hull", out);
    }

    /** Se queda con los bloques de la selección que son block (y meta, si no es -1). */
    public static void filter(Block block, int meta) {
        if (!Selection.active) {
            return;
        }
        if (Selection.volume() > EditEngine.MAX_BLOCKS) {
            EditEngine.status = "Filter: la selección es demasiado grande";
            return;
        }
        World w = Minecraft.getMinecraft().world;
        Set<Long> keep = new HashSet<Long>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = Selection.min(1); y <= Selection.max(1); y++) {
            for (int z = Selection.min(2); z <= Selection.max(2); z++) {
                for (int x = Selection.min(0); x <= Selection.max(0); x++) {
                    p.setPos(x, y, z);
                    if (y < 0 || y > 255 || !Selection.contains(x, y, z) || !w.isBlockLoaded(p)) {
                        continue;
                    }
                    IBlockState s = w.getBlockState(p);
                    if (s.getBlock() == block && (meta < 0 || block.getMetaFromState(s) == meta)) {
                        keep.add(p.toLong());
                    }
                }
            }
        }
        finish("Filter", keep);
    }

    /** Suaviza la forma con un desenfoque gaussiano: se queda con las celdas cuyo valor desenfocado llega a threshold. */
    public static void smooth(int sigma, float threshold) {
        if (!Selection.active) {
            return;
        }
        int k = (int) Math.ceil(2.0 * sigma);
        int[] lo = {Selection.min(0) - k, Selection.min(1) - k, Selection.min(2) - k};
        int[] n = {Selection.size(0) + 2 * k, Selection.size(1) + 2 * k, Selection.size(2) + 2 * k};
        if ((double) n[0] * n[1] * n[2] > MAX_GRID) {
            EditEngine.status = "Smooth: la selección es demasiado grande";
            return;
        }
        float[] occ = new float[n[0] * n[1] * n[2]];
        for (int y = Selection.min(1); y <= Selection.max(1); y++) {
            for (int z = Selection.min(2); z <= Selection.max(2); z++) {
                for (int x = Selection.min(0); x <= Selection.max(0); x++) {
                    if (Selection.contains(x, y, z)) {
                        occ[index(x - lo[0], y - lo[1], z - lo[2], n)] = 1f;
                    }
                }
            }
        }
        float[] blurred = blur(occ, n, kernel(sigma, k), k);
        Set<Long> out = new HashSet<Long>();
        for (int y = 0; y < n[1]; y++) {
            for (int z = 0; z < n[2]; z++) {
                for (int x = 0; x < n[0]; x++) {
                    if (blurred[index(x, y, z, n)] >= threshold && out.size() <= EditEngine.MAX_BLOCKS) {
                        out.add(new BlockPos(lo[0] + x, lo[1] + y, lo[2] + z).toLong());
                    }
                }
            }
        }
        finish("Smooth", out);
    }

    /**
     * Distorsiona la forma con ruido: cada celda pertenece a la nueva selección si la celda desplazada por el ruido
     * (hasta distance bloques) pertenecía a la antigua. scale es el tamaño de las ondulaciones.
     */
    public static void distort(double scale, double distance, long seed) {
        if (!Selection.active) {
            return;
        }
        int e = (int) Math.ceil(distance) + 1;
        int[] lo = {Selection.min(0) - e, Math.max(0, Selection.min(1) - e), Selection.min(2) - e};
        int[] hi = {Selection.max(0) + e, Math.min(255, Selection.max(1) + e), Selection.max(2) + e};
        if ((double) (hi[0] - lo[0] + 1) * (hi[1] - lo[1] + 1) * (hi[2] - lo[2] + 1) > MAX_GRID) {
            EditEngine.status = "Distort: la selección es demasiado grande";
            return;
        }
        Noise noise = new Noise(seed);
        double s = Math.max(0.5, scale);
        Set<Long> out = new HashSet<Long>();
        for (int y = lo[1]; y <= hi[1]; y++) {
            for (int z = lo[2]; z <= hi[2]; z++) {
                for (int x = lo[0]; x <= hi[0]; x++) {
                    int dx = offset(noise.fbm(x / s + 0.37, y / s + 0.11, z / s + 0.53, 1), distance);
                    int dy = offset(noise.fbm(x / s + 97.1, y / s + 0.29, z / s + 0.71, 1), distance);
                    int dz = offset(noise.fbm(x / s + 0.43, y / s + 51.7, z / s + 193.3, 1), distance);
                    if (Selection.contains(x + dx, y + dy, z + dz) && out.size() <= EditEngine.MAX_BLOCKS) {
                        out.add(new BlockPos(x, y, z).toLong());
                    }
                }
            }
        }
        finish("Distort", out);
    }

    private static int offset(double noise, double distance) {
        return (int) Math.round(Math.max(-1.0, Math.min(1.0, noise * 2.0)) * distance);
    }

    private static void finish(String name, Set<Long> out) {
        if (out.size() > EditEngine.MAX_BLOCKS) {
            EditEngine.status = name + ": el resultado es demasiado grande";
        } else if (out.isEmpty()) {
            EditEngine.status = name + ": no queda nada seleccionado (no se ha cambiado)";
        } else {
            Selection.combine(out, Selection.REPLACE);
            EditEngine.status = name + ": " + out.size() + " bloques";
        }
    }

    // ---- Desenfoque sobre una rejilla de caras desiguales ------------------------------------

    private static int index(int x, int y, int z, int[] n) {
        return (y * n[2] + z) * n[0] + x;
    }

    private static float[] kernel(int sigma, int k) {
        float[] g = new float[2 * k + 1];
        float sum = 0;
        for (int i = -k; i <= k; i++) {
            g[i + k] = (float) Math.exp(-(i * i) / (2.0 * sigma * sigma));
            sum += g[i + k];
        }
        for (int i = 0; i < g.length; i++) {
            g[i] /= sum;
        }
        return g;
    }

    static float[] blur(float[] src, int[] n, float[] g, int k) {
        float[] a = src;
        for (int axis = 0; axis < 3; axis++) {
            float[] dst = new float[a.length];
            for (int y = 0; y < n[1]; y++) {
                for (int z = 0; z < n[2]; z++) {
                    for (int x = 0; x < n[0]; x++) {
                        float sum = 0;
                        for (int t = -k; t <= k; t++) {
                            int xx = axis == 0 ? clamp(x + t, n[0]) : x;
                            int yy = axis == 1 ? clamp(y + t, n[1]) : y;
                            int zz = axis == 2 ? clamp(z + t, n[2]) : z;
                            sum += g[t + k] * a[index(xx, yy, zz, n)];
                        }
                        dst[index(x, y, z, n)] = sum;
                    }
                }
            }
            a = dst;
        }
        return a;
    }

    private static int clamp(int v, int n) {
        return v < 0 ? 0 : v >= n ? n - 1 : v;
    }
}
