package dev.litbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Estados de bloque como texto: "minecraft:stone[variant=granite]" (formato Sponge y WorldEdit) y como entrada de
 * paleta de las estructuras de Minecraft ({Name, Properties}). Las propiedades van ordenadas por nombre.
 */
final class BlockStates {
    private BlockStates() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(IProperty p, Comparable v) {
        return p.getName(v);
    }

    private static List<IProperty<?>> sortedKeys(IBlockState s) {
        List<IProperty<?>> keys = new ArrayList<IProperty<?>>(s.getPropertyKeys());
        Collections.sort(keys, new Comparator<IProperty<?>>() {
            @Override
            public int compare(IProperty<?> a, IProperty<?> b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return keys;
    }

    static String name(IBlockState s) {
        return String.valueOf(Block.REGISTRY.getNameForObject(s.getBlock()));
    }

    static String toText(IBlockState s) {
        StringBuilder sb = new StringBuilder(name(s));
        boolean first = true;
        for (IProperty<?> p : sortedKeys(s)) {
            sb.append(first ? '[' : ',').append(p.getName()).append('=').append(valueName(p, s.getValue(p)));
            first = false;
        }
        return first ? sb.toString() : sb.append(']').toString();
    }

    static NBTTagCompound toTag(IBlockState s) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", name(s));
        List<IProperty<?>> keys = sortedKeys(s);
        if (!keys.isEmpty()) {
            NBTTagCompound props = new NBTTagCompound();
            for (IProperty<?> p : keys) {
                props.setString(p.getName(), valueName(p, s.getValue(p)));
            }
            tag.setTag("Properties", props);
        }
        return tag;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState with(IBlockState s, String prop, String value) {
        for (IProperty p : s.getPropertyKeys()) {
            if (p.getName().equals(prop)) {
                com.google.common.base.Optional o = p.parseValue(value);
                return o.isPresent() ? s.withProperty(p, (Comparable) o.get()) : s;
            }
        }
        return s;
    }

    /** null si el bloque no existe en este juego (por ejemplo, de una versión más nueva). */
    static IBlockState fromTag(NBTTagCompound tag) {
        Block b = Block.getBlockFromName(tag.getString("Name"));
        if (b == null) {
            return null;
        }
        IBlockState s = b.getDefaultState();
        if (tag.hasKey("Properties", 10)) {
            NBTTagCompound props = tag.getCompoundTag("Properties");
            for (String key : props.getKeySet()) {
                s = with(s, key, props.getString(key));
            }
        }
        return s;
    }

    static IBlockState fromText(String text) {
        int br = text.indexOf('[');
        Block b = Block.getBlockFromName(br < 0 ? text : text.substring(0, br));
        if (b == null) {
            return null;
        }
        IBlockState s = b.getDefaultState();
        if (br >= 0 && text.endsWith("]")) {
            for (String pair : text.substring(br + 1, text.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    s = with(s, pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        return s;
    }
}
