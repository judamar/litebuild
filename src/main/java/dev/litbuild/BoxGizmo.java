package dev.litbuild;

import imgui.ImGui;

/**
 * Manejadores de Box Select: un cubito de color en el centro de cada cara de la selección. Arrastrar uno con el
 * botón izquierdo estira o encoge esa cara. Todo se hace en pantalla: se proyecta cada manejador para ver cuál
 * está bajo el cursor y el arrastre se mide sobre la dirección en pantalla del eje de la cara.
 *
 * <p>Con un Placement fijado (placementMode) el gizmo es el completo: flechas por eje y nodo central para mover,
 * nodos de plano (mueven en dos ejes), tres anillos de rotación (yaw, pitch y roll del Placement) y tres nodos de
 * escala. Con Ctrl el giro salta de 15 en 15 grados y la escala es uniforme.
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
    /** Manejadores del Placement: 0-5 flechas, 6 nodo central, 7-9 planos (perpendiculares a X, Y, Z), 10-12 anillos, 13-15 escala. */
    public static final int CENTER = 6, PLANE = 7, RING = 10, SCALE = 13, COUNT = 16;
    /** Centro de cada manejador en el mundo (los anillos no usan el suyo: ver {@link #ringPoint}). */
    public static final double[][] center = new double[COUNT][3];
    /** El centro de la caja y el radio de los anillos, para dibujarlos. */
    public static final double[] mid = new double[3];
    public static double ringRadius;
    /** true = mueve el Placement (flechas y nodo central) en vez de estirar la selección. */
    public static boolean placementMode;
    private static final int M_ARROW = 0, M_CENTER = 1, M_PLANE = 2, M_RING = 3, M_SCALE = 4;
    private static int mode;
    private static double centerDist;
    private static int startPos;
    private static final int[] startXyz = new int[3];
    private static double planeCoord;
    private static final double[] startPoint = new double[3];
    private static final double[] ringC = new double[3];
    private static double ringStartAngle, ringStartValue;
    private static double scaleExtent;
    private static final float[] scaleStart = new float[3];
    private static final int RING_SEGMENTS = 64;
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
        for (int f = 0; f < (placementMode ? COUNT : 6); f++) {
            float d2 = f >= RING && f < SCALE ? ringDistance2(f - RING, mx, my) * 1.6f : pointDistance2(f, mx, my);
            if (d2 < best) {
                best = d2;
                hovered = f;
            }
        }
    }

    private static float pointDistance2(int f, float mx, float my) {
        float[] p = Targeting.project(center[f][0], center[f][1], center[f][2]);
        if (p == null) {
            return Float.MAX_VALUE;
        }
        return (p[0] - mx) * (p[0] - mx) + (p[1] - my) * (p[1] - my);
    }

    /** El punto i de los RING_SEGMENTS del anillo alrededor del eje a (0 X, 1 Y, 2 Z), en el mundo. */
    public static double[] ringPoint(int a, int i) {
        double t = 2 * Math.PI * i / RING_SEGMENTS;
        double[] p = {mid[0], mid[1], mid[2]};
        int b = (a + 1) % 3, c = (a + 2) % 3;
        p[b] += Math.cos(t) * ringRadius;
        p[c] += Math.sin(t) * ringRadius;
        return p;
    }

    public static int ringSegments() {
        return RING_SEGMENTS;
    }

    /** Distancia al cuadrado en pantalla del cursor al anillo (polilínea de segmentos proyectados). */
    private static float ringDistance2(int a, float mx, float my) {
        float best = Float.MAX_VALUE;
        float[] prev = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double[] w = ringPoint(a, i % RING_SEGMENTS);
            float[] p = Targeting.project(w[0], w[1], w[2]);
            if (p != null && prev != null) {
                float dx = p[0] - prev[0], dy = p[1] - prev[1];
                float len2 = dx * dx + dy * dy;
                float t = len2 < 1e-6f ? 0f : Math.max(0f, Math.min(1f, ((mx - prev[0]) * dx + (my - prev[1]) * dy) / len2));
                float qx = prev[0] + dx * t - mx, qy = prev[1] + dy * t - my;
                best = Math.min(best, qx * qx + qy * qy);
            }
            prev = p;
        }
        return best;
    }

    public static void reset() {
        visible = false;
        hovered = -1;
        dragging = false;
        active = -1;
    }

    private static void layout() {
        double[] lo, hi;
        if (placementMode && Placement.shape != null) {
            lo = new double[] {Placement.pos[0], Placement.pos[1], Placement.pos[2]};
            hi = new double[] {lo[0] + Placement.shape.sx, lo[1] + Placement.shape.sy, lo[2] + Placement.shape.sz};
        } else {
            lo = new double[] {Selection.min(0), Selection.min(1), Selection.min(2)};
            hi = new double[] {Selection.max(0) + 1.0, Selection.max(1) + 1.0, Selection.max(2) + 1.0};
        }
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
        System.arraycopy(mid, 0, center[CENTER], 0, 3);
        System.arraycopy(mid, 0, BoxGizmo.mid, 0, 3);
        double extent = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
        ringRadius = extent / 2 + offset * 2.4;
        for (int a = 0; a < 3; a++) {
            int b = (a + 1) % 3, c = (a + 2) % 3;
            // Plano perpendicular al eje a: un cuadradito en la diagonal de los otros dos ejes.
            System.arraycopy(mid, 0, center[PLANE + a], 0, 3);
            center[PLANE + a][b] += half * 3.2;
            center[PLANE + a][c] += half * 3.2;
            // Escala: junto a la flecha positiva, desplazado hacia otro eje.
            System.arraycopy(center[a * 2], 0, center[SCALE + a], 0, 3);
            center[SCALE + a][c] += half * 2.4;
        }
    }

    /** Empieza el arrastre del manejador bajo el cursor. */
    public static void begin(float mx, float my) {
        int f = hovered;
        if (f < 0) {
            return;
        }
        if (f == CENTER) {
            // Nodo central: el Placement sigue al cursor manteniendo la distancia a la cámara.
            mode = M_CENTER;
            centerDist = Math.sqrt(Math.pow(center[CENTER][0] - Targeting.camX, 2)
                    + Math.pow(center[CENTER][1] - Targeting.camY, 2) + Math.pow(center[CENTER][2] - Targeting.camZ, 2));
            active = CENTER;
            dragging = true;
            return;
        }
        if (f >= PLANE && f < RING) {
            beginPlane(f - PLANE, mx, my);
            return;
        }
        if (f >= RING && f < SCALE) {
            beginRing(f - RING, mx, my);
            return;
        }
        mode = f >= SCALE ? M_SCALE : M_ARROW;
        axis = f >= SCALE ? f - SCALE : f / 2;
        int dirIndex = f >= SCALE ? axis * 2 : f;
        if (mode == M_SCALE) {
            Placement.follow = false;
            for (int k = 0; k < 3; k++) {
                scaleStart[k] = Placement.scale[k];
            }
            scaleExtent = Math.max(1, new double[] {Placement.shape.sx, Placement.shape.sy, Placement.shape.sz}[axis]);
        } else if (placementMode) {
            startPos = Placement.pos[axis];
            Placement.follow = false;
        }
        boolean positive = f % 2 == 0;
        // El extremo (p1 o p2) que forma esa cara; la selección no cambia de extremos durante el arrastre.
        if (placementMode) {
            endpoint = null;
        } else if (positive) {
            endpoint = Selection.p1[axis] >= Selection.p2[axis] ? Selection.p1 : Selection.p2;
        } else {
            endpoint = Selection.p1[axis] <= Selection.p2[axis] ? Selection.p1 : Selection.p2;
        }
        if (!placementMode) {
            startValue = endpoint[axis];
        }
        float[] a = Targeting.project(center[f][0], center[f][1], center[f][2]);
        float[] b = Targeting.project(center[f][0] + DIR[dirIndex][0], center[f][1] + DIR[dirIndex][1],
                center[f][2] + DIR[dirIndex][2]);
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

    /** Punto donde el rayo del cursor corta el plano perpendicular al eje a que pasa por planeCoord; null si es casi paralelo. */
    private static double[] planePoint(float mx, float my, int a) {
        double[] r = Targeting.ray(mx, my);
        if (r == null || Math.abs(r[3 + a]) < 1e-3) {
            return null;
        }
        double t = (planeCoord - r[a]) / r[3 + a];
        if (t < 0) {
            return null;
        }
        return new double[] {r[0] + r[3] * t, r[1] + r[4] * t, r[2] + r[5] * t};
    }

    private static void beginPlane(int a, float mx, float my) {
        axis = a;
        planeCoord = mid[a];
        double[] p = planePoint(mx, my, a);
        if (p == null) {
            return;
        }
        System.arraycopy(p, 0, startPoint, 0, 3);
        for (int k = 0; k < 3; k++) {
            startXyz[k] = Placement.pos[k];
        }
        Placement.follow = false;
        mode = M_PLANE;
        active = PLANE + a;
        dragging = true;
    }

    /** El ángulo del punto (x, y, z) respecto al centro del anillo del eje a, medido en el sentido en que crece ese ángulo del Placement. */
    private static double ringAngle(int a, double x, double y, double z) {
        switch (a) {
            case 0:
                return Math.atan2(z, y); // pitch: de +Y hacia +Z
            case 1:
                return Math.atan2(z, x); // yaw: de +X hacia +Z (horario visto desde arriba)
            default:
                return Math.atan2(y, x); // roll: de +X hacia +Y
        }
    }

    /** Índice en Placement.angles (yaw, pitch, roll) del giro alrededor del eje a. */
    private static int angleIndex(int a) {
        return a == 0 ? 1 : a == 1 ? 0 : 2;
    }

    private static void beginRing(int a, float mx, float my) {
        axis = a;
        planeCoord = mid[a];
        double[] p = planePoint(mx, my, a);
        if (p == null) {
            return;
        }
        System.arraycopy(mid, 0, ringC, 0, 3);
        ringStartAngle = ringAngle(a, p[0] - ringC[0], p[1] - ringC[1], p[2] - ringC[2]);
        ringStartValue = Placement.angles[angleIndex(a)];
        Placement.follow = false;
        mode = M_RING;
        active = RING + a;
        dragging = true;
    }

    private static double wrap180(double d) {
        d = d % 360.0;
        return d > 180 ? d - 360 : d <= -180 ? d + 360 : d;
    }

    /** Refresca el Placement tras cambiar giro o escala manteniendo también el centro vertical; false si no cabe. */
    private static boolean refreshKeepingCentre() {
        Clipboard old = Placement.shape;
        if (!Placement.refresh()) {
            return false;
        }
        if (old != null && Placement.shape != null) {
            Placement.pos[1] = Math.max(0, Math.min(255, Placement.pos[1] + (old.sy - Placement.shape.sy) / 2));
        }
        return true;
    }

    /** Mueve la cara según lo que ha avanzado el ratón sobre la dirección del eje en pantalla. */
    public static void drag(float mx, float my) {
        if (mode == M_CENTER) {
            net.minecraft.util.math.Vec3d p = Targeting.pointAt(mx, my, centerDist);
            if (p != null && Placement.shape != null) {
                Placement.pos[0] = (int) Math.floor(p.x - Placement.shape.sx / 2.0 + 0.5);
                Placement.pos[1] = Math.max(0, (int) Math.floor(p.y - Placement.shape.sy / 2.0 + 0.5));
                Placement.pos[2] = (int) Math.floor(p.z - Placement.shape.sz / 2.0 + 0.5);
                Placement.follow = false;
            }
            return;
        }
        if (mode == M_PLANE) {
            double[] p = planePoint(mx, my, axis);
            if (p != null) {
                for (int k = 0; k < 3; k++) {
                    if (k != axis) {
                        Placement.pos[k] = startXyz[k] + (int) Math.round(p[k] - startPoint[k]);
                    }
                }
                Placement.pos[1] = Math.max(0, Math.min(255, Placement.pos[1]));
            }
            return;
        }
        if (mode == M_RING) {
            double[] p = planePoint(mx, my, axis);
            if (p == null) {
                return;
            }
            double now = ringAngle(axis, p[0] - ringC[0], p[1] - ringC[1], p[2] - ringC[2]);
            double value = ringStartValue + wrap180(Math.toDegrees(now - ringStartAngle));
            int snap = ImGui.getIO().getKeyCtrl() ? 15 : 1;
            value = wrap180(Math.round(value / snap) * (double) snap);
            int idx = angleIndex(axis);
            float before = Placement.angles[idx];
            if ((float) value == before) {
                return;
            }
            Placement.angles[idx] = (float) value;
            if (!refreshKeepingCentre()) {
                Placement.angles[idx] = before;
            }
            return;
        }
        float blocks = ((mx - startMx) * vx + (my - startMy) * vy) / vlen2;
        if (mode == M_SCALE) {
            double factor = Math.max(0.05, (scaleExtent + blocks) / scaleExtent);
            boolean uniform = ImGui.getIO().getKeyCtrl();
            float[] before = {Placement.scale[0], Placement.scale[1], Placement.scale[2]};
            boolean changed = false;
            for (int k = 0; k < 3; k++) {
                if (uniform || k == axis) {
                    float v = (float) Math.max(0.1, Math.min(8.0, Math.round(scaleStart[k] * factor / 0.05) * 0.05));
                    changed |= v != Placement.scale[k];
                    Placement.scale[k] = v;
                }
            }
            if (changed && !refreshKeepingCentre()) {
                System.arraycopy(before, 0, Placement.scale, 0, 3);
            }
            return;
        }
        int n = Math.round(blocks);
        boolean positive = active % 2 == 0;
        if (placementMode) {
            Placement.pos[axis] = startPos + (positive ? n : -n);
            if (axis == 1) {
                Placement.pos[1] = Math.max(0, Math.min(255, Placement.pos[1]));
            }
            return;
        }
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
        mode = M_ARROW;
        active = -1;
    }
}
