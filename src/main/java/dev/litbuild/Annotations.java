package dev.litbuild;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Anotaciones para planificar sobre el terreno: líneas, bloques resaltados y textos. Se guardan por mundo en
 * saves/&lt;mundo&gt;/litbuild-annotations.txt y tienen su propio deshacer (Ctrl+Z con la herramienta activa).
 * Las líneas y los contornos se dibujan en el mundo (WorldOverlay); los textos, con ImGui (solo dentro del editor).
 */
public final class Annotations {
    public static final int LINE = 0, OUTLINE = 1, TEXT = 2, IMAGE = 3, ERASE = 4, MOVE = 5;
    public static final String[] MODES = {"Draw", "Outline", "Text", "Image", "Erase", "Move"};
    public static final String[] OUTLINE_MODES = {"Freehand", "Box"};
    private static final int MAX_BLOCKS = 5000;

    /** Una anotación: p guarda ternas x,y,z (puntos de la línea, bloques del contorno o la posición del texto). */
    public static final class Item {
        public int type;
        public int color;
        public float width = 2f;
        public double[] p = new double[0];
        public String text = "";
        public boolean shadow = true;

        int count() {
            return p.length / 3;
        }
    }

    private static final class Action {
        final List<Item> added = new ArrayList<Item>();
        final List<Item> removed = new ArrayList<Item>();
    }

    public static final List<Item> items = new ArrayList<Item>();
    public static final ImBoolean show = new ImBoolean(true);
    public static final ImInt mode = new ImInt(0);
    public static final ImInt outlineMode = new ImInt(0);
    public static final float[] color = {1f, 0.8f, 0.2f};
    public static final float[] width = {3f};
    public static final float[] eraseRadius = {3f};
    public static final ImString text = new ImString("Texto", 200);
    public static final ImBoolean shadow = new ImBoolean(true);
    public static final ImString imageUrl = new ImString("", 512);
    public static final float[] imageWidth = {8f};

    private static final Deque<Action> UNDO = new ArrayDeque<Action>();
    private static final Deque<Action> REDO = new ArrayDeque<Action>();
    private static String loadedFor;
    /** Trazo en curso (línea o contorno a mano alzada) y lo borrado en un trazo de Erase. */
    private static Item current;
    private static Action erasing;
    private static BlockPos boxCorner;
    /** Texto o imagen que se está moviendo y cómo era antes de empezar. */
    private static Item moving;
    private static Item movingBefore;

    private Annotations() {
    }

    // ---- Entrada --------------------------------------------------------------------------

    /** Llamar cada frame con la herramienta activa. click = botón derecho recién pulsado; down = mantenido. */
    public static void handle(boolean click, boolean down, BlockPos pos, EnumFacing side, Vec3d hit) {
        ensureLoaded();
        int m = mode.get();
        if (m == MOVE) {
            move(click, down, hit, side);
            return;
        }
        if (m == IMAGE) {
            if (click && hit != null && side != null && !imageUrl.get().trim().isEmpty()) {
                Item im = new Item();
                im.type = IMAGE;
                im.color = 0xFFFFFF;
                im.width = imageWidth[0];
                im.text = imageUrl.get().trim();
                im.p = new double[] {hit.x + side.getFrontOffsetX() * 0.03, hit.y + side.getFrontOffsetY() * 0.03,
                    hit.z + side.getFrontOffsetZ() * 0.03, side.getFrontOffsetX(), side.getFrontOffsetY(),
                    side.getFrontOffsetZ()};
                Action a = new Action();
                a.added.add(im);
                items.add(im);
                push(a);
                save();
            }
            return;
        }
        if (m == ERASE) {
            if (click) {
                erasing = new Action();
            }
            if (down && erasing != null && hit != null) {
                eraseNear(hit, eraseRadius[0], erasing);
            }
            if (!down && erasing != null) {
                if (!erasing.removed.isEmpty()) {
                    push(erasing);
                    save();
                }
                erasing = null;
            }
            return;
        }
        if (m == TEXT) {
            if (click && hit != null && side != null) {
                Item t = new Item();
                t.type = TEXT;
                t.color = rgb();
                t.text = text.get().replace('\n', ' ');
                t.shadow = shadow.get();
                t.p = new double[] {hit.x + side.getFrontOffsetX() * 0.1, hit.y + side.getFrontOffsetY() * 0.1,
                    hit.z + side.getFrontOffsetZ() * 0.1};
                Action a = new Action();
                a.added.add(t);
                items.add(t);
                push(a);
                save();
            }
            return;
        }
        if (m == LINE) {
            if (click && hit != null) {
                current = new Item();
                current.type = LINE;
                current.color = rgb();
                current.width = width[0];
            }
            if (down && current != null && hit != null && side != null) {
                double x = hit.x + side.getFrontOffsetX() * 0.03, y = hit.y + side.getFrontOffsetY() * 0.03,
                        z = hit.z + side.getFrontOffsetZ() * 0.03;
                int n = current.count();
                if (n == 0 || dist2(current.p, n - 1, x, y, z) >= 0.06) {
                    append(current, x, y, z);
                }
            }
            if (!down && current != null) {
                finishCurrent();
            }
            return;
        }
        // Outline
        if (outlineMode.get() == 0) {
            if (click && pos != null) {
                current = new Item();
                current.type = OUTLINE;
                current.color = rgb();
            }
            if (down && current != null && pos != null && current.count() < MAX_BLOCKS && !has(current, pos)) {
                append(current, pos.getX(), pos.getY(), pos.getZ());
            }
            if (!down && current != null) {
                finishCurrent();
            }
        } else {
            if (click && pos != null) {
                boxCorner = pos;
            }
            if (!down && boxCorner != null) {
                BlockPos a = boxCorner;
                boxCorner = null;
                if (pos != null) {
                    outlineBox(a, pos);
                }
            }
        }
    }

