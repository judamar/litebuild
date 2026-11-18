package dev.litebuild;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;

/**
 * Equivalencias entre bloque completo, escalera, losa, muro, valla y puerta de valla de un mismo material en 1.12
 * (por ejemplo tablones de abeto, escalera de abeto, losa de madera de abeto...). Sirve al Type Replace de Replace Mode
 * y al paso bloque -> escalera -> losa del Tinker.
 */
public final class BlockVariants {
    /** Una familia de material; null en lo que no existe. */
    private static final class Mat {
        final String full;
        final int fullMeta;
        final String stairs;
        final String slab;
        final int slabMeta;
        final String wall;
        final int wallMeta;
        final String fence;
        final String gate;

        Mat(String full, int fullMeta, String stairs, String slab, int slabMeta, String wall, int wallMeta, String fence,
                String gate) {
            this.full = full;
            this.fullMeta = fullMeta;
            this.stairs = stairs;
            this.slab = slab;
            this.slabMeta = slabMeta;
            this.wall = wall;
            this.wallMeta = wallMeta;
            this.fence = fence;
            this.gate = gate;
        }
    }

    private static final Map<String, Mat> BY_FULL = new HashMap<String, Mat>();
    private static final Map<String, Mat> BY_STAIRS = new HashMap<String, Mat>();
    private static final Map<String, Mat> BY_SLAB = new HashMap<String, Mat>();

    private static void add(Mat m) {
        BY_FULL.put(m.full + "#" + m.fullMeta, m);
        if (m.stairs != null) {
            BY_STAIRS.put(m.stairs, m);
        }
        if (m.slab != null) {
            BY_SLAB.put(m.slab + "#" + m.slabMeta, m);
        }
    }

    static {
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (int i = 0; i < woods.length; i++) {
            String w = woods[i];
            add(new Mat("planks", i, w + "_stairs", "wooden_slab", i, null, 0, i == 0 ? "fence" : w + "_fence",
                    i == 0 ? "fence_gate" : w + "_fence_gate"));
        }
        add(new Mat("cobblestone", 0, "stone_stairs", "stone_slab", 3, "cobblestone_wall", 0, null, null));
        add(new Mat("mossy_cobblestone", 0, null, null, 0, "cobblestone_wall", 1, null, null));
        add(new Mat("stonebrick", 0, "stone_brick_stairs", "stone_slab", 5, null, 0, null, null));
        add(new Mat("brick_block", 0, "brick_stairs", "stone_slab", 4, null, 0, null, null));
        add(new Mat("nether_brick", 0, "nether_brick_stairs", "stone_slab", 6, null, 0, "nether_brick_fence", null));
        add(new Mat("sandstone", 0, "sandstone_stairs", "stone_slab", 1, null, 0, null, null));
        add(new Mat("red_sandstone", 0, "red_sandstone_stairs", "stone_slab2", 0, null, 0, null, null));
        add(new Mat("quartz_block", 0, "quartz_stairs", "stone_slab", 7, null, 0, null, null));
        add(new Mat("purpur_block", 0, "purpur_stairs", "purpur_slab", 0, null, 0, null, null));
        add(new Mat("stone", 0, null, "stone_slab", 0, null, 0, null, null));
    }

    private BlockVariants() {
    }

    private static String name(IBlockState s) {
        String n = String.valueOf(Block.REGISTRY.getNameForObject(s.getBlock()));
        return n.startsWith("minecraft:") ? n.substring(10) : n;
    }

    private static int meta(IBlockState s) {
        return s.getBlock().getMetaFromState(s);
    }

    /** La familia a la que pertenece el bloque (completo, escalera, losa, muro, valla...), o null. */
    private static Mat materialOf(IBlockState s) {
        String n = name(s);
        Mat m = BY_FULL.get(n + "#" + meta(s));
        if (m == null && s.getBlock() instanceof BlockStairs) {
            m = BY_STAIRS.get(n);
        }
        if (m == null && s.getBlock() instanceof BlockSlab) {
            m = BY_SLAB.get(n + "#" + (meta(s) & 7));
        }
        if (m == null && s.getBlock() instanceof BlockWall) {
            for (Mat c : BY_FULL.values()) {
                if (c.wall != null && c.wall.equals(n) && c.wallMeta == meta(s)) {
                    m = c;
                }
            }
        }
        if (m == null && (s.getBlock() instanceof BlockFence || s.getBlock() instanceof BlockFenceGate)) {
            for (Mat c : BY_FULL.values()) {
                if (n.equals(c.fence) || n.equals(c.gate)) {
                    m = c;
                }
            }
        }
        return m;
    }

    private static IBlockState state(String name, int meta) {
        Block b = name == null ? null : Block.getBlockFromName("minecraft:" + name);
        if (b == null) {
            return null;
        }
        try {
            return b.getStateFromMeta(meta);
        } catch (RuntimeException e) {
            return b.getDefaultState();
        }
    }

    /**
     * Type Replace: target (escalera, losa, muro, valla o puerta) con el material de base (por ejemplo tablones de
     * abeto), conservando orientación y mitad. null si no hay equivalente.
     */
    public static IBlockState convert(IBlockState base, IBlockState target) {
        Mat m = materialOf(base);
        if (m == null) {
            return null;
        }
        IBlockState out;
        if (target.getBlock() instanceof BlockStairs) {
            out = state(m.stairs, 0);
        } else if (target.getBlock() instanceof BlockSlab) {
            out = state(m.slab, m.slabMeta);
        } else if (target.getBlock() instanceof BlockWall) {
            out = state(m.wall, m.wallMeta);
        } else if (target.getBlock() instanceof BlockFenceGate) {
            out = state(m.gate, 0);
        } else if (target.getBlock() instanceof BlockFence) {
            out = state(m.fence, 0);
        } else {
            out = state(m.full, m.fullMeta);
        }
        return out == null ? null : EditEngine.copyProperties(target, out);
    }

    /** Tinker: bloque completo -> escalera -> losa -> bloque completo del mismo material; null si no hay familia. */
    public static IBlockState nextShape(IBlockState s) {
        Mat m = materialOf(s);
        if (m == null) {
            return null;
        }
        IBlockState out;
        if (s.getBlock() instanceof BlockStairs) {
            out = state(m.slab, m.slabMeta);
        } else if (s.getBlock() instanceof BlockSlab) {
            out = state(m.full, m.fullMeta);
        } else if (s.getBlock() instanceof BlockWall || s.getBlock() instanceof BlockFence
                || s.getBlock() instanceof BlockFenceGate) {
            return null;
        } else {
            out = state(m.stairs, 0);
            if (out == null) {
                out = state(m.slab, m.slabMeta);
            }
        }
        return out == null ? null : EditEngine.copyProperties(s, out);
    }
}
