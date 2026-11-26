package dev.cityroleplay.tools;

import dev.cityroleplay.util.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record ToolContext(
        Player player,
        ItemStack item,
        ToolDefinition tool,
        ItemUtil.ToolRef reference
) {
}
