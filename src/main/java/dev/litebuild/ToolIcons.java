package dev.litebuild;

import imgui.ImDrawList;

/**
 * Iconos vectoriales de las herramientas y las formas, dibujados con primitivas de ImGui para que
 * escalen con el tamaño de la interfaz y respeten el tema. Todo se define en un cuadrado unidad.
 */
public final class ToolIcons {
    // Estado del icono que se está dibujando (la interfaz es de un solo hilo).
    private static ImDrawList dl;
    private static float ox, oy, sz, th;
    private static int col, acc;

    private ToolIcons() {
    }

    /** Dibuja el icono name en el cuadrado de lado size con esquina (x, y). */
    public static void draw(ImDrawList list, String name, float x, float y, float size, int color, int accent) {
        dl = list;
        ox = x;
        oy = y;
        sz = size;
        col = color;
        acc = accent;
        th = Math.max(1.5f, size / 13f);
        switch (name) {
            case "Box Select":
                corner(.16f, .16f, 1, 1);
                corner(.84f, .16f, -1, 1);
                corner(.16f, .84f, 1, -1);
                corner(.84f, .84f, -1, -1);
                dot(.5f, .5f, .07f, acc);
                break;
            case "Magic Select":
                line(.18f, .86f, .6f, .44f, col, th * 1.4f);
                line(.72f, .1f, .72f, .5f, acc, th);
                line(.52f, .3f, .92f, .3f, acc, th);
                line(.58f, .16f, .86f, .44f, acc, th * .8f);
                line(.86f, .16f, .58f, .44f, acc, th * .8f);
                break;
            case "Painter":
                line(.2f, .86f, .5f, .56f, col, th * 1.8f);
                line(.48f, .58f, .6f, .46f, col, th * 3f);
                dot(.68f, .36f, .16f, acc);
                break;
            case "Noise Painter":
                float[][] d = {{.2f, .2f, .07f}, {.5f, .14f, .05f}, {.8f, .24f, .08f}, {.3f, .5f, .05f},
                    {.62f, .5f, .09f}, {.86f, .62f, .05f}, {.18f, .8f, .08f}, {.5f, .86f, .06f}, {.8f, .86f, .04f}};
                for (int i = 0; i < d.length; i++) {
                    dot(d[i][0], d[i][1], d[i][2], i % 2 == 0 ? acc : col);
                }
                break;
            case "Freehand Draw":
                line(.3f, .7f, .8f, .2f, col, th * 2.4f);
                tri(.14f, .88f, .24f, .62f, .4f, .78f, acc);
                for (int i = 0; i < 6; i++) {
                    line(.46f + i * .08f, .9f - (i % 2) * .06f, .54f + i * .08f, .84f + (i % 2) * .0f, col, th * .8f);
                }
                break;
            case "Shape":
                circle(.38f, .4f, .24f, col);
                rect(.42f, .46f, .84f, .88f, acc);
                break;
            case "Smooth":
                for (int i = 0; i < 14; i++) {
                    float u1 = .1f + .8f * i / 14f, u2 = .1f + .8f * (i + 1) / 14f;
                    float a1 = .24f * (1f - i / 14f), a2 = .24f * (1f - (i + 1) / 14f);
                    line(u1, .5f + a1 * (float) Math.sin(i * 1.1), u2, .5f + a2 * (float) Math.sin((i + 1) * 1.1), col, th * 1.2f);
                }
                line(.1f, .84f, .9f, .84f, acc, th);
                break;
            case "Melt":
                tri(.5f, .1f, .28f, .56f, .72f, .56f, acc);
                dot(.5f, .64f, .23f, acc);
                dot(.42f, .6f, .06f, col);
                break;
            case "Weld":
                circle(.5f, .5f, .34f, col);
                line(.5f, .3f, .5f, .7f, acc, th * 1.4f);
                line(.3f, .5f, .7f, .5f, acc, th * 1.4f);
                line(.82f, .12f, .92f, .02f, acc, th);
                break;
            case "Extrude":
                rect(.12f, .46f, .58f, .88f, col);
                line(.4f, .62f, .86f, .16f, acc, th * 1.4f);
                line(.86f, .16f, .6f, .16f, acc, th * 1.4f);
                line(.86f, .16f, .86f, .42f, acc, th * 1.4f);
                break;
            case "Ruler":
                quad(.08f, .66f, .66f, .08f, .92f, .34f, .34f, .92f, col);
                for (int i = 1; i <= 4; i++) {
                    float t = i / 5f;
                    float ux = .08f + (.66f - .08f) * t, uy = .66f + (.08f - .66f) * t;
                    line(ux + .03f, uy + .03f, ux + .12f, uy + .12f, acc, th * .9f);
                }
                break;
            case "Elevation":
                line(.08f, .84f, .36f, .3f, col, th * 1.2f);
                line(.36f, .3f, .54f, .6f, col, th * 1.2f);
                line(.54f, .6f, .68f, .42f, col, th * 1.2f);
                line(.68f, .42f, .92f, .84f, col, th * 1.2f);
                line(.08f, .84f, .92f, .84f, col, th);
                line(.8f, .34f, .8f, .08f, acc, th * 1.3f);
                line(.8f, .08f, .72f, .18f, acc, th * 1.3f);
                line(.8f, .08f, .88f, .18f, acc, th * 1.3f);
                break;
            case "Distort":
                for (int r = 0; r < 3; r++) {
                    for (int i = 0; i < 12; i++) {
                        float u1 = .1f + .8f * i / 12f, u2 = .1f + .8f * (i + 1) / 12f;
                        float v0 = .26f + r * .24f;
                        float w1 = .07f * (float) Math.sin(i * 0.9 + r * 1.3), w2 = .07f * (float) Math.sin((i + 1) * 0.9 + r * 1.3);
                        line(u1, v0 + w1, u2, v0 + w2, r == 1 ? acc : col, th);
                    }
                }
                break;
            case "Text":
                line(.18f, .2f, .82f, .2f, col, th * 1.6f);
                line(.5f, .2f, .5f, .84f, col, th * 1.6f);
                line(.3f, .9f, .7f, .9f, acc, th);
                break;
            case "Symmetry":
                for (int i = 0; i < 5; i++) {
                    line(.5f, .08f + i * .2f, .5f, .18f + i * .2f, acc, th * 1.2f);
                }
                tri(.4f, .3f, .4f, .74f, .1f, .74f, col);
                line(.6f, .3f, .6f, .74f, col, th);
                line(.6f, .74f, .9f, .74f, col, th);
                line(.6f, .3f, .9f, .74f, col, th);
                break;
            case "Script Brush":
                line(.34f, .24f, .12f, .5f, col, th * 1.3f);
                line(.12f, .5f, .34f, .76f, col, th * 1.3f);
                line(.66f, .24f, .88f, .5f, col, th * 1.3f);
                line(.88f, .5f, .66f, .76f, col, th * 1.3f);
                line(.56f, .18f, .44f, .82f, acc, th * 1.3f);
                break;
            case "Gradient Painter":
                for (int i = 0; i < 5; i++) {
                    float u = .14f + i * .17f;
                    quad(u, .2f, u + .15f, .2f, u + .15f, .8f, u, .8f, i % 2 == 0 ? col : acc);
                }
                line(.14f, .9f, .86f, .9f, acc, th);
                break;
            case "Freehand Select":
                for (int i = 0; i < 14; i++) {
                    float t1 = i / 14f, t2 = (i + 1) / 14f;
                    float a1 = (float) (t1 * Math.PI * 2), a2 = (float) (t2 * Math.PI * 2);
                    if (i % 2 == 0) {
                        line(.5f + .34f * (float) Math.cos(a1), .5f + .3f * (float) Math.sin(a1),
                                .5f + .34f * (float) Math.cos(a2), .5f + .3f * (float) Math.sin(a2), col, th * 1.3f);
                    }
                }
                line(.4f, .5f, .6f, .5f, acc, th);
                line(.5f, .4f, .5f, .6f, acc, th);
                break;
            case "Stamp":
                rect(.16f, .2f, .46f, .5f, col);
                rect(.54f, .5f, .84f, .8f, col);
                dot(.31f, .35f, .06f, acc);
                dot(.69f, .65f, .06f, acc);
                line(.46f, .5f, .54f, .5f, acc, th);
                break;
            case "Lasso Select":
                for (int i = 0; i < 16; i++) {
                    float a1 = (float) (i / 16.0 * Math.PI * 2), a2 = (float) ((i + 1) / 16.0 * Math.PI * 2);
                    line(.5f + .36f * (float) Math.cos(a1), .42f + .26f * (float) Math.sin(a1),
                            .5f + .36f * (float) Math.cos(a2), .42f + .26f * (float) Math.sin(a2), col, th * 1.3f);
                }
                line(.5f, .68f, .4f, .92f, acc, th);
                dot(.4f, .92f, .05f, acc);
                break;
            case "Sculpt Draw":
                for (int i = 0; i < 12; i++) {
                    float t1 = i / 12f, t2 = (i + 1) / 12f;
                    line(.1f + .8f * t1, .8f - .55f * (float) Math.exp(-Math.pow((t1 - .5) * 4.2, 2)),
                            .1f + .8f * t2, .8f - .55f * (float) Math.exp(-Math.pow((t2 - .5) * 4.2, 2)), col, th * 1.4f);
                }
                line(.1f, .9f, .9f, .9f, acc, th);
                break;
            case "Slope":
                tri(.12f, .84f, .88f, .84f, .88f, .24f, col);
                line(.12f, .84f, .88f, .24f, acc, th * 1.3f);
                dot(.88f, .24f, .07f, acc);
                break;
            case "Modify":
                rect(.12f, .12f, .52f, .52f, col);
                rect(.48f, .48f, .88f, .88f, col);
                line(.3f, .7f, .7f, .3f, acc, th * 1.2f);
                tri(.7f, .3f, .5f, .32f, .68f, .5f, acc);
                break;
            case "Biome Painter":
                quad(.16f, .84f, .22f, .36f, .8f, .16f, .7f, .66f, col);
                line(.16f, .84f, .62f, .38f, acc, th * 1.3f);
                dot(.82f, .84f, .06f, acc);
                break;
            case "Annotation":
                line(.18f, .78f, .5f, .22f, col, th * 1.2f);
                line(.5f, .22f, .82f, .62f, col, th * 1.2f);
                tri(.12f, .88f, .3f, .76f, .22f, .66f, acc);
                dot(.82f, .62f, .07f, acc);
                break;
            case "Fluid Ball":
                dot(.5f, .62f, .24f, col);
                tri(.5f, .12f, .32f, .42f, .68f, .42f, acc);
                break;
            case "Modelling":
                line(.15f, .75f, .5f, .2f, col, th);
                line(.5f, .2f, .85f, .75f, col, th);
                line(.15f, .75f, .85f, .75f, col, th);
                line(.5f, .2f, .5f, .55f, acc, th);
                dot(.15f, .75f, .07f, acc);
                dot(.5f, .2f, .07f, acc);
                dot(.85f, .75f, .07f, acc);
                dot(.5f, .55f, .07f, col);
                break;
            case "Roughen":
                for (int i = 0; i < 8; i++) {
                    float u1 = .1f + .8f * i / 8f, u2 = .1f + .8f * (i + 1) / 8f;
                    float v1 = i % 2 == 0 ? .66f : .3f, v2 = i % 2 == 0 ? .3f : .66f;
                    line(u1, v1, u2, v2, col, th * 1.2f);
                }
                line(.1f, .86f, .9f, .86f, acc, th);
                break;
            case "Shatter":
                rect(.14f, .14f, .86f, .86f, col);
                line(.5f, .14f, .42f, .4f, acc, th * 1.2f);
                line(.42f, .4f, .58f, .56f, acc, th * 1.2f);
                line(.58f, .56f, .46f, .86f, acc, th * 1.2f);
                line(.42f, .4f, .14f, .46f, acc, th);
                line(.58f, .56f, .86f, .5f, acc, th);
                break;
            case "Rock":
                line(.14f, .66f, .26f, .36f, col, th * 1.2f);
                line(.26f, .36f, .5f, .2f, col, th * 1.2f);
                line(.5f, .2f, .76f, .3f, col, th * 1.2f);
                line(.76f, .3f, .88f, .6f, col, th * 1.2f);
                line(.88f, .6f, .7f, .84f, col, th * 1.2f);
                line(.7f, .84f, .3f, .86f, col, th * 1.2f);
                line(.3f, .86f, .14f, .66f, col, th * 1.2f);
                line(.26f, .36f, .42f, .6f, acc, th);
                line(.42f, .6f, .7f, .84f, acc, th);
                break;
            case "Floodfill":
                quad(.16f, .44f, .5f, .16f, .78f, .46f, .44f, .74f, col);
                tri(.84f, .6f, .74f, .8f, .94f, .8f, acc);
                dot(.84f, .82f, .1f, acc);
                line(.34f, .3f, .22f, .1f, col, th);
                break;
            case "Path":
                dot(.14f, .8f, .08f, acc);
                dot(.4f, .26f, .08f, acc);
                dot(.64f, .7f, .08f, acc);
                dot(.88f, .2f, .08f, acc);
                for (int i = 0; i < 12; i++) {
                    float t1 = i / 12f, t2 = (i + 1) / 12f;
                    line(.14f + .74f * t1, .8f - .6f * t1 - .3f * (float) Math.sin(t1 * Math.PI * 2),
                            .14f + .74f * t2, .8f - .6f * t2 - .3f * (float) Math.sin(t2 * Math.PI * 2), col, th);
                }
                break;
            case "Sphere":
                circle(.5f, .5f, .38f, col);
                ellipse(.5f, .5f, .38f, .12f, col);
                dot(.38f, .34f, .06f, acc);
                break;
            case "Cuboid":
            case "Cube":
                cube(col, acc);
                break;
            case "Cylinder":
                ellipse(.5f, .22f, .3f, .11f, col);
                line(.2f, .22f, .2f, .78f, col, th);
                line(.8f, .22f, .8f, .78f, col, th);
                arc(.5f, .78f, .3f, .11f, 0, 180, col);
                break;
            case "Cone":
                line(.5f, .1f, .2f, .78f, col, th);
                line(.5f, .1f, .8f, .78f, col, th);
                arc(.5f, .78f, .3f, .11f, 0, 180, col);
                ellipseDot(.5f, .78f, .3f, .11f);
                break;
            case "Pyramid":
                line(.5f, .1f, .14f, .72f, col, th);
                line(.5f, .1f, .86f, .72f, col, th);
                line(.5f, .1f, .5f, .9f, acc, th);
                line(.14f, .72f, .5f, .9f, col, th);
                line(.86f, .72f, .5f, .9f, col, th);
                line(.14f, .72f, .86f, .72f, col, th * .6f);
                break;
            case "Octahedron":
                quad(.5f, .08f, .9f, .5f, .5f, .92f, .1f, .5f, col);
                line(.1f, .5f, .9f, .5f, col, th * .8f);
                line(.1f, .5f, .5f, .66f, acc, th * .8f);
                line(.9f, .5f, .5f, .66f, acc, th * .8f);
                line(.5f, .08f, .5f, .66f, acc, th * .8f);
                break;
            case "Disk":
                ellipse(.5f, .5f, .4f, .16f, col);
                ellipse(.5f, .5f, .22f, .08f, acc);
                break;
            default:
                rect(.2f, .2f, .8f, .8f, col);
                break;
        }
    }

