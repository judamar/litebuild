package dev.litbuild;

import java.util.Set;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import net.minecraft.util.math.BlockPos;

/** Dibuja en el mundo la selección y el bloque bajo el cursor. */
public final class WorldOverlay {
    private static final double EPS = 0.002;
    /** View > Show Selections. */
    public static final imgui.type.ImBoolean showSelections = new imgui.type.ImBoolean(true);

    private WorldOverlay() {
    }

    /** Llamar con las matrices del mundo activas (PostRenderListener.onPostRenderEntities). */
    public static void render() {
        boolean sym = Symmetry.enabled.get() && Symmetry.nodeSet;
        if (!Selection.active && Targeting.hover == null && !Placement.active && Ruler.points.isEmpty() && !sym
                && !BoxGizmo.visible && PathTool.nodes.isEmpty() && SlopeTool.target == null && !Modify.showPivot
                && !(Annotations.show.get() && (!Annotations.items.isEmpty() || Annotations.current() != null))
                && !BiomeView.active() && !Modelling.hasPoints() && BuilderTools.preview().isEmpty() && !BlockHighlights.any()) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2f);

        GL11.glPushMatrix();
        GL11.glTranslated(-Targeting.camX, -Targeting.camY, -Targeting.camZ);
        if (Selection.active && showSelections.get()) {
            if (Selection.mask == null) {
                box(Selection.min(0), Selection.min(1), Selection.min(2),
                        Selection.max(0) + 1, Selection.max(1) + 1, Selection.max(2) + 1, 0.2f, 0.75f, 1f);
            } else {
                drawMask(Selection.mask);
            }
        }
        if (Placement.active && Placement.shape != null) {
            int x = Placement.pos[0];
            int y = Placement.pos[1];
            int z = Placement.pos[2];
            box(x, y, z, x + Placement.shape.sx, y + Placement.shape.sy, z + Placement.shape.sz, 1f, 0.6f, 0.1f);
        }
        if (!Ruler.points.isEmpty() && Ruler.show.get()) {
            ruler();
        }
        if (Modify.showPivot) {
            int px = Modify.pivot[0], py = Modify.pivot[1], pz = Modify.pivot[2];
            box(px, py, pz, px + 1, py + 1, pz + 1, 1f, 1f, 0.2f);
        }
        if (SlopeTool.target != null) {
            BlockPos t = SlopeTool.target;
            box(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1, 1f, 0.3f, 1f);
        }
        if (sym) {
            symmetry();
        }
        if (BoxGizmo.visible) {
            gizmo();
        }
        if (!PathTool.nodes.isEmpty()) {
            path();
        }
        if (BiomeView.active()) {
            BiomeView.render();
        }
        for (int[] b : BuilderTools.preview()) {
            box(b[0], b[1], b[2], b[3], b[4], b[5], 1f, 0.6f, 0.1f);
        }
        if (BlockHighlights.any()) {
            blockHighlights();
        }
        if (Annotations.show.get()) {
            annotations();
        }
        if (Modelling.hasPoints()) {
            modelling();
        }
        if (Brush.preview && Targeting.hover != null) {
            int r = Brush.radius[0];
            for (BlockPos h : Symmetry.expand(Targeting.hover)) {
                box(h.getX() - r, h.getY() - r, h.getZ() - r, h.getX() + r + 1, h.getY() + r + 1, h.getZ() + r + 1,
                        0.4f, 1f, 0.4f);
            }
        }
        if (Targeting.hover != null) {
            int x = Targeting.hover.getX();
            int y = Targeting.hover.getY();
            int z = Targeting.hover.getZ();
            box(x, y, z, x + 1, y + 1, z + 1, 1f, 1f, 1f);
        }
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** Líneas y contornos de las anotaciones (los textos los dibuja ImGui). */
    private static void annotations() {
        Annotations.ensureLoaded();
        for (Annotations.Item it : Annotations.items) {
            annotation(it);
        }
        if (Annotations.current() != null) {
            annotation(Annotations.current());
        }
    }

