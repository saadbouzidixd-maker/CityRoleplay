package dev.cityroleplay.prison;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.criminal.CriminalService;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class PrisonManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ProfileService profiles;
    private final Database database;
    private final Scheduler scheduler;
    private final CriminalService criminals;
    private final Map<UUID, PendingJail> pending = new HashMap<>();
    private BukkitTask releaseTask;

    public PrisonManager(JavaPlugin plugin, ConfigManager config, Messages messages,
                         ProfileService profiles, Database database, Scheduler scheduler,
                         CriminalService criminals) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.database = database;
        this.scheduler = scheduler;
        this.criminals = criminals;
    }

    public void start() {
        stop();
        releaseTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L,
                Math.max(20, config.get(ConfigManager.PRISON).getInt("prison.check-interval-ticks", 100)));
    }

    public void stop() {
        if (releaseTask != null) {
            releaseTask.cancel();
            releaseTask = null;
        }
    }

    public void scheduleAfterRespawn(Player player, int minutes, String reason, String officer) {
        pending.put(player.getUniqueId(), new PendingJail(minutes, reason, officer));
    }

    public void applyPending(Player player) {
        PendingJail jail = pending.remove(player.getUniqueId());
        if (jail != null) {
            jail(player, jail.minutes(), jail.reason(), jail.officer());
        }
    }

    public boolean jail(Player player, int minutes, String reason, String officer) {
        if (player == null) {
            return false;
        }
        int clamped = clamp(minutes);
        PlayerProfile profile = profiles.getOrCreate(player);
        long until = System.currentTimeMillis() + clamped * 60_000L;
        profile.prisonUntil(until);
        profile.prisonReason(reason == null ? "" : reason);
        profiles.save(profile);
        database.recordPrison(player.getUniqueId(), until, reason, officer);
        boolean dispatched = dispatch(player, config.get(ConfigManager.PRISON)
                .getString("prison.jail-command", "warp prison %player%"));
        messages.send(player, "prison.jailed", "minutes", Integer.toString(clamped));
        if (!dispatched) {
            messages.send(player, "prison.command-failed");
        }
        return true;
    }

    public void onJoin(Player player) {
        PlayerProfile profile = profiles.getOrCreate(player);
        long now = System.currentTimeMillis();
        if (!profile.isInPrison(now)) {
            return;
        }
        if (config.get(ConfigManager.PRISON).getBoolean("prison.notify-on-join", true)) {
            messages.send(player, "prison.remaining",
                    "time", dev.cityroleplay.util.Formats.duration(remainingSeconds(player)));
        }
        if (config.get(ConfigManager.PRISON).getBoolean("prison.enforce-on-join", true)) {
            scheduler.later(() -> {
                if (player.isOnline() && isImprisoned(player.getUniqueId())) {
                    dispatch(player, config.get(ConfigManager.PRISON)
                            .getString("prison.jail-command", "warp prison %player%"));
                }
            }, 20L);
        }
    }

    public boolean isImprisoned(UUID uuid) {
        return profiles.get(uuid).map(profile -> profile.isInPrison(System.currentTimeMillis())).orElse(false);
    }

    public long remainingSeconds(Player player) {
        return profiles.get(player.getUniqueId())
                .map(profile -> Math.max(0, (profile.prisonUntil() - System.currentTimeMillis()) / 1000L))
                .orElse(0L);
    }

    public void release(Player player, boolean administrative) {
        PlayerProfile profile = profiles.getOrCreate(player);
        if (!profile.isInPrison(System.currentTimeMillis()) && profile.prisonUntil() == 0) {
            if (administrative) {
                messages.send(player, "prison.record-none");
            }
            return;
        }
        profile.prisonUntil(0);
        profile.prisonReason("");
        profiles.save(profile);
        if (config.get(ConfigManager.POLICE).getBoolean("arrest.clear-criminal-on-release", true)
                && config.get(ConfigManager.CRIMINALS).getBoolean("criminals.clear-on-prison-release", true)) {
            criminals.clear(player.getUniqueId());
        }
        String command = config.get(ConfigManager.PRISON).getString("prison.release-command", "");
        boolean dispatched = false;
        if (command != null && !command.isBlank()) {
            dispatched = dispatch(player, command);
        }
        if (!dispatched) {
            teleportFallback(player);
        }
        messages.send(player, "prison.release");
    }

    public void releaseOffline(PlayerProfile profile) {
        profile.prisonUntil(0);
        profile.prisonReason("");
        profiles.save(profile);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<PlayerProfile> optional = profiles.get(player.getUniqueId());
            if (optional.isEmpty()) {
                continue;
            }
            PlayerProfile profile = optional.get();
            if (profile.prisonUntil() > 0 && profile.prisonUntil() <= now) {
                release(player, false);
            }
        }
    }

    private boolean dispatch(Player player, String template) {
        if (template == null || template.isBlank()) {
            plugin.getLogger().warning("Prison command is empty in prison.yml");
            return false;
        }
        if (!template.contains("%player%")) {
            plugin.getLogger().warning("Prison command must contain %player%: " + template);
            messages.send(player, "prison.command-failed");
            return false;
        }
        String name = player.getName();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            plugin.getLogger().warning("Refusing to dispatch a prison command for an invalid name.");
            return false;
        }
        String command = template.replace("%player%", name);
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Prison command failed: " + command, exception);
            messages.send(player, "prison.command-failed");
            return false;
        }
    }

    private void teleportFallback(Player player) {
        String worldName = config.get(ConfigManager.PRISON).getString("prison.release-world", "");
        World world = worldName == null || worldName.isBlank()
                ? player.getWorld() : Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        if (world != null) {
            player.teleport(world.getSpawnLocation());
        }
    }

    private int clamp(int minutes) {
        int min = Math.max(1, config.get(ConfigManager.PRISON).getInt("prison.min-sentence-minutes", 1));
        int max = Math.max(min, config.get(ConfigManager.PRISON).getInt("prison.max-sentence-minutes", 1440));
        return Math.max(min, Math.min(max, minutes));
    }

    private record PendingJail(int minutes, String reason, String officer) {
    }
}
