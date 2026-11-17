package dev.litbuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Modelling: puntos 3D que forman superficies, agrupados en filas. Cada clic derecho añade un punto a la fila actual;
 * *Build* voxeliza la superficie (ver {@link Mesh}) con el Active Block y la deja como Placement en su sitio.
 */
public final class Modelling {
    public static final List<List<double[]>> rows = new ArrayList<List<double[]>>();
    public static int currentRow;
    public static final ImInt shape = new ImInt(Mesh.LOFT);
    public static final ImInt surface = new ImInt(Mesh.CATMULL);
    /** Coloca el punto en el bloque contiguo a la cara apuntada, no en el bloque apuntado. */
    public static final ImBoolean offsetTarget = new ImBoolean(false);

    /** Resultado de Build: la forma y la esquina mínima donde va. */
    public static final class Result {
        public Clipboard clip;
        public int[] min;
    }

    private Modelling() {
    }

    public static boolean hasPoints() {
        for (List<double[]> r : rows) {
            if (!r.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static int pointCount() {
        int n = 0;
        for (List<double[]> r : rows) {
            n += r.size();
        }
        return n;
    }

    private static List<double[]> row() {
        if (rows.isEmpty()) {
            rows.add(new ArrayList<double[]>());
            currentRow = 0;
        }
        currentRow = Math.max(0, Math.min(rows.size() - 1, currentRow));
        return rows.get(currentRow);
    }

    public static void add(BlockPos p) {
        row().add(new double[] {p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5});
    }

    /** Abre una fila nueva (si la actual tiene puntos) y la deja como la actual. */
    public static void newRow() {
        if (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
            currentRow = rows.size() - 1;
            return;
        }
        rows.add(new ArrayList<double[]>());
        currentRow = rows.size() - 1;
    }

    public static void removeLast() {
        List<double[]> r = row();
        if (!r.isEmpty()) {
            r.remove(r.size() - 1);
        }
        if (r.isEmpty() && rows.size() > 1) {
            rows.remove(currentRow);
            currentRow = Math.max(0, currentRow - 1);
        }
    }

    public static void clear() {
        rows.clear();
        currentRow = 0;
    }

    /** Extrude Point: añade un punto en la posición del último de la fila actual (luego se puede mover en la lista). */
    public static void extrude() {
        List<double[]> r = row();
        if (!r.isEmpty()) {
            r.add(r.get(r.size() - 1).clone());
        }
    }

    /** Voxeliza la superficie con state; null (con el motivo en EditEngine.status) si no se puede. */
    public static Result build(IBlockState state) {
        List<List<double[]>> use = new ArrayList<List<double[]>>();
        for (List<double[]> r : rows) {
            if (!r.isEmpty()) {
                use.add(r);
            }
        }
        if (use.isEmpty()) {
            EditEngine.status = "Modelling: no hay puntos";
            return null;
        }
        if (shape.get() == Mesh.LOFT && use.size() < 2) {
            EditEngine.status = "Modelling: Loft necesita al menos 2 filas con puntos";
            return null;
        }
        if (shape.get() != Mesh.LOFT && pointCount() < 3) {
            EditEngine.status = "Modelling: hacen falta al menos 3 puntos";
            return null;
        }
        Set<Long> vox = Mesh.voxelize(use, shape.get(), surface.get(), (int) EditEngine.MAX_BLOCKS);
        if (vox == null) {
            EditEngine.status = "Modelling: la forma es degenerada (puntos en línea o coplanares en el casco) o pasa de "
                    + EditEngine.MAX_BLOCKS + " bloques";
            return null;
        }
        if (vox.isEmpty()) {
            EditEngine.status = "Modelling: la superficie no tiene bloques";
            return null;
        }
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, max = {Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MIN_VALUE};
        List<int[]> cells = new ArrayList<int[]>(vox.size());
        for (long k : vox) {
            int[] c = Mesh.unpack(k);
            cells.add(c);
            for (int a = 0; a < 3; a++) {
                min[a] = Math.min(min[a], c[a]);
                max[a] = Math.max(max[a], c[a]);
            }
        }
        int sx = max[0] - min[0] + 1, sy = max[1] - min[1] + 1, sz = max[2] - min[2] + 1;
        if ((long) sx * sy * sz > EditEngine.MAX_BLOCKS) {
            EditEngine.status = "Modelling: la caja de la forma es demasiado grande (" + sx + " x " + sy + " x " + sz + ")";
            return null;
        }
        int[] states = new int[sx * sy * sz];
        java.util.Arrays.fill(states, Clipboard.AIR);
        int id = Block.getStateId(state);
        for (int[] c : cells) {
            states[((c[1] - min[1]) * sz + (c[2] - min[2])) * sx + (c[0] - min[0])] = id;
        }
        Result r = new Result();
        r.clip = new Clipboard(sx, sy, sz, states, new HashMap<Integer, NBTTagCompound>());
        r.min = min;
        EditEngine.status = "Modelling: " + cells.size() + " bloques (" + sx + " x " + sy + " x " + sz + ")";
        return r;
    }
}
