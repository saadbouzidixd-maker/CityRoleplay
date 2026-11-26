package dev.cityroleplay.criminal;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.util.Formats;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CriminalService {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ProfileService profiles;

    public CriminalService(JavaPlugin plugin, ConfigManager config, Messages messages, ProfileService profiles) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
    }

    public boolean isCriminal(Player player) {
        return player != null && isCriminal(player.getUniqueId());
    }

    public boolean isCriminal(UUID uuid) {
        return profiles.get(uuid).map(profile -> profile.isCriminal(System.currentTimeMillis())).orElse(false);
    }

    public Optional<PlayerProfile> profile(UUID uuid) {
        return profiles.get(uuid);
    }

    public void setCriminal(Player target, String reason) {
        if (target == null) {
            return;
        }
        PlayerProfile profile = profiles.getOrCreate(target);
        long now = System.currentTimeMillis();
        int minutes = config.get(ConfigManager.CRIMINALS).getInt("criminals.wanted-minutes", 120);
        profile.criminalFlag(true);
        profile.criminalSince(profile.criminalSince() > 0 ? profile.criminalSince() : now);
        profile.wantedUntil(minutes <= 0 ? 0 : now + minutes * 60_000L);
        profiles.save(profile);
        if (config.get(ConfigManager.CRIMINALS).getBoolean("criminals.public-broadcast", false)) {
            Bukkit.broadcast(messages.component("criminal.set", "player", target.getName()));
        }
    }

    public void clear(Player target) {
        if (target == null) {
            return;
        }
        PlayerProfile profile = profiles.getOrCreate(target);
        profile.criminalFlag(false);
        profile.wantedUntil(0);
        profile.criminalSince(0);
        profiles.save(profile);
        if (config.get(ConfigManager.CRIMINALS).getBoolean("criminals.public-broadcast", false)) {
            Bukkit.broadcast(messages.component("criminal.cleared", "player", target.getName()));
        }
    }

    public void clear(UUID uuid) {
        profiles.get(uuid).ifPresent(profile -> {
            profile.criminalFlag(false);
            profile.wantedUntil(0);
            profile.criminalSince(0);
            profiles.save(profile);
        });
    }

    /**
     * A kill only marks the killer criminal when at least one player witnessed
     * it. There is no global criminal list and no public broadcast by default.
     */
    public void handleWitnessedKill(Player victim, Player killer) {
        if (victim == null || killer == null || victim.getUniqueId().equals(killer.getUniqueId())) {
            return;
        }
        if (!config.get(ConfigManager.CRIMINALS).getBoolean("criminals.criminal-on-witnessed-kill", true)) {
            return;
        }
        List<Player> witnesses = witnesses(victim.getLocation(), killer, victim);
        if (witnesses.isEmpty()) {
            return;
        }
        setCriminal(killer, "witnessed kill");
        if (config.get(ConfigManager.CRIMINALS).getBoolean("criminals.notify-witnesses", true)) {
            for (Player witness : witnesses) {
                messages.send(witness, "criminal.witness-kill",
                        "killer", killer.getName(), "victim", victim.getName());
            }
        }
    }

    public void handleWitnessedRobbery(Player robber, String zoneName) {
        if (robber == null || !config.get(ConfigManager.CRIMINALS).getBoolean("criminals.criminal-on-robbery", true)) {
            return;
        }
        List<Player> witnesses = witnesses(robber.getLocation(), robber);
        if (config.get(ConfigManager.CRIMINALS).getBoolean("criminals.notify-witnesses", true)) {
            for (Player witness : witnesses) {
                messages.send(witness, "criminal.witness-robbery",
                        "player", robber.getName(), "zone", zoneName);
            }
        }
    }

    public List<Player> witnesses(Location location, Player... excluded) {
        List<Player> result = new ArrayList<>();
        if (location == null || location.getWorld() == null) {
            return result;
        }
        double radius = Math.max(1,
                config.get(ConfigManager.CRIMINALS).getInt("criminals.witness-radius", 24));
        double radiusSquared = radius * radius;
        outer:
        for (Player online : location.getWorld().getPlayers()) {
            if (online.getLocation().distanceSquared(location) > radiusSquared) {
                continue;
            }
            if (excluded != null) {
                for (Player skip : excluded) {
                    if (skip != null && skip.getUniqueId().equals(online.getUniqueId())) {
                        continue outer;
                    }
                }
            }
            result.add(online);
        }
        return result;
    }

    public String wantedLabel(PlayerProfile profile) {
        if (!profile.isWanted(System.currentTimeMillis())) {
            return "";
        }
        return Formats.dateTime(profile.wantedUntil(), java.time.ZoneId.systemDefault());
    }
}
