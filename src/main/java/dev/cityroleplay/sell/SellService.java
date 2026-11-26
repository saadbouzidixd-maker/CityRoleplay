package dev.cityroleplay.sell;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.util.Formats;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

public final class SellService {

    private final ConfigManager config;
    private final Messages messages;
    private final EconomyManager economy;
    private final Set<Material> blacklist = new HashSet<>();
    private final Set<Material> whitelist = new HashSet<>();
    private final Map<Material, Double> prices = new HashMap<>();
    private final List<CustomPrice> customPrices = new ArrayList<>();

    public SellService(ConfigManager config, Messages messages, EconomyManager economy) {
        this.config = config;
        this.messages = messages;
        this.economy = economy;
        reload();
    }

    public void reload() {
        blacklist.clear();
        whitelist.clear();
        prices.clear();
        customPrices.clear();
        ConfigurationSection root = config.get(ConfigManager.SELL);
        for (String name : root.getStringList("sell.blacklist")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                blacklist.add(material);
            }
        }
        for (String name : root.getStringList("sell.whitelist")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                whitelist.add(material);
            }
        }
        ConfigurationSection priceRoot = root.getConfigurationSection("sell.prices");
        if (priceRoot != null) {
            for (String key : priceRoot.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) {
                    prices.put(material, Math.max(0, priceRoot.getDouble(key)));
                }
            }
        }
        List<Map<?, ?>> custom = root.getMapList("sell.custom-prices");
        for (Map<?, ?> map : custom) {
            Object materialRaw = map.get("material");
            Object modelRaw = map.get("custom-model-data");
            Object priceRaw = map.get("price");
            if (!(materialRaw instanceof String materialName) || !(modelRaw instanceof Number model)
                    || !(priceRaw instanceof Number price)) {
                continue;
            }
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                customPrices.add(new CustomPrice(material, model.intValue(), price.doubleValue()));
            }
        }
    }

    public boolean enabled() {
        return config.get(ConfigManager.SELL).getBoolean("sell.enabled", true);
    }

    public int rows() {
        return Math.max(3, Math.min(6, config.get(ConfigManager.SELL).getInt("sell.rows", 6)));
    }

    public String title() {
        return config.get(ConfigManager.SELL).getString("sell.title", "&8SELL ITEMS");
    }

    public OptionalDouble price(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return OptionalDouble.empty();
        }
        Material material = item.getType();
        if (blacklist.contains(material)) {
            return OptionalDouble.empty();
        }
        if (!whitelist.isEmpty() && !whitelist.contains(material)) {
            return OptionalDouble.empty();
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasCustomModelData()) {
                for (CustomPrice custom : customPrices) {
                    if (custom.material == material && custom.modelData == meta.getCustomModelData()) {
                        return OptionalDouble.of(Math.max(0, custom.price));
                    }
                }
            }
        }
        Double price = prices.get(material);
        return price == null ? OptionalDouble.empty() : OptionalDouble.of(Math.max(0, price));
    }

    public double total(Inventory inventory, int maxSlotExclusive) {
        double total = 0;
        for (int slot = 0; slot < Math.min(maxSlotExclusive, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            OptionalDouble price = price(item);
            if (price.isPresent()) {
                total += price.getAsDouble() * item.getAmount();
            }
        }
        return Formats.round(total, economy.precision());
    }

    public List<String> unsupported(Inventory inventory, int maxSlotExclusive) {
        List<String> names = new ArrayList<>();
        for (int slot = 0; slot < Math.min(maxSlotExclusive, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR && price(item).isEmpty()) {
                names.add(item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            }
        }
        return names;
    }

    public boolean sellContents(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            messages.send(player, "sell.empty");
            return false;
        }
        double total = 0;
        for (ItemStack item : items) {
            OptionalDouble price = price(item);
            if (price.isPresent()) {
                total += price.getAsDouble() * item.getAmount();
            }
        }
        total = Formats.round(total, economy.precision());
        if (total <= 0) {
            messages.send(player, "sell.empty");
            return false;
        }
        if (!economy.pay(player, total, "SELL", "Sold player items")) {
            messages.send(player, "sell.failed");
            return false;
        }
        messages.send(player, "sell.success",
                "count", Integer.toString(items.size()),
                "amount", economy.format(total));
        return true;
    }

    /**
     * Sells every supported item in the player's inventory, removing each item
     * only after the complete set has been validated. Payment is synchronous;
     * if the provider rejects it, all items are returned.
     */
    public boolean sellAll(Player player) {
        List<ItemStack> sold = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR && price(item).isPresent()) {
                sold.add(item.clone());
            }
        }
        if (sold.isEmpty()) {
            messages.send(player, "sell.no-items");
            return false;
        }
        for (ItemStack item : sold) {
            player.getInventory().removeItem(item);
        }
        if (!sellContents(player, sold)) {
            for (ItemStack item : sold) {
                if (!player.getInventory().addItem(item).isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            return false;
        }
        return true;
    }

    public String priceLine(ItemStack item, double unitPrice) {
        return messages.format("sell.price-lore", "price", economy.format(unitPrice))
                + " (" + item.getAmount() + ")";
    }

    public EconomyManager economy() {
        return economy;
    }

    private record CustomPrice(Material material, int modelData, double price) {
    }
}
