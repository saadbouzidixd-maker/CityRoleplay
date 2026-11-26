package dev.cityroleplay.command;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.police.PoliceDashboardGui;
import dev.cityroleplay.police.PoliceService;
import dev.cityroleplay.police.ShopGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class PoliceCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final PoliceService police;
    private final PoliceDashboardGui dashboard;
    private final ShopGui shop;

    public PoliceCommand(Messages messages, PoliceService police,
                         PoliceDashboardGui dashboard, ShopGui shop) {
        this.messages = messages;
        this.police = police;
        this.dashboard = dashboard;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!police.isPolice(player)) {
            messages.send(player, "police.not-officer");
            return true;
        }
        if (args.length == 0) {
            dashboard.open(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "shop" -> shop.open(player);
            case "stats" -> dashboard.openStats(player);
            case "activity" -> dashboard.openActivity(player);
            default -> messages.send(player, "command-usage", "usage", "/police [shop|stats|activity]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("shop", "stats", "activity");
        }
        return List.of();
    }
}
