package dev.litbuild;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec4;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;

/**
 * Backend de Dear ImGui para LWJGL 2 / OpenGL de función fija (Minecraft 1.12.2).
 * Hace lo mismo que imgui_impl_opengl2 de ImGui: un solo contexto estático, sin viewports.
 */
public final class ImGuiBackend {
    private static boolean ready;
    private static int fontTexture;
    private static long lastNanos;
    private static final ImVec4 CLIP = new ImVec4();

    // Estado de modificadores (LWJGL 2 no da un evento por modificador combinado).
    private static boolean ctrlL, ctrlR, shiftL, shiftR, altL, altR;

    private ImGuiBackend() {
    }

    private static void init() {
        Minecraft mc = Minecraft.getMinecraft();
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.NavEnableKeyboard);
        io.setBackendPlatformName("litbuild-lwjgl2");
        File dir = new File(mc.mcDataDir, "config/litbuild");
        dir.mkdirs();
        io.setIniFilename(new File(dir, "imgui.ini").getPath());

        UiSettings.load();
        fontTexture = GL11.glGenTextures();
        buildFonts(UiSettings.fontSize);
        Theme.apply(UiSettings.theme);
        ready = true;
    }

    /** Tamaño de fuente pendiente de aplicar al empezar el siguiente frame (0 = ninguno). */
    private static float pendingFontSize;

    /** Cambia el tamaño de la fuente (se reconstruye el atlas entre dos frames). */
    public static void setFontSize(float pixels) {
        pendingFontSize = pixels;
    }

    /** Reconstruye el atlas de fuentes (ruta clásica: sin RendererHasTextures) y lo sube a la textura. */
    private static void buildFonts(float pixels) {
        ImFontAtlas atlas = ImGui.getIO().getFonts();
        atlas.clear();
        ImFontConfig cfg = new ImFontConfig();
        cfg.setSizePixels(pixels);
        atlas.addFontDefaultVector(cfg);
        ImInt w = new ImInt();
        ImInt h = new ImInt();
        ByteBuffer pixelData = atlas.getTexDataAsRGBA32(w, h);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fontTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w.get(), h.get(), 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, pixelData);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        atlas.setTexID(fontTexture);
    }

    public static boolean isReady() {
        return ready;
    }

    public static void shutdown() {
        if (!ready) {
            return;
        }
        ready = false;
        GL11.glDeleteTextures(fontTexture);
        ImGui.destroyContext();
    }

    /** Un frame completo: entrada de ratón, UI del callback y dibujado. */
    public static void frame(Runnable ui) {
        if (!ready) {
            init();
        }
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(w, h);
        io.setDisplayFramebufferScale(1f, 1f);
        long now = System.nanoTime();
        io.setDeltaTime(lastNanos == 0 ? 1f / 60f : Math.max((now - lastNanos) / 1e9f, 1e-5f));
        lastNanos = now;
        io.addMousePosEvent(Mouse.getX(), h - Mouse.getY() - 1);

        if (pendingFontSize > 0) {
            buildFonts(pendingFontSize);
            pendingFontSize = 0;
        }
        ImGui.newFrame();
        try {
            ui.run();
        } finally {
            ImGui.render();
        }
        draw(ImGui.getDrawData(), w, h);
    }

    // ---- Entrada -------------------------------------------------------------------------

    public static void key(int key, boolean down, char ch) {
        ImGuiIO io = ImGui.getIO();
        switch (key) {
            case Keyboard.KEY_LCONTROL: ctrlL = down; break;
            case Keyboard.KEY_RCONTROL: ctrlR = down; break;
            case Keyboard.KEY_LSHIFT: shiftL = down; break;
            case Keyboard.KEY_RSHIFT: shiftR = down; break;
            case Keyboard.KEY_LMENU: altL = down; break;
            case Keyboard.KEY_RMENU: altR = down; break;
            default: break;
        }
        io.addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, ctrlL || ctrlR);
        io.addKeyEvent(ImGuiKey.ImGuiMod_Shift, shiftL || shiftR);
        io.addKeyEvent(ImGuiKey.ImGuiMod_Alt, altL || altR);
        int k = mapKey(key);
        if (k != 0) {
            io.addKeyEvent(k, down);
        }
        if (down && ch >= 32 && ch != 127) {
            io.addInputCharacter(ch);
        }
    }

    /** x, y en píxeles de LWJGL (origen abajo a la izquierda). button = -1 si es solo movimiento. */
    public static void mouse(int x, int y, int button, boolean down, int dWheel) {
        ImGuiIO io = ImGui.getIO();
        io.addMousePosEvent(x, Minecraft.getMinecraft().displayHeight - y - 1);
        if (button >= 0 && button < 5) {
            io.addMouseButtonEvent(button, down);
        }
        if (dWheel != 0) {
            io.addMouseWheelEvent(0f, dWheel / 120f);
        }
    }

    private static int mapKey(int key) {
        switch (key) {
            case Keyboard.KEY_TAB: return ImGuiKey.Tab;
            case Keyboard.KEY_LEFT: return ImGuiKey.LeftArrow;
            case Keyboard.KEY_RIGHT: return ImGuiKey.RightArrow;
            case Keyboard.KEY_UP: return ImGuiKey.UpArrow;
            case Keyboard.KEY_DOWN: return ImGuiKey.DownArrow;
            case Keyboard.KEY_PRIOR: return ImGuiKey.PageUp;
            case Keyboard.KEY_NEXT: return ImGuiKey.PageDown;
            case Keyboard.KEY_HOME: return ImGuiKey.Home;
            case Keyboard.KEY_END: return ImGuiKey.End;
            case Keyboard.KEY_INSERT: return ImGuiKey.Insert;
            case Keyboard.KEY_DELETE: return ImGuiKey.Delete;
            case Keyboard.KEY_BACK: return ImGuiKey.Backspace;
            case Keyboard.KEY_SPACE: return ImGuiKey.Space;
            case Keyboard.KEY_RETURN: return ImGuiKey.Enter;
            case Keyboard.KEY_NUMPADENTER: return ImGuiKey.KeypadEnter;
            case Keyboard.KEY_ESCAPE: return ImGuiKey.Escape;
            case Keyboard.KEY_LCONTROL: return ImGuiKey.LeftCtrl;
            case Keyboard.KEY_RCONTROL: return ImGuiKey.RightCtrl;
            case Keyboard.KEY_LSHIFT: return ImGuiKey.LeftShift;
            case Keyboard.KEY_RSHIFT: return ImGuiKey.RightShift;
            case Keyboard.KEY_LMENU: return ImGuiKey.LeftAlt;
            case Keyboard.KEY_RMENU: return ImGuiKey.RightAlt;
            default: break;
        }
        // Letras, dígitos y F1-F12 por el nombre de tecla de LWJGL ("A", "5", "F3").
        String n = Keyboard.getKeyName(key);
        if (n == null) {
            return 0;
        }
        if (n.length() == 1) {
            char c = n.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return ImGuiKey.A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return ImGuiKey._0 + (c - '0');
            }
        } else if (n.charAt(0) == 'F') {
            try {
                int f = Integer.parseInt(n.substring(1));
                if (f >= 1 && f <= 12) {
                    return ImGuiKey.F1 + (f - 1);
                }
            } catch (NumberFormatException ignored) {
                // no es una tecla F
            }
        }
        return 0;
    }

    // ---- Render (OpenGL de función fija) ---------------------------------------------------

    private static ByteBuffer vtxCopy = alloc(1 << 16);
    private static ByteBuffer idxCopy = alloc(1 << 16);

    private static ByteBuffer alloc(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    /** Copia src a un buffer directo propio (uno para vértices, otro para índices). */
    private static ByteBuffer copy(ByteBuffer src, boolean vertices) {
        ByteBuffer dst = vertices ? vtxCopy : idxCopy;
        if (dst.capacity() < src.remaining()) {
            dst = alloc(Math.max(src.remaining(), dst.capacity() * 2));
            if (vertices) {
                vtxCopy = dst;
            } else {
                idxCopy = dst;
            }
        }
        dst.clear();
        dst.put(src);
        dst.flip();
        return dst;
    }

    private static void draw(ImDrawData dd, int fbW, int fbH) {
        if (fbW <= 0 || fbH <= 0 || dd.getCmdListsCount() == 0) {
            return;
        }
        if (ImDrawData.sizeOfImDrawIdx() != 2) {
            throw new IllegalStateException("ImDrawIdx de 4 bytes no soportado");
        }
        int stride = ImDrawData.sizeOfImDrawVert();

        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
        GL20.glUseProgram(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glColorMask(true, true, true, true);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);

        GL11.glViewport(0, 0, fbW, fbH);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, fbW, fbH, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        float ox = dd.getDisplayPosX();
        float oy = dd.getDisplayPosY();
        for (int n = 0; n < dd.getCmdListsCount(); n++) {
            // Los dos getters de imgui-java devuelven el MISMO ByteBuffer estático: hay que copiar
            // los vértices antes de pedir los índices.
            ByteBuffer vtx = copy(dd.getCmdListVtxBufferData(n), true);
            ShortBuffer idx = copy(dd.getCmdListIdxBufferData(n), false).asShortBuffer();
            vtx.position(0);
            GL11.glVertexPointer(2, GL11.GL_FLOAT, stride, vtx);
            vtx.position(8);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, vtx);
            vtx.position(16);
            GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, vtx);

            for (int c = 0, cc = dd.getCmdListCmdBufferSize(n); c < cc; c++) {
                dd.getCmdListCmdBufferClipRect(CLIP, n, c);
                float x1 = CLIP.x - ox;
                float y1 = CLIP.y - oy;
                float x2 = CLIP.z - ox;
                float y2 = CLIP.w - oy;
                if (x2 <= x1 || y2 <= y1) {
                    continue;
                }
                GL11.glScissor((int) x1, (int) (fbH - y2), (int) (x2 - x1), (int) (y2 - y1));
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, (int) dd.getCmdListCmdBufferTextureId(n, c));
                int off = dd.getCmdListCmdBufferIdxOffset(n, c);
                idx.limit(off + dd.getCmdListCmdBufferElemCount(n, c));
                idx.position(off);
                GL11.glDrawElements(GL11.GL_TRIANGLES, idx);
            }
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        GL20.glUseProgram(program);
    }
}
