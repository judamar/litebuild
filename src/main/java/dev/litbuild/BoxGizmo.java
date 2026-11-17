package dev.litbuild;

import imgui.ImGui;

/**
 * Manejadores de Box Select: un cubito de color en el centro de cada cara de la selección. Arrastrar uno con el
 * botón izquierdo estira o encoge esa cara. Todo se hace en pantalla: se proyecta cada manejador para ver cuál
 * está bajo el cursor y el arrastre se mide sobre la dirección en pantalla del eje de la cara.
 *
 * <p>Con un Placement fijado (placementMode) el gizmo es el completo: flechas por eje y nodo central para mover,
 * nodos de plano (mueven en dos ejes), tres anillos de rotación (yaw, pitch y roll del Placement) y tres nodos de
 * escala. Con Ctrl el giro salta de 15 en 15 grados y la escala es uniforme. Los ejes son los del mundo (Global) o los
 * propios de la copia, ya girada (Local, {@link Placement#LOCAL}); los nodos de escala siguen siempre los ejes propios,
 * porque la escala se aplica antes que el giro.
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
    /** Manejadores del Placement: 0-5 flechas, 6 nodo central, 7-9 planos (perpendiculares a los ejes 0-2), 10-12 anillos, 13-15 escala. */
    public static final int CENTER = 6, PLANE = 7, RING = 10, SCALE = 13, COUNT = 16;
    /** Centro de cada manejador en el mundo (los anillos no usan el suyo: ver {@link #ringPoint}). */
    public static final double[][] center = new double[COUNT][3];
    /** El centro de la caja y el radio de los anillos, para dibujarlos. */
    public static final double[] mid = new double[3];
    public static double ringRadius;
    /** Ejes de los manejadores: frame[a] es el vector unitario del eje a (los del mundo, o los de la copia en modo Local). */
    public static final double[][] frame = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    /** Ejes propios de la copia (giro actual), para los nodos de escala. */
    private static final double[][] ownFrame = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    /** true = mueve el Placement (flechas y nodo central) en vez de estirar la selección. */
    public static boolean placementMode;
    private static final int M_ARROW = 0, M_CENTER = 1, M_PLANE = 2, M_RING = 3, M_SCALE = 4;
    private static int mode;
    private static double centerDist;
    private static final int[] startXyz = new int[3];
    /** Plano de arrastre: pasa por planeOrigin y es perpendicular a planeNormal. */
    private static final double[] planeOrigin = new double[3], planeNormal = new double[3];
    private static final double[][] dragFrame = new double[3][3];
    private static final double[] startPoint = new double[3];
    private static double ringStartAngle, ringStartValue;
    private static boolean ringLocal;
    private static double[][] ringStartMatrix;
    private static final float[] ringStartAngles = new float[3];
    private static double scaleExtent;
    private static final float[] scaleStart = new float[3];
    /** Dirección en el mundo (con signo) del eje que se arrastra. */
    private static final double[] arrowDir = new double[3];
    private static final int RING_SEGMENTS = 64;
    public static double half = 0.3;

    private static double offset = 0.5;
    private static int[] endpoint;
    private static int axis;
    private static int startValue;
    private static float startMx, startMy, vx, vy, vlen2;

    private BoxGizmo() {
    }

    /** La dirección con signo de la flecha f (0-5) en el mundo. */
    public static double[] direction(int f) {
        double s = f % 2 == 0 ? 1 : -1;
        double[] fr = frame[f / 2];
        return new double[] {fr[0] * s, fr[1] * s, fr[2] * s};
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

    /** El punto i de los RING_SEGMENTS del anillo alrededor del eje a, en el mundo. */
    public static double[] ringPoint(int a, int i) {
        double t = 2 * Math.PI * i / RING_SEGMENTS;
        double[] u = frame[(a + 1) % 3], v = frame[(a + 2) % 3];
        double c = Math.cos(t) * ringRadius, s = Math.sin(t) * ringRadius;
        return new double[] {mid[0] + u[0] * c + v[0] * s, mid[1] + u[1] * c + v[1] * s, mid[2] + u[2] * c + v[2] * s};
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
        boolean place = placementMode && Placement.shape != null;
        if (place) {
            lo = new double[] {Placement.pos[0], Placement.pos[1], Placement.pos[2]};
            hi = new double[] {lo[0] + Placement.shape.sx, lo[1] + Placement.shape.sy, lo[2] + Placement.shape.sz};
        } else {
            lo = new double[] {Selection.min(0), Selection.min(1), Selection.min(2)};
            hi = new double[] {Selection.max(0) + 1.0, Selection.max(1) + 1.0, Selection.max(2) + 1.0};
        }
        double[] m = {(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2};
        // Más lejos, más grande, para que siga siendo fácil de pillar.
        double dx = m[0] - Targeting.camX, dy = m[1] - (Targeting.camY + 1.62), dz = m[2] - Targeting.camZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        half = Math.max(0.18, Math.min(2.5, 0.035 * dist));
        offset = half * 1.6;

        // Ejes: los del mundo, o los de la copia girada (Local). halfExt = medio largo de la copia a lo largo de cada eje.
        double[] halfExt = {(hi[0] - lo[0]) / 2, (hi[1] - lo[1]) / 2, (hi[2] - lo[2]) / 2};
        double[] ownHalf = halfExt.clone();
        boolean local = false;
        for (int a = 0; a < 3; a++) {
            for (int k = 0; k < 3; k++) {
                frame[a][k] = a == k ? 1 : 0;
                ownFrame[a][k] = frame[a][k];
            }
        }
        if (place && Placement.original != null) {
            double[][] axes = Euler.axes(Euler.matrix(Placement.angles[0], Placement.angles[1], Placement.angles[2]));
            Clipboard o = Placement.original;
            ownHalf = new double[] {o.sx * Placement.scale[0] / 2.0, o.sy * Placement.scale[1] / 2.0,
                o.sz * Placement.scale[2] / 2.0};
            for (int a = 0; a < 3; a++) {
                System.arraycopy(axes[a], 0, ownFrame[a], 0, 3);
            }
            if (Placement.LOCAL.get()) {
                local = true;
                halfExt = ownHalf.clone();
                for (int a = 0; a < 3; a++) {
                    System.arraycopy(axes[a], 0, frame[a], 0, 3);
                }
            }
        }
        for (int f = 0; f < 6; f++) {
            int a = f / 2;
            double s = f % 2 == 0 ? 1 : -1;
            for (int k = 0; k < 3; k++) {
                center[f][k] = m[k] + frame[a][k] * s * (halfExt[a] + offset);
            }
        }
        System.arraycopy(m, 0, center[CENTER], 0, 3);
        System.arraycopy(m, 0, mid, 0, 3);
        ringRadius = Math.max(halfExt[0], Math.max(halfExt[1], halfExt[2])) + offset * 2.4;
        for (int a = 0; a < 3; a++) {
            int b = (a + 1) % 3, c = (a + 2) % 3;
            for (int k = 0; k < 3; k++) {
                // Plano perpendicular al eje a: un cuadradito en la diagonal de los otros dos ejes.
                center[PLANE + a][k] = m[k] + (frame[b][k] + frame[c][k]) * half * 3.2;
                // Escala: junto a la flecha positiva del eje propio, desplazado hacia otro eje propio.
                center[SCALE + a][k] = m[k] + ownFrame[a][k] * (ownHalf[a] + offset) + ownFrame[c][k] * half * 2.4;
            }
        }
    }

    private static void setPlane(double[] origin, double[] normal) {
        System.arraycopy(origin, 0, planeOrigin, 0, 3);
        System.arraycopy(normal, 0, planeNormal, 0, 3);
    }

    /** Empieza el arrastre del manejador bajo el cursor. */
    public static void begin(float mx, float my) {
        int f = hovered;
        if (f < 0) {
            return;
        }
        for (int k = 0; k < 3; k++) {
            startXyz[k] = Placement.pos[k];
            System.arraycopy(frame[k], 0, dragFrame[k], 0, 3);
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
        if (mode == M_SCALE) {
            Placement.follow = false;
            for (int k = 0; k < 3; k++) {
                scaleStart[k] = Placement.scale[k];
                arrowDir[k] = ownFrame[axis][k];
            }
            Clipboard o = Placement.original;
            scaleExtent = Math.max(1, new double[] {o.sx, o.sy, o.sz}[axis] * Placement.scale[axis]);
        } else {
            double[] d = direction(f);
            System.arraycopy(d, 0, arrowDir, 0, 3);
            if (placementMode) {
                Placement.follow = false;
            }
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
        float[] b = Targeting.project(center[f][0] + arrowDir[0], center[f][1] + arrowDir[1], center[f][2] + arrowDir[2]);
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

    /** Punto donde el rayo del cursor corta el plano de arrastre; null si es casi paralelo o queda detrás de la cámara. */
    private static double[] planePoint(float mx, float my) {
        double[] r = Targeting.ray(mx, my);
        if (r == null) {
            return null;
        }
        double denom = r[3] * planeNormal[0] + r[4] * planeNormal[1] + r[5] * planeNormal[2];
        if (Math.abs(denom) < 1e-3) {
            return null;
        }
        double t = ((planeOrigin[0] - r[0]) * planeNormal[0] + (planeOrigin[1] - r[1]) * planeNormal[1]
                + (planeOrigin[2] - r[2]) * planeNormal[2]) / denom;
        if (t < 0) {
            return null;
        }
        return new double[] {r[0] + r[3] * t, r[1] + r[4] * t, r[2] + r[5] * t};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void beginPlane(int a, float mx, float my) {
        axis = a;
        setPlane(mid, dragFrame[a]);
        double[] p = planePoint(mx, my);
        if (p == null) {
            return;
        }
        System.arraycopy(p, 0, startPoint, 0, 3);
        Placement.follow = false;
        mode = M_PLANE;
        active = PLANE + a;
        dragging = true;
    }

    /** El ángulo del punto (x, y, z), en coordenadas del eje del anillo, en el sentido en que crece ese ángulo del Placement. */
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

    /** Ángulo del punto del mundo p respecto al centro, en las coordenadas del marco de arrastre. */
    private static double angleOf(int a, double[] p) {
        double[] r = {p[0] - mid[0], p[1] - mid[1], p[2] - mid[2]};
        return ringAngle(a, dot(r, dragFrame[0]), dot(r, dragFrame[1]), dot(r, dragFrame[2]));
    }

    /** Índice en Placement.angles (yaw, pitch, roll) del giro alrededor del eje a. */
    private static int angleIndex(int a) {
        return a == 0 ? 1 : a == 1 ? 0 : 2;
    }

    private static void beginRing(int a, float mx, float my) {
        axis = a;
        setPlane(mid, dragFrame[a]);
        double[] p = planePoint(mx, my);
        if (p == null) {
            return;
        }
        ringStartAngle = angleOf(a, p);
        ringStartValue = Placement.angles[angleIndex(a)];
        ringLocal = Placement.LOCAL.get();
        ringStartMatrix = Euler.matrix(Placement.angles[0], Placement.angles[1], Placement.angles[2]);
        System.arraycopy(Placement.angles, 0, ringStartAngles, 0, 3);
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
            double[] p = planePoint(mx, my);
            if (p != null) {
                double[] delta = {p[0] - startPoint[0], p[1] - startPoint[1], p[2] - startPoint[2]};
                int b = (axis + 1) % 3, c = (axis + 2) % 3;
                double sb = dot(delta, dragFrame[b]), sc = dot(delta, dragFrame[c]);
                for (int k = 0; k < 3; k++) {
                    Placement.pos[k] = startXyz[k] + (int) Math.round(sb * dragFrame[b][k] + sc * dragFrame[c][k]);
                }
                Placement.pos[1] = Math.max(0, Math.min(255, Placement.pos[1]));
            }
            return;
        }
        if (mode == M_RING) {
            ringDrag(mx, my);
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
        if (placementMode) {
            for (int k = 0; k < 3; k++) {
                Placement.pos[k] = startXyz[k] + (int) Math.round(blocks * arrowDir[k]);
            }
            Placement.pos[1] = Math.max(0, Math.min(255, Placement.pos[1]));
            return;
        }
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

    /**
     * Gira con el anillo. En Global solo cambia el ángulo de ese eje (yaw, pitch o roll); en Local el giro se compone con
     * el de partida alrededor del eje propio de la copia y se vuelve a pasar a yaw, pitch y roll.
     */
    private static void ringDrag(float mx, float my) {
        double[] p = planePoint(mx, my);
        if (p == null) {
            return;
        }
        double delta = wrap180(Math.toDegrees(angleOf(axis, p) - ringStartAngle));
        float[] before = {Placement.angles[0], Placement.angles[1], Placement.angles[2]};
        int snap = ImGui.getIO().getKeyCtrl() ? 15 : 1;
        if (ringLocal) {
            double d = Math.round(delta / snap) * (double) snap;
            double[][] turn = axis == 0 ? Euler.rx(d) : axis == 1 ? Euler.ry(d) : Euler.rz(d);
            double[] a = Euler.angles(Euler.mul(ringStartMatrix, turn));
            for (int k = 0; k < 3; k++) {
                Placement.angles[k] = (float) a[k];
            }
        } else {
            Placement.angles[angleIndex(axis)] = (float) wrap180(Math.round((ringStartValue + delta) / snap) * (double) snap);
        }
        if (before[0] == Placement.angles[0] && before[1] == Placement.angles[1] && before[2] == Placement.angles[2]) {
            return;
        }
        if (!refreshKeepingCentre()) {
            System.arraycopy(before, 0, Placement.angles, 0, 3);
        }
    }

    public static void end() {
        dragging = false;
        mode = M_ARROW;
        active = -1;
    }
}
