package dev.litebuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JseMathLib;

import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockSand;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Ejecuta un script de Script Brush en el hilo del servidor. Lua corre aislado: sin io, os, luajava, load ni
 * require, con un presupuesto de instrucciones por sello (corta los bucles infinitos) y un tope de bloques
 * colocados. Las funciones siguen la API del Script Brush de Axiom adaptada a 1.12.2: un id de estado es
 * id_del_bloque * 16 + meta, y los "tags" se aproximan por clase o por nombre del bloque.
 */
final class ScriptRunner {
    /** Instrucciones de Lua permitidas por sello (unos 0,5 s como máximo). */
    private static final int BUDGET = 10_000_000;
    private static final int MAX_SETS = 200_000;

    /** Nombres de bloques de versiones nuevas que se reconocen en blocks.xxx (nombre[:meta] de 1.12). */
    private static final Map<String, String> ALIAS = new HashMap<String, String>();
    private static final Map<Integer, int[]> CANDIDATES = new HashMap<Integer, int[]>();

    static {
        String[][] a = {
            {"coarse_dirt", "dirt:1"}, {"podzol", "dirt:2"}, {"grass_block", "grass"}, {"dirt_path", "grass_path"},
            {"granite", "stone:1"}, {"polished_granite", "stone:2"}, {"diorite", "stone:3"},
            {"polished_diorite", "stone:4"}, {"andesite", "stone:5"}, {"polished_andesite", "stone:6"},
            {"oak_planks", "planks:0"}, {"spruce_planks", "planks:1"}, {"birch_planks", "planks:2"},
            {"jungle_planks", "planks:3"}, {"acacia_planks", "planks:4"}, {"dark_oak_planks", "planks:5"},
            {"oak_log", "log:0"}, {"spruce_log", "log:1"}, {"birch_log", "log:2"}, {"jungle_log", "log:3"},
            {"acacia_log", "log2:0"}, {"dark_oak_log", "log2:1"},
            {"oak_leaves", "leaves:0"}, {"spruce_leaves", "leaves:1"}, {"birch_leaves", "leaves:2"},
            {"jungle_leaves", "leaves:3"}, {"acacia_leaves", "leaves2:0"}, {"dark_oak_leaves", "leaves2:1"},
            {"white_concrete", "concrete:0"}, {"black_concrete", "concrete:15"}, {"red_concrete", "concrete:14"},
            {"green_concrete", "concrete:13"}, {"blue_concrete", "concrete:11"}, {"lime_concrete", "concrete:5"},
            {"white_wool", "wool:0"}, {"black_wool", "wool:15"}, {"red_wool", "wool:14"}, {"lime_wool", "wool:5"},
        };
        for (String[] e : a) {
            ALIAS.put(e[0], e[1]);
        }
    }

    private final World w;
    private final Globals g;
    private final LuaValue chunk;
    private final LuaValue sethook;
    private final LuaValue hook;
    private final Map<Long, Noise> noises = new HashMap<Long, Noise>();
    private Terrain.Setter out;
    private int sets;

