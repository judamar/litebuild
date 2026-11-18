package dev.litebuild;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;

/**
 * Minimum Brightness: sube el gamma de Minecraft según UiSettings.minBrightness. El gamma original se apunta en
 * config/litebuild/gamma.txt mientras está subido: si el juego guardó el gamma alto en options.txt (al cerrar), al arrancar
 * se restaura el original.
 */
public final class Brightness {
    private static float saved = -1f;

    private Brightness() {
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/litebuild/gamma.txt");
    }

    /** Al arrancar: si options.txt quedó con el gamma subido, vuelve al que había antes. */
    public static void restoreAtStartup() {
        File f = file();
        try {
            if (f.isFile() && Minecraft.getMinecraft().gameSettings.gammaSetting > 1.5f) {
                float original = Float.parseFloat(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
                Minecraft.getMinecraft().gameSettings.gammaSetting = original;
            }
            f.delete();
        } catch (IOException | RuntimeException e) {
            LiteModLiteBuild.LOG.warn("No se pudo restaurar el brillo", e);
        }
    }

    /** Cada tick del cliente. */
    public static void tick(Minecraft mc) {
        float want = UiSettings.minBrightness;
        if (want > 0f) {
            if (saved < 0f) {
                saved = mc.gameSettings.gammaSetting;
                try {
                    file().getParentFile().mkdirs();
                    Files.write(file().toPath(), Float.toString(saved).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    LiteModLiteBuild.LOG.warn("No se pudo apuntar el gamma original", e);
                }
            }
            mc.gameSettings.gammaSetting = saved + want * 100f;
        } else if (saved >= 0f) {
            mc.gameSettings.gammaSetting = saved;
            saved = -1f;
            file().delete();
        }
    }
}
