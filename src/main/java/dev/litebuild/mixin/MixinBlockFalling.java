package dev.litebuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.litebuild.Rules;
import net.minecraft.block.BlockFalling;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(BlockFalling.class)
public abstract class MixinBlockFalling {
    /** Arena y grava no caen mientras Block Gravity esté desactivado. */
    @Inject(method = "checkFallable", at = @At("HEAD"), cancellable = true)
    private void litebuild$noGravity(World world, BlockPos pos, CallbackInfo ci) {
        if (!Rules.blockGravity) {
            ci.cancel();
        }
    }
}