    // ---- Primitivas en coordenadas unidad -------------------------------------------------------

    private static float X(float u) {
        return ox + u * sz;
    }

    private static float Y(float v) {
        return oy + v * sz;
    }

    private static void line(float u1, float v1, float u2, float v2, int c, float t) {
        dl.addLine(X(u1), Y(v1), X(u2), Y(v2), c, t);
    }

    private static void corner(float u, float v, int dx, int dy) {
        line(u, v, u + dx * .24f, v, col, th * 1.2f);
        line(u, v, u, v + dy * .24f, col, th * 1.2f);
    }

    private static void dot(float u, float v, float r, int c) {
        dl.addCircleFilled(X(u), Y(v), r * sz, c, 16);
    }

    private static void circle(float u, float v, float r, int c) {
        dl.addCircle(X(u), Y(v), r * sz, c, 24, th);
    }

    private static void rect(float u1, float v1, float u2, float v2, int c) {
        dl.addRect(X(u1), Y(v1), X(u2), Y(v2), c, 0f, 0, th);
    }

    private static void tri(float u1, float v1, float u2, float v2, float u3, float v3, int c) {
        dl.addTriangleFilled(X(u1), Y(v1), X(u2), Y(v2), X(u3), Y(v3), c);
    }

    private static void quad(float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int c) {
        dl.addQuad(X(u1), Y(v1), X(u2), Y(v2), X(u3), Y(v3), X(u4), Y(v4), c, th);
    }

