package dev.litbuild;

import net.minecraft.block.state.IBlockState;

/** Elige el bloque que pinta un pincel en cada posición (ruido, degradado...); null = no tocar esa posición. */
public interface StatePicker {
    IBlockState pick(int x, int y, int z);
}
