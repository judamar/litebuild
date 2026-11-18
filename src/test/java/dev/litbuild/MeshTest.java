package dev.litbuild;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

public class MeshTest {
    private static List<List<double[]>> oneRow(double[]... pts) {
        List<double[]> r = new ArrayList<double[]>();
        for (double[] p : pts) {
            r.add(p);
        }
        List<List<double[]>> rows = new ArrayList<List<double[]>>();
        rows.add(r);
        return rows;
    }

    @Test
    public void smartSurfaceFollowsTheHeightOfEachPoint() {
        Set<Long> v = Mesh.voxelize(oneRow(new double[] {0.5, 0.5, 0.5}, new double[] {10.5, 0.5, 0.5},
                new double[] {0.5, 0.5, 10.5}, new double[] {10.5, 0.5, 10.5}, new double[] {5.5, 6.5, 5.5}),
                Mesh.LOFT, Mesh.SMART, 500000);
        assertNotNull(v);
        int minY = 99, maxY = -1, centre = -1;
        Set<Integer> columns = new HashSet<Integer>();
        for (long k : v) {
            int[] c = Mesh.unpack(k);
            minY = Math.min(minY, c[1]);
            maxY = Math.max(maxY, c[1]);
            columns.add(c[0] * 100 + c[2]);
            if (c[0] == 5 && c[2] == 5) {
                centre = Math.max(centre, c[1]);
            }
        }
        assertEquals(0, minY);
        assertEquals(6, maxY);
        assertEquals(6, centre);
        assertEquals(121, columns.size()); // cubre toda la huella de 11 x 11
    }

    @Test
    public void smartSurfaceRejectsPointsInALine() {
        assertNull(Mesh.voxelize(oneRow(new double[] {0.5, 2.5, 0.5}, new double[] {1.5, 2.5, 0.5},
                new double[] {2.5, 2.5, 0.5}, new double[] {3.5, 2.5, 0.5}), Mesh.LOFT, Mesh.SMART, 500000));
    }

    @Test
    public void delaunayTriangulatesASquareIntoTwoTriangles() {
        assertEquals(2, Mesh.delaunay(new double[] {0, 1, 0, 1}, new double[] {0, 0, 1, 1}, 4).size());
    }

    @Test
    public void delaunayTriangleCountStaysWithinEulerBounds() {
        Random r = new Random(3);
        int n = 20;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = r.nextDouble() * 10;
            ys[i] = r.nextDouble() * 10;
        }
        int t = Mesh.delaunay(xs, ys, n).size();
        assertTrue(t >= n - 2 && t <= 2 * n - 5);
    }

    @Test
    public void loftNeedsTwoRows() {
        assertNull(Mesh.voxelize(oneRow(new double[] {0.5, 0.5, 0.5}, new double[] {5.5, 0.5, 0.5}), Mesh.LOFT, Mesh.FLAT,
                500000));
    }

    @Test
    public void packUnpackRoundTripsNegativeCoordinates() {
        int[] c = Mesh.unpack(Mesh.pack(-123456, 77, -9));
        assertEquals(-123456, c[0]);
        assertEquals(77, c[1]);
        assertEquals(-9, c[2]);
    }
}
