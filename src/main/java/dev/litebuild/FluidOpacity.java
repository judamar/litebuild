package dev.litebuild;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Fluid Opacity: cambia el alfa de las texturas del agua (quieta y en movimiento) en el atlas de bloques. Solo el
 * agua; la lava y los fluidos de otros mods no cambian. Se vuelve a aplicar si el atlas se recarga (por ejemplo
 * al cambiar de paquete de recursos). El alfa original se guarda para poder volver al de vanilla.
 */
public final class FluidOpacity {
    private static final String[] SPRITES = {"minecraft:blocks/water_still", "minecraft:blocks/water_flow"};
    /** Alfa original de cada sprite: [frame][mipmap][píxel]. */
    private static final Map<String, int[][][]> ORIGINAL = new HashMap<String, int[][][]>();
    private static float applied = -1f;
    private static final Map<String, Object> APPLIED_TO = new HashMap<String, Object>();

    private FluidOpacity() {
    }

    /** Llamar cada frame: aplica value (0-1) o, con value < 0, restaura el alfa de vanilla. */
    public static void tick(float value) {
        if (Minecraft.getMinecraft().getTextureMapBlocks() == null) {
            return;
        }
        boolean stale = false;
        for (String name : SPRITES) {
            TextureAtlasSprite s = sprite(name);
            if (s != null && s.getFrameCount() > 0) {
                Object id = s.getFrameTextureData(0);
                stale |= APPLIED_TO.get(name) != id;
            }
        }
        if (value == applied && !stale) {
            return;
        }
        if (value < 0 && applied < 0 && ORIGINAL.isEmpty()) {
            applied = value;
            return;
        }
        for (String name : SPRITES) {
            apply(name, value);
        }
        applied = value;
    }

    private static TextureAtlasSprite sprite(String name) {
        TextureAtlasSprite s = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
        return s == null || s.getIconName().equals("missingno") ? null : s;
    }

    private static void apply(String name, float value) {
        TextureAtlasSprite s = sprite(name);
        if (s == null) {
            return;
        }
        int frames = s.getFrameCount();
        int[][][] original = ORIGINAL.get(name);
        if (original == null) {
            if (value < 0) {
                return;
            }
            original = new int[frames][][];
            for (int f = 0; f < frames; f++) {
                int[][] data = s.getFrameTextureData(f);
                original[f] = new int[data.length][];
                for (int m = 0; m < data.length; m++) {
                    original[f][m] = data[m].clone();
                }
            }
            ORIGINAL.put(name, original);
        }
        int alpha = Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
        for (int f = 0; f < frames && f < original.length; f++) {
            int[][] data = s.getFrameTextureData(f);
            for (int m = 0; m < data.length && m < original[f].length; m++) {
                for (int i = 0; i < data[m].length; i++) {
                    int px = original[f][m][i];
                    // solo los píxeles que ya eran visibles; el alfa nuevo es absoluto
                    data[m][i] = value < 0 || (px >>> 24) == 0 ? px : (alpha << 24) | (px & 0xFFFFFF);
                }
            }
        }
        APPLIED_TO.put(name, s.getFrameTextureData(0));
    }
}
