package dev.litebuild;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * No Clip: el jugador atraviesa bloques. Los mixins de {@code dev.litebuild.mixin} consultan {@link #applies} en
 * Entity.move (cliente y servidor integrado, que valida la posición) y en pushOutOfBlocks / isEntityInsideOpaqueBlock.
 * Solo actúa mientras vuelas (creativo): sin vuelo la gravedad te haría caer a través del suelo. Al activarlo se
 * empieza a volar, pero se puede dejar de volar (doble salto) para volver a chocar con los bloques.
 */
public final class NoClip {
    public static volatile boolean enabled;
    /** Cuántas veces un mixin ha actuado: 0 con No Clip activo y moviéndote = los mixins no se aplicaron. */
    public static volatile int hits;

    private NoClip() {
    }

    /** true si e es el jugador local (su copia del cliente o la del servidor integrado) y No Clip está activo. */
    public static boolean applies(Entity e) {
        if (!enabled || !(e instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayerSP me = Minecraft.getMinecraft().player;
        if (me == null || !me.getUniqueID().equals(e.getUniqueID())) {
            return false;
        }
        if (!me.capabilities.isFlying) {
            return false;
        }
        hits++;
        return true;
    }

    /** true si el jugador local está volando (No Clip solo actúa entonces). */
    public static boolean flying() {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        return p != null && p.capabilities.isFlying;
    }

    public static void toggle() {
        enabled = !enabled;
        hits = 0;
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (enabled && p != null && p.capabilities.allowFlying && !p.capabilities.isFlying) {
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
        }
        EditEngine.status = "No Clip " + (enabled ? "activado" : "desactivado");
    }
}
