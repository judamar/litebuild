package dev.litbuild;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;

/**
 * Bloques copiados: una caja de sx*sy*sz estados (índice = (y*sz + z)*sx + x) más el NBT de los
 * tile entities. Inmutable; rotar y voltear devuelven una copia nueva.
 */
public final class Clipboard {
    /** El portapapeles actual (se escribe desde el hilo del servidor). */
    public static volatile Clipboard current;

    public static final int AIR = Block.getStateId(Blocks.AIR.getDefaultState());

    public final int sx, sy, sz;
    public final int[] states;
    public final Map<Integer, NBTTagCompound> tiles;

    public Clipboard(int sx, int sy, int sz, int[] states, Map<Integer, NBTTagCompound> tiles) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.states = states;
        this.tiles = tiles;
    }

    public int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    /** Bloques que no son aire. */
    public int solidCount() {
        int n = 0;
        for (int s : states) {
            if (s != AIR) {
                n++;
            }
        }
        return n;
    }

    /** Giro de 90° en sentido horario visto desde arriba; las propiedades de los bloques giran con ellos. */
    public Clipboard rotated() {
        int nsx = sz;
        int nsz = sx;
        int[] out = new int[states.length];
        Map<Integer, NBTTagCompound> t = new HashMap<Integer, NBTTagCompound>();
        Map<Integer, Integer> cache = new HashMap<Integer, Integer>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = index(x, y, z);
                    int ni = (y * nsz + x) * nsx + (sz - 1 - z);
                    out[ni] = map(cache, states[i], true, false);
                    NBTTagCompound tag = tiles.get(i);
                    if (tag != null) {
                        t.put(ni, tag);
                    }
                }
            }
        }
        return new Clipboard(nsx, sy, nsz, out, t);
    }

    /**
     * Giro libre (en grados) y escala por eje, con muestreo del vecino más cercano. Se escala primero, luego se
     * gira en Z (roll), luego en Y (yaw, positivo = sentido horario visto desde arriba) y por último en X (pitch).
     * Los bloques con orientación solo giran en cuartos de vuelta según el yaw. Devuelve null si el resultado
     * pasa del tope de bloques.
     */
    public Clipboard transformed(double yaw, double pitch, double roll, double kx, double ky, double kz) {
        double hx = sx / 2.0, hy = sy / 2.0, hz = sz / 2.0;
        // Caja que contiene la forma transformada: el centro queda en el origen.
        double ex = 0, ey = 0, ez = 0;
        for (int c = 0; c < 8; c++) {
            double[] p = forward(((c & 1) == 0 ? -hx : hx) * kx, ((c & 2) == 0 ? -hy : hy) * ky,
                    ((c & 4) == 0 ? -hz : hz) * kz, yaw, pitch, roll);
            ex = Math.max(ex, Math.abs(p[0]));
            ey = Math.max(ey, Math.abs(p[1]));
            ez = Math.max(ez, Math.abs(p[2]));
        }
        int nx = Math.max(1, (int) Math.ceil(2 * ex - 1e-6));
        int ny = Math.max(1, (int) Math.ceil(2 * ey - 1e-6));
        int nz = Math.max(1, (int) Math.ceil(2 * ez - 1e-6));
        if ((long) nx * ny * nz > EditEngine.MAX_BLOCKS) {
            return null;
        }
        int quarter = Math.floorMod((int) Math.round(yaw / 90.0), 4);
        Map<Integer, Integer> cache = new HashMap<Integer, Integer>();
        int[] out = new int[nx * ny * nz];
        java.util.Arrays.fill(out, AIR);
        Map<Integer, NBTTagCompound> t = new HashMap<Integer, NBTTagCompound>();
        for (int j = 0; j < ny; j++) {
            for (int k = 0; k < nz; k++) {
                for (int i = 0; i < nx; i++) {
                    double[] s = inverse(i + 0.5 - nx / 2.0, j + 0.5 - ny / 2.0, k + 0.5 - nz / 2.0, yaw, pitch, roll);
                    int ix = (int) Math.floor(s[0] / kx + hx);
                    int iy = (int) Math.floor(s[1] / ky + hy);
                    int iz = (int) Math.floor(s[2] / kz + hz);
                    if (ix < 0 || iy < 0 || iz < 0 || ix >= sx || iy >= sy || iz >= sz) {
                        continue;
                    }
                    int from = index(ix, iy, iz);
                    int to = (j * nz + k) * nx + i;
                    int id = states[from];
                    for (int q = 0; q < quarter; q++) {
                        id = map(cache, id, true, false);
                    }
                    out[to] = id;
                    NBTTagCompound tag = tiles.get(from);
                    if (tag != null) {
                        t.put(to, tag);
                    }
                }
            }
        }
        return new Clipboard(nx, ny, nz, out, t);
    }

    /** Cada bloque pasa a ser un cubo de f x f x f (Scale2x, Scale3x). null si pasa del tope de bloques. */
    public Clipboard scaled(int f) {
        if ((long) sx * f * sy * f * sz * f > EditEngine.MAX_BLOCKS) {
            return null;
        }
        int nx = sx * f, ny = sy * f, nz = sz * f;
        int[] out = new int[nx * ny * nz];
        Map<Integer, NBTTagCompound> t = new HashMap<Integer, NBTTagCompound>();
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    out[(y * nz + z) * nx + x] = states[index(x / f, y / f, z / f)];
                }
            }
        }
        for (Map.Entry<Integer, NBTTagCompound> e : tiles.entrySet()) {
            int i = e.getKey();
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            t.put(((y * f) * nz + z * f) * nx + x * f, e.getValue());
        }
        return new Clipboard(nx, ny, nz, out, t);
    }

    /** Ejes: primero roll (Z), luego yaw (Y, horario visto desde arriba), luego pitch (X). */
    private static double[] forward(double x, double y, double z, double yaw, double pitch, double roll) {
        double rz = Math.toRadians(roll), ry = Math.toRadians(yaw), rx = Math.toRadians(pitch);
        double x1 = x * Math.cos(rz) - y * Math.sin(rz);
        double y1 = x * Math.sin(rz) + y * Math.cos(rz);
        double x2 = x1 * Math.cos(ry) - z * Math.sin(ry);
        double z2 = x1 * Math.sin(ry) + z * Math.cos(ry);
        double y3 = y1 * Math.cos(rx) - z2 * Math.sin(rx);
        double z3 = y1 * Math.sin(rx) + z2 * Math.cos(rx);
        return new double[] {x2, y3, z3};
    }

    /** La transformación inversa de {@link #forward} (sin la escala). */
    private static double[] inverse(double x, double y, double z, double yaw, double pitch, double roll) {
        double rz = Math.toRadians(-roll), ry = Math.toRadians(-yaw), rx = Math.toRadians(-pitch);
        double y1 = y * Math.cos(rx) - z * Math.sin(rx);
        double z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x2 = x * Math.cos(ry) - z1 * Math.sin(ry);
        double z2 = x * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
        double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
        return new double[] {x3, y3, z2};
    }

    /** Espejo a lo largo del eje X (este-oeste) o del eje Z (norte-sur). */
    public Clipboard mirrored(boolean alongX) {
        int[] out = new int[states.length];
        Map<Integer, NBTTagCompound> t = new HashMap<Integer, NBTTagCompound>();
        Map<Integer, Integer> cache = new HashMap<Integer, Integer>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = index(x, y, z);
                    int ni = alongX ? index(sx - 1 - x, y, z) : index(x, y, sz - 1 - z);
                    out[ni] = map(cache, states[i], false, alongX);
                    NBTTagCompound tag = tiles.get(i);
                    if (tag != null) {
                        t.put(ni, tag);
                    }
                }
            }
        }
        return new Clipboard(sx, sy, sz, out, t);
    }

    private static int map(Map<Integer, Integer> cache, int id, boolean rotate, boolean alongX) {
        Integer hit = cache.get(id);
        if (hit != null) {
            return hit;
        }
        IBlockState s = Block.getStateById(id);
        IBlockState r = rotate ? s.withRotation(Rotation.CLOCKWISE_90)
                : s.withMirror(alongX ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT);
        int result = Block.getStateId(r);
        cache.put(id, result);
        return result;
    }
}
