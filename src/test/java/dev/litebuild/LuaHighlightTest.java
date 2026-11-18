package dev.litebuild;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class LuaHighlightTest {
    private static String join(String line) {
        StringBuilder sb = new StringBuilder();
        for (LuaHighlight.Token t : LuaHighlight.tokens(line)) {
            sb.append(t.text);
        }
        return sb.toString();
    }

    private static int kindOf(String line, String text) {
        for (LuaHighlight.Token t : LuaHighlight.tokens(line)) {
            if (t.text.equals(text)) {
                return t.kind;
            }
        }
        throw new AssertionError(text + " no es un token de " + line);
    }

    @Test
    public void tokensRebuildTheOriginalLine() {
        String[] lines = {"return isSolid(x, y, z) and y > 64", "local s = \"a \\\" b\" -- comentario", "x = 0x1F + 3.5e2",
            "if getBlock(x,y,z) == 'stone' then setBlock(x,y+1,z, 'air') end", "-- solo comentario", "", "  a.b:c(1)  "};
        for (String l : lines) {
            assertEquals(l, join(l));
        }
    }

    @Test
    public void tabsBecomeFourSpaces() {
        assertEquals("    return 1", join("\treturn 1"));
    }

    @Test
    public void classifiesKeywordsStringsCommentsNumbersAndCalls() {
        String line = "local n = 12 -- fin";
        assertEquals(LuaHighlight.KEYWORD, kindOf(line, "local"));
        assertEquals(LuaHighlight.NUMBER, kindOf(line, "12"));
        assertEquals(LuaHighlight.COMMENT, kindOf(line, "-- fin"));
        assertEquals(LuaHighlight.STRING, kindOf("x = 'hola'", "'hola'"));
        assertEquals(LuaHighlight.FUNCTION, kindOf("isSolid (x)", "isSolid"));
        assertEquals(LuaHighlight.PLAIN, kindOf("isSolid", "isSolid"));
    }

    @Test
    public void anUnterminatedStringDoesNotLoseText() {
        List<LuaHighlight.Token> t = LuaHighlight.tokens("x = \"abierto");
        assertEquals(LuaHighlight.STRING, t.get(t.size() - 1).kind);
        assertEquals("x = \"abierto", join("x = \"abierto"));
    }
}
