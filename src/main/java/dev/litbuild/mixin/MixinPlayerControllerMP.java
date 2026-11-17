package dev.litbuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.litbuild.BuilderMode;
import net.minecraft.client.multiplayer.PlayerControllerMP;

@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {
    /** Infinite Reach: el alcance con el que apunta el cliente. */
    @Inject(method = "getBlockReachDistance", at = @At("HEAD"), cancellable = true)
    private void litbuild$reach(CallbackInfoReturnable<Float> cir) {
        float reach = BuilderMode.reach();
        if (reach > 0) {
            cir.setReturnValue(reach);
        }
    }
}
