package dev.litbuild;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Biomes;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

/**
 * Vista previa del Placement con los bloques de verdad, translúcidos. La forma se renderiza una vez con el renderizador
 * de bloques de vanilla sobre un mundo falso (el propio portapapeles) y se guarda en una lista de dibujo de OpenGL, como
 * hacía vanilla con los chunks; luego solo se mueve. Los tile entities (cofres, carteles) no se dibujan. Si algo falla,
 * se desactiva y el Placement se queda con la caja naranja.
 */
public final class PlacementPreview {
    /** Más bloques que esto y solo se dibuja la caja (cada cambio de forma vuelve a renderizar todo). */
    public static final int MAX_BLOCKS = 20_000;
    private static final float ALPHA = 0.7f;
    private static int list = -1;
    private static Clipboard built;
    private static boolean failed;

    private PlacementPreview() {
    }

    /**
     * Dibuja shape con su esquina mínima en (x, y, z), con las matrices del mundo ya activas y trasladadas a la cámara. false
     * si no se pudo (desactivada, demasiado grande o un fallo de renderizado): entonces solo queda la caja.
     */
    public static boolean draw(Clipboard shape, double x, double y, double z) {
        if (failed || shape == null || !Placement.PREVIEW.get()) {
            return false;
        }
        if (shape != built && BoxGizmo.dragging) {
            return false; // mientras se arrastra un anillo o un cubito la forma cambia a cada paso: solo la caja, y se renderiza al soltar
        }
        boolean pushed = false;
        try {
            if (shape != built) {
                rebuild(shape);
            }
            if (list < 0) {
                return false;
            }
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            pushed = true;
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendColor(1f, 1f, 1f, ALPHA);
            GL11.glBlendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(true);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                GL11.glCallList(list);
            } finally {
                GL11.glPopMatrix();
            }
            GL11.glPopAttrib();
            pushed = false;
            return true;
        } catch (Throwable t) {
            if (pushed) {
                GL11.glPopAttrib();
            }
            failed = true;
            release();
            LiteModLitBuild.LOG.error("Vista previa del Placement desactivada", t);
            return false;
        }
    }

    /** Suelta la lista de dibujo (al cerrar el Placement o cambiar de forma). */
    public static void release() {
        if (list >= 0) {
            GL11.glDeleteLists(list, 1);
            list = -1;
        }
        built = null;
    }

    private static void rebuild(final Clipboard shape) {
        release();
        built = shape;
        int count = 0;
        for (int id : shape.states) {
            if (id != Clipboard.AIR) {
                count++;
            }
        }
        if (count == 0 || count > MAX_BLOCKS) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        IBlockAccess access = new IBlockAccess() {
            @Override
            public TileEntity getTileEntity(BlockPos pos) {
                return null;
            }

            @Override
            public int getCombinedLight(BlockPos pos, int lightValue) {
                return 15 << 20 | 15 << 4;
            }

            @Override
            public IBlockState getBlockState(BlockPos pos) {
                int x = pos.getX(), y = pos.getY(), z = pos.getZ();
                if (x < 0 || y < 0 || z < 0 || x >= shape.sx || y >= shape.sy || z >= shape.sz) {
                    return net.minecraft.init.Blocks.AIR.getDefaultState();
                }
                return Block.getStateById(shape.states[shape.index(x, y, z)]);
            }

            @Override
            public boolean isAirBlock(BlockPos pos) {
                return getBlockState(pos).getMaterial() == net.minecraft.block.material.Material.AIR;
            }

            @Override
            public Biome getBiome(BlockPos pos) {
                return Biomes.PLAINS;
            }

            @Override
            public int getStrongPower(BlockPos pos, EnumFacing direction) {
                return 0;
            }

            @Override
            public WorldType getWorldType() {
                return WorldType.DEFAULT;
            }
        };
        BufferBuilder buffer = new BufferBuilder(1 << 21);
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = 0; y < shape.sy; y++) {
            for (int z = 0; z < shape.sz; z++) {
                for (int x = 0; x < shape.sx; x++) {
                    int id = shape.states[shape.index(x, y, z)];
                    if (id == Clipboard.AIR) {
                        continue;
                    }
                    q.setPos(x, y, z);
                    IBlockState s = Block.getStateById(id);
                    if (s.getRenderType() == EnumBlockRenderType.INVISIBLE) {
                        continue;
                    }
                    dispatcher.renderBlock(s.getActualState(access, q), q, access, buffer);
                }
            }
        }
        buffer.finishDrawing();
        list = GL11.glGenLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        try {
            new WorldVertexBufferUploader().draw(buffer);
        } finally {
            GL11.glEndList();
        }
    }
}
