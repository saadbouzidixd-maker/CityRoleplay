package dev.cityroleplay.robbery;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.criminal.CriminalService;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.police.DutyZoneManager;
import dev.cityroleplay.police.PoliceService;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.util.Cuboid;
import dev.cityroleplay.util.Formats;
import dev.cityroleplay.util.ItemUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RobberyManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ProfileService profiles;
    private final EconomyManager economy;
    private final Database database;
    private final CriminalService criminals;
    private final PoliceService police;
    private final DutyZoneManager dutyZones;

    private final Map<String, RobberyZone> zones = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, Set<UUID>> occupants = new HashMap<>();
    private BukkitTask task;

    public RobberyManager(JavaPlugin plugin, ConfigManager config, Messages messages,
                          ProfileService profiles, EconomyManager economy, Database database,
                          CriminalService criminals, PoliceService police, DutyZoneManager dutyZones) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.economy = economy;
        this.database = database;
        this.criminals = criminals;
        this.police = police;
        this.dutyZones = dutyZones;
    }

    public void load() {
        zones.clear();
        sessions.clear();
        occupants.clear();
        cooldowns.clear();
        YamlConfiguration yaml = config.get(ConfigManager.ROBBERY);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                try {
                    Cuboid cuboid = new Cuboid(
                            section.getString("world", "world"),
                            section.getInt("min.x"), section.getInt("min.y"), section.getInt("min.z"),
                            section.getInt("max.x"), section.getInt("max.y"), section.getInt("max.z"));
                    zones.put(id.toLowerCase(Locale.ROOT), new RobberyZone(
                            id.toLowerCase(Locale.ROOT),
                            section.getString("display-name", id),
                            cuboid,
                            Math.max(5, section.getLong("duration-seconds", 300)),
                            Math.max(0, section.getDouble("reward", 500)),
                            Math.max(0, section.getLong("cooldown-seconds", 1800)),
                            section.getBoolean("enabled", true),
                            Math.max(1, section.getInt("min-online-players",
                                    config.get(ConfigManager.ROBBERY)
                                            .getInt("robbery.min-online-players", 1))),
                            Math.max(1, section.getInt("max-robbers",
                                    config.get(ConfigManager.ROBBERY)
                                            .getInt("robbery.max-robbers", 1))),
                            section.getBoolean("police-notify", true)
                    ));
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Invalid robbery zone '" + id + "': " + exception.getMessage());
                }
            }
        }
        cooldowns.putAll(database.loadRobberyCooldowns().join());
        plugin.getLogger().info("Loaded " + zones.size() + " robbery zones.");
    }

    public void start() {
        stop();
        int interval = Math.max(10, config.get(ConfigManager.ROBBERY).getInt("robbery.update-interval-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            cancel(uuid, false);
        }
    }

    public List<RobberyZone> zones() {
        return new ArrayList<>(zones.values());
    }

    public Optional<RobberyZone> zone(String id) {
        return Optional.ofNullable(zones.get(id.toLowerCase(Locale.ROOT)));
    }

    public long cooldownRemaining(String zoneId) {
        Long until = cooldowns.get(zoneId.toLowerCase(Locale.ROOT));
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }

    public void setCooldown(String zoneId, long until) {
        cooldowns.put(zoneId.toLowerCase(Locale.ROOT), until);
    }

    public Optional<RobberyZone> zoneAt(Player player) {
        for (RobberyZone zone : zones.values()) {
            if (zone.enabled() && zone.cuboid().contains(player.getLocation())) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancel(event.getEntity().getUniqueId(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                cancel(uuid, false);
                continue;
            }
            Session session = sessions.get(uuid);
            RobberyZone zone = zones.get(session.zoneId);
            if (zone == null || !zone.enabled()) {
                cancel(uuid, true);
                continue;
            }
            if (!zone.cuboid().contains(player.getLocation())) {
                if (config.get(ConfigManager.ROBBERY).getBoolean("robbery.reset-on-leave", true)) {
                    messages.send(player, "robbery.cancelled-left");
                    cancel(uuid, true);
                } else {
                    updateBossBar(session, true);
                }
                continue;
            }

            Location location = player.getLocation();
            if (session.lastLocation == null || session.lastLocation.getWorld() != location.getWorld()
                    || session.lastLocation.distanceSquared(location) >= 0.25) {
                session.lastMoveAt = now;
                session.lastLocation = location.clone();
            }
            long afkTimeout = Math.max(10,
                    config.get(ConfigManager.ROBBERY).getInt("robbery.afk-timeout-seconds", 60)) * 1000L;
            if (now - session.lastMoveAt > afkTimeout) {
                messages.send(player, "robbery.cancelled-afk");
                cancel(uuid, true);
                continue;
            }

            session.progressSeconds++;
            if (session.progressSeconds >= zone.durationSeconds()) {
                complete(player, session, zone);
                continue;
            }
            updateBossBar(session, false);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sessions.containsKey(player.getUniqueId())) {
                continue;
            }
            Optional<RobberyZone> optional = zoneAt(player);
            if (optional.isEmpty()) {
                continue;
            }
            attemptStart(player, optional.get());
        }
    }

    private void attemptStart(Player player, RobberyZone zone) {
        if (!player.hasPermission("city.robbery") && !player.hasPermission("city.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        long cooldown = cooldownRemaining(zone.id());
        if (cooldown > 0) {
            messages.send(player, "robbery.cooldown", "time", Formats.duration(cooldown));
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        if (online < Math.max(1, zone.minOnlinePlayers())) {
            messages.send(player, "robbery.min-players", "count", Integer.toString(zone.minOnlinePlayers()));
            return;
        }
        Set<UUID> current = occupants.computeIfAbsent(zone.id(), ignored -> new HashSet<>());
        if (current.size() >= zone.maxRobbers()) {
            messages.send(player, "robbery.occupied");
            return;
        }
        if (config.get(ConfigManager.ROBBERY).getBoolean("robbery.block-while-police-on-duty", false)) {
            for (Player officer : Bukkit.getOnlinePlayers()) {
                if (police.isPolice(officer) && dutyZones.isInAnyZone(officer)) {
                    messages.send(player, "robbery.police-blocked");
                    return;
                }
            }
        }
        Session session = new Session(zone.id(), System.currentTimeMillis(), player.getLocation().clone());
        BossBar bossBar = BossBar.bossBar(ItemUtil.text("&cRobbery"), 0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        session.bossBar = bossBar;
        sessions.put(player.getUniqueId(), session);
        current.add(player.getUniqueId());
        player.showBossBar(bossBar);
        messages.send(player, "robbery.started",
                "zone", zone.displayName(),
                "duration", Formats.duration(zone.durationSeconds()));
        if (config.get(ConfigManager.CRIMINALS).getBoolean("criminals.criminal-on-robbery", true)) {
            criminals.setCriminal(player, "robbery");
        }
        if (zone.policeNotify() && config.get(ConfigManager.ROBBERY).getBoolean("robbery.notify-police", true)) {
            alertPolice(zone, player.getLocation());
        }
    }

    private void alertPolice(RobberyZone zone, Location location) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (police.isPolice(online)) {
                messages.send(online, "robbery.police-alert",
                        "zone", zone.displayName(),
                        "location", location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
            }
        }
    }

    private void complete(Player player, Session session, RobberyZone zone) {
        if (session.completing) {
            return;
        }
        session.completing = true;
        long until = System.currentTimeMillis() + zone.cooldownSeconds() * 1000L;
        database.completeRobbery(zone.id(), player.getUniqueId(), zone.reward(), until)
                .whenComplete((claimed, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().warning("Robbery completion failed: " + error.getMessage());
                        messages.send(player, "database-error");
                        cancel(player.getUniqueId(), true);
                        return;
                    }
                    if (!Boolean.TRUE.equals(claimed)) {
                        messages.send(player, "robbery.cooldown",
                                "time", Formats.duration(cooldownRemaining(zone.id())));
                        cancel(player.getUniqueId(), true);
                        return;
                    }
                    cooldowns.put(zone.id(), until);
                    if (zone.reward() > 0) {
                        economy.pay(player, zone.reward(), "ROBBERY_REWARD", zone.id());
                    }
                    PlayerProfile profile = profiles.getOrCreate(player);
                    profiles.addRobberies(profile, 1);
                    criminals.setCriminal(player, "robbery");
                    criminals.handleWitnessedRobbery(player, zone.displayName());
                    messages.send(player, "robbery.completed",
                            "amount", economy.format(zone.reward()),
                            "zone", zone.displayName());
                    alertPolice(zone, player.getLocation());
                    cancel(player.getUniqueId(), false);
                }));
    }

    private void updateBossBar(Session session, boolean paused) {
        if (session.bossBar == null) {
            return;
        }
        RobberyZone zone = zones.get(session.zoneId);
        if (zone == null) {
            return;
        }
        float progress = Math.min(1f, (float) session.progressSeconds / (float) zone.durationSeconds());
        session.bossBar.progress(progress);
        session.bossBar.name(ItemUtil.text(messages.format("robbery.bossbar",
                "zone", zone.displayName(),
                "progress", Integer.toString(Math.round(progress * 100)))));
        if (paused) {
            session.bossBar.color(BossBar.Color.YELLOW);
        }
    }

    private void cancel(UUID uuid, boolean notify) {
        Session session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        if (session.bossBar != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.hideBossBar(session.bossBar);
            }
        }
        Set<UUID> current = occupants.get(session.zoneId);
        if (current != null) {
            current.remove(uuid);
            if (current.isEmpty()) {
                occupants.remove(session.zoneId);
            }
        }
    }

    public boolean addZone(RobberyZone zone) {
        if (zones.containsKey(zone.id())) {
            return false;
        }
        for (RobberyZone existing : zones.values()) {
            if (existing.cuboid().intersects(zone.cuboid())) {
                return false;
            }
        }
        zones.put(zone.id(), zone);
        saveZones();
        return true;
    }

    public boolean removeZone(String id) {
        RobberyZone removed = zones.remove(id.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        saveZones();
        return true;
    }

    public void saveZones() {
        YamlConfiguration yaml = config.get(ConfigManager.ROBBERY);
        yaml.set("zones", null);
        for (RobberyZone zone : zones.values()) {
            String path = "zones." + zone.id() + ".";
            yaml.set(path + "display-name", zone.displayName());
            yaml.set(path + "world", zone.cuboid().world());
            yaml.set(path + "min.x", zone.cuboid().minX());
            yaml.set(path + "min.y", zone.cuboid().minY());
            yaml.set(path + "min.z", zone.cuboid().minZ());
            yaml.set(path + "max.x", zone.cuboid().maxX());
            yaml.set(path + "max.y", zone.cuboid().maxY());
            yaml.set(path + "max.z", zone.cuboid().maxZ());
            yaml.set(path + "duration-seconds", zone.durationSeconds());
            yaml.set(path + "reward", zone.reward());
            yaml.set(path + "cooldown-seconds", zone.cooldownSeconds());
            yaml.set(path + "enabled", zone.enabled());
            yaml.set(path + "min-online-players", zone.minOnlinePlayers());
            yaml.set(path + "max-robbers", zone.maxRobbers());
            yaml.set(path + "police-notify", zone.policeNotify());
        }
        config.save(ConfigManager.ROBBERY);
    }

    public void replaceZone(RobberyZone zone) {
        zones.put(zone.id(), zone);
        saveZones();
    }

    private static final class Session {
        private final String zoneId;
        private final long startedAt;
        private Location lastLocation;
        private long lastMoveAt;
        private long progressSeconds;
        private BossBar bossBar;
        private boolean completing;

        private Session(String zoneId, long startedAt, Location location) {
            this.zoneId = zoneId;
            this.startedAt = startedAt;
            this.lastLocation = location;
            this.lastMoveAt = startedAt;
        }
    }
}
