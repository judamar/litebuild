package dev.litbuild;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
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

    /** Cuántos bloques se extiende el fluido en horizontal desde las fuentes (0 = solo las fuentes). */
    public static final int[] flowLength = {4};
    /** Solo se extiende sobre suelo y no cae en cascadas. */
    public static final imgui.type.ImBoolean requireSupport = new imgui.type.ImBoolean(false);
    /** Quita el fluido que fluye (sin fuente) de alrededor antes de colocar el nuevo. */
    public static final imgui.type.ImBoolean removeFluids = new imgui.type.ImBoolean(false);
    /** Rellena esquinas de la orilla y deja planos los recipientes cerrados que contienen una fuente. */
    public static final imgui.type.ImBoolean fillEdges = new imgui.type.ImBoolean(false);
    /** 1 = solo cuatro direcciones (vanilla), 2 = con diagonales, 3 = con saltos de caballo. */
    public static final int[] quality = {1};
    /** La región inicial es toda de fuentes; si no, una sola fuente en el centro. */
    public static final imgui.type.ImBoolean infiniteSources = new imgui.type.ImBoolean(true);

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
        final Block fluid = type == LAVA ? Blocks.FLOWING_LAVA : Blocks.FLOWING_WATER;
        IBlockState source = fluid.getDefaultState();
        final World world = w;
        final java.util.List<int[]> sources = new java.util.ArrayList<int[]>();
        final java.util.Map<Long, IBlockState> placed = new java.util.LinkedHashMap<Long, IBlockState>();
        if (infiniteSources.get()) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (dx * dx + dy * dy + dz * dz > lim) {
                            continue;
                        }
                        addSource(world, cx + dx, cy + dy, cz + dz, source, sources, placed);
                    }
                }
            }
        } else {
            addSource(world, cx, cy, cz, source, sources, placed);
        }
        final int length = flowLength[0];
        FluidFlow.flow(new FluidFlow.Probe() {
            @Override
            public boolean solid(int x, int y, int z) {
                if (y < 0 || y > 255) {
                    return true;
                }
                BlockPos p = new BlockPos(x, y, z);
                if (!world.isBlockLoaded(p)) {
                    return true;
                }
                IBlockState s = world.getBlockState(p);
                return s.getMaterial().isSolid() || s.getMaterial().isLiquid() && s.getValue(BlockLiquid.LEVEL) == 0;
            }
        }, sources, length, type == LAVA ? 2 : 1, requireSupport.get(), quality[0], fillEdges.get(),
                new FluidFlow.Out() {
            @Override
            public void set(int x, int y, int z, int level) {
                placed.put(new BlockPos(x, y, z).toLong(), fluid.getDefaultState().withProperty(BlockLiquid.LEVEL, level));
            }
        });
        if (removeFluids.get()) {
            int reach = r + length + 1;
            IBlockState air = Blocks.AIR.getDefaultState();
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        BlockPos p = new BlockPos(x, y, z);
                        if (y < 0 || y > 255 || !world.isBlockLoaded(p) || placed.containsKey(p.toLong())) {
                            continue;
                        }
                        IBlockState s = world.getBlockState(p);
                        if (s.getMaterial().isLiquid() && s.getValue(BlockLiquid.LEVEL) != 0) {
                            out.set(x, y, z, air);
                        }
                    }
                }
            }
        }
        for (java.util.Map.Entry<Long, IBlockState> e : placed.entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            out.set(p.getX(), p.getY(), p.getZ(), e.getValue());
        }
    }

    /** Una fuente nueva en (x, y, z) si ahí no hay nada sólido. */
    private static void addSource(World w, int x, int y, int z, IBlockState source, java.util.List<int[]> sources,
            java.util.Map<Long, IBlockState> placed) {
        BlockPos p = new BlockPos(x, y, z);
        if (y < 0 || y > 255 || !w.isBlockLoaded(p) || w.getBlockState(p).getMaterial().isSolid()) {
            return;
        }
        placed.put(p.toLong(), source);
        sources.add(new int[] {x, y, z});
    }
}
