package dev.cityroleplay.command;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class EcoCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final EconomyManager economy;
    private final ProfileService profiles;

    public EcoCommand(Messages messages, EconomyManager economy, ProfileService profiles) {
        this.messages = messages;
        this.economy = economy;
        this.profiles = profiles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("city.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "eco.usage");
            return true;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            messages.send(sender, "unknown-player", "player", args[1]);
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException exception) {
            messages.send(sender, "invalid-number", "value", args[2]);
            return true;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (economy.pay(target, amount, "ECO_GIVE", "Administrative grant")) {
                    messages.send(sender, "eco.given",
                            "amount", economy.format(amount), "player", target.getName());
                } else {
                    messages.send(sender, "database-error");
                }
            }
            case "take" -> {
                if (economy.charge(target, amount, "ECO_TAKE", "Administrative removal")) {
                    messages.send(sender, "eco.taken",
                            "amount", economy.format(amount), "player", target.getName());
                } else {
                    messages.send(sender, "invalid-amount");
                }
            }
            case "set" -> {
                if (economy.setBalance(target, amount)) {
                    messages.send(sender, "eco.set",
                            "amount", economy.format(amount), "player", target.getName());
                } else {
                    messages.send(sender, "database-error");
                }
            }
            default -> messages.send(sender, "eco.usage");
        }
        return true;
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        var cached = profiles.get(name);
        if (cached.isPresent()) {
            PlayerProfile profile = cached.get();
            return Bukkit.getOfflinePlayer(profile.uuid());
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "take", "set");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        return List.of();
    }
}
