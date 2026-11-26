package dev.cityroleplay.tools;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record ToolDefinition(
        String id,
        boolean enabled,
        String behavior,
        Material material,
        String name,
        List<String> lore,
        double price,
        String permission,
        int purchaseLimit,
        int slot,
        boolean glow,
        boolean soulbound,
        int customModelData,
        ConfigurationSection config
) {

    public double configDouble(String key, double fallback) {
        return config == null ? fallback : config.getDouble(key, fallback);
    }

    public int configInt(String key, int fallback) {
        return config == null ? fallback : config.getInt(key, fallback);
    }

    public String configString(String key, String fallback) {
        return config == null ? fallback : config.getString(key, fallback);
    }

    public boolean configBoolean(String key, boolean fallback) {
        return config == null ? fallback : config.getBoolean(key, fallback);
    }
}
