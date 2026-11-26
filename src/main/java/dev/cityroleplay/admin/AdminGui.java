package dev.cityroleplay.admin;

import dev.cityroleplay.CityRoleplay;
import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.db.TransactionRecord;
import dev.cityroleplay.gui.Gui;
import dev.cityroleplay.police.DutyZone;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.robbery.RobberyZone;
import dev.cityroleplay.tools.ToolBehavior;
import dev.cityroleplay.tools.ToolDefinition;
import dev.cityroleplay.util.Cuboid;
import dev.cityroleplay.util.Formats;
import dev.cityroleplay.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class AdminGui {

    private final CityRoleplay plugin;
    private final Messages messages;
    private final ConfigManager config;

    public AdminGui(CityRoleplay plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.config = plugin.config();
    }

    public void openMain(Player player) {
        if (!allowed(player)) {
            return;
        }
        Gui gui = new Gui("admin-main", messages.raw("admin.title"), 6);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        gui.set(20, item(Material.CLOCK, messages.raw("admin.zones"),
                List.of("&7Create, edit and remove", "&7paid police duty zones.")), click -> openZones(player));
        gui.set(22, item(Material.CHEST, messages.raw("admin.robberies"),
                List.of("&7Create and tune robbery", "&7capture locations.")), click -> openRobberies(player));
        gui.set(24, item(Material.STICK, messages.raw("admin.tools"),
                List.of("&7Manage the dynamic", "&7police tool registry.")), click -> openTools(player));
        gui.set(29, item(Material.PLAYER_HEAD, messages.raw("admin.records"),
                List.of("&7View police, criminal", "&7and prison records.")), click -> openRecords(player, "all"));
        gui.set(31, item(Material.PAPER, messages.raw("admin.transactions"),
                List.of("&7Inspect recent economy", "&7transactions.")), click -> openTransactions(player));
        gui.set(33, item(Material.IRON_DOOR, messages.raw("admin.prison"),
                List.of("&7View and release", "&7current prisoners.")), click -> openPrison(player));
        gui.set(38, item(Material.COMPARATOR, messages.raw("admin.integrations"),
                List.of("&7Vault, LuckPerms and", "&7Essentials status.")), click -> openIntegrations(player));
        gui.set(40, item(Material.LIME_DYE, messages.raw("admin.reload"),
                List.of("&7Reload all configuration", "&7and manager state.")), click -> {
            plugin.reloadEverything();
            messages.send(player, "admin.reloaded");
            openMain(player);
        });
        gui.set(49, item(Material.BARRIER, messages.raw("admin.close"), List.of()),
                click -> player.closeInventory());
        gui.open(player);
    }

    public void openZones(Player player) {
        if (!allowed(player)) {
            return;
        }
        Gui gui = new Gui("admin-zones", messages.raw("admin.zones-title"), 6);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (DutyZone zone : plugin.dutyZones().zones()) {
            if (slot >= 45) {
                break;
            }
            gui.set(slot++, item(Material.CLOCK, "&e" + zone.displayName(), List.of(
                    "&7ID: &f" + zone.id(),
                    "&7Region: &f" + zone.cuboid().locationString(),
                    "&7Reward: &6" + plugin.economy().format(zone.reward()),
                    "&7Interval: &f" + Formats.duration(zone.intervalSeconds()),
                    "&7Group: &f" + (zone.group().isBlank() ? "any police" : zone.group()),
                    "&7Permission: &f" + (zone.permission().isBlank() ? "none" : zone.permission()),
                    "&7Click to edit."
            )), click -> openZoneEdit(player, zone));
        }
        if (slot == 0) {
            gui.set(22, item(Material.PAPER, messages.raw("duty.zone-none"), List.of()));
        }
        gui.set(45, item(Material.LIME_CONCRETE, messages.raw("admin.create"),
                List.of("&7Use your selection wand", "&7then create a new zone.")), click -> createZone(player));
        gui.button(49, Material.BARRIER, messages.raw("admin.back"), click -> openMain(player));
        gui.open(player);
    }

    private void openZoneEdit(Player player, DutyZone zone) {
        Gui gui = new Gui("admin-zone-edit", "&8ZONE: " + zone.id(), 4);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        gui.set(10, item(Material.GOLD_INGOT, messages.raw("admin.edit-reward"),
                List.of("&7Current: &6" + plugin.economy().format(zone.reward()))), click ->
                askNumber(player, "admin.prompt-reward", value -> {
                    DutyZone updated = new DutyZone(zone.id(), zone.displayName(), zone.cuboid(),
                            value, zone.intervalSeconds(), zone.permission(), zone.group(),
                            zone.enabled(), zone.requireMovement(), zone.movementTimeoutSeconds());
                    plugin.dutyZones().replaceZone(updated);
                    messages.send(player, "admin.saved");
                    openZoneEdit(player, updated);
                }));
        gui.set(12, item(Material.CLOCK, messages.raw("admin.edit-interval"),
                List.of("&7Current: &f" + Formats.duration(zone.intervalSeconds()))), click ->
                askNumber(player, "admin.prompt-interval", value -> {
                    DutyZone updated = new DutyZone(zone.id(), zone.displayName(), zone.cuboid(),
                            zone.reward(), Math.max(1, value.longValue()), zone.permission(), zone.group(),
                            zone.enabled(), zone.requireMovement(), zone.movementTimeoutSeconds());
                    plugin.dutyZones().replaceZone(updated);
                    messages.send(player, "admin.saved");
                    openZoneEdit(player, updated);
                }));
        gui.set(14, item(zone.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                messages.raw("admin.toggle"), List.of(zone.enabled()
                        ? messages.raw("admin.enabled") : messages.raw("admin.disabled"))), click -> {
            DutyZone updated = new DutyZone(zone.id(), zone.displayName(), zone.cuboid(),
                    zone.reward(), zone.intervalSeconds(), zone.permission(), zone.group(),
                    !zone.enabled(), zone.requireMovement(), zone.movementTimeoutSeconds());
            plugin.dutyZones().replaceZone(updated);
            openZoneEdit(player, updated);
        });
        gui.set(22, item(Material.RED_CONCRETE, messages.raw("admin.delete"),
                List.of("&7Permanently remove this zone.")), click -> {
            plugin.dutyZones().removeZone(zone.id());
            messages.send(player, "duty.zone-removed", "zone", zone.displayName());
            openZones(player);
        });
        gui.button(26, Material.BARRIER, messages.raw("admin.back"), click -> openZones(player));
        gui.open(player);
    }

    private void createZone(Player player) {
        if (plugin.selections().selection(player).isEmpty()) {
            messages.send(player, "admin.selection-missing");
            return;
        }
        plugin.prompts().ask(player, "admin.prompt-id", id -> {
            if (!validId(id)) {
                messages.send(player, "admin.cancelled");
                openZones(player);
                return;
            }
            plugin.prompts().ask(player, "admin.prompt-name", name -> {
                Cuboid cuboid = plugin.selections().selection(player).orElse(null);
                if (cuboid == null) {
                    messages.send(player, "admin.selection-missing");
                    openZones(player);
                    return;
                }
                askNumber(player, "admin.prompt-reward", reward -> askNumber(player, "admin.prompt-interval", interval -> {
                    DutyZone zone = new DutyZone(id.toLowerCase(Locale.ROOT), name, cuboid,
                            reward, Math.max(1, interval.longValue()), "", "", true, true, 90);
                    if (!plugin.dutyZones().addZone(zone)) {
                        messages.send(player, "duty.zone-overlap", "zone", "an existing zone");
                        openZones(player);
                        return;
                    }
                    messages.send(player, "duty.zone-created", "zone", name);
                    openZones(player);
                }));
            });
        });
    }

    public void openRobberies(Player player) {
        if (!allowed(player)) {
            return;
        }
        Gui gui = new Gui("admin-robberies", messages.raw("admin.robbery-title"), 6);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (RobberyZone zone : plugin.robberies().zones()) {
            if (slot >= 45) {
                break;
            }
            gui.set(slot++, item(Material.CHEST, "&c" + zone.displayName(), List.of(
                    "&7ID: &f" + zone.id(),
                    "&7Region: &f" + zone.cuboid().locationString(),
                    "&7Duration: &f" + Formats.duration(zone.durationSeconds()),
                    "&7Reward: &6" + plugin.economy().format(zone.reward()),
                    "&7Cooldown: &f" + Formats.duration(zone.cooldownSeconds()),
                    "&7Status: " + (zone.enabled() ? "&aEnabled" : "&cDisabled"),
                    "&7Click to edit."
            )), click -> openRobberyEdit(player, zone));
        }
        if (slot == 0) {
            gui.set(22, item(Material.PAPER, messages.raw("robbery.none"), List.of()));
        }
        gui.set(45, item(Material.LIME_CONCRETE, messages.raw("admin.create"),
                List.of("&7Use your selection wand", "&7then create a robbery zone.")), click -> createRobbery(player));
        gui.button(49, Material.BARRIER, messages.raw("admin.back"), click -> openMain(player));
        gui.open(player);
    }

    private void openRobberyEdit(Player player, RobberyZone zone) {
        Gui gui = new Gui("admin-robbery-edit", "&8ROBBERY: " + zone.id(), 4);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        gui.set(10, item(Material.CLOCK, messages.raw("admin.edit-duration"),
                List.of("&7Current: &f" + Formats.duration(zone.durationSeconds()))), click ->
                askNumber(player, "admin.prompt-duration", value -> {
                    RobberyZone updated = copy(zone, Math.max(5, value.longValue()), zone.reward(),
                            zone.cooldownSeconds(), zone.enabled());
                    plugin.robberies().replaceZone(updated);
                    messages.send(player, "admin.saved");
                    openRobberyEdit(player, updated);
                }));
        gui.set(12, item(Material.GOLD_INGOT, messages.raw("admin.edit-reward"),
                List.of("&7Current: &6" + plugin.economy().format(zone.reward()))), click ->
                askNumber(player, "admin.prompt-reward", value -> {
                    RobberyZone updated = copy(zone, zone.durationSeconds(), value,
                            zone.cooldownSeconds(), zone.enabled());
                    plugin.robberies().replaceZone(updated);
                    messages.send(player, "admin.saved");
                    openRobberyEdit(player, updated);
                }));
        gui.set(14, item(Material.COMPASS, messages.raw("admin.edit-cooldown"),
                List.of("&7Current: &f" + Formats.duration(zone.cooldownSeconds()))), click ->
                askNumber(player, "admin.prompt-cooldown", value -> {
                    RobberyZone updated = copy(zone, zone.durationSeconds(), zone.reward(),
                            Math.max(0, value.longValue()), zone.enabled());
                    plugin.robberies().replaceZone(updated);
                    messages.send(player, "admin.saved");
                    openRobberyEdit(player, updated);
                }));
        gui.set(16, item(zone.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                messages.raw("admin.toggle"), List.of(zone.enabled()
                        ? messages.raw("admin.enabled") : messages.raw("admin.disabled"))), click -> {
            RobberyZone updated = copy(zone, zone.durationSeconds(), zone.reward(),
                    zone.cooldownSeconds(), !zone.enabled());
            plugin.robberies().replaceZone(updated);
            openRobberyEdit(player, updated);
        });
        gui.set(22, item(Material.RED_CONCRETE, messages.raw("admin.delete"),
                List.of("&7Permanently remove this location.")), click -> {
            plugin.robberies().removeZone(zone.id());
            messages.send(player, "robbery.removed", "zone", zone.displayName());
            openRobberies(player);
        });
        gui.button(26, Material.BARRIER, messages.raw("admin.back"), click -> openRobberies(player));
        gui.open(player);
    }

    private RobberyZone copy(RobberyZone zone, long duration, double reward, long cooldown, boolean enabled) {
        return new RobberyZone(zone.id(), zone.displayName(), zone.cuboid(), duration, reward,
                cooldown, enabled, zone.minOnlinePlayers(), zone.maxRobbers(), zone.policeNotify());
    }

    private void createRobbery(Player player) {
        if (plugin.selections().selection(player).isEmpty()) {
            messages.send(player, "admin.selection-missing");
            return;
        }
        plugin.prompts().ask(player, "admin.prompt-id", id -> {
            if (!validId(id)) {
                openRobberies(player);
                return;
            }
            plugin.prompts().ask(player, "admin.prompt-name", name -> {
                Cuboid cuboid = plugin.selections().selection(player).orElse(null);
                if (cuboid == null) {
                    messages.send(player, "admin.selection-missing");
                    openRobberies(player);
                    return;
                }
                askNumber(player, "admin.prompt-duration", duration ->
                        askNumber(player, "admin.prompt-reward", reward ->
                                askNumber(player, "admin.prompt-cooldown", cooldown -> {
                                    RobberyZone zone = new RobberyZone(id.toLowerCase(Locale.ROOT), name,
                                            cuboid, Math.max(5, duration.longValue()), reward,
                                            Math.max(0, cooldown.longValue()), true, 1, 1, true);
                                    if (!plugin.robberies().addZone(zone)) {
                                        messages.send(player, "robbery.overlap", "zone", "an existing zone");
                                        openRobberies(player);
                                        return;
                                    }
                                    messages.send(player, "robbery.created", "zone", name);
                                    openRobberies(player);
                                })));
            });
        });
    }

    public void openTools(Player player) {
        if (!allowed(player)) {
            return;
        }
        Gui gui = new Gui("admin-tools", messages.raw("admin.tools-title"), 6);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (ToolDefinition tool : plugin.toolRegistry().all()) {
            if (slot >= 45) {
                break;
            }
            gui.set(slot++, item(tool.material(), tool.name(), List.of(
                    "&7ID: &f" + tool.id(),
                    "&7Behavior: &f" + tool.behavior(),
                    "&7Price: &6" + plugin.economy().format(tool.price()),
                    "&7Permission: &f" + (tool.permission().isBlank() ? "none" : tool.permission()),
                    "&7Limit: &f" + (tool.purchaseLimit() == 0 ? "unlimited" : String.valueOf(tool.purchaseLimit())),
                    "&7Status: " + (tool.enabled() ? "&aEnabled" : "&cDisabled"),
                    "&7Click to edit."
            )), click -> openToolEdit(player, tool));
        }
        gui.set(45, item(Material.LIME_CONCRETE, messages.raw("admin.create"),
                List.of("&7Register a new tool with", "&7a real behavior implementation.")), click -> openBehaviorPicker(player));
        gui.button(49, Material.BARRIER, messages.raw("admin.back"), click -> openMain(player));
        gui.open(player);
    }

    private void openBehaviorPicker(Player player) {
        Gui gui = new Gui("admin-behaviors", messages.raw("admin.behavior-title"), 3);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        int slot = 10;
        for (ToolBehavior behavior : plugin.toolRegistry().behaviors()) {
            gui.set(slot++, item(Material.PAPER, "&e" + behavior.id(), List.of(
                    "&7Click to build a tool with", "&7this behavior.")), click -> createTool(player, behavior.id()));
            if (slot > 16) {
                break;
            }
        }
        gui.button(26, Material.BARRIER, messages.raw("admin.back"), click -> openTools(player));
        gui.open(player);
    }

    private void createTool(Player player, String behavior) {
        plugin.prompts().ask(player, "admin.prompt-id", id -> {
            if (!validId(id)) {
                openTools(player);
                return;
            }
            plugin.prompts().ask(player, "admin.prompt-material", materialRaw -> {
                Material material = Material.matchMaterial(materialRaw.toUpperCase(Locale.ROOT));
                if (material == null) {
                    messages.send(player, "admin.cancelled");
                    openTools(player);
                    return;
                }
                askNumber(player, "admin.prompt-price", price -> {
                    plugin.prompts().ask(player, "admin.prompt-permission", permission -> {
                        String path = "tools." + id.toLowerCase(Locale.ROOT) + ".";
                        var yaml = config.get(ConfigManager.TOOLS);
                        int slot = 10;
                        for (ToolDefinition existing : plugin.toolRegistry().all()) {
                            slot = Math.max(slot, existing.slot() + 2);
                        }
                        yaml.set(path + "enabled", true);
                        yaml.set(path + "behavior", behavior);
                        yaml.set(path + "material", material.name());
                        yaml.set(path + "name", "&e" + id);
                        yaml.set(path + "lore", List.of("&7Configured police tool."));
                        yaml.set(path + "price", price);
                        yaml.set(path + "permission", permission.equalsIgnoreCase("none") ? "" : permission);
                        yaml.set(path + "purchase-limit", 0);
                        yaml.set(path + "slot", Math.min(44, slot));
                        yaml.set(path + "glow", false);
                        yaml.set(path + "soulbound", true);
                        yaml.set(path + "custom-model-data", 0);
                        config.save(ConfigManager.TOOLS);
                        plugin.toolRegistry().load();
                        messages.send(player, "admin.saved");
                        openTools(player);
                    });
                });
            });
        });
    }

    private void openToolEdit(Player player, ToolDefinition tool) {
        Gui gui = new Gui("admin-tool-edit", "&8TOOL: " + tool.id(), 4);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        gui.set(10, item(Material.GOLD_INGOT, messages.raw("admin.edit-reward"),
                List.of("&7Price: &6" + plugin.economy().format(tool.price()))), click ->
                askNumber(player, "admin.prompt-price", value -> {
                    saveToolValue(player, tool, "price", value);
                    reopenTool(player, tool.id());
                }));
        gui.set(12, item(Material.NAME_TAG, "&eEdit Name",
                List.of("&7Current: &f" + tool.name())), click ->
                plugin.prompts().ask(player, "admin.prompt-name", value -> {
                    saveToolValue(player, tool, "name", value);
                    reopenTool(player, tool.id());
                }));
        gui.set(14, item(tool.material(), "&eEdit Material",
                List.of("&7Current: &f" + tool.material().name())), click ->
                plugin.prompts().ask(player, "admin.prompt-material", value -> {
                    Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
                    if (material == null) {
                        messages.send(player, "admin.cancelled");
                    } else {
                        saveToolValue(player, tool, "material", material.name());
                    }
                    reopenTool(player, tool.id());
                }));
        gui.set(16, item(Material.PAPER, "&eEdit Permission",
                List.of("&7Current: &f" + (tool.permission().isBlank() ? "none" : tool.permission()))), click ->
                plugin.prompts().ask(player, "admin.prompt-permission", value -> {
                    saveToolValue(player, tool, "permission", value.equalsIgnoreCase("none") ? "" : value);
                    reopenTool(player, tool.id());
                }));
        gui.set(20, item(tool.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                messages.raw("admin.toggle"), List.of(tool.enabled()
                        ? messages.raw("admin.enabled") : messages.raw("admin.disabled"))), click -> {
            saveToolValue(player, tool, "enabled", !tool.enabled());
            reopenTool(player, tool.id());
        });
        gui.set(22, item(Material.RED_CONCRETE, messages.raw("admin.delete"),
                List.of("&7Remove this tool from the registry.")), click -> {
            config.get(ConfigManager.TOOLS).set("tools." + tool.id(), null);
            config.save(ConfigManager.TOOLS);
            plugin.toolRegistry().load();
            messages.send(player, "admin.deleted", "name", tool.id());
            openTools(player);
        });
        gui.button(26, Material.BARRIER, messages.raw("admin.back"), click -> openTools(player));
        gui.open(player);
    }

    private void reopenTool(Player player, String id) {
        plugin.toolRegistry().tool(id).ifPresentOrElse(tool -> openToolEdit(player, tool),
                () -> openTools(player));
    }

    private void saveToolValue(Player player, ToolDefinition tool, String key, Object value) {
        config.get(ConfigManager.TOOLS).set("tools." + tool.id() + "." + key, value);
        config.save(ConfigManager.TOOLS);
        plugin.toolRegistry().load();
        messages.send(player, "admin.saved");
    }

    public void openRecords(Player player, String filter) {
        if (!allowed(player)) {
            return;
        }
        Gui gui = new Gui("admin-records", messages.raw("admin.records-title"), 6);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (PlayerProfile profile : plugin.profiles().sortedByName()) {
            if (slot >= 45) {
                break;
            }
            boolean prison = profile.isInPrison(System.currentTimeMillis());
            boolean criminal = profile.isCriminal(System.currentTimeMillis());
            if (filter.equals("police") && profile.kills() == 0 && profile.arrests() == 0) {
                continue;
            }
            if (filter.equals("criminal") && !criminal) {
                continue;
            }
            List<String> lore = new ArrayList<>(List.of(
                    "&7Kills: &f" + profile.kills(),
                    "&7Arrests: &f" + profile.arrests(),
                    "&7Robberies: &f" + profile.robberies(),
                    "&7Duty: &f" + Formats.duration(profile.dutySeconds()),
                    "&7Earnings: &6" + plugin.economy().format(profile.policeEarnings()),
                    "&7Criminal: " + (criminal ? "&cYes" : "&aNo"),
                    "&7Balance: &6" + plugin.economy().format(plugin.economy().balance(Bukkit.getOfflinePlayer(profile.uuid()))),
                    prison ? "&cIn prison" : "&aFree",
                    "&7Click for details."
            ));
            gui.set(slot++, head(profile, lore), click -> openRecord(player, profile, filter));
        }
        if (slot == 0) {
            gui.set(22, item(Material.PAPER, messages.raw("admin.no-records"), List.of()));
        }
        gui.set(45, item(Material.IRON_SWORD, "&eFilter: Police",
                List.of("&7Show officers with activity.")), click -> openRecords(player, "police"));
        gui.set(46, item(Material.IRON_BARS, "&eFilter: Criminals",
                List.of("&7Show active criminals.")), click -> openRecords(player, "criminal"));
        gui.set(47, item(Material.BOOK, "&eFilter: All",
                List.of("&7Show every profile.")), click -> openRecords(player, "all"));
        gui.button(49, Material.BARRIER, messages.raw("admin.back"), click -> openMain(player));
        gui.open(player);
    }

    private void openRecord(Player player, PlayerProfile profile, String filter) {
        Gui gui = new Gui("admin-record", "&8" + profile.name(), 3);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        gui.set(10, item(Material.IRON_SWORD, "&cKills", List.of("&f" + profile.kills())));
        gui.set(11, item(Material.IRON_BARS, "&6Arrests", List.of("&f" + profile.arrests())));
        gui.set(12, item(Material.CHEST, "&eRobberies", List.of("&f" + profile.robberies())));
        gui.set(13, item(Material.CLOCK, "&bDuty Time", List.of("&f" + Formats.duration(profile.dutySeconds()))));
        gui.set(14, item(Material.GOLD_INGOT, "&6Police Earnings",
                List.of("&f" + plugin.economy().format(profile.policeEarnings()))));
        gui.set(15, item(Material.EMERALD, "&aBalance",
                List.of("&f" + plugin.economy().format(
                        plugin.economy().balance(Bukkit.getOfflinePlayer(profile.uuid()))))));
        gui.set(16, item(profile.isCriminal(System.currentTimeMillis()) ? Material.REDSTONE : Material.LIME_DYE,
                "&eCriminal Status", List.of(profile.isCriminal(System.currentTimeMillis())
                        ? "&cCriminal" : "&aCitizen")));
        gui.button(22, Material.BARRIER, messages.raw("admin.back"), click -> openRecords(player, filter));
        gui.open(player);
    }

    public void openTransactions(Player player) {
        if (!allowed(player)) {
            return;
        }
        plugin.database().recentTransactions(null, 45).whenComplete((records, error) ->
                plugin.scheduler().sync(() -> {
                    if (error != null) {
                        messages.send(player, "database-error");
                        return;
                    }
                    Gui gui = new Gui("admin-transactions", messages.raw("admin.transactions-title"), 6);
                    gui.fill(Material.BLACK_STAINED_GLASS_PANE);
                    if (records.isEmpty()) {
                        gui.set(22, item(Material.PAPER, messages.raw("admin.no-records"), List.of()));
                    } else {
                        int slot = 0;
                        for (TransactionRecord record : records) {
                            PlayerProfile owner = plugin.profiles().get(record.uuid()).orElse(null);
                            String name = owner == null ? record.uuid().toString() : owner.name();
                            gui.set(slot++, item(Material.PAPER, "&6" + record.type(), List.of(
                                    "&7Player: &f" + name,
                                    "&7Amount: &6" + plugin.economy().format(record.amount()),
                                    "&7Detail: &f" + record.detail(),
                                    "&7Time: &f" + Formats.dateTime(record.createdAt(), zone())
                            )));
                        }
                    }
                    gui.button(49, Material.BARRIER, messages.raw("admin.back"), click -> openMain(player));
                    gui.open(player);
                }));
    }

    public void openPrison(Player player) {
        if (!allowed(player)) {
            return;
        }
        Gui gui = new Gui("admin-prison", messages.raw("admin.prison-title"), 6);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        long now = System.currentTimeMillis();
        for (PlayerProfile profile : plugin.profiles().all()) {
            if (!profile.isInPrison(now) || slot >= 45) {
                continue;
            }
            long remaining = Math.max(0, (profile.prisonUntil() - now) / 1000L);
            gui.set(slot++, head(profile, List.of(
                    "&7Sentence: &f" + Formats.duration(remaining),
                    "&7Reason: &f" + profile.prisonReason(),
                    "&7Click to release."
            )), click -> {
                Player online = Bukkit.getPlayer(profile.uuid());
                if (online != null) {
                    plugin.prison().release(online, true);
                } else {
                    plugin.prison().releaseOffline(profile);
                }
                messages.send(player, "admin.released", "player", profile.name());
                openPrison(player);
            });
        }
        if (slot == 0) {
            gui.set(22, item(Material.PAPER, messages.raw("prison.record-none"), List.of()));
        }
        gui.button(49, Material.BARRIER, messages.raw("admin.back"), click -> openMain(player));
        gui.open(player);
    }

    private void openIntegrations(Player player) {
        if (!allowed(player)) {
            return;
        }
        Gui gui = new Gui("admin-integrations", messages.raw("admin.integrations-title"), 3);
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        gui.set(10, item(Material.GOLD_INGOT, "&6Vault Economy", List.of(
                messages.format("admin.integration-vault", "status",
                        plugin.integrations().vaultAvailable()
                                ? messages.raw("admin.integration-status")
                                : messages.raw("admin.integration-missing")),
                "&7Provider: &f" + plugin.economy().service().name()
        )));
        gui.set(12, item(Material.NAME_TAG, "&bLuckPerms", List.of(
                messages.format("admin.integration-luckperms", "status",
                        plugin.integrations().luckPerms().available()
                                ? messages.raw("admin.integration-status")
                                : messages.raw("admin.integration-missing")),
                "&7Police group: &f" + plugin.police().group()
        )));
        gui.set(14, item(Material.BOOK, "&eEssentialsX", List.of(
                Bukkit.getPluginManager().getPlugin("Essentials") != null
                        ? messages.raw("admin.integration-status") : messages.raw("admin.integration-missing"),
                "&7Economy is used through Vault", "&7when both are installed."
        )));
        gui.set(16, item(Material.CHEST, "&aSQLite", List.of(
                "&7Storage: &fSQLite (bundled)",
                "&7Profiles: &f" + plugin.profiles().all().size()
        )));
        gui.button(26, Material.BARRIER, messages.raw("admin.back"), click -> openMain(player));
        gui.open(player);
    }

    private void askNumber(Player player, String prompt, Consumer<Double> consumer) {
        plugin.prompts().ask(player, prompt, input -> {
            try {
                double value = Double.parseDouble(input);
                if (!Double.isFinite(value) || value < 0) {
                    throw new NumberFormatException();
                }
                consumer.accept(value);
            } catch (NumberFormatException exception) {
                messages.send(player, "invalid-number", "value", input);
            }
        });
    }

    private boolean allowed(Player player) {
        if (!player.hasPermission("city.admin")) {
            messages.send(player, "no-permission");
            return false;
        }
        return true;
    }

    private boolean validId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_-]{2,32}");
    }

    private ItemStack head(PlayerProfile profile, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(profile.uuid());
            skull.setOwningPlayer(offline);
        }
        meta.displayName(ItemUtil.text("&e" + profile.name()));
        meta.lore(ItemUtil.textList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemUtil.text(name));
        meta.lore(ItemUtil.textList(lore));
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
}
