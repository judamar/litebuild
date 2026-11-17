package dev.litbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Suavizado de terreno (Smooth, Melt y Weld): desenfoque gaussiano de la ocupación sólida dentro
 * del pincel y reparto de los bloques en las celdas con más valor. Con ratio 100 % conserva el
 * número de bloques; por debajo quita masa (Melt) y por encima la añade (Weld).
 */
public final class Terrain {
    private Terrain() {
    }

    public interface Setter {
        void set(int x, int y, int z, IBlockState state);
    }

    /**
     * sigma = fuerza del desenfoque; ratio = % de bloques que debe quedar; add = bloque para las
     * celdas nuevas (null: el más común entre los vecinos sólidos). mask limita qué celdas se tocan.
     */
    public static void smooth(World w, int cx, int cy, int cz, int radius, int shape, int sigma, int ratio,
            IBlockState add, MaskSpec mask, double jitter, long seed, Setter out) {
        int k = (int) Math.ceil(2.0 * sigma);
        int e = radius + k;
        int n = 2 * e + 1;
        IBlockState[] st = new IBlockState[n * n * n];
        float[] occ = new float[st.length];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -e; dy <= e; dy++) {
            for (int dz = -e; dz <= e; dz++) {
                for (int dx = -e; dx <= e; dx++) {
                    int y = cy + dy;
                    p.setPos(cx + dx, y, cz + dz);
                    int i = index(dx + e, dy + e, dz + e, n);
                    if (y >= 0 && y <= 255 && w.isBlockLoaded(p)) {
                        st[i] = w.getBlockState(p);
                        occ[i] = st[i].getMaterial().isSolid() ? 1f : 0f;
                    }
                }
            }
        }

