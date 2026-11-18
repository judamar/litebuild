package dev.litebuild;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Vistas: posiciones con nombre del jugador (y hacia dónde mira) a las que se puede volver con un clic. */
public final class Views {
    public static final class View {
        public String name;
        public double x, y, z;
        public float yaw, pitch;
        public int dim;
    }

    public static final List<View> list = new ArrayList<View>();
    private static boolean loaded;

    private Views() {
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/litebuild/views.properties");
    }

    /** Carga las vistas guardadas la primera vez. */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = file();
        if (!f.isFile()) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
            int n = Integer.parseInt(p.getProperty("count", "0"));
            for (int i = 0; i < n; i++) {
                View v = new View();
                v.name = p.getProperty(i + ".name", "Vista " + (i + 1));
                v.x = Double.parseDouble(p.getProperty(i + ".x", "0"));
                v.y = Double.parseDouble(p.getProperty(i + ".y", "0"));
                v.z = Double.parseDouble(p.getProperty(i + ".z", "0"));
                v.yaw = Float.parseFloat(p.getProperty(i + ".yaw", "0"));
                v.pitch = Float.parseFloat(p.getProperty(i + ".pitch", "0"));
                v.dim = Integer.parseInt(p.getProperty(i + ".dim", "0"));
                list.add(v);
            }
        } catch (IOException | NumberFormatException e) {
            LiteModLiteBuild.LOG.warn("No se pudo leer views.properties", e);
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("count", Integer.toString(list.size()));
        for (int i = 0; i < list.size(); i++) {
            View v = list.get(i);
            p.setProperty(i + ".name", v.name);
            p.setProperty(i + ".x", Double.toString(v.x));
            p.setProperty(i + ".y", Double.toString(v.y));
            p.setProperty(i + ".z", Double.toString(v.z));
            p.setProperty(i + ".yaw", Float.toString(v.yaw));
            p.setProperty(i + ".pitch", Float.toString(v.pitch));
            p.setProperty(i + ".dim", Integer.toString(v.dim));
        }
        File f = file();
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "LiteBuild views");
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudo guardar views.properties", e);
        }
    }

    /** Guarda la posición actual del jugador con ese nombre. */
    public static void add(String name) {
        EntityPlayerSP pl = Minecraft.getMinecraft().player;
        View v = new View();
        v.name = name.trim().isEmpty() ? "Vista " + (list.size() + 1) : name.trim();
        v.x = pl.posX;
        v.y = pl.posY;
        v.z = pl.posZ;
        v.yaw = pl.rotationYaw;
        v.pitch = pl.rotationPitch;
        v.dim = pl.dimension;
        list.add(v);
        save();
    }

    public static void remove(int i) {
        list.remove(i);
        save();
    }

    /** Lleva al jugador a la vista. Devuelve false si está en otra dimensión. */
    public static boolean teleport(View v) {
        EntityPlayerSP pl = Minecraft.getMinecraft().player;
        if (pl.dimension != v.dim) {
            return false;
        }
        pl.setPositionAndUpdate(v.x, v.y, v.z);
        pl.rotationYaw = v.yaw;
        pl.rotationPitch = v.pitch;
        return true;
    }
}
