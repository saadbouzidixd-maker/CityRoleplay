package dev.cityroleplay.command;

import dev.cityroleplay.CityRoleplay;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;

import java.util.List;

public final class CommandManager {

    private final CityRoleplay plugin;

    public CommandManager(CityRoleplay plugin) {
        this.plugin = plugin;
    }

    public void register() {
        police();
        cityAdmin();
        sell();
        arrest();
        ecoFallback();
    }

    private void police() {
        PluginCommand command = plugin.getCommand("police");
        if (command == null) {
            return;
        }
        PoliceCommand executor = new PoliceCommand(plugin.messages(), plugin.police(),
                plugin.policeDashboard(), plugin.shopGui());
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void cityAdmin() {
        PluginCommand command = plugin.getCommand("cityadmin");
        if (command == null) {
            return;
        }
        CityAdminCommand executor = new CityAdminCommand(plugin);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void sell() {
        PluginCommand command = plugin.getCommand("sell");
        if (command != null) {
            command.setExecutor(new SellCommand(plugin.messages(), plugin.sellGui()));
        }
    }

    private void arrest() {
        PluginCommand command = plugin.getCommand("arrest");
        if (command == null) {
            return;
        }
        ArrestCommand executor = new ArrestCommand(plugin.messages(), plugin.police(), plugin.arrests());
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * EssentialsX owns /eco on most servers. Only register the fallback when no
     * other plugin has already claimed the command.
     */
    private void ecoFallback() {
        boolean respect = plugin.config().get(dev.cityroleplay.config.ConfigManager.INTEGRATIONS)
                .getBoolean("essentials.respect-existing-eco-command", true);
        CommandMap map = plugin.getServer().getCommandMap();
        if (respect && map.getCommand("eco") != null) {
            return;
        }
        EcoCommand executor = new EcoCommand(plugin.messages(), plugin.economy(), plugin.profiles());
        Command command = new Command("eco") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                return executor.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                return executor.onTabComplete(sender, this, alias, args);
            }
        };
        command.setDescription("CityRoleplay economy administration");
        command.setUsage("/eco <give|take|set> <player> <amount>");
        command.setPermission("city.admin");
        map.register("cityroleplay", command);
    }
}
