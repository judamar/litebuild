package dev.litbuild.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.litbuild.BuilderMode;
import net.minecraft.entity.player.InventoryPlayer;

@Mixin(InventoryPlayer.class)
public abstract class MixinInventoryPlayer {
    /** La rueda del ratón empuja la selección del Builder Mode (o cambia de herramienta con Alt) en vez de cambiar de ranura. */
    @Inject(method = "changeCurrentItem", at = @At("HEAD"), cancellable = true)
    private void litbuild$wheel(int direction, CallbackInfo ci) {
        if (BuilderMode.wheel(direction)) {
            ci.cancel();
        }
    }
}
