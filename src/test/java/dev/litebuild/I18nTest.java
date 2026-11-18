package dev.litebuild;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class I18nTest {
    private static final Pattern HOLE = Pattern.compile("\\{\\d+\\}");

    private static String[][] catalog() throws Exception {
        java.util.List<String[]> rows = new java.util.ArrayList<String[]>();
        BufferedReader r = new BufferedReader(new InputStreamReader(I18nTest.class.getResourceAsStream("/lang/en.tsv"), "UTF-8"));
        for (String line; (line = r.readLine()) != null;) {
            if (!line.isEmpty() && line.charAt(0) != '#') {
                rows.add(line.split("\t", -1));
            }
        }
        return rows.toArray(new String[0][]);
    }

    private static Set<String> holes(String s) {
        Set<String> out = new HashSet<String>();
        Matcher m = HOLE.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    @Test
    public void everyCatalogLineHasTwoNonEmptyColumnsAndTheSameHolesInBoth() throws Exception {
        String[][] rows = catalog();
        assertTrue("catálogo demasiado corto", rows.length > 200);
        Set<String> keys = new HashSet<String>();
        for (String[] row : rows) {
            assertEquals("columnas en: " + row[0], 2, row.length);
            assertTrue(!row[0].isEmpty() && !row[1].isEmpty());
            assertEquals("huecos en: " + row[0], holes(row[0]), holes(row[1]));
            assertTrue("clave repetida: " + row[0], keys.add(row[0]));
        }
    }

    @Test
    public void translatedTextIsNotTranslatedAgain() throws Exception {
        for (String[] row : catalog()) {
            String en = row[1].replace("\\n", "\n");
            String filled = HOLE.matcher(en).replaceAll("7");
            assertEquals("no es estable: " + en, filled, I18n.translate(filled));
        }
    }

    @Test
    public void translatesExactTextsAndTemplates() {
        assertEquals("No selection", I18n.translate("No hay selección"));
        assertEquals("Copied: 3 x 4 x 5 (60 blocks)", I18n.translate("Copiado: 3 x 4 x 5 (60 bloques)"));
        assertEquals("Copied: 3 x 4 x 5 (60 blocks, 2 entities)", I18n.translate("Copiado: 3 x 4 x 5 (60 bloques, 2 entidades)"));
        assertEquals("Modelling: 12 blocks (3 x 4 x 5)", I18n.translate("Modelling: 12 bloques (3 x 4 x 5)"));
        assertEquals("Slope: height difference 4, angle 12.5°, distance 30.1",
                I18n.translate("Slope: diferencia de altura 4, ángulo 12.5°, distancia 30.1"));
        assertEquals("Floodfill (limit reached)", I18n.translate("Floodfill (límite alcanzado)"));
    }

    @Test
    public void translatesTheFillsOfATemplate() {
        assertEquals("Script: the script took too long (more than 200000 instructions in one stamp)",
                I18n.translate("Script: el script ha tardado demasiado (más de 200000 instrucciones en un sello)"));
        assertEquals("Tool Masks (Lua), rule 2: the script placed too many blocks in one stamp (maximum 5000)",
                I18n.translate("Tool Masks (Lua), regla 2: el script ha colocado demasiados bloques en un sello (máximo 5000)"));
        assertEquals("Could not import: Invalid file: x.nbt", I18n.translate("No se pudo importar: Archivo no válido: x.nbt"));
    }

    @Test
    public void translatesMultiLineTextLineByLine() {
        assertEquals("Mi blueprint\nby judamar\n3 x 4 x 5 (60 blocks)\ntag",
                I18n.translate("Mi blueprint\npor judamar\n3 x 4 x 5 (60 bloques)\ntag"));
    }

    @Test
    public void unknownTextPassesThroughUntouched() {
        assertEquals("Weld", I18n.translate("Weld"));
        assertEquals("Una frase que no está en el catálogo", I18n.translate("Una frase que no está en el catálogo"));
        assertEquals("100 %", I18n.translate("100 %"));
    }

    @Test
    public void aCustomCatalogCanBeParsed() throws Exception {
        I18n.parse(new BufferedReader(new StringReader("# c\nhola\thello\n{0} cosas\t{0} things\nlinea\\ndos\tline\\ntwo\n")));
        try {
            assertEquals("hello", I18n.translate("hola"));
            assertEquals("5 things", I18n.translate("5 cosas"));
            assertEquals("line\ntwo", I18n.translate("linea\ndos"));
        } finally {
            I18n.parse(new BufferedReader(new InputStreamReader(I18nTest.class.getResourceAsStream("/lang/en.tsv"), "UTF-8")));
        }
    }
}
