package dev.litebuild;

/**
 * Miniatura isométrica de una caja de bloques, por trazado de rayos: cada píxel lanza un rayo paralelo desde
 * la esquina (+X, +Y, +Z) y se queda con el primer bloque que toca, sombreado según la cara (arriba, +X, +Z).
 * Sin dependencias de Minecraft: recibe colores 0xRRGGBB (-1 = aire) con el orden (y*sz + z)*sx + x.
 */
public final class Thumb {
    private static final double INV_SQRT3 = 1 / Math.sqrt(3), INV_SQRT2 = 1 / Math.sqrt(2), INV_SQRT6 = 1 / Math.sqrt(6);
    /** Brillo por eje de la cara que se ve: X, Y (arriba), Z. */
    private static final double[] SHADE = {0.78, 1.0, 0.58};

    private Thumb() {
    }

    /** Devuelve size*size píxeles ARGB (alfa 0 = fondo). */
    public static int[] render(int[] rgb, int sx, int sy, int sz, int size) {
        double[] d = {-INV_SQRT3, -INV_SQRT3, -INV_SQRT3};
        double[] right = {INV_SQRT2, 0, -INV_SQRT2};
        double[] up = {-INV_SQRT6, 2 * INV_SQRT6, -INV_SQRT6};
        double[] c = {sx / 2.0, sy / 2.0, sz / 2.0};
        double radius = 0.5 * Math.sqrt((double) sx * sx + (double) sy * sy + (double) sz * sz);
        double scale = size / (2 * radius * 1.06);
        int[] out = new int[size * size];
        final int ss = 2;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < ss; j++) {
                    for (int i = 0; i < ss; i++) {
                        double u = (px + (i + 0.5) / ss - size / 2.0) / scale;
                        double v = (size / 2.0 - py - (j + 0.5) / ss) / scale;
                        double[] o = new double[3];
                        for (int k = 0; k < 3; k++) {
                            o[k] = c[k] + right[k] * u + up[k] * v - d[k] * 2 * radius;
                        }
                        int color = cast(rgb, sx, sy, sz, o, d);
                        if (color >= 0) {
                            a++;
                            r += (color >> 16) & 255;
                            g += (color >> 8) & 255;
                            b += color & 255;
                        }
                    }
                }
                if (a > 0) {
                    int n = ss * ss;
                    out[py * size + px] = (a * 255 / n) << 24 | (r / a) << 16 | (g / a) << 8 | (b / a);
                }
            }
        }
        return out;
    }

    /** Color sombreado del primer bloque que atraviesa el rayo, o -1 si no toca ninguno. */
    private static int cast(int[] rgb, int sx, int sy, int sz, double[] o, double[] d) {
        int[] dim = {sx, sy, sz};
        double tmin = Double.NEGATIVE_INFINITY, tmax = Double.POSITIVE_INFINITY;
        int axis = 1;
        for (int k = 0; k < 3; k++) {
            double t1 = (0 - o[k]) / d[k], t2 = (dim[k] - o[k]) / d[k];
            double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
            if (lo > tmin) {
                tmin = lo;
                axis = k;
            }
            tmax = Math.min(tmax, hi);
        }
        if (tmin > tmax) {
            return -1;
        }
        int[] cell = new int[3];
        double[] tNext = new double[3];
        double[] tStep = new double[3];
        for (int k = 0; k < 3; k++) {
            double p = o[k] + d[k] * (tmin + 1e-6);
            cell[k] = Math.min(dim[k] - 1, Math.max(0, (int) Math.floor(p)));
            tStep[k] = Math.abs(1 / d[k]);
            // d es negativo en los tres ejes: el siguiente límite es la cara baja de la celda.
            tNext[k] = (cell[k] - o[k]) / d[k];
        }
        while (true) {
            int color = rgb[(cell[1] * sz + cell[2]) * sx + cell[0]];
            if (color >= 0) {
                double s = SHADE[axis];
                int r = (int) (((color >> 16) & 255) * s), g = (int) (((color >> 8) & 255) * s), b = (int) ((color & 255) * s);
                return r << 16 | g << 8 | b;
            }
            axis = tNext[0] <= tNext[1] && tNext[0] <= tNext[2] ? 0 : tNext[1] <= tNext[2] ? 1 : 2;
            cell[axis]--;
            if (cell[axis] < 0) {
                return -1;
            }
            tNext[axis] += tStep[axis];
        }
    }
}
