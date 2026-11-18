package dev.litebuild;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import net.minecraft.client.Minecraft;

/** Ajustes de la interfaz que se recuerdan entre sesiones (config/litebuild/ui.properties). */
public final class UiSettings {
    /** Sube este número para que todos reciban de nuevo el diseño de ventanas por defecto. */
    public static final int LAYOUT_VERSION = 2;

    public static float fontSize = 16f;
    public static int theme = 0;
    public static int layout = 0;
    /** Brillo mínimo del mundo en el editor (0 = el de las opciones, 1 = todo iluminado). */
    public static float minBrightness = 0f;
    /** Opacidad del agua (0-1); negativo = la de vanilla. */
    public static float fluidOpacity = -1f;

    private UiSettings() {
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/litebuild/ui.properties");
    }

    public static void load() {
        File f = file();
        if (!f.isFile()) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
            fontSize = Math.max(10f, Math.min(32f, Float.parseFloat(p.getProperty("fontSize", "16"))));
            theme = Integer.parseInt(p.getProperty("theme", "0"));
            layout = Integer.parseInt(p.getProperty("layout", "0"));
            minBrightness = Math.max(0f, Math.min(1f, Float.parseFloat(p.getProperty("minBrightness", "0"))));
            fluidOpacity = Float.parseFloat(p.getProperty("fluidOpacity", "-1"));
        } catch (IOException | NumberFormatException e) {
            LiteModLiteBuild.LOG.warn("No se pudo leer ui.properties; se usan los valores por defecto", e);
        }
    }

    public static void save() {
        File f = file();
        f.getParentFile().mkdirs();
        Properties p = new Properties();
        p.setProperty("fontSize", Float.toString(fontSize));
        p.setProperty("theme", Integer.toString(theme));
        p.setProperty("layout", Integer.toString(layout));
        p.setProperty("minBrightness", Float.toString(minBrightness));
        p.setProperty("fluidOpacity", Float.toString(fluidOpacity));
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "LiteBuild UI");
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudo guardar ui.properties", e);
        }
    }
}
