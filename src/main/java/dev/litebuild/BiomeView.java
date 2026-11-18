package dev.litebuild;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Show Biomes / Visualize Biome: pinta cada columna de alrededor de la cámara con el color de su bioma, sobre la
 * superficie. Es el bioma real del chunk, sin el degradado de color que aplica el juego a la hierba y el agua.
 */
public final class BiomeView {
    public static final ImBoolean show = new ImBoolean(false);
    /** Lo activa el editor mientras Biome Painter está seleccionado y Visualize Biome marcado. */
    public static boolean visualize;
    private static final int RADIUS = 40;
    private static final int[] COLORS = new int[256];

    private BiomeView() {
    }

    public static boolean active() {
        return show.get() || visualize;
    }

    /** Color 0xRRGGBB estable para un id de bioma (los tonos se reparten con la razón áurea). */
    public static int color(int biomeId) {
        int id = biomeId & 255;
        if (COLORS[id] == 0) {
            float hue = (id * 0.61803399f) % 1f;
            COLORS[id] = Color.HSBtoRGB(hue, 0.65f, 0.95f) & 0xFFFFFF | 0x1000000;
        }
        return COLORS[id] & 0xFFFFFF;
    }

    /** Con las matrices del mundo activas y el origen en la cámara ya aplicado (WorldOverlay). */
    public static void render() {
        World w = Minecraft.getMinecraft().world;
        if (w == null) {
            return;
        }
        int cx = (int) Math.floor(Targeting.camX), cz = (int) Math.floor(Targeting.camZ);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glBegin(GL11.GL_QUADS);
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                p.setPos(x, 64, z);
                if (!w.isBlockLoaded(p)) {
                    continue;
                }
                int y = w.getHeight(x, z);
                Biome b = w.getBiome(p);
                int rgb = color(Biome.getIdForBiome(b));
                GL11.glColor4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 0.45f);
                double h = y + 0.03;
                GL11.glVertex3d(x, h, z);
                GL11.glVertex3d(x, h, z + 1);
                GL11.glVertex3d(x + 1, h, z + 1);
                GL11.glVertex3d(x + 1, h, z);
            }
        }
        GL11.glEnd();
    }
}
