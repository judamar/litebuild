package dev.litebuild;

import org.lwjgl.opengl.GL11;

import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;

/**
 * Reference Image: una imagen (URL o archivo) en una ventana del editor y, con Show In Game UI, también sobre el juego en el
 * mismo sitio y con el mismo tamaño y opacidad cuando el editor está cerrado.
 */
public final class ReferenceImage {
    public static final ImString source = new ImString("", 512);
    public static final float[] opacity = {1f};
    public static final ImBoolean nearest = new ImBoolean(false);
    public static final ImBoolean borderless = new ImBoolean(false);
    public static final ImBoolean showInGame = new ImBoolean(false);
    public static ImageCache.Entry entry;
    /** El rectángulo de la imagen en píxeles de ventana (esquina superior izquierda y tamaño); lo fija el editor cada frame. */
    public static float x, y, w, h;

    private ReferenceImage() {
    }

    public static void load() {
        String s = source.get().trim();
        if (!s.isEmpty()) {
            ImageCache.forget(s);
            entry = ImageCache.get(s);
        }
    }

    /** Dibuja la imagen sobre el juego (con el editor cerrado) si Show In Game UI está activado. sw, sh = tamaño de la pantalla escalada. */
    public static void drawHud(Minecraft mc, int sw, int sh) {
        if (!showInGame.get() || entry == null || entry.loading || w <= 0 || mc.currentScreen != null) {
            return;
        }
        int tex = ImageCache.texture(entry);
        if (tex == 0) {
            return;
        }
        float k = mc.displayWidth / (float) sw;
        float x1 = x / k, y1 = y / k, x2 = (x + w) / k, y2 = (y + h) / k;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glColor4f(1f, 1f, 1f, opacity[0]);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x1, y2);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x2, y2);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x2, y1);
        GL11.glEnd();
        GL11.glPopAttrib();
    }
}
