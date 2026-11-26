package dev.cityroleplay.gui;

import dev.cityroleplay.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public final class Gui {

    private final GuiHolder holder;
    private final Inventory inventory;

    public Gui(String id, String title, int rows) {
        int size = Math.max(9, Math.min(54, rows * 9));
        this.holder = new GuiHolder(id);
        this.inventory = Bukkit.createInventory(holder, size, ItemUtil.text(title));
        holder.inventory(inventory);
    }

    public Gui fill(Material material) {
        ItemStack filler = new ItemStack(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
        return this;
    }

    public Gui set(int slot, ItemStack item, Consumer<GuiHolder.GuiClick> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return this;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            holder.button(slot, action);
        }
        return this;
    }

    public Gui set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    public Gui button(int slot, Material material, String name, Consumer<GuiHolder.GuiClick> action) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(ItemUtil.text(name));
        item.setItemMeta(meta);
        return set(slot, item, action);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public GuiHolder holder() {
        return holder;
    }

    public Inventory inventory() {
        return inventory;
    }
}
