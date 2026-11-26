package dev.cityroleplay.gui;

import org.bukkit.plugin.java.JavaPlugin;

public final class GuiManager {

    private final JavaPlugin plugin;

    public GuiManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(new GuiListener(), plugin);
    }

    public Gui create(String id, String title, int rows) {
        return new Gui(id, title, rows);
    }
}
