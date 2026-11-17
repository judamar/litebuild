package dev.litbuild;

import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Máscara de herramienta ya resuelta (inmutable): una lista de reglas que se tienen que cumplir
 * todas (AND), cada una con su NOT opcional. Se evalúa en el hilo del servidor.
 */
public final class MaskSpec {
    public static final int BLOCK = 0, ABOVE = 1, BELOW = 2, SURFACE = 3, SKY = 4, IN_SELECTION = 5, Y = 6,
            NEIGHBOUR = 7, ADJACENT = 8;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private final int[] type;
    private final boolean[] not;
    private final Block[] block;
    private final int[] meta;
    private final int[] y1;
    private final int[] y2;
    /** Selección en el momento de crear la máscara (para IN_SELECTION); box null si no había. */
    private final int[] box;
    private final Set<Long> selMask;

    public MaskSpec(int[] type, boolean[] not, Block[] block, int[] meta, int[] y1, int[] y2, int[] box,
            Set<Long> selMask) {
        this.type = type;
        this.not = not;
        this.block = block;
        this.meta = meta;
        this.y1 = y1;
        this.y2 = y2;
        this.box = box;
        this.selMask = selMask;
    }

    public boolean test(World w, int x, int y, int z) {
        for (int i = 0; i < type.length; i++) {
            if (eval(i, w, x, y, z) == not[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean eval(int i, World w, int x, int y, int z) {
        switch (type[i]) {
            case BLOCK:
                return is(i, w, x, y, z);
            case ABOVE:
                return is(i, w, x, y + 1, z);
            case BELOW:
                return is(i, w, x, y - 1, z);
            case SURFACE:
                for (int[] o : FACES) {
                    IBlockState s = state(w, x + o[0], y + o[1], z + o[2]);
                    if (s != null && !s.getMaterial().isSolid()) {
                        return true;
                    }
                }
                return false;
            case SKY:
                return y < 255 && w.canSeeSky(new BlockPos(x, y + 1, z));
            case IN_SELECTION:
                return box != null && x >= box[0] && y >= box[1] && z >= box[2] && x <= box[3] && y <= box[4]
                        && z <= box[5] && (selMask == null || selMask.contains(Selection.key(x, y, z)));
            case Y:
                return y >= y1[i] && y <= y2[i];
            case NEIGHBOUR:
                for (int[] o : FACES) {
                    if (is(i, w, x + o[0], y + o[1], z + o[2])) {
                        return true;
                    }
                }
                return false;
            case ADJACENT:
                for (int[] o : SIDES) {
                    if (is(i, w, x + o[0], y, z + o[2])) {
                        return true;
                    }
                }
                return false;
            default:
                return true;
        }
    }

    private boolean is(int i, World w, int x, int y, int z) {
        IBlockState s = state(w, x, y, z);
        return s != null && s.getBlock() == block[i] && (meta[i] < 0 || block[i].getMetaFromState(s) == meta[i]);
    }

    private static IBlockState state(World w, int x, int y, int z) {
        if (y < 0 || y > 255) {
            return null;
        }
        BlockPos p = new BlockPos(x, y, z);
        return w.isBlockLoaded(p) ? w.getBlockState(p) : null;
    }
}
