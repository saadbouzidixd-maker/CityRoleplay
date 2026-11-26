package dev.cityroleplay.integration;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class IntegrationManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final LuckPermsHook luckPerms;
    private final EconomyManager economy;

    public IntegrationManager(JavaPlugin plugin, ConfigManager config,
                              LuckPermsHook luckPerms, EconomyManager economy) {
        this.plugin = plugin;
        this.config = config;
        this.luckPerms = luckPerms;
        this.economy = economy;
    }

    public void setup() {
        luckPerms.setup(config.get(ConfigManager.INTEGRATIONS).getBoolean("luckperms.enabled", true));
        economy.setup();
        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            plugin.getLogger().info("Essentials detected; Vault will supply its economy if installed.");
        }
    }

    public LuckPermsHook luckPerms() {
        return luckPerms;
    }

    public EconomyManager economy() {
        return economy;
    }

    public boolean vaultAvailable() {
        return economy.vaultActive();
    }
}
