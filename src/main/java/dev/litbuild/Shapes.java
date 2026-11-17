package dev.litbuild;

import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/** Formas geométricas con el bloque activo, listas para pegar con un Placement. */
public final class Shapes {
    public static final String[] NAMES = {
        "Sphere", "Cuboid", "Cylinder", "Cone", "Pyramid", "Octahedron", "Disk"
    };
    public static final int SPHERE = 0, CUBOID = 1, CYLINDER = 2, CONE = 3, PYRAMID = 4, OCTAHEDRON = 5, DISK = 6;

    private Shapes() {
    }

    /** Genera la forma dentro de una caja sx*sy*sz; hollow deja solo la cáscara. */
    public static Clipboard create(int type, int sx, int sy, int sz, boolean hollow, IBlockState state) {
        if (type == DISK) {
            sy = 1;
        }
        boolean[] in = new boolean[sx * sy * sz];
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    in[(y * sz + z) * sx + x] = inside(type == DISK ? CYLINDER : type, x, y, z, sx, sy, sz);
                }
            }
        }
        int id = Block.getStateId(state);
        int[] states = new int[in.length];
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = (y * sz + z) * sx + x;
                    boolean keep = in[i];
                    if (keep && hollow) {
                        // Cáscara: se queda si algún vecino (o el exterior de la caja) no es de la forma.
                        keep = !at(in, x + 1, y, z, sx, sy, sz) || !at(in, x - 1, y, z, sx, sy, sz)
                                || !at(in, x, y + 1, z, sx, sy, sz) || !at(in, x, y - 1, z, sx, sy, sz)
                                || !at(in, x, y, z + 1, sx, sy, sz) || !at(in, x, y, z - 1, sx, sy, sz);
                    }
                    states[i] = keep ? id : Clipboard.AIR;
                }
            }
        }
        return new Clipboard(sx, sy, sz, states, new HashMap<Integer, NBTTagCompound>());
    }

    private static boolean at(boolean[] in, int x, int y, int z, int sx, int sy, int sz) {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz && in[(y * sz + z) * sx + x];
    }

    /** Centro de la celda normalizado a [-1, 1] en cada eje y prueba de pertenencia. */
    private static boolean inside(int type, int x, int y, int z, int sx, int sy, int sz) {
        double nx = (x + 0.5 - sx / 2.0) / (sx / 2.0);
        double ny = (y + 0.5 - sy / 2.0) / (sy / 2.0);
        double nz = (z + 0.5 - sz / 2.0) / (sz / 2.0);
        double shrink = 1.0 - (y + 0.5) / sy; // 1 en la base, 0 en la punta
        switch (type) {
            case CUBOID:
                return true;
            case CYLINDER:
                return nx * nx + nz * nz <= 1.0;
            case CONE:
                return nx * nx + nz * nz <= shrink * shrink;
            case PYRAMID:
                return Math.abs(nx) <= shrink && Math.abs(nz) <= shrink;
            case OCTAHEDRON:
                return Math.abs(nx) + Math.abs(ny) + Math.abs(nz) <= 1.0;
            default:
                return nx * nx + ny * ny + nz * nz <= 1.0;
        }
    }
}
