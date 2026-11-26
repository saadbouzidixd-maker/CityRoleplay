package dev.cityroleplay.sell;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.gui.Gui;
import dev.cityroleplay.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Consumer;

public final class SellGui implements Listener {

    private static final int ROWS = 6;
    private static final int ITEM_SLOTS = 45;
    private static final int SELL_ALL_SLOT = 45;
    private static final int CONFIRM_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final JavaPlugin plugin;
    private final SellService sellService;
    private final Messages messages;

    public SellGui(JavaPlugin plugin, SellService sellService, Messages messages) {
        this.plugin = plugin;
        this.sellService = sellService;
        this.messages = messages;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        if (!sellService.enabled()) {
            messages.send(player, "sell.disabled");
            return;
        }
        Holder holder = new Holder(sellService, messages, player);
        Inventory inventory = Bukkit.createInventory(holder, ROWS * 9,
                ItemUtil.text(sellService.title()));
        holder.inventory = inventory;

        for (int slot = ITEM_SLOTS; slot < ROWS * 9; slot++) {
            if (slot != SELL_ALL_SLOT && slot != CONFIRM_SLOT && slot != CLOSE_SLOT) {
                inventory.setItem(slot, filler(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        holder.buttons.put(SELL_ALL_SLOT, this::sellAll);
        holder.buttons.put(CONFIRM_SLOT, this::confirm);
        holder.buttons.put(CLOSE_SLOT, Player::closeInventory);
        inventory.setItem(SELL_ALL_SLOT, button(Material.GOLD_INGOT, messages.raw("sell.all-button")));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, messages.raw("sell.cancel")));
        refreshTotal(holder);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        int rawSlot = event.getRawSlot();

        if (rawSlot >= top.getSize()) {
            if (event.isShiftClick() && event.getCurrentItem() != null) {
                event.setCancelled(true);
                int free = firstFreeItemSlot(top);
                if (free < 0) {
                    return;
                }
                ItemStack moving = event.getCurrentItem().clone();
                clicker.getInventory().setItem(event.getSlot(), null);
                top.setItem(free, moving);
                refreshTotal(holder);
            }
            return;
        }

        if (rawSlot >= ITEM_SLOTS) {
            event.setCancelled(true);
            Consumer<Player> action = holder.buttons.get(rawSlot);
            if (action != null) {
                action.accept(clicker);
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> refreshTotal(holder));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= ITEM_SLOTS && slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (holder.session.completed) {
            return;
        }
        Player player = holder.player;
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            holder.inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }

    private void confirm(Player player) {
        Holder holder = holder(player);
        if (holder == null || holder.session.processing) {
            return;
        }
        List<String> invalid = sellService.unsupported(holder.inventory, ITEM_SLOTS);
        if (!invalid.isEmpty()) {
            messages.send(player, "sell.unsupported", "items", String.join(", ", invalid));
            return;
        }
        double total = sellService.total(holder.inventory, ITEM_SLOTS);
        if (total <= 0) {
            messages.send(player, "sell.empty");
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
                holder.inventory.setItem(slot, null);
            }
        }
        holder.session.processing = true;
        if (sellService.sellContents(player, items)) {
            holder.session.completed = true;
            player.closeInventory();
        } else {
            holder.session.processing = false;
            for (ItemStack item : items) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
            player.closeInventory();
        }
    }

    private void sellAll(Player player) {
        Holder holder = holder(player);
        if (holder == null || holder.session.processing) {
            return;
        }
        double total = 0;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            OptionalDouble price = sellService.price(item);
            if (price.isPresent()) {
                total += price.getAsDouble() * item.getAmount();
                count++;
            }
        }
        if (count == 0) {
            messages.send(player, "sell.no-items");
            return;
        }
        double finalTotal = total;
        Gui confirm = new Gui("sell-all-confirm", messages.raw("sell.title"), 3);
        confirm.fill(Material.BLACK_STAINED_GLASS_PANE);
        confirm.set(13, button(Material.LIME_CONCRETE, messages.raw("sell.confirm"),
                List.of(messages.format("sell.total", "amount", sellService.economy().format(finalTotal)))), click -> {
            click.player().closeInventory();
            sellService.sellAll(click.player());
        });
        confirm.set(15, button(Material.RED_CONCRETE, messages.raw("sell.cancel")),
                click -> click.player().closeInventory());
        confirm.open(player);
    }

    private Holder holder(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                && holder.player.getUniqueId().equals(player.getUniqueId())) {
            return holder;
        }
        return null;
    }

    private void refreshTotal(Holder holder) {
        double total = sellService.total(holder.inventory, ITEM_SLOTS);
        List<String> lore = new ArrayList<>();
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            sellService.price(item).ifPresent(price ->
                    lore.add(messages.format("sell.price-lore", "price", sellService.economy().format(price))
                            + " &8x" + item.getAmount()));
        }
        lore.add(messages.format("sell.total", "amount", sellService.economy().format(total)));
        holder.inventory.setItem(CONFIRM_SLOT, button(Material.LIME_CONCRETE, messages.raw("sell.confirm"), lore));
    }

    private int firstFreeItemSlot(Inventory top) {
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            if (top.getItem(slot) == null || top.getItem(slot).getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name) {
        return button(material, name, List.of());
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemUtil.text(name));
        meta.lore(ItemUtil.textList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements InventoryHolder {
        private final SellService sellService;
        private final Messages messages;
        private final Player player;
        private final Session session;
        private final Map<Integer, Consumer<Player>> buttons = new HashMap<>();
        private Inventory inventory;

        private Holder(SellService sellService, Messages messages, Player player) {
            this.sellService = sellService;
            this.messages = messages;
            this.player = player;
            this.session = new Session();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class Session {
        private boolean completed;
        private boolean processing;
    }
}
