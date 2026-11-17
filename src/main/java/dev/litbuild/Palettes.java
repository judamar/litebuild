package dev.litbuild;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;

/**
 * Paletas de bloques: los 16 usados más recientemente (en memoria) y paletas con nombre que se guardan en
 * config/litbuild/palettes.txt ("[Nombre]" y debajo un "id|meta" por línea).
 */
public final class Palettes {
    public static final int RECENT_MAX = 16;

    public static final class Palette {
        public String name;
        public final List<String> blocks = new ArrayList<String>();

        Palette(String name) {
            this.name = name;
        }
    }

    public static final List<Palette> list = new ArrayList<Palette>();
    public static final List<String> recent = new ArrayList<String>();
    private static boolean loaded;

    private Palettes() {
    }

    private static File file() {
        File d = new File(Minecraft.getMinecraft().mcDataDir, "config/litbuild");
        d.mkdirs();
        return new File(d, "palettes.txt");
    }

    public static String entry(String id, int meta) {
        return id + "|" + meta;
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!file().isFile()) {
                return;
            }
            Palette cur = null;
            for (String line : Files.readAllLines(file().toPath(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    cur = new Palette(line.substring(1, line.length() - 1));
                    list.add(cur);
                } else if (cur != null && line.contains("|")) {
                    cur.blocks.add(line);
                }
            }
        } catch (IOException e) {
            LiteModLitBuild.LOG.warn("No se pudieron leer las paletas", e);
        }
    }

    public static void save() {
        StringBuilder sb = new StringBuilder();
        for (Palette p : list) {
            sb.append('[').append(p.name).append("]\n");
            for (String b : p.blocks) {
                sb.append(b).append('\n');
            }
        }
        try {
            Files.write(file().toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LiteModLitBuild.LOG.warn("No se pudieron guardar las paletas", e);
        }
    }

    public static Palette add(String name) {
        Palette p = new Palette(name.trim().isEmpty() ? "Palette " + (list.size() + 1) : name.trim().replace("]", ")"));
        list.add(p);
        save();
        return p;
    }

    /** Pone el bloque el primero de los recientes (sin repetirlo). */
    public static void pushRecent(String id, int meta) {
        String e = entry(id, meta);
        recent.remove(e);
        recent.add(0, e);
        while (recent.size() > RECENT_MAX) {
            recent.remove(recent.size() - 1);
        }
    }

    public static String idOf(String entry) {
        return entry.substring(0, entry.lastIndexOf('|'));
    }

    public static int metaOf(String entry) {
        try {
            return Integer.parseInt(entry.substring(entry.lastIndexOf('|') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** El estado de una entrada, o null si el bloque ya no existe. */
    public static IBlockState resolve(String entry) {
        Block b = Block.getBlockFromName(idOf(entry));
        if (b == null) {
            return null;
        }
        try {
            return b.getStateFromMeta(metaOf(entry));
        } catch (RuntimeException e) {
            return b.getDefaultState();
        }
    }

    /** Elige un bloque de la paleta por posición, sin repetir patrón: ruido blanco con semilla fija. */
    public static StatePicker picker(Palette p, final long seed) {
        final List<IBlockState> states = new ArrayList<IBlockState>();
        for (String e : p.blocks) {
            IBlockState s = resolve(e);
            if (s != null) {
                states.add(s);
            }
        }
        if (states.isEmpty()) {
            return null;
        }
        return new StatePicker() {
            @Override
            public IBlockState pick(int x, int y, int z) {
                return states.get(Math.min(states.size() - 1, (int) (Noise.white(x, y, z, seed) * states.size())));
            }
        };
    }
}
