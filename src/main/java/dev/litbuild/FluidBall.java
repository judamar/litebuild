package dev.litbuild;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Fluid Ball: una esfera de fuentes de agua o lava en el aire y los bloques no sólidos (el fluido fluye solo por
 * las reglas de Minecraft al colocarse), o nieve sobre la superficie, más gruesa en el centro.
 */
public final class FluidBall {
    public static final String[] TYPES = {"Water", "Lava", "Snow"};
    public static final int WATER = 0, LAVA = 1, SNOW = 2;

    private FluidBall() {
    }

    public static void apply(World w, int cx, int cy, int cz, int r, int type, Terrain.Setter out) {
        double lim = (r + 0.5) * (r + 0.5);
        if (type == SNOW) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 > lim) {
                        continue;
                    }
                    int x = cx + dx, z = cz + dz;
                    int y = Terrain.surfaceY(w, x, z, cy + r, cy - r);
                    if (y < 0 || y + 1 > 255) {
                        continue;
                    }
                    BlockPos p = new BlockPos(x, y + 1, z);
                    IBlockState above = w.isBlockLoaded(p) ? w.getBlockState(p) : null;
                    if (above == null || !(above.getBlock() == Blocks.AIR || above.getBlock() == Blocks.SNOW_LAYER
                            || above.getMaterial().isReplaceable() && !above.getMaterial().isLiquid())) {
                        continue;
                    }
                    int layers = Math.max(1, Math.min(8, (int) Math.round(1 + 7 * (1 - Math.sqrt(d2) / (r + 0.5)))));
                    out.set(x, y + 1, z, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, layers));
                }
            }
            return;
        }
        Block fluid = type == LAVA ? Blocks.FLOWING_LAVA : Blocks.FLOWING_WATER;
        IBlockState source = fluid.getDefaultState();
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy + dz * dz > lim) {
                        continue;
                    }
                    int y = cy + dy;
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    if (y < 0 || y > 255 || !w.isBlockLoaded(p)) {
                        continue;
                    }
                    IBlockState s = w.getBlockState(p);
                    if (!s.getMaterial().isSolid()) {
                        out.set(p.getX(), y, p.getZ(), source);
                    }
                }
            }
        }
    }
}
