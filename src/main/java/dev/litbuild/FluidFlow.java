package dev.litbuild;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flujo de un fluido a partir de unas fuentes, calculado de antemano con las reglas de Minecraft 1.12: cae mientras
 * debajo no haya nada sólido (nivel 8 = cayendo) y, apoyado en suelo, se extiende en horizontal bajando un nivel por
 * bloque (2 niveles por bloque la lava; el máximo es 7). Sin dependencias de Minecraft.
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
    private static final int MAX_FALL = 64;

    private FluidFlow() {
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * Calcula y entrega con out cada bloque nuevo de fluido (sin las fuentes). sources = {x, y, z}; length = cuántos bloques
     * se extiende en horizontal (0 = nada); step = niveles que baja por bloque (1 agua, 2 lava); needSupport = no cae y
     * solo se extiende sobre suelo.
     */
    public static void flow(Probe probe, List<int[]> sources, int length, int step, boolean needSupport, Out out) {
        if (length <= 0 || sources.isEmpty()) {
            return;
        }
        int maxLevel = Math.min(7, length * step);
        Map<Long, Integer> level = new HashMap<Long, Integer>();
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        for (int[] s : sources) {
            level.put(key(s[0], s[1], s[2]), 0);
            queue.add(new int[] {s[0], s[1], s[2]});
        }
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && level.size() < MAX_CELLS) {
            int[] c = queue.poll();
            int l = level.get(key(c[0], c[1], c[2]));
            int depth = l >= 8 ? 0 : l;
            boolean below = !probe.solid(c[0], c[1] - 1, c[2]) && c[1] > 0;
            if (below && !needSupport) {
                int y = c[1] - 1;
                if (!level.containsKey(key(c[0], y, c[2]))) {
                    level.put(key(c[0], y, c[2]), 8);
                    queue.add(new int[] {c[0], y, c[2]});
                }
                continue; // un bloque sin suelo cae en vez de extenderse
            }
            if (below) {
                continue;
            }
            int next = depth + step;
            if (next > maxLevel) {
                continue;
            }
            for (int[] d : side) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                if (probe.solid(nx, c[1], nz)) {
                    continue;
                }
                if (needSupport && !probe.solid(nx, c[1] - 1, nz)) {
                    continue;
                }
                Integer old = level.get(key(nx, c[1], nz));
                if (old == null || (old != 0 && old != 8 && old > next)) {
                    level.put(key(nx, c[1], nz), next);
                    queue.add(new int[] {nx, c[1], nz});
                }
            }
        }
        for (Map.Entry<Long, Integer> e : level.entrySet()) {
            if (e.getValue() != 0) {
                long k = e.getKey();
                int x = (int) (k >> 38), z = (int) ((k >> 12) & 0x3FFFFFF), y = (int) (k & 0xFFF);
                if (x >= 0x2000000) {
                    x -= 0x4000000;
                }
                if (z >= 0x2000000) {
                    z -= 0x4000000;
                }
                out.set(x, y, z, e.getValue());
            }
        }
    }
}
