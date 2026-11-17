package dev.litbuild;

import imgui.type.ImBoolean;
import imgui.type.ImInt;

/** Ajustes del pincel compartidos por Painter y Freehand Draw. */
public final class Brush {
    public static final String[] SHAPES = {"Sphere", "Cube", "Octahedron"};
    public static final int SPHERE = 0, CUBE = 1, OCTAHEDRON = 2;

    public static final int[] radius = {4};
    public static final ImInt shape = new ImInt(SPHERE);
    /** Painter: solo bloques en contacto con un bloque no sólido. */
    public static final ImBoolean maskSurface = new ImBoolean(false);
    /** Lo marca la pantalla cada frame: hay que dibujar el pincel bajo el cursor. */
    public static boolean preview;

    private Brush() {
    }

    /** ¿Está el desplazamiento (dx, dy, dz) dentro de un pincel de radio r? */
    public static boolean contains(int shape, int dx, int dy, int dz, int r) {
        switch (shape) {
            case CUBE:
                return Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) <= r;
            case OCTAHEDRON:
                return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= r;
            default:
                double lim = r + 0.5;
                return dx * dx + dy * dy + dz * dz <= lim * lim;
        }
    }
}
