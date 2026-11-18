package dev.litebuild;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Lectura y escritura de schematics en tres formatos, elegidos por la extensión del archivo:
 * .schematic (MCEdit/WorldEdit clásico, el nativo de 1.12: los ids numéricos coinciden con los del juego),
 * .schem (Sponge v1/v2/v3, con paleta de estados como texto) y .nbt (estructura de Minecraft).
 * Los bloques de versiones más nuevas que no existen en 1.12 se importan como aire y se cuentan en {@link #unknown}.
 */
public final class Schematics {
    public static final String[] FORMATS = {"Schematic (.schematic)", "Sponge (.schem)", "Structure NBT (.nbt)"};
    public static final String[] EXTENSIONS = {".schematic", ".schem", ".nbt"};
    /** Bloques del último archivo importado que no existen en esta versión. */
    public static volatile int unknown;
    /** Versión de datos de Minecraft 1.12.2 (se escribe en .schem y .nbt). */
    private static final int DATA_VERSION = 1343;

    private Schematics() {
    }

    public static File dir() {
        File d = new File(Minecraft.getMinecraft().mcDataDir, "config/litebuild/schematics");
        d.mkdirs();
        return d;
    }

    /** Nombres de archivo (con extensión) de la carpeta que se pueden importar, en orden alfabético. */
    public static List<String> list() {
        File[] files = dir().listFiles();
        List<String> names = new ArrayList<String>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && formatOf(f.getName()) >= 0) {
                    names.add(f.getName());
                }
            }
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** Índice en FORMATS según la extensión, o -1. */
    public static int formatOf(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < EXTENSIONS.length; i++) {
            if (n.endsWith(EXTENSIONS[i])) {
                return i;
            }
        }
        return -1;
    }

    /** El archivo con ese nombre y formato dentro de la carpeta (sin separadores de ruta). */
    public static File file(String name, int format) {
        String safe = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return new File(dir(), (safe.isEmpty() ? "schematic" : safe) + EXTENSIONS[format]);
    }

    public static void export(Clipboard c, File f) throws IOException {
        switch (formatOf(f.getName())) {
            case 1:
                writeSponge(c, f);
                break;
            case 2:
                writeStructure(c, f);
                break;
            default:
                writeClassic(c, f);
        }
    }

    public static Clipboard read(File f) throws IOException {
        unknown = 0;
        switch (formatOf(f.getName())) {
            case 1:
                return readSponge(f);
            case 2:
                return readStructure(f);
            default:
                return readClassic(f);
        }
    }

    private static void checkVolume(long volume) throws IOException {
        if (volume <= 0) {
            throw new IOException("Archivo no válido (tamaño cero)");
        }
        if (volume > EditEngine.MAX_BLOCKS) {
            throw new IOException("Demasiado grande: " + volume + " bloques (máximo " + EditEngine.MAX_BLOCKS + ")");
        }
    }

    private static void writeNbt(NBTTagCompound root, File f) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
    }

    public static NBTTagCompound readNbt(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return CompressedStreamTools.readCompressed(in);
        }
    }

    // ---- Sponge (.schem) --------------------------------------------------------------------

    private static void writeSponge(Clipboard c, File f) throws IOException {
        NBTTagCompound meta = new NBTTagCompound();
        meta.setString("Name", f.getName().replaceFirst("\\.[^.]+$", ""));
        meta.setString("Author", "LiteBuild");
        writeSponge(c, f, meta);
    }

    /** .schem con metadatos propios (Name, Author y lo que se quiera añadir: los lectores ajenos lo ignoran). */
    public static void writeSponge(Clipboard c, File f, NBTTagCompound meta) throws IOException {
        Map<Integer, Integer> palette = new LinkedHashMap<Integer, Integer>();
        ByteArrayOutputStream data = new ByteArrayOutputStream(c.states.length);
        for (int id : c.states) {
            Integer idx = palette.get(id);
            if (idx == null) {
                idx = palette.size();
                palette.put(id, idx);
            }
            VarInt.write(data, idx);
        }
        NBTTagCompound pal = new NBTTagCompound();
        for (Map.Entry<Integer, Integer> e : palette.entrySet()) {
            pal.setInteger(BlockStates.toText(Block.getStateById(e.getKey())), e.getValue());
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Version", 2);
        root.setInteger("DataVersion", DATA_VERSION);
        root.setShort("Width", (short) c.sx);
        root.setShort("Height", (short) c.sy);
        root.setShort("Length", (short) c.sz);
        root.setIntArray("Offset", new int[3]);
        root.setInteger("PaletteMax", palette.size());
        root.setTag("Palette", pal);
        root.setByteArray("BlockData", data.toByteArray());
        NBTTagList tiles = new NBTTagList();
        for (Map.Entry<Integer, NBTTagCompound> e : c.tiles.entrySet()) {
            int i = e.getKey();
            NBTTagCompound tag = e.getValue().copy();
            tag.removeTag("x");
            tag.removeTag("y");
            tag.removeTag("z");
            tag.setString("Id", tag.getString("id"));
            tag.removeTag("id");
            tag.setIntArray("Pos", new int[] {i % c.sx, i / (c.sx * c.sz), (i / c.sx) % c.sz});
            tiles.appendTag(tag);
        }
        root.setTag("BlockEntities", tiles);
        root.setTag("Metadata", meta);
        writeNbt(root, f);
    }

    private static Clipboard readSponge(File f) throws IOException {
        return readSponge(readNbt(f));
    }

    /** El compuesto Metadata de un .schem ya leído (vacío si no tiene); admite v3 (dentro de "Schematic"). */
    public static NBTTagCompound metadata(NBTTagCompound root) {
        if (root.hasKey("Schematic", 10)) {
            root = root.getCompoundTag("Schematic");
        }
        return root.getCompoundTag("Metadata");
    }

    public static Clipboard readSponge(NBTTagCompound root) throws IOException {
        // Sponge v3 guarda todo dentro de "Schematic" y los bloques dentro de "Blocks".
        if (root.hasKey("Schematic", 10)) {
            root = root.getCompoundTag("Schematic");
        }
        int w = root.getShort("Width") & 0xFFFF, h = root.getShort("Height") & 0xFFFF, l = root.getShort("Length") & 0xFFFF;
        NBTTagCompound blocks = root.hasKey("Blocks", 10) ? root.getCompoundTag("Blocks") : root;
        NBTTagCompound pal = blocks.getCompoundTag("Palette");
        byte[] data = blocks.hasKey("Data", 7) ? blocks.getByteArray("Data") : blocks.getByteArray("BlockData");
        checkVolume((long) w * h * l);
        int n = w * h * l;
        Map<Integer, Integer> byIndex = new HashMap<Integer, Integer>();
        for (String key : pal.getKeySet()) {
            IBlockState s = BlockStates.fromText(key);
            if (s != null) {
                byIndex.put(pal.getInteger(key), Block.getStateId(s));
            }
        }
        int[] states = new int[n];
        int[] pos = {0};
        try {
            for (int i = 0; i < n; i++) {
                Integer id = byIndex.get(VarInt.read(data, pos));
                if (id == null) {
                    unknown++;
                    states[i] = Clipboard.AIR;
                } else {
                    states[i] = id;
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Archivo no válido: " + e.getMessage());
        }
        Map<Integer, NBTTagCompound> tiles = new HashMap<Integer, NBTTagCompound>();
        NBTTagList list = blocks.hasKey("BlockEntities", 9) ? blocks.getTagList("BlockEntities", 10)
                : root.getTagList("TileEntities", 10);
        for (int t = 0; t < list.tagCount(); t++) {
            NBTTagCompound tag = list.getCompoundTagAt(t).copy();
            int[] p = tag.getIntArray("Pos");
            if (p.length != 3 || p[0] < 0 || p[0] >= w || p[1] < 0 || p[1] >= h || p[2] < 0 || p[2] >= l) {
                continue;
            }
            if (tag.hasKey("Data", 10)) {
                // Sponge v3 anida los datos del tile entity dentro de "Data".
                NBTTagCompound inner = tag.getCompoundTag("Data");
                for (String key : inner.getKeySet()) {
                    tag.setTag(key, inner.getTag(key));
                }
                tag.removeTag("Data");
            }
            if (tag.hasKey("Id")) {
                tag.setString("id", tag.getString("Id"));
                tag.removeTag("Id");
            }
            tag.removeTag("Pos");
            tag.setInteger("x", p[0]);
            tag.setInteger("y", p[1]);
            tag.setInteger("z", p[2]);
            tiles.put((p[1] * l + p[2]) * w + p[0], tag);
        }
        return new Clipboard(w, h, l, states, tiles);
    }

    // ---- Estructura de Minecraft (.nbt) -----------------------------------------------------

    private static void writeStructure(Clipboard c, File f) throws IOException {
        Map<Integer, Integer> palette = new LinkedHashMap<Integer, Integer>();
        NBTTagList pal = new NBTTagList();
        NBTTagList blocks = new NBTTagList();
        for (int i = 0; i < c.states.length; i++) {
            if (c.states[i] == Clipboard.AIR && !c.tiles.containsKey(i)) {
                continue;
            }
            Integer idx = palette.get(c.states[i]);
            if (idx == null) {
                idx = palette.size();
                palette.put(c.states[i], idx);
                pal.appendTag(BlockStates.toTag(Block.getStateById(c.states[i])));
            }
            NBTTagCompound b = new NBTTagCompound();
            b.setTag("pos", ints(i % c.sx, i / (c.sx * c.sz), (i / c.sx) % c.sz));
            b.setInteger("state", idx);
            NBTTagCompound tile = c.tiles.get(i);
            if (tile != null) {
                NBTTagCompound copy = tile.copy();
                copy.removeTag("x");
                copy.removeTag("y");
                copy.removeTag("z");
                b.setTag("nbt", copy);
            }
            blocks.appendTag(b);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("size", ints(c.sx, c.sy, c.sz));
        root.setTag("entities", new NBTTagList());
        root.setTag("blocks", blocks);
        root.setTag("palette", pal);
        root.setString("author", "LiteBuild");
        root.setInteger("DataVersion", DATA_VERSION);
        writeNbt(root, f);
    }

    private static NBTTagList ints(int x, int y, int z) {
        NBTTagList l = new NBTTagList();
        l.appendTag(new net.minecraft.nbt.NBTTagInt(x));
        l.appendTag(new net.minecraft.nbt.NBTTagInt(y));
        l.appendTag(new net.minecraft.nbt.NBTTagInt(z));
        return l;
    }

    private static Clipboard readStructure(File f) throws IOException {
        NBTTagCompound root = readNbt(f);
        NBTTagList size = root.getTagList("size", 3);
        if (size.tagCount() != 3) {
            throw new IOException("Estructura no válida (sin tamaño)");
        }
        int w = size.getIntAt(0), h = size.getIntAt(1), l = size.getIntAt(2);
        checkVolume((long) w * h * l);
        NBTTagList pal = root.getTagList("palette", 10);
        int[] map = new int[pal.tagCount()];
        for (int i = 0; i < map.length; i++) {
            IBlockState s = BlockStates.fromTag(pal.getCompoundTagAt(i));
            map[i] = s == null ? -1 : Block.getStateId(s);
        }
        int[] states = new int[w * h * l];
        Arrays.fill(states, Clipboard.AIR);
        Map<Integer, NBTTagCompound> tiles = new HashMap<Integer, NBTTagCompound>();
        NBTTagList blocks = root.getTagList("blocks", 10);
        for (int t = 0; t < blocks.tagCount(); t++) {
            NBTTagCompound b = blocks.getCompoundTagAt(t);
            NBTTagList p = b.getTagList("pos", 3);
            int state = b.getInteger("state");
            if (p.tagCount() != 3 || state < 0 || state >= map.length) {
                continue;
            }
            int x = p.getIntAt(0), y = p.getIntAt(1), z = p.getIntAt(2);
            if (x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= l) {
                continue;
            }
            int i = (y * l + z) * w + x;
            if (map[state] < 0) {
                unknown++;
                continue;
            }
            states[i] = map[state];
            if (b.hasKey("nbt", 10)) {
                NBTTagCompound tag = b.getCompoundTag("nbt").copy();
                tag.setInteger("x", x);
                tag.setInteger("y", y);
                tag.setInteger("z", z);
                tiles.put(i, tag);
            }
        }
        return new Clipboard(w, h, l, states, tiles);
    }

    // ---- Clásico (.schematic) ---------------------------------------------------------------

    private static void writeClassic(Clipboard c, File f) throws IOException {
        int n = c.states.length;
        byte[] blocks = new byte[n];
        byte[] data = new byte[n];
        byte[] add = null;
        for (int i = 0; i < n; i++) {
            int id = c.states[i] >> 4;
            blocks[i] = (byte) id;
            data[i] = (byte) (c.states[i] & 15);
            if (id > 255) {
                if (add == null) {
                    add = new byte[(n + 1) / 2];
                }
                int high = (id >> 8) & 15;
                add[i >> 1] |= (i & 1) == 0 ? high : high << 4;
            }
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setShort("Width", (short) c.sx);
        root.setShort("Height", (short) c.sy);
        root.setShort("Length", (short) c.sz);
        root.setString("Materials", "Alpha");
        root.setByteArray("Blocks", blocks);
        root.setByteArray("Data", data);
        if (add != null) {
            root.setByteArray("AddBlocks", add);
        }
        root.setTag("Entities", new NBTTagList());
        NBTTagList tiles = new NBTTagList();
        for (Map.Entry<Integer, NBTTagCompound> e : c.tiles.entrySet()) {
            int i = e.getKey();
            NBTTagCompound tag = e.getValue().copy();
            tag.setInteger("x", i % c.sx);
            tag.setInteger("y", i / (c.sx * c.sz));
            tag.setInteger("z", (i / c.sx) % c.sz);
            tiles.appendTag(tag);
        }
        root.setTag("TileEntities", tiles);
        try (OutputStream out = new FileOutputStream(f)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
    }

    private static Clipboard readClassic(File f) throws IOException {
        NBTTagCompound root = readNbt(f);
        int w = root.getShort("Width") & 0xFFFF;
        int h = root.getShort("Height") & 0xFFFF;
        int l = root.getShort("Length") & 0xFFFF;
        byte[] blocks = root.getByteArray("Blocks");
        byte[] data = root.getByteArray("Data");
        byte[] add = root.hasKey("AddBlocks", 7) ? root.getByteArray("AddBlocks") : null;
        long volume = (long) w * h * l;
        if (volume == 0 || blocks.length != volume || data.length != volume) {
            throw new IOException("Schematic no válido (las dimensiones no coinciden con los datos)");
        }
        if (volume > EditEngine.MAX_BLOCKS) {
            throw new IOException("Schematic demasiado grande: " + volume + " bloques (máximo " + EditEngine.MAX_BLOCKS + ")");
        }
        int n = (int) volume;
        int[] cache = new int[1 << 16];
        Arrays.fill(cache, -1);
        int[] states = new int[n];
        for (int i = 0; i < n; i++) {
            int id = blocks[i] & 0xFF;
            if (add != null) {
                int hi = (i & 1) == 0 ? add[i >> 1] & 15 : (add[i >> 1] >> 4) & 15;
                id |= hi << 8;
            }
            int sid = (id << 4) | (data[i] & 15);
            if (cache[sid] < 0) {
                cache[sid] = resolve(id, data[i] & 15);
            }
            states[i] = cache[sid];
        }
        Map<Integer, NBTTagCompound> tiles = new HashMap<Integer, NBTTagCompound>();
        NBTTagList list = root.getTagList("TileEntities", 10);
        for (int t = 0; t < list.tagCount(); t++) {
            NBTTagCompound tag = list.getCompoundTagAt(t);
            int x = tag.getInteger("x"), y = tag.getInteger("y"), z = tag.getInteger("z");
            if (x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < l) {
                tiles.put((y * l + z) * w + x, tag);
            }
        }
        return new Clipboard(w, h, l, states, tiles);
    }

    /** Estado de un par (id, meta) del archivo; aire si el id no existe en este juego. */
    private static int resolve(int id, int meta) {
        Block b = Block.getBlockById(id);
        IBlockState s;
        try {
            s = b.getStateFromMeta(meta);
        } catch (RuntimeException e) {
            s = b.getDefaultState();
        }
        return Block.getStateId(s);
    }
}
