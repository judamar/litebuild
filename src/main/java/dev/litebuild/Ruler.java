package dev.litebuild;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.math.BlockPos;

/** Regla: una lista de puntos en el mundo y sus medidas. */
public final class Ruler {
    public static final List<BlockPos> points = new ArrayList<BlockPos>();
    /** View > Show Ruler: dibuja o no las mediciones en el mundo. */
    public static final imgui.type.ImBoolean show = new imgui.type.ImBoolean(true);

    private Ruler() {
    }

    public static void add(BlockPos p) {
        points.add(p);
    }

    public static void removeLast() {
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
        }
    }

    public static void clear() {
        points.clear();
    }

    /** Distancia en línea recta del primer al último punto. */
    public static double euclidean() {
        if (points.size() < 2) {
            return 0;
        }
        BlockPos a = points.get(0), b = points.get(points.size() - 1);
        return Math.sqrt(a.distanceSq(b));
    }

    /** Suma de las distancias por eje del primer al último punto (bloques recorridos andando). */
    public static int manhattan() {
        if (points.size() < 2) {
            return 0;
        }
        BlockPos a = points.get(0), b = points.get(points.size() - 1);
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    /** Esquina mínima (0) o máxima (1) de la caja que contiene todos los puntos. */
    public static BlockPos corner(boolean max) {
        int x = max ? Integer.MIN_VALUE : Integer.MAX_VALUE, y = x, z = x;
        for (BlockPos p : points) {
            x = max ? Math.max(x, p.getX()) : Math.min(x, p.getX());
            y = max ? Math.max(y, p.getY()) : Math.min(y, p.getY());
            z = max ? Math.max(z, p.getZ()) : Math.min(z, p.getZ());
        }
        return new BlockPos(x, y, z);
    }

    /** Rumbo (yaw) en grados del primer al segundo punto, con el convenio de Minecraft (0 = sur). */
    public static double yaw() {
        BlockPos a = points.get(0), b = points.get(1);
        return Math.toDegrees(Math.atan2(-(b.getX() - a.getX()), b.getZ() - a.getZ()));
    }

    /** Inclinación (pitch) en grados; positivo mira hacia abajo, como en Minecraft. */
    public static double pitch() {
        BlockPos a = points.get(0), b = points.get(1);
        double h = Math.sqrt((double) (b.getX() - a.getX()) * (b.getX() - a.getX())
                + (double) (b.getZ() - a.getZ()) * (b.getZ() - a.getZ()));
        return -Math.toDegrees(Math.atan2(b.getY() - a.getY(), h));
    }
}
