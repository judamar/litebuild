package dev.litbuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.luaj.vm2.LuaError;

import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;

/**
 * Script Brush: un script de Lua que se ejecuta por cada bloque del pincel. El texto puede llevar variables de
 * plantilla entre $ que se sustituyen por los valores de unos controles que se generan solos:
 * $int(título, valor, mín, máx)$, $float(...)$, $boolean(título, valor)$, $blockState(título, bloque)$ y
 * $once$ (el script corre una sola vez por sello, en el bloque apuntado).
 */
public final class ScriptBrush {
    private static final Pattern TEMPLATE =
            Pattern.compile("\\$(once|blockState|int|float|boolean)(?:\\(([^)]*)\\))?\\$");

    public static final String[] EXAMPLE_NAMES = {"Texturing", "Spikes", "Noise"};
    public static final String[] EXAMPLES = {
        "-- Cambia parte de la hierba por tierra, en manchas de ruido.\n"
            + "-- x, y, z es el bloque que se está procesando; si devuelves un id de bloque, se coloca ahí.\n"
            + "local umbral = $float(Umbral, 0.6, 0, 1)$\n"
            + "if getBlock(x, y, z) == blocks.grass then\n"
            + "  if getSimplexNoise(x / 6, y / 6, z / 6, 1) > umbral then\n"
            + "    return blocks.dirt\n"
            + "  end\n"
            + "end\n",
        "$once$\n"
            + "-- Una columna sobre el bloque apuntado, una vez por clic.\n"
            + "local altura = $int(Altura, 6, 1, 20)$\n"
            + "local bloque = $blockState(Bloque, minecraft:stone)$\n"
            + "for i = 1, altura do\n"
            + "  setBlock(x, y + i, z, bloque)\n"
            + "end\n",
        "-- Los bloques sólidos pasan a cobblestone donde el ruido es alto.\n"
            + "local n = getSimplexNoise(x / 8, y / 8, z / 8, 7)\n"
            + "if isSolid(getBlock(x, y, z)) and n > 0.55 then\n"
            + "  return blocks.cobblestone\n"
            + "end\n",
    };

    /** Valores actuales de los controles de las variables de plantilla, por "tipo:título". */
    public static final Map<String, Object> values = new HashMap<String, Object>();

    private ScriptBrush() {
    }

    /** Una variable de plantilla del texto del script. */
    public static final class Var {
        public final String kind;
        public final String title;
        final String[] args;

        Var(String kind, String[] args) {
            this.kind = kind;
            this.args = args;
            this.title = args.length > 0 && !args[0].isEmpty() ? args[0] : kind;
        }

        String key() {
            return kind + ":" + title;
        }

        private String arg(int i, String def) {
            return i < args.length && !args[i].isEmpty() ? args[i] : def;
        }

        /** El valor del control de esta variable; si no existe aún, lo crea con el valor por defecto. */
        public Object value() {
            Object v = values.get(key());
            if (v != null) {
                return v;
            }
            if (kind.equals("int")) {
                v = new int[] {parseInt(arg(1, "0"), 0)};
            } else if (kind.equals("float")) {
                v = new float[] {(float) parseDouble(arg(1, "0"), 0)};
            } else if (kind.equals("boolean")) {
                v = new ImBoolean(arg(1, "false").equalsIgnoreCase("true"));
            } else {
                String id = arg(1, "minecraft:stone");
                int meta = 0;
                int colon = id.lastIndexOf(':');
                if (colon > 0 && id.substring(colon + 1).matches("\\d+")) {
                    meta = Integer.parseInt(id.substring(colon + 1));
                    id = id.substring(0, colon);
                }
                v = new Object[] {new ImString(id, 128), new int[] {meta}};
            }
            values.put(key(), v);
            return v;
        }

        public int min() {
            return parseInt(arg(2, "0"), 0);
        }

        public int max() {
            return parseInt(arg(3, "100"), 100);
        }

