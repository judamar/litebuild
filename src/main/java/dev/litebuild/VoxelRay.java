package dev.litebuild;

/**
 * Rayo por la cuadrícula de bloques (Amanatides-Woo): el primer bloque que cumple una condición, tratándolo como un
 * cubo completo. Sin dependencias de Minecraft.
 */
public final class VoxelRay {
    public interface Probe {
        /** 0 = sigue, 1 = este bloque es el impacto, 2 = para (fuera del mundo o sin cargar). */
        int at(int x, int y, int z);
    }

    private VoxelRay() {
    }

    /**
     * Sale de (ox, oy, oz) en la dirección (dx, dy, dz) (normalizada) hasta max bloques. Devuelve {x, y, z, cara, t} con la
     * cara por la que entra el rayo en el orden de EnumFacing (0 abajo, 1 arriba, 2 norte, 3 sur, 4 oeste, 5 este) y t la
     * distancia; null si no hay impacto. La celda de salida no cuenta.
     */
    public static double[] trace(Probe probe, double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double inf = Double.POSITIVE_INFINITY;
        double tx = dx == 0 ? inf : ((dx > 0 ? x + 1 - ox : ox - x) / Math.abs(dx));
        double ty = dy == 0 ? inf : ((dy > 0 ? y + 1 - oy : oy - y) / Math.abs(dy));
        double tz = dz == 0 ? inf : ((dz > 0 ? z + 1 - oz : oz - z) / Math.abs(dz));
        double ddx = dx == 0 ? inf : 1 / Math.abs(dx), ddy = dy == 0 ? inf : 1 / Math.abs(dy), ddz = dz == 0 ? inf : 1 / Math.abs(dz);
        for (int i = 0; i < 4096; i++) {
            double t;
            int face;
            if (tx <= ty && tx <= tz) {
                t = tx;
                tx += ddx;
                x += sx;
                face = sx > 0 ? 4 : 5;
            } else if (ty <= tz) {
                t = ty;
                ty += ddy;
                y += sy;
                face = sy > 0 ? 0 : 1;
            } else {
                t = tz;
                tz += ddz;
                z += sz;
                face = sz > 0 ? 2 : 3;
            }
            if (t > max) {
                return null;
            }
            int r = probe.at(x, y, z);
            if (r == 1) {
                return new double[] {x, y, z, face, t};
            }
            if (r == 2) {
                return null;
            }
        }
        return null;
    }
}
