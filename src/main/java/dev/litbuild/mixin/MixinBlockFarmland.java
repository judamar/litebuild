package dev.litbuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.litbuild.Rules;
import net.minecraft.block.BlockFarmland;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(BlockFarmland.class)
public abstract class MixinBlockFarmland {
    /** Sin pisoteo: se omite el cuerpo pero se conserva lo que hace Block.onFallenUpon (el daño por caída). */
    @Inject(method = "onFallenUpon", at = @At("HEAD"), cancellable = true)
    private void litbuild$noTrample(World world, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        if (!Rules.trampleFarmland) {
            entity.fall(fallDistance, 1.0F);
            ci.cancel();
        }
    }
}
