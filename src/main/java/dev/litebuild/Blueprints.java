package dev.litebuild;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Blueprints propios: prefabricados en .schem (Sponge) con metadatos (nombre, autores, etiquetas y la rotación
 * de la miniatura) dentro del compuesto Metadata, que otros lectores de .schem ignoran. Axiom usa su propio
 * formato .bp, que es cerrado; estos se pueden abrir también desde WorldEdit u otras herramientas.
 * Viven en config/litebuild/blueprints, con subcarpetas.
 */
public final class Blueprints {
    public static final int THUMB = 96;
    public static final String[] DEFAULT_TAGS = {"Building", "House", "Castle", "Tower", "Wall", "Bridge", "Road", "Tree",
        "Plant", "Rock", "Terrain", "Cave", "Ship", "Vehicle", "Furniture", "Interior", "Decoration", "Medieval", "Modern",
        "Fantasy", "Sci-Fi", "Ruins", "Farm", "Redstone"};

    /** Un blueprint del navegador: los metadatos y la miniatura se leen en segundo plano. */
    public static final class Entry {
        public File file;
        /** Ruta relativa a la carpeta de blueprints, con "/". */
        public String rel;
        public String name;
        public String authors = "";
        public List<String> tags = new ArrayList<String>();
        public int sx, sy, sz, blocks;
        /** Miniatura ARGB (THUMB x THUMB); null si falló la lectura. */
        public int[] pixels;
        public String error;
        /** Textura de OpenGL de la miniatura (0 = aún sin subir). Solo se toca desde el hilo de render. */
        public int texture;
        String key;
    }

    /** Resultado del último refresh; crece mientras el hilo de lectura trabaja. */
    public static volatile List<Entry> entries = new CopyOnWriteArrayList<Entry>();
    public static volatile boolean scanning;
    private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();
    private static long generation;

    private Blueprints() {
    }

    public static File dir() {
        File d = new File(Minecraft.getMinecraft().mcDataDir, "config/litebuild/blueprints");
        d.mkdirs();
        return d;
    }

    // ---- Etiquetas ------------------------------------------------------------------------

    private static File tagFile() {
        return new File(dir().getParentFile(), "blueprint-tags.txt");
    }

    /** Las etiquetas por defecto más las creadas por el usuario, sin repetir y en orden alfabético. */
    public static List<String> allTags() {
        TreeSet<String> all = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(Arrays.asList(DEFAULT_TAGS));
        try {
            if (tagFile().isFile()) {
                for (String line : Files.readAllLines(tagFile().toPath(), StandardCharsets.UTF_8)) {
                    if (!line.trim().isEmpty()) {
                        all.add(line.trim());
                    }
                }
            }
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudieron leer las etiquetas", e);
        }
        return new ArrayList<String>(all);
    }

