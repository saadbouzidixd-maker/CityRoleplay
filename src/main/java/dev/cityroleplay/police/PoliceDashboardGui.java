package dev.cityroleplay.police;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.db.TransactionRecord;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.gui.Gui;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.util.Formats;
import dev.cityroleplay.util.ItemUtil;
import dev.cityroleplay.util.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class PoliceDashboardGui {

    private final ConfigManager config;
    private final Messages messages;
    private final ProfileService profiles;
    private final EconomyManager economy;
    private final PoliceService police;
    private final DutyZoneManager dutyZones;
    private final ShopGui shop;
    private final Database database;
    private final Scheduler scheduler;

    public PoliceDashboardGui(ConfigManager config, Messages messages, ProfileService profiles,
                              EconomyManager economy, PoliceService police, DutyZoneManager dutyZones,
                              ShopGui shop, Database database, Scheduler scheduler) {
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.economy = economy;
        this.police = police;
        this.dutyZones = dutyZones;
        this.shop = shop;
        this.database = database;
        this.scheduler = scheduler;
    }

    public void open(Player player) {
        if (!police.isPolice(player)) {
            messages.send(player, "police.not-officer");
            return;
        }
        PlayerProfile profile = profiles.getOrCreate(player);
        boolean onDuty = dutyZones.isInAnyZone(player);
        Gui gui = new Gui("police-dashboard", messages.raw("police.dashboard-title"), 3);
        gui.fill(Material.GRAY_STAINED_GLASS_PANE);

        gui.set(4, head(player, profile, onDuty));
        gui.set(10, item(Material.CLOCK, messages.raw("police.button-stats"),
                List.of(
                        messages.format("police.stats-kills", "value", profile.kills()),
                        messages.format("police.stats-arrests", "value", profile.arrests()),
                        messages.format("police.stats-duty", "value", Formats.duration(profile.dutySeconds())),
                        messages.format("police.stats-earnings", "value", economy.format(profile.policeEarnings())))));
        gui.set(12, item(Material.CHEST, messages.raw("police.button-shop"),
                List.of("&7Browse the police equipment store.")), click -> shop.open(player));
        gui.set(14, item(Material.BOOK, messages.raw("police.button-stats"),
                List.of("&7Open detailed statistics.")), click -> openStats(player));
        gui.set(16, item(Material.PAPER, messages.raw("police.button-activity"),
                List.of("&7Open recent police activity.")), click -> openActivity(player));
        gui.set(22, item(Material.BARRIER, messages.raw("police.button-close"), List.of()),
                click -> player.closeInventory());
        gui.open(player);
    }

    public void openStats(Player player) {
        PlayerProfile profile = profiles.getOrCreate(player);
        Gui gui = new Gui("police-stats", messages.raw("police.stats-header"), 3);
        gui.fill(Material.GRAY_STAINED_GLASS_PANE);
        gui.set(10, item(Material.IRON_SWORD, "&cCriminals Killed",
                List.of(messages.format("police.stats-kills", "value", profile.kills()))));
        gui.set(12, item(Material.IRON_BARS, "&6Criminals Arrested",
                List.of(messages.format("police.stats-arrests", "value", profile.arrests()))));
        gui.set(14, item(Material.CLOCK, "&eDuty Time",
                List.of(messages.format("police.stats-duty", "value", Formats.duration(profile.dutySeconds())))));
        gui.set(16, item(Material.GOLD_INGOT, "&6Police Earnings",
                List.of(messages.format("police.stats-earnings",
                        "value", economy.format(profile.policeEarnings())))));
        gui.set(20, item(Material.EMERALD, "&aBalance",
                List.of(messages.format("police.stats-balance",
                        "value", economy.format(economy.balance(player))))));
        gui.set(24, item(Material.WRITABLE_BOOK, "&bOfficer Since",
                List.of(messages.format("police.stats-since",
                        "value", Formats.date(profile.policeSince(), zone())))));
        gui.button(26, Material.BARRIER, messages.raw("police.button-close"), click -> player.closeInventory());
        gui.open(player);
    }

    public void openActivity(Player player) {
        int limit = Math.max(5, config.get(ConfigManager.CONFIG).getInt("activity-history-size", 15));
        database.recentTransactions(player.getUniqueId(), limit).whenComplete((records, error) ->
                scheduler.sync(() -> {
                    if (error != null) {
                        messages.send(player, "database-error");
                        return;
                    }
                    Gui gui = new Gui("police-activity", messages.raw("police.activity-header"), 6);
                    gui.fill(Material.GRAY_STAINED_GLASS_PANE);
                    if (records.isEmpty()) {
                        gui.set(22, item(Material.PAPER, messages.raw("police.activity-empty"), List.of()));
                    } else {
                        int slot = 0;
                        for (TransactionRecord record : records) {
                            if (slot >= 45) {
                                break;
                            }
                            gui.set(slot++, item(Material.PAPER,
                                    "&6" + prettify(record.type()),
                                    List.of(
                                            messages.format("police.activity-line",
                                                    "type", prettify(record.type()),
                                                    "amount", economy.format(record.amount()),
                                                    "date", Formats.dateTime(record.createdAt(), zone())),
                                            "&7" + record.detail())));
                        }
                    }
                    gui.button(49, Material.BARRIER, messages.raw("police.button-close"),
                            click -> player.closeInventory());
                    gui.open(player);
                }));
    }

    private ItemStack head(Player player, PlayerProfile profile, boolean onDuty) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        meta.displayName(ItemUtil.text("&6" + player.getName()));
        meta.lore(ItemUtil.textList(List.of(
                messages.format("police.stats-kills", "value", profile.kills()),
                messages.format("police.stats-arrests", "value", profile.arrests()),
                messages.format("police.stats-duty", "value", Formats.duration(profile.dutySeconds())),
                messages.format("police.stats-earnings", "value", economy.format(profile.policeEarnings())),
                messages.format("police.stats-balance", "value", economy.format(economy.balance(player))),
                messages.format("police.stats-since", "value", Formats.date(profile.policeSince(), zone())),
                onDuty ? "&a" + messages.raw("police.status-on-duty") : "&7" + messages.raw("police.status-off-duty")
        )));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemUtil.text(name));
        List<Component> lines = new ArrayList<>(ItemUtil.textList(lore));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(config.get(ConfigManager.CONFIG).getString("timezone", "UTC"));
        } catch (RuntimeException exception) {
            return ZoneId.of("UTC");
        }
    }

    private String prettify(String raw) {
        return raw == null ? "" : raw.replace('_', ' ');
    }
}
