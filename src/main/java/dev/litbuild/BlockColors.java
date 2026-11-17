package dev.litbuild;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;

/**
 * Color medio de cada estado de bloque (media de los píxeles opacos de su textura), indexado por el id de
 * estado de Minecraft (id << 4 | meta). Hay que construirlo en el hilo del cliente, que es donde están las
 * texturas; después se puede leer desde cualquier hilo.
 */
public final class BlockColors {
    private static volatile int[] colors;
    /** true si la textura del estado es casi de un solo color (Same Texture del Colour Picker). */
    private static volatile boolean[] uniform;
    private static boolean lastUniform;

    private BlockColors() {
    }

    public static synchronized void ensureBuilt() {
        if (colors != null) {
            return;
        }
        int[] table = new int[1 << 16];
        boolean[] flat = new boolean[1 << 16];
        Arrays.fill(table, -1);
        BlockModelShapes shapes = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes();
        for (Block b : Block.REGISTRY) {
            if (b == Blocks.AIR) {
                continue;
            }
            for (int meta = 0; meta < 16; meta++) {
                try {
                    IBlockState s = b.getStateFromMeta(meta);
                    int id = Block.getStateId(s);
                    if (id >= 0 && id < table.length && table[id] == -1) {
                        table[id] = average(shapes.getTexture(s));
                        flat[id] = lastUniform;
                    }
                } catch (RuntimeException ignored) {
                    // algunos bloques no admiten ciertas metas
                }
            }
        }
        uniform = flat;
        colors = table;
    }

    private static int average(TextureAtlasSprite sprite) {
        lastUniform = false;
        if (sprite == null || sprite.getFrameCount() == 0) {
            return -1;
        }
        int[][] frame = sprite.getFrameTextureData(0);
        if (frame == null || frame.length == 0) {
            return -1;
        }
        long r = 0, g = 0, b = 0, sq = 0;
        int n = 0;
        for (int p : frame[0]) {
            if ((p >>> 24) > 127) {
                int pr = (p >> 16) & 255, pg = (p >> 8) & 255, pb = p & 255;
                r += pr;
                g += pg;
                b += pb;
                sq += (long) pr * pr + (long) pg * pg + (long) pb * pb;
                n++;
            }
        }
        if (n == 0) {
            lastUniform = false;
            return -1;
        }
        double mr = (double) r / n, mg = (double) g / n, mb = (double) b / n;
        double variance = (double) sq / n - (mr * mr + mg * mg + mb * mb);
        lastUniform = Math.sqrt(Math.max(0, variance) / 3) < 14;
        return (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
    }

    /** Color 0xRRGGBB del estado, o -1 si no tiene (o la tabla aún no está construida). */
    public static int get(int stateId) {
        int[] t = colors;
        return t == null || stateId < 0 || stateId >= t.length ? -1 : t[stateId];
    }

    /** ¿Es la textura del estado casi uniforme? false si aún no hay tabla. */
    public static boolean isUniform(int stateId) {
        boolean[] t = uniform;
        return t != null && stateId >= 0 && stateId < t.length && t[stateId];
    }

    public static int size() {
        return 1 << 16;
    }
}
