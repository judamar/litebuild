package dev.litbuild;

import java.util.Locale;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImBoolean;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/** Ventanas informativas del editor: Target Info, World Properties y el resultado de Analyze. */
public final class WorldPanels {
    /** Velocidad de vuelo en % de la normal. */
    public static final int[] flight = {100};
    private static int appliedFlight = 100;

    private WorldPanels() {
    }

    /** Aplica la velocidad de vuelo elegida; llamar cada tick (el servidor puede restablecerla). */
    public static void applyFlight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && (flight[0] != 100 || appliedFlight != 100)) {
            mc.player.capabilities.setFlySpeed(0.05f * flight[0] / 100f);
            appliedFlight = flight[0];
        }
    }

    public static void targetInfo() {
        ImGui.setNextWindowPos(380, 240, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(330, 0, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Target Info")) {
            BlockPos h = Targeting.hover;
            Minecraft mc = Minecraft.getMinecraft();
            if (h == null || mc.world == null) {
                Txt.text("Sin bloque bajo el cursor");
            } else {
                IBlockState s = mc.world.getBlockState(h);
                Block b = s.getBlock();
                Txt.text(String.valueOf(Block.REGISTRY.getNameForObject(b)));
                Txt.wrapped(s.toString());
                Txt.text(h.getX() + ", " + h.getY() + ", " + h.getZ());
                double d = Math.sqrt(h.distanceSq(Targeting.camX, Targeting.camY, Targeting.camZ));
                Txt.text(String.format(Locale.ROOT, "Distancia: %.1f", d));
                Txt.text("Bioma: " + mc.world.getBiome(h).getBiomeName());
            }
        }
        ImGui.end();
    }

    public static void worldProperties() {
        ImGui.setNextWindowPos(380, 400, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(330, 0, ImGuiCond.FirstUseEver);
        if (ImGui.begin("World Properties")) {
            IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
            if (server == null) {
                Txt.wrapped("Solo disponible en mundos de un jugador.");
            } else {
                World w = server.getWorld(0);
                GameRules rules = w.getGameRules();
                int[] time = {(int) (w.getWorldTime() % 24000L)};
                if (ImGui.sliderInt("Time", time, 0, 23999)) {
                    setTime(time[0]);
                }
                if (ImGui.button("Day")) {
                    setTime(1000);
                }
                ImGui.sameLine();
                if (ImGui.button("Noon")) {
                    setTime(6000);
                }
                ImGui.sameLine();
                if (ImGui.button("Sunset")) {
                    setTime(12000);
                }
                ImGui.sameLine();
                if (ImGui.button("Night")) {
                    setTime(13000);
                }
                ImGui.sameLine();
                if (ImGui.button("Midnight")) {
                    setTime(18000);
                }
                ImGui.separator();
                ruleCheckbox(rules, "Daylight cycle", "doDaylightCycle");
                ruleCheckbox(rules, "Mob spawning", "doMobSpawning");
                ruleCheckbox(rules, "Mob griefing", "mobGriefing");
                ruleCheckbox(rules, "Fire tick", "doFireTick");
                ruleCheckbox(rules, "Weather cycle", "doWeatherCycle");
                int[] rts = {rules.getInt("randomTickSpeed")};
                if (ImGui.sliderInt("Random tick speed", rts, 0, 100)) {
                    setRule("randomTickSpeed", Integer.toString(rts[0]));
                }
                ImGui.separatorText("Player");
                ImGui.sliderInt("Flight speed %", flight, 100, 999);
                ImBoolean inv = new ImBoolean(Minecraft.getMinecraft().player.capabilities.disableDamage);
                if (ImGui.checkbox("Invulnerable", inv)) {
                    setInvulnerable(inv.get());
                }
                ImBoolean grav = new ImBoolean(Rules.blockGravity);
                if (ImGui.checkbox("Block gravity", grav)) {
                    Rules.blockGravity = grav.get();
                }
                ImGui.sameLine();
                ImBoolean trample = new ImBoolean(Rules.trampleFarmland);
                if (ImGui.checkbox("Trample farmland", trample)) {
                    Rules.trampleFarmland = trample.get();
                }
                ruleCheckbox(rules, "Block drops", "doTileDrops");
                ImBoolean noClip = new ImBoolean(NoClip.enabled);
                if (ImGui.checkbox("No Clip", noClip)) {
                    NoClip.toggle();
                }
                ImGui.sameLine();
                Txt.disabled("(creativo: atraviesas bloques volando)");
                if (NoClip.enabled && NoClip.hits == 0) {
                    Txt.wrapped("Aún no ha actuado: muévete. Si sigue sin atravesar bloques, los mixins no se aplicaron (mira logs/latest.log).");
                }
                String[] modes = {"Creative", "Survival", "Spectator"};
                String[] cmds = {"creative", "survival", "spectator"};
                float spacing = ImGui.getStyle().getItemSpacingX();
                float bw = (ImGui.getContentRegionAvailX() - spacing * 2) / 3f;
                for (int i = 0; i < modes.length; i++) {
                    if (i > 0) {
                        ImGui.sameLine();
                    }
                    if (ImGui.button(modes[i], bw, 0)) {
                        Minecraft.getMinecraft().player.sendChatMessage("/gamemode " + cmds[i]);
                    }
                }
            }
        }
        ImGui.end();
    }

    /** Muestra el resultado de Analyze; open pasa a false al cerrar la ventana. */
    public static void analysis(ImBoolean open) {
        String[] lines = EditEngine.analysis;
        if (lines == null) {
            return;
        }
        ImGui.setNextWindowPos(720, 40, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(420, 0, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Analyze", open)) {
            for (String l : lines) {
                Txt.text(l);
            }
        }
        ImGui.end();
    }

    private static void ruleCheckbox(GameRules rules, String label, String rule) {
        ImBoolean v = new ImBoolean(rules.getBoolean(rule));
        if (ImGui.checkbox(label, v)) {
            setRule(rule, Boolean.toString(v.get()));
        }
    }

    private static void setInvulnerable(final boolean on) {
        final java.util.UUID id = Minecraft.getMinecraft().player.getUniqueID();
        EditEngine.runOnServer(new EditEngine.ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                net.minecraft.entity.player.EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (p != null) {
                    p.capabilities.disableDamage = on;
                    p.sendPlayerAbilities();
                }
            }
        });
    }

    private static void setRule(final String rule, final String value) {
        EditEngine.runOnServer(new EditEngine.ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                server.getWorld(0).getGameRules().setOrCreateGameRule(rule, value);
            }
        });
    }

    private static void setTime(final int ticksOfDay) {
        EditEngine.runOnServer(new EditEngine.ServerTask() {
            @Override
            public void run(IntegratedServer server) {
                World w = server.getWorld(0);
                long now = w.getWorldTime();
                w.setWorldTime(now - now % 24000L + ticksOfDay);
            }
        });
    }
}
