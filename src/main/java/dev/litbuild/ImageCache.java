package dev.litbuild;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Imágenes por URL (http/https) o ruta de archivo, leídas en un hilo aparte y subidas a OpenGL cuando se piden
 * desde el hilo de render. Las usan las anotaciones de imagen y la ventana Reference Image. Una URL inválida o
 * que falla deja la entrada en error (se dibuja una textura rosa y negra).
 */
public final class ImageCache {
    public static final class Entry {
        public volatile int w, h;
        public volatile int[] pixels;
        public volatile boolean loading = true;
        public volatile String error;
        public int texture;
        boolean nearest;
    }

    private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();
    private static final int MAX_SIDE = 2048;

    private ImageCache() {
    }

    /** La entrada de la imagen (empieza a cargarla la primera vez). */
    public static synchronized Entry get(final String source) {
        Entry e = CACHE.get(source);
        if (e != null) {
            return e;
        }
        final Entry fresh = new Entry();
        CACHE.put(source, fresh);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                load(source, fresh);
            }
        }, "litbuild-image");
        t.setDaemon(true);
        t.start();
        return fresh;
    }

    /** Fuerza una nueva lectura (después de cambiar el archivo, por ejemplo). */
    public static synchronized void forget(String source) {
        CACHE.remove(source);
    }

    private static void load(String source, Entry e) {
        try {
            BufferedImage img;
            String s = source.trim();
            if (s.startsWith("http://") || s.startsWith("https://")) {
                URLConnection c = new URL(s).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                img = ImageIO.read(c.getInputStream());
            } else {
                img = ImageIO.read(new File(s.startsWith("file:") ? s.substring(5) : s));
            }
            if (img == null) {
                throw new IOException("formato de imagen no reconocido");
            }
            int w = img.getWidth(), h = img.getHeight();
            double k = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
            int nw = Math.max(1, (int) (w * k)), nh = Math.max(1, (int) (h * k));
            int[] px = new int[nw * nh];
            for (int y = 0; y < nh; y++) {
                for (int x = 0; x < nw; x++) {
                    px[y * nw + x] = img.getRGB((int) (x / k), (int) (y / k));
                }
            }
            e.w = nw;
            e.h = nh;
            e.pixels = px;
        } catch (IOException | RuntimeException ex) {
            e.error = String.valueOf(ex.getMessage());
        } finally {
            e.loading = false;
        }
    }

    /** El id de la textura (la sube la primera vez); la textura rosa y negra si falló; 0 mientras carga. */
    public static int texture(Entry e) {
        if (e.texture != 0 || e.loading) {
            return e.texture;
        }
        if (e.pixels == null) {
            int[] px = new int[64];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    px[y * 8 + x] = ((x + y) & 1) == 0 ? 0xFFFF00FF : 0xFF000000;
                }
            }
            e.w = 8;
            e.h = 8;
            e.texture = Textures.upload(px, 8, 8, true);
        } else {
            e.texture = Textures.upload(e.pixels, e.w, e.h, e.nearest);
        }
        return e.texture;
    }

    public static void setNearest(Entry e, boolean nearest) {
        if (e.nearest != nearest) {
            e.nearest = nearest;
            Textures.setFilter(e.texture, nearest);
        }
    }
}
