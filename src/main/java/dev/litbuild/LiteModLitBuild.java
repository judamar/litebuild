package dev.litbuild;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import com.mumfrey.liteloader.LiteMod;
import com.mumfrey.liteloader.PostRenderListener;
import com.mumfrey.liteloader.ShutdownListener;
import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/** Punto de entrada del mod. Right Shift abre el editor (la pantalla con ImGui). */
public class LiteModLitBuild implements LiteMod, Tickable, ShutdownListener, PostRenderListener {
    public static final Logger LOG = LogManager.getLogger("LitBuild");

    private final KeyBinding openEditor = new KeyBinding("Abrir editor", Keyboard.KEY_RSHIFT, "LitBuild");
    private final KeyBinding noClipKey = new KeyBinding("Alternar No Clip", Keyboard.KEY_NONE, "LitBuild");

    @Override
    public String getName() {
        return "LitBuild";
    }

    @Override
    public String getVersion() {
        return "0.25.0";
    }

    @Override
    public void init(File configPath) {
        LiteLoader.getInput().registerKeyBinding(openEditor);
        migrateOldConfig();
        UiSettings.load();
        LiteLoader.getInput().registerKeyBinding(noClipKey);
        LOG.info("LitBuild cargado");
        // Diagnóstico: forzar la carga de la librería nativa de imgui-java ya en el arranque.
        try {
            Class.forName("imgui.ImGui");
            LOG.info("imgui-java: librería nativa cargada");
        } catch (Throwable t) {
            LOG.error("imgui-java: no se pudo cargar la librería nativa", t);
        }
    }

    /** El mod se llamaba AxiomLite: si existe su carpeta de config y la nueva no, se renombra (presets, atajos, paletas...). */
    private static void migrateOldConfig() {
        File base = Minecraft.getMinecraft().mcDataDir;
        File old = new File(base, "config/axiomlite");
        File fresh = new File(base, "config/litbuild");
        if (old.isDirectory() && !fresh.exists() && old.renameTo(fresh)) {
            LOG.info("Configuración migrada de config/axiomlite a config/litbuild");
        }
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath) {
    }

    @Override
    public void onTick(Minecraft mc, float partialTicks, boolean inGame, boolean clock) {
        WorldPanels.applyFlight();
        NoClip.tick(mc);
        FluidOpacity.tick(UiSettings.fluidOpacity);
        if (clock && inGame) {
            AnimatedRebuild.tick();
        }
        if (noClipKey.isPressed() && inGame) {
            NoClip.toggle();
        }
        if (openEditor.isPressed() && inGame && mc.currentScreen == null) {
            mc.displayGuiScreen(new EditorScreen());
        }
    }

    /** Con las matrices del mundo activas: guarda la cámara para apuntar y dibuja la selección. */
    @Override
    public void onPostRenderEntities(float partialTicks) {
        Targeting.capture(partialTicks);
        WorldOverlay.render();
    }

    @Override
    public void onPostRender(float partialTicks) {
    }

    @Override
    public void onShutDown() {
        ImGuiBackend.shutdown();
    }
}
