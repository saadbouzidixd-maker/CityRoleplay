package dev.cityroleplay.profile;

import dev.cityroleplay.db.Database;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Profiles are cached in memory for synchronous gameplay checks (combat, GUI
 * clicks) and persisted asynchronously on a single database thread.
 */
public final class ProfileService {

    private final JavaPlugin plugin;
    private final Database database;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, UUID> names = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final java.util.Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final double startingBalance;

    public ProfileService(JavaPlugin plugin, Database database) {
        this(plugin, database, 0);
    }

    public ProfileService(JavaPlugin plugin, Database database, double startingBalance) {
        this.plugin = plugin;
        this.database = database;
        this.startingBalance = Math.max(0, startingBalance);
    }

    public void loadAll() {
        try {
            for (PlayerProfile profile : database.loadProfilesSync()) {
                profiles.put(profile.uuid(), profile);
                names.put(profile.name().toLowerCase(Locale.ROOT), profile.uuid());
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load player profiles", exception);
        }
    }

    public PlayerProfile getOrCreate(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) {
            profile = new PlayerProfile(player.getUniqueId(), player.getName());
            profile.balance(startingBalance);
            profiles.put(profile.uuid(), profile);
            names.put(profile.name().toLowerCase(Locale.ROOT), profile.uuid());
            database.saveProfile(profile);
        }
        return profile;
    }

    public PlayerProfile getOrCreate(OfflinePlayer player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) {
            String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
            profile = new PlayerProfile(player.getUniqueId(), name);
            profile.balance(startingBalance);
            profiles.put(profile.uuid(), profile);
            names.put(profile.name().toLowerCase(Locale.ROOT), profile.uuid());
            database.saveProfile(profile);
        }
        return profile;
    }

    public PlayerProfile getOrCreate(UUID uuid, String name) {
        PlayerProfile profile = profiles.get(uuid);
        if (profile == null) {
            profile = new PlayerProfile(uuid, name);
            profile.balance(startingBalance);
            profiles.put(uuid, profile);
            names.put(name.toLowerCase(Locale.ROOT), uuid);
            database.saveProfile(profile);
        }
        return profile;
    }

    public Optional<PlayerProfile> get(UUID uuid) {
        return Optional.ofNullable(profiles.get(uuid));
    }

    public Optional<PlayerProfile> get(String name) {
        UUID uuid = names.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? Optional.empty() : get(uuid);
    }

    public Collection<PlayerProfile> all() {
        return new ArrayList<>(profiles.values());
    }

    public List<PlayerProfile> sortedByName() {
        List<PlayerProfile> list = new ArrayList<>(profiles.values());
        list.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        return list;
    }

    public void onJoin(Player player, boolean police) {
        PlayerProfile profile = getOrCreate(player);
        profile.name(player.getName());
        profile.lastSeen(System.currentTimeMillis());
        names.put(player.getName().toLowerCase(Locale.ROOT), profile.uuid());
        if (police && profile.policeSince() <= 0) {
            profile.policeSince(System.currentTimeMillis());
        }
        save(profile);
    }

    public void onQuit(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile != null) {
            profile.lastSeen(System.currentTimeMillis());
            save(profile);
        }
    }

    public void save(PlayerProfile profile) {
        dirty.add(profile.uuid());
        database.saveProfile(profile);
    }

    public void markDirty(PlayerProfile profile) {
        dirty.add(profile.uuid());
    }

    public void flushDirty() {
        for (UUID uuid : dirty) {
            PlayerProfile profile = profiles.get(uuid);
            if (profile != null) {
                database.saveProfile(profile);
            }
        }
        dirty.clear();
    }

    public void saveAll() {
        for (PlayerProfile profile : profiles.values()) {
            database.saveProfile(profile);
        }
        dirty.clear();
    }

    public void addKills(PlayerProfile profile, int amount) {
        profile.kills(profile.kills() + amount);
        markDirty(profile);
    }

    public void addArrests(PlayerProfile profile, int amount) {
        profile.arrests(profile.arrests() + amount);
        markDirty(profile);
    }

    public void addDutySeconds(PlayerProfile profile, long seconds) {
        profile.dutySeconds(profile.dutySeconds() + seconds);
        markDirty(profile);
    }

    public void addRobberies(PlayerProfile profile, int amount) {
        profile.robberies(profile.robberies() + amount);
        markDirty(profile);
    }

    public void addPoliceEarnings(PlayerProfile profile, double amount) {
        profile.policeEarnings(profile.policeEarnings() + amount);
        markDirty(profile);
    }

    public void resetPolice(PlayerProfile profile) {
        profile.kills(0);
        profile.arrests(0);
        profile.dutySeconds(0);
        profile.policeEarnings(0);
        save(profile);
    }

    public void resetCriminal(PlayerProfile profile) {
        profile.criminalFlag(false);
        profile.wantedUntil(0);
        profile.criminalSince(0);
        profile.robberies(0);
        save(profile);
    }

    public Optional<PlayerProfile> findOffline(String name) {
        Optional<PlayerProfile> cached = get(name);
        if (cached.isPresent()) {
            return cached;
        }
        try {
            Optional<PlayerProfile> loaded = database.findProfile(name).join();
            loaded.ifPresent(profile -> {
                profiles.put(profile.uuid(), profile);
                names.put(profile.name().toLowerCase(Locale.ROOT), profile.uuid());
            });
            return loaded;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not look up profile " + name, exception);
            return Optional.empty();
        }
    }
}
