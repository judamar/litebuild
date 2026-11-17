package dev.litbuild;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * No Clip: el jugador atraviesa bloques. Los mixins de {@code dev.litbuild.mixin} consultan {@link #applies} en
 * Entity.move (cliente y servidor integrado, que valida la posición) y en pushOutOfBlocks / isEntityInsideOpaqueBlock.
 * Hace falta poder volar (creativo): sin vuelo la gravedad te hace caer a través del suelo.
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
        hits++;
        return true;
    }

    /** Cada tick del cliente: con No Clip se fuerza el vuelo (si el modo lo permite). */
    public static void tick(Minecraft mc) {
        EntityPlayerSP p = mc.player;
        if (enabled && p != null && p.capabilities.allowFlying && !p.capabilities.isFlying) {
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
        }
    }

    public static void toggle() {
        enabled = !enabled;
        hits = 0;
        EditEngine.status = "No Clip " + (enabled ? "activado" : "desactivado");
    }
}