    ScriptRunner(World world, String code) {
        this.w = world;
        g = new Globals();
        g.load(new BaseLib());
        g.load(new PackageLib());
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new JseMathLib());
        g.load(new DebugLib());
        LoadState.install(g);
        LuaC.install(g);
        // debug solo sirve para instalar el hook de instrucciones; el script no lo ve.
        sethook = g.get("debug").get("sethook");
        for (String n : new String[] {"debug", "package", "dofile", "loadfile", "require", "load", "loadstring",
            "collectgarbage"}) {
            g.set(n, LuaValue.NIL);
        }
        hook = new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                throw new LuaError("el script ha tardado demasiado (más de " + BUDGET + " instrucciones en un sello)");
            }
        };
        register();
        chunk = g.load(code, "script");
    }

    World world() {
        return w;
    }

    /** Un sello: ejecuta el script una vez (once) o una vez por cada bloque del pincel. */
    void run(boolean once, int cx, int cy, int cz, int radius, int shape, MaskSpec mask, Terrain.Setter setter) {
        this.out = setter;
        sets = 0;
        sethook.invoke(LuaValue.varargsOf(hook, LuaValue.valueOf(""), LuaValue.valueOf(BUDGET)));
        try {
            if (once) {
                exec(cx, cy, cz);
                return;
            }
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!Brush.contains(shape, dx, dy, dz, radius)) {
                            continue;
                        }
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        if (mask == null || mask.test(w, x, y, z)) {
                            exec(x, y, z);
                        }
                    }
                }
            }
        } finally {
            sethook.call(LuaValue.NIL);
        }
    }

    /** Instrucciones de Lua permitidas por bloque al usar el script como máscara (se evalúa en cada bloque del pincel). */
    private static final int MASK_BUDGET = 200_000;
    private static final Terrain.Setter NOOP = new Terrain.Setter() {
        @Override
        public void set(int x, int y, int z, IBlockState state) {
        }
    };

    /** Lua Tool Mask: ejecuta el script en (x, y, z) y devuelve si lo que retorna es verdadero. Sin colocar bloques. */
    boolean test(int x, int y, int z) {
        this.out = NOOP;
        sets = 0;
        sethook.invoke(LuaValue.varargsOf(hook, LuaValue.valueOf(""), LuaValue.valueOf(MASK_BUDGET)));
        try {
            g.set("x", x);
            g.set("y", y);
            g.set("z", z);
            return chunk.call().toboolean();
        } finally {
            sethook.call(LuaValue.NIL);
        }
    }

    private void exec(int x, int y, int z) {
        g.set("x", x);
        g.set("y", y);
        g.set("z", z);
        LuaValue r = chunk.call();
        if (r.isnumber()) {
            put(x, y, z, r.toint());
        }
    }

    private void put(int x, int y, int z, int id) {
        if (++sets > MAX_SETS) {
            throw new LuaError("el script ha colocado demasiados bloques en un sello (máximo " + MAX_SETS + ")");
        }
        out.set(x, y, z, Block.getStateById(id));
    }

    // ---- API de Lua -----------------------------------------------------------------------------

    private void register() {
        LuaTable blocks = new LuaTable();
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key) {
                return blockId(key.tojstring());
            }
        });
        blocks.setmetatable(meta);
        g.set("blocks", blocks);

        g.set("getBlock", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                IBlockState s = state(fl(a, 1), fl(a, 2), fl(a, 3));
                return LuaValue.valueOf(s == null ? 0 : Block.getStateId(s.getBlock().getDefaultState()));
            }
        });
        g.set("getBlockState", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                IBlockState s = state(fl(a, 1), fl(a, 2), fl(a, 3));
                return LuaValue.valueOf(s == null ? 0 : Block.getStateId(s));
            }
        });
        g.set("setBlock", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                put(fl(a, 1), fl(a, 2), fl(a, 3), a.toint(4));
                return LuaValue.NONE;
            }
        });
        g.set("isSolid", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                return LuaValue.valueOf(Block.getStateById(a.toint(1)).getMaterial().isSolid());
            }
        });
        g.set("getHighestBlockYAt", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                int x = fl(a, 1), z = fl(a, 2);
                for (int y = 255; y >= 0; y--) {
                    IBlockState s = state(x, y, z);
                    if (s != null && s.getBlock() != net.minecraft.init.Blocks.AIR) {
                        return LuaValue.valueOf(y);
                    }
                }
                return LuaValue.valueOf(-1);
            }
        });
        g.set("withBlockProperty", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                return LuaValue.valueOf(withProperty(a.toint(1), a.checkjstring(2)));
            }
        });
        g.set("getBlockProperty", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                String v = propertyName(a.toint(1), a.checkjstring(2));
                return v == null ? LuaValue.NIL : LuaValue.valueOf(v);
            }
        });
        g.set("isBlockTagged", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                return LuaValue.valueOf(tagged(Block.getStateById(a.toint(1)).getBlock(), a.tojstring(2)));
            }
        });
        g.set("getBlockRGB", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                int c = BlockColors.get(a.toint(1));
                return c < 0 ? LuaValue.NIL : LuaValue.valueOf(c);
            }
        });
        g.set("findClosestBlockToRGB", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                return LuaValue.valueOf(closest(a.toint(1), a.optint(2, 0), a.optint(3, 0)));
            }
        });
        g.set("getSimplexNoise", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                double n = noise(a.optlong(4, 0)).fbm(a.todouble(1), a.todouble(2), a.todouble(3), 1);
                return LuaValue.valueOf(Math.max(0.0, Math.min(1.0, 0.5 + 0.5 * n * 1.4)));
            }
        });
        g.set("getVoroniEdgeNoise", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                double e = Noise.cellEdge(a.todouble(1), a.todouble(2), a.todouble(3), a.optlong(4, 0));
                return LuaValue.valueOf(Math.max(0.0, Math.min(1.0, e)));
            }
        });
        g.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                StringBuilder sb = new StringBuilder("Script: ");
                for (int i = 1; i <= a.narg(); i++) {
                    sb.append(i > 1 ? " " : "").append(a.tojstring(i));
                }
                EditEngine.status = sb.toString();
                return LuaValue.NONE;
            }
        });
    }

    /** Coordenada de Lua (número real) a entero hacia abajo, para que las negativas funcionen. */
    private static int fl(Varargs a, int i) {
        return (int) Math.floor(a.todouble(i));
    }

    private IBlockState state(int x, int y, int z) {
        if (y < 0 || y > 255) {
            return null;
        }
        BlockPos p = new BlockPos(x, y, z);
        return w.isBlockLoaded(p) ? w.getBlockState(p) : null;
    }

    private Noise noise(long seed) {
        Noise n = noises.get(seed);
        if (n == null) {
            n = new Noise(seed);
            noises.put(seed, n);
        }
        return n;
    }

    /** blocks.nombre: id de estado del bloque por nombre de 1.12, con meta opcional (nombre:meta). */
    private static LuaValue blockId(String key) {
        String k = ALIAS.containsKey(key) ? ALIAS.get(key) : key;
        int meta = 0;
        int colon = k.lastIndexOf(':');
        if (colon > 0 && colon < k.length() - 1 && k.substring(colon + 1).matches("\\d+")) {
            meta = Integer.parseInt(k.substring(colon + 1));
            k = k.substring(0, colon);
        }
        Block b = Block.getBlockFromName(k);
        if (b == null) {
            return LuaValue.NIL;
        }
        IBlockState s;
        try {
            s = b.getStateFromMeta(meta);
        } catch (RuntimeException e) {
            s = b.getDefaultState();
        }
        return LuaValue.valueOf(Block.getStateId(s));
    }

    private static int withProperty(int id, String spec) {
        IBlockState s = Block.getStateById(id);
        int eq = spec.indexOf('=');
        if (eq < 0) {
            return id;
        }
        String name = spec.substring(0, eq).trim();
        String value = spec.substring(eq + 1).trim();
        for (IProperty<?> p : s.getPropertyKeys()) {
            if (p.getName().equals(name)) {
                IBlockState n = withValue(s, p, value);
                return n == null ? id : Block.getStateId(n);
            }
        }
        return id;
    }

    private static <T extends Comparable<T>> IBlockState withValue(IBlockState s, IProperty<T> p, String value) {
        for (T v : p.getAllowedValues()) {
            if (p.getName(v).equals(value)) {
                return s.withProperty(p, v);
            }
        }
        return null;
    }

    private static String propertyName(int id, String name) {
        IBlockState s = Block.getStateById(id);
        for (IProperty<?> p : s.getPropertyKeys()) {
            if (p.getName().equals(name)) {
                return valueName(s, p);
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> String valueName(IBlockState s, IProperty<T> p) {
        return p.getName(s.getValue(p));
    }

    /** En 1.12 no hay tags de bloques: se aproximan por clase de bloque o por el nombre. */
    private static boolean tagged(Block b, String tag) {
        String t = tag.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        String name = String.valueOf(Block.REGISTRY.getNameForObject(b)).replace("minecraft:", "");
        switch (t) {
            case "fences": return b instanceof BlockFence;
            case "fence_gates": return b instanceof BlockFenceGate;
            case "logs": return b instanceof BlockLog;
            case "leaves": return b instanceof BlockLeaves;
            case "slabs": return b instanceof BlockSlab;
            case "stairs": return b instanceof BlockStairs;
            case "walls": return b instanceof BlockWall;
            case "doors": return b instanceof BlockDoor;
            case "buttons": return b instanceof BlockButton;
            case "sand": return b instanceof BlockSand;
            case "saplings": return b instanceof BlockSapling;
            case "flowers": return b instanceof BlockFlower;
            default: return name.equals(t) || name.contains(t);
        }
    }

    /** El index-ésimo bloque cuyo color medio se parece más a rgb (0 = el más cercano), según los flags. */
    private static int closest(final int rgb, int flags, int index) {
        int[] ids = candidates(flags);
        if (ids.length == 0) {
            return 0;
        }
        final int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
        Integer[] sorted = new Integer[ids.length];
        for (int i = 0; i < ids.length; i++) {
            sorted[i] = ids[i];
        }
        Arrays.sort(sorted, new Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                return Long.compare(dist(x), dist(y));
            }

            private long dist(int id) {
                int c = BlockColors.get(id);
                long dr = ((c >> 16) & 255) - r, dg = ((c >> 8) & 255) - gr, db = (c & 255) - b;
                return dr * dr + dg * dg + db * db;
            }
        });
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    /** Estados con color que cumplen los flags: 1 sólido, 2 opaco, 4 cubo completo, 16 sin minerales, 32 sin terracota vidriada, 64 sin tile entities. */
    private static int[] candidates(int flags) {
        int[] cached = CANDIDATES.get(flags);
        if (cached != null) {
            return cached;
        }
        List<Integer> out = new ArrayList<Integer>();
        for (int id = 0; id < BlockColors.size(); id++) {
            if (BlockColors.get(id) < 0) {
                continue;
            }
            IBlockState s = Block.getStateById(id);
            Block b = s.getBlock();
            String name = String.valueOf(Block.REGISTRY.getNameForObject(b));
            if (Block.getStateId(s) != id
                    || ((flags & 1) != 0 && !s.getMaterial().isSolid())
                    || ((flags & 2) != 0 && !b.isOpaqueCube(s))
                    || ((flags & 4) != 0 && !b.isFullCube(s))
                    || ((flags & 16) != 0 && name.endsWith("_ore"))
                    || ((flags & 32) != 0 && name.contains("glazed_terracotta"))
                    || ((flags & 64) != 0 && b.hasTileEntity())) {
                continue;
            }
            out.add(id);
        }
        Collections.sort(out);
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        CANDIDATES.put(flags, arr);
        return arr;
    }
}
