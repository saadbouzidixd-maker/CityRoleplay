package dev.cityroleplay.tools;

import dev.cityroleplay.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<String, ToolBehavior> behaviors = new LinkedHashMap<>();
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    public ToolRegistry(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void registerBehavior(ToolBehavior behavior) {
        behaviors.put(behavior.id().toLowerCase(Locale.ROOT), behavior);
    }

    public void load() {
        tools.clear();
        YamlConfiguration yaml = config.get(ConfigManager.TOOLS);
        ConfigurationSection root = yaml.getConfigurationSection("tools");
        if (root == null) {
            plugin.getLogger().warning("tools.yml has no tools section.");
            return;
        }
        int fallbackSlot = 10;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String behavior = section.getString("behavior", "").toLowerCase(Locale.ROOT);
            if (!behaviors.containsKey(behavior)) {
                plugin.getLogger().warning("Tool '" + id + "' references unknown behavior '" + behavior + "' and was skipped.");
                continue;
            }
            Material material = Material.matchMaterial(section.getString("material", "STICK"));
            if (material == null) {
                plugin.getLogger().warning("Tool '" + id + "' has an invalid material and was skipped.");
                continue;
            }
            ToolDefinition definition = new ToolDefinition(
                    id.toLowerCase(Locale.ROOT),
                    section.getBoolean("enabled", true),
                    behavior,
                    material,
                    section.getString("name", id),
                    section.getStringList("lore"),
                    Math.max(0, section.getDouble("price", 0)),
                    section.getString("permission", ""),
                    Math.max(0, section.getInt("purchase-limit", 0)),
                    section.getInt("slot", fallbackSlot),
                    section.getBoolean("glow", false),
                    section.getBoolean("soulbound", true),
                    Math.max(0, section.getInt("custom-model-data", 0)),
                    section.getConfigurationSection("config")
            );
            tools.put(definition.id(), definition);
            fallbackSlot += 2;
        }
        plugin.getLogger().info("Loaded " + tools.size() + " tools and " + behaviors.size() + " behaviors.");
    }

    public Optional<ToolDefinition> tool(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(tools.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<ToolBehavior> behavior(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(behaviors.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<ToolDefinition> all() {
        return new ArrayList<>(tools.values());
    }

    public Collection<ToolBehavior> behaviors() {
        return new ArrayList<>(behaviors.values());
    }

    public List<ToolDefinition> enabled() {
        List<ToolDefinition> list = new ArrayList<>();
        for (ToolDefinition tool : tools.values()) {
            if (tool.enabled()) {
                list.add(tool);
            }
        }
        return list;
    }

    public Map<String, ToolDefinition> map() {
        return new HashMap<>(tools);
    }
}
