package dev.litebuild;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

/**
 * Idiomas de la interfaz: español (el idioma del código fuente) e inglés. Los textos del código están en español; en
 * inglés se traducen al mostrarse con el catálogo {@code /lang/en.tsv}. Los nombres de las herramientas y de los
 * ajustes ya son los de Axiom (en inglés) y no se tocan.
 *
 * <p>El catálogo tiene dos tipos de entrada. Las exactas (un texto fijo) y las plantillas con huecos {@code {0}},
 * {@code {1}}... para los mensajes que llevan números o nombres ("{0}: {1} bloques"): se reconocen con una expresión
 * regular y cada hueco se traduce a su vez, de modo que "Script: {0}" sirve de enlace a un mensaje de error en español.
 * Un texto de varias líneas que no está entero se traduce línea a línea. Lo que no está en el catálogo se muestra tal cual.
 */
public final class I18n {
    public static final String[] LANGUAGES = {"Auto", "English", "Español"};
    public static final int AUTO = 0, ENGLISH = 1, SPANISH = 2;
    /** La elección del usuario (se guarda en {@link UiSettings}). Auto sigue el idioma del juego: español si empieza por "es". */
    public static int setting = AUTO;

    private static final String CATALOG = "/lang/en.tsv";
    private static final int MAX_DEPTH = 3;
    private static final Pattern HOLE = Pattern.compile("\\{(\\d+)\\}");

    private static final Map<String, String> exact = new HashMap<String, String>();
    private static final List<Template> templates = new ArrayList<Template>();
    private static boolean loaded;
    /** Resultados recientes (los mensajes con números cambian y no deben llenar la memoria). */
    private static final Map<String, String> cache = new LinkedHashMap<String, String>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 1024;
        }
    };

    private static final class Template {
        final Pattern pattern;
        final String english;
        final int literal;

        Template(Pattern pattern, String english, int literal) {
            this.pattern = pattern;
            this.english = english;
            this.literal = literal;
        }
    }

    private I18n() {
    }

    /** ¿Se muestra la interfaz en inglés ahora mismo? */
    public static boolean english() {
        if (setting == ENGLISH) {
            return true;
        }
        if (setting == SPANISH) {
            return false;
        }
        try {
            String lang = Minecraft.getMinecraft().gameSettings.language;
            return lang == null || !lang.toLowerCase(java.util.Locale.ROOT).startsWith("es");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** El texto (en español, como está en el código) en el idioma elegido. */
    public static synchronized String tr(String s) {
        if (s == null || s.isEmpty() || !english()) {
            return s;
        }
        String hit = cache.get(s);
        if (hit == null) {
            hit = translate(s);
            cache.put(s, hit);
        }
        return hit;
    }

    /** Cambia la elección y vacía la caché. */
    public static synchronized void set(int value) {
        setting = Math.max(0, Math.min(LANGUAGES.length - 1, value));
        cache.clear();
    }

    /** La traducción al inglés sin mirar el idioma elegido (la parte pura, para los tests). */
    public static synchronized String translate(String s) {
        ensureLoaded();
        return translate(s, 0);
    }

    private static String translate(String s, int depth) {
        String e = exact.get(s);
        if (e != null) {
            return e;
        }
        for (Template t : templates) {
            Matcher m = t.pattern.matcher(s);
            if (m.matches()) {
                return fill(t.english, m, depth);
            }
        }
        if (s.indexOf('\n') >= 0) {
            String[] lines = s.split("\n", -1);
            boolean changed = false;
            for (int i = 0; i < lines.length; i++) {
                String t = depth < MAX_DEPTH ? translate(lines[i], depth + 1) : lines[i];
                changed |= !t.equals(lines[i]);
                lines[i] = t;
            }
            if (changed) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lines.length; i++) {
                    sb.append(i == 0 ? "" : "\n").append(lines[i]);
                }
                return sb.toString();
            }
        }
        return s;
    }

    private static String fill(String english, Matcher m, int depth) {
        Matcher h = HOLE.matcher(english);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (h.find()) {
            out.append(english, last, h.start());
            int g = Integer.parseInt(h.group(1)) + 1;
            String v = g <= m.groupCount() ? m.group(g) : "";
            out.append(depth < MAX_DEPTH ? translate(v, depth + 1) : v);
            last = h.end();
        }
        return out.append(english, last, english.length()).toString();
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = I18n.class.getResourceAsStream(CATALOG)) {
            if (in == null) {
                LiteModLiteBuild.LOG.warn("No se encontró el catálogo de inglés " + CATALOG);
                return;
            }
            parse(new BufferedReader(new InputStreamReader(in, "UTF-8")));
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudo leer el catálogo de inglés", e);
        }
    }

    /** Lee un catálogo: líneas "español TAB inglés"; # comenta; \n y \\ se escapan. Público para los tests. */
    static synchronized void parse(BufferedReader r) throws IOException {
        exact.clear();
        templates.clear();
        cache.clear();
        loaded = true;
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            String es = unescape(line.substring(0, tab)), en = unescape(line.substring(tab + 1));
            if (HOLE.matcher(es).find()) {
                templates.add(template(es, en));
            } else {
                exact.put(es, en);
            }
        }
        // Primero las plantillas con más texto fijo: son las más específicas.
        Collections.sort(templates, new Comparator<Template>() {
            @Override
            public int compare(Template a, Template b) {
                return b.literal - a.literal;
            }
        });
    }

    /** Los datos del catálogo ya leído: (claves exactas, plantillas). */
    static synchronized int[] counts() {
        ensureLoaded();
        return new int[] {exact.size(), templates.size()};
    }

    private static Template template(String es, String en) {
        StringBuilder re = new StringBuilder("^");
        Matcher h = HOLE.matcher(es);
        int last = 0, literal = 0;
        while (h.find()) {
            String lit = es.substring(last, h.start());
            literal += lit.length();
            re.append(Pattern.quote(lit)).append("(.+?)");
            last = h.end();
        }
        String tail = es.substring(last);
        literal += tail.length();
        re.append(Pattern.quote(tail)).append('$');
        return new Template(Pattern.compile(re.toString()), en, literal);
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
