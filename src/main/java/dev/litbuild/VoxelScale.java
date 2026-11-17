package dev.litbuild;

import java.util.HashMap;
import java.util.Map;

/**
 * Escalado de voxels con detección de bordes: la versión en 3D de Scale2x/Scale3x de los gráficos pixelados. Cada celda se
 * divide en f x f x f y cada subcelda de una esquina toma el valor de sus vecinos si dos de los tres que la tocan
 * coinciden entre sí y no hay una pared recta (el vecino del lado contrario es distinto). Así las escaleras se alisan en
 * vez de repetir el bloque. Los índices son los del {@link Clipboard}: (y * sz + z) * sx + x. Sin dependencias de Minecraft.
 */
public final class VoxelScale {
    private VoxelScale() {
    }

    /** Scale2x (f = 2) o Scale3x (f = 3) con bordes. Fuera del cuadro cuenta el propio valor de la celda. */
    public static int[] smooth(int[] s, int sx, int sy, int sz, int f) {
        int nx = sx * f, ny = sy * f, nz = sz * f;
        int[] out = new int[nx * ny * nz];
        int[] n = new int[6]; // vecinos +x, -x, +y, -y, +z, -z
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int e = s[(y * sz + z) * sx + x];
                    n[0] = at(s, sx, sy, sz, x + 1, y, z, e);
                    n[1] = at(s, sx, sy, sz, x - 1, y, z, e);
                    n[2] = at(s, sx, sy, sz, x, y + 1, z, e);
                    n[3] = at(s, sx, sy, sz, x, y - 1, z, e);
                    n[4] = at(s, sx, sy, sz, x, y, z + 1, e);
                    n[5] = at(s, sx, sy, sz, x, y, z - 1, e);
                    for (int j = 0; j < f; j++) {
                        for (int k = 0; k < f; k++) {
                            for (int i = 0; i < f; i++) {
                                int[] d = {dir(i, f), dir(j, f), dir(k, f)};
                                int nonZero = (d[0] != 0 ? 1 : 0) + (d[1] != 0 ? 1 : 0) + (d[2] != 0 ? 1 : 0);
                                int v = e;
                                if (nonZero >= 2) {
                                    v = corner(n, d, e);
                                }
                                out[((y * f + j) * nz + (z * f + k)) * nx + x * f + i] = v;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** -1, 0 o +1 según el lado de la celda al que cae la subcelda i (el medio de f = 3 es 0). */
    private static int dir(int i, int f) {
        if (f % 2 == 1 && i == f / 2) {
            return 0;
        }
        return i < f / 2.0 ? -1 : 1;
    }

    private static int at(int[] s, int sx, int sy, int sz, int x, int y, int z, int fallback) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return fallback;
        }
        return s[(y * sz + z) * sx + x];
    }

    /**
     * El valor de una subcelda de arista o de esquina. d = hacia dónde cae en cada eje (0 = en medio); vale lo mismo
     * que dos vecinos de sus ejes si coinciden entre sí, son distintos de la celda y ninguno tiene al lado contrario el
     * mismo valor (sería una pared recta, no una esquina).
     */
    private static int corner(int[] n, int[] d, int e) {
        int[] near = new int[3], far = new int[3];
        for (int a = 0; a < 3; a++) {
            if (d[a] == 0) {
                near[a] = far[a] = Integer.MIN_VALUE;
            } else {
                near[a] = n[a * 2 + (d[a] > 0 ? 0 : 1)];
                far[a] = n[a * 2 + (d[a] > 0 ? 1 : 0)];
            }
        }
        for (int a = 0; a < 3; a++) {
            for (int b = a + 1; b < 3; b++) {
                if (d[a] == 0 || d[b] == 0) {
                    continue;
                }
                if (near[a] == near[b] && near[a] != e && near[a] != far[a] && near[b] != far[b]) {
                    return near[a];
                }
            }
        }
        return e;
    }

    /**
     * Reduce por un factor f: cada celda de salida es la más frecuente de sus f x f x f celdas (el aire solo gana si
     * es más de la mitad). dims recibe el tamaño de salida: lo que sobra por no ser múltiplo de f cuenta como aire.
     */
    public static int[] downscale(int[] s, int sx, int sy, int sz, int f, int air, int[] dims) {
        int nx = (sx + f - 1) / f, ny = (sy + f - 1) / f, nz = (sz + f - 1) / f;
        dims[0] = nx;
        dims[1] = ny;
        dims[2] = nz;
        int[] out = new int[nx * ny * nz];
        int total = f * f * f;
        Map<Integer, Integer> count = new HashMap<Integer, Integer>();
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    count.clear();
                    int empty = 0, best = air, bestCount = 0;
                    for (int j = 0; j < f; j++) {
                        for (int k = 0; k < f; k++) {
                            for (int i = 0; i < f; i++) {
                                int xx = x * f + i, yy = y * f + j, zz = z * f + k;
                                int v = xx < sx && yy < sy && zz < sz ? s[(yy * sz + zz) * sx + xx] : air;
                                if (v == air) {
                                    empty++;
                                    continue;
                                }
                                Integer c = count.get(v);
                                int nc = c == null ? 1 : c + 1;
                                count.put(v, nc);
                                if (nc > bestCount) {
                                    bestCount = nc;
                                    best = v;
                                }
                            }
                        }
                    }
                    out[(y * nz + z) * nx + x] = empty * 2 > total ? air : best;
                }
            }
        }
        return out;
    }
}
