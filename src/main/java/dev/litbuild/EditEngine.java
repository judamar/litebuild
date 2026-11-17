package dev.litbuild;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * Ediciones de bloques con historial. Solo cliente: trabaja sobre el mundo del servidor integrado
 * (un jugador) y ejecuta todo en su hilo. En multijugador no hace nada.
 */
public final class EditEngine {
    /** Tope por operación: se hace en un solo tick del servidor. ponytail: partir en ticks si hace falta más. */
    public static final long MAX_BLOCKS = 500_000L;
    private static final int MAX_EDITS = 20;
    private static final long MAX_HISTORY_BLOCKS = 2_000_000L;

    /** Qué posiciones de la caja toca un Fill. */
    public static final int ALL = 0, OUTLINE = 1, WALLS = 2, SHELL = 3, TOP = 4, BOTTOM = 5, HOLLOW = 6;

    /** Último mensaje para la UI. */
    public static volatile String status = "";
    /** Nombre de la edición que deshacería Undo / rehacería Redo; null si no hay. */
    public static volatile String undoLabel;
    public static volatile String redoLabel;
    /** Historial para la ventana History (de la más reciente a la más antigua). */
    public static volatile String[] undoNames = new String[0];
    public static volatile String[] redoNames = new String[0];
    /** Resultado del último Analyze (líneas de texto) o null. */
    public static volatile String[] analysis;

    // Solo se tocan desde el hilo del servidor integrado.
    private static final Deque<Edit> UNDO = new ArrayDeque<Edit>();
    private static final Deque<Edit> REDO = new ArrayDeque<Edit>();
    private static Builder stroke;
    private static String strokeName;

    private EditEngine() {
    }

    /** Una edición ya hecha: estado de cada bloque antes y después, y los tile entities de ambos lados. */
    private static final class Edit {
        final String name;
        final int dim;
        final long[] pos;
        final int[] before;
        final int[] after;
        final Map<Long, NBTTagCompound> tilesBefore;
        final Map<Long, NBTTagCompound> tilesAfter;
        /** Solo en ediciones de bioma: columnas (x en los 32 bits bajos, z en los altos), bioma antes y después. */
        final long[] biomeCols;
        final byte[] biomeBefore;
        final byte biomeAfter;

        Edit(String name, int dim, long[] cols, byte[] before, byte after) {
            this.name = name;
            this.dim = dim;
            this.pos = new long[0];
            this.before = new int[0];
            this.after = new int[0];
            this.tilesBefore = new HashMap<Long, NBTTagCompound>();
            this.tilesAfter = new HashMap<Long, NBTTagCompound>();
            this.biomeCols = cols;
            this.biomeBefore = before;
            this.biomeAfter = after;
        }

        /** Elementos que toca la edición (para el tope del historial y la ventana History). */
        int size() {
            return biomeCols != null ? biomeCols.length : pos.length;
        }

        Edit(String name, int dim, long[] pos, int[] before, int[] after,
                Map<Long, NBTTagCompound> tilesBefore, Map<Long, NBTTagCompound> tilesAfter) {
            this.name = name;
            this.dim = dim;
            this.pos = pos;
            this.before = before;
            this.after = after;
            this.tilesBefore = tilesBefore;
            this.tilesAfter = tilesAfter;
            this.biomeCols = null;
            this.biomeBefore = null;
            this.biomeAfter = 0;
        }
    }

    /** Aplica cambios al mundo y va anotando lo necesario para deshacerlos. */
    private static final class Builder {
        final World w;
        final int dim;
        long[] pos;
        int[] before;
        int[] after;
        final Map<Long, NBTTagCompound> tilesBefore = new HashMap<Long, NBTTagCompound>();
        final Map<Long, NBTTagCompound> tilesAfter = new HashMap<Long, NBTTagCompound>();
        /** Posiciones ya cambiadas (solo en trazos de pincel: cada bloque se toca una vez). */
        final Set<Long> touched;
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n;
        int skipped;

        Builder(World w, int dim, int capacity, boolean dedupe) {
            this.w = w;
            this.dim = dim;
            int c = Math.max(16, Math.min(capacity, 1 << 20));
            this.pos = new long[c];
            this.before = new int[c];
            this.after = new int[c];
            this.touched = dedupe ? new HashSet<Long>() : null;
        }

        /** Estado actual del bloque o null si está fuera de altura o en un chunk sin cargar. */
        IBlockState peek(int x, int y, int z) {
            if (y < 0 || y > 255) {
                return null;
            }
            p.setPos(x, y, z);
            return w.isBlockLoaded(p) ? w.getBlockState(p) : null;
        }

        /** tile = NBT a aplicar al tile entity del bloque nuevo (o null). */
        void set(int x, int y, int z, IBlockState target, NBTTagCompound tile, boolean keepExisting) {
            p.setPos(x, y, z);
            if (y < 0 || y > 255 || !w.isBlockLoaded(p)) {
                skipped++;
                return;
            }
            IBlockState old = w.getBlockState(p);
            if (keepExisting && old.getBlock() != Blocks.AIR) {
                return;
            }
            if (old == target && tile == null) {
                return;
            }
            long key = p.toLong();
            if (touched != null && !touched.add(key)) {
                return;
            }
            if (old.getBlock().hasTileEntity()) {
                TileEntity te = w.getTileEntity(p);
                if (te != null) {
                    tilesBefore.put(key, te.writeToNBT(new NBTTagCompound()));
                }
            }
            w.setBlockState(p, target, 2);
            if (tile != null) {
                TileEntity te = w.getTileEntity(p);
                if (te != null) {
                    NBTTagCompound tag = tile.copy();
                    tag.setInteger("x", x);
                    tag.setInteger("y", y);
                    tag.setInteger("z", z);
                    te.readFromNBT(tag);
                    te.markDirty();
                    w.notifyBlockUpdate(p, target, target, 3);
                    tilesAfter.put(key, tag);
                }
            }
            if (n == pos.length) {
                int c = n * 2;
                pos = Arrays.copyOf(pos, c);
                before = Arrays.copyOf(before, c);
                after = Arrays.copyOf(after, c);
            }
            pos[n] = key;
            before[n] = Block.getStateId(old);
            after[n] = Block.getStateId(target);
            n++;
        }

        void commit(String name) {
            if (n == 0) {
                status = name + ": nada que cambiar" + (skipped > 0 ? " (" + skipped + " fuera de chunks cargados)" : "");
                return;
            }
            push(new Edit(name, dim, Arrays.copyOf(pos, n), Arrays.copyOf(before, n), Arrays.copyOf(after, n),
                    tilesBefore, tilesAfter));
            status = name + ": " + n + " bloques" + (skipped > 0 ? " (" + skipped + " omitidos: chunk sin cargar)" : "");
        }
    }

    public interface ServerTask {
        void run(IntegratedServer server);
    }

    private interface Visit {
        void at(int x, int y, int z);
    }

