package dev.litbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.util.vector.Vector3f;

import imgui.ImDrawList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLilyPad;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.BlockVine;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;

/**
 * Iconos de items y bloques como en el inventario, dibujados con ImGui: se toman los polígonos del
 * modelo del item, se proyectan con la transformación "gui" del propio modelo (la vista isométrica
 * de los bloques) y se pintan como quads texturados del atlas de bloques. No usa framebuffers ni toca
 * el estado de OpenGL.
 */
public final class Icons {
    /** Polígonos ya proyectados, centrados en 0 y en unidades de 16 px (el tamaño del icono vanilla). */
    private static final class Model {
        final float[] xy;
        final float[] uv;
        final int[] col;
        final int n;

        Model(float[] xy, float[] uv, int[] col, int n) {
            this.xy = xy;
            this.uv = uv;
            this.col = col;
            this.n = n;
        }
    }

    private static final Model EMPTY = new Model(new float[0], new float[0], new int[0], 0);
    private static final Map<String, Model> CACHE = new HashMap<String, Model>();
    private static final EnumFacing[] SIDES = {null, EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH,
        EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    /** Las dos luces del inventario vanilla (RenderHelper.enableGUIStandardItemLighting), en espacio de ojo. */
    private static final float[] L0 = light(0.2f, 1f, -0.7f);
    private static final float[] L1 = light(-0.2f, 1f, 0.7f);

    private Icons() {
    }

    /** Vacía la caché (hay que reconstruirla si se recargan los recursos). */
    public static void clear() {
        CACHE.clear();
    }

    /** El item que representa un estado de bloque en el inventario, o null si no tiene. */
    public static ItemStack stackFor(IBlockState state) {
        Block b = state.getBlock();
        Item item = Item.getItemFromBlock(b);
        return item == Items.AIR ? null : new ItemStack(item, 1, b.damageDropped(state));
    }

    /** Dibuja el icono de un item centrado en (cx, cy) con lado size. false si no se pudo. */
    public static boolean draw(ImDrawList dl, ItemStack stack, float cx, float cy, float size) {
        String key = Item.REGISTRY.getNameForObject(stack.getItem()) + ":" + stack.getMetadata();
        Model m = CACHE.get(key);
        if (m == null) {
            try {
                m = build(stack);
            } catch (RuntimeException e) {
                LiteModLitBuild.LOG.warn("No se pudo generar el icono de " + key, e);
                m = EMPTY;
            }
            CACHE.put(key, m);
        }
        paint(dl, m, cx, cy, size);
        return m.n > 0;
    }

    /** Dibuja un bloque: su icono de inventario o, si no tiene item, su textura. */
    public static boolean drawState(ImDrawList dl, IBlockState state, float cx, float cy, float size) {
        ItemStack s = stackFor(state);
        if (s != null && draw(dl, s, cx, cy, size)) {
            return true;
        }
        String key = "state:" + Block.getStateId(state);
        Model m = CACHE.get(key);
        if (m == null) {
            try {
                m = flat(Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getTexture(state));
            } catch (RuntimeException e) {
                m = EMPTY;
            }
            CACHE.put(key, m);
        }
        paint(dl, m, cx, cy, size);
        return m.n > 0;
    }

    private static void paint(ImDrawList dl, Model m, float cx, float cy, float size) {
        if (m.n == 0) {
            return;
        }
        float k = size / 16f;
        long atlas = Minecraft.getMinecraft().getTextureMapBlocks().getGlTextureId();
        for (int i = 0; i < m.n; i++) {
            int a = i * 8;
            dl.addImageQuad(atlas,
                    cx + m.xy[a] * k, cy + m.xy[a + 1] * k, cx + m.xy[a + 2] * k, cy + m.xy[a + 3] * k,
                    cx + m.xy[a + 4] * k, cy + m.xy[a + 5] * k, cx + m.xy[a + 6] * k, cy + m.xy[a + 7] * k,
                    m.uv[a], m.uv[a + 1], m.uv[a + 2], m.uv[a + 3], m.uv[a + 4], m.uv[a + 5], m.uv[a + 6],
                    m.uv[a + 7], m.col[i]);
        }
    }

    /** Una sola cara con la textura de partícula (para modelos especiales como cofres o camas). */
    private static Model flat(TextureAtlasSprite s) {
        if (s == null) {
            return EMPTY;
        }
        float[] xy = {-8, -8, 8, -8, 8, 8, -8, 8};
        float[] uv = {s.getMinU(), s.getMinV(), s.getMaxU(), s.getMinV(), s.getMaxU(), s.getMaxV(),
            s.getMinU(), s.getMaxV()};
        return new Model(xy, uv, new int[] {0xFFFFFFFF}, 1);
    }

    private static Model build(ItemStack stack) {
        IBakedModel model = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(stack, null, null);
        if (model == null) {
            return EMPTY;
        }
        if (model.isBuiltInRenderer()) {
            return flat(model.getParticleTexture());
        }
        ItemTransformVec3f t = model.getItemCameraTransforms().gui;
        boolean lit = model.isGui3d();
        List<Quad> quads = new ArrayList<Quad>();
        for (EnumFacing side : SIDES) {
            for (BakedQuad q : model.getQuads(null, side, 0L)) {
                int[] d = q.getVertexData();
                if (d.length < 28 || q.getFace() == null) {
                    continue;
                }
                float[] n = rotate(t.rotation, q.getFace().getFrontOffsetX(), q.getFace().getFrontOffsetY(),
                        q.getFace().getFrontOffsetZ());
                n[1] = -n[1]; // el espacio de ojo del inventario tiene la Y hacia abajo
                if (n[2] <= 0.001f) {
                    continue; // cara que mira hacia atrás
                }
                Quad e = new Quad();
                for (int v = 0; v < 4; v++) {
                    float x = (Float.intBitsToFloat(d[v * 7]) - 0.5f) * t.scale.x;
                    float y = (Float.intBitsToFloat(d[v * 7 + 1]) - 0.5f) * t.scale.y;
                    float z = (Float.intBitsToFloat(d[v * 7 + 2]) - 0.5f) * t.scale.z;
                    float[] p = rotate(t.rotation, x, y, z);
                    e.xy[v * 2] = (p[0] + t.translation.x) * 16f;
                    e.xy[v * 2 + 1] = -(p[1] + t.translation.y) * 16f;
                    e.depth += (p[2] + t.translation.z) * 16f / 4f;
                    e.uv[v * 2] = Float.intBitsToFloat(d[v * 7 + 4]);
                    e.uv[v * 2 + 1] = Float.intBitsToFloat(d[v * 7 + 5]);
                }
                float shade = lit ? Math.min(1f, 0.4f + 0.6f * Math.max(0f, dot(n, L0)) + 0.6f * Math.max(0f, dot(n, L1))) : 1f;
                e.col = pack(shade, q.hasTintIndex() ? tint(stack) : 0xFFFFFF);
                quads.add(e);
            }
        }
        if (quads.isEmpty()) {
            return flat(model.getParticleTexture());
        }
        // Algoritmo del pintor: primero lo más lejano (menor z en el espacio de ojo).
        Collections.sort(quads, new Comparator<Quad>() {
            @Override
            public int compare(Quad a, Quad b) {
                return Float.compare(a.depth, b.depth);
            }
        });
        int n = quads.size();
        float[] xy = new float[n * 8];
        float[] uv = new float[n * 8];
        int[] col = new int[n];
        for (int i = 0; i < n; i++) {
            Quad e = quads.get(i);
            System.arraycopy(e.xy, 0, xy, i * 8, 8);
            System.arraycopy(e.uv, 0, uv, i * 8, 8);
            col[i] = e.col;
        }
        return new Model(xy, uv, col, n);
    }

    private static final class Quad {
        float depth;
        final float[] xy = new float[8];
        final float[] uv = new float[8];
        int col;
    }

    /**
     * Aplica la rotación de la transformación "gui". El juego la construye como el cuaternión
     * qX * qY * qZ, que sobre un vector equivale a girar primero Z, luego Y y por último X.
     */
    private static float[] rotate(Vector3f rot, float x, float y, float z) {
        double rz = Math.toRadians(rot.z);
        float x1 = (float) (x * Math.cos(rz) - y * Math.sin(rz));
        float y1 = (float) (x * Math.sin(rz) + y * Math.cos(rz));
        double ry = Math.toRadians(rot.y);
        float x2 = (float) (x1 * Math.cos(ry) + z * Math.sin(ry));
        float z2 = (float) (-x1 * Math.sin(ry) + z * Math.cos(ry));
        double rx = Math.toRadians(rot.x);
        float y3 = (float) (y1 * Math.cos(rx) - z2 * Math.sin(rx));
        float z3 = (float) (y1 * Math.sin(rx) + z2 * Math.cos(rx));
        return new float[] {x2, y3, z3};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] light(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        x /= len;
        y /= len;
        z /= len;
        double a = Math.toRadians(165);
        float y1 = (float) (y * Math.cos(a) - z * Math.sin(a));
        float z1 = (float) (y * Math.sin(a) + z * Math.cos(a));
        double b = Math.toRadians(-30);
        return new float[] {(float) (x * Math.cos(b) + z1 * Math.sin(b)), y1, (float) (-x * Math.sin(b) + z1 * Math.cos(b))};
    }

    /** Color ABGR de ImGui: la luz de la cara multiplicada por el tinte (0xRRGGBB). */
    private static int pack(float shade, int tint) {
        int r = Math.min(255, Math.round(shade * ((tint >> 16) & 255)));
        int g = Math.min(255, Math.round(shade * ((tint >> 8) & 255)));
        int b = Math.min(255, Math.round(shade * (tint & 255)));
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** Tinte de hierba, hojas y similares (el juego los saca de ItemColors, que no es accesible). */
    private static int tint(ItemStack stack) {
        Block b = Block.getBlockFromItem(stack.getItem());
        if (b instanceof BlockGrass || b instanceof BlockTallGrass || b instanceof BlockDoublePlant) {
            return ColorizerGrass.getGrassColor(0.5, 1.0);
        }
        if (b instanceof BlockOldLeaf) {
            int variant = stack.getMetadata() & 3;
            return variant == 1 ? ColorizerFoliage.getFoliageColorPine()
                    : variant == 2 ? ColorizerFoliage.getFoliageColorBirch() : ColorizerFoliage.getFoliageColorBasic();
        }
        if (b instanceof BlockLeaves || b instanceof BlockVine) {
            return ColorizerFoliage.getFoliageColorBasic();
        }
        if (b instanceof BlockLilyPad) {
            return 0x208030;
        }
        return 0xFFFFFF;
    }
}
