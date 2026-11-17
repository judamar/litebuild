package dev.litbuild;

import java.util.HashMap;
import java.util.Map;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

/**
 * Modify: transforma la selección activa. Translate Copies y Rotate Copies añaden copias; Revolve (curva la
 * selección en un arco alrededor de un eje) y Twist (la retuerce a lo largo de un eje) la reconstruyen. Todo se
 * calcula por muestreo inverso (para cada bloque de destino se busca el de origen más cercano), así que no
 * quedan huecos. Con el eje Y los bloques con orientación giran en cuartos de vuelta; con otros ejes no.
 */
public final class Modify {
    public static final String[] MODES = {"Translate Copies", "Rotate Copies", "Revolve", "Twist"};
    public static final String[] AXES = {"X", "Y", "Z"};
    public static final String[] TYPES = {"Equidistant", "Custom Angle"};
    public static final int TRANSLATE = 0, ROTATE = 1, REVOLVE = 2, TWIST = 3;
    /** Radio máximo (en el plano de giro) de Revolve y Twist. */
    private static final int MAX_RADIUS = 300;

    // Estado de la interfaz (se congela en un Spec al aplicar).
    public static final ImInt mode = new ImInt(TRANSLATE);
    public static final ImInt axis = new ImInt(1);
    public static final ImInt type = new ImInt(0);
    public static final int[] count = {3};
    public static final int[] offset = {1, 0, 0};
    public static final ImBoolean relative = new ImBoolean(true);
    public static final float[] angle = {90f};
    public static final int[] translation = {0, 0, 0};
    /** Desplazamiento a lo largo del eje al final de un Revolve (espiral). */
    public static final int[] spiral = {0};
    public static final int[] innerRadius = {6};
    public static final ImBoolean removeOriginal = new ImBoolean(true);
    public static final int[] pivot = new int[3];
    /** false = el pivote es el centro de la selección. */
    public static boolean pivotSet;
    /** Lo marca la pantalla cada frame: hay que dibujar el pivote. */
    public static boolean showPivot;

    private Modify() {
    }

    /** Parámetros de una operación, ya congelados. */
    public static final class Spec {
        int mode, axis, type, count;
        int[] offset = new int[3];
        boolean relative;
        float angle;
        int[] translation = new int[3];
        int innerRadius;
        boolean removeOriginal;
        double[] pivot = new double[3];

        public int mode() {
            return mode;
        }

        /** Revolve y Twist reconstruyen la selección; con removeOriginal se borra lo que había. */
        public boolean consumesOriginal() {
            return (mode == REVOLVE || mode == TWIST) && removeOriginal;
        }

        public String label() {
            return MODES[mode];
        }
    }

    /** Congela la interfaz; el pivote por defecto es el centro de la selección. */
    public static Spec snapshot() {
        Spec s = new Spec();
        s.mode = mode.get();
        s.axis = axis.get();
        s.type = type.get();
        s.count = count[0];
        s.offset = offset.clone();
        s.relative = relative.get();
        s.angle = angle[0];
        s.translation = translation.clone();
        if (s.mode == REVOLVE) {
            s.translation[s.axis] = spiral[0];
        }
        s.innerRadius = innerRadius[0];
        s.removeOriginal = removeOriginal.get();
        for (int a = 0; a < 3; a++) {
            // Centro del bloque central de la selección.
            s.pivot[a] = (pivotSet ? pivot[a] : Math.floorDiv(Selection.min(a) + Selection.max(a), 2)) + 0.5;
        }
        return s;
    }

    /**
     * Calcula los bloques resultantes (posición -> id de estado, sin aire). c es la selección leída y (bx, by, bz)
     * su esquina mínima en el mundo. Devuelve null (con el motivo en EditEngine.status) si pasa del tope.
     */
    public static Map<Long, Integer> run(Spec s, Clipboard c, int bx, int by, int bz) {
        Map<Long, Integer> out = new HashMap<Long, Integer>();
        int[] org = {bx, by, bz};
        int[] size = {c.sx, c.sy, c.sz};
        switch (s.mode) {
            case TRANSLATE:
                return translate(s, c, org, size, out);
            case ROTATE:
                return rotate(s, c, org, size, out);
            case REVOLVE:
                return revolve(s, c, org, size, out);
            default:
                return twist(s, c, org, size, out);
        }
    }