    /** Elipse de contorno con segmentos de línea. */
    private static void ellipse(float u, float v, float ru, float rv, int c) {
        arc(u, v, ru, rv, 0, 360, c);
    }

    private static void arc(float u, float v, float ru, float rv, int from, int to, int c) {
        int steps = 24;
        float px = 0, py = 0;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(from + (to - from) * i / (double) steps);
            float x = u + ru * (float) Math.cos(a), y = v + rv * (float) Math.sin(a);
            if (i > 0) {
                line(px, py, x, y, c, th);
            }
            px = x;
            py = y;
        }
    }

    private static void ellipseDot(float u, float v, float ru, float rv) {
        arc(u, v, ru, rv, 180, 360, (acc & 0x00FFFFFF) | 0x66000000);
    }

    /** Cubo en perspectiva isométrica con la cara superior resaltada. */
    private static void cube(int outline, int accent) {
        float[] p = {.5f, .08f, .88f, .3f, .88f, .7f, .5f, .92f, .12f, .7f, .12f, .3f};
        dl.addQuadFilled(X(.5f), Y(.08f), X(.88f), Y(.3f), X(.5f), Y(.5f), X(.12f), Y(.3f),
                (accent & 0x00FFFFFF) | 0x66000000);
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            line(p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1], outline, th);
        }
        line(.12f, .3f, .5f, .5f, outline, th);
        line(.88f, .3f, .5f, .5f, outline, th);
        line(.5f, .5f, .5f, .92f, outline, th);
    }
}
