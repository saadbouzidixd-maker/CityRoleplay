package dev.cityroleplay.command;

import dev.cityroleplay.CityRoleplay;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CityAdminCommand implements CommandExecutor, TabCompleter {

    private final CityRoleplay plugin;
    private final Messages messages;

    public CityAdminCommand(CityRoleplay plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("city.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            plugin.adminGui().openMain(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadEverything();
                messages.send(sender, "reload-success");
            }
            case "zones" -> withPlayer(sender, player -> plugin.adminGui().openZones(player));
            case "robbery" -> withPlayer(sender, player -> plugin.adminGui().openRobberies(player));
            case "tools" -> withPlayer(sender, player -> plugin.adminGui().openTools(player));
            case "records" -> withPlayer(sender, player -> plugin.adminGui().openRecords(player, "all"));
            case "transactions" -> withPlayer(sender, player -> plugin.adminGui().openTransactions(player));
            case "prison" -> withPlayer(sender, player -> plugin.adminGui().openPrison(player));
            case "wand" -> withPlayer(sender, player -> {
                ItemUtil.give(player, ItemUtil.selectionWand());
                messages.send(player, "selection.wand-given");
            });
            case "grave" -> grave(sender, args);
            case "release" -> release(sender, args);
            case "police" -> police(sender, args);
            case "criminal" -> criminal(sender, args);
            case "reset" -> reset(sender, args);
            case "tool" -> tool(sender, args);
            case "eco" -> eco(sender, args);
            case "help" -> help(sender);
            default -> messages.send(sender, "command-usage", "usage",
                    "/cityadmin <zones|robbery|tools|records|transactions|prison|wand|grave|release|police|criminal|reset|eco|reload>");
        }
        return true;
    }

    private void grave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(player, "admin.grave-hint");
            return;
        }
        var block = player.getTargetBlockExact(6);
        if (block == null) {
            messages.send(player, "grave.none");
            return;
        }
        if (args[1].equalsIgnoreCase("add")) {
            plugin.graves().register(block, player);
        } else if (args[1].equalsIgnoreCase("remove")) {
            plugin.graves().remove(block, player);
        } else {
            messages.send(player, "admin.grave-hint");
        }
    }

    private void release(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "command-usage", "usage", "/cityadmin release <player>");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            if (!plugin.prison().isImprisoned(online.getUniqueId())) {
                messages.send(sender, "prison.record-none");
                return;
            }
            plugin.prison().release(online, true);
            messages.send(sender, "admin.released", "player", online.getName());
            return;
        }
        var profile = plugin.profiles().findOffline(args[1]);
        if (profile.isEmpty()) {
            messages.send(sender, "unknown-player", "player", args[1]);
            return;
        }
        plugin.prison().releaseOffline(profile.get());
        messages.send(sender, "admin.released", "player", profile.get().name());
    }

    private void police(CommandSender sender, String[] args) {
        if (args.length < 3 || !(args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            messages.send(sender, "command-usage", "usage", "/cityadmin police <add|remove> <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[2]);
            return;
        }
        boolean add = args[1].equalsIgnoreCase("add");
        plugin.police().setPolice(target, add, error -> plugin.scheduler().sync(() -> {
            if (error != null) {
                messages.send(sender, "reload-failed", "error", error);
            } else {
                messages.send(sender, add ? "admin.police-added" : "admin.police-removed",
                        "player", target.getName());
            }
        }));
    }

    private void criminal(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command-usage", "usage", "/cityadmin criminal <set|clear> <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[2]);
            return;
        }
        if (args[1].equalsIgnoreCase("set")) {
            plugin.criminals().setCriminal(target, "administrative");
            messages.send(sender, "criminal.set", "player", target.getName());
        } else if (args[1].equalsIgnoreCase("clear")) {
            plugin.criminals().clear(target);
            messages.send(sender, "criminal.cleared", "player", target.getName());
        } else {
            messages.send(sender, "command-usage", "usage", "/cityadmin criminal <set|clear> <player>");
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "admin.reset-usage");
            return;
        }
        var profile = plugin.profiles().findOffline(args[1]);
        if (profile.isEmpty()) {
            messages.send(sender, "unknown-player", "player", args[1]);
            return;
        }
        String scope = args[2].toLowerCase(Locale.ROOT);
        PlayerProfile value = profile.get();
        switch (scope) {
            case "police" -> plugin.profiles().resetPolice(value);
            case "criminal" -> plugin.profiles().resetCriminal(value);
            case "all" -> {
                plugin.profiles().resetPolice(value);
                plugin.profiles().resetCriminal(value);
            }
            default -> {
                messages.send(sender, "admin.reset-usage");
                return;
            }
        }
        messages.send(sender, "admin.stats-reset", "player", value.name(), "scope", scope);
    }

    private void tool(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            messages.send(sender, "command-usage", "usage", "/cityadmin tool give <player> <tool>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[2]);
            return;
        }
        var tool = plugin.toolRegistry().tool(args[3]);
        if (tool.isEmpty()) {
            messages.send(sender, "police.tool-invalid");
            return;
        }
        plugin.toolService().give(target, tool.get());
        messages.send(sender, "police.tool-given", "item", tool.get().name(), "player", target.getName());
        messages.send(target, "police.tool-received", "item", tool.get().name());
    }

    private void eco(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "eco.usage");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            var profile = plugin.profiles().findOffline(args[2]);
            if (profile.isEmpty()) {
                messages.send(sender, "unknown-player", "player", args[2]);
                return;
            }
            target = Bukkit.getOfflinePlayer(profile.get().uuid());
        }
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException exception) {
            messages.send(sender, "invalid-number", "value", args[3]);
            return;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            messages.send(sender, "invalid-amount");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (plugin.economy().pay(target, amount, "ADMIN_GIVE", "Administrative grant")) {
                    messages.send(sender, "admin.eco-given",
                            "amount", plugin.economy().format(amount), "player", target.getName());
                } else {
                    messages.send(sender, "database-error");
                }
            }
            case "take" -> {
                if (plugin.economy().charge(target, amount, "ADMIN_TAKE", "Administrative removal")) {
                    messages.send(sender, "admin.eco-taken",
                            "amount", plugin.economy().format(amount), "player", target.getName());
                } else {
                    messages.send(sender, "invalid-amount");
                }
            }
            case "set" -> {
                if (plugin.economy().setBalance(target, amount)) {
                    messages.send(sender, "admin.eco-set",
                            "amount", plugin.economy().format(amount), "player", target.getName());
                } else {
                    messages.send(sender, "database-error");
                }
            }
            default -> messages.send(sender, "eco.usage");
        }
    }

    private void help(CommandSender sender) {
        messages.sendNoPrefix(sender, "&6CityRoleplay administration");
        messages.sendNoPrefix(sender, "&7/cityadmin &8- &fOpen the admin panel");
        messages.sendNoPrefix(sender, "&7/cityadmin reload &8- &fReload configuration");
        messages.sendNoPrefix(sender, "&7/cityadmin wand &8- &fGet the selection wand");
        messages.sendNoPrefix(sender, "&7/cityadmin grave <add|remove> &8- &fRegister a grave");
        messages.sendNoPrefix(sender, "&7/cityadmin release <player> &8- &fRelease a prisoner");
        messages.sendNoPrefix(sender, "&7/cityadmin police <add|remove> <player>");
        messages.sendNoPrefix(sender, "&7/cityadmin criminal <set|clear> <player>");
        messages.sendNoPrefix(sender, "&7/cityadmin reset <player> <police|criminal|all>");
        messages.sendNoPrefix(sender, "&7/cityadmin tool give <player> <tool>");
        messages.sendNoPrefix(sender, "&7/cityadmin eco <give|take|set> <player> <amount>");
    }

    private void withPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        action.accept(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "wand", "grave", "release", "police", "criminal", "reset", "tool", "eco", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("grave")) {
            return List.of("add", "remove");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("police")) {
            return List.of("add", "remove");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("criminal")) {
            return List.of("set", "clear");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            return List.of("police", "criminal", "all");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tool")) {
            return List.of("give");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("eco")) {
            return List.of("give", "take", "set");
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("release")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        return List.of();
    }
}
