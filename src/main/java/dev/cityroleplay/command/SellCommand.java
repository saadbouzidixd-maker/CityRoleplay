package dev.cityroleplay.command;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.sell.SellGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SellCommand implements CommandExecutor {

    private final Messages messages;
    private final SellGui sellGui;

    public SellCommand(Messages messages, SellGui sellGui) {
        this.messages = messages;
        this.sellGui = sellGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("city.sell") && !player.hasPermission("city.admin")) {
            messages.send(player, "no-permission");
            return true;
        }
        sellGui.open(player);
        return true;
    }
}
