package dev.litbuild;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Herramientas del Builder Mode que se usan en el mundo, sin abrir el editor: Move, Clone, Stack, Smear, Extrude, Erase
 * y Setup Symmetry. Comparten la selección del editor (Selection): clic izquierdo = primera esquina, derecho = segunda, y
 * después la rueda empuja el destino 1 bloque (hacia donde mira el jugador; X, Y o Z fijan el eje), el clic central lo
 * lleva al bloque apuntado y el derecho confirma. Ctrl+Z / Ctrl+Y deshacen y rehacen.
 */
public final class BuilderTools {
    public static final String[] NAMES = {"None", "Move", "Clone", "Stack", "Smear", "Extrude", "Erase", "Setup Symmetry"};
    public static final int NONE = 0, MOVE = 1, CLONE = 2, STACK = 3, SMEAR = 4, EXTRUDE = 5, ERASE = 6, SYMMETRY = 7;

    public static int tool;
    public static final ImBoolean keepExisting = new ImBoolean(false);
    public static final ImBoolean copyAir = new ImBoolean(false);
    /** Destino relativo a la selección (Move, Clone, Smear). */
    public static final int[] offset = new int[3];

    /** 0 = sin esquinas, 1 = una esquina, 2 = selección completa. */
    private static int phase;
    private static int stackCount = 1;
    private static EnumFacing stackDir = EnumFacing.UP;
    private static final boolean[] PREV = new boolean[256];

    private BuilderTools() {
    }

    public static boolean active() {
        return tool != NONE;
    }

    public static void select(int t) {
        tool = Math.floorMod(t, NAMES.length);
        reset();
    }

    public static void cycle(int dir) {
        select(tool + (dir > 0 ? 1 : -1));
    }

    private static void reset() {
        phase = 0;
        offset[0] = offset[1] = offset[2] = 0;
        stackCount = 1;
        if (tool != NONE) {
            Selection.clear();
        }
    }

    // ---- Entrada --------------------------------------------------------------------------

    /** Un clic (0 izquierdo, 1 derecho, 2 central) con una herramienta activa; siempre se queda con él. */
    public static boolean click(int button, Minecraft mc) {
        RayTraceResult hit = mc.objectMouseOver;
        boolean block = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK;
        BlockPos pos = block ? hit.getBlockPos() : null;
        if (tool == EXTRUDE) {
            if (block && button != 2) {
                EditEngine.extrude(pos, hit.sideHit, 1, button == 0, MagicSelect.BLOCK);
            }
            return true;
        }
        if (tool == SYMMETRY) {
            if (block && button == 1) {
                Symmetry.setNode(hit.hitVec);
                Symmetry.enabled.set(true);
            }
            return true;
        }
        if (button == 0) {
            if (tool == CLONE && phase == 2) {
                reset();
            } else if (pos != null) {
                Selection.set(pos, pos);
                phase = 1;
                offset[0] = offset[1] = offset[2] = 0;
                stackCount = 1;
            }
        } else if (button == 1) {
            if (phase == 1 && pos != null) {
                Selection.setSecond(pos);
                phase = 2;
            } else if (phase == 2) {
                confirm();
            } else if (tool == ERASE && pos != null) {
                EditEngine.eraseConnected(pos, 128);
            }
        } else if (pos != null) {
            if (phase == 2) {
                moveDestination(pos.offset(hit.sideHit));
            } else {
                Selection.set(pos, pos);
                phase = 2;
                offset[0] = offset[1] = offset[2] = 0;
            }
        }
        return true;
    }

    /** La rueda con la selección completa: empuja el destino (o añade un paso al Stack). false si no hay nada que empujar. */
    public static boolean wheel(int dir) {
        if (phase != 2 || !Selection.active) {
            return false;
        }
        EnumFacing f = lookAxis(dir > 0 ? 1 : -1);
        if (tool == STACK) {
            if (f != stackDir && stackCount > 1) {
                stackCount = 1;
            }
            stackDir = f;
            stackCount = Math.max(1, stackCount + 1);
        } else if (tool == MOVE || tool == CLONE || tool == SMEAR) {
            offset[0] += f.getFrontOffsetX();
            offset[1] += f.getFrontOffsetY();
            offset[2] += f.getFrontOffsetZ();
        } else {
            return false;
        }
        return true;
    }

