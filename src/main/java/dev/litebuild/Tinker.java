package dev.litebuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockWall;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Tinker: clic derecho con la mano vacía cambia el estado del bloque. Escaleras, losas y bloques completos del mismo
 * material se convierten unos en otros con Ctrl; en muros, vallas y barras se activa o apaga el lado que apuntas; en lo
 * demás se cicla la propiedad más relevante (orientación, mitad, abierto, encendido...).
 */
public final class Tinker {
    /** Propiedades por orden de interés; la primera que tenga el bloque es la que cicla. */
    private static final List<String> ORDER = Arrays.asList("open", "facing", "axis", "half", "shape", "hinge", "powered",
            "lit", "hanging", "bites", "layers", "level", "age", "moisture", "rotation", "variant", "type", "color");

    private Tinker() {
    }

    /** El estado nuevo, o null si no hay nada que cambiar. shape = convertir entre bloque, escalera y losa en vez de ciclar. */
    public static IBlockState apply(IBlockState s, BlockPos pos, Vec3d hit, boolean shape) {
        if (shape) {
            IBlockState n = BlockVariants.nextShape(s);
            if (n != null) {
                return n;
            }
        }
        if (s.getBlock() instanceof BlockFence || s.getBlock() instanceof BlockPane || s.getBlock() instanceof BlockWall) {
            IBlockState n = toggleSide(s, hit.x - pos.getX() - 0.5, hit.z - pos.getZ() - 0.5);
            if (n != null) {
                return n;
            }
        }
        IProperty<?> p = pick(s);
        return p == null ? null : cycle(s, p);
    }

    /** Activa o apaga la conexión del lado hacia el que apunta el clic. */
    private static IBlockState toggleSide(IBlockState s, double dx, double dz) {
        EnumFacing side = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? EnumFacing.EAST : EnumFacing.WEST)
                : (dz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH);
        PropertyBool prop = side == EnumFacing.NORTH ? BlockFence.NORTH : side == EnumFacing.SOUTH ? BlockFence.SOUTH
                : side == EnumFacing.EAST ? BlockFence.EAST : BlockFence.WEST;
        if (!s.getPropertyKeys().contains(prop)) {
            return null;
        }
        return s.withProperty(prop, !s.getValue(prop));
    }

    private static IProperty<?> pick(IBlockState s) {
        List<IProperty<?>> props = new ArrayList<IProperty<?>>(s.getPropertyKeys());
        for (String want : ORDER) {
            for (IProperty<?> p : props) {
                if (p.getName().equals(want) && p.getAllowedValues().size() > 1) {
                    return p;
                }
            }
        }
        for (IProperty<?> p : props) {
            if (p.getAllowedValues().size() > 1) {
                return p;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState cycle(IBlockState s, IProperty p) {
        List<Comparable> values = new ArrayList<Comparable>(p.getAllowedValues());
        int i = values.indexOf(s.getValue(p));
        return EditEngine.withProperty(s, p, values.get((i + 1) % values.size()));
    }
}