        // Celdas del pincel que se pueden tocar.
        final float[] blurred = blur(occ, n, kernel(sigma, k), k);
        List<Integer> cells = new ArrayList<Integer>();
        int solid = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (!Brush.contains(shape, dx, dy, dz, radius)) {
                        continue;
                    }
                    int i = index(dx + e, dy + e, dz + e, n);
                    if (st[i] == null || (mask != null && !mask.test(w, cx + dx, cy + dy, cz + dz))) {
                        continue;
                    }
                    cells.add(i);
                    if (occ[i] > 0.5f) {
                        solid++;
                    }
                }
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        if (jitter > 0) {
            // Roughen: se perturba el orden con ruido blanco para que el borde quede dentado.
            for (int i : cells) {
                int lx = i % n - e, lz = (i / n) % n - e, ly = i / (n * n) - e;
                blurred[i] += (float) (jitter * (Noise.white(cx + lx, cy + ly, cz + lz, seed) - 0.5));
            }
        }
        final float[] occFinal = occ;
        Collections.sort(cells, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int c = Float.compare(blurred[b], blurred[a]);
                // A igualdad de valor se prefiere dejar como está lo que ya era sólido.
                return c != 0 ? c : Float.compare(occFinal[b], occFinal[a]);
            }
        });
        int keep = Math.max(0, Math.min(cells.size(), Math.round(solid * ratio / 100f)));
        IBlockState air = Blocks.AIR.getDefaultState();
        for (int rank = 0; rank < cells.size(); rank++) {
            int i = cells.get(rank);
            boolean wasSolid = occ[i] > 0.5f;
            boolean wantSolid = rank < keep;
            if (wantSolid == wasSolid) {
                continue;
            }
            int lx = i % n - e, lz = (i / n) % n - e, ly = i / (n * n) - e;
            if (!wantSolid) {
                out.set(cx + lx, cy + ly, cz + lz, air);
                continue;
            }
            IBlockState fill = add != null ? add : dominantNeighbour(st, n, i);
            if (fill != null) {
                out.set(cx + lx, cy + ly, cz + lz, fill);
            }
        }
    }

    /**
     * Shatter: abre grietas con ruido celular (Voronoi). En las fronteras entre celdas, de ancho width bloques,
     * los bloques sólidos se quitan o, con fill, se sustituyen por ese bloque.
     */
    public static void shatter(World w, int cx, int cy, int cz, int radius, int shape, double scale, double width,
            long seed, IBlockState fill, MaskSpec mask, Setter out) {
        IBlockState air = Blocks.AIR.getDefaultState();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (!Brush.contains(shape, dx, dy, dz, radius)) {
                        continue;
                    }
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    IBlockState cur = state(w, x, y, z);
                    if (cur == null || !cur.getMaterial().isSolid() || (mask != null && !mask.test(w, x, y, z))) {
                        continue;
                    }
                    double edge = Noise.cellEdge((x + 0.5) / scale + 0.31, (y + 0.5) / scale + 0.17,
                            (z + 0.5) / scale + 0.53, seed) * scale;
                    if (edge < width) {
                        out.set(x, y, z, fill != null ? fill : air);
                    }
                }
            }
        }
    }

    public static final int RAISE = 0, LOWER = 1, FLATTEN = 2;
    public static final String[] ELEVATION_MODES = {"Raise", "Lower", "Flatten"};

    /**
     * Elevation: sube, baja o aplana el terreno por columnas dentro del círculo del pincel; el efecto es
     * máximo en el centro y se desvanece hacia el borde. Flatten lleva cada columna hacia la altura de la
     * columna central. Las capas nuevas repiten el bloque de debajo de la superficie y la capa superior
     * conserva el bloque de la superficie (hierba, arena...).
     */
    public static void elevate(World w, int cx, int cy, int cz, int radius, int mode, int amount, MaskSpec mask,
            Setter out) {
        int yHi = cy + radius;
        int yLo = cy - 2 * radius;
        int target = surfaceY(w, cx, cz, yHi, yLo);
        IBlockState air = Blocks.AIR.getDefaultState();
        double lim = radius + 0.5;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > lim) {
                    continue;
                }
                double weight = 0.5 + 0.5 * Math.cos(Math.PI * dist / lim);
                int x = cx + dx, z = cz + dz;
                int top = surfaceY(w, x, z, yHi, yLo);
                if (top < 0 || (mask != null && !mask.test(w, x, top, z))) {
                    continue;
                }
                int delta;
                if (mode == RAISE) {
                    delta = (int) Math.round(amount * weight);
                } else if (mode == LOWER) {
                    delta = -(int) Math.round(amount * weight);
                } else if (target >= 0) {
                    delta = (int) Math.round((target - top) * weight);
                } else {
                    continue;
                }
                if (delta == 0) {
                    continue;
                }
                IBlockState surface = state(w, x, top, z);
                IBlockState under = state(w, x, top - 1, z);
                if (under == null || !under.getMaterial().isSolid()) {
                    under = surface;
                }
                if (delta > 0) {
                    out.set(x, top, z, under);
                    for (int k = 1; k <= delta; k++) {
                        out.set(x, top + k, z, k == delta ? surface : under);
                    }
                } else {
                    int n = -delta;
                    for (int k = 0; k < n; k++) {
                        out.set(x, top - k, z, air);
                    }
                    out.set(x, top - n, z, surface);
                }
            }
        }
    }

    /**
     * Sculpt Draw: suma (o resta con invert) una protuberancia suave al terreno, con el pico desplazado a lo
     * largo de la normal de la superficie (o recto hacia arriba con maskY). strength escala la altura de la
     * protuberancia; con denoise el campo se desenfoca antes de decidir qué celdas cambian, así la forma sale
     * más lisa. Las celdas nuevas copian el bloque más repetido de sus vecinos y se van encadenando hacia fuera.
     */
    public static void sculpt(World w, int cx, int cy, int cz, int radius, double strength, boolean invert,
            boolean maskY, boolean denoise, MaskSpec mask, Setter out) {
        final int k = denoise ? 2 : 0;
        int e = radius + k;
        int n = 2 * e + 1;
        IBlockState[] st = new IBlockState[n * n * n];
        float[] occ = new float[st.length];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -e; dy <= e; dy++) {
            for (int dz = -e; dz <= e; dz++) {
                for (int dx = -e; dx <= e; dx++) {
                    int y = cy + dy;
                    p.setPos(cx + dx, y, cz + dz);
                    int i = index(dx + e, dy + e, dz + e, n);
                    if (y >= 0 && y <= 255 && w.isBlockLoaded(p)) {
                        st[i] = w.getBlockState(p);
                        occ[i] = st[i].getMaterial().isSolid() ? 1f : 0f;
                    }
                }
            }
        }
        double lim = radius + 0.5;

        // Normal: hacia donde está el aire dentro del pincel.
        double nx = 0, ny = 1, nz = 0;
        if (!maskY) {
            double ax = 0, ay = 0, az = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int i = index(dx + e, dy + e, dz + e, n);
                        if (dx * dx + dy * dy + dz * dz <= lim * lim && st[i] != null && occ[i] == 0f) {
                            ax += dx;
                            ay += dy;
                            az += dz;
                        }
                    }
                }
            }
            double len = Math.sqrt(ax * ax + ay * ay + az * az);
            if (len > 1e-6) {
                nx = ax / len;
                ny = ay / len;
                nz = az / len;
            }
        }
        double shift = (invert ? -0.2 : 0.25) * radius;
        double bx = nx * shift, by = ny * shift, bz = nz * shift;

        float[] field = new float[occ.length];
        for (int dy = -e; dy <= e; dy++) {
            for (int dz = -e; dz <= e; dz++) {
                for (int dx = -e; dx <= e; dx++) {
                    int i = index(dx + e, dy + e, dz + e, n);
                    double d2 = ((dx - bx) * (dx - bx) + (dy - by) * (dy - by) + (dz - bz) * (dz - bz)) / (lim * lim);
                    double a = d2 < 1 ? strength * (1 - d2) * (1 - d2) : 0;
                    field[i] = occ[i] + (float) (invert ? -a : a);
                }
            }
        }
        float[] g = k > 0 ? kernel(1, k) : null;
        float[] after = k > 0 ? blur(field, n, g, k) : field;
        float[] before = k > 0 ? blur(occ, n, g, k) : occ;

        IBlockState air = Blocks.AIR.getDefaultState();
        List<Integer> pending = new ArrayList<Integer>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy + dz * dz > lim * lim) {
                        continue;
                    }
                    int i = index(dx + e, dy + e, dz + e, n);
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (st[i] == null || (mask != null && !mask.test(w, x, y, z))) {
                        continue;
                    }
                    if (invert) {
                        if (occ[i] == 1f && after[i] < 0.5f && (k == 0 || before[i] >= 0.5f)) {
                            out.set(x, y, z, air);
                        }
                    } else if (occ[i] == 0f && after[i] > 0.5f && (k == 0 || before[i] <= 0.5f)) {
                        pending.add(i);
                    }
                }
            }
        }
        // Las celdas nuevas copian a sus vecinos; se repite para que el relleno crezca hacia fuera.
        IBlockState[] made = new IBlockState[st.length];
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (int j = pending.size() - 1; j >= 0; j--) {
                int i = pending.get(j);
                IBlockState b = dominantNeighbour(st, n, i);
                if (b != null) {
                    st[i] = b;
                    made[i] = b;
                    pending.remove(j);
                    progress = true;
                }
            }
        }
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int i = index(dx + e, dy + e, dz + e, n);
                    if (made[i] == null) {
                        continue;
                    }
                    IBlockState b = made[i];
                    IBlockState above = dy + e + 1 < n ? st[index(dx + e, dy + e + 1, dz + e, n)] : null;
                    if (above != null && above.getMaterial().isSolid()
                            && (b.getBlock() == Blocks.GRASS || b.getBlock() == Blocks.MYCELIUM)) {
                        b = Blocks.DIRT.getDefaultState();
                    }
                    out.set(cx + dx, cy + dy, cz + dz, b);
                }
            }
        }
    }

    /**
     * Slope: lleva las columnas del círculo de radio radius hacia un plano (o un cono) definido por el punto de
     * inicio (sx, sy, sz) y el objetivo (tx, ty, tz). En modo plano la altura sigue la recta inicio-objetivo
     * en horizontal; en modo cono es una pendiente radial alrededor del inicio, que sube si el objetivo está
     * por encima y baja si está por debajo. sharpness: lo rápido que el efecto llega al 100 % desde el borde;
     * height: cambio máximo de altura por columna.
     */
    public static void slope(World w, int cx, int cz, int radius, int sx, int sy, int sz, int tx, int ty, int tz,
            boolean cone, int height, double sharpness, MaskSpec mask, Setter out) {
        double hdx = tx - sx, hdz = tz - sz;
        double dist = Math.sqrt(hdx * hdx + hdz * hdz);
        if (dist < 1e-6) {
            return;
        }
        double m = (ty - sy) / dist;
        double ux = hdx / dist, uz = hdz / dist;
        IBlockState air = Blocks.AIR.getDefaultState();
        double lim = radius + 0.5;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > lim) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                double along = cone ? Math.sqrt((x - sx) * (x - sx) + (z - sz) * (z - sz))
                        : (x - sx) * ux + (z - sz) * uz;
                int plane = (int) Math.round(sy + m * along);
                int top = surfaceY(w, x, z, plane + height, plane - height);
                if (top < 0 || (mask != null && !mask.test(w, x, top, z))) {
                    continue;
                }
                double weight = Math.min(1.0, (1.0 - d / lim) * sharpness);
                int delta = (int) Math.round((plane - top) * weight);
                delta = Math.max(-height, Math.min(height, delta));
                if (delta == 0) {
                    continue;
                }
                IBlockState surface = state(w, x, top, z);
                IBlockState under = state(w, x, top - 1, z);
                if (under == null || !under.getMaterial().isSolid()) {
                    under = surface;
                }
                if (delta > 0) {
                    out.set(x, top, z, under);
                    for (int q = 1; q <= delta; q++) {
                        out.set(x, top + q, z, q == delta ? surface : under);
                    }
                } else {
                    int cut = -delta;
                    for (int q = 0; q < cut; q++) {
                        out.set(x, top - q, z, air);
                    }
                    out.set(x, top - cut, z, surface);
                }
            }
        }
    }

    /** Altura (y) del bloque sólido más alto de la columna con aire o algo no sólido encima, o -1. */
    static int surfaceY(World w, int x, int z, int yFrom, int yTo) {
        for (int y = Math.min(254, yFrom); y >= Math.max(0, yTo); y--) {
            IBlockState s = state(w, x, y, z);
            if (s != null && s.getMaterial().isSolid()) {
                IBlockState above = state(w, x, y + 1, z);
                if (above == null || !above.getMaterial().isSolid()) {
                    return y;
                }
            }
        }
        return -1;
    }

    private static IBlockState state(World w, int x, int y, int z) {
        if (y < 0 || y > 255) {
            return null;
        }
        BlockPos p = new BlockPos(x, y, z);
        return w.isBlockLoaded(p) ? w.getBlockState(p) : null;
    }

    /**
     * Distort: cada bloque del pincel pasa a ser el que hay en una posición desplazada por ruido
     * (deformación de dominio), así que el terreno se vuelve irregular. El efecto se desvanece hacia el borde.
     */
    public static void distort(World w, int cx, int cy, int cz, int radius, int shape, double scale, double distance,
            Noise noise, MaskSpec mask, Setter out) {
        int k = (int) Math.ceil(distance);
        int e = radius + k;
        int n = 2 * e + 1;
        IBlockState[] st = new IBlockState[n * n * n];
        for (int dy = -e; dy <= e; dy++) {
            for (int dz = -e; dz <= e; dz++) {
                for (int dx = -e; dx <= e; dx++) {
                    st[index(dx + e, dy + e, dz + e, n)] = state(w, cx + dx, cy + dy, cz + dz);
                }
            }
        }
        double lim = radius + 0.5;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (!Brush.contains(shape, dx, dy, dz, radius)) {
                        continue;
                    }
                    IBlockState cur = st[index(dx + e, dy + e, dz + e, n)];
                    if (cur == null || (mask != null && !mask.test(w, cx + dx, cy + dy, cz + dz))) {
                        continue;
                    }
                    double r = Math.sqrt(dx * dx + dy * dy + dz * dz) / lim;
                    double fall = Math.max(0.0, 1.0 - r * r);
                    double px = (cx + dx) / scale, py = (cy + dy) / scale, pz = (cz + dz) / scale;
                    int ox = offset(noise.fbm(px + 0.137, py + 0.271, pz + 0.389, 2), distance, fall);
                    int oy = offset(noise.fbm(px + 31.7, py + 12.3, pz + 7.9, 2), distance, fall);
                    int oz = offset(noise.fbm(px + 101.1, py + 53.9, pz + 77.3, 2), distance, fall);
                    int sx = Math.max(-e, Math.min(e, dx + ox));
                    int sy = Math.max(-e, Math.min(e, dy + oy));
                    int sz = Math.max(-e, Math.min(e, dz + oz));
                    IBlockState src = st[index(sx + e, sy + e, sz + e, n)];
                    if (src != null && src != cur) {
                        out.set(cx + dx, cy + dy, cz + dz, src);
                    }
                }
            }
        }
    }

    /** El ruido normalizado ronda ±0,5: se amplía, se limita a ±1 y se escala a bloques. */
    private static int offset(double noise, double distance, double fall) {
        return (int) Math.round(Math.max(-1.0, Math.min(1.0, noise * 2.0)) * distance * fall);
    }

    private static int index(int x, int y, int z, int n) {
        return (y * n + z) * n + x;
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

    /** Desenfoque separable (x, luego y, luego z) con los bordes replicados. */
    private static float[] blur(float[] src, int n, float[] g, int k) {
        float[] a = src;
        for (int axis = 0; axis < 3; axis++) {
            float[] dst = new float[a.length];
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    for (int x = 0; x < n; x++) {
                        float sum = 0;
                        for (int t = -k; t <= k; t++) {
                            int xx = axis == 0 ? clamp(x + t, n) : x;
                            int yy = axis == 1 ? clamp(y + t, n) : y;
                            int zz = axis == 2 ? clamp(z + t, n) : z;
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

    /** El bloque sólido más repetido entre los 26 vecinos de la celda, o null si no hay ninguno. */
    private static IBlockState dominantNeighbour(IBlockState[] st, int n, int i) {
        int x = i % n, z = (i / n) % n, y = i / (n * n);
        Map<IBlockState, Integer> count = new HashMap<IBlockState, Integer>();
        IBlockState best = null;
        int bestCount = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy, zz = z + dz;
                    if ((dx == 0 && dy == 0 && dz == 0) || xx < 0 || yy < 0 || zz < 0 || xx >= n || yy >= n || zz >= n) {
                        continue;
                    }
                    IBlockState s = st[index(xx, yy, zz, n)];
                    if (s == null || !s.getMaterial().isSolid()) {
                        continue;
                    }
                    Integer c = count.get(s);
                    int nc = c == null ? 1 : c + 1;
                    count.put(s, nc);
                    if (nc > bestCount) {
                        bestCount = nc;
                        best = s;
                    }
                }
            }
        }
        return best;
    }
}