    /** Move: arrastra el texto o la imagen más cercana al cursor (a menos de 4 bloques) a otro sitio de la superficie. */
    private static void move(boolean click, boolean down, Vec3d hit, EnumFacing side) {
        if (click && hit != null) {
            double best = 16;
            for (Item it : items) {
                if (it.type != TEXT && it.type != IMAGE) {
                    continue;
                }
                double dx = it.p[0] - hit.x, dy = it.p[1] - hit.y, dz = it.p[2] - hit.z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < best) {
                    best = d;
                    moving = it;
                }
            }
            if (moving != null) {
                movingBefore = copy(moving);
            }
        }
        if (down && moving != null && hit != null && side != null) {
            double k = moving.type == IMAGE ? 0.03 : 0.1;
            moving.p[0] = hit.x + side.getFrontOffsetX() * k;
            moving.p[1] = hit.y + side.getFrontOffsetY() * k;
            moving.p[2] = hit.z + side.getFrontOffsetZ() * k;
            if (moving.type == IMAGE) {
                moving.p[3] = side.getFrontOffsetX();
                moving.p[4] = side.getFrontOffsetY();
                moving.p[5] = side.getFrontOffsetZ();
            }
        }
        if (!down && moving != null) {
            Action a = new Action();
            a.removed.add(movingBefore);
            a.added.add(moving);
            push(a);
            save();
            moving = null;
            movingBefore = null;
        }
    }

    private static Item copy(Item it) {
        Item c = new Item();
        c.type = it.type;
        c.color = it.color;
        c.width = it.width;
        c.p = it.p.clone();
        c.text = it.text;
        c.shadow = it.shadow;
        return c;
    }

    /** true mientras hay una esquina de Box esperando la segunda. */
    public static boolean boxPending() {
        return boxCorner != null;
    }

    private static void outlineBox(BlockPos a, BlockPos b) {
        World w = Minecraft.getMinecraft().world;
        Item it = new Item();
        it.type = OUTLINE;
        it.color = rgb();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int n = 0;
        scan:
        for (int y = Math.min(a.getY(), b.getY()); y <= Math.max(a.getY(), b.getY()); y++) {
            for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) {
                for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++) {
                    q.setPos(x, y, z);
                    IBlockState s = w.isBlockLoaded(q) ? w.getBlockState(q) : null;
                    if (s != null && s.getMaterial() != net.minecraft.block.material.Material.AIR) {
                        if (n >= MAX_BLOCKS) {
                            EditEngine.status = "Annotation: solo se anotan los primeros " + MAX_BLOCKS + " bloques";
                            break scan;
                        }
                        append(it, x, y, z);
                        n++;
                    }
                }
            }
        }
        if (it.count() > 0) {
            current = it;
            finishCurrent();
        }
    }

    private static void finishCurrent() {
        Item it = current;
        current = null;
        if (it.count() == 0 || it.type == LINE && it.count() < 2) {
            return;
        }
        items.add(it);
        Action a = new Action();
        a.added.add(it);
        push(a);
        save();
    }

    private static void append(Item it, double x, double y, double z) {
        double[] n = java.util.Arrays.copyOf(it.p, it.p.length + 3);
        n[n.length - 3] = x;
        n[n.length - 2] = y;
        n[n.length - 1] = z;
        it.p = n;
    }

    private static boolean has(Item it, BlockPos pos) {
        for (int i = 0; i < it.count(); i++) {
            if (it.p[i * 3] == pos.getX() && it.p[i * 3 + 1] == pos.getY() && it.p[i * 3 + 2] == pos.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static double dist2(double[] p, int i, double x, double y, double z) {
        double dx = p[i * 3] - x, dy = p[i * 3 + 1] - y, dz = p[i * 3 + 2] - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void eraseNear(Vec3d hit, double radius, Action into) {
        double r2 = radius * radius;
        for (int k = items.size() - 1; k >= 0; k--) {
            Item it = items.get(k);
            boolean near = false;
            int n = it.type == TEXT || it.type == IMAGE ? 1 : it.count();
            for (int i = 0; i < n && !near; i++) {
                // Los bloques de un contorno miden 1: se mide al centro del bloque.
                double off = it.type == OUTLINE ? 0.5 : 0;
                double dx = it.p[i * 3] + off - hit.x, dy = it.p[i * 3 + 1] + off - hit.y, dz = it.p[i * 3 + 2] + off - hit.z;
                near = dx * dx + dy * dy + dz * dz <= r2;
            }
            if (near) {
                items.remove(k);
                into.removed.add(it);
            }
        }
    }

    private static int rgb() {
        return ((int) (color[0] * 255) & 255) << 16 | ((int) (color[1] * 255) & 255) << 8 | ((int) (color[2] * 255) & 255);
    }

    // ---- Historial propio -------------------------------------------------------------------

    private static void push(Action a) {
        UNDO.push(a);
        REDO.clear();
        while (UNDO.size() > 50) {
            UNDO.removeLast();
        }
    }

    public static boolean canUndo() {
        return !UNDO.isEmpty();
    }

    public static boolean canRedo() {
        return !REDO.isEmpty();
    }

    public static void undo() {
        Action a = UNDO.poll();
        if (a == null) {
            return;
        }
        items.removeAll(a.added);
        items.addAll(a.removed);
        REDO.push(a);
        save();
        EditEngine.status = "Annotation: deshecho";
    }

    public static void redo() {
        Action a = REDO.poll();
        if (a == null) {
            return;
        }
        items.removeAll(a.removed);
        items.addAll(a.added);
        UNDO.push(a);
        save();
        EditEngine.status = "Annotation: rehecho";
    }

    /** Borra todo; no se puede deshacer. */
    public static void clearAll() {
        items.clear();
        UNDO.clear();
        REDO.clear();
        save();
        EditEngine.status = "Annotation: todas las anotaciones borradas";
    }

    // ---- Texto y dibujo ---------------------------------------------------------------------

    /** El trazo en curso, para dibujarlo mientras se hace (null si no hay). */
    public static Item current() {
        return current;
    }

    // ---- Guardado ---------------------------------------------------------------------------

    private static File file() {
        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) {
            return null;
        }
        return new File(Minecraft.getMinecraft().mcDataDir, "saves/" + server.getFolderName() + "/litbuild-annotations.txt");
    }

    /** El archivo que usaba el mod con su nombre anterior (AxiomLite); se lee si aún no existe el nuevo. */
    private static File legacyFile() {
        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        return server == null ? null : new File(Minecraft.getMinecraft().mcDataDir,
                "saves/" + server.getFolderName() + "/axiomlite-annotations.txt");
    }

    /** Carga las anotaciones del mundo actual la primera vez y cada vez que cambia de mundo. */
    public static void ensureLoaded() {
        File f = file();
        String key = f == null ? "" : f.getPath();
        if (key.equals(loadedFor)) {
            return;
        }
        loadedFor = key;
        items.clear();
        UNDO.clear();
        REDO.clear();
        File legacy = legacyFile();
        if (f != null && !f.isFile() && legacy != null && legacy.isFile()) {
            f = legacy;
        }
        if (f == null || !f.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                Item it = parse(line);
                if (it != null) {
                    items.add(it);
                }
            }
        } catch (IOException e) {
            LiteModLitBuild.LOG.warn("No se pudieron leer las anotaciones", e);
        }
    }

    public static void save() {
        File f = file();
        if (f == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            sb.append(it.type == LINE ? "L" : it.type == OUTLINE ? "O" : it.type == IMAGE ? "I" : "T").append(' ')
                    .append(it.color).append(' ');
            sb.append(it.type == LINE || it.type == IMAGE ? String.format(Locale.ROOT, "%.1f", it.width)
                    : it.type == TEXT ? (it.shadow ? "1" : "0") : "0").append(' ');
            for (int i = 0; i < it.count(); i++) {
                sb.append(i > 0 ? ";" : "").append(String.format(Locale.ROOT, "%.3f,%.3f,%.3f", it.p[i * 3], it.p[i * 3 + 1],
                        it.p[i * 3 + 2]));
            }
            if (it.type == TEXT || it.type == IMAGE) {
                sb.append(' ').append(it.text.replace('\n', ' '));
            }
            sb.append('\n');
        }
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LiteModLitBuild.LOG.warn("No se pudieron guardar las anotaciones", e);
        }
    }

    private static Item parse(String line) {
        try {
            String[] f = line.split(" ", 5);
            Item it = new Item();
            it.type = f[0].equals("L") ? LINE : f[0].equals("O") ? OUTLINE : f[0].equals("I") ? IMAGE : TEXT;
            it.color = Integer.parseInt(f[1]);
            if (it.type == LINE) {
                it.width = Float.parseFloat(f[2]);
            } else if (it.type == IMAGE) {
                it.width = Float.parseFloat(f[2]);
                it.text = f.length > 4 ? f[4] : "";
            } else if (it.type == TEXT) {
                it.shadow = f[2].equals("1");
                it.text = f.length > 4 ? f[4] : "";
            }
            String[] pts = f[3].split(";");
            it.p = new double[pts.length * 3];
            for (int i = 0; i < pts.length; i++) {
                String[] c = pts[i].split(",");
                for (int k = 0; k < 3; k++) {
                    it.p[i * 3 + k] = Double.parseDouble(c[k]);
                }
            }
            return it;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