    private static Map<Long, Integer> translate(Spec s, Clipboard c, int[] org, int[] size, Map<Long, Integer> out) {
        long solid = c.solidCount();
        if (solid * s.count > EditEngine.MAX_BLOCKS) {
            return tooBig(solid * s.count);
        }
        for (int k = 1; k <= s.count; k++) {
            int[] d = new int[3];
            for (int a = 0; a < 3; a++) {
                d[a] = s.offset[a] * k * (s.relative ? size[a] : 1);
            }
            for (int y = 0; y < c.sy; y++) {
                for (int z = 0; z < c.sz; z++) {
                    for (int x = 0; x < c.sx; x++) {
                        int id = c.states[c.index(x, y, z)];
                        if (id != Clipboard.AIR) {
                            out.put(key(org[0] + x + d[0], org[1] + y + d[1], org[2] + z + d[2]), id);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static Map<Long, Integer> rotate(Spec s, Clipboard c, int[] org, int[] size, Map<Long, Integer> out) {
        double step = s.type == 0 ? 360.0 / (s.count + 1) : s.angle;
        Map<Integer, Integer> cache = new HashMap<Integer, Integer>();
        for (int k = 1; k <= s.count; k++) {
            double th = step * k;
            int[] shift = new int[3];
            for (int a = 0; a < 3; a++) {
                shift[a] = (int) Math.round(s.translation[a] * (double) k / s.count);
            }
            // Caja de destino: las 8 esquinas del origen, giradas y desplazadas.
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int corner = 0; corner < 8; corner++) {
                double[] p = {org[0] + ((corner & 1) == 0 ? 0 : size[0]), org[1] + ((corner & 2) == 0 ? 0 : size[1]),
                    org[2] + ((corner & 4) == 0 ? 0 : size[2])};
                double[] q = turn(p, s.pivot, s.axis, th);
                for (int a = 0; a < 3; a++) {
                    lo[a] = Math.min(lo[a], q[a] + shift[a]);
                    hi[a] = Math.max(hi[a], q[a] + shift[a]);
                }
            }
            long cells = 1;
            for (int a = 0; a < 3; a++) {
                cells *= (long) (Math.ceil(hi[a]) - Math.floor(lo[a]) + 1);
            }
            if (cells > 8 * EditEngine.MAX_BLOCKS) {
                return tooBig(cells);
            }
            int quarter = s.axis == 1 ? Math.floorMod((int) Math.round(th / 90.0), 4) : 0;
            for (int y = (int) Math.floor(lo[1]); y <= (int) Math.ceil(hi[1]); y++) {
                for (int z = (int) Math.floor(lo[2]); z <= (int) Math.ceil(hi[2]); z++) {
                    for (int x = (int) Math.floor(lo[0]); x <= (int) Math.ceil(hi[0]); x++) {
                        double[] q = {x + 0.5 - shift[0], y + 0.5 - shift[1], z + 0.5 - shift[2]};
                        double[] src = turn(q, s.pivot, s.axis, -th);
                        int id = sample(c, org, (int) Math.floor(src[0]), (int) Math.floor(src[1]),
                                (int) Math.floor(src[2]));
                        if (id != Clipboard.AIR) {
                            out.put(key(x, y, z), spin(id, quarter, cache));
                        }
                    }
                }
            }
            if (out.size() > EditEngine.MAX_BLOCKS) {
                return tooBig(out.size());
            }
        }
        return out;
    }

    private static Map<Long, Integer> twist(Spec s, Clipboard c, int[] org, int[] size, Map<Long, Integer> out) {
        int a = s.axis, b = (a + 2) % 3, cc = (a + 1) % 3;
        double reach = 0;
        for (int corner = 0; corner < 4; corner++) {
            double db = org[b] + ((corner & 1) == 0 ? 0 : size[b]) - s.pivot[b];
            double dc = org[cc] + ((corner & 2) == 0 ? 0 : size[cc]) - s.pivot[cc];
            reach = Math.max(reach, Math.hypot(db, dc));
        }
        int r = (int) Math.ceil(reach) + 1;
        if (r > MAX_RADIUS) {
            return tooBig((long) r * r * 4 * size[a]);
        }
        Map<Integer, Integer> cache = new HashMap<Integer, Integer>();
        int[] cell = new int[3];
        for (int la = 0; la < size[a]; la++) {
            double phi = s.angle * (la + 0.5) / size[a];
            int quarter = s.axis == 1 ? Math.floorMod((int) Math.round(phi / 90.0), 4) : 0;
            int pb = (int) Math.floor(s.pivot[b]), pc = (int) Math.floor(s.pivot[cc]);
            for (int dc = -r; dc <= r; dc++) {
                for (int db = -r; db <= r; db++) {
                    cell[a] = org[a] + la;
                    cell[b] = pb + db;
                    cell[cc] = pc + dc;
                    double[] q = {cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5};
                    double[] src = turn(q, s.pivot, s.axis, -phi);
                    int id = sample(c, org, (int) Math.floor(src[0]), (int) Math.floor(src[1]),
                            (int) Math.floor(src[2]));
                    if (id != Clipboard.AIR) {
                        out.put(key(cell[0], cell[1], cell[2]), spin(id, quarter, cache));
                    }
                }
            }
            if (out.size() > EditEngine.MAX_BLOCKS) {
                return tooBig(out.size());
            }
        }
        return out;
    }

    /**
     * Revolve: la longitud de la selección a lo largo de b se enrolla como el ángulo, la profundidad a lo largo de
     * c como el radio (desde innerRadius) y a lo largo del eje a queda la altura. translation[eje] desplaza el
     * final del arco (una espiral).
     */
    private static Map<Long, Integer> revolve(Spec s, Clipboard c, int[] org, int[] size, Map<Long, Integer> out) {
        int a = s.axis, b = (a + 2) % 3, cc = (a + 1) % 3;
        double total = Math.max(1.0, Math.min(360.0, Math.abs(s.angle)));
        int outer = s.innerRadius + size[cc] + 1;
        if (outer > MAX_RADIUS) {
            return tooBig((long) outer * outer * 4 * size[a]);
        }
        int tr = s.translation[a];
        int lowA = org[a] + Math.min(0, tr), highA = org[a] + size[a] - 1 + Math.max(0, tr);
        int pb = (int) Math.floor(s.pivot[b]), pc = (int) Math.floor(s.pivot[cc]);
        int[] cell = new int[3];
        int[] idx = new int[3];
        for (int da = lowA; da <= highA; da++) {
            for (int dc = -outer; dc <= outer; dc++) {
                for (int db = -outer; db <= outer; db++) {
                    double ob = pb + db + 0.5 - s.pivot[b], oc = pc + dc + 0.5 - s.pivot[cc];
                    double r = Math.hypot(ob, oc);
                    int ic = (int) Math.floor(r - s.innerRadius);
                    if (ic < 0 || ic >= size[cc]) {
                        continue;
                    }
                    double theta = Math.toDegrees(Math.atan2(oc, ob));
                    if (theta < 0) {
                        theta += 360.0;
                    }
                    if (theta > total) {
                        continue;
                    }
                    int ib = Math.min(size[b] - 1, (int) Math.floor(theta / total * size[b]));
                    int ia = da - (int) Math.round(tr * theta / total) - org[a];
                    if (ia < 0 || ia >= size[a]) {
                        continue;
                    }
                    idx[a] = ia;
                    idx[b] = ib;
                    idx[cc] = ic;
                    int id = c.states[c.index(idx[0], idx[1], idx[2])];
                    if (id != Clipboard.AIR) {
                        cell[a] = da;
                        cell[b] = pb + db;
                        cell[cc] = pc + dc;
                        out.put(key(cell[0], cell[1], cell[2]), id);
                    }
                }
            }
            if (out.size() > EditEngine.MAX_BLOCKS) {
                return tooBig(out.size());
            }
        }
        return out;
    }

    // ---- Utilidades ---------------------------------------------------------------------------

    private static Map<Long, Integer> tooBig(long n) {
        EditEngine.status = "Modify: el resultado es demasiado grande (más de " + EditEngine.MAX_BLOCKS + " bloques; ~" + n
                + ")";
        return null;
    }

    private static long key(int x, int y, int z) {
        return new BlockPos(x, y, z).toLong();
    }

    /** El estado de la selección en la posición del mundo, o aire si queda fuera de ella. */
    private static int sample(Clipboard c, int[] org, int x, int y, int z) {
        int ix = x - org[0], iy = y - org[1], iz = z - org[2];
        if (ix < 0 || iy < 0 || iz < 0 || ix >= c.sx || iy >= c.sy || iz >= c.sz) {
            return Clipboard.AIR;
        }
        return c.states[c.index(ix, iy, iz)];
    }

    /**
     * Gira el punto theta grados alrededor del eje axis que pasa por pivot. El giro lleva el eje (axis+2)%3 hacia
     * el (axis+1)%3: con el eje Y es de X hacia Z, es decir, en sentido horario visto desde arriba (como el yaw).
     */
    static double[] turn(double[] p, double[] pivot, int axis, double theta) {
        int b = (axis + 2) % 3, c = (axis + 1) % 3;
        double rad = Math.toRadians(theta), cos = Math.cos(rad), sin = Math.sin(rad);
        double db = p[b] - pivot[b], dc = p[c] - pivot[c];
        double[] q = p.clone();
        q[b] = pivot[b] + db * cos - dc * sin;
        q[c] = pivot[c] + db * sin + dc * cos;
        return q;
    }

    /** Gira un estado de bloque quarters cuartos de vuelta en sentido horario (con caché). */
    private static int spin(int id, int quarters, Map<Integer, Integer> cache) {
        if (quarters == 0) {
            return id;
        }
        int ckey = id * 4 + quarters;
        Integer hit = cache.get(ckey);
        if (hit != null) {
            return hit;
        }
        IBlockState st = Block.getStateById(id);
        for (int q = 0; q < quarters; q++) {
            st = st.withRotation(Rotation.CLOCKWISE_90);
        }
        int result = Block.getStateId(st);
        cache.put(ckey, result);
        return result;
    }
}
