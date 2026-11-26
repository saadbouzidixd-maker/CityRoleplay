package dev.cityroleplay.command;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.police.ArrestManager;
import dev.cityroleplay.police.PoliceService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ArrestCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final PoliceService police;
    private final ArrestManager arrests;

    public ArrestCommand(Messages messages, PoliceService police, ArrestManager arrests) {
        this.messages = messages;
        this.police = police;
        this.arrests = arrests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player officer)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!police.isPolice(officer)) {
            messages.send(officer, "police.not-officer");
            return true;
        }
        if (args.length == 0) {
            messages.send(officer, "arrest.usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "jail" -> {
                Optional<Player> target = target(officer, args);
                if (target.isEmpty()) {
                    messages.send(officer, "arrest.not-arrested");
                    return true;
                }
                arrests.jail(officer, target.get());
            }
            case "release" -> {
                Optional<Player> target = target(officer, args);
                if (target.isEmpty()) {
                    messages.send(officer, "arrest.not-arrested");
                    return true;
                }
                arrests.releaseByAdmin(officer, target.get());
            }
            case "escort" -> {
                Optional<Player> target = target(officer, args);
                if (target.isEmpty()) {
                    messages.send(officer, "arrest.not-arrested");
                    return true;
                }
                arrests.toggleEscort(officer, target.get());
            }
            default -> messages.send(officer, "arrest.usage");
        }
        return true;
    }

    private Optional<Player> target(Player officer, String[] args) {
        if (args.length >= 2) {
            Player named = Bukkit.getPlayerExact(args[1]);
            return Optional.ofNullable(named);
        }
        if (officer.getTargetEntity(10) instanceof Player looked) {
            return Optional.of(looked);
        }
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!arrests.isArrested(online.getUniqueId())) {
                continue;
            }
            double distance = online.getWorld() == officer.getWorld()
                    ? online.getLocation().distanceSquared(officer.getLocation()) : Double.MAX_VALUE;
            if (distance < best) {
                best = distance;
                nearest = online;
            }
        }
        return Optional.ofNullable(nearest);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("jail", "release", "escort");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (args[1].isEmpty() || player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
