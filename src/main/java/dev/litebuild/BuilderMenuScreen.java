package dev.litebuild;

import java.io.IOException;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameType;

/**
 * Menú del Builder Mode (el Context Menu de Axiom): herramientas del Builder, capabilities, Toolbox, Edit Block Attributes,
 * cambio de modo de juego, velocidad de vuelo, Hotbar Swapper, Colour Picker y Gradient Helper. Se abre con la tecla
 * "Menú de Builder" (B por defecto) o tocando Alt, y se cierra con la misma, con Escape o con Alt.
 */
public class BuilderMenuScreen extends GuiScreen {
    private final ImBoolean showPicker = new ImBoolean(false);
    private final ImBoolean showGradient = new ImBoolean(false);
    private final ImString hotbarName = new ImString("", 32);

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        Hotbars.ensureLoaded();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ImGuiBackend.frame(new Runnable() {
            @Override
            public void run() {
                ui();
            }
        });
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        int key = Keyboard.getEventKey();
        boolean down = Keyboard.getEventKeyState();
        ImGuiBackend.key(key, down, Keyboard.getEventCharacter());
        if (down && (key == Keyboard.KEY_ESCAPE || key == LiteModLiteBuild.menuKeyCode || key == Keyboard.KEY_LMENU)
                && !ImGui.getIO().getWantTextInput()) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        ImGuiBackend.mouse(Mouse.getEventX(), Mouse.getEventY(), Mouse.getEventButton(), Mouse.getEventButtonState(),
                Mouse.getEventDWheel());
    }

    private static void tip(String text) {
        if (ImGui.isItemHovered()) {
            Txt.tooltip(text);
        }
    }

    private void ui() {
        ImGui.setNextWindowPos(30, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(400, 640, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Builder")) {
            toolsSection();
            capabilitiesSection();
            toolboxSection();
            gameSection();
            hotbarSection();
            ImGui.separatorText("Color");
            float w = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2f;
            if (ImGui.button("Colour Picker", w, 0)) {
                showPicker.set(true);
            }
            ImGui.sameLine();
            if (ImGui.button("Gradient Helper", w, 0)) {
                showGradient.set(true);
            }
        }
        ImGui.end();
        if (showPicker.get()) {
            ColourTools.pickerWindow(showPicker, BuilderMode.heldState(), new ColourTools.Sink() {
                @Override
                public void accept(IBlockState state) {
                    Hotbars.giveHeld(state);
                }
            }, "lo pone en la mano (ranura actual de la hotbar)");
        }
        if (showGradient.get()) {
            ColourTools.gradientWindow(showGradient, new ColourTools.ListSink() {
                @Override
                public void accept(java.util.List<IBlockState> states) {
                    Hotbars.fill(states);
                }
            }, null);
        }
    }

    private void toolsSection() {
        ImGui.separatorText("Builder Tools");
        float spacing = ImGui.getStyle().getItemSpacingX();
        int perRow = 4;
        float w = (ImGui.getContentRegionAvailX() - spacing * (perRow - 1)) / perRow;
        for (int i = 0; i < BuilderTools.NAMES.length; i++) {
            if (i % perRow != 0) {
                ImGui.sameLine();
            }
            boolean on = BuilderTools.tool == i;
            if (on) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(ImGuiCol.ButtonActive));
            }
            if (ImGui.button(BuilderTools.NAMES[i] + "##bt", w, 0)) {
                BuilderTools.select(i);
            }
            if (on) {
                ImGui.popStyleColor();
            }
        }
        ImGui.checkbox("Keep Existing", BuilderTools.keepExisting);
        ImGui.sameLine();
        ImGui.checkbox("Copy Air", BuilderTools.copyAir);
        Txt.disabled("Alt + rueda también cambia de herramienta. Izquierdo/derecho: esquinas; rueda: empujar; central: llevar al bloque; derecho: confirmar.");
    }

    private void capabilitiesSection() {
        ImGui.separatorText("Capabilities");
        for (int i = 0; i < BuilderMode.CAPABILITIES.length; i++) {
            ImBoolean v = new ImBoolean(BuilderMode.isOn(i));
            if (v.get()) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.45f, 0.95f, 0.55f, 1f);
            }
            if (ImGui.checkbox(BuilderMode.CAPABILITIES[i], v)) {
                BuilderMode.toggle(i);
            }
            if (v.get()) {
                ImGui.popStyleColor();
            }
            tip(describe(i));
            if (i % 2 == 0 && i + 1 < BuilderMode.CAPABILITIES.length) {
                ImGui.sameLine(ImGui.getWindowWidth() * 0.5f);
            }
        }
    }

    private static String describe(int i) {
        switch (i) {
            case 0:
                return "Clic derecho con la mano vacía cambia el estado del bloque (Ctrl: bloque, escalera y losa del mismo material).";
            case 1:
                return "Coloca y rompe sin avisar a los bloques vecinos.";
            case 2:
                return "Coloca saltándose las restricciones (soporte, entidades).";
            case 3:
                return "Clic derecho sustituye el bloque apuntado por el de la mano, heredando sus propiedades.";
            case 4:
                return "Mantén el botón izquierdo: tras un instante rompe bloques muy rápido.";
            case 5:
                return "Apunta, rompe y coloca a cualquier distancia (hasta el límite de la Toolbox).";
            case 6:
                return "Sin pausa entre colocaciones.";
            case 7:
                return "Coloca bloques en el aire, a la distancia de la Toolbox, sin bloque de apoyo.";
            default:
                return "Atraviesas bloques volando.";
        }
    }

    private void toolboxSection() {
        ImGui.separatorText("Toolbox");
        ImGui.sliderFloat("Infinite reach limit", BuilderMode.reachLimit, 5f, 100f, "%.0f");
        ImGui.sliderFloat("Angel placement distance", BuilderMode.angelDistance, 2f, 20f, "%.0f");
        ImGui.checkbox("Type replace", BuilderMode.typeReplace);
        tip("Replace Mode con un bloque de otro material: las escaleras, losas, muros y vallas pasan a ser de ese material.");
        ImGui.combo("Flight direction", BuilderMode.flightDirection, BuilderMode.FLIGHT);
        int[] speed = {WorldPanels.flight[0]};
        if (ImGui.sliderInt("Flight speed %", speed, 100, 999)) {
            WorldPanels.flight[0] = speed[0];
        }
        float[] fo = {UiSettings.fluidOpacity < 0 ? 0.7f : UiSettings.fluidOpacity};
        if (ImGui.sliderFloat("Liquid opacity", fo, 0f, 1f, "%.2f")) {
            UiSettings.fluidOpacity = fo[0];
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            UiSettings.save();
        }
        float[] mb = {UiSettings.minBrightness};
        if (ImGui.sliderFloat("Minimum brightness", mb, 0f, 1f, "%.2f")) {
            UiSettings.minBrightness = mb[0];
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            UiSettings.save();
        }
        ImGui.checkbox("Show key hints", BuilderMode.showKeys);
        ImInt language = new ImInt(UiSettings.language);
        if (ImGui.combo("Language", language, I18n.LANGUAGES)) {
            I18n.set(language.get());
            UiSettings.language = language.get();
            UiSettings.save();
        }
        ImGui.separatorText("Edit Block Attributes");
        ImGui.checkbox("Show structure void blocks", BuilderMode.showStructureVoid);
        ImGui.checkbox("Show collision mesh", BuilderMode.showCollision);
        tip("Contorno rojo con la caja de colisión real de barreras, vallas, puertas de valla, muros, paneles de cristal y barras de hierro cercanos.");
        ImGui.checkbox("Make fluid hitboxes solid", BuilderMode.fluidsSolid);
        tip("Apuntas al agua y a la lava como a cualquier bloque: se pueden romper con el puño y colocar bloques contra su superficie.");
        ImGui.checkbox("Expand hitboxes to full cube", BuilderMode.expandHitboxes);
        tip("Todos los bloques se apuntan como cubos completos (losas, botones, flores, antorchas...); el contorno blanco muestra el cubo. Con Make fluid hitboxes solid también cuentan los fluidos.");
        ImGui.checkbox("Prevent interactions", BuilderMode.preventInteractions);
        tip("Con un bloque en la mano, el clic derecho sobre un cofre, puerta o similar coloca el bloque en vez de abrirlo.");
    }

    private void gameSection() {
        ImGui.separatorText("Gamemode");
        String[] names = {"Creative", "Survival", "Adventure", "Spectator"};
        GameType[] types = {GameType.CREATIVE, GameType.SURVIVAL, GameType.ADVENTURE, GameType.SPECTATOR};
        float spacing = ImGui.getStyle().getItemSpacingX();
        float w = (ImGui.getContentRegionAvailX() - spacing * 3) / 4f;
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            if (ImGui.button(names[i], w, 0)) {
                setGameType(types[i]);
            }
        }
    }

    private void setGameType(final GameType type) {
        final java.util.UUID id = mc.player.getUniqueID();
        EditEngine.runOnServer(new EditEngine.ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                net.minecraft.entity.player.EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (p != null) {
                    p.setGameType(type);
                }
            }
        });
    }

    private void hotbarSection() {
        ImGui.separatorText("Hotbar Swapper");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 130f);
        ImGui.inputText("##hbname", hotbarName);
        ImGui.sameLine();
        if (ImGui.button("Save hotbar")) {
            Hotbars.saveCurrent(hotbarName.get());
            hotbarName.set("");
        }
        int remove = -1;
        float cell = ImGui.getFrameHeight() * 1.2f;
        for (int i = 0; i < Hotbars.list.size(); i++) {
            Hotbars.Saved s = Hotbars.list.get(i);
            ImGui.pushID("hb" + i);
            ImDrawList dl = ImGui.getWindowDrawList();
            float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
            ImGui.dummy(cell * 9, cell);
            for (int k = 0; k < 9; k++) {
                ItemStack st = s.items[k];
                if (!st.isEmpty()) {
                    Icons.draw(dl, st, x + cell * (k + 0.5f), y + cell / 2, cell - 2f);
                }
            }
            if (ImGui.button("Load")) {
                Hotbars.load(s);
            }
            ImGui.sameLine();
            if (ImGui.button("X")) {
                remove = i;
            }
            ImGui.sameLine();
            Txt.text(s.name);
            ImGui.popID();
        }
        if (remove >= 0) {
            Hotbars.delete(remove);
        }
        if (Hotbars.list.isEmpty()) {
            Txt.disabled("Sin hotbars guardadas. Rellena la hotbar y guárdala aquí; valen para todos los mundos.");
        }
    }
}
