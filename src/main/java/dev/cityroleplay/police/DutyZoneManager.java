package dev.cityroleplay.police;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.integration.LuckPermsHook;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.util.Cuboid;
import dev.cityroleplay.util.Formats;
import dev.cityroleplay.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DutyZoneManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ProfileService profiles;
    private final PoliceService police;
    private final EconomyManager economy;
    private final Database database;
    private final LuckPermsHook luckPerms;
    private final List<DutyZone> zones = new ArrayList<>();
    private final Map<UUID, DutyState> states = new HashMap<>();
    private BukkitTask task;

    public DutyZoneManager(JavaPlugin plugin, ConfigManager config, Messages messages,
                           ProfileService profiles, PoliceService police, EconomyManager economy,
                           Database database, LuckPermsHook luckPerms) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.police = police;
        this.economy = economy;
        this.database = database;
        this.luckPerms = luckPerms;
    }

    public void load() {
        zones.clear();
        YamlConfiguration yaml = config.get(ConfigManager.DUTY_ZONES);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                String world = section.getString("world", "world");
                Cuboid cuboid = new Cuboid(world,
                        section.getInt("min.x"), section.getInt("min.y"), section.getInt("min.z"),
                        section.getInt("max.x"), section.getInt("max.y"), section.getInt("max.z"));
                zones.add(new DutyZone(
                        id,
                        section.getString("display-name", id),
                        cuboid,
                        Math.max(0, section.getDouble("reward", 50)),
                        Math.max(1, section.getLong("interval-seconds", 300)),
                        section.getString("permission", ""),
                        section.getString("group", ""),
                        section.getBoolean("enabled", true),
                        section.getBoolean("require-movement", true),
                        Math.max(5, section.getInt("movement-timeout-seconds", 90))
                ));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Invalid duty zone '" + id + "': " + exception.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + zones.size() + " duty zones.");
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flushAll();
    }

    public void mutateZone(DutyZone zone) {
        replaceZone(zone);
    }

    public List<DutyZone> zones() {
        return new ArrayList<>(zones);
    }

    public boolean addZone(DutyZone zone) {
        for (DutyZone existing : zones) {
            if (existing.cuboid().intersects(zone.cuboid())) {
                return false;
            }
        }
        zones.add(zone);
        saveZones();
        return true;
    }

    public boolean replaceZone(DutyZone zone) {
        zones.removeIf(existing -> existing.id().equalsIgnoreCase(zone.id()));
        zones.add(zone);
        saveZones();
        return true;
    }

    public boolean removeZone(String id) {
        boolean removed = zones.removeIf(zone -> zone.id().equalsIgnoreCase(id));
        if (removed) {
            saveZones();
        }
        return removed;
    }

    public void saveZones() {
        YamlConfiguration yaml = config.get(ConfigManager.DUTY_ZONES);
        yaml.set("zones", null);
        for (DutyZone zone : zones) {
            String path = "zones." + zone.id() + ".";
            yaml.set(path + "display-name", zone.displayName());
            yaml.set(path + "world", zone.cuboid().world());
            yaml.set(path + "min.x", zone.cuboid().minX());
            yaml.set(path + "min.y", zone.cuboid().minY());
            yaml.set(path + "min.z", zone.cuboid().minZ());
            yaml.set(path + "max.x", zone.cuboid().maxX());
            yaml.set(path + "max.y", zone.cuboid().maxY());
            yaml.set(path + "max.z", zone.cuboid().maxZ());
            yaml.set(path + "reward", zone.reward());
            yaml.set(path + "interval-seconds", zone.intervalSeconds());
            yaml.set(path + "permission", zone.permission());
            yaml.set(path + "group", zone.group());
            yaml.set(path + "enabled", zone.enabled());
            yaml.set(path + "require-movement", zone.requireMovement());
            yaml.set(path + "movement-timeout-seconds", zone.movementTimeoutSeconds());
        }
        config.save(ConfigManager.DUTY_ZONES);
    }

    public void reload() {
        flushAll();
        load();
    }

    public Optional<DutyZone> bestZone(Player player) {
        DutyZone best = null;
        for (DutyZone zone : zones) {
            if (!zone.enabled() || !zone.cuboid().contains(player.getLocation())) {
                continue;
            }
            if (zone.permission() != null && !zone.permission().isBlank()
                    && !player.hasPermission(zone.permission())) {
                continue;
            }
            if (zone.group() != null && !zone.group().isBlank()
                    && !(luckPerms.available() && luckPerms.inGroup(player, zone.group()))) {
                continue;
            }
            if (best == null || zone.reward() > best.reward()) {
                best = zone;
            }
        }
        return Optional.ofNullable(best);
    }

    public boolean isInAnyZone(Player player) {
        return bestZone(player).isPresent();
    }

    public Optional<String> currentZoneId(Player player) {
        return bestZone(player).map(DutyZone::id);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!police.isPolice(player)) {
                removeState(player.getUniqueId(), false);
                continue;
            }
            Optional<DutyZone> zoneOptional = bestZone(player);
            if (zoneOptional.isEmpty()) {
                DutyState existing = states.get(player.getUniqueId());
                if (existing != null) {
                    flushState(player.getUniqueId(), existing);
                    states.remove(player.getUniqueId());
                    messages.send(player, "duty.left");
                }
                continue;
            }

            DutyZone zone = zoneOptional.get();
            DutyState state = states.get(player.getUniqueId());
            if (state == null || !state.zone.id().equals(zone.id())) {
                if (state != null) {
                    flushState(player.getUniqueId(), state);
                }
                state = new DutyState(zone, player.getLocation().clone());
                state.nextRewardAt = now + zone.intervalSeconds() * 1000L;
                boolean requiresMovement = config.get(ConfigManager.POLICE)
                        .getBoolean("duty.require-movement", true) && zone.requireMovement();
                // Do not grant a grace reward for simply standing in the zone:
                // the first payout requires an observed movement.
                state.lastMoveAt = requiresMovement
                        ? now - zone.movementTimeoutSeconds() * 1000L : now;
                state.lastDutyFlush = now;
                states.put(player.getUniqueId(), state);
            }

            Location location = player.getLocation();
            double threshold = Math.max(0.05,
                    config.get(ConfigManager.POLICE).getDouble("duty.movement-threshold", 0.5));
            if (state.lastLocation == null) {
                // The first observation only establishes a baseline. Movement is
                // measured from the next tick, so entering by teleport does not
                // count as activity for the anti-AFK check.
                state.lastLocation = location.clone();
            } else if (state.lastLocation.getWorld() != location.getWorld()
                    || state.lastLocation.distanceSquared(location) >= threshold * threshold) {
                state.lastMoveAt = now;
                state.observedMovement = true;
                state.lastLocation = location.clone();
            }

            boolean requireMovement = config.get(ConfigManager.POLICE)
                    .getBoolean("duty.require-movement", true) && zone.requireMovement();
            long timeout = zone.movementTimeoutSeconds() * 1000L;
            boolean active = !requireMovement
                    || (state.observedMovement && now - state.lastMoveAt <= timeout);

            if (!active) {
                if (now - state.lastAfkWarning > 10_000L) {
                    state.lastAfkWarning = now;
                    messages.send(player, "duty.afk");
                }
                if (config.get(ConfigManager.POLICE).getBoolean("duty.show-countdown", true)) {
                    player.sendActionBar(ItemUtil.text(messages.format("duty.afk")));
                }
                continue;
            }

            state.unsavedSeconds++;
            if (now - state.lastDutyFlush >= Math.max(5,
                    config.get(ConfigManager.POLICE).getInt("duty.save-interval-seconds", 30)) * 1000L) {
                flushState(player.getUniqueId(), state);
                state.lastDutyFlush = now;
            }

            long remaining = Math.max(0, (state.nextRewardAt - now) / 1000L);
            if (now >= state.nextRewardAt) {
                if (zone.reward() > 0 && economy.pay(player, zone.reward(), "DUTY_REWARD", zone.id())) {
                    PlayerProfile profile = profiles.getOrCreate(player);
                    profiles.addPoliceEarnings(profile, zone.reward());
                    messages.send(player, "duty.reward",
                            "amount", economy.format(zone.reward()),
                            "zone", zone.displayName());
                }
                state.nextRewardAt = now + zone.intervalSeconds() * 1000L;
                remaining = zone.intervalSeconds();
            }

            if (config.get(ConfigManager.POLICE).getBoolean("duty.show-countdown", true)) {
                player.sendActionBar(ItemUtil.text(messages.format("duty.next-reward",
                        "time", Formats.duration(remaining), "zone", zone.displayName())));
            }
        }
    }

    private void removeState(UUID uuid, boolean flush) {
        DutyState state = states.remove(uuid);
        if (state != null && flush) {
            flushState(uuid, state);
        }
    }

    private void flushState(UUID uuid, DutyState state) {
        if (state.unsavedSeconds <= 0) {
            return;
        }
        long seconds = state.unsavedSeconds;
        state.unsavedSeconds = 0;
        profiles.get(uuid).ifPresent(profile -> profiles.addDutySeconds(profile, seconds));
        database.addDutyZoneTime(state.zone.id(), uuid, seconds);
    }

    public void flushAll() {
        for (Map.Entry<UUID, DutyState> entry : new HashMap<>(states).entrySet()) {
            flushState(entry.getKey(), entry.getValue());
        }
        states.clear();
    }

    public void handleQuit(Player player) {
        DutyState state = states.remove(player.getUniqueId());
        if (state != null) {
            flushState(player.getUniqueId(), state);
        }
    }

    public void handleDeath(Player player) {
        removeState(player.getUniqueId(), true);
    }

    private static final class DutyState {
        private final DutyZone zone;
        private Location lastLocation;
        private long lastMoveAt;
        private long nextRewardAt;
        private long lastAfkWarning;
        private long lastDutyFlush;
        private long unsavedSeconds;
        private boolean observedMovement;

        private DutyState(DutyZone zone, Location location) {
            this.zone = zone;
            this.lastLocation = null;
        }
    }
}
