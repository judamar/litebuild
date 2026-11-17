package dev.litbuild;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

/** Temas visuales de la interfaz. */
public final class Theme {
    public static final String[] NAMES = {"LitBuild Dark", "Light", "Classic", "ImGui Dark"};

    private Theme() {
    }

    public static void apply(int theme) {
        switch (theme) {
            case 1:
                ImGui.styleColorsLight();
                sizes(true);
                break;
            case 2:
                ImGui.styleColorsClassic();
                sizes(false);
                break;
            case 3:
                ImGui.styleColorsDark();
                sizes(false);
                break;
            default:
                litbuildDark();
                sizes(true);
                break;
        }
    }

    /** Esquinas redondeadas y más aire entre elementos (rounded) o los tamaños clásicos. */
    private static void sizes(boolean rounded) {
        ImGuiStyle s = ImGui.getStyle();
        s.setWindowPadding(rounded ? 10f : 8f, rounded ? 8f : 8f);
        s.setFramePadding(rounded ? 8f : 4f, rounded ? 4f : 3f);
        s.setItemSpacing(rounded ? 8f : 8f, rounded ? 6f : 4f);
        s.setIndentSpacing(rounded ? 16f : 21f);
        s.setScrollbarSize(rounded ? 12f : 14f);
        s.setGrabMinSize(rounded ? 12f : 10f);
        s.setWindowBorderSize(1f);
        s.setFrameBorderSize(0f);
        float r = rounded ? 6f : 0f;
        s.setWindowRounding(r);
        s.setChildRounding(rounded ? 4f : 0f);
        s.setPopupRounding(r);
        s.setFrameRounding(rounded ? 4f : 0f);
        s.setScrollbarRounding(rounded ? 6f : 9f);
        s.setGrabRounding(rounded ? 4f : 0f);
        s.setTabRounding(rounded ? 4f : 4f);
    }

    /** Tema por defecto: fondos oscuros con un matiz verdoso y verde como color primario. */
    private static void litbuildDark() {
        ImGui.styleColorsDark();
        ImGuiStyle s = ImGui.getStyle();
        s.setColor(ImGuiCol.Text, 0.92f, 0.95f, 0.93f, 1f);
        s.setColor(ImGuiCol.TextDisabled, 0.52f, 0.60f, 0.56f, 1f);
        s.setColor(ImGuiCol.WindowBg, 0.09f, 0.12f, 0.11f, 0.96f);
        s.setColor(ImGuiCol.ChildBg, 0f, 0f, 0f, 0f);
        s.setColor(ImGuiCol.PopupBg, 0.11f, 0.15f, 0.13f, 0.98f);
        s.setColor(ImGuiCol.Border, 0.20f, 0.28f, 0.24f, 1f);
        s.setColor(ImGuiCol.FrameBg, 0.14f, 0.19f, 0.17f, 1f);
        s.setColor(ImGuiCol.FrameBgHovered, 0.19f, 0.27f, 0.23f, 1f);
        s.setColor(ImGuiCol.FrameBgActive, 0.23f, 0.34f, 0.28f, 1f);
        s.setColor(ImGuiCol.TitleBg, 0.07f, 0.10f, 0.09f, 1f);
        s.setColor(ImGuiCol.TitleBgActive, 0.11f, 0.17f, 0.14f, 1f);
        s.setColor(ImGuiCol.MenuBarBg, 0.07f, 0.10f, 0.09f, 1f);
        s.setColor(ImGuiCol.ScrollbarBg, 0.07f, 0.10f, 0.09f, 0.6f);
        s.setColor(ImGuiCol.ScrollbarGrab, 0.26f, 0.36f, 0.31f, 1f);
        s.setColor(ImGuiCol.CheckMark, 0.40f, 0.92f, 0.55f, 1f);
        s.setColor(ImGuiCol.SliderGrab, 0.28f, 0.78f, 0.45f, 1f);
        s.setColor(ImGuiCol.SliderGrabActive, 0.40f, 0.92f, 0.58f, 1f);
        s.setColor(ImGuiCol.Button, 0.15f, 0.42f, 0.27f, 1f);
        s.setColor(ImGuiCol.ButtonHovered, 0.20f, 0.55f, 0.35f, 1f);
        s.setColor(ImGuiCol.ButtonActive, 0.24f, 0.66f, 0.41f, 1f);
        s.setColor(ImGuiCol.Header, 0.15f, 0.42f, 0.27f, 0.65f);
        s.setColor(ImGuiCol.HeaderHovered, 0.20f, 0.55f, 0.35f, 0.85f);
        s.setColor(ImGuiCol.HeaderActive, 0.24f, 0.66f, 0.41f, 1f);
        s.setColor(ImGuiCol.Separator, 0.20f, 0.28f, 0.24f, 1f);
        s.setColor(ImGuiCol.ResizeGrip, 0.28f, 0.78f, 0.45f, 0.25f);
        s.setColor(ImGuiCol.Tab, 0.11f, 0.17f, 0.14f, 1f);
        s.setColor(ImGuiCol.TabHovered, 0.20f, 0.55f, 0.35f, 1f);
        s.setColor(ImGuiCol.TabActive, 0.15f, 0.42f, 0.27f, 1f);
        s.setColor(ImGuiCol.DockingPreview, 0.28f, 0.78f, 0.45f, 0.70f);
        s.setColor(ImGuiCol.NavHighlight, 0.40f, 0.92f, 0.55f, 1f);
    }
}
