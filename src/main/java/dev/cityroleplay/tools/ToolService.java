package dev.cityroleplay.tools;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.police.PoliceService;
import dev.cityroleplay.util.ItemUtil;
import dev.cityroleplay.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class ToolService {

    private final JavaPlugin plugin;
    private final ToolRegistry registry;
    private final Database database;
    private final EconomyManager economy;
    private final PoliceService police;
    private final Messages messages;
    private final Scheduler scheduler;
    private final Set<String> serials = new HashSet<>();

    public ToolService(JavaPlugin plugin, ToolRegistry registry, Database database,
                       EconomyManager economy, PoliceService police, Messages messages,
                       Scheduler scheduler) {
        this.plugin = plugin;
        this.registry = registry;
        this.database = database;
        this.economy = economy;
        this.police = police;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    public void loadSerials() {
        serials.clear();
        serials.addAll(database.loadToolSerials().join());
        plugin.getLogger().info("Loaded " + serials.size() + " tool serials.");
    }

    public Optional<ValidatedTool> validate(Player player, ItemStack item) {
        Optional<ItemUtil.ToolRef> reference = ItemUtil.identify(item);
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        ItemUtil.ToolRef ref = reference.get();
        Optional<ToolDefinition> definition = registry.tool(ref.toolId());
        if (definition.isEmpty() || !definition.get().enabled()) {
            return Optional.empty();
        }
        if (!serials.contains(ref.serial())) {
            return Optional.empty();
        }
        if (ref.owner() != null && !ref.owner().equals(player.getUniqueId())) {
            messages.send(player, "police.tool-not-owner");
            return Optional.empty();
        }
        if (ref.owner() == null && definition.get().soulbound()) {
            return Optional.empty();
        }
        return Optional.of(new ValidatedTool(definition.get(), ref));
    }

    public boolean canUse(Player player, ToolDefinition tool) {
        if (player.hasPermission("city.admin")) {
            return true;
        }
        if (!police.isPolice(player)) {
            messages.send(player, "police.not-officer");
            return false;
        }
        if (tool.permission() != null && !tool.permission().isBlank()
                && !player.hasPermission(tool.permission())) {
            messages.send(player, "no-permission");
            return false;
        }
        return true;
    }

    public void give(Player player, ToolDefinition tool) {
        String serial = UUID.randomUUID().toString();
        ItemStack item = ItemUtil.createTool(tool.id(), serial, player.getUniqueId(), tool.material(),
                tool.name(), tool.lore(), tool.glow(), tool.soulbound(), tool.customModelData());
        ItemUtil.give(player, item);
        serials.add(serial);
        database.recordToolSerial(serial, tool.id(), player.getUniqueId());
    }

    public void purchase(Player player, ToolDefinition tool, Consumer<Boolean> callback) {
        if (!tool.enabled()) {
            messages.send(player, "police.purchase-disabled");
            callback.accept(false);
            return;
        }
        boolean permitted = tool.permission() == null || tool.permission().isBlank()
                || player.hasPermission(tool.permission());
        if (!player.hasPermission("city.admin") && !permitted) {
            messages.send(player, "police.purchase-permission");
            callback.accept(false);
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            messages.send(player, "police.purchase-inventory");
            callback.accept(false);
            return;
        }
        database.purchaseCount(player.getUniqueId(), tool.id()).whenComplete((count, error) -> {
            scheduler.sync(() -> {
                if (error != null) {
                    messages.send(player, "database-error");
                    callback.accept(false);
                    return;
                }
                if (tool.purchaseLimit() > 0 && count >= tool.purchaseLimit()) {
                    messages.send(player, "police.purchase-limit");
                    callback.accept(false);
                    return;
                }
                if (!economy.charge(player, tool.price(), "TOOL_PURCHASE", tool.id())) {
                    messages.send(player, "police.purchase-failed", "reason", "insufficient funds");
                    callback.accept(false);
                    return;
                }
                try {
                    String serial = UUID.randomUUID().toString();
                    ItemStack item = ItemUtil.createTool(tool.id(), serial, player.getUniqueId(),
                            tool.material(), tool.name(), tool.lore(), tool.glow(), tool.soulbound(),
                            tool.customModelData());
                    ItemUtil.give(player, item);
                    serials.add(serial);
                    database.recordToolSerial(serial, tool.id(), player.getUniqueId());
                    database.recordPurchase(player.getUniqueId(), tool.id(), tool.price());
                    messages.send(player, "police.purchase-success",
                            "item", tool.name(), "price", economy.format(tool.price()));
                    callback.accept(true);
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Could not deliver tool " + tool.id() + ": " + exception.getMessage());
                    economy.pay(player, tool.price(), "TOOL_REFUND", tool.id());
                    messages.send(player, "police.purchase-failed", "reason", "delivery error");
                    callback.accept(false);
                }
            });
        });
    }

    public record ValidatedTool(ToolDefinition definition, ItemUtil.ToolRef reference) {
    }
}
