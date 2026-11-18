package dev.litebuild;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Hotbar Swapper: hotbars guardadas sin límite (config/litebuild/hotbars.dat), compartidas entre mundos. */
public final class Hotbars {
    public static final class Saved {
        public String name;
        public final ItemStack[] items = new ItemStack[9];
    }

    public static final List<Saved> list = new ArrayList<Saved>();
    private static boolean loaded;

    private Hotbars() {
    }

    private static File file() {
        File d = new File(Minecraft.getMinecraft().mcDataDir, "config/litebuild");
        d.mkdirs();
        return new File(d, "hotbars.dat");
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = file();
        if (!f.isFile()) {
            return;
        }
        try {
            NBTTagList l = CompressedStreamTools.read(f).getTagList("hotbars", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound c = l.getCompoundTagAt(i);
                Saved s = new Saved();
                s.name = c.getString("name");
                NBTTagList items = c.getTagList("items", 10);
                for (int k = 0; k < 9; k++) {
                    s.items[k] = k < items.tagCount() ? new ItemStack(items.getCompoundTagAt(k)) : ItemStack.EMPTY;
                }
                list.add(s);
            }
        } catch (IOException | RuntimeException e) {
            LiteModLiteBuild.LOG.warn("No se pudieron leer las hotbars", e);
        }
    }

    private static void save() {
        NBTTagList l = new NBTTagList();
        for (Saved s : list) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("name", s.name);
            NBTTagList items = new NBTTagList();
            for (ItemStack st : s.items) {
                items.appendTag(st.writeToNBT(new NBTTagCompound()));
            }
            c.setTag("items", items);
            l.appendTag(c);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("hotbars", l);
        try {
            CompressedStreamTools.write(root, file());
        } catch (IOException e) {
            LiteModLiteBuild.LOG.warn("No se pudieron guardar las hotbars", e);
        }
    }

    /** Guarda la hotbar actual con un nombre. */
    public static void saveCurrent(String name) {
        Saved s = new Saved();
        s.name = name.trim().isEmpty() ? "Hotbar " + (list.size() + 1) : name.trim();
        for (int i = 0; i < 9; i++) {
            s.items[i] = Minecraft.getMinecraft().player.inventory.mainInventory.get(i).copy();
        }
        list.add(s);
        save();
    }

    public static void delete(int index) {
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save();
        }
    }

    public static void load(Saved s) {
        for (int i = 0; i < 9; i++) {
            setSlot(i, s.items[i].copy());
        }
    }

    /** Pone los bloques de states en la hotbar (sustituye lo que hubiera en esas ranuras). */
    public static void fill(List<IBlockState> states) {
        for (int i = 0; i < Math.min(9, states.size()); i++) {
            IBlockState st = states.get(i);
            Item item = Item.getItemFromBlock(st.getBlock());
            if (item != null && item != net.minecraft.init.Items.AIR) {
                setSlot(i, new ItemStack(item, 1, st.getBlock().damageDropped(st)));
            }
        }
        EditEngine.status = "Hotbar: " + Math.min(9, states.size()) + " bloques copiados";
    }

    /** Pone un solo bloque en la ranura de la mano. */
    public static void giveHeld(IBlockState st) {
        Item item = Item.getItemFromBlock(st.getBlock());
        if (item != null && item != net.minecraft.init.Items.AIR) {
            setSlot(Minecraft.getMinecraft().player.inventory.currentItem, new ItemStack(item, 1, st.getBlock().damageDropped(st)));
        }
    }

    private static void setSlot(int i, ItemStack st) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.player.inventory.setInventorySlotContents(i, st);
        if (mc.playerController != null && mc.player.isCreative()) {
            mc.playerController.sendSlotPacket(st, 36 + i);
        }
    }
}
