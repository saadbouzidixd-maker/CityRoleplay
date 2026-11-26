package dev.cityroleplay.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class GuiHolder implements InventoryHolder {

    private final String id;
    private final Map<Integer, Consumer<GuiClick>> buttons = new HashMap<>();
    private Inventory inventory;

    public GuiHolder(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void button(int slot, Consumer<GuiClick> action) {
        buttons.put(slot, action);
    }

    public Consumer<GuiClick> button(int slot) {
        return buttons.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public record GuiClick(org.bukkit.entity.Player player, int slot,
                           org.bukkit.event.inventory.ClickType clickType,
                           org.bukkit.event.inventory.InventoryClickEvent event) {
    }
}
