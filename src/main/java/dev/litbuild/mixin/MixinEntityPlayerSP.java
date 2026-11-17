package dev.litbuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.litbuild.NoClip;
import net.minecraft.client.entity.EntityPlayerSP;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {
    /** El jugador del cliente se empuja fuera de los bloques salvo que noClip ya esté activo, y aún no lo está. */
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void litbuild$noPush(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (NoClip.applies((EntityPlayerSP) (Object) this)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
