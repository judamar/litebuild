package dev.litebuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import imgui.ImGui;

/**
 * Resaltado de sintaxis de Lua para la vista previa del editor de scripts: parte cada línea en trozos (palabra clave, texto,
 * comentario, número, función o resto) y los dibuja de colores. El campo de texto de ImGui no admite colores, así que el
 * código se ve resaltado en una vista de solo lectura debajo del campo.
 */
public final class LuaHighlight {
    public static final int PLAIN = 0, KEYWORD = 1, STRING = 2, COMMENT = 3, NUMBER = 4, FUNCTION = 5;
    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList("and", "break", "do", "else", "elseif", "end",
            "false", "for", "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true",
            "until", "while"));
    /** Colores ABGR de cada tipo. */
    private static final int[] COLORS = {0xFFDDDDDD, 0xFFFF9D5C, 0xFF7DC4F0, 0xFF8E9A8E, 0xFFA5E085, 0xFF7FE0F5};

    private LuaHighlight() {
    }

    /** Un trozo de línea: texto y tipo. */
    public static final class Token {
        public final String text;
        public final int kind;

        Token(String text, int kind) {
            this.text = text;
            this.kind = kind;
        }
    }

    /** Parte una línea de Lua en trozos que, unidos, dan la línea original (con los tabuladores como 4 espacios). */
    public static List<Token> tokens(String line) {
        line = line.replace("\t", "    ");
        List<Token> out = new ArrayList<Token>();
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (c == '-' && i + 1 < n && line.charAt(i + 1) == '-') {
                out.add(new Token(line.substring(i), COMMENT));
                break;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && line.charAt(j) != c) {
                    j += line.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(n, j + 1);
                out.add(new Token(line.substring(i, j), STRING));
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '.')) {
                    j++;
                }
                out.add(new Token(line.substring(i, j), NUMBER));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) {
                    j++;
                }
                String word = line.substring(i, j);
                int k = j;
                while (k < n && line.charAt(k) == ' ') {
                    k++;
                }
                int kind = KEYWORDS.contains(word) ? KEYWORD : k < n && line.charAt(k) == '(' ? FUNCTION : PLAIN;
                out.add(new Token(word, kind));
                i = j;
            } else {
                int j = i + 1;
                while (j < n && !Character.isLetterOrDigit(line.charAt(j)) && line.charAt(j) != '_' && line.charAt(j) != '"'
                        && line.charAt(j) != '\'' && !(line.charAt(j) == '-' && j + 1 < n && line.charAt(j + 1) == '-')) {
                    j++;
                }
                out.add(new Token(line.substring(i, j), PLAIN));
                i = j;
            }
        }
        return out;
    }

    /** Dibuja code resaltado en una región con scroll de la altura dada (solo lectura). */
    public static void draw(String id, String code, float height) {
        if (ImGui.beginChild(id, 0, height, true)) {
            for (String line : code.split("\n", -1)) {
                List<Token> toks = tokens(line);
                if (toks.isEmpty()) {
                    ImGui.dummy(1f, ImGui.getTextLineHeight());
                    continue;
                }
                for (int t = 0; t < toks.size(); t++) {
                    if (t > 0) {
                        ImGui.sameLine(0f, 0f);
                    }
                    Token k = toks.get(t);
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, COLORS[k.kind]);
                    Txt.text(k.text);
                    ImGui.popStyleColor();
                }
            }
        }
        ImGui.endChild();
    }
}
