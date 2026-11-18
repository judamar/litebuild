package dev.litbuild;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

public class VoxelScaleTest {
    private static int count(int[] a, int v) {
        int n = 0;
        for (int x : a) {
            if (x == v) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void uniformAndSingleCellsAreJustRepeated() {
        int[] u = new int[27];
        Arrays.fill(u, 5);
        assertEquals(27 * 8, count(VoxelScale.smooth(u, 3, 3, 3, 2), 5));
        assertEquals(27, count(VoxelScale.smooth(new int[] {7}, 1, 1, 1, 3), 7));
    }

    @Test
    public void anIsolatedBlockKeepsItsFullCube() {
        int[] iso = new int[27];
        iso[13] = 9;
        assertEquals(8, count(VoxelScale.smooth(iso, 3, 3, 3, 2), 9));
    }

    @Test
    public void stairsAreSmoothed() {
        // Escalera x <= y extruida en z (4 x 4 x 1): la diagonal de x2 pierde las puntas.
        int[] s = new int[16];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                s[y * 4 + x] = x <= y ? 1 : 0;
            }
        }
        int[] a = VoxelScale.smooth(s, 4, 4, 1, 2);
        int[] plain = new int[8 * 8 * 2];
        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 8; x++) {
                    plain[(y * 2 + z) * 8 + x] = s[(y / 2) * 4 + x / 2];
                }
            }
        }
        assertEquals(false, Arrays.equals(a, plain)); // no es solo repetir cada bloque
        // Y la reducción devuelve la forma original.
        int[] dims = new int[3];
        assertArrayEquals(s, VoxelScale.downscale(a, 8, 8, 2, 2, 0, dims));
        assertArrayEquals(new int[] {4, 4, 1}, dims);
    }

    @Test
    public void aSolidCubeGetsChamferedEdgesAndSurvivesTheRoundTrip() {
        int[] big = new int[125];
        for (int y = 1; y < 4; y++) {
            for (int z = 1; z < 4; z++) {
                for (int x = 1; x < 4; x++) {
                    big[(y * 5 + z) * 5 + x] = 3;
                }
            }
        }
        int[] up = VoxelScale.smooth(big, 5, 5, 5, 2);
        assertEquals(160, count(up, 3)); // 216 de un cubo macizo menos las aristas y esquinas achaflanadas
        int[] dims = new int[3];
        assertArrayEquals(big, VoxelScale.downscale(up, 10, 10, 10, 2, 0, dims));
    }

    @Test
    public void downscaleTakesTheMostFrequentAndAirOnlyWinsWithAMajority() {
        int[] dims = new int[3];
        assertEquals(2, VoxelScale.downscale(new int[] {2, 2, 2, 2, 2, 6, 6, 6}, 2, 2, 2, 2, 0, dims)[0]);
        // 4 de 8 vacíos no bastan para ser aire; 5 sí.
        assertEquals(4, VoxelScale.downscale(new int[] {4, 4, 4, 4, 0, 0, 0, 0}, 2, 2, 2, 2, 0, dims)[0]);
        assertEquals(0, VoxelScale.downscale(new int[] {4, 4, 4, 0, 0, 0, 0, 0}, 2, 2, 2, 2, 0, dims)[0]);
        // 3 -> 2: la última celda cubre una sola celda real y el resto cuenta como aire.
        int[] odd = new int[27];
        Arrays.fill(odd, 4);
        int[] d = VoxelScale.downscale(odd, 3, 3, 3, 2, 0, dims);
        assertArrayEquals(new int[] {2, 2, 2}, dims);
        assertEquals(0, d[7]);
    }

    @Test
    public void randomShapesKeepTheirSizeAndOnlyUseExistingValues() {
        Random r = new Random(1);
        int[] rnd = new int[5 * 4 * 6];
        for (int i = 0; i < rnd.length; i++) {
            rnd[i] = r.nextInt(3);
        }
        for (int f = 2; f <= 3; f++) {
            int[] o = VoxelScale.smooth(rnd, 5, 4, 6, f);
            assertEquals(5 * 4 * 6 * f * f * f, o.length);
            for (int v : o) {
                assertEquals(true, v >= 0 && v <= 2);
            }
        }
    }
}
