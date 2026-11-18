package dev.litbuild;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class VoxelRayTest {
    private static VoxelRay.Probe block(final int bx, final int by, final int bz) {
        return new VoxelRay.Probe() {
            @Override
            public int at(int x, int y, int z) {
                return x == bx && y == by && z == bz ? 1 : 0;
            }
        };
    }

    private static void hit(double[] r, double x, double y, double z, double face, double t) {
        assertTrue("sin impacto", r != null);
        assertArrayEquals(new double[] {x, y, z, face}, new double[] {r[0], r[1], r[2], r[3]}, 0);
        assertEquals(t, r[4], 1e-9);
    }

    @Test
    public void reportsTheFaceTheRayEntersThrough() {
        hit(VoxelRay.trace(block(3, 0, 0), 0.5, 0.5, 0.5, 1, 0, 0, 10), 3, 0, 0, 4, 2.5); // +x: cara oeste
        hit(VoxelRay.trace(block(-2, 0, 0), 0.5, 0.5, 0.5, -1, 0, 0, 10), -2, 0, 0, 5, 1.5); // -x: cara este
        hit(VoxelRay.trace(block(0, 3, 0), 0.5, 0.5, 0.5, 0, 1, 0, 10), 0, 3, 0, 0, 2.5); // +y: cara de abajo
        hit(VoxelRay.trace(block(0, -1, 0), 0.5, 0.5, 0.5, 0, -1, 0, 10), 0, -1, 0, 1, 0.5); // -y: cara de arriba
        hit(VoxelRay.trace(block(0, 0, 2), 0.5, 0.5, 0.5, 0, 0, 1, 10), 0, 0, 2, 2, 1.5); // +z: cara norte
        hit(VoxelRay.trace(block(0, 0, -4), 0.5, 0.5, 0.5, 0, 0, -1, 10), 0, 0, -4, 3, 3.5); // -z: cara sur
    }

    @Test
    public void respectsReachAndStopSignals() {
        assertNull(VoxelRay.trace(block(8, 0, 0), 0.5, 0.5, 0.5, 1, 0, 0, 5));
        VoxelRay.Probe stop = new VoxelRay.Probe() {
            @Override
            public int at(int x, int y, int z) {
                return x == 2 ? 2 : x == 3 ? 1 : 0;
            }
        };
        assertNull(VoxelRay.trace(stop, 0.5, 0.5, 0.5, 1, 0, 0, 10));
    }

    @Test
    public void agreesWithFineMarching() {
        Random rnd = new Random(7);
        for (int n = 0; n < 1000; n++) {
            double ox = rnd.nextDouble() * 4, oy = rnd.nextDouble() * 4, oz = rnd.nextDouble() * 4;
            double dx = rnd.nextGaussian(), dy = rnd.nextGaussian(), dz = rnd.nextGaussian();
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l;
            dy /= l;
            dz /= l;
            int bx = rnd.nextInt(9) - 2, by = rnd.nextInt(9) - 2, bz = rnd.nextInt(9) - 2;
            double[] fast = VoxelRay.trace(block(bx, by, bz), ox, oy, oz, dx, dy, dz, 12);
            int sx = (int) Math.floor(ox), sy = (int) Math.floor(oy), sz = (int) Math.floor(oz);
            double slow = -1;
            for (double t = 0; t <= 12; t += 0.0005) {
                int cx = (int) Math.floor(ox + dx * t), cy = (int) Math.floor(oy + dy * t), cz = (int) Math.floor(oz + dz * t);
                if (cx == bx && cy == by && cz == bz && !(cx == sx && cy == sy && cz == sz)) {
                    slow = t;
                    break;
                }
            }
            assertEquals("existencia del impacto en " + n, slow < 0, fast == null);
            if (fast != null) {
                assertEquals(slow, fast[4], 0.002);
            }
        }
    }
}
