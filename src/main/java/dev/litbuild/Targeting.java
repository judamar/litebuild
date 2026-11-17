package dev.litbuild;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Apuntado con el cursor: guarda las matrices del mundo en cada frame y las usa para
 * trazar un rayo desde el píxel del ratón hasta el primer bloque.
 */
public final class Targeting {
    /** Bloque bajo el cursor mientras el editor está abierto (null si ninguno). */
    public static BlockPos hover;
    /** Posición interpolada de los PIES de la cámara (el origen que usa el render del mundo). */
    public static double camX, camY, camZ;

    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer OUT = BufferUtils.createFloatBuffer(4);
    private static boolean valid;

    private Targeting() {
    }

    /** Llamar con las matrices del mundo activas (PostRenderListener.onPostRenderEntities). */
    public static void capture(float partialTicks) {
        Entity e = Minecraft.getMinecraft().getRenderViewEntity();
        if (e == null) {
            valid = false;
            return;
        }
        camX = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks;
        camY = e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks;
        camZ = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks;
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        PROJECTION.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        VIEWPORT.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        valid = true;
    }

    /**
     * Primer bloque que atraviesa el rayo bajo el píxel (mouseX, mouseY), con origen arriba a la
     * izquierda. Ignora bloques sin colisión (hierba, flores). null si no hay.
     */
    public static RayTraceResult pick(float mouseX, float mouseY, double maxDistance) {
        if (!valid || Minecraft.getMinecraft().world == null) {
            return null;
        }
        float winY = VIEWPORT.get(3) - mouseY - 1;
        Vec3d near = unproject(mouseX, winY, 0f);
        Vec3d far = unproject(mouseX, winY, 1f);
        if (near == null || far == null) {
            return null;
        }
        Vec3d dir = far.subtract(near).normalize();
        Vec3d start = new Vec3d(camX + near.x, camY + near.y, camZ + near.z);
        Vec3d end = start.addVector(dir.x * maxDistance, dir.y * maxDistance, dir.z * maxDistance);
        RayTraceResult r = Minecraft.getMinecraft().world.rayTraceBlocks(start, end, false, true, false);
        return r != null && r.typeOfHit == RayTraceResult.Type.BLOCK ? r : null;
    }

    /**
     * Para el Lasso: suma a out el bloque que toca el rayo bajo el píxel y, si depth > 1, los que le siguen
     * hasta depth - 1 bloques más atrás. Con includeNonSolid cuentan también hierba, flores y similares.
     */
    public static void pickInto(float mouseX, float mouseY, double maxDistance, int depth, boolean includeNonSolid,
            java.util.Set<Long> out) {
        if (!valid || Minecraft.getMinecraft().world == null) {
            return;
        }
        float winY = VIEWPORT.get(3) - mouseY - 1;
        Vec3d near = unproject(mouseX, winY, 0f);
        Vec3d far = unproject(mouseX, winY, 1f);
        if (near == null || far == null) {
            return;
        }
        Vec3d dir = far.subtract(near).normalize();
        Vec3d start = new Vec3d(camX + near.x, camY + near.y, camZ + near.z);
        Vec3d end = start.addVector(dir.x * maxDistance, dir.y * maxDistance, dir.z * maxDistance);
        net.minecraft.world.World w = Minecraft.getMinecraft().world;
        RayTraceResult r = w.rayTraceBlocks(start, end, false, !includeNonSolid, false);
        if (r == null || r.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        BlockPos last = r.getBlockPos();
        out.add(last.toLong());
        for (double d = 0.5; d <= depth - 1; d += 0.5) {
            BlockPos bp = new BlockPos(r.hitVec.x + dir.x * d, r.hitVec.y + dir.y * d, r.hitVec.z + dir.z * d);
            if (bp.equals(last)) {
                continue;
            }
            last = bp;
            net.minecraft.block.state.IBlockState s = w.getBlockState(bp);
            if (includeNonSolid ? s.getMaterial() != net.minecraft.block.material.Material.AIR : s.getMaterial().isSolid()) {
                out.add(bp.toLong());
            }
        }
    }

    /**
     * Proyecta un punto del mundo a la pantalla: {x, y, profundidad}, con el origen arriba a la izquierda
     * (como el ratón de ImGui). null si no hay cámara o el punto queda detrás de ella.
     */
    public static float[] project(double x, double y, double z) {
        if (!valid) {
            return null;
        }
        OUT.clear();
        if (!GLU.gluProject((float) (x - camX), (float) (y - camY), (float) (z - camZ), MODELVIEW, PROJECTION,
                VIEWPORT, OUT)) {
            return null;
        }
        float depth = OUT.get(2);
        if (depth < 0f || depth > 1f) {
            return null;
        }
        return new float[] {OUT.get(0), VIEWPORT.get(3) - OUT.get(1) - 1, depth};
    }

    /** El punto del mundo a distancia dist de la cámara bajo el píxel (mouseX, mouseY); null si no hay cámara. */
    public static Vec3d pointAt(float mouseX, float mouseY, double dist) {
        if (!valid) {
            return null;
        }
        float winY = VIEWPORT.get(3) - mouseY - 1;
        Vec3d near = unproject(mouseX, winY, 0f);
        Vec3d far = unproject(mouseX, winY, 1f);
        if (near == null || far == null) {
            return null;
        }
        Vec3d dir = far.subtract(near).normalize();
        Vec3d start = new Vec3d(camX + near.x, camY + near.y, camZ + near.z);
        return start.addVector(dir.x * dist, dir.y * dist, dir.z * dist);
    }

    /** El rayo bajo el píxel (mouseX, mouseY) en coordenadas del mundo: {origen x, y, z, dirección x, y, z}; null si no hay cámara. */
    public static double[] ray(float mouseX, float mouseY) {
        if (!valid) {
            return null;
        }
        float winY = VIEWPORT.get(3) - mouseY - 1;
        Vec3d near = unproject(mouseX, winY, 0f);
        Vec3d far = unproject(mouseX, winY, 1f);
        if (near == null || far == null) {
            return null;
        }
        Vec3d dir = far.subtract(near).normalize();
        return new double[] {camX + near.x, camY + near.y, camZ + near.z, dir.x, dir.y, dir.z};
    }

    private static Vec3d unproject(float x, float y, float z) {
        OUT.clear();
        if (!GLU.gluUnProject(x, y, z, MODELVIEW, PROJECTION, VIEWPORT, OUT)) {
            return null;
        }
        return new Vec3d(OUT.get(0), OUT.get(1), OUT.get(2));
    }
}