        public float minF() {
            return (float) parseDouble(arg(2, "0"), 0);
        }

        public float maxF() {
            return (float) parseDouble(arg(3, "1"), 1);
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Las variables de plantilla del script que generan un control (sin repetidas, en orden). */
    public static List<Var> vars(String code) {
        List<Var> out = new ArrayList<Var>();
        Set<String> seen = new HashSet<String>();
        Matcher m = TEMPLATE.matcher(code);
        while (m.find()) {
            if (m.group(1).equals("once")) {
                continue;
            }
            String[] args = m.group(2) == null ? new String[0] : m.group(2).split(",", -1);
            for (int i = 0; i < args.length; i++) {
                args[i] = args[i].trim();
            }
            Var v = new Var(m.group(1), args);
            if (seen.add(v.key())) {
                out.add(v);
            }
        }
        return out;
    }

    /** El script con cada variable de plantilla sustituida por el valor actual de su control. */
    private static String process(String code) {
        Matcher m = TEMPLATE.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String repl = "";
            if (!m.group(1).equals("once")) {
                String[] args = m.group(2) == null ? new String[0] : m.group(2).split(",", -1);
                for (int i = 0; i < args.length; i++) {
                    args[i] = args[i].trim();
                }
                Var v = new Var(m.group(1), args);
                Object val = v.value();
                if (val instanceof int[]) {
                    repl = Integer.toString(((int[]) val)[0]);
                } else if (val instanceof float[]) {
                    repl = String.format(Locale.ROOT, "%f", ((float[]) val)[0]);
                } else if (val instanceof ImBoolean) {
                    repl = Boolean.toString(((ImBoolean) val).get());
                } else {
                    Object[] pair = (Object[]) val;
                    Block b = Block.getBlockFromName(((ImString) pair[0]).get().trim());
                    int meta = Math.max(0, ((int[]) pair[1])[0]);
                    IBlockState s = null;
                    if (b != null) {
                        try {
                            s = b.getStateFromMeta(meta);
                        } catch (RuntimeException e) {
                            s = b.getDefaultState();
                        }
                    }
                    repl = Integer.toString(s == null ? 0 : Block.getStateId(s));
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Congela el script y los valores de sus controles para un trazo. Hay que llamarlo en el hilo del cliente. */
    public static Spec build(String code) {
        boolean once = false;
        Matcher m = TEMPLATE.matcher(code);
        while (m.find()) {
            once |= m.group(1).equals("once");
        }
        String processed = process(code);
        if (processed.contains("RGB")) {
            BlockColors.ensureBuilt();
        }
        return new Spec(processed, once);
    }

    /** Un script listo para un trazo; el intérprete se crea la primera vez que se usa, en el hilo del servidor. */
    public static final class Spec {
        private final String code;
        private final boolean once;
        private ScriptRunner runner;
        private boolean failed;

        Spec(String code, boolean once) {
            this.code = code;
            this.once = once;
        }

        /** Comprueba la sintaxis del script (sin ejecutarlo). Devuelve el error o null si está bien. */
        public static String check(String code) {
            try {
                new ScriptRunner(null, process(code));
                return null;
            } catch (LuaError e) {
                return firstLine(e.getMessage());
            }
        }

        /** Un sello del pincel (hilo del servidor). Un error detiene el script durante el resto del trazo. */
        public void run(World w, int cx, int cy, int cz, int radius, int shape, MaskSpec mask, Terrain.Setter out) {
            if (failed) {
                return;
            }
            try {
                if (runner == null || runner.world() != w) {
                    runner = new ScriptRunner(w, code);
                }
                runner.run(once, cx, cy, cz, radius, shape, mask, out);
            } catch (LuaError e) {
                failed = true;
                EditEngine.status = "Script: " + firstLine(e.getMessage());
            } catch (RuntimeException e) {
                failed = true;
                EditEngine.status = "Script: " + e;
            }
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "error desconocido";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
