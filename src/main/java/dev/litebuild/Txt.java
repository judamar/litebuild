package dev.litebuild;

import imgui.ImGui;

/**
 * Texto de ImGui sin sorpresas: text(), textWrapped(), textDisabled(), textColored(), setTooltip(), labelText()
 * y bulletText() tratan la cadena como un formato de printf, así que un "%" suelto (un porcentaje, un mensaje de
 * error de Lua, un nombre de archivo) imprime basura de memoria. Estos envoltorios lo escapan; usarlos siempre.
 */
public final class Txt {
    private Txt() {
    }

    private static String esc(String s) {
        return s.replace("%", "%%");
    }

    public static void text(String s) {
        ImGui.text(esc(s));
    }

    public static void wrapped(String s) {
        ImGui.textWrapped(esc(s));
    }

    public static void disabled(String s) {
        ImGui.textDisabled(esc(s));
    }

    public static void colored(float r, float g, float b, float a, String s) {
        ImGui.textColored(r, g, b, a, esc(s));
    }

    public static void tooltip(String s) {
        ImGui.setTooltip(esc(s));
    }

    public static void bullet(String s) {
        ImGui.bulletText(esc(s));
    }

    public static void label(String label, String s) {
        ImGui.labelText(label, esc(s));
    }
}
