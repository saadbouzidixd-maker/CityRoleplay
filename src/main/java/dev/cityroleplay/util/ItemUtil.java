package dev.cityroleplay.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and identifies plugin items exclusively through PersistentDataContainer.
 * Display names and lore are presentation only and are never trusted.
 */
public final class ItemUtil {

    private static NamespacedKey toolIdKey;
    private static NamespacedKey toolSerialKey;
    private static NamespacedKey toolOwnerKey;
    private static NamespacedKey soulboundKey;
    private static NamespacedKey selectionKey;
    private static NamespacedKey arrowKey;

    private ItemUtil() {
    }

    public static void init(Plugin plugin) {
        toolIdKey = new NamespacedKey(plugin, "tool_id");
        toolSerialKey = new NamespacedKey(plugin, "tool_serial");
        toolOwnerKey = new NamespacedKey(plugin, "tool_owner");
        soulboundKey = new NamespacedKey(plugin, "soulbound");
        selectionKey = new NamespacedKey(plugin, "selection_wand");
        arrowKey = new NamespacedKey(plugin, "police_arrow");
    }

    public static Component text(String input) {
        if (input == null) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
    }

    public static List<Component> textList(List<String> input) {
        List<Component> lines = new ArrayList<>();
        if (input == null) {
            return lines;
        }
        for (String line : input) {
            lines.add(text(line).decoration(TextDecoration.ITALIC, false));
        }
        return lines;
    }

    public static ItemStack createTool(String toolId, String serial, UUID owner, Material material,
                                       String name, List<String> lore, boolean glow, boolean soulbound) {
        return createTool(toolId, serial, owner, material, name, lore, glow, soulbound, 0);
    }

    public static ItemStack createTool(String toolId, String serial, UUID owner, Material material,
                                       String name, List<String> lore, boolean glow, boolean soulbound,
                                       int customModelData) {
        ItemStack item = new ItemStack(material == null ? Material.STICK : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(textList(lore));
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        meta.getPersistentDataContainer().set(toolIdKey, PersistentDataType.STRING, toolId);
        meta.getPersistentDataContainer().set(toolSerialKey, PersistentDataType.STRING, serial);
        meta.getPersistentDataContainer().set(toolOwnerKey, PersistentDataType.STRING, owner.toString());
        if (soulbound) {
            meta.getPersistentDataContainer().set(soulboundKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    public static Optional<ToolRef> identify(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(toolIdKey, PersistentDataType.STRING);
        String serial = meta.getPersistentDataContainer().get(toolSerialKey, PersistentDataType.STRING);
        if (id == null || id.isBlank() || serial == null || serial.isBlank()) {
            return Optional.empty();
        }
        UUID owner = null;
        String ownerRaw = meta.getPersistentDataContainer().get(toolOwnerKey, PersistentDataType.STRING);
        if (ownerRaw != null) {
            try {
                owner = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ignored) {
                // A corrupt owner value invalidates ownership checks but not the tool id.
            }
        }
        return Optional.of(new ToolRef(id, serial, owner));
    }

    public static boolean isSoulbound(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer()
                .get(soulboundKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public static boolean isSelectionWand(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_HOE || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer()
                .get(selectionKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public static ItemStack selectionWand() {
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("&eCity Selection Wand").decoration(TextDecoration.ITALIC, false));
        meta.lore(textList(List.of("&7Left-click: position 1", "&7Right-click: position 2")));
        meta.getPersistentDataContainer().set(selectionKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static void markArrow(ItemStack item, String toolId, UUID owner) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING,
                toolId + "|" + (owner == null ? "" : owner));
        item.setItemMeta(meta);
    }

    public static Optional<String> arrowOwnerTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(arrowKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.split("\\|", 2)[0]);
    }

    public static void give(Player player, ItemStack item) {
        if (player == null || item == null) {
            return;
        }
        ItemStack copy = item.clone();
        player.getInventory().addItem(copy).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public record ToolRef(String toolId, String serial, UUID owner) {
    }
}
