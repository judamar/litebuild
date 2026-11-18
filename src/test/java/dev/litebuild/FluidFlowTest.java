package dev.litebuild;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

public class FluidFlowTest {
    private static final FluidFlow.Probe FLAT = new FluidFlow.Probe() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y <= 0;
        }
    };

    private static Map<String, Integer> run(FluidFlow.Probe p, int length, int step, boolean support, int quality,
            boolean fill) {
        List<int[]> src = new ArrayList<int[]>();
        src.add(new int[] {0, 1, 0});
        final Map<String, Integer> out = new TreeMap<String, Integer>();
        FluidFlow.flow(p, src, length, step, support, quality, fill, new FluidFlow.Out() {
            @Override
            public void set(int x, int y, int z, int level) {
                out.put(x + "," + y + "," + z, level);
            }
        });
        return out;
    }

    @Test
    public void waterSpreadsAsADiamondAndLosesOneLevelPerBlock() {
        Map<String, Integer> m = run(FLAT, 4, 1, false, 1, false);
        assertEquals(1, (int) m.get("1,1,0"));
        assertEquals(4, (int) m.get("2,1,2"));
        assertEquals(4, (int) m.get("4,1,0"));
        assertNull(m.get("5,1,0"));
        assertNull(m.get("3,1,2")); // fuera del rombo
        assertEquals(40, m.size());
    }

    @Test
    public void lavaLosesTwoLevelsPerBlockAndReachesHalf() {
        Map<String, Integer> m = run(FLAT, 3, 2, false, 1, false);
        assertEquals(2, (int) m.get("1,1,0"));
        assertEquals(4, (int) m.get("2,1,0"));
        assertEquals(6, (int) m.get("3,1,0"));
        assertNull(m.get("4,1,0"));
    }

    @Test
    public void fallsThroughAHoleAndStopsAtAWall() {
        // Suelo hasta y = 10 salvo un hueco de tres bloques en x = 3, z = 0.
        FluidFlow.Probe hole = new FluidFlow.Probe() {
            @Override
            public boolean solid(int x, int y, int z) {
                if (x == 3 && z == 0 && y >= 8 && y <= 10) {
                    return false;
                }
                return y <= 10;
            }
        };
        List<int[]> src = new ArrayList<int[]>();
        src.add(new int[] {0, 11, 0});
        final Map<String, Integer> m = new TreeMap<String, Integer>();
        FluidFlow.flow(hole, src, 5, 1, false, new FluidFlow.Out() {
            @Override
            public void set(int x, int y, int z, int level) {
                m.put(x + "," + y + "," + z, level);
            }
        });
        assertEquals(3, (int) m.get("3,11,0"));
        assertEquals(8, (int) m.get("3,10,0"));
        assertEquals(8, (int) m.get("3,8,0"));
        assertNull(m.get("2,8,0")); // la pared del hueco es sólida
    }

    @Test
    public void requireSupportBlocksTheFall() {
        FluidFlow.Probe hole = new FluidFlow.Probe() {
            @Override
            public boolean solid(int x, int y, int z) {
                return !(x == 3 && z == 0) && y <= 0;
            }
        };
        Map<String, Integer> m = run(hole, 4, 1, true, 1, false);
        assertNull(m.get("3,1,0"));
        assertNull(m.get("3,0,0"));
        assertEquals(2, (int) m.get("2,1,0"));
    }

    @Test
    public void higherQualityAddsDiagonalsAndKnightMoves() {
        Map<String, Integer> q1 = run(FLAT, 4, 1, false, 1, false);
        Map<String, Integer> q2 = run(FLAT, 4, 1, false, 2, false);
        Map<String, Integer> q3 = run(FLAT, 4, 1, false, 3, false);
        assertEquals(4, (int) q1.get("2,1,2"));
        assertEquals(1, (int) q2.get("1,1,1")); // diagonal: raíz de 2 se redondea a 1
        assertTrue(q2.size() > q1.size() && q3.size() > q2.size());
    }

    private static final FluidFlow.Probe BOX = new FluidFlow.Probe() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y <= 0 || Math.abs(x) > 2 || Math.abs(z) > 2;
        }
    };

    @Test
    public void fillEdgesFlattensAClosedContainerWithASource() {
        Map<String, Integer> plain = run(BOX, 7, 1, false, 1, false);
        Map<String, Integer> flat = run(BOX, 7, 1, false, 1, true);
        assertTrue(plain.values().contains(3));
        assertEquals(24, flat.size());
        for (int v : flat.values()) {
            assertEquals(0, v);
        }
    }

    @Test
    public void fillEdgesLeavesAnOpenOrLeakyAreaAlone() {
        for (int v : run(FLAT, 3, 1, false, 1, true).values()) {
            assertFalse(v == 0);
        }
        FluidFlow.Probe leaky = new FluidFlow.Probe() {
            @Override
            public boolean solid(int x, int y, int z) {
                if (x == 2 && z == 1 && y == 1) {
                    return false;
                }
                return y <= 0 || Math.abs(x) > 1 || Math.abs(z) > 1;
            }
        };
        for (int v : run(leaky, 1, 1, false, 1, true).values()) {
            assertFalse(v == 0);
        }
    }

    @Test
    public void fillEdgesFillsTheCornersOfASmallClosedBox() {
        FluidFlow.Probe small = new FluidFlow.Probe() {
            @Override
            public boolean solid(int x, int y, int z) {
                return y <= 0 || Math.abs(x) > 1 || Math.abs(z) > 1;
            }
        };
        assertEquals(8, run(small, 1, 1, false, 1, true).size()); // cuatro lados y cuatro esquinas, todo fuentes
    }

    @Test
    public void aSourceOnTopOfAnotherSourceStillSpreadsSideways() {
        List<int[]> src = new ArrayList<int[]>();
        src.add(new int[] {0, 1, 0});
        src.add(new int[] {0, 2, 0}); // sobre la anterior: no cae, se extiende
        final Map<String, Integer> m = new TreeMap<String, Integer>();
        FluidFlow.flow(FLAT, src, 2, 1, true, 1, false, new FluidFlow.Out() {
            @Override
            public void set(int x, int y, int z, int level) {
                m.put(x + "," + y + "," + z, level);
            }
        });
        assertEquals(1, (int) m.get("1,1,0"));
        assertEquals(null, m.get("1,2,0")); // con Require Support no hay suelo debajo: no se extiende por el aire
        m.clear();
        FluidFlow.flow(FLAT, src, 2, 1, false, 1, false, new FluidFlow.Out() {
            @Override
            public void set(int x, int y, int z, int level) {
                m.put(x + "," + y + "," + z, level);
            }
        });
        assertEquals(1, (int) m.get("1,2,0")); // sin soporte sí se extiende desde la fuente de arriba (y luego cae)
    }

    @Test
    public void aHugeSourceBallStillFlows() {
        // Una placa de 200 x 200 = 40 000 fuentes (más que el tope de celdas): el flujo de su borde debe existir.
        List<int[]> src = new ArrayList<int[]>();
        for (int x = -100; x < 100; x++) {
            for (int z = -100; z < 100; z++) {
                src.add(new int[] {x, 1, z});
            }
        }
        final Map<String, Integer> m = new TreeMap<String, Integer>();
        FluidFlow.flow(FLAT, src, 1, 1, false, 1, false, new FluidFlow.Out() {
            @Override
            public void set(int x, int y, int z, int level) {
                m.put(x + "," + y + "," + z, level);
            }
        });
        assertEquals(1, (int) m.get("100,1,0"));
        assertEquals(1, (int) m.get("-101,1,5"));
    }
}
