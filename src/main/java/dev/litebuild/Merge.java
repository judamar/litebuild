package dev.litebuild;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/**
 * Merge Blocks al pegar: dos bloques que se pueden fundir en uno lo hacen en vez de que gane el nuevo. Hoy: dos losas del
 * mismo material en mitades opuestas pasan a ser la losa doble, y las capas de nieve se suman (hasta 8).
 */
public final class Merge {
    private Merge() {
    }

    /** El estado fundido de old (lo que hay) y next (lo que se pega), o null si no se funden. */
    public static IBlockState merge(IBlockState old, IBlockState next) {
        if (old.getBlock() == Blocks.SNOW_LAYER && next.getBlock() == Blocks.SNOW_LAYER) {
            int layers = Math.min(8, old.getValue(BlockSnow.LAYERS) + next.getValue(BlockSnow.LAYERS));
            return layers == old.getValue(BlockSnow.LAYERS) ? null : old.withProperty(BlockSnow.LAYERS, layers);
        }
        if (old.getBlock() == next.getBlock() && old.getBlock() instanceof BlockSlab && !((BlockSlab) old.getBlock()).isDouble()) {
            BlockSlab slab = (BlockSlab) old.getBlock();
            if (old.getValue(BlockSlab.HALF) == next.getValue(BlockSlab.HALF)) {
                return null;
            }
            int variant = slab.getMetaFromState(old) & 7;
            if (variant != (slab.getMetaFromState(next) & 7)) {
                return null;
            }
            String name = String.valueOf(Block.REGISTRY.getNameForObject(slab)).replace("minecraft:", "");
            String doubleName = name.equals("purpur_slab") ? "purpur_double_slab" : "double_" + name;
            Block d = Block.getBlockFromName("minecraft:" + doubleName);
            if (d != null) {
                try {
                    return d.getStateFromMeta(variant);
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
