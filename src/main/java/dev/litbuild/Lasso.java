package dev.litbuild;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import imgui.type.ImBoolean;

/**
 * Lasso Select: el contorno dibujado en pantalla se proyecta sobre los bloques visibles. Se lanzan rayos desde
 * una rejilla de píxeles dentro del contorno (más gruesa cuanto mayor es el lazo, con un tope de rayos).
 */
public final class Lasso {
    public static final int[] depth = {1};
    public static final ImBoolean nonSolid = new ImBoolean(false);
    public static final List<float[]> points = new ArrayList<float[]>();
    public static boolean active;

    private static final int MAX_RAYS = 30_000;

    private Lasso() {
    }

    public static void begin(float x, float y) {
        points.clear();
        points.add(new float[] {x, y});
        active = true;
    }

    public static void add(float x, float y) {
        float[] last = points.get(points.size() - 1);
        if ((x - last[0]) * (x - last[0]) + (y - last[1]) * (y - last[1]) >= 9f) {
            points.add(new float[] {x, y});
        }
    }

    /** Cierra el lazo y devuelve los bloques que cubre (vacío si el contorno es demasiado pequeño). */
    public static Set<Long> finish() {
        active = false;
        Set<Long> found = new HashSet<Long>();
        if (points.size() >= 3) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] p : points) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            float step = Math.max(2f, (float) Math.sqrt((maxX - minX) * (maxY - minY) / MAX_RAYS));
            for (float y = minY; y <= maxY && found.size() < EditEngine.MAX_BLOCKS; y += step) {
                for (float x = minX; x <= maxX; x += step) {
                    if (inside(x, y)) {
                        Targeting.pickInto(x, y, 192, depth[0], nonSolid.get(), found);
                    }
                }
            }
        }
        points.clear();
        return found;
    }

    /** Regla par-impar: ¿está el punto dentro del polígono? */
    private static boolean inside(float x, float y) {
        boolean in = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            float[] a = points.get(i), b = points.get(j);
            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
                in = !in;
            }
        }
        return in;
    }
}
