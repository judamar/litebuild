package dev.litebuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.litebuild.BuilderMode;
import net.minecraft.client.Minecraft;

/** Deja que el Builder Mode se quede con los clics del ratón antes de que Minecraft rompa o coloque nada. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    private void litebuild$left(CallbackInfo ci) {
        if (BuilderMode.click(0)) {
            ci.cancel();
        }
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    private void litebuild$right(CallbackInfo ci) {
        if (BuilderMode.click(1)) {
            ci.cancel();
        }
    }

    @Inject(method = "middleClickMouse", at = @At("HEAD"), cancellable = true)
    private void litebuild$middle(CallbackInfo ci) {
        if (BuilderMode.click(2)) {
            ci.cancel();
        }
    }

    /** Mantener el botón izquierdo sigue rompiendo bloques en creativo: se corta mientras una herramienta o Bulldozer mandan. */
    @Inject(method = "sendClickBlockToController", at = @At("HEAD"), cancellable = true)
    private void litebuild$hold(boolean leftClick, CallbackInfo ci) {
        if (BuilderMode.suppressHold()) {
            ci.cancel();
        }
    }
}
