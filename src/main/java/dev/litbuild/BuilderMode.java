package dev.litbuild;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Builder Mode: lo que se hace en el mundo sin abrir el editor. Las capabilities (Tinker, No Updates, Force Place,
 * Replace Mode, Bulldozer, Infinite Reach, Fast Place, Angel Placement y No Clip) cambian cómo se rompe y se coloca;
 * las herramientas (Move, Clone...) están en {@link BuilderTools}. Los mixins de Minecraft, InventoryPlayer y
 * PlayerControllerMP llaman a {@link #click}, {@link #wheel}, {@link #suppressHold} y {@link #reach}.
 * Todo actúa sobre el mundo del servidor integrado; lo de aquí no entra en el historial del editor (como en Axiom, Replace
 * Mode no se deshace).
 */
public final class BuilderMode {
    public static final ImBoolean tinker = new ImBoolean(false);
    public static final ImBoolean noUpdates = new ImBoolean(false);
    public static final ImBoolean forcePlace = new ImBoolean(false);
    public static final ImBoolean replaceMode = new ImBoolean(false);
    public static final ImBoolean bulldozer = new ImBoolean(false);
    public static final ImBoolean infiniteReach = new ImBoolean(false);
    public static final ImBoolean fastPlace = new ImBoolean(false);
    public static final ImBoolean angelPlacement = new ImBoolean(false);
    /** Nombres de las capabilities en el orden de {@link #flag(int)}; la última (No Clip) vive en {@link NoClip}. */
    public static final String[] CAPABILITIES = {"Tinker", "No Updates", "Force Place", "Replace Mode", "Bulldozer",
        "Infinite Reach", "Fast Place", "Angel Placement", "No Clip"};

    public static final float[] reachLimit = {100f};
    public static final float[] angelDistance = {4f};
    public static final ImBoolean typeReplace = new ImBoolean(false);
    public static final String[] FLIGHT = {"Horizontal", "Camera"};
    public static final ImInt flightDirection = new ImInt(0);
    public static final ImBoolean showKeys = new ImBoolean(false);
    public static final ImBoolean preventInteractions = new ImBoolean(false);
    public static final ImBoolean showStructureVoid = new ImBoolean(false);
    public static final ImBoolean showBarriers = new ImBoolean(false);

    /** Alcance de vanilla en creativo: más allá de esto el servidor rechaza romper o colocar. */
    private static final double VANILLA_REACH = 5.5;
    private static int attackTicks;
    private static boolean altWasDown, altUsed;
    private static long altDownAt;
    private static Field rightClickDelay;
    private static boolean fieldSearched;

    private BuilderMode() {
    }

    /** La capability i de {@link #CAPABILITIES}; null para No Clip (que no es un ImBoolean). */
    public static ImBoolean flag(int i) {
        switch (i) {
            case 0:
                return tinker;
            case 1:
                return noUpdates;
            case 2:
                return forcePlace;
            case 3:
                return replaceMode;
            case 4:
                return bulldozer;
            case 5:
                return infiniteReach;
            case 6:
                return fastPlace;
            case 7:
                return angelPlacement;
            default:
                return null;
        }
    }

    public static boolean isOn(int i) {
        ImBoolean f = flag(i);
        return f == null ? NoClip.enabled : f.get();
    }

    public static void toggle(int i) {
        ImBoolean f = flag(i);
        if (f == null) {
            NoClip.toggle();
        } else {
            f.set(!f.get());
            EditEngine.status = CAPABILITIES[i] + (f.get() ? " activado" : " desactivado");
        }
    }

    // ---- Llamadas de los mixins ----------------------------------------------------------------

    /** Alcance con el que apunta el cliente (Infinite Reach), o -1 para dejar el de vanilla. */
    public static float reach() {
        return infiniteReach.get() ? reachLimit[0] : -1f;
    }

    /** ¿Hay que cortar el "mantener botón izquierdo" de vanilla (rompe bloques solo en creativo)? */
    public static boolean suppressHold() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.currentScreen == null && (BuilderTools.active() || bulldozer.get() || noUpdates.get());
    }

    /** La rueda: con Alt cambia de herramienta; con una selección completa empuja el destino. */
    public static boolean wheel(int direction) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null) {
            return false;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU)) {
            altUsed = true;
            BuilderTools.cycle(direction);
            return true;
        }
        return BuilderTools.active() && BuilderTools.wheel(direction);
    }

    /** Un clic del ratón (0 izquierdo, 1 derecho, 2 central) en el mundo; true si el Builder Mode se encarga y vanilla no. */
    public static boolean click(int button) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null || mc.getIntegratedServer() == null) {
            return false;
        }
        if (BuilderTools.active()) {
            return BuilderTools.click(button, mc);
        }
        RayTraceResult hit = mc.objectMouseOver;
        boolean block = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK;
        if (button == 0) {
            if (block && (bulldozer.get() || noUpdates.get() || far(mc, hit))) {
                breakBlock(mc, hit.getBlockPos());
                return true;
            }
            return false;
        }
        if (button == 1) {
            return useItem(mc, hit, block);
        }
        return false;
    }

    private static boolean far(Minecraft mc, RayTraceResult hit) {
        return infiniteReach.get() && hit.hitVec.distanceTo(mc.player.getPositionEyes(1f)) > VANILLA_REACH;
    }

    // ---- Romper y colocar --------------------------------------------------------------------------

    private static void breakBlock(Minecraft mc, BlockPos pos) {
        IBlockState s = mc.world.getBlockState(pos);
        if (s.getBlock() == Blocks.AIR || s.getBlock() == Blocks.BEDROCK && !mc.player.isCreative()) {
            return;
        }
        mc.player.swingArm(EnumHand.MAIN_HAND);
        EditEngine.placeRaw(pos, Blocks.AIR.getDefaultState(), noUpdates.get());
    }

    private static boolean useItem(Minecraft mc, RayTraceResult hit, boolean block) {
        ItemStack held = mc.player.getHeldItemMainhand();
        if (block && replaceMode.get() && held.getItem() instanceof ItemBlock) {
            replace(mc, hit, held);
            return true;
        }
        if (block && tinker.get() && held.isEmpty() && !mc.player.isSneaking()) {
            BlockPos pos = hit.getBlockPos();
            boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            IBlockState next = Tinker.apply(mc.world.getBlockState(pos), pos, hit.hitVec, ctrl);
            if (next != null) {
                mc.player.swingArm(EnumHand.MAIN_HAND);
                EditEngine.placeRaw(pos, next, noUpdates.get());
            }
            return true;
        }
        if (!(held.getItem() instanceof ItemBlock)) {
            return false;
        }
        if (block) {
            if (forcePlace.get() || noUpdates.get() || preventInteractions.get() || far(mc, hit)) {
                BlockPos at = mc.world.getBlockState(hit.getBlockPos()).getBlock().isReplaceable(mc.world, hit.getBlockPos())
                        ? hit.getBlockPos() : hit.getBlockPos().offset(hit.sideHit);
                Vec3d v = hit.hitVec;
                place(mc, at, hit.sideHit, (float) (v.x - hit.getBlockPos().getX()), (float) (v.y - hit.getBlockPos().getY()),
                        (float) (v.z - hit.getBlockPos().getZ()), held);
                return true;
            }
            return false;
        }
        if (angelPlacement.get()) {
            Vec3d eye = mc.player.getPositionEyes(1f), look = mc.player.getLookVec();
            Vec3d p = eye.addVector(look.x * angelDistance[0], look.y * angelDistance[0], look.z * angelDistance[0]);
            place(mc, new BlockPos(p), EnumFacing.UP, 0.5f, 0.5f, 0.5f, held);
            return true;
        }
        return false;
    }

    /** Coloca el bloque de la mano en pos como lo haría vanilla (orientación según quien coloca), sin comprobar soporte con Force Place. */
    private static void place(Minecraft mc, BlockPos pos, EnumFacing side, float hx, float hy, float hz, ItemStack held) {
        ItemBlock ib = (ItemBlock) held.getItem();
        World w = mc.world;
        if (!forcePlace.get() && !w.mayPlace(ib.getBlock(), pos, false, side, mc.player)) {
            return;
        }
        IBlockState st = ib.getBlock().getStateForPlacement(w, pos, side, hx, hy, hz, ib.getMetadata(held.getMetadata()),
                mc.player);
        mc.player.swingArm(EnumHand.MAIN_HAND);
        EditEngine.placeRaw(pos, st, noUpdates.get());
        if (!mc.player.isCreative()) {
            held.shrink(1);
        }
    }

    /** Replace Mode: el bloque apuntado pasa a ser el de la mano, heredando sus propiedades; con Type Replace, la variante del material. */
    private static void replace(Minecraft mc, RayTraceResult hit, ItemStack held) {
        ItemBlock ib = (ItemBlock) held.getItem();
        BlockPos pos = hit.getBlockPos();
        IBlockState old = mc.world.getBlockState(pos);
        IBlockState base = ib.getBlock().getStateFromMeta(ib.getMetadata(held.getMetadata()));
        IBlockState next;
        if (typeReplace.get()) {
            next = BlockVariants.convert(base, old);
            if (next == null) {
                EditEngine.status = "Type Replace: no hay equivalente de ese material";
                return;
            }
        } else {
            next = EditEngine.copyProperties(old, base);
        }
        mc.player.swingArm(EnumHand.MAIN_HAND);
        EditEngine.placeRaw(pos, next, noUpdates.get());
    }

    // ---- Cada tick ----------------------------------------------------------------------------------

    /** Llamar al principio de cada tick del cliente con el mundo abierto. */
    public static void tick(Minecraft mc) {
        EntityPlayerSP p = mc.player;
        if (p == null || mc.world == null) {
            return;
        }
        if (mc.currentScreen == null) {
            altTap(mc);
            if (BuilderTools.active()) {
                BuilderTools.tick(mc);
            }
            bulldoze(mc);
            if (fastPlace.get()) {
                clearRightClickDelay(mc);
            }
            cameraFlight(mc, p);
        } else {
            attackTicks = 0;
        }
        BlockHighlights.update(mc);
    }

    /** Tocar Alt (pulsar y soltar sin rueda, clics ni otras teclas) abre el menú de Builder. */
    private static void altTap(Minecraft mc) {
        boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU);
        if (alt && !altWasDown) {
            altDownAt = System.currentTimeMillis();
            altUsed = false;
        }
        if (alt) {
            for (int k = 1; k < 256 && !altUsed; k++) {
                if (k != Keyboard.KEY_LMENU && Keyboard.isKeyDown(k)) {
                    altUsed = true;
                }
            }
            for (int b = 0; b < 3 && !altUsed; b++) {
                if (Mouse.isButtonDown(b)) {
                    altUsed = true;
                }
            }
        }
        if (!alt && altWasDown && !altUsed && System.currentTimeMillis() - altDownAt < 350) {
            mc.displayGuiScreen(new BuilderMenuScreen());
        }
        altWasDown = alt;
    }

    /** Bulldozer: tras un breve arranque con el botón izquierdo pulsado, rompe el bloque apuntado cada tick. */
    private static void bulldoze(Minecraft mc) {
        if (!bulldozer.get() || !mc.gameSettings.keyBindAttack.isKeyDown()) {
            attackTicks = 0;
            return;
        }
        if (++attackTicks > 8) {
            RayTraceResult hit = mc.objectMouseOver;
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                breakBlock(mc, hit.getBlockPos());
            }
        }
    }

    private static void clearRightClickDelay(Minecraft mc) {
        if (!fieldSearched) {
            fieldSearched = true;
            rightClickDelay = Reflect.find(Minecraft.class, "rightClickDelayTimer", "field_71467_ac", "as");
        }
        Reflect.setInt(rightClickDelay, mc, 0);
    }

    /** Flight direction = Camera: al volar hacia delante también se sube o se baja según hacia dónde mira la cámara. */
    private static void cameraFlight(Minecraft mc, EntityPlayerSP p) {
        if (flightDirection.get() != 1 || !p.capabilities.isFlying || !mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindJump.isKeyDown() || mc.gameSettings.keyBindSneak.isKeyDown()) {
            return;
        }
        double v = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
        double pitch = Math.toRadians(p.rotationPitch);
        double tan = Math.tan(Math.max(-1.45, Math.min(1.45, pitch)));
        p.motionY = Math.max(-1.5, Math.min(1.5, -tan * v));
    }

    // ---- Texto en pantalla ----------------------------------------------------------------------------

    /** Las teclas y botones pulsados ahora, para Show Key Presses. */
    public static List<String> pressedKeys() {
        List<String> out = new ArrayList<String>();
        for (int k = 1; k < 256; k++) {
            if (Keyboard.isKeyDown(k)) {
                String n = Keyboard.getKeyName(k);
                if (n != null) {
                    out.add(n);
                }
            }
        }
        for (int b = 0; b < 3; b++) {
            if (Mouse.isButtonDown(b)) {
                out.add(b == 0 ? "Left Click" : b == 1 ? "Right Click" : "Middle Click");
            }
        }
        return out;
    }

    /** Las capabilities activas, para la esquina de la pantalla. */
    public static String activeCapabilities() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CAPABILITIES.length; i++) {
            if (isOn(i)) {
                sb.append(sb.length() == 0 ? "" : ", ").append(CAPABILITIES[i]);
            }
        }
        return sb.toString();
    }

    /** El bloque de la mano como estado, para el Colour Picker y similares; null si no es un bloque. */
    public static IBlockState heldState() {
        ItemStack held = Minecraft.getMinecraft().player.getHeldItemMainhand();
        if (!(held.getItem() instanceof ItemBlock)) {
            return null;
        }
        ItemBlock ib = (ItemBlock) held.getItem();
        Block b = ib.getBlock();
        try {
            return b.getStateFromMeta(ib.getMetadata(held.getMetadata()));
        } catch (RuntimeException e) {
            return b.getDefaultState();
        }
    }
}
