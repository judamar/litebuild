package dev.litbuild;

import java.util.Arrays;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

/** Configuración del Noise Painter ya resuelta: elige un bloque para cada posición según el ruido. */
public final class NoiseSpec implements StatePicker {
    private final int[] stateIds;
    private final double[] cumulative;
    private final double scale;
    private final int octaves;
    private final boolean threeD;
    private final boolean white;
    private final long seed;
    private final Noise noise;
    /** Valores del ruido ordenados: sirven para convertir un valor en su rango (percentil). */
    private final double[] sorted;

    /** weights: peso relativo de cada bloque (>= 1). */
    public NoiseSpec(int[] stateIds, int[] weights, float scale, int octaves, boolean threeD, boolean white,
            int seed) {
        this.stateIds = stateIds;
        this.cumulative = new double[weights.length];
        double total = 0;
        for (int w : weights) {
            total += w;
        }
        double run = 0;
        for (int i = 0; i < weights.length; i++) {
            run += weights[i];
            cumulative[i] = run / total;
        }
        this.scale = Math.max(0.5, scale);
        this.octaves = octaves;
        this.threeD = threeD;
        this.white = white;
        this.seed = seed;
        this.noise = new Noise(seed);
        // El ruido de Perlin se concentra cerca de 0. Para que los pesos se cumplan como porcentajes
        // se mide su distribución real (no depende de la escala) y se usa el rango de cada valor.
        Random r = new Random(7);
        this.sorted = new double[4096];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = noise.fbm(r.nextDouble() * 256, r.nextDouble() * 256, r.nextDouble() * 256, octaves);
        }
        Arrays.sort(sorted);
    }

    @Override
    public IBlockState pick(int x, int y, int z) {
        double t;
        if (white) {
            t = Noise.white(x, y, z, seed);
        } else {
            // Desfases fraccionarios: el ruido de Perlin vale 0 justo en la rejilla entera.
            double n = noise.fbm((x + 0.5) / scale + 0.137, threeD ? (y + 0.5) / scale + 0.271 : 0.271,
                    (z + 0.5) / scale + 0.389, octaves);
            int idx = Arrays.binarySearch(sorted, n);
            if (idx < 0) {
                idx = -idx - 1;
            }
            t = (idx + 0.5) / sorted.length;
        }
        for (int i = 0; i < cumulative.length; i++) {
            if (t <= cumulative[i]) {
                return Block.getStateById(stateIds[i]);
            }
        }
        return Block.getStateById(stateIds[stateIds.length - 1]);
    }
}
