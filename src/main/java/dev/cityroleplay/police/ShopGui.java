package dev.cityroleplay.police;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.gui.Gui;
import dev.cityroleplay.tools.ToolDefinition;
import dev.cityroleplay.tools.ToolRegistry;
import dev.cityroleplay.tools.ToolService;
import dev.cityroleplay.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ShopGui {

    private final ConfigManager config;
    private final Messages messages;
    private final EconomyManager economy;
    private final ToolRegistry registry;
    private final ToolService tools;

    public ShopGui(ConfigManager config, Messages messages, EconomyManager economy,
                   ToolRegistry registry, ToolService tools) {
        this.config = config;
        this.messages = messages;
        this.economy = economy;
        this.registry = registry;
        this.tools = tools;
    }

    public void open(Player player) {
        int rows = Math.max(3, Math.min(6, config.get(ConfigManager.SHOP).getInt("shop.rows", 6)));
        Gui gui = new Gui("police-shop", config.get(ConfigManager.SHOP)
                .getString("shop.title", "&8POLICE SHOP"), rows);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);

        for (ToolDefinition tool : registry.enabled()) {
            boolean permitted = player.hasPermission("city.admin")
                    || tool.permission().isBlank()
                    || player.hasPermission(tool.permission());
            Material material = permitted ? tool.material() : Material.BARRIER;
            String name = permitted ? tool.name() : "&c" + strip(tool.name());
            List<String> lore = new ArrayList<>(tool.lore());
            if (permitted) {
                lore.add("");
                lore.add(messages.format("police.shop-price", "price", economy.format(tool.price())));
                if (tool.purchaseLimit() > 0) {
                    lore.add(messages.format("police.shop-limit",
                            "limit", Integer.toString(tool.purchaseLimit())));
                }
                lore.add(messages.format("police.shop-click"));
            } else {
                lore.add(messages.format("police.shop-locked"));
            }
            gui.set(tool.slot(), display(material, name, lore), click -> {
                if (!permitted) {
                    messages.send(player, "police.purchase-permission");
                    return;
                }
                confirm(player, tool);
            });
        }
        gui.button(49, Material.BARRIER, messages.raw("police.button-close"), click -> player.closeInventory());
        gui.open(player);
    }

    private void confirm(Player player, ToolDefinition tool) {
        Gui gui = new Gui("police-shop-confirm", messages.raw("police.purchase-confirm-title"), 3);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        List<String> lore = new ArrayList<>(tool.lore());
        lore.add("");
        lore.add(messages.format("police.shop-price", "price", economy.format(tool.price())));
        gui.set(11, display(tool.material(), tool.name(), lore));
        gui.set(15, display(Material.LIME_CONCRETE, messages.raw("police.purchase-confirm"), List.of()), click -> {
            player.closeInventory();
            tools.purchase(player, tool, success -> {
                if (!success) {
                    open(player);
                }
            });
        });
        gui.set(13, display(Material.RED_CONCRETE, messages.raw("police.purchase-cancel"), List.of()),
                click -> player.closeInventory());
        gui.open(player);
    }

    private ItemStack display(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemUtil.text(name));
        meta.lore(ItemUtil.textList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private String strip(String input) {
        return input == null ? "" : input.replaceAll("&[0-9a-fk-or]", "");
    }
}
