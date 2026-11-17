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

    private BlockColors() {
    }

    public static synchronized void ensureBuilt() {
        if (colors != null) {
            return;
        }
        int[] table = new int[1 << 16];
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
                    }
                } catch (RuntimeException ignored) {
                    // algunos bloques no admiten ciertas metas
                }
            }
        }
        colors = table;
    }

    private static int average(TextureAtlasSprite sprite) {
        if (sprite == null || sprite.getFrameCount() == 0) {
            return -1;
        }
        int[][] frame = sprite.getFrameTextureData(0);
        if (frame == null || frame.length == 0) {
            return -1;
        }
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int p : frame[0]) {
            if ((p >>> 24) > 127) {
                r += (p >> 16) & 255;
                g += (p >> 8) & 255;
                b += p & 255;
                n++;
            }
        }
        return n == 0 ? -1 : (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
    }

    /** Color 0xRRGGBB del estado, o -1 si no tiene (o la tabla aún no está construida). */
    public static int get(int stateId) {
        int[] t = colors;
        return t == null || stateId < 0 || stateId >= t.length ? -1 : t[stateId];
    }

    public static int size() {
        return 1 << 16;
    }
}
