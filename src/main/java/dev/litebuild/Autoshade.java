package dev.litebuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Autoshade: sustituye los bloques de la superficie de la selección por los de una paleta ordenada de oscuro a
 * claro, según cuánta luz recibe cada uno. La luz puede venir del sol (una dirección) y de luces puntuales (la
 * posición del jugador o varias posiciones con su intensidad y alcance, con caída lineal). La luz directa es el
 * coseno entre la normal de las caras expuestas y la luz, con sombras por rayo. Los rebotes (iluminación global)
 * suman a cada bloque parte de la luz de los bloques de su entorno que lo ven; la luz ambiente y la oclusión
 * ambiental (cuántos de los 26 vecinos son opacos, descontando el suelo plano) completan el brillo. El ruido
 * blanco (dither) mezcla bloques vecinos de la paleta en las transiciones.
 */
public final class Autoshade {
    public static final class Params {
        public boolean sunOn = true;
        public double[] sun = {0, 1, 0};
        /** Luces puntuales: {x, y, z, intensidad (0-1), alcance en bloques}. */
        public List<double[]> lights = new ArrayList<double[]>();
        public double ambient = 0.25;
        public double ao = 0.5;
        public double dither = 0.3;
        /** Número de rebotes de luz (0 = sin iluminación global) y qué parte de la luz vecina se recoge en cada uno. */
        public int bounces = 0;
        public double bounceStrength = 0.5;
        /** Estados de la paleta de oscuro a claro; null = los bloques de la selección ordenados por luminosidad. */
        public int[] palette;
        /** Filtros de la paleta automática: nombres de bloque que deben contener alguno de include (vacío = todos) y ninguno de exclude. */
        public String include = "", exclude = "";
        public long seed = 1;
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** Fracción de vecinos opacos de un bloque sobre un suelo plano (9 de 26). */
    private static final double FLAT = 9.0 / 26.0;
    private static final int GI_RADIUS = 2;

    private Autoshade() {
    }

    /** Vector hacia el sol: azimut 0 = sur, 90 = oeste; elevación sobre el horizonte. */
    public static double[] sunVector(double azimuthDeg, double elevationDeg) {
        double az = Math.toRadians(azimuthDeg), el = Math.toRadians(elevationDeg);
        return new double[] {-Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)};
    }

    private static boolean matches(String name, String list) {
        for (String t : list.split(",")) {
            t = t.trim().toLowerCase(java.util.Locale.ROOT);
            if (!t.isEmpty() && name.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sombrea la selección y devuelve cuántos bloques ha evaluado (-1 si no hay paleta usable). Requiere
     * BlockColors.ensureBuilt para la paleta automática.
     */
    public static int compute(World w, int[] box, Set<Long> mask, Params p, Terrain.Setter out) {
        final BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        List<int[]> cells = new ArrayList<int[]>();
        Set<Integer> kinds = new LinkedHashSet<Integer>();
        boolean filtered = !p.include.trim().isEmpty() || !p.exclude.trim().isEmpty();
        for (int y = box[1]; y <= box[4]; y++) {
            for (int z = box[2]; z <= box[5]; z++) {
                for (int x = box[0]; x <= box[3]; x++) {
                    if (mask != null && !mask.contains(new BlockPos(x, y, z).toLong())) {
                        continue;
                    }
                    IBlockState s = state(w, q, x, y, z);
                    if (s != null && s.isOpaqueCube() && s.isFullCube()) {
                        cells.add(new int[] {x, y, z});
                        kinds.add(Block.getStateId(s));
                    }
                }
            }
        }
        int[] palette = p.palette;
        if (palette == null) {
            List<Integer> sorted = new ArrayList<Integer>();
            for (int id : kinds) {
                if (filtered) {
                    String name = String.valueOf(Block.REGISTRY.getNameForObject(Block.getStateById(id).getBlock()));
                    boolean inc = p.include.trim().isEmpty() || matches(name, p.include);
                    if (!inc || matches(name, p.exclude)) {
                        continue;
                    }
                }
                sorted.add(id);
            }
            Collections.sort(sorted, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Double.compare(luminance(a), luminance(b));
                }
            });
            palette = new int[sorted.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = sorted.get(i);
            }
        }
        if (palette.length < 2) {
            return -1;
        }
        int n = palette.length;
        double step = 1.0 / (n - 1);

        // Fase 1: luz directa y oclusión de cada bloque con alguna cara expuesta.
        int m = 0;
        int[][] pos = new int[cells.size()][];
        double[][] normal = new double[cells.size()][];
        double[] light = new double[cells.size()];
        double[] occ = new double[cells.size()];
        for (int[] c : cells) {
            double[] nrm = exposedNormal(w, q, c[0], c[1], c[2]);
            if (nrm == null) {
                continue; // sin caras expuestas: el interior no se toca
            }
            pos[m] = c;
            normal[m] = nrm;
            light[m] = Math.min(1, direct(w, q, c, nrm, p));
            occ[m] = occlusion(w, q, c[0], c[1], c[2]);
            m++;
        }

        // Fase 2: rebotes. Cada bloque recoge luz de los vecinos (a distancia <= GI_RADIUS) que quedan delante de su cara.
        if (p.bounces > 0 && p.bounceStrength > 0 && m > 0) {
            Map<Long, Integer> index = new HashMap<Long, Integer>();
            for (int i = 0; i < m; i++) {
                index.put(Mesh.pack(pos[i][0], pos[i][1], pos[i][2]), i);
            }
            double[] next = new double[m];
            for (int b = 0; b < p.bounces; b++) {
                for (int i = 0; i < m; i++) {
                    double sum = 0, weight = 0;
                    for (int dy = -GI_RADIUS; dy <= GI_RADIUS; dy++) {
                        for (int dz = -GI_RADIUS; dz <= GI_RADIUS; dz++) {
                            for (int dx = -GI_RADIUS; dx <= GI_RADIUS; dx++) {
                                if (dx == 0 && dy == 0 && dz == 0) {
                                    continue;
                                }
                                if (dx * normal[i][0] + dy * normal[i][1] + dz * normal[i][2] <= 0) {
                                    continue;
                                }
                                Integer j = index.get(Mesh.pack(pos[i][0] + dx, pos[i][1] + dy, pos[i][2] + dz));
                                if (j != null) {
                                    double wgt = 1.0 / (1 + dx * dx + dy * dy + dz * dz);
                                    sum += light[j] * wgt;
                                    weight += wgt;
                                }
                            }
                        }
                    }
                    double indirect = weight > 0 ? sum / weight : 0;
                    next[i] = Math.min(1, light[i] + p.bounceStrength * (1 - light[i]) * indirect);
                }
                System.arraycopy(next, 0, light, 0, m);
            }
        }

        for (int i = 0; i < m; i++) {
            double b = (p.ambient + (1 - p.ambient) * light[i]) * (1 - p.ao * occ[i]);
            b += (Noise.white(pos[i][0], pos[i][1], pos[i][2], p.seed) - 0.5) * p.dither * step * 2;
            int idx = (int) Math.round(Math.max(0, Math.min(1, b)) * (n - 1));
            out.set(pos[i][0], pos[i][1], pos[i][2], Block.getStateById(palette[idx]));
        }
        return m;
    }

    private static double luminance(int stateId) {
        int rgb = BlockColors.get(stateId);
        return Lab.fromRgb(rgb < 0 ? 0x808080 : rgb)[0];
    }

    /** La normal unitaria media de las caras expuestas, o null si no hay ninguna. */
    private static double[] exposedNormal(World w, BlockPos.MutableBlockPos q, int x, int y, int z) {
        double nx = 0, ny = 0, nz = 0;
        int exposed = 0;
        for (int[] d : DIRS) {
            if (!opaque(w, q, x + d[0], y + d[1], z + d[2])) {
                nx += d[0];
                ny += d[1];
                nz += d[2];
                exposed++;
            }
        }
        if (exposed == 0) {
            return null;
        }
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9) {
            // caras opuestas expuestas (una columna fina): sin normal clara, se toma la luz como llega
            return new double[] {0, 1, 0};
        }
        return new double[] {nx / len, ny / len, nz / len};
    }

