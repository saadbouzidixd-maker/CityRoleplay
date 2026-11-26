package dev.cityroleplay.police;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.integration.LuckPermsHook;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

public final class PoliceService {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ProfileService profiles;
    private final EconomyManager economy;
    private final LuckPermsHook luckPerms;

    public PoliceService(JavaPlugin plugin, ConfigManager config, Messages messages,
                         ProfileService profiles, EconomyManager economy, LuckPermsHook luckPerms) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.economy = economy;
        this.luckPerms = luckPerms;
    }

    public String group() {
        return config.get(ConfigManager.POLICE).getString("police.group", "POLICE");
    }

    public boolean isPolice(Player player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermission("city.admin") || player.hasPermission("city.police")) {
            return true;
        }
        return luckPerms.available() && luckPerms.inGroup(player, group());
    }

    public String primaryGroup(Player player) {
        return luckPerms.available() ? luckPerms.primaryGroup(player) : "default";
    }

    public boolean reward(Player player, double amount, String type, String detail) {
        if (player == null || amount <= 0) {
            return false;
        }
        if (!economy.pay(player, amount, type, detail)) {
            return false;
        }
        PlayerProfile profile = profiles.getOrCreate(player);
        profiles.addPoliceEarnings(profile, amount);
        return true;
    }

    public boolean fine(Player player, double amount, String detail) {
        if (player == null || amount <= 0) {
            return false;
        }
        return economy.charge(player, amount, "POLICE_FINE", detail);
    }

    public double killReward() {
        return config.get(ConfigManager.POLICE).getDouble("police.kill-reward", 250.0);
    }

    public double arrestReward() {
        return config.get(ConfigManager.POLICE).getDouble("police.arrest-reward", 400.0);
    }

    public double citizenFine() {
        return config.get(ConfigManager.POLICE).getDouble("police.citizen-fine", 150.0);
    }

    public void setPolice(Player target, boolean add, Consumer<String> result) {
        if (!luckPerms.available()) {
            result.accept("LuckPerms is not installed");
            return;
        }
        if (!luckPerms.groupExists(group())) {
            result.accept("LuckPerms group '" + group() + "' does not exist");
            return;
        }
        UUID uuid = target.getUniqueId();
        var future = add ? luckPerms.addToGroup(uuid, group()) : luckPerms.removeFromGroup(uuid, group());
        future.whenComplete((success, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Police group update failed: " + error.getMessage());
            }
            result.accept(Boolean.TRUE.equals(success) ? null : "group update failed");
        });
    }

    public String statistics(Player player) {
        PlayerProfile profile = profiles.getOrCreate(player);
        return "kills=" + profile.kills()
                + " arrests=" + profile.arrests()
                + " dutySeconds=" + profile.dutySeconds()
                + " earnings=" + profile.policeEarnings()
                + " since=" + profile.policeSince();
    }

    public void ensureJoiningDate(Player player) {
        if (!config.get(ConfigManager.POLICE).getBoolean("police.record-joining-date", true)) {
            return;
        }
        PlayerProfile profile = profiles.getOrCreate(player);
        if (isPolice(player) && profile.policeSince() <= 0) {
            profile.policeSince(System.currentTimeMillis());
            profiles.save(profile);
        }
    }

    public void notifyNoPermission(Player player) {
        messages.send(player, "police.not-officer");
    }
}
