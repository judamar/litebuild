package dev.litbuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.litbuild.BuilderMode;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;

@Mixin(BlockLiquid.class)
public abstract class MixinBlockLiquid {
    /** Make Fluid Hitboxes Solid: el agua y la lava pasan a ser apuntables (y se pueden romper) como cualquier bloque. */
    @Inject(method = "canCollideCheck", at = @At("HEAD"), cancellable = true)
    private void litbuild$solid(IBlockState state, boolean hitIfLiquid, CallbackInfoReturnable<Boolean> cir) {
        if (BuilderMode.fluidsSolid.get()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
