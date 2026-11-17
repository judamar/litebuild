package dev.litbuild;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Magic Select: selecciona por relleno los bloques "iguales" conectados al que se pulsa. */
public final class MagicSelect {
    public static final String[] COMPARE = {"Block", "BlockState", "Solid", "Any"};
    public static final int BLOCK = 0, STATE = 1, SOLID = 2, ANY = 3;
    public static final String[] OPS = {"Replace", "Add", "Subtract", "Intersect"};

    public static final ImInt compare = new ImInt(BLOCK);
    public static final int[] limit = {100000};
    /** Hasta dónde busca el siguiente bloque vecino (salta huecos de aire). */
    public static final int[] range = {1};
    /** Con range 1, incluye también los vecinos en diagonal. */
    public static final ImBoolean corners = new ImBoolean(false);
    public static final ImBoolean surfaceOnly = new ImBoolean(false);
    public static final ImInt op = new ImInt(Selection.REPLACE);

    private MagicSelect() {
    }

    /** Selecciona a partir de start usando el mundo cargado en el cliente. */
    public static void select(BlockPos start) {
        World w = Minecraft.getMinecraft().world;
        IBlockState origin = w.getBlockState(start);
        int mode = compare.get();
        if (!matches(mode, origin, origin)) {
            EditEngine.status = "Magic Select: ese bloque no cuenta con la comparación " + COMPARE[mode];
            return;
        }
        int max = Math.max(1, Math.min(limit[0], (int) EditEngine.MAX_BLOCKS));
        int r = Math.max(1, range[0]);
        int[][] offsets = neighbours(r, corners.get() || r > 1);

        Set<Long> found = new HashSet<Long>();
        ArrayDeque<Long> queue = new ArrayDeque<Long>();
        long k0 = start.toLong();
        found.add(k0);
        queue.add(k0);
        while (!queue.isEmpty() && found.size() < max) {
            BlockPos p = BlockPos.fromLong(queue.poll());
            for (int[] o : offsets) {
                int y = p.getY() + o[1];
                if (y < 0 || y > 255) {
                    continue;
                }
                BlockPos q = new BlockPos(p.getX() + o[0], y, p.getZ() + o[2]);
                long k = q.toLong();
                if (found.contains(k) || !w.isBlockLoaded(q) || !matches(mode, origin, w.getBlockState(q))) {
                    continue;
                }
                found.add(k);
                queue.add(k);
                if (found.size() >= max) {
                    break;
                }
            }
        }
        if (surfaceOnly.get()) {
            found = surface(w, found);
        }
        if (!Selection.combine(found, op.get())) {
            EditEngine.status = "Magic Select: la selección actual es demasiado grande para combinarla";
            return;
        }
        EditEngine.status = "Magic Select: " + Selection.volume() + " bloques" + (found.size() >= max ? " (límite alcanzado)" : "");
    }

    static boolean matches(int mode, IBlockState origin, IBlockState c) {
        switch (mode) {
            case STATE:
                return c == origin;
            case SOLID:
                return c.getMaterial().isSolid();
            case ANY:
                return c.getBlock() != Blocks.AIR;
            default:
                return c.getBlock() == origin.getBlock();
        }
    }

    /** Desplazamientos vecinos: 6 caras, o todo el cubo de radio r (con diagonales). */
    private static int[][] neighbours(int r, boolean cube) {
        if (!cube) {
            return new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        }
        int side = 2 * r + 1;
        int[][] out = new int[side * side * side - 1][];
        int n = 0;
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        out[n++] = new int[] {dx, dy, dz};
                    }
                }
            }
        }
        return out;
    }

    /** Se queda con los bloques que tocan (por una cara) algo no sólido. */
    private static Set<Long> surface(World w, Set<Long> all) {
        Set<Long> out = new HashSet<Long>();
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (Long k : all) {
            BlockPos p = BlockPos.fromLong(k);
            for (int[] o : d) {
                BlockPos q = p.add(o[0], o[1], o[2]);
                if (w.isBlockLoaded(q) && !w.getBlockState(q).getMaterial().isSolid()) {
                    out.add(k);
                    break;
                }
            }
        }
        return out;
    }
}
