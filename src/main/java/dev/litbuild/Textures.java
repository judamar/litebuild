package dev.litbuild;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Texturas de OpenGL para imágenes propias (miniaturas, imágenes de anotaciones, imagen de referencia). Solo desde el hilo de render. */
public final class Textures {
    private Textures() {
    }

    /** Sube w x h píxeles ARGB y devuelve el id de la textura. */
    public static int upload(int[] argb, int w, int h, boolean nearest) {
        ByteBuffer buf = BufferUtils.createByteBuffer(argb.length * 4);
        for (int p : argb) {
            buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        }
        buf.flip();
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        filter(nearest);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        return id;
    }

    /** Cambia el filtro de la textura que está enlazada. */
    private static void filter(boolean nearest) {
        int f = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, f);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, f);
    }

    public static void setFilter(int texture, boolean nearest) {
        if (texture == 0) {
            return;
        }
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        filter(nearest);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
    }

    public static void free(int texture) {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
        }
    }
}
