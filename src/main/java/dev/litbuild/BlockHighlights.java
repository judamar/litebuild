package dev.litbuild;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBarrier;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Edit Block Attributes: marca en el mundo los bloques que no se ven o no se distinguen: structure void y la malla de
 * colisión (las cajas reales de barreras, vallas, puertas de valla, muros, paneles de cristal y barras de hierro). Se
 * explora un cubo alrededor del jugador cada pocos ticks y WorldOverlay dibuja los que encuentra.
 */
public final class BlockHighlights {
    private static final int RADIUS = 20;
    public static volatile List<BlockPos> voids = new ArrayList<BlockPos>();
    /** Cajas de colisión {minX, minY, minZ, maxX, maxY, maxZ} en coordenadas del mundo. */
    public static volatile List<double[]> collision = new ArrayList<double[]>();
    private static int counter;

    private BlockHighlights() {
    }

    public static boolean any() {
        return BuilderMode.showStructureVoid.get() && !voids.isEmpty() || BuilderMode.showCollision.get() && !collision.isEmpty();
    }

    public static void update(Minecraft mc) {
        boolean sv = BuilderMode.showStructureVoid.get(), br = BuilderMode.showCollision.get();
        if (!sv && !br) {
            voids = new ArrayList<BlockPos>();
            collision = new ArrayList<double[]>();
            return;
        }
        if (++counter % 10 != 0) {
            return;
        }
        World w = mc.world;
        List<BlockPos> v = new ArrayList<BlockPos>();
        List<double[]> b = new ArrayList<double[]>();
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
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
                    } else if (br && hasInvisibleHitbox(s.getBlock())) {
                        boxes.clear();
                        s.addCollisionBoxToList(w, q, new AxisAlignedBB(q), boxes, null, false);
                        for (AxisAlignedBB bb : boxes) {
                            b.add(new double[] {bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ});
                        }
                    }
                }
            }
        }
        voids = v;
        collision = b;
    }

    /** Bloques cuya hitbox no se distingue bien del aire o del modelo: barreras y los que se conectan por brazos finos. */
    private static boolean hasInvisibleHitbox(Block b) {
        return b == Blocks.BARRIER || b instanceof BlockBarrier || b instanceof BlockFence || b instanceof BlockFenceGate
                || b instanceof BlockWall || b instanceof BlockPane;
    }
}