    /** Ejecuta la tarea en el hilo del servidor integrado (nada en multijugador). */
    public static void runOnServer(final ServerTask task) {
        final IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) {
            status = "Solo disponible en mundos de un jugador";
            return;
        }
        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run(server);
                } catch (Throwable t) {
                    LiteModLitBuild.LOG.error("Error en la edición", t);
                    status = "Error: " + t;
                }
            }
        });
    }

    // ---- Operaciones sobre la selección -----------------------------------------------------

    /** Comprueba la selección y su tamaño; devuelve false (y deja el motivo en status) si no sirve. */
    private static boolean selectionOk() {
        if (!Selection.active) {
            status = "No hay selección";
            return false;
        }
        long volume = Selection.volume();
        if (volume > MAX_BLOCKS) {
            status = "Selección demasiado grande: " + volume + " bloques (máximo " + MAX_BLOCKS + ")";
            return false;
        }
        return true;
    }

    /** Copiar, mover y apilar leen la caja entera: su volumen también tiene tope. */
    private static boolean boxOk() {
        long volume = Selection.boxVolume();
        if (volume > MAX_BLOCKS) {
            status = "La caja de la selección es demasiado grande: " + volume + " bloques (máximo " + MAX_BLOCKS + ")";
            return false;
        }
        return true;
    }

    /** Rellena toda la selección con un estado de bloque. */
    public static void fill(String name, IBlockState state) {
        fill(name, state, ALL);
    }

    /** Rellena parte de la selección según mode (HOLLOW la vacía y ignora state). */
    public static void fill(final String name, final IBlockState state, final int mode) {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, cap, false);
                final IBlockState target = mode == HOLLOW ? Blocks.AIR.getDefaultState() : state;
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        if (selects(mode, x, y, z, box)) {
                            b.set(x, y, z, target, null, false);
                        }
                    }
                });
                b.commit(name);
            }
        });
    }

    /** Sustituye en la selección los bloques de tipo from (meta -1 = cualquiera) por to. */
    public static void replace(final String name, final Block from, final int fromMeta, final IBlockState to) {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, cap, false);
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s != null && s.getBlock() == from && (fromMeta < 0 || from.getMetaFromState(s) == fromMeta)) {
                            b.set(x, y, z, to, null, false);
                        }
                    }
                });
                b.commit(name);
            }
        });
    }

    /** Quita el agua y la lava de la selección. */
    public static void drain() {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, cap, false);
                final IBlockState air = Blocks.AIR.getDefaultState();
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s != null && s.getMaterial().isLiquid()) {
                            b.set(x, y, z, air, null, false);
                        }
                    }
                });
                b.commit("Drain");
            }
        });
    }

    /** Cuenta los tipos de bloque de la selección y deja el resultado en analysis. */
    public static void analyze() {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, 16, false);
                final Map<String, Integer> counts = new HashMap<String, Integer>();
                final long[] total = {0};
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s == null) {
                            return;
                        }
                        Block blk = s.getBlock();
                        String key = Block.REGISTRY.getNameForObject(blk) + ":" + blk.getMetaFromState(s);
                        Integer c = counts.get(key);
                        counts.put(key, c == null ? 1 : c + 1);
                        total[0]++;
                    }
                });
                List<String> keys = new ArrayList<String>(counts.keySet());
                Collections.sort(keys, new Comparator<String>() {
                    @Override
                    public int compare(String a, String c) {
                        return counts.get(c) - counts.get(a);
                    }
                });
                List<String> lines = new ArrayList<String>();
                lines.add(total[0] + " bloques, " + keys.size() + " tipos");
                for (int i = 0; i < Math.min(keys.size(), 30); i++) {
                    int c = counts.get(keys.get(i));
                    lines.add(String.format(Locale.ROOT, "%8d  %5.1f%%  %s", c, 100.0 * c / total[0], keys.get(i)));
                }
                analysis = lines.toArray(new String[0]);
                status = "Analyze: " + total[0] + " bloques";
            }
        });
    }

    /** Copia la selección al portapapeles; con cut también la borra (una sola edición deshacible). */
    public static void copy(final boolean cut) {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                Clipboard c = readClipboard(w, box, mask);
                Clipboard.current = c;
                if (cut) {
                    final Builder b = new Builder(w, dim, c.states.length, false);
                    final IBlockState air = Blocks.AIR.getDefaultState();
                    iterate(box, mask, new Visit() {
                        @Override
                        public void at(int x, int y, int z) {
                            b.set(x, y, z, air, null, false);
                        }
                    });
                    b.commit("Cut");
                } else {
                    status = "Copiado: " + c.sx + " x " + c.sy + " x " + c.sz + " (" + c.solidCount() + " bloques)";
                }
            }
        });
    }

    /** Pega el Placement activo en el mundo. */
    public static void paste(final String name) {
        final Clipboard c = Placement.shape;
        if (c == null) {
            return;
        }
        final int ox = Placement.pos[0], oy = Placement.pos[1], oz = Placement.pos[2];
        final boolean keep = Placement.KEEP_EXISTING.get();
        final boolean air = Placement.PASTE_AIR.get();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Builder b = new Builder(server.getWorld(dim), dim, c.states.length, false);
                place(b, c, ox, oy, oz, air, keep);
                b.commit(name);
            }
        });
    }

    /** Mueve el contenido de la selección (y la selección) dx, dy, dz bloques. Devuelve si se lanzó. */
    public static boolean move(final int dx, final int dy, final int dz) {
        if (!selectionOk() || !boxOk()) {
            return false;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                Clipboard c = readClipboard(w, box, mask);
                final Builder b = new Builder(w, dim, c.states.length * 2, false);
                final IBlockState air = Blocks.AIR.getDefaultState();
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        b.set(x, y, z, air, null, false);
                    }
                });
                place(b, c, box[0] + dx, box[1] + dy, box[2] + dz, false, false);
                b.commit("Move");
            }
        });
        return true;
    }

    /** Repite count copias de la selección en la dirección dir, separadas por gap bloques. */
    public static void stack(final EnumFacing dir, final int count, final int gap) {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        if (Selection.volume() * (long) count > MAX_BLOCKS) {
            status = "Stack demasiado grande: " + Selection.volume() * (long) count + " bloques (máximo " + MAX_BLOCKS + ")";
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        final int step = Selection.size(dir.getAxis().ordinal()) + gap;
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                Clipboard c = readClipboard(w, box, mask);
                Builder b = new Builder(w, dim, c.states.length * count, false);
                for (int k = 1; k <= count; k++) {
                    place(b, c, box[0] + dir.getFrontOffsetX() * step * k, box[1] + dir.getFrontOffsetY() * step * k,
                            box[2] + dir.getFrontOffsetZ() * step * k, false, false);
                }
                b.commit("Stack x" + count);
            }
        });
    }

    /** Lee la caja del mundo; lo que queda fuera de la máscara cuenta como aire. */
    private static Clipboard readClipboard(World w, int[] box, Set<Long> mask) {
        int sx = box[3] - box[0] + 1, sy = box[4] - box[1] + 1, sz = box[5] - box[2] + 1;
        int[] states = new int[sx * sy * sz];
        Map<Integer, NBTTagCompound> tiles = new HashMap<Integer, NBTTagCompound>();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = (y * sz + z) * sx + x;
                    int wx = box[0] + x, wy = box[1] + y, wz = box[2] + z;
                    q.setPos(wx, wy, wz);
                    if (wy < 0 || wy > 255 || !w.isBlockLoaded(q) || (mask != null && !mask.contains(q.toLong()))) {
                        states[i] = Clipboard.AIR;
                        continue;
                    }
                    IBlockState s = w.getBlockState(q);
                    states[i] = Block.getStateId(s);
                    if (s.getBlock().hasTileEntity()) {
                        TileEntity te = w.getTileEntity(q);
                        if (te != null) {
                            tiles.put(i, te.writeToNBT(new NBTTagCompound()));
                        }
                    }
                }
            }
        }
        return new Clipboard(sx, sy, sz, states, tiles);
    }

    /** Coloca el portapapeles con su esquina mínima en (ox, oy, oz). */
    private static void place(Builder b, Clipboard c, int ox, int oy, int oz, boolean pasteAir, boolean keep) {
        for (int y = 0; y < c.sy; y++) {
            for (int z = 0; z < c.sz; z++) {
                for (int x = 0; x < c.sx; x++) {
                    int i = c.index(x, y, z);
                    int id = c.states[i];
                    if (id == Clipboard.AIR && !pasteAir) {
                        continue;
                    }
                    b.set(ox + x, oy + y, oz + z, Block.getStateById(id), c.tiles.get(i), keep);
                }
            }
        }
    }

    // ---- Trazos de pincel (varios aplicados, una sola edición deshacible) ---------------------

    /** dedupe = cada bloque se toca una sola vez en el trazo (false para herramientas acumulativas). */
    public static void strokeStart(final String name, final boolean dedupe) {
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                stroke = new Builder(server.getWorld(dim), dim, 4096, dedupe);
                strokeName = name;
            }
        });
    }

    /** Una operación de terreno que escribe cambios por un Setter (Elevation, Distort...). */
    public interface TerrainOp {
        void apply(World w, Terrain.Setter out);
    }

    /** Aplica una operación de terreno dentro del trazo actual. */
    public static void strokeOp(final TerrainOp op) {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = stroke;
                if (b == null) {
                    return;
                }
                op.apply(b.w, new Terrain.Setter() {
                    @Override
                    public void set(int x, int y, int z, IBlockState state) {
                        b.set(x, y, z, state, null, false);
                    }
                });
            }
        });
    }

    /** Una roca: una esfera deformada por ruido (Rock), colocada con el bloque dado, como una sola edición. */
    public static void rock(final BlockPos center, final int radius, final double noisiness, final double scale,
            final long seed, final IBlockState state, final MaskSpec mask) {
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Builder b = new Builder(server.getWorld(dim), dim, 4096, false);
                Noise noise = new Noise(seed);
                int reach = radius * 2;
                int cx = center.getX(), cy = center.getY(), cz = center.getZ();
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        for (int dx = -reach; dx <= reach; dx++) {
                            double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / (radius + 0.5);
                            if (d > 1.0 + noisiness) {
                                continue;
                            }
                            double n = Math.max(-1.0, Math.min(1.0, noise.fbm((cx + dx) / scale + 0.137,
                                    (cy + dy) / scale + 0.271, (cz + dz) / scale + 0.389, 2) * 2.0));
                            if (d < 1.0 + noisiness * n && (mask == null || mask.test(b.w, cx + dx, cy + dy, cz + dz))) {
                                b.set(cx + dx, cy + dy, cz + dz, state, null, false);
                            }
                        }
                    }
                }
                b.commit("Rock");
            }
        });
    }

    /**
     * Floodfill: rellena con state los huecos (bloques no sólidos) conectados al de partida. Se avanza
     * en horizontal, hacia abajo y/o hacia arriba según los permisos, hasta limit bloques.
     */
    public static void floodFill(final BlockPos start, final IBlockState state, final int limit, final boolean up,
            final boolean down, final boolean horizontal, final boolean corners) {
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Builder b = new Builder(server.getWorld(dim), dim, 4096, false);
                IBlockState s0 = b.peek(start.getX(), start.getY(), start.getZ());
                if (s0 == null || s0.getMaterial().isSolid()) {
                    status = "Floodfill: el punto de partida no es un hueco";
                    return;
                }
                List<int[]> steps = new ArrayList<int[]>();
                if (horizontal) {
                    steps.add(new int[] {1, 0, 0});
                    steps.add(new int[] {-1, 0, 0});
                    steps.add(new int[] {0, 0, 1});
                    steps.add(new int[] {0, 0, -1});
                    if (corners) {
                        steps.add(new int[] {1, 0, 1});
                        steps.add(new int[] {1, 0, -1});
                        steps.add(new int[] {-1, 0, 1});
                        steps.add(new int[] {-1, 0, -1});
                    }
                }
                if (up) {
                    steps.add(new int[] {0, 1, 0});
                }
                if (down) {
                    steps.add(new int[] {0, -1, 0});
                }
                Set<Long> seen = new HashSet<Long>();
                ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
                int[] first = {start.getX(), start.getY(), start.getZ()};
                seen.add(Selection.key(first[0], first[1], first[2]));
                queue.add(first);
                int filled = 0;
                while (!queue.isEmpty() && filled < limit) {
                    int[] c = queue.poll();
                    b.set(c[0], c[1], c[2], state, null, false);
                    filled++;
                    for (int[] o : steps) {
                        int[] nx = {c[0] + o[0], c[1] + o[1], c[2] + o[2]};
                        if (!seen.add(Selection.key(nx[0], nx[1], nx[2]))) {
                            continue;
                        }
                        IBlockState s = b.peek(nx[0], nx[1], nx[2]);
                        if (s != null && !s.getMaterial().isSolid()) {
                            queue.add(nx);
                        }
                    }
                }
                b.commit("Floodfill" + (filled >= limit ? " (límite alcanzado)" : ""));
            }
        });
    }

    /** Coloca state en las posiciones dadas (claves de BlockPos.toLong) como una sola edición. */
    public static void applyPositions(final String name, final long[] positions, final IBlockState state,
            final boolean keepExisting) {
        if (positions.length > MAX_BLOCKS) {
            status = name + ": demasiados bloques (" + positions.length + ", máximo " + MAX_BLOCKS + ")";
            return;
        }
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Builder b = new Builder(server.getWorld(dim), dim, positions.length, false);
                for (long k : positions) {
                    BlockPos p = BlockPos.fromLong(k);
                    b.set(p.getX(), p.getY(), p.getZ(), state, null, keepExisting);
                }
                b.commit(name);
            }
        });
    }

    /**
     * Simulate Gravity: los bloques de cada columna de la caja caen conservando su orden hasta apoyarse en
     * el primer bloque sólido que haya debajo de la caja (o en el fondo de la caja si ya está apoyada).
     */
    public static void gravity() {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        if (Selection.mask != null) {
            status = "Simulate Gravity: solo funciona con selecciones en caja";
            return;
        }
        final int[] box = box();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, capacity(), false);
                IBlockState air = Blocks.AIR.getDefaultState();
                for (int z = box[2]; z <= box[5]; z++) {
                    for (int x = box[0]; x <= box[3]; x++) {
                        List<IBlockState> states = new ArrayList<IBlockState>();
                        List<NBTTagCompound> tiles = new ArrayList<NBTTagCompound>();
                        boolean loaded = true;
                        for (int y = box[1]; y <= box[4] && loaded; y++) {
                            IBlockState s = b.peek(x, y, z);
                            if (s == null) {
                                loaded = false;
                            } else if (s.getBlock() != Blocks.AIR) {
                                states.add(s);
                                TileEntity te = s.getBlock().hasTileEntity() ? b.w.getTileEntity(new BlockPos(x, y, z)) : null;
                                tiles.add(te == null ? null : te.writeToNBT(new NBTTagCompound()));
                            }
                        }
                        if (!loaded) {
                            continue;
                        }
                        // La columna cae hasta apoyarse en el primer bloque sólido que haya bajo la caja.
                        int base = box[1];
                        while (base > 0) {
                            IBlockState below = b.peek(x, base - 1, z);
                            if (below == null || below.getMaterial().isSolid()) {
                                break;
                            }
                            base--;
                        }
                        for (int y = base, i = 0; y <= box[4]; y++, i++) {
                            b.set(x, y, z, i < states.size() ? states.get(i) : air, i < states.size() ? tiles.get(i) : null,
                                    false);
                        }
                    }
                }
                b.commit("Simulate Gravity");
            }
        });
    }

    /** Fill Gaps: rellena con state el espacio no sólido de la caja al que no se llega desde fuera. */
    public static void fillGaps(final IBlockState state) {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        if (Selection.mask != null) {
            status = "Fill Gaps: solo funciona con selecciones en caja";
            return;
        }
        final int[] box = box();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, capacity(), false);
                final int sx = box[3] - box[0] + 1, sy = box[4] - box[1] + 1, sz = box[5] - box[2] + 1;
                boolean[] open = new boolean[sx * sy * sz];
                boolean[] reached = new boolean[open.length];
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        for (int x = 0; x < sx; x++) {
                            IBlockState s = b.peek(box[0] + x, box[1] + y, box[2] + z);
                            open[(y * sz + z) * sx + x] = s != null && !s.getMaterial().isSolid();
                        }
                    }
                }
                // Inundación desde los espacios libres de la superficie de la caja.
                int[] queue = new int[open.length];
                int head = 0, tail = 0;
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        for (int x = 0; x < sx; x++) {
                            int i = (y * sz + z) * sx + x;
                            boolean edge = x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1;
                            if (edge && open[i]) {
                                reached[i] = true;
                                queue[tail++] = i;
                            }
                        }
                    }
                }
                int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                while (head < tail) {
                    int i = queue[head++];
                    int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
                    for (int[] o : d) {
                        int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                            continue;
                        }
                        int j = (ny * sz + nz) * sx + nx;
                        if (open[j] && !reached[j]) {
                            reached[j] = true;
                            queue[tail++] = j;
                        }
                    }
                }
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        for (int x = 0; x < sx; x++) {
                            int i = (y * sz + z) * sx + x;
                            if (open[i] && !reached[i]) {
                                b.set(box[0] + x, box[1] + y, box[2] + z, state, null, false);
                            }
                        }
                    }
                }
                b.commit("Fill Gaps");
            }
        });
    }

    /**
     * Type Replace: los bloques de la selección de la misma clase que target (escaleras por escaleras,
     * losas por losas...) pasan a ser target conservando sus propiedades (orientación, mitad, forma...).
     */
    public static void typeReplace(final IBlockState target) {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, cap, false);
                final Block to = target.getBlock();
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s != null && s.getBlock() != to && s.getBlock().getClass() == to.getClass()) {
                            b.set(x, y, z, copyProperties(s, to.getDefaultState()), null, false);
                        }
                    }
                });
                b.commit("Type Replace " + Block.REGISTRY.getNameForObject(to));
            }
        });
    }

    /** Copia a dst las propiedades de src que dst también tiene (con un valor permitido). */
    private static IBlockState copyProperties(IBlockState src, IBlockState dst) {
        IBlockState out = dst;
        for (IProperty<?> p : src.getPropertyKeys()) {
            if (out.getPropertyKeys().contains(p) && p.getAllowedValues().contains(src.getValue(p))) {
                out = withProperty(out, p, src.getValue(p));
            }
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState withProperty(IBlockState state, IProperty p, Comparable value) {
        return state.withProperty(p, value);
    }

    /** Un sello del pincel: qué pintar, dónde y con qué restricciones. */
    public static final class Stamp {
        /** true coloca el bloque en todo el volumen; false solo pinta bloques sólidos. */
        public boolean draw;
        public BlockPos center;
        public int radius;
        public int shape;
        public boolean maskSurface;
        public IBlockState state;
        /** Noise Painter, Gradient Painter...: elige el bloque por posición (null = usar state). */
        public StatePicker noise;
        /** Tool Mask: restringe las posiciones que se tocan (null = sin máscara). */
        public MaskSpec mask;
    }

    public static void strokeBrush(final Stamp s) {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Builder b = stroke;
                if (b == null) {
                    return;
                }
                int cx = s.center.getX(), cy = s.center.getY(), cz = s.center.getZ();
                int r = s.radius;
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        for (int dx = -r; dx <= r; dx++) {
                            if (!Brush.contains(s.shape, dx, dy, dz, r)) {
                                continue;
                            }
                            int x = cx + dx, y = cy + dy, z = cz + dz;
                            if (!s.draw && !paintable(b, x, y, z, s.maskSurface)) {
                                continue;
                            }
                            if (s.mask != null && !s.mask.test(b.w, x, y, z)) {
                                continue;
                            }
                            IBlockState chosen = s.noise != null ? s.noise.pick(x, y, z) : s.state;
                            if (chosen != null) {
                                b.set(x, y, z, chosen, null, false);
                            }
                        }
                    }
                }
            }
        });
    }

    /** Un sello de Smooth / Melt / Weld (ver {@link Terrain}). */
    public static void strokeTerrain(final BlockPos center, final int radius, final int shape, final int sigma,
            final int ratio, final IBlockState add, final MaskSpec mask, final double jitter, final long seed) {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = stroke;
                if (b == null) {
                    return;
                }
                Terrain.smooth(b.w, center.getX(), center.getY(), center.getZ(), radius, shape, sigma, ratio, add,
                        mask, jitter, seed, new Terrain.Setter() {
                            @Override
                            public void set(int x, int y, int z, IBlockState state) {
                                b.set(x, y, z, state, null, false);
                            }
                        });
            }
        });
    }

    /**
     * Extrude: la superficie de bloques "iguales" (según compare, como en Magic Select) que mira hacia
     * face y contiene a pos crece count capas hacia fuera o, con shrink, se come count capas.
     */
    public static void extrude(final BlockPos pos, final EnumFacing face, final int count, final boolean shrink,
            final int compare) {
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, 4096, false);
                IBlockState origin = b.peek(pos.getX(), pos.getY(), pos.getZ());
                if (origin == null || !MagicSelect.matches(compare, origin, origin)) {
                    status = "Extrude: ese bloque no cuenta con la comparación elegida";
                    return;
                }
                // Superficie: se recorre el plano de la cara (8 vecinos) por bloques iguales con la cara libre.
                int fx = face.getFrontOffsetX(), fy = face.getFrontOffsetY(), fz = face.getFrontOffsetZ();
                List<int[]> offsets = new ArrayList<int[]>();
                for (int da = -1; da <= 1; da++) {
                    for (int db = -1; db <= 1; db++) {
                        if (da == 0 && db == 0) {
                            continue;
                        }
                        switch (face.getAxis()) {
                            case X: offsets.add(new int[] {0, da, db}); break;
                            case Y: offsets.add(new int[] {da, 0, db}); break;
                            default: offsets.add(new int[] {da, db, 0}); break;
                        }
                    }
                }
                List<int[]> surface = new ArrayList<int[]>();
                Set<Long> seen = new HashSet<Long>();
                ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
                int[] start = {pos.getX(), pos.getY(), pos.getZ()};
                if (!isSurface(b, start, fx, fy, fz, origin, compare)) {
                    status = "Extrude: esa cara no está libre";
                    return;
                }
                seen.add(Selection.key(start[0], start[1], start[2]));
                queue.add(start);
                while (!queue.isEmpty() && surface.size() < 100_000) {
                    int[] c = queue.poll();
                    surface.add(c);
                    for (int[] o : offsets) {
                        int[] n = {c[0] + o[0], c[1] + o[1], c[2] + o[2]};
                        if (seen.add(Selection.key(n[0], n[1], n[2])) && isSurface(b, n, fx, fy, fz, origin, compare)) {
                            queue.add(n);
                        }
                    }
                }
                IBlockState air = Blocks.AIR.getDefaultState();
                for (int[] c : surface) {
                    IBlockState own = b.peek(c[0], c[1], c[2]);
                    for (int k = shrink ? 0 : 1; k <= (shrink ? count - 1 : count); k++) {
                        int x = shrink ? c[0] - fx * k : c[0] + fx * k;
                        int y = shrink ? c[1] - fy * k : c[1] + fy * k;
                        int z = shrink ? c[2] - fz * k : c[2] + fz * k;
                        IBlockState t = b.peek(x, y, z);
                        if (t == null) {
                            continue;
                        }
                        if (shrink) {
                            if (t.getMaterial().isSolid()) {
                                b.set(x, y, z, air, null, false);
                            }
                        } else if (!t.getMaterial().isSolid()) {
                            b.set(x, y, z, own, null, false);
                        }
                    }
                }
                b.commit(shrink ? "Extrude -" + count : "Extrude +" + count);
            }
        });
    }

    /** ¿La celda c es del mismo tipo que origin y tiene libre la cara que mira hacia (fx, fy, fz)? */
    private static boolean isSurface(Builder b, int[] c, int fx, int fy, int fz, IBlockState origin, int compare) {
        IBlockState s = b.peek(c[0], c[1], c[2]);
        if (s == null || !MagicSelect.matches(compare, origin, s)) {
            return false;
        }
        IBlockState front = b.peek(c[0] + fx, c[1] + fy, c[2] + fz);
        return front != null && !front.getMaterial().isSolid();
    }

    public static void strokeEnd() {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                if (stroke != null) {
                    stroke.commit(strokeName);
                    stroke = null;
                }
            }
        });
    }

    /** Painter: solo bloques sólidos y, con maskSurface, solo los que tocan algo no sólido. */
    private static boolean paintable(Builder b, int x, int y, int z, boolean maskSurface) {
        IBlockState s = b.peek(x, y, z);
        if (s == null || !s.getMaterial().isSolid()) {
            return false;
        }
        if (!maskSurface) {
            return true;
        }
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : d) {
            IBlockState n = b.peek(x + o[0], y + o[1], z + o[2]);
            if (n != null && !n.getMaterial().isSolid()) {
                return true;
            }
        }
        return false;
    }

    // ---- Fill Nearest, Smoothsnow, Trigger Updates ---------------------------------------

    /**
     * Fill Nearest: cada bloque de aire de la selección toma el estado del bloque no aire más cercano de la
     * selección (distancia en pasos por las 6 caras; gana el primero que llega).
     */
    public static void fillNearest() {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, capacity(), false);
                final int sx = box[3] - box[0] + 1, sy = box[4] - box[1] + 1, sz = box[5] - box[2] + 1;
                final boolean[] in = new boolean[sx * sy * sz];
                final IBlockState[] st = new IBlockState[in.length];
                final int[] owner = new int[in.length];
                Arrays.fill(owner, -1);
                final int[] queue = new int[in.length];
                final int[] tail = {0};
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s == null) {
                            return;
                        }
                        int i = ((y - box[1]) * sz + (z - box[2])) * sx + (x - box[0]);
                        in[i] = true;
                        st[i] = s;
                        if (s.getBlock() != Blocks.AIR) {
                            owner[i] = i;
                            queue[tail[0]++] = i;
                        }
                    }
                });
                int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int head = 0; head < tail[0]; head++) {
                    int i = queue[head];
                    int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
                    for (int[] o : d) {
                        int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                            continue;
                        }
                        int j = (ny * sz + nz) * sx + nx;
                        if (in[j] && owner[j] < 0) {
                            owner[j] = owner[i];
                            queue[tail[0]++] = j;
                        }
                    }
                }
                if (tail[0] == 0) {
                    status = "Fill Nearest: la selección solo tiene aire";
                    return;
                }
                for (int i = 0; i < in.length; i++) {
                    if (in[i] && owner[i] >= 0 && owner[i] != i) {
                        b.set(box[0] + i % sx, box[1] + i / (sx * sz), box[2] + (i / sx) % sz, st[owner[i]], null, false);
                    }
                }
                b.commit("Fill Nearest");
            }
        });
    }

    /**
     * Smoothsnow: pone capas de nieve (1 a maxLayers) sobre la superficie de cada columna de la selección.
     * La altura de la nieve sigue una media de la altura del terreno en un radio, de modo que los huecos y
     * escalones se rellenan con más capas y la cobertura se ve continua. Solo ocupa aire y plantas
     * reemplazables (hierba alta...); si ya hay nieve, la sustituye (no se acumula al repetir).
     */
    public static void smoothSnow(final int maxLayers, final int radius) {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                final Builder b = new Builder(w, dim, capacity(), false);
                final int r = radius;
                final int x0 = box[0] - r, z0 = box[2] - r;
                final int gw = box[3] - box[0] + 1 + 2 * r, gd = box[5] - box[2] + 1 + 2 * r;
                if ((long) gw * gd > MAX_BLOCKS) {
                    status = "Smoothsnow: área demasiado grande";
                    return;
                }
                final int top = Math.min(box[4], 254), bottom = Math.max(box[1], 0);
                // Altura de la cara superior del terreno en cada columna (-1 = sin superficie / sin cargar).
                int[] height = new int[gw * gd];
                for (int gz = 0; gz < gd; gz++) {
                    for (int gx = 0; gx < gw; gx++) {
                        int h = -1;
                        for (int y = top; y >= bottom; y--) {
                            IBlockState s = b.peek(x0 + gx, y, z0 + gz);
                            if (s == null) {
                                break;
                            }
                            if (s.getMaterial().isSolid() && s.getBlock() != Blocks.SNOW_LAYER) {
                                h = y + 1;
                                break;
                            }
                        }
                        height[gz * gw + gx] = h;
                    }
                }
                final boolean[] selected = new boolean[gw * gd];
                if (mask != null) {
                    for (Long k : mask) {
                        BlockPos p = BlockPos.fromLong(k);
                        selected[(p.getZ() - z0) * gw + (p.getX() - x0)] = true;
                    }
                } else {
                    for (int gz = r; gz < gd - r; gz++) {
                        for (int gx = r; gx < gw - r; gx++) {
                            selected[gz * gw + gx] = true;
                        }
                    }
                }
                for (int gz = r; gz < gd - r; gz++) {
                    for (int gx = r; gx < gw - r; gx++) {
                        int h = height[gz * gw + gx];
                        if (!selected[gz * gw + gx] || h < 0) {
                            continue;
                        }
                        double sum = 0;
                        int n = 0;
                        for (int dz = -r; dz <= r; dz++) {
                            for (int dx = -r; dx <= r; dx++) {
                                int o = height[(gz + dz) * gw + gx + dx];
                                if (o >= 0) {
                                    sum += o;
                                    n++;
                                }
                            }
                        }
                        int layers = Math.max(1, Math.min(maxLayers, 1 + (int) Math.round(Math.max(0, sum / n - h) * 8)));
                        int x = x0 + gx, z = z0 + gz;
                        IBlockState above = b.peek(x, h, z);
                        if (above == null || h > 255) {
                            continue;
                        }
                        Block ab = above.getBlock();
                        boolean free = ab == Blocks.AIR || ab == Blocks.SNOW_LAYER
                                || above.getMaterial().isReplaceable() && !above.getMaterial().isLiquid()
                                && !(ab instanceof net.minecraft.block.BlockDoublePlant);
                        if (free) {
                            b.set(x, h, z, Blocks.SNOW_LAYER.getDefaultState()
                                    .withProperty(net.minecraft.block.BlockSnow.LAYERS, layers), null, false);
                        }
                    }
                }
                b.commit("Smoothsnow");
            }
        });
    }

    /**
     * Trigger Updates: avisa a cada bloque de la selección de que sus vecinos cambiaron, de modo que los que no
     * pueden sostenerse (carteles, antorchas, plantas flotantes...) desaparecen, sin soltar objetos. Ignora
     * fluidos y bloques con gravedad. Lo que se rompe en cadena fuera de la selección no entra en el historial.
     */
    public static void triggerUpdates() {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                final Builder b = new Builder(w, dim, cap, false);
                final List<BlockPos> cells = new ArrayList<BlockPos>();
                final List<IBlockState> was = new ArrayList<IBlockState>();
                final List<NBTTagCompound> tiles = new ArrayList<NBTTagCompound>();
                final World fw = w;
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s == null || s.getBlock() == Blocks.AIR || s.getMaterial().isLiquid()
                                || s.getBlock() instanceof net.minecraft.block.BlockFalling) {
                            return;
                        }
                        BlockPos p = new BlockPos(x, y, z);
                        cells.add(p);
                        was.add(s);
                        TileEntity te = s.getBlock().hasTileEntity() ? fw.getTileEntity(p) : null;
                        tiles.add(te == null ? null : te.writeToNBT(new NBTTagCompound()));
                    }
                });
                List<Integer> changed = new ArrayList<Integer>();
                List<IBlockState> now = new ArrayList<IBlockState>();
                net.minecraft.world.GameRules rules = w.getGameRules();
                String drops = rules.getString("doTileDrops");
                rules.setOrCreateGameRule("doTileDrops", "false");
                try {
                    for (int i = 0; i < cells.size(); i++) {
                        w.neighborChanged(cells.get(i), was.get(i).getBlock(), cells.get(i));
                    }
                    for (int i = 0; i < cells.size(); i++) {
                        IBlockState s = w.getBlockState(cells.get(i));
                        if (s != was.get(i)) {
                            changed.add(i);
                            now.add(s);
                        }
                    }
                } finally {
                    rules.setOrCreateGameRule("doTileDrops", drops);
                }
                // El Builder anota el estado "antes" leyéndolo del mundo: se devuelve el original y se reaplica.
                for (int k : changed) {
                    w.setBlockState(cells.get(k), was.get(k), 2);
                    if (tiles.get(k) != null) {
                        TileEntity te = w.getTileEntity(cells.get(k));
                        if (te != null) {
                            te.readFromNBT(tiles.get(k));
                        }
                    }
                }
                for (int j = 0; j < changed.size(); j++) {
                    BlockPos p = cells.get(changed.get(j));
                    b.set(p.getX(), p.getY(), p.getZ(), now.get(j), null, false);
                }
                b.commit("Trigger Updates");
            }
        });
    }

    /** Rellena la selección eligiendo el bloque de cada posición con picker (null = dejarla como está). */
    public static void fillPicker(final String name, final StatePicker picker) {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, cap, false);
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = picker.pick(x, y, z);
                        if (s != null) {
                            b.set(x, y, z, s, null, false);
                        }
                    }
                });
                b.commit(name);
            }
        });
    }

    /** Waterlog: el aire de la selección pasa a agua (en 1.12 no existen los bloques con agua dentro). */
    public static void waterlog() {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final Builder b = new Builder(server.getWorld(dim), dim, cap, false);
                final IBlockState water = Blocks.WATER.getDefaultState();
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s != null && s.getBlock() == Blocks.AIR) {
                            b.set(x, y, z, water, null, false);
                        }
                    }
                });
                b.commit("Waterlog");
            }
        });
    }

    /**
     * Generate Colour Field: la caja de la selección se llena con un campo de color CIELAB, con L en X, a (verde a
     * rojo) en Y y b (azul a amarillo) en Z, usando el bloque completo y opaco de color medio más cercano.
     * Requiere los colores de bloques (BlockColors.ensureBuilt desde el hilo de render).
     */
    public static void colourField() {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final List<IBlockState> states = new ArrayList<IBlockState>();
                final List<double[]> labs = new ArrayList<double[]>();
                Set<Integer> seen = new HashSet<Integer>();
                for (Block bl : Block.REGISTRY) {
                    if (bl instanceof net.minecraft.block.BlockFalling || bl.hasTileEntity()) {
                        continue;
                    }
                    for (int meta = 0; meta < 16; meta++) {
                        IBlockState s;
                        try {
                            s = bl.getStateFromMeta(meta);
                        } catch (RuntimeException e) {
                            continue;
                        }
                        int id = Block.getStateId(s);
                        int rgb = BlockColors.get(id);
                        if (rgb < 0 || !seen.add(id) || !s.isFullCube() || !s.isOpaqueCube()) {
                            continue;
                        }
                        states.add(s);
                        labs.add(Lab.fromRgb(rgb));
                    }
                }
                if (states.isEmpty()) {
                    status = "Colour Field: aún no hay colores de bloques (abre el editor y vuelve a probar)";
                    return;
                }
                final double[][] table = labs.toArray(new double[0][]);
                final int sx = box[3] - box[0], sy = box[4] - box[1], sz = box[5] - box[2];
                final Builder b = new Builder(server.getWorld(dim), dim, cap, false);
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        double L = sx == 0 ? 50 : 100.0 * (x - box[0]) / sx;
                        double a = sy == 0 ? 0 : -128.0 + 255.0 * (y - box[1]) / sy;
                        double bb = sz == 0 ? 0 : -128.0 + 255.0 * (z - box[2]) / sz;
                        int best = 0;
                        double bd = Double.MAX_VALUE;
                        for (int i = 0; i < table.length; i++) {
                            double dl = table[i][0] - L, da = table[i][1] - a, db = table[i][2] - bb;
                            double dd = dl * dl + da * da + db * db;
                            if (dd < bd) {
                                bd = dd;
                                best = i;
                            }
                        }
                        b.set(x, y, z, states.get(best), null, false);
                    }
                });
                b.commit("Generate Colour Field");
            }
        });
    }

    /**
     * Animated Rebuild: vacía la selección (una edición deshacible) y la reconstruye con retrasos, del centro hacia
     * fuera. Solo hay una a la vez.
     */
    public static void animatedRebuild() {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        if (AnimatedRebuild.active()) {
            status = "Animated Rebuild: ya hay una en curso (Cancel o Undo)";
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        final float start = AnimatedRebuild.startDelay[0], air = AnimatedRebuild.airDelay[0],
                same = AnimatedRebuild.sameDelay[0], diff = AnimatedRebuild.differentDelay[0],
                extra = AnimatedRebuild.randomExtra[0], max = AnimatedRebuild.maxDelay[0],
                hm = AnimatedRebuild.horizontal[0], vm = AnimatedRebuild.vertical[0];
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                final World w = server.getWorld(dim);
                final Builder b = new Builder(w, dim, cap, false);
                final List<long[]> cells = new ArrayList<long[]>();
                final List<NBTTagCompound> tiles = new ArrayList<NBTTagCompound>();
                final double cx = (box[0] + box[3] + 1) / 2.0, cy = (box[1] + box[4] + 1) / 2.0, cz = (box[2] + box[5] + 1) / 2.0;
                iterate(box, mask, new Visit() {
                    @Override
                    public void at(int x, int y, int z) {
                        IBlockState s = b.peek(x, y, z);
                        if (s == null) {
                            return;
                        }
                        double dx = (x + 0.5 - cx) * hm, dy = (y + 0.5 - cy) * vm, dz = (z + 0.5 - cz) * hm;
                        long d = (long) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 1000);
                        // {orden por distancia, posición, estado, índice del tile (-1 = ninguno)}
                        NBTTagCompound tag = null;
                        if (s.getBlock().hasTileEntity()) {
                            TileEntity te = w.getTileEntity(new BlockPos(x, y, z));
                            tag = te == null ? null : te.writeToNBT(new NBTTagCompound());
                        }
                        cells.add(new long[] {d, new BlockPos(x, y, z).toLong(), Block.getStateId(s), tag == null ? -1 : tiles.size()});
                        if (tag != null) {
                            tiles.add(tag);
                        }
                    }
                });
                if (cells.isEmpty()) {
                    status = "Animated Rebuild: no hay nada que reconstruir";
                    return;
                }
                java.util.Collections.sort(cells, new Comparator<long[]>() {
                    @Override
                    public int compare(long[] a, long[] c) {
                        return a[0] < c[0] ? -1 : a[0] > c[0] ? 1 : 0;
                    }
                });
                int air0 = Clipboard.AIR;
                int solid = 0;
                for (long[] c : cells) {
                    if (c[2] != air0) {
                        solid++;
                    }
                }
                if (solid == 0) {
                    status = "Animated Rebuild: la selección solo tiene aire";
                    return;
                }
                AnimatedRebuild.Job job = new AnimatedRebuild.Job();
                job.dim = dim;
                job.pos = new long[solid];
                job.state = new int[solid];
                job.tile = new NBTTagCompound[solid];
                job.time = new double[solid];
                java.util.Random rnd = new java.util.Random(cells.size());
                double t = start;
                long prev = -1;
                int k = 0;
                for (long[] c : cells) {
                    boolean isAir = c[2] == air0;
                    double delay = isAir ? air : c[2] == prev ? same : diff;
                    delay = Math.min(max, delay + rnd.nextDouble() * extra);
                    t += delay;
                    if (!isAir) {
                        prev = c[2];
                        job.pos[k] = c[1];
                        job.state[k] = (int) c[2];
                        job.tile[k] = c[3] < 0 ? null : tiles.get((int) c[3]);
                        job.time[k] = t;
                        k++;
                    }
                }
                final IBlockState empty = Blocks.AIR.getDefaultState();
                for (int i = 0; i < job.pos.length; i++) {
                    BlockPos p = BlockPos.fromLong(job.pos[i]);
                    b.set(p.getX(), p.getY(), p.getZ(), empty, null, false);
                }
                b.commit("Animated Rebuild");
                AnimatedRebuild.start(job);
                status = String.format(Locale.ROOT, "Animated Rebuild: %d bloques, unos %.0f s", solid, t / 20.0);
            }
        });
    }

    /** Autoshade: sustituye los bloques de la superficie de la selección por los de una paleta según la luz que reciben. */
    public static void autoshade(final Autoshade.Params params) {
        if (!selectionOk() || !boxOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int cap = capacity();
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                final Builder b = new Builder(w, dim, cap, false);
                int n = Autoshade.compute(w, box, mask, params, new Terrain.Setter() {
                    @Override
                    public void set(int x, int y, int z, IBlockState state) {
                        b.set(x, y, z, state, null, false);
                    }
                });
                if (n < 0) {
                    status = "Autoshade: hace falta una paleta de al menos 2 bloques (o 2 tipos distintos en la selección)";
                } else {
                    b.commit("Autoshade");
                }
            }
        });
    }

    // ---- Utilidades -----------------------------------------------------------------------

    private static int[] box() {
        return new int[] {Selection.min(0), Selection.min(1), Selection.min(2),
            Selection.max(0), Selection.max(1), Selection.max(2)};
    }

    /** Capacidad inicial de un Builder para la selección actual. */
    private static int capacity() {
        return (int) Math.min(Selection.volume(), MAX_BLOCKS);
    }

    private static int dim() {
        return Minecraft.getMinecraft().player.dimension;
    }

    /** Recorre las posiciones seleccionadas: las de la máscara si la hay, si no toda la caja. */
    private static void iterate(int[] box, Set<Long> mask, Visit v) {
        if (mask != null) {
            for (Long k : mask) {
                BlockPos p = BlockPos.fromLong(k);
                v.at(p.getX(), p.getY(), p.getZ());
            }
            return;
        }
        for (int y = box[1]; y <= box[4]; y++) {
            for (int z = box[2]; z <= box[5]; z++) {
                for (int x = box[0]; x <= box[3]; x++) {
                    v.at(x, y, z);
                }
            }
        }
    }

    private static boolean selects(int mode, int x, int y, int z, int[] box) {
        boolean ex = x == box[0] || x == box[3];
        boolean ey = y == box[1] || y == box[4];
        boolean ez = z == box[2] || z == box[5];
        switch (mode) {
            case OUTLINE:
                return (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0) >= 2;
            case WALLS:
                return ex || ez;
            case SHELL:
                return ex || ey || ez;
            case TOP:
                return y == box[4];
            case BOTTOM:
                return y == box[1];
            case HOLLOW:
                return !(ex || ey || ez);
            default:
                return true;
        }
    }

    /** Exporta la selección (con su máscara) a un archivo: .schematic, .schem, .nbt o .csv (x,y,z,bloque). */
    public static boolean exportSelection(final File f) {
        if (!selectionOk() || !boxOk()) {
            return false;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Clipboard c = readClipboard(server.getWorld(dim), box, mask);
                try {
                    if (f.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                        try (java.io.Writer out = new java.io.BufferedWriter(new java.io.FileWriter(f))) {
                            out.write("x,y,z,block\n");
                            for (int y = 0; y < c.sy; y++) {
                                for (int z = 0; z < c.sz; z++) {
                                    for (int x = 0; x < c.sx; x++) {
                                        int id = c.states[c.index(x, y, z)];
                                        if (id != Clipboard.AIR) {
                                            out.write((box[0] + x) + "," + (box[1] + y) + "," + (box[2] + z) + ","
                                                    + BlockStates.toText(Block.getStateById(id)) + "\n");
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Schematics.export(c, f);
                    }
                    status = "Exportado: " + f.getPath() + " (" + c.solidCount() + " bloques)";
                } catch (java.io.IOException e) {
                    LiteModLitBuild.LOG.error("No se pudo exportar la selección", e);
                    status = "No se pudo exportar: " + e.getMessage();
                }
            }
        });
        return true;
    }

    /** Modify: añade copias de la selección o la reconstruye (Revolve, Twist). Devuelve si se lanzó. */
    public static boolean modify(final Modify.Spec spec) {
        if (!selectionOk() || !boxOk()) {
            return false;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                Clipboard c = readClipboard(w, box, mask);
                Map<Long, Integer> out = Modify.run(spec, c, box[0], box[1], box[2]);
                if (out == null) {
                    return;
                }
                if (out.isEmpty()) {
                    status = "Modify: no hay bloques que colocar";
                    return;
                }
                final Builder b = new Builder(w, dim, out.size() + c.states.length, false);
                if (spec.consumesOriginal()) {
                    final IBlockState air = Blocks.AIR.getDefaultState();
                    iterate(box, mask, new Visit() {
                        @Override
                        public void at(int x, int y, int z) {
                            b.set(x, y, z, air, null, false);
                        }
                    });
                }
                for (Map.Entry<Long, Integer> e : out.entrySet()) {
                    BlockPos p = BlockPos.fromLong(e.getKey());
                    b.set(p.getX(), p.getY(), p.getZ(), Block.getStateById(e.getValue()), null, false);
                }
                b.commit("Modify: " + spec.label());
            }
        });
        return true;
    }

    // ---- Biomas ---------------------------------------------------------------------------

    /** Trazo de Biome Painter en curso: columna -> bioma que tenía. Solo se toca desde el hilo del servidor. */
    private static Map<Long, Byte> biomeStroke;
    private static byte biomeStrokeId;
    private static int biomeStrokeDim;

    private static long chunkKey(int cx, int cz) {
        return ((long) cz << 32) | (cx & 0xFFFFFFFFL);
    }

    /** Pone el bioma de la columna (x, z) si su chunk está cargado. */
    private static boolean setBiome(World w, int x, int z, byte biome) {
        BlockPos p = new BlockPos(x, 64, z);
        if (!w.isBlockLoaded(p)) {
            return false;
        }
        Chunk ch = w.getChunkFromBlockCoords(p);
        ch.getBiomeArray()[(z & 15) << 4 | (x & 15)] = biome;
        ch.markDirty();
        return true;
    }

    /** Manda de nuevo los chunks al cliente para que repinte el color de hierba, hojas y agua. */
    private static void resend(World w, Set<Long> chunks) {
        if (!(w instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) w;
        for (long k : chunks) {
            int cx = (int) k, cz = (int) (k >> 32);
            PlayerChunkMapEntry entry = ws.getPlayerChunkMap().getEntry(cx, cz);
            if (entry != null) {
                entry.sendPacket(new SPacketChunkData(w.getChunkFromChunkCoords(cx, cz), 65535));
            }
        }
    }

    public static void biomeStrokeStart() {
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                biomeStroke = new LinkedHashMap<Long, Byte>();
                biomeStrokeDim = dim;
            }
        });
    }

    /** Un sello de Biome Painter: las columnas del círculo de radio radius pasan al bioma biomeId. */
    public static void biomePaint(final int cx, final int cz, final int radius, final int biomeId) {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                if (biomeStroke == null) {
                    return;
                }
                World w = server.getWorld(biomeStrokeDim);
                byte id = (byte) biomeId;
                biomeStrokeId = id;
                Set<Long> chunks = new HashSet<Long>();
                double lim = radius + 0.5;
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (dx * dx + dz * dz > lim * lim) {
                            continue;
                        }
                        int x = cx + dx, z = cz + dz;
                        BlockPos p = new BlockPos(x, 64, z);
                        if (!w.isBlockLoaded(p)) {
                            continue;
                        }
                        byte old = w.getChunkFromBlockCoords(p).getBiomeArray()[(z & 15) << 4 | (x & 15)];
                        if (old == id) {
                            continue;
                        }
                        long col = ((long) z << 32) | (x & 0xFFFFFFFFL);
                        if (!biomeStroke.containsKey(col)) {
                            biomeStroke.put(col, old);
                        }
                        setBiome(w, x, z, id);
                        chunks.add(chunkKey(x >> 4, z >> 4));
                    }
                }
                resend(w, chunks);
            }
        });
    }

    /** Set Biome: todas las columnas de la selección pasan al bioma biomeId (una sola edición deshacible). */
    public static void setBiomeSelection(final int biomeId, final String biomeName) {
        if (!selectionOk()) {
            return;
        }
        final int[] box = box();
        final Set<Long> mask = Selection.mask;
        final int dim = dim();
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(dim);
                byte id = (byte) biomeId;
                Map<Long, Byte> done = new LinkedHashMap<Long, Byte>();
                Set<Long> chunks = new HashSet<Long>();
                Set<Long> columns = new HashSet<Long>();
                if (mask != null) {
                    for (Long k : mask) {
                        BlockPos p = BlockPos.fromLong(k);
                        columns.add(((long) p.getZ() << 32) | (p.getX() & 0xFFFFFFFFL));
                    }
                } else {
                    for (int z = box[2]; z <= box[5]; z++) {
                        for (int x = box[0]; x <= box[3]; x++) {
                            columns.add(((long) z << 32) | (x & 0xFFFFFFFFL));
                        }
                    }
                }
                for (long col : columns) {
                    int x = (int) col, z = (int) (col >> 32);
                    BlockPos p = new BlockPos(x, 64, z);
                    if (!w.isBlockLoaded(p)) {
                        continue;
                    }
                    byte old = w.getChunkFromBlockCoords(p).getBiomeArray()[(z & 15) << 4 | (x & 15)];
                    if (old != id) {
                        done.put(col, old);
                        setBiome(w, x, z, id);
                        chunks.add(chunkKey(x >> 4, z >> 4));
                    }
                }
                resend(w, chunks);
                if (done.isEmpty()) {
                    status = "Set Biome: nada que cambiar";
                    return;
                }
                long[] cols = new long[done.size()];
                byte[] before = new byte[cols.length];
                int i = 0;
                for (Map.Entry<Long, Byte> e : done.entrySet()) {
                    cols[i] = e.getKey();
                    before[i++] = e.getValue();
                }
                push(new Edit("Set Biome " + biomeName, dim, cols, before, id));
                status = "Set Biome " + biomeName + ": " + cols.length + " columnas";
            }
        });
    }

    public static void biomeStrokeEnd() {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Map<Long, Byte> done = biomeStroke;
                biomeStroke = null;
                if (done == null || done.isEmpty()) {
                    status = "Biome Painter: nada que cambiar";
                    return;
                }
                long[] cols = new long[done.size()];
                byte[] before = new byte[cols.length];
                int i = 0;
                for (Map.Entry<Long, Byte> e : done.entrySet()) {
                    cols[i] = e.getKey();
                    before[i++] = e.getValue();
                }
                push(new Edit("Biome Painter", biomeStrokeDim, cols, before, biomeStrokeId));
                status = "Biome Painter: " + cols.length + " columnas";
            }
        });
    }

    // ---- Historial --------------------------------------------------------------------------

    public static void undo() {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                AnimatedRebuild.cancel();
                Edit e = UNDO.pollFirst();
                if (e == null) {
                    status = "Nada que deshacer";
                    return;
                }
                apply(server.getWorld(e.dim), e, e.before, e.tilesBefore, true);
                REDO.addFirst(e);
                refreshLabels();
                status = "Deshecho: " + e.name;
            }
        });
    }

    public static void redo() {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                Edit e = REDO.pollFirst();
                if (e == null) {
                    status = "Nada que rehacer";
                    return;
                }
                apply(server.getWorld(e.dim), e, e.after, e.tilesAfter, false);
                UNDO.addFirst(e);
                refreshLabels();
                status = "Rehecho: " + e.name;
            }
        });
    }

    /** Deshace las n ediciones más recientes de golpe (ventana History). */
    public static void undoMany(final int n) {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                AnimatedRebuild.cancel();
                int done = 0;
                while (done < n && !UNDO.isEmpty()) {
                    Edit e = UNDO.pollFirst();
                    apply(server.getWorld(e.dim), e, e.before, e.tilesBefore, true);
                    REDO.addFirst(e);
                    done++;
                }
                refreshLabels();
                status = "Deshechas " + done + " ediciones";
            }
        });
    }

    /** Rehace las n ediciones siguientes de golpe (ventana History). */
    public static void redoMany(final int n) {
        runOnServer(new ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                int done = 0;
                while (done < n && !REDO.isEmpty()) {
                    Edit e = REDO.pollFirst();
                    apply(server.getWorld(e.dim), e, e.after, e.tilesAfter, false);
                    UNDO.addFirst(e);
                    done++;
                }
                refreshLabels();
                status = "Rehechas " + done + " ediciones";
            }
        });
    }

    private static void apply(World w, Edit e, int[] ids, Map<Long, NBTTagCompound> tiles, boolean reverse) {
        if (e.biomeCols != null) {
            Set<Long> chunks = new HashSet<Long>();
            for (int i = 0; i < e.biomeCols.length; i++) {
                int x = (int) e.biomeCols[i], z = (int) (e.biomeCols[i] >> 32);
                if (setBiome(w, x, z, reverse ? e.biomeBefore[i] : e.biomeAfter)) {
                    chunks.add(chunkKey(x >> 4, z >> 4));
                }
            }
            resend(w, chunks);
            return;
        }
        int len = e.pos.length;
        for (int k = 0; k < len; k++) {
            int i = reverse ? len - 1 - k : k;
            BlockPos p = BlockPos.fromLong(e.pos[i]);
            if (!w.isBlockLoaded(p)) {
                continue;
            }
            IBlockState s = Block.getStateById(ids[i]);
            w.setBlockState(p, s, 2);
            NBTTagCompound tag = tiles.get(e.pos[i]);
            if (tag != null) {
                TileEntity te = w.getTileEntity(p);
                if (te != null) {
                    te.readFromNBT(tag);
                    te.markDirty();
                    w.notifyBlockUpdate(p, s, s, 3);
                }
            }
        }
    }

    private static void push(Edit e) {
        UNDO.addFirst(e);
        REDO.clear();
        long total = 0;
        for (Edit x : UNDO) {
            total += x.size();
        }
        while (UNDO.size() > MAX_EDITS || (total > MAX_HISTORY_BLOCKS && UNDO.size() > 1)) {
            total -= UNDO.pollLast().size();
        }
        refreshLabels();
    }

    private static void refreshLabels() {
        undoLabel = UNDO.isEmpty() ? null : UNDO.peekFirst().name;
        redoLabel = REDO.isEmpty() ? null : REDO.peekFirst().name;
        undoNames = names(UNDO);
        redoNames = names(REDO);
    }

    /** Nombres para la ventana History, de la más reciente a la más antigua. */
    private static String[] names(Deque<Edit> d) {
        String[] out = new String[d.size()];
        int i = 0;
        for (Edit e : d) {
            out[i++] = e.name + " (" + e.size() + ")";
        }
        return out;
    }
}