    /** Luz directa recibida (sol y luces puntuales); puede pasar de 1, quien llama la acota. */
    private static double direct(World w, BlockPos.MutableBlockPos q, int[] c, double[] nrm, Params p) {
        double cx = c[0] + 0.5, cy = c[1] + 0.5, cz = c[2] + 0.5;
        double ox = cx + nrm[0] * 0.9, oy = cy + nrm[1] * 0.9, oz = cz + nrm[2] * 0.9;
        double total = 0;
        if (p.sunOn) {
            double lambert = Math.max(0, nrm[0] * p.sun[0] + nrm[1] * p.sun[1] + nrm[2] * p.sun[2]);
            if (lambert > 0 && !shadowed(w, q, ox, oy, oz, p.sun[0], p.sun[1], p.sun[2], 64)) {
                total += lambert;
            }
        }
        for (double[] l : p.lights) {
            double lx = l[0] - cx, ly = l[1] - cy, lz = l[2] - cz;
            double dist = Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (dist < 1e-6 || dist >= l[4]) {
                continue;
            }
            lx /= dist;
            ly /= dist;
            lz /= dist;
            double lambert = Math.max(0, nrm[0] * lx + nrm[1] * ly + nrm[2] * lz);
            if (lambert > 0 && !shadowed(w, q, ox, oy, oz, lx, ly, lz, dist)) {
                total += l[3] * (1 - dist / l[4]) * lambert;
            }
        }
        return total;
    }

    private static double occlusion(World w, BlockPos.MutableBlockPos q, int x, int y, int z) {
        int solid = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx != 0 || dy != 0 || dz != 0) && opaque(w, q, x + dx, y + dy, z + dz)) {
                        solid++;
                    }
                }
            }
        }
        return Math.max(0, Math.min(1, (solid / 26.0 - FLAT) / (1 - FLAT)));
    }

    private static boolean shadowed(World w, BlockPos.MutableBlockPos q, double ox, double oy, double oz, double lx, double ly,
            double lz, double reach) {
        for (double t = 0; t < reach; t += 0.5) {
            if (opaque(w, q, (int) Math.floor(ox + lx * t), (int) Math.floor(oy + ly * t), (int) Math.floor(oz + lz * t))) {
                return true;
            }
        }
        return false;
    }

    private static IBlockState state(World w, BlockPos.MutableBlockPos q, int x, int y, int z) {
        if (y < 0 || y > 255) {
            return null;
        }
        q.setPos(x, y, z);
        return w.isBlockLoaded(q) ? w.getBlockState(q) : null;
    }

    private static boolean opaque(World w, BlockPos.MutableBlockPos q, int x, int y, int z) {
        IBlockState s = state(w, q, x, y, z);
        return s != null && s.isOpaqueCube();
    }
}
