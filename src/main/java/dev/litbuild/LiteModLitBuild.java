package dev.litbuild;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import com.mumfrey.liteloader.HUDRenderListener;
import com.mumfrey.liteloader.LiteMod;
import com.mumfrey.liteloader.PostRenderListener;
import com.mumfrey.liteloader.ShutdownListener;
import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/** Punto de entrada del mod. Right Shift abre el editor (la pantalla con ImGui). */
public class LiteModLitBuild implements LiteMod, Tickable, ShutdownListener, PostRenderListener, HUDRenderListener {
    public static final Logger LOG = LogManager.getLogger("LitBuild");

    private final KeyBinding openEditor = new KeyBinding("Abrir editor", Keyboard.KEY_RSHIFT, "LitBuild");
    private final KeyBinding noClipKey = new KeyBinding("Alternar No Clip", Keyboard.KEY_NONE, "LitBuild");
    private final KeyBinding builderMenu = new KeyBinding("Menú de Builder", Keyboard.KEY_B, "LitBuild");
    /** Una tecla por capability (la de Replace Mode es R, como en Axiom); No Clip tiene la suya aparte. */
    private final KeyBinding[] capabilityKeys = new KeyBinding[8];
    /** El código de la tecla del menú de Builder (para que la propia pantalla se cierre con ella). */
    public static volatile int menuKeyCode = Keyboard.KEY_B;

    @Override
    public String getName() {
        return "LitBuild";
    }

    @Override
    public String getVersion() {
        return "0.27.0";
    }

    @Override
    public void init(File configPath) {
        LiteLoader.getInput().registerKeyBinding(openEditor);
        migrateOldConfig();
        UiSettings.load();
        LiteLoader.getInput().registerKeyBinding(noClipKey);
        LiteLoader.getInput().registerKeyBinding(builderMenu);
        for (int i = 0; i < capabilityKeys.length; i++) {
            capabilityKeys[i] = new KeyBinding("Capability: " + BuilderMode.CAPABILITIES[i],
                    i == 3 ? Keyboard.KEY_R : Keyboard.KEY_NONE, "LitBuild");
            LiteLoader.getInput().registerKeyBinding(capabilityKeys[i]);
        }
        Brightness.restoreAtStartup();
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
        Brightness.tick(mc);
        menuKeyCode = builderMenu.getKeyCode();
        if (clock && inGame) {
            AnimatedRebuild.tick();
            BuilderMode.tick(mc);
        }
        if (inGame && mc.currentScreen == null) {
            for (int i = 0; i < capabilityKeys.length; i++) {
                if (capabilityKeys[i].isPressed()) {
                    BuilderMode.toggle(i);
                }
            }
            if (builderMenu.isPressed()) {
                mc.displayGuiScreen(new BuilderMenuScreen());
            }
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
    public void onPreRenderHUD(int screenWidth, int screenHeight) {
    }

    @Override
    public void onPostRenderHUD(int screenWidth, int screenHeight) {
        BuilderHud.draw(Minecraft.getMinecraft(), screenWidth, screenHeight);
    }

    @Override
    public void onShutDown() {
        ImGuiBackend.shutdown();
    }
}
