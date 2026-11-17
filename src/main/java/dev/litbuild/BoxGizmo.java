package dev.litbuild;

/**
 * Manejadores de Box Select: un cubito de color en el centro de cada cara de la selección. Arrastrar uno con el
 * botón izquierdo estira o encoge esa cara. Todo se hace en pantalla: se proyecta cada manejador para ver cuál
 * está bajo el cursor y el arrastre se mide sobre la dirección en pantalla del eje de la cara.
 */
public final class BoxGizmo {
    /** Direcciones de las caras: 0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z. */
    public static final int[][] DIR = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Los manejadores se dibujan (lo calcula {@link #update} cada frame). */
    public static boolean visible;
    /** Cara bajo el cursor (0-5) o -1. */
    public static int hovered = -1;
    public static boolean dragging;
    /** Cara que se está arrastrando. */
    public static int active = -1;
    /** Centro de cada manejador en el mundo y su medio lado. */
    public static final double[][] center = new double[6][3];
    public static double half = 0.3;

    private static double offset = 0.5;
    private static int[] endpoint;
    private static int axis;
    private static int startValue;
    private static float startMx, startMy, vx, vy, vlen2;

    private BoxGizmo() {
    }

    /**
     * Recoloca los manejadores y detecta cuál está bajo el cursor (mx, my en píxeles de ImGui).
     * usable = hay una selección en caja con Box Select; allowHover = no se está moviendo la cámara.
     */
    public static void update(boolean usable, boolean allowHover, float mx, float my, float radius) {
        if (!usable) {
            if (dragging) {
                end();
            }
            visible = false;
            hovered = -1;
            return;
        }
        visible = true;
        layout();
        if (dragging) {
            return;
        }
        hovered = -1;
        if (!allowHover) {
            return;
        }
        float best = radius * radius;
        for (int f = 0; f < 6; f++) {
            float[] p = Targeting.project(center[f][0], center[f][1], center[f][2]);
            if (p == null) {
                continue;
            }
            float d2 = (p[0] - mx) * (p[0] - mx) + (p[1] - my) * (p[1] - my);
            if (d2 < best) {
                best = d2;
                hovered = f;
            }
        }
    }

    public static void reset() {
        visible = false;
        hovered = -1;
        dragging = false;
        active = -1;
    }

    private static void layout() {
        double[] lo = {Selection.min(0), Selection.min(1), Selection.min(2)};
        double[] hi = {Selection.max(0) + 1.0, Selection.max(1) + 1.0, Selection.max(2) + 1.0};
        double[] mid = {(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2};
        // Más lejos, más grande, para que siga siendo fácil de pillar.
        double dx = mid[0] - Targeting.camX, dy = mid[1] - (Targeting.camY + 1.62), dz = mid[2] - Targeting.camZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        half = Math.max(0.18, Math.min(2.5, 0.035 * dist));
        offset = half * 1.6;
        for (int f = 0; f < 6; f++) {
            int a = f / 2;
            for (int k = 0; k < 3; k++) {
                center[f][k] = mid[k];
            }
            center[f][a] = f % 2 == 0 ? hi[a] + offset : lo[a] - offset;
        }
    }

    /** Empieza el arrastre del manejador bajo el cursor. */
    public static void begin(float mx, float my) {
        int f = hovered;
        if (f < 0) {
            return;
        }
        axis = f / 2;
        boolean positive = f % 2 == 0;
        // El extremo (p1 o p2) que forma esa cara; la selección no cambia de extremos durante el arrastre.
        if (positive) {
            endpoint = Selection.p1[axis] >= Selection.p2[axis] ? Selection.p1 : Selection.p2;
        } else {
            endpoint = Selection.p1[axis] <= Selection.p2[axis] ? Selection.p1 : Selection.p2;
        }
        startValue = endpoint[axis];
        float[] a = Targeting.project(center[f][0], center[f][1], center[f][2]);
        float[] b = Targeting.project(center[f][0] + DIR[f][0], center[f][1] + DIR[f][1], center[f][2] + DIR[f][2]);
        if (a == null || b == null) {
            return;
        }
        vx = b[0] - a[0];
        vy = b[1] - a[1];
        vlen2 = vx * vx + vy * vy;
        if (vlen2 < 4f) {
            return; // el eje apunta casi directo a la cámara: no se puede medir el arrastre
        }
        startMx = mx;
        startMy = my;
        active = f;
        dragging = true;
    }

    /** Mueve la cara según lo que ha avanzado el ratón sobre la dirección del eje en pantalla. */
    public static void drag(float mx, float my) {
        float blocks = ((mx - startMx) * vx + (my - startMy) * vy) / vlen2;
        int n = Math.round(blocks);
        boolean positive = active % 2 == 0;
        int value = startValue + (positive ? n : -n);
        int other = (endpoint == Selection.p1 ? Selection.p2 : Selection.p1)[axis];
        // La cara no puede cruzar a la opuesta: como mínimo queda un bloque.
        value = positive ? Math.max(value, other) : Math.min(value, other);
        if (axis == 1) {
            value = Math.max(0, Math.min(255, value));
        }
        endpoint[axis] = value;
        Selection.mask = null;
    }

    public static void end() {
        dragging = false;
        active = -1;
    }
}
