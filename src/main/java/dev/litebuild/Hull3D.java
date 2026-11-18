package dev.litebuild;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cierre convexo 3D (quickhull) de una nube de puntos. Sin dependencias de Minecraft. Los puntos deben estar en
 * posición general (sin 4 coplanares): quien lo llama les suma un ruido minúsculo.
 */
public final class Hull3D {
    private static final double EPS = 1e-9;

    private Hull3D() {
    }

    private static final class Face {
        final int a, b, c;
        final double nx, ny, nz, d;
        List<Integer> out = new ArrayList<Integer>();
        boolean dead;

        Face(double[][] p, int a, int b, int c) {
            this.a = a;
            this.b = b;
            this.c = c;
            double ux = p[b][0] - p[a][0], uy = p[b][1] - p[a][1], uz = p[b][2] - p[a][2];
            double vx = p[c][0] - p[a][0], vy = p[c][1] - p[a][1], vz = p[c][2] - p[a][2];
            double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            double len = Math.sqrt(x * x + y * y + z * z);
            nx = x / len;
            ny = y / len;
            nz = z / len;
            d = nx * p[a][0] + ny * p[a][1] + nz * p[a][2];
        }

        double dist(double[] q) {
            return nx * q[0] + ny * q[1] + nz * q[2] - d;
        }
    }

    /**
     * Los planos de las caras del cierre como {nx, ny, nz, d} con normal unitaria hacia fuera (un punto q está
     * dentro si n·q <= d). null si hay menos de 4 puntos o todos son coplanares.
     */
    public static List<double[]> planes(double[][] p) {
        List<Face> faces = hull(p);
        if (faces == null) {
            return null;
        }
        List<double[]> out = new ArrayList<double[]>();
        for (Face f : faces) {
            out.add(new double[] {f.nx, f.ny, f.nz, f.d});
        }
        return out;
    }

    /** Las caras del cierre como ternas de índices de p (orientadas hacia fuera); null si es degenerado. */
    public static List<int[]> triangles(double[][] p) {
        List<Face> faces = hull(p);
        if (faces == null) {
            return null;
        }
        List<int[]> out = new ArrayList<int[]>();
        for (Face f : faces) {
            out.add(new int[] {f.a, f.b, f.c});
        }
        return out;
    }

    private static List<Face> hull(double[][] p) {
        int n = p.length;
        if (n < 4) {
            return null;
        }
        int i0 = 0, i1 = 0;
        for (int i = 1; i < n; i++) {
            if (p[i][0] < p[i0][0]) {
                i0 = i;
            }
            if (p[i][0] > p[i1][0]) {
                i1 = i;
            }
        }
        if (i0 == i1) {
            return null;
        }
        int i2 = -1;
        double best = EPS;
        for (int i = 0; i < n; i++) {
            double d = lineDistSq(p[i], p[i0], p[i1]);
            if (d > best) {
                best = d;
                i2 = i;
            }
        }
        if (i2 < 0) {
            return null;
        }
        Face base = new Face(p, i0, i1, i2);
        int i3 = -1;
        best = EPS;
        for (int i = 0; i < n; i++) {
            double d = Math.abs(base.dist(p[i]));
            if (d > best) {
                best = d;
                i3 = i;
            }
        }
        if (i3 < 0) {
            return null;
        }
        double[] inner = {(p[i0][0] + p[i1][0] + p[i2][0] + p[i3][0]) / 4, (p[i0][1] + p[i1][1] + p[i2][1] + p[i3][1]) / 4,
            (p[i0][2] + p[i1][2] + p[i2][2] + p[i3][2]) / 4};
        List<Face> faces = new ArrayList<Face>();
        int[][] tri = {{i0, i1, i2}, {i0, i1, i3}, {i1, i2, i3}, {i0, i2, i3}};
        for (int[] t : tri) {
            Face f = new Face(p, t[0], t[1], t[2]);
            if (f.dist(inner) > 0) {
                f = new Face(p, t[0], t[2], t[1]);
            }
            faces.add(f);
        }
        for (int i = 0; i < n; i++) {
            if (i == i0 || i == i1 || i == i2 || i == i3) {
                continue;
            }
            for (Face f : faces) {
                if (f.dist(p[i]) > EPS) {
                    f.out.add(i);
                    break;
                }
            }
        }
        Deque<Face> work = new ArrayDeque<Face>(faces);
        int iterations = 0;
        while (!work.isEmpty()) {
            Face f = work.pop();
            if (f.dead || f.out.isEmpty()) {
                continue;
            }
            int far = f.out.get(0);
            double fd = f.dist(p[far]);
            for (int i : f.out) {
                double d = f.dist(p[i]);
                if (d > fd) {
                    fd = d;
                    far = i;
                }
            }
            List<Face> visible = new ArrayList<Face>();
            Set<Long> edges = new HashSet<Long>();
            for (Face g : faces) {
                if (!g.dead && g.dist(p[far]) > EPS) {
                    visible.add(g);
                    edges.add(edge(g.a, g.b));
                    edges.add(edge(g.b, g.c));
                    edges.add(edge(g.c, g.a));
                }
            }
            List<Face> fresh = new ArrayList<Face>();
            for (Face g : visible) {
                int[] ring = {g.a, g.b, g.c};
                for (int k = 0; k < 3; k++) {
                    int a = ring[k], b = ring[(k + 1) % 3];
                    if (!edges.contains(edge(b, a))) {
                        fresh.add(new Face(p, a, b, far));
                    }
                }
            }
            for (Face g : visible) {
                g.dead = true;
                for (int i : g.out) {
                    if (i == far) {
                        continue;
                    }
                    for (Face h : fresh) {
                        if (h.dist(p[i]) > EPS) {
                            h.out.add(i);
                            break;
                        }
                    }
                }
                g.out = null;
            }
            faces.addAll(fresh);
            for (Face h : fresh) {
                if (!h.out.isEmpty()) {
                    work.push(h);
                }
            }
            // Las caras muertas se van quitando de vez en cuando para no recorrerlas siempre.
            if (++iterations % 16 == 0) {
                List<Face> live = new ArrayList<Face>();
                for (Face g : faces) {
                    if (!g.dead) {
                        live.add(g);
                    }
                }
                faces = live;
            }
        }
        List<Face> live = new ArrayList<Face>();
        for (Face f : faces) {
            if (!f.dead) {
                live.add(f);
            }
        }
        return live;
    }

    private static long edge(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    private static double lineDistSq(double[] q, double[] a, double[] b) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = q[0] - a[0], vy = q[1] - a[1], vz = q[2] - a[2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return (cx * cx + cy * cy + cz * cz) / (ux * ux + uy * uy + uz * uz);
    }

    /**
     * Intervalo de y dentro del cierre para la columna (x, z): {lo, hi} con tolerancia tol, o null si está vacía.
     */
    public static double[] column(List<double[]> planes, double x, double z, double tol) {
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
        for (double[] f : planes) {
            double r = f[3] + tol - f[0] * x - f[2] * z;
            double ny = f[1];
            if (Math.abs(ny) < 1e-12) {
                if (r < 0) {
                    return null;
                }
            } else if (ny > 0) {
                hi = Math.min(hi, r / ny);
            } else {
                lo = Math.max(lo, r / ny);
            }
            if (lo > hi) {
                return null;
            }
        }
        return new double[] {lo, hi};
    }
}
