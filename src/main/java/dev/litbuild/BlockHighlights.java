package dev.litbuild;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Edit Block Attributes: marca en el mundo los bloques que no se ven o no se distinguen (structure void y barreras). Se
 * explora un cubo alrededor del jugador cada pocos ticks y WorldOverlay dibuja los que encuentra.
 */
public final class BlockHighlights {
    private static final int RADIUS = 20;
    public static volatile List<BlockPos> voids = new ArrayList<BlockPos>();
    public static volatile List<BlockPos> barriers = new ArrayList<BlockPos>();
    private static int counter;

    private BlockHighlights() {
    }

    public static boolean any() {
        return BuilderMode.showStructureVoid.get() && !voids.isEmpty() || BuilderMode.showBarriers.get() && !barriers.isEmpty();
    }

    public static void update(Minecraft mc) {
        boolean sv = BuilderMode.showStructureVoid.get(), br = BuilderMode.showBarriers.get();
        if (!sv && !br) {
            voids = new ArrayList<BlockPos>();
            barriers = new ArrayList<BlockPos>();
            return;
        }
        if (++counter % 10 != 0) {
            return;
        }
        World w = mc.world;
        List<BlockPos> v = new ArrayList<BlockPos>(), b = new ArrayList<BlockPos>();
        BlockPos c = mc.player.getPosition();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = Math.max(0, c.getY() - RADIUS); y <= Math.min(255, c.getY() + RADIUS); y++) {
            for (int z = c.getZ() - RADIUS; z <= c.getZ() + RADIUS; z++) {
                for (int x = c.getX() - RADIUS; x <= c.getX() + RADIUS; x++) {
                    q.setPos(x, y, z);
                    if (!w.isBlockLoaded(q)) {
                        continue;
                    }
                    IBlockState s = w.getBlockState(q);
                    if (sv && s.getBlock() == Blocks.STRUCTURE_VOID) {
                        v.add(q.toImmutable());
                    } else if (br && s.getBlock() == Blocks.BARRIER) {
                        b.add(q.toImmutable());
                    }
                }
            }
        }
        voids = v;
        barriers = b;
    }
}
