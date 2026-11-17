package dev.litbuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Flujo de un fluido a partir de unas fuentes, calculado de antemano con las reglas de Minecraft 1.12: cae mientras
 * debajo no haya nada sólido (nivel 8 = cayendo) y, apoyado en suelo, se extiende en horizontal bajando un nivel por
 * bloque (2 niveles por bloque la lava; el máximo es 7). Sin dependencias de Minecraft.
 *
 * <p>Quality 1 extiende solo en las cuatro direcciones (el rombo de vanilla); 2 añade las diagonales y 3 los saltos de
 * caballo, y el nivel sale de la distancia real recorrida, así que la mancha queda más redonda. Fill Edges rellena los
 * huecos sueltos de la orilla y deja planos los recipientes cerrados que contienen una fuente.
 */
public final class FluidFlow {
    public interface Probe {
        /** ¿Bloque sólido (o fuera del mundo / sin cargar) por el que el fluido no pasa? */
        boolean solid(int x, int y, int z);
    }

    public interface Out {
        void set(int x, int y, int z, int level);
    }

    private static final int MAX_CELLS = 30_000;
    private static final int[][] SIDE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] KNIGHT = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};

    private FluidFlow() {
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int kx(long k) {
        int x = (int) (k >> 38);
        return x >= 0x2000000 ? x - 0x4000000 : x;
    }

    private static int kz(long k) {
        int z = (int) ((k >> 12) & 0x3FFFFFF);
        return z >= 0x2000000 ? z - 0x4000000 : z;
    }

    private static int ky(long k) {
        return (int) (k & 0xFFF);
    }

    public static void flow(Probe probe, List<int[]> sources, int length, int step, boolean needSupport, Out out) {
        flow(probe, sources, length, step, needSupport, 1, false, out);
    }

    /**
     * Calcula y entrega con out cada bloque nuevo de fluido (sin las fuentes; una celda con nivel 0 es una fuente nueva).
     * sources = {x, y, z}; length = cuántos bloques se extiende en horizontal (0 = nada); step = niveles que baja por
     * bloque (1 agua, 2 lava); needSupport = no cae y solo se extiende sobre suelo; quality 1-3; fillEdges ver arriba.
     */
    public static void flow(Probe probe, List<int[]> sources, int length, int step, boolean needSupport, int quality,
            boolean fillEdges, Out out) {
        if (length <= 0 || sources.isEmpty()) {
            return;
        }
        int maxLevel = Math.min(7, length * step);
        Map<Long, Double> dist = new HashMap<Long, Double>();
        Set<Long> src = new HashSet<Long>();
        Set<Long> falling = new HashSet<Long>();
        PriorityQueue<double[]> queue = new PriorityQueue<double[]>(64, new java.util.Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });
        for (int[] s : sources) {
            long k = key(s[0], s[1], s[2]);
            dist.put(k, 0.0);
            src.add(k);
            queue.add(new double[] {0, s[0], s[1], s[2]});
        }
        List<int[]> moves = new ArrayList<int[]>();
        List<Double> costs = new ArrayList<Double>();
        for (int[] d : SIDE) {
            moves.add(d);
            costs.add(1.0);
        }
        if (quality >= 2) {
            for (int[] d : DIAG) {
                moves.add(d);
                costs.add(Math.sqrt(2));
            }
        }
        if (quality >= 3) {
            for (int[] d : KNIGHT) {
                moves.add(d);
                costs.add(Math.sqrt(5));
            }
        }
        while (!queue.isEmpty() && dist.size() < MAX_CELLS) {
            double[] t = queue.poll();
            int x = (int) t[1], y = (int) t[2], z = (int) t[3];
            double d = t[0];
            if (d > dist.get(key(x, y, z)) + 1e-9) {
                continue;
            }
            boolean below = y > 0 && !probe.solid(x, y - 1, z);
            if (below && !needSupport) {
                long k = key(x, y - 1, z);
                if (!dist.containsKey(k)) {
                    dist.put(k, 0.0);
                    falling.add(k);
                    queue.add(new double[] {0, x, y - 1, z});
                }
                continue; // un bloque sin suelo cae en vez de extenderse
            }
            if (below) {
                continue;
            }
            for (int m = 0; m < moves.size(); m++) {
                int dx = moves.get(m)[0], dz = moves.get(m)[1];
                double nd = d + costs.get(m);
                if ((int) Math.floor(nd * step + 0.5 + 1e-9) > maxLevel) {
                    continue;
                }
                int nx = x + dx, nz = z + dz;
                if (!passable(probe, x, y, z, dx, dz, needSupport)) {
                    continue;
                }
                long k = key(nx, y, nz);
                Double old = dist.get(k);
                if (src.contains(k) || falling.contains(k)) {
                    continue;
                }
                if (old == null || old > nd + 1e-9) {
                    dist.put(k, nd);
                    queue.add(new double[] {nd, nx, y, nz});
                }
            }
        }
        Map<Long, Integer> level = new HashMap<Long, Integer>();
        for (Map.Entry<Long, Double> e : dist.entrySet()) {
            long k = e.getKey();
            if (src.contains(k)) {
                continue;
            }
            level.put(k, falling.contains(k) ? 8 : Math.max(1, (int) Math.floor(e.getValue() * step + 0.5 + 1e-9)));
        }
        if (fillEdges) {
            fillEdges(probe, level, src, falling, step);
        }
        for (Map.Entry<Long, Integer> e : level.entrySet()) {
            long k = e.getKey();
            out.set(kx(k), ky(k), kz(k), e.getValue());
        }
    }

    /** ¿Puede el fluido ir de (x, y, z) a (x + dx, y, z + dz)? Las diagonales y los saltos pasan por celdas libres. */
    private static boolean passable(Probe p, int x, int y, int z, int dx, int dz, boolean needSupport) {
        int tx = x + dx, tz = z + dz;
        if (p.solid(tx, y, tz) || needSupport && !p.solid(tx, y - 1, tz)) {
            return false;
        }
        int ax = Math.abs(dx), az = Math.abs(dz);
        if (ax + az == 1) {
            return true;
        }
        if (ax == 1 && az == 1) {
            // Diagonal: basta que una de las dos celdas contiguas esté libre (hay un camino de cuatro direcciones).
            return free(p, x + dx, y, z, needSupport) || free(p, x, y, z + dz, needSupport);
        }
        // Salto de caballo (2, 1): por (1, 0) y (1, 1) hasta (2, 1).
        int sx = Integer.signum(dx), sz = Integer.signum(dz);
        if (ax == 2) {
            return free(p, x + sx, y, z, needSupport) && free(p, x + sx, y, z + sz, needSupport);
        }
        return free(p, x, y, z + sz, needSupport) && free(p, x + sx, y, z + sz, needSupport);
    }

    private static boolean free(Probe p, int x, int y, int z, boolean needSupport) {
        return !p.solid(x, y, z) && (!needSupport || p.solid(x, y - 1, z));
    }

    /**
     * Fill Edges: (1) rellena las celdas vacías de la orilla rodeadas de fluido y pared (hasta tres pasadas); (2) un
     * recipiente cerrado (suelo sólido bajo todas sus celdas y solo paredes alrededor) que contiene una fuente se
     * convierte entero en fuentes, o sea, queda plano.
     */
    private static void fillEdges(Probe probe, Map<Long, Integer> level, Set<Long> src, Set<Long> falling, int step) {
        for (int pass = 0; pass < 3; pass++) {
            Map<Long, Integer> add = new HashMap<Long, Integer>();
            Set<Long> seen = new HashSet<Long>();
            for (Map.Entry<Long, Integer> e : level.entrySet()) {
                if (falling.contains(e.getKey())) {
                    continue;
                }
                seen.add(e.getKey());
            }
            seen.addAll(src);
            for (long k : new ArrayList<Long>(seen)) {
                int x = kx(k), y = ky(k), z = kz(k);
                for (int[] d : SIDE) {
                    int nx = x + d[0], nz = z + d[1];
                    long nk = key(nx, y, nz);
                    if (seen.contains(nk) || add.containsKey(nk) || level.containsKey(nk) || falling.contains(nk)
                            || probe.solid(nx, y, nz) || !probe.solid(nx, y - 1, nz)) {
                        continue;
                    }
                    int fluid = 0, best = 99;
                    boolean enclosed = true;
                    for (int[] e : SIDE) {
                        long ek = key(nx + e[0], y, nz + e[1]);
                        if (seen.contains(ek)) {
                            fluid++;
                            best = Math.min(best, src.contains(ek) ? 0 : level.get(ek));
                        } else if (!probe.solid(nx + e[0], y, nz + e[1])) {
                            enclosed = false;
                        }
                    }
                    if (enclosed && fluid >= 2 && best + step <= 7) {
                        add.put(nk, best + step);
                    }
                }
            }
            if (add.isEmpty()) {
                break;
            }
            level.putAll(add);
        }
        // Recipientes cerrados con fuente: todo a fuentes (nivel 0).
        Set<Long> cells = new HashSet<Long>(src);
        for (long k : level.keySet()) {
            if (!falling.contains(k)) {
                cells.add(k);
            }
        }
        Set<Long> done = new HashSet<Long>();
        for (long start : cells) {
            if (done.contains(start)) {
                continue;
            }
            List<Long> region = new ArrayList<Long>();
            List<Long> stack = new ArrayList<Long>();
            stack.add(start);
            done.add(start);
            boolean contained = true, hasSource = false;
            while (!stack.isEmpty()) {
                long k = stack.remove(stack.size() - 1);
                region.add(k);
                hasSource |= src.contains(k);
                int x = kx(k), y = ky(k), z = kz(k);
                if (!probe.solid(x, y - 1, z)) {
                    contained = false;
                }
                for (int[] d : SIDE) {
                    long nk = key(x + d[0], y, z + d[1]);
                    if (cells.contains(nk)) {
                        if (done.add(nk)) {
                            stack.add(nk);
                        }
                    } else if (!probe.solid(x + d[0], y, z + d[1])) {
                        contained = false;
                    }
                }
            }
            if (contained && hasSource) {
                for (long k : region) {
                    if (!src.contains(k)) {
                        level.put(k, 0);
                    }
                }
            }
        }
    }
}
