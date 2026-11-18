package dev.litebuild;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;

/**
 * Presets de herramienta: guardan los ajustes de una herramienta con un nombre y los restauran. Cada herramienta
 * entrega sus ajustes como un mapa clave -> objeto vivo (ImInt, ImBoolean, ImString, int[], float[] o {@link Custom});
 * no hace falta reflexión. Se guardan en config/litebuild/presets/&lt;herramienta&gt;.txt ("[nombre]" y debajo líneas
 * clave=valor, con \n y \\ escapados).
 */
public final class Presets {
    /** Un ajuste con forma propia (listas de filas...): se guarda como una sola línea de texto. */
    public interface Custom {
        String save();

        void load(String text);
    }

    public static final class Preset {
        public final String name;
        final Map<String, String> values;

        Preset(String name, Map<String, String> values) {
            this.name = name;
            this.values = values;
        }
    }

    private static final Map<String, List<Preset>> CACHE = new HashMap<String, List<Preset>>();

    private Presets() {
    }

    private static File file(String tool) {
        File d = new File(Minecraft.getMinecraft().mcDataDir, "config/litebuild/presets");
        d.mkdirs();
        return new File(d, tool.replaceAll("[^A-Za-z0-9._-]", "_") + ".txt");
    }

    public static List<Preset> list(String tool) {
        List<Preset> l = CACHE.get(tool);
        if (l == null) {
            l = read(file(tool));
            CACHE.put(tool, l);
        }
        return l;
    }

    public static void save(String tool, String name, Map<String, Object> holders) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> e : holders.entrySet()) {
            String v = dump(e.getValue());
            if (v != null) {
                values.put(e.getKey(), v);
            }
        }
        List<Preset> l = list(tool);
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).name.equals(name)) {
                l.remove(i);
            }
        }
        l.add(new Preset(name, values));
        write(tool, l);
    }

    public static void delete(String tool, int index) {
        List<Preset> l = list(tool);
        if (index >= 0 && index < l.size()) {
            l.remove(index);
            write(tool, l);
        }
    }

    /** Restaura en los objetos vivos lo que guarda el preset (lo que falte se deja como está). */
    public static void apply(Preset p, Map<String, Object> holders) {
        for (Map.Entry<String, Object> e : holders.entrySet()) {
            String v = p.values.get(e.getKey());
            if (v != null) {
                try {
                    load(e.getValue(), v);
                } catch (RuntimeException ex) {
                    LiteModLiteBuild.LOG.warn("Preset: no se pudo restaurar " + e.getKey(), ex);
                }
            }
        }
    }

    // ---- Valores ----------------------------------------------------------------------------

    private static String dump(Object o) {
        if (o instanceof ImInt) {
            return Integer.toString(((ImInt) o).get());
        } else if (o instanceof ImBoolean) {
            return Boolean.toString(((ImBoolean) o).get());
        } else if (o instanceof ImString) {
            return ((ImString) o).get();
        } else if (o instanceof int[]) {
            StringBuilder sb = new StringBuilder();
            for (int v : (int[]) o) {
                sb.append(sb.length() == 0 ? "" : ",").append(v);
            }
            return sb.toString();
        } else if (o instanceof float[]) {
            StringBuilder sb = new StringBuilder();
            for (float v : (float[]) o) {
                sb.append(sb.length() == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.6f", v));
            }
            return sb.toString();
        } else if (o instanceof Custom) {
            return ((Custom) o).save();
        }
        return null;
    }

    private static void load(Object o, String v) {
        if (o instanceof ImInt) {
            ((ImInt) o).set(Integer.parseInt(v.trim()));
        } else if (o instanceof ImBoolean) {
            ((ImBoolean) o).set(Boolean.parseBoolean(v.trim()));
        } else if (o instanceof ImString) {
            ((ImString) o).set(v);
        } else if (o instanceof int[]) {
            int[] a = (int[]) o;
            String[] t = v.split(",");
            for (int i = 0; i < a.length && i < t.length; i++) {
                a[i] = Integer.parseInt(t[i].trim());
            }
        } else if (o instanceof float[]) {
            float[] a = (float[]) o;
            String[] t = v.split(",");
            for (int i = 0; i < a.length && i < t.length; i++) {
                a[i] = Float.parseFloat(t[i].trim());
            }
        } else if (o instanceof Custom) {
            ((Custom) o).load(v);
        }
    }

    // ---- Archivo ----------------------------------------------------------------------------

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void write(String tool, List<Preset> l) {
        StringBuilder sb = new StringBuilder();
        for (Preset p : l) {
            sb.append('[').append(escape(p.name)).append("]\n");
            for (Map.Entry<String, String> e : p.values.entrySet()) {
                sb.append(e.getKey()).append('=').append(escape(e.getValue())).append('\n');
            }
        }
        try {
            Files.write(file(tool).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudieron guardar los presets de " + tool, e);
        }
    }

    private static List<Preset> read(File f) {
        List<Preset> out = new ArrayList<Preset>();
        if (!f.isFile()) {
            return out;
        }
        try {
            Map<String, String> cur = null;
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("[") && line.endsWith("]")) {
                    cur = new LinkedHashMap<String, String>();
                    out.add(new Preset(unescape(line.substring(1, line.length() - 1)), cur));
                } else if (cur != null) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        cur.put(line.substring(0, eq), unescape(line.substring(eq + 1)));
                    }
                }
            }
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudieron leer los presets de " + f.getName(), e);
        }
        return out;
    }
}
