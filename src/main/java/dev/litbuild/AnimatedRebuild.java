package dev.litbuild;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Animated Rebuild: la selección se vacía y se vuelve a construir bloque a bloque, del centro hacia fuera, con
 * retrasos entre bloques (para cinemáticas). La edición deshacible es la de vaciar: Undo cancela lo que quede por
 * colocar y devuelve el original. Los parámetros están en ticks (20 por segundo).
 */
public final class AnimatedRebuild {
    public static final float[] startDelay = {10f};
    public static final float[] airDelay = {0f};
    public static final float[] sameDelay = {0.03f};
    public static final float[] differentDelay = {0.2f};
    public static final float[] randomExtra = {0.1f};
    public static final float[] maxDelay = {2f};
    public static final float[] horizontal = {1f};
    public static final float[] vertical = {1f};

    /** Lo que queda por colocar. Lo toca el hilo del servidor; el cliente solo mira si es null. */
    static final class Job {
        int dim;
        long[] pos;
        int[] state;
        NBTTagCompound[] tile;
        double[] time;
        int next;
        double now;
    }

    private static volatile Job job;
    private static final AtomicBoolean pending = new AtomicBoolean();

    private AnimatedRebuild() {
    }

    public static boolean active() {
        return job != null;
    }

    static void start(Job j) {
        job = j;
    }

    /** Deja de colocar bloques (lo ya colocado se queda). */
    public static void cancel() {
        if (job != null) {
            job = null;
            EditEngine.status = "Animated Rebuild cancelado";
        }
    }

    /** Un tick del cliente: pide al servidor que avance un tick la reconstrucción. */
    public static void tick() {
        if (job == null || !pending.compareAndSet(false, true)) {
            return;
        }
        EditEngine.runOnServer(new EditEngine.ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                try {
                    step(server);
                } finally {
                    pending.set(false);
                }
            }
        });
    }

    private static void step(IntegratedServer server) {
        Job j = job;
        if (j == null) {
            return;
        }
        World w = server.getWorld(j.dim);
        j.now += 1;
        while (j.next < j.pos.length && j.time[j.next] <= j.now) {
            int i = j.next++;
            BlockPos p = BlockPos.fromLong(j.pos[i]);
            if (!w.isBlockLoaded(p)) {
                continue;
            }
            w.setBlockState(p, Block.getStateById(j.state[i]), 2);
            if (j.tile[i] != null) {
                TileEntity te = w.getTileEntity(p);
                if (te != null) {
                    te.readFromNBT(j.tile[i]);
                    te.markDirty();
                }
            }
        }
        if (j.next >= j.pos.length) {
            job = null;
            EditEngine.status = "Animated Rebuild: terminado";
        }
    }
}