    public static void addTag(String tag) {
        tag = tag.trim().replace(",", " ");
        if (tag.isEmpty() || allTags().contains(tag)) {
            return;
        }
        try {
            Files.write(tagFile().toPath(), (tag + "\n").getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudo guardar la etiqueta", e);
        }
    }

    // ---- Guardar --------------------------------------------------------------------------

    /**
     * Guarda c como blueprint en folder (ruta relativa dentro de la carpeta de blueprints; "" = raíz). Devuelve el
     * archivo creado; si el nombre ya existe se añade un número.
     */
    public static File save(Clipboard c, String name, String authors, Collection<String> tags, int thumbRot, String folder)
            throws IOException {
        File base = dir();
        File target = base;
        if (folder != null && !folder.trim().isEmpty()) {
            target = new File(base, folder.trim().replace('\\', '/'));
            if (!target.getCanonicalPath().startsWith(base.getCanonicalPath())) {
                throw new IOException("La carpeta debe estar dentro de la carpeta de blueprints");
            }
            target.mkdirs();
        }
        String safe = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) {
            throw new IOException("Falta el nombre");
        }
        File f = new File(target, safe + ".schem");
        for (int i = 2; f.exists(); i++) {
            f = new File(target, safe + " (" + i + ").schem");
        }
        NBTTagCompound meta = new NBTTagCompound();
        meta.setString("Name", name.trim());
        meta.setString("Author", authors.trim());
        meta.setString("Date", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()));
        meta.setString("Tags", join(tags));
        meta.setInteger("ThumbRotation", Math.floorMod(thumbRot, 4));
        Schematics.writeSponge(c, f, meta);
        return f;
    }

    private static String join(Collection<String> tags) {
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            sb.append(sb.length() == 0 ? "" : ",").append(t);
        }
        return sb.toString();
    }

    // ---- Leer -----------------------------------------------------------------------------

    /** Lee el blueprint entero para dejarlo en el portapapeles. */
    public static Clipboard load(Entry e) throws IOException {
        return Schematics.read(e.file);
    }

    /**
     * Vuelve a listar los blueprints de folder (relativa a la raíz; "" = raíz), con subcarpetas si recursive. La
     * lectura de los archivos va en un hilo aparte; entries se va llenando. Hay que llamarlo desde el hilo de render
     * (necesita los colores de los bloques).
     */
    public static void refresh(final String folder, final boolean recursive) {
        BlockColors.ensureBuilt();
        final long gen;
        synchronized (CACHE) {
            gen = ++generation;
        }
        final List<Entry> fresh = new CopyOnWriteArrayList<Entry>();
        entries = fresh;
        scanning = true;
        final File base = dir();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File start = folder.isEmpty() ? base : new File(base, folder);
                    List<File> files = new ArrayList<File>();
                    collect(start, recursive, files);
                    Collections.sort(files, new java.util.Comparator<File>() {
                        @Override
                        public int compare(File a, File b) {
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    });
                    for (File f : files) {
                        synchronized (CACHE) {
                            if (gen != generation) {
                                return; // otro refresh más reciente
                            }
                        }
                        fresh.add(entryFor(f, base));
                    }
                } finally {
                    synchronized (CACHE) {
                        if (gen == generation) {
                            scanning = false;
                        }
                    }
                }
            }
        }, "litebuild-blueprints");
        t.setDaemon(true);
        t.start();
    }

    private static void collect(File dir, boolean recursive, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                if (recursive) {
                    collect(f, true, out);
                }
            } else if (Schematics.formatOf(f.getName()) >= 0) {
                out.add(f);
            }
        }
    }

    /** Subcarpetas inmediatas de folder, en orden alfabético. */
    public static List<String> subfolders(String folder) {
        File start = folder.isEmpty() ? dir() : new File(dir(), folder);
        List<String> names = new ArrayList<String>();
        File[] list = start.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.isDirectory()) {
                    names.add(f.getName());
                }
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static Entry entryFor(File f, File base) {
        String key = f.getPath() + "|" + f.lastModified() + "|" + f.length();
        synchronized (CACHE) {
            Entry hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
        }
        Entry e = new Entry();
        e.file = f;
        e.key = key;
        e.rel = base.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        e.name = f.getName().replaceFirst("\\.[^.]+$", "");
        try {
            NBTTagCompound meta = null;
            Clipboard c;
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".schem")) {
                NBTTagCompound root = Schematics.readNbt(f);
                meta = Schematics.metadata(root);
                c = Schematics.readSponge(root);
            } else {
                c = Schematics.read(f);
            }
            int rot = 0;
            if (meta != null) {
                if (meta.hasKey("Name", 8) && !meta.getString("Name").isEmpty()) {
                    e.name = meta.getString("Name");
                }
                e.authors = meta.getString("Author");
                for (String t : meta.getString("Tags").split(",")) {
                    if (!t.trim().isEmpty()) {
                        e.tags.add(t.trim());
                    }
                }
                rot = meta.getInteger("ThumbRotation");
            }
            e.sx = c.sx;
            e.sy = c.sy;
            e.sz = c.sz;
            e.blocks = c.solidCount();
            e.pixels = thumbnail(c, rot);
        } catch (IOException | RuntimeException ex) {
            e.error = String.valueOf(ex.getMessage());
        }
        synchronized (CACHE) {
            CACHE.put(key, e);
        }
        return e;
    }

    /** Miniatura isométrica del portapapeles girado rot cuartos de vuelta. */
    public static int[] thumbnail(Clipboard c, int rot) {
        for (int i = Math.floorMod(rot, 4); i > 0; i--) {
            c = c.rotated();
        }
        int[] rgb = new int[c.states.length];
        for (int i = 0; i < rgb.length; i++) {
            int id = c.states[i];
            int col = BlockColors.get(id);
            rgb[i] = id == Clipboard.AIR ? -1 : col < 0 ? 0x808080 : col;
        }
        return Thumb.render(rgb, c.sx, c.sy, c.sz, THUMB);
    }

    // ---- Texturas (hilo de render) ----------------------------------------------------------

    /** Sube la miniatura a una textura de OpenGL si aún no está; devuelve su id o 0 si no hay miniatura. */
    public static int texture(Entry e) {
        if (e.texture == 0 && e.pixels != null) {
            e.texture = upload(e.pixels);
        }
        return e.texture;
    }

    public static int upload(int[] argb) {
        return Textures.upload(argb, THUMB, THUMB, false);
    }

    public static void free(int texture) {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
        }
    }
}
