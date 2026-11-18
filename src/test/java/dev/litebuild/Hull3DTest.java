package dev.litebuild;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class Hull3DTest {
    @Test
    public void aTetrahedronHasFourFaces() {
        double[][] p = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        assertEquals(4, Hull3D.triangles(p).size());
    }

    @Test
    public void aCubeHasTwelveTrianglesAndEveryPointIsInsideItsPlanes() {
        double[][] p = new double[9][];
        int i = 0;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    p[i++] = new double[] {x + 1e-6 * i, y - 1e-6 * i, z + 1e-6 * i};
                }
            }
        }
        p[8] = new double[] {0.5, 0.5, 0.5}; // un punto interior no cambia el cierre
        List<int[]> tris = Hull3D.triangles(p);
        assertNotNull(tris);
        assertEquals(12, tris.size());
        for (double[] plane : Hull3D.planes(p)) {
            for (double[] q : p) {
                assertTrue(plane[0] * q[0] + plane[1] * q[1] + plane[2] * q[2] <= plane[3] + 1e-6);
            }
        }
    }

    @Test
    public void coplanarPointsAreDegenerate() {
        assertNull(Hull3D.triangles(new double[][] {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}}));
        assertNull(Hull3D.planes(new double[][] {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}}));
    }
}
