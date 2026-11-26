package dev.cityroleplay.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsHook {

    private final JavaPlugin plugin;
    private LuckPermsBridge bridge;

    public LuckPermsHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup(boolean enabled) {
        if (!enabled) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("LuckPerms is not installed; the city.police permission fallback will be used.");
            return;
        }
        try {
            Class<?> type = Class.forName("dev.cityroleplay.integration.LuckPermsApiBridge");
            LuckPermsBridge candidate = (LuckPermsBridge) type.getConstructor(JavaPlugin.class)
                    .newInstance(plugin);
            candidate.setup();
            if (candidate.available()) {
                bridge = candidate;
                plugin.getLogger().info("LuckPerms integration enabled.");
            }
        } catch (Throwable throwable) {
            bridge = null;
            plugin.getLogger().warning("LuckPerms could not be hooked: " + throwable.getMessage());
        }
    }

    public boolean available() {
        return bridge != null && bridge.available();
    }

    public String primaryGroup(Player player) {
        return available() ? bridge.primaryGroup(player) : "default";
    }

    public boolean inGroup(Player player, String group) {
        return available() && bridge.inGroup(player, group);
    }

    public boolean groupExists(String group) {
        return available() && bridge.groupExists(group);
    }

    public CompletableFuture<Boolean> addToGroup(UUID uuid, String group) {
        return available() ? bridge.addToGroup(uuid, group) : CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> removeFromGroup(UUID uuid, String group) {
        return available() ? bridge.removeFromGroup(uuid, group) : CompletableFuture.completedFuture(false);
    }
}
