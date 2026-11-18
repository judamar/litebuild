package dev.litebuild;

import java.util.Random;

/** Ruido de Perlin 3D mejorado (con octavas) y ruido blanco determinista. */
public final class Noise {
    private final int[] p = new int[512];

    public Noise(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) {
            perm[i] = i;
        }
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            p[i] = perm[i & 255];
        }
    }

    /** Suma de octavas, normalizada a aproximadamente [-1, 1]. */
    public double fbm(double x, double y, double z, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < octaves; o++) {
            sum += amp * perlin(x * f, y * f, z * f);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    private double perlin(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int a = p[xi] + yi, aa = p[a] + zi, ab = p[a + 1] + zi;
        int b = p[xi + 1] + yi, ba = p[b] + zi, bb = p[b + 1] + zi;
        return lerp(w,
                lerp(v, lerp(u, grad(p[aa], x, y, z), grad(p[ba], x - 1, y, z)),
                        lerp(u, grad(p[ab], x, y - 1, z), grad(p[bb], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(p[aa + 1], x, y, z - 1), grad(p[ba + 1], x - 1, y, z - 1)),
                        lerp(u, grad(p[ab + 1], x, y - 1, z - 1), grad(p[bb + 1], x - 1, y - 1, z - 1))));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /**
     * Ruido celular 3D (Voronoi): distancia, en celdas, entre el punto de semilla más cercano y el segundo más
     * cercano. Vale casi 0 en las fronteras entre celdas, así que sirve para dibujar grietas.
     */
    public static double cellEdge(double x, double y, double z, long seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double f1 = Double.MAX_VALUE, f2 = Double.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = ix + dx, cy = iy + dy, cz = iz + dz;
                    double px = cx + white(cx, cy, cz, seed);
                    double py = cy + white(cx, cy, cz, seed + 1337);
                    double pz = cz + white(cx, cy, cz, seed + 7331);
                    double d = Math.sqrt((px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z));
                    if (d < f1) {
                        f2 = f1;
                        f1 = d;
                    } else if (d < f2) {
                        f2 = d;
                    }
                }
            }
        }
        return f2 - f1;
    }

    /** Valor uniforme en [0, 1) que depende solo de la posición y la semilla (splitmix64). */
    public static double white(int x, int y, int z, long seed) {
        long h = seed + x * 0x632BE59BD9B4E019L + y * 0x9E3779B97F4A7C15L + z * 0xD1B54A32D192ED03L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (1.0 / (1L << 53));
    }
}