    /** Structure void (cian) y barreras (rojo) cercanos, de Edit Block Attributes. */
    private static void blockHighlights() {
        if (BuilderMode.showStructureVoid.get()) {
            for (BlockPos p : BlockHighlights.voids) {
                box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0.3f, 0.9f, 1f);
            }
        }
        if (BuilderMode.showBarriers.get()) {
            for (BlockPos p : BlockHighlights.barriers) {
                box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 1f, 0.25f, 0.2f);
            }
        }
    }

    /** Los puntos de Modelling (cubitos cian), cada fila como una polilínea y las filas contiguas unidas punto a punto. */
    private static void modelling() {
        java.util.List<java.util.List<double[]>> rows = Modelling.rows;
        for (int r = 0; r < rows.size(); r++) {
            boolean current = r == Modelling.currentRow;
            float cr = current ? 0.3f : 0.2f, cg = current ? 1f : 0.7f, cb = current ? 1f : 0.7f;
            for (double[] p : rows.get(r)) {
                box(p[0] - 0.5 + 0.2, p[1] - 0.5 + 0.2, p[2] - 0.5 + 0.2, p[0] + 0.5 - 0.2, p[1] + 0.5 - 0.2, p[2] + 0.5 - 0.2,
                        cr, cg, cb);
            }
            for (int pass = 0; pass < 2; pass++) {
                GL11.glDepthFunc(pass == 0 ? GL11.GL_LEQUAL : GL11.GL_GREATER);
                GL11.glColor4f(cr, cg, cb, pass == 0 ? 0.95f : 0.3f);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (double[] p : rows.get(r)) {
                    GL11.glVertex3d(p[0], p[1], p[2]);
                }
                GL11.glEnd();
                if (r + 1 < rows.size()) {
                    GL11.glColor4f(1f, 0.8f, 0.3f, pass == 0 ? 0.5f : 0.15f);
                    GL11.glBegin(GL11.GL_LINES);
                    int n = Math.min(rows.get(r).size(), rows.get(r + 1).size());
                    for (int i = 0; i < n; i++) {
                        double[] a = rows.get(r).get(i), b = rows.get(r + 1).get(i);
                        GL11.glVertex3d(a[0], a[1], a[2]);
                        GL11.glVertex3d(b[0], b[1], b[2]);
                    }
                    GL11.glEnd();
                }
            }
        }
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    /** Una imagen de anotación: un cuadro texturizado pegado a la superficie, de cara a su normal. */
    private static void image(Annotations.Item it) {
        if (it.p.length < 6) {
            return;
        }
        ImageCache.Entry e = ImageCache.get(it.text);
        int tex = ImageCache.texture(e);
        if (tex == 0) {
            return;
        }
        double nx = it.p[3], ny = it.p[4], nz = it.p[5];
        // right = arriba del mundo x normal; en suelos y techos, el eje X
        double rx, ry, rz;
        if (Math.abs(ny) > 0.9) {
            rx = 1;
            ry = 0;
            rz = 0;
        } else {
            rx = 1 * nz - 0 * ny;
            ry = 0 * nx - 0 * nz;
            rz = 0 * ny - 1 * nx;
            double l = Math.sqrt(rx * rx + ry * ry + rz * rz);
            rx /= l;
            ry /= l;
            rz /= l;
        }
        // up = normal x right
        double ux = ny * rz - nz * ry, uy = nz * rx - nx * rz, uz = nx * ry - ny * rx;
        double w = it.width / 2, h = it.width * e.h / Math.max(1, e.w) / 2;
        double cx = it.p[0], cy = it.p[1], cz = it.p[2];
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3d(cx - rx * w - ux * h, cy - ry * w - uy * h, cz - rz * w - uz * h);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3d(cx + rx * w - ux * h, cy + ry * w - uy * h, cz + rz * w - uz * h);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3d(cx + rx * w + ux * h, cy + ry * w + uy * h, cz + rz * w + uz * h);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3d(cx - rx * w + ux * h, cy - ry * w + uy * h, cz - rz * w + uz * h);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private static void annotation(Annotations.Item it) {
        if (it.type == Annotations.IMAGE) {
            image(it);
            return;
        }
        float r = ((it.color >> 16) & 255) / 255f, g = ((it.color >> 8) & 255) / 255f, b = (it.color & 255) / 255f;
        int n = it.count();
        if (it.type == Annotations.LINE) {
            GL11.glLineWidth(it.width);
            for (int pass = 0; pass < 2; pass++) {
                GL11.glDepthFunc(pass == 0 ? GL11.GL_LEQUAL : GL11.GL_GREATER);
                GL11.glColor4f(r, g, b, pass == 0 ? 1f : 0.3f);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (int i = 0; i < n; i++) {
                    GL11.glVertex3d(it.p[i * 3], it.p[i * 3 + 1], it.p[i * 3 + 2]);
                }
                GL11.glEnd();
            }
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glLineWidth(2f);
        } else if (it.type == Annotations.OUTLINE) {
            for (int pass = 0; pass < 2; pass++) {
                GL11.glDepthFunc(pass == 0 ? GL11.GL_LEQUAL : GL11.GL_GREATER);
                GL11.glColor4f(r, g, b, pass == 0 ? 0.95f : 0.25f);
                for (int i = 0; i < n; i++) {
                    double x = it.p[i * 3], y = it.p[i * 3 + 1], z = it.p[i * 3 + 2];
                    edges(x - EPS, y - EPS, z - EPS, x + 1 + EPS, y + 1 + EPS, z + 1 + EPS);
                }
            }
            GL11.glDepthFunc(GL11.GL_LEQUAL);
        }
    }

    /** Los nodos del camino (cubitos cian) y la curva que los une. */
    private static void path() {
        for (BlockPos p : PathTool.nodes) {
            box(p.getX() + 0.25, p.getY() + 0.25, p.getZ() + 0.25, p.getX() + 0.75, p.getY() + 0.75, p.getZ() + 0.75,
                    0.3f, 1f, 1f);
        }
        java.util.List<double[]> pts = PathTool.samples();
        if (pts.size() < 2) {
            return;
        }
        for (int pass = 0; pass < 2; pass++) {
            GL11.glDepthFunc(pass == 0 ? GL11.GL_LEQUAL : GL11.GL_GREATER);
            GL11.glColor4f(0.3f, 1f, 1f, pass == 0 ? 0.95f : 0.3f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (double[] q : pts) {
                GL11.glVertex3d(q[0], q[1], q[2]);
            }
            GL11.glEnd();
        }
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    /** Los seis manejadores de Box Select: cubitos de color por eje (amarillo si están bajo el cursor). */
    private static void gizmo() {
        float[][] axis = {{1f, 0.3f, 0.3f}, {0.3f, 1f, 0.3f}, {0.4f, 0.55f, 1f}};
        for (int f = 0; f < 6; f++) {
            boolean hot = f == BoxGizmo.hovered || f == BoxGizmo.active;
            float[] c = hot ? new float[] {1f, 0.9f, 0.2f} : axis[f / 2];
            double[] p = BoxGizmo.center[f];
            int[] d = BoxGizmo.DIR[f];
            double h = BoxGizmo.half * (hot ? 1.3 : 1.0);
            double stem = BoxGizmo.half * 1.6;
            for (int pass = 0; pass < 2; pass++) {
                // Primero lo visible; luego, más tenue, lo que tapa el terreno.
                GL11.glDepthFunc(pass == 0 ? GL11.GL_LEQUAL : GL11.GL_GREATER);
                float alpha = pass == 0 ? 0.92f : 0.35f;
                GL11.glColor4f(c[0], c[1], c[2], alpha);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex3d(p[0] - d[0] * stem, p[1] - d[1] * stem, p[2] - d[2] * stem);
                GL11.glVertex3d(p[0], p[1], p[2]);
                GL11.glEnd();
                solid(p[0], p[1], p[2], h, c[0], c[1], c[2], alpha);
            }
        }
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    /** Cubo macizo de medio lado h con una luz distinta por cara. */
    private static void solid(double x, double y, double z, double h, float r, float g, float b, float a) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x - h, y + h, z - h); GL11.glVertex3d(x - h, y + h, z + h);
        GL11.glVertex3d(x + h, y + h, z + h); GL11.glVertex3d(x + h, y + h, z - h);
        GL11.glColor4f(r * 0.5f, g * 0.5f, b * 0.5f, a);
        GL11.glVertex3d(x - h, y - h, z - h); GL11.glVertex3d(x + h, y - h, z - h);
        GL11.glVertex3d(x + h, y - h, z + h); GL11.glVertex3d(x - h, y - h, z + h);
        GL11.glColor4f(r * 0.75f, g * 0.75f, b * 0.75f, a);
        GL11.glVertex3d(x + h, y - h, z - h); GL11.glVertex3d(x + h, y + h, z - h);
        GL11.glVertex3d(x + h, y + h, z + h); GL11.glVertex3d(x + h, y - h, z + h);
        GL11.glVertex3d(x - h, y - h, z - h); GL11.glVertex3d(x - h, y - h, z + h);
        GL11.glVertex3d(x - h, y + h, z + h); GL11.glVertex3d(x - h, y + h, z - h);
        GL11.glColor4f(r * 0.6f, g * 0.6f, b * 0.6f, a);
        GL11.glVertex3d(x - h, y - h, z + h); GL11.glVertex3d(x + h, y - h, z + h);
        GL11.glVertex3d(x + h, y + h, z + h); GL11.glVertex3d(x - h, y + h, z + h);
        GL11.glVertex3d(x - h, y - h, z - h); GL11.glVertex3d(x - h, y + h, z - h);
        GL11.glVertex3d(x + h, y + h, z - h); GL11.glVertex3d(x + h, y - h, z - h);
        GL11.glEnd();
    }

    /** El nodo de simetría (cubito amarillo) y un plano translúcido por cada espejo activo. */
    private static void symmetry() {
        double nx = Symmetry.node[0] / 2.0, ny = Symmetry.node[1] / 2.0, nz = Symmetry.node[2] / 2.0;
        double e = 24;
        box(nx - 0.15, ny - 0.15, nz - 0.15, nx + 0.15, ny + 0.15, nz + 0.15, 1f, 0.85f, 0.2f);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        if (Symmetry.mirrorX.get()) {
            plane(1f, 0.35f, 0.35f, nx, ny - e, nz - e, nx, ny - e, nz + e, nx, ny + e, nz + e, nx, ny + e, nz - e);
        }
        if (Symmetry.mirrorY.get()) {
            plane(0.4f, 1f, 0.4f, nx - e, ny, nz - e, nx + e, ny, nz - e, nx + e, ny, nz + e, nx - e, ny, nz + e);
        }
        if (Symmetry.mirrorZ.get()) {
            plane(0.4f, 0.55f, 1f, nx - e, ny - e, nz, nx + e, ny - e, nz, nx + e, ny + e, nz, nx - e, ny + e, nz);
        }
    }

    private static void plane(float r, float g, float b, double x1, double y1, double z1, double x2, double y2,
            double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        GL11.glColor4f(r, g, b, 0.10f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x3, y3, z3);
        GL11.glVertex3d(x4, y4, z4);
        GL11.glEnd();
        GL11.glColor4f(r, g, b, 0.55f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x3, y3, z3);
        GL11.glVertex3d(x4, y4, z4);
        GL11.glEnd();
    }

    /** Puntos de la regla (cubitos amarillos) y la línea que los une. */
    private static void ruler() {
        for (BlockPos p : Ruler.points) {
            box(p.getX() + 0.3, p.getY() + 0.3, p.getZ() + 0.3, p.getX() + 0.7, p.getY() + 0.7, p.getZ() + 0.7,
                    1f, 0.9f, 0.2f);
        }
        if (Ruler.points.size() < 2) {
            return;
        }
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColor4f(1f, 0.9f, 0.2f, 0.95f);
        line();
        GL11.glDepthFunc(GL11.GL_GREATER);
        GL11.glColor4f(1f, 0.9f, 0.2f, 0.3f);
        line();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    private static void line() {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (BlockPos p : Ruler.points) {
            GL11.glVertex3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        }
        GL11.glEnd();
    }

    // Máscara de Magic Select: se compila una vez en una display list (solo las caras expuestas).
    private static int list;
    private static Set<Long> listed;

    private static void drawMask(Set<Long> mask) {
        if (mask != listed) {
            if (list == 0) {
                list = GL11.glGenLists(1);
            }
            GL11.glNewList(list, GL11.GL_COMPILE);
            buildMask(mask);
            GL11.glEndList();
            listed = mask;
        }
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glCallList(list);
    }

    private static void buildMask(Set<Long> mask) {
        GL11.glColor4f(0.2f, 0.75f, 1f, 0.22f);
        GL11.glBegin(GL11.GL_QUADS);
        for (Long k : mask) {
            BlockPos p = BlockPos.fromLong(k);
            for (int d = 0; d < 6; d++) {
                if (exposed(mask, p, d)) {
                    face(p.getX(), p.getY(), p.getZ(), d);
                }
            }
        }
        GL11.glEnd();
        GL11.glColor4f(0.2f, 0.75f, 1f, 0.9f);
        for (Long k : mask) {
            BlockPos p = BlockPos.fromLong(k);
            for (int d = 0; d < 6; d++) {
                if (exposed(mask, p, d)) {
                    GL11.glBegin(GL11.GL_LINE_LOOP);
                    face(p.getX(), p.getY(), p.getZ(), d);
                    GL11.glEnd();
                }
            }
        }
    }

    /** ¿La cara d (0 +x, 1 -x, 2 +y, 3 -y, 4 +z, 5 -z) del bloque no tiene otro bloque de la máscara delante? */
    private static boolean exposed(Set<Long> mask, BlockPos p, int d) {
        int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
        int dy = d == 2 ? 1 : d == 3 ? -1 : 0;
        int dz = d == 4 ? 1 : d == 5 ? -1 : 0;
        return !mask.contains(Selection.key(p.getX() + dx, p.getY() + dy, p.getZ() + dz));
    }

    /** Emite los 4 vértices de la cara d del bloque (x, y, z), un poco separada del bloque. */
    private static void face(int x, int y, int z, int d) {
        double lo = -EPS, hi = 1 + EPS;
        switch (d) {
            case 0:
                v(x + hi, y, z); v(x + hi, y + 1, z); v(x + hi, y + 1, z + 1); v(x + hi, y, z + 1);
                break;
            case 1:
                v(x + lo, y, z); v(x + lo, y, z + 1); v(x + lo, y + 1, z + 1); v(x + lo, y + 1, z);
                break;
            case 2:
                v(x, y + hi, z); v(x, y + hi, z + 1); v(x + 1, y + hi, z + 1); v(x + 1, y + hi, z);
                break;
            case 3:
                v(x, y + lo, z); v(x + 1, y + lo, z); v(x + 1, y + lo, z + 1); v(x, y + lo, z + 1);
                break;
            case 4:
                v(x, y, z + hi); v(x + 1, y, z + hi); v(x + 1, y + 1, z + hi); v(x, y + 1, z + hi);
                break;
            default:
                v(x, y, z + lo); v(x, y + 1, z + lo); v(x + 1, y + 1, z + lo); v(x + 1, y, z + lo);
                break;
        }
    }

    private static void v(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }

    private static void box(double ax, double ay, double az, double bx, double by, double bz,
            float r, float g, float b) {
        double x1 = ax - EPS, y1 = ay - EPS, z1 = az - EPS;
        double x2 = bx + EPS, y2 = by + EPS, z2 = bz + EPS;

        // Caras translúcidas y aristas nítidas donde la caja está por delante de la geometría.
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColor4f(r, g, b, 0.10f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y1, z2);
        GL11.glEnd();

        GL11.glColor4f(r, g, b, 0.95f);
        edges(x1, y1, z1, x2, y2, z2);

        // Aristas tenues donde queda tapada por el terreno.
        GL11.glDepthFunc(GL11.GL_GREATER);
        GL11.glColor4f(r, g, b, 0.25f);
        edges(x1, y1, z1, x2, y2, z2);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    private static void edges(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2);
        GL11.glEnd();
    }
}
