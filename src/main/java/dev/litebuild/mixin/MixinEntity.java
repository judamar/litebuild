package dev.litebuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.litebuild.NoClip;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;

/**
 * Los nombres son los de MCP; el procesador de anotaciones de Mixin genera el refmap que los traduce al juego real.
 */
@Mixin(Entity.class)
public abstract class MixinEntity {
    /** EntityPlayer.onUpdate reinicia noClip cada tick; aquí se vuelve a poner justo antes de mover (y de validar en el servidor). */
    @Inject(method = "move", at = @At("HEAD"))
    private void litebuild$noClipMove(MoverType type, double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (NoClip.applies(self)) {
            self.noClip = true;
        }
    }

    /** Sin esto atravesar un bloque haría daño por asfixia y taparía la pantalla con la textura del bloque. */
    @Inject(method = "isEntityInsideOpaqueBlock", at = @At("HEAD"), cancellable = true)
    private void litebuild$noSuffocate(CallbackInfoReturnable<Boolean> cir) {
        if (NoClip.applies((Entity) (Object) this)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
