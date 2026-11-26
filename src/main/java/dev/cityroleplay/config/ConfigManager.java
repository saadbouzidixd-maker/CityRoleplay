package dev.cityroleplay.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public final class ConfigManager {

    public static final String CONFIG = "config.yml";
    public static final String MESSAGES = "messages.yml";
    public static final String POLICE = "police.yml";
    public static final String CRIMINALS = "criminals.yml";
    public static final String ROBBERY = "robbery.yml";
    public static final String PRISON = "prison.yml";
    public static final String ECONOMY = "economy.yml";
    public static final String SHOP = "shop.yml";
    public static final String TOOLS = "tools.yml";
    public static final String DUTY_ZONES = "afk-zones.yml";
    public static final String SELL = "sell.yml";
    public static final String INTEGRATIONS = "integrations.yml";

    private static final String[] FILES = {
            CONFIG, MESSAGES, POLICE, CRIMINALS, ROBBERY, PRISON,
            ECONOMY, SHOP, TOOLS, DUTY_ZONES, SELL, INTEGRATIONS
    };

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> configs = new LinkedHashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create the plugin data folder.");
        }
        File logs = new File(plugin.getDataFolder(), "logs");
        if (!logs.exists() && !logs.mkdirs()) {
            plugin.getLogger().warning("Could not create the log folder.");
        }
        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) {
                plugin.saveResource(name, false);
            }
            configs.put(name, YamlConfiguration.loadConfiguration(file));
        }
    }

    public void reloadAll() {
        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            configs.put(name, YamlConfiguration.loadConfiguration(file));
        }
    }

    public YamlConfiguration get(String name) {
        YamlConfiguration configuration = configs.get(name);
        if (configuration == null) {
            File file = new File(plugin.getDataFolder(), name);
            configuration = YamlConfiguration.loadConfiguration(file);
            configs.put(name, configuration);
        }
        return configuration;
    }

    public void save(String name) {
        YamlConfiguration configuration = configs.get(name);
        if (configuration == null) {
            return;
        }
        try {
            configuration.save(new File(plugin.getDataFolder(), name));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, exception);
        }
    }
}
