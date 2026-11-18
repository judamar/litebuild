package dev.litebuild;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

public class EulerTest {
    /** La misma convención que Clipboard.transformed: primero roll (Z), luego yaw (Y) y por último pitch (X). */
    private static double[] forward(double x, double y, double z, double yaw, double pitch, double roll) {
        double rz = Math.toRadians(roll), ry = Math.toRadians(yaw), rx = Math.toRadians(pitch);
        double x1 = x * Math.cos(rz) - y * Math.sin(rz), y1 = x * Math.sin(rz) + y * Math.cos(rz);
        double x2 = x1 * Math.cos(ry) - z * Math.sin(ry), z2 = x1 * Math.sin(ry) + z * Math.cos(ry);
        double y3 = y1 * Math.cos(rx) - z2 * Math.sin(rx), z3 = y1 * Math.sin(rx) + z2 * Math.cos(rx);
        return new double[] {x2, y3, z3};
    }

    private static void assertMatrix(double[][] a, double[][] b) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(a[i][j], b[i][j], 1e-9);
            }
        }
    }

    @Test
    public void matrixMatchesTheClipboardConvention() {
        Random r = new Random(5);
        for (int n = 0; n < 500; n++) {
            double yaw = r.nextDouble() * 360 - 180, pitch = r.nextDouble() * 360 - 180, roll = r.nextDouble() * 360 - 180;
            double[][] m = Euler.matrix(yaw, pitch, roll);
            for (int i = 0; i < 3; i++) {
                double[] e = new double[3];
                e[i] = 1;
                double[] f = forward(e[0], e[1], e[2], yaw, pitch, roll);
                for (int k = 0; k < 3; k++) {
                    assertEquals(f[k], m[k][i], 1e-9);
                }
            }
        }
    }

    @Test
    public void anglesRoundTripThroughTheMatrix() {
        Random r = new Random(6);
        for (int n = 0; n < 2000; n++) {
            double[][] m = Euler.matrix(r.nextDouble() * 360 - 180, r.nextDouble() * 360 - 180, r.nextDouble() * 360 - 180);
            double[] a = Euler.angles(m);
            assertMatrix(m, Euler.matrix(a[0], a[1], a[2]));
        }
    }

    @Test
    public void lockedGimbalStillRoundTrips() {
        for (double yaw : new double[] {90, -90}) {
            double[][] m = Euler.matrix(yaw, 30, 20);
            double[] a = Euler.angles(m);
            assertMatrix(m, Euler.matrix(a[0], a[1], a[2]));
        }
    }

    @Test
    public void turningAroundAnOwnAxisKeepsThatAxisFixed() {
        Random r = new Random(7);
        for (int n = 0; n < 200; n++) {
            double[][] m = Euler.matrix(r.nextDouble() * 180 - 90, r.nextDouble() * 360 - 180, r.nextDouble() * 360 - 180);
            double d = r.nextDouble() * 360 - 180;
            double[][][] turns = {Euler.rx(d), Euler.ry(d), Euler.rz(d)};
            double[][] before = Euler.axes(m);
            for (int i = 0; i < 3; i++) {
                double[][] after = Euler.axes(Euler.mul(m, turns[i]));
                for (int k = 0; k < 3; k++) {
                    assertEquals(before[i][k], after[i][k], 1e-9);
                }
            }
        }
    }

    @Test
    public void positiveYawTurnsXTowardsZ() {
        double[][] ax = Euler.axes(Euler.matrix(90, 0, 0));
        assertEquals(1.0, ax[0][2], 1e-9);
    }
}
