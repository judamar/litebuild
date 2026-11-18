package dev.litebuild;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

/**
 * Gradient Painter ya resuelto: reparte una lista de bloques a lo largo de una dirección (degradado plano) o
 * desde un punto (degradado esférico). Con la interpolación Linear o Bezier las transiciones entre bloques
 * se mezclan con ruido blanco, de modo que se ven como manchas que se funden en vez de bandas.
 */
public final class GradientSpec implements StatePicker {
    public static final String[] DIRECTIONS = {"+X", "-X", "+Y", "-Y", "+Z", "-Z", "Spherical"};
    public static final String[] INTERPOLATIONS = {"Nearest", "Linear", "Bezier"};
    public static final int NEAREST = 0, LINEAR = 1, BEZIER = 2;

    private final int[] stateIds;
    private final double[] end;     // fin de cada banda en [0, 1]
    private final double[] center;  // centro de cada banda
    private final double ox, oy, oz;
    private final double dx, dy, dz;
    private final boolean spherical;
    private final double length;
    private final int interp;
    private final boolean clamp;
    private final long seed;

    /** direction: índice de DIRECTIONS (0-5 = ejes, 6 = esférico); length en bloques. */
    public GradientSpec(int[] stateIds, int[] weights, BlockPos origin, int direction, double length, int interp,
            boolean clamp, long seed) {
        this.stateIds = stateIds;
        this.end = new double[weights.length];
        this.center = new double[weights.length];
        double total = 0;
        for (int w : weights) {
            total += w;
        }
        double run = 0;
        for (int i = 0; i < weights.length; i++) {
            double start = run / total;
            run += weights[i];
            end[i] = run / total;
            center[i] = (start + end[i]) / 2;
        }
        this.ox = origin.getX() + 0.5;
        this.oy = origin.getY() + 0.5;
        this.oz = origin.getZ() + 0.5;
        this.spherical = direction >= 6;
        int[] d = spherical ? new int[] {0, 0, 0} : BoxGizmo.DIR[direction];
        this.dx = d[0];
        this.dy = d[1];
        this.dz = d[2];
        this.length = Math.max(1.0, length);
        this.interp = interp;
        this.clamp = clamp;
        this.seed = seed;
    }

    @Override
    public IBlockState pick(int x, int y, int z) {
        double px = x + 0.5 - ox, py = y + 0.5 - oy, pz = z + 0.5 - oz;
        double t = spherical ? Math.sqrt(px * px + py * py + pz * pz) / length
                : (px * dx + py * dy + pz * dz) / length;
        if (clamp && (t < 0 || t > 1)) {
            return null;
        }
        t = Math.max(0.0, Math.min(1.0, t));
        if (interp == BEZIER) {
            t = t * t * (3 - 2 * t); // suave en los extremos
        }
        int n = stateIds.length;
        int band = n - 1;
        if (interp == NEAREST || n == 1) {
            for (int i = 0; i < n; i++) {
                if (t <= end[i]) {
                    band = i;
                    break;
                }
            }
        } else if (t <= center[0]) {
            band = 0;
        } else if (t >= center[n - 1]) {
            band = n - 1;
        } else {
            for (int i = 0; i < n - 1; i++) {
                if (t < center[i + 1]) {
                    double frac = (t - center[i]) / (center[i + 1] - center[i]);
                    band = Noise.white(x, y, z, seed) < frac ? i + 1 : i;
                    break;
                }
            }
        }
        return Block.getStateById(stateIds[band]);
    }
}
