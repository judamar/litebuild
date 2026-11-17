package dev.litbuild;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

/** Texto del Builder Mode sobre el juego: herramienta activa, capabilities, teclas pulsadas y la lista de herramientas con Alt. */
public final class BuilderHud {
    private BuilderHud() {
    }

    public static void draw(Minecraft mc, int w, int h) {
        if (mc.player == null || mc.currentScreen != null || mc.gameSettings.hideGUI) {
            return;
        }
        ReferenceImage.drawHud(mc, w, h);
        FontRenderer font = mc.fontRenderer;
        // Herramienta activa, justo sobre la hotbar
        List<String> lines = BuilderTools.hud();
        int y = h - 64 - lines.size() * 11;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            font.drawStringWithShadow(s, (w - font.getStringWidth(s)) / 2f, y + i * 11, i == 0 ? 0x66FF88 : 0xAAAAAA);
        }
        // Capabilities activas, arriba a la izquierda
        String caps = BuilderMode.activeCapabilities();
        if (!caps.isEmpty()) {
            font.drawStringWithShadow("LitBuild: " + caps, 4, 4, 0x66FF88);
        }
        // Lista de herramientas mientras se mantiene Alt
        if (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU)) {
            int x = w / 2 - 50, top = h / 2 - BuilderTools.NAMES.length * 6;
            Gui.drawRect(x - 6, top - 6, x + 110, top + BuilderTools.NAMES.length * 12 + 4, 0xAA000000);
            for (int i = 0; i < BuilderTools.NAMES.length; i++) {
                boolean on = i == BuilderTools.tool;
                font.drawStringWithShadow((on ? "> " : "  ") + BuilderTools.NAMES[i], x, top + i * 12, on ? 0x66FF88 : 0xCCCCCC);
            }
        }
        // Show Key Presses, abajo a la derecha
        if (BuilderMode.showKeys.get()) {
            List<String> keys = BuilderMode.pressedKeys();
            for (int i = 0; i < keys.size(); i++) {
                String s = keys.get(i);
                font.drawStringWithShadow(s, w - font.getStringWidth(s) - 6, h - 16 - i * 11, 0xFFFFFF);
            }
        }
    }
}