    /** El eje hacia el que mira el jugador (o el fijado con X, Y, Z) con signo sign (1 = alejarse). */
    private static EnumFacing lookAxis(int sign) {
        Vec3d look = Minecraft.getMinecraft().player.getLookVec();
        double[] v = {look.x, look.y, look.z};
        int axis = 0;
        for (int i = 1; i < 3; i++) {
            if (Math.abs(v[i]) > Math.abs(v[axis])) {
                axis = i;
            }
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_X)) {
            axis = 0;
        } else if (Keyboard.isKeyDown(Keyboard.KEY_Y)) {
            axis = 1;
        } else if (Keyboard.isKeyDown(Keyboard.KEY_Z)) {
            axis = 2;
        }
        boolean positive = v[axis] >= 0;
        if (sign < 0) {
            positive = !positive;
        }
        return axis == 0 ? (positive ? EnumFacing.EAST : EnumFacing.WEST)
                : axis == 1 ? (positive ? EnumFacing.UP : EnumFacing.DOWN) : (positive ? EnumFacing.SOUTH : EnumFacing.NORTH);
    }

    /** Clic central: el destino pasa a ser el bloque apuntado (la esquina mínima de la selección va ahí). */
    private static void moveDestination(BlockPos target) {
        int dx = target.getX() - Selection.min(0), dy = target.getY() - Selection.min(1), dz = target.getZ() - Selection.min(2);
        if (tool == STACK) {
            int[] d = {dx, dy, dz};
            int axis = 0;
            for (int i = 1; i < 3; i++) {
                if (Math.abs(d[i]) > Math.abs(d[axis])) {
                    axis = i;
                }
            }
            stackDir = axis == 0 ? (d[0] >= 0 ? EnumFacing.EAST : EnumFacing.WEST)
                    : axis == 1 ? (d[1] >= 0 ? EnumFacing.UP : EnumFacing.DOWN) : (d[2] >= 0 ? EnumFacing.SOUTH : EnumFacing.NORTH);
            stackCount = Math.max(1, Math.abs(d[axis]) / Math.max(1, Selection.size(axis)));
        } else {
            offset[0] = dx;
            offset[1] = dy;
            offset[2] = dz;
        }
    }

    private static void confirm() {
        switch (tool) {
            case MOVE:
                if ((offset[0] | offset[1] | offset[2]) != 0
                        && EditEngine.transfer(offset[0], offset[1], offset[2], true, keepExisting.get(), copyAir.get())) {
                    Selection.shift(offset[0], offset[1], offset[2]);
                    offset[0] = offset[1] = offset[2] = 0;
                }
                break;
            case CLONE:
                if ((offset[0] | offset[1] | offset[2]) != 0) {
                    EditEngine.transfer(offset[0], offset[1], offset[2], false, keepExisting.get(), copyAir.get());
                }
                break;
            case STACK:
                EditEngine.stack(stackDir, stackCount, 0);
                stackCount = 1;
                break;
            case SMEAR:
                EditEngine.smear(offset[0], offset[1], offset[2], keepExisting.get());
                offset[0] = offset[1] = offset[2] = 0;
                break;
            case ERASE:
                EditEngine.fill("Erase", Blocks.AIR.getDefaultState());
                reset();
                break;
            default:
                break;
        }
    }

    // ---- Teclado cada tick ------------------------------------------------------------------

    private static boolean pressed(int key) {
        boolean down = Keyboard.isKeyDown(key);
        boolean edge = down && !PREV[key];
        PREV[key] = down;
        return edge;
    }

    /** Ctrl+Z / Ctrl+Y, Del / Backspace y los modificadores de simetría; llamar cada tick con una herramienta activa. */
    public static void tick(Minecraft mc) {
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean z = pressed(Keyboard.KEY_Z), y = pressed(Keyboard.KEY_Y), f = pressed(Keyboard.KEY_F), r = pressed(Keyboard.KEY_R);
        boolean del = pressed(Keyboard.KEY_DELETE), back = pressed(Keyboard.KEY_BACK);
        if (ctrl && z) {
            EditEngine.undo();
        } else if (ctrl && y) {
            EditEngine.redo();
        }
        if (tool == ERASE && phase == 2 && (del || back)) {
            confirm();
        }
        if (tool == SYMMETRY) {
            if (ctrl && f) {
                EnumFacing face = lookAxis(1);
                ImBoolean m = face.getAxis() == EnumFacing.Axis.X ? Symmetry.mirrorX
                        : face.getAxis() == EnumFacing.Axis.Y ? Symmetry.mirrorY : Symmetry.mirrorZ;
                m.set(!m.get());
            } else if (ctrl && r) {
                Symmetry.rotate180.set(!Symmetry.rotate180.get());
            }
            if (del || back) {
                Symmetry.nodeSet = false;
                Symmetry.enabled.set(false);
            }
            if (Symmetry.nodeSet) {
                double dx = Symmetry.node[0] - mc.player.posX, dy = Symmetry.node[1] - mc.player.posY,
                        dz = Symmetry.node[2] - mc.player.posZ;
                if (dx * dx + dy * dy + dz * dz > 256 * 256) {
                    Symmetry.nodeSet = false;
                }
            }
        }
    }

    // ---- Vista previa y texto -----------------------------------------------------------------

    /** Cajas {x1, y1, z1, x2, y2, z2} (esquina máxima exclusiva) del destino que se va a confirmar. */
    public static List<int[]> preview() {
        List<int[]> out = new ArrayList<int[]>();
        if (phase != 2 || !Selection.active) {
            return out;
        }
        int x1 = Selection.min(0), y1 = Selection.min(1), z1 = Selection.min(2);
        int x2 = Selection.max(0) + 1, y2 = Selection.max(1) + 1, z2 = Selection.max(2) + 1;
        if (tool == MOVE || tool == CLONE || tool == SMEAR) {
            if ((offset[0] | offset[1] | offset[2]) != 0) {
                out.add(new int[] {x1 + offset[0], y1 + offset[1], z1 + offset[2], x2 + offset[0], y2 + offset[1], z2 + offset[2]});
            }
        } else if (tool == STACK) {
            int step = Selection.size(stackDir.getAxis().ordinal());
            for (int k = 1; k <= Math.min(stackCount, 64); k++) {
                int ox = stackDir.getFrontOffsetX() * step * k, oy = stackDir.getFrontOffsetY() * step * k,
                        oz = stackDir.getFrontOffsetZ() * step * k;
                out.add(new int[] {x1 + ox, y1 + oy, z1 + oz, x2 + ox, y2 + oy, z2 + oz});
            }
        }
        return out;
    }

    /** Las líneas de la barra de estado sobre la hotbar. */
    public static List<String> hud() {
        List<String> lines = new ArrayList<String>();
        if (tool == NONE) {
            return lines;
        }
        String s = "Builder: " + NAMES[tool];
        if (Selection.active && phase > 0) {
            s += "  " + Selection.size(0) + " x " + Selection.size(1) + " x " + Selection.size(2);
        }
        if (phase == 2 && (tool == MOVE || tool == CLONE || tool == SMEAR) && (offset[0] | offset[1] | offset[2]) != 0) {
            s += "  offset " + offset[0] + ", " + offset[1] + ", " + offset[2];
        }
        if (phase == 2 && tool == STACK) {
            s += "  x" + stackCount + " " + stackDir.getName();
        }
        lines.add(s);
        String help;
        switch (tool) {
            case EXTRUDE:
                help = "Derecho: extruir cara · Izquierdo: encoger";
                break;
            case SYMMETRY:
                help = "Derecho: nodo · Ctrl+F voltear · Ctrl+R rotar · Supr: quitar";
                break;
            case ERASE:
                help = phase == 0 ? "Derecho: borrar 128 conectados · Izq + Der: cuboide, Supr" : "Supr / Derecho: borrar";
                break;
            default:
                help = phase == 0 ? "Izquierdo: 1ª esquina" : phase == 1 ? "Derecho: 2ª esquina"
                        : "Rueda: empujar · Central: llevar al bloque · Derecho: confirmar";
        }
        lines.add(help);
        return lines;
    }
}
