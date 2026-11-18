package dev.litebuild;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/** Convierte un texto en bloques: lo dibuja con una fuente de Java sin suavizado y cada píxel pasa a ser un bloque. */
public final class TextTool {
    public static final String[] FONTS = {"SansSerif", "Serif", "Monospaced", "Dialog"};
    private static final int MAX_SIDE = 512;

    private TextTool() {
    }

    /**
     * Devuelve el texto como portapapeles: de pie (plano XY, legible desde el sur) o tumbado
     * (plano XZ, legible desde arriba). null si no hay nada que dibujar o es demasiado grande.
     */
    public static Clipboard render(String text, String fontName, int size, boolean bold, boolean italic,
            boolean flat, IBlockState state) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            System.setProperty("java.awt.headless", "true");
        } catch (SecurityException ignored) {
            // si no se permite, AWT sigue funcionando sin la propiedad
        }
        Font font = new Font(fontName, (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0), size);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g0 = probe.createGraphics();
        g0.setFont(font);
        FontMetrics fm = g0.getFontMetrics();
        String[] lines = text.split("\n");
        int w = 2;
        for (String l : lines) {
            w = Math.max(w, fm.stringWidth(l) + 2);
        }
        int h = lines.length * fm.getHeight() + 2;
        g0.dispose();
        if (w > MAX_SIDE || h > MAX_SIDE) {
            return null;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 1, 1 + fm.getAscent() + i * fm.getHeight());
        }
        g.dispose();

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) > 127) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        int sx = cw, sy = flat ? 1 : ch, sz = flat ? ch : 1;
        int[] states = new int[sx * sy * sz];
        java.util.Arrays.fill(states, Clipboard.AIR);
        int id = Block.getStateId(state);
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                if ((img.getRGB(minX + x, minY + y) >>> 24) > 127) {
                    // De pie, la primera fila de la imagen es la de arriba; tumbado, la de más al norte.
                    int by = flat ? 0 : ch - 1 - y;
                    int bz = flat ? y : 0;
                    states[(by * sz + bz) * sx + x] = id;
                }
            }
        }
        return new Clipboard(sx, sy, sz, states, new HashMap<Integer, NBTTagCompound>());
    }
}
