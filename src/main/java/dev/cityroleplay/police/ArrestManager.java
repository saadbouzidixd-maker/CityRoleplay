package dev.cityroleplay.police;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.criminal.CriminalService;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.prison.PrisonManager;
import dev.cityroleplay.util.ItemUtil;
import dev.cityroleplay.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ArrestManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final PoliceService police;
    private final PrisonManager prison;
    private final ProfileService profiles;
    private final Database database;
    private final CriminalService criminals;
    private final EconomyManager economy;
    private final Scheduler scheduler;

    private final Map<UUID, ArrestSession> sessions = new HashMap<>();
    private final Map<String, Long> fineCooldowns = new HashMap<>();
    private BukkitTask task;

    public ArrestManager(JavaPlugin plugin, ConfigManager config, Messages messages, PoliceService police,
                         PrisonManager prison, ProfileService profiles, Database database,
                         CriminalService criminals, EconomyManager economy, Scheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.police = police;
        this.prison = prison;
        this.profiles = profiles;
        this.database = database;
        this.criminals = criminals;
        this.economy = economy;
        this.scheduler = scheduler;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        for (ArrestSession session : new HashMap<>(sessions).values()) {
            restore(session);
        }
        sessions.clear();
        stop();
    }

    public Optional<ArrestSession> session(UUID criminal) {
        return Optional.ofNullable(sessions.get(criminal));
    }

    public boolean isArrested(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Called for a validated baton hit. Damage is always cancelled: the baton
     * arrests criminals and fines officers who hit citizens.
     */
    public void handleBatonHit(Player officer, Player target, EntityDamageByEntityEvent event) {
        event.setCancelled(true);
        if (officer.getUniqueId().equals(target.getUniqueId())) {
            messages.send(officer, "arrest.self");
            return;
        }
        if (criminals.isCriminal(target)) {
            begin(officer, target);
            return;
        }
        fineForCitizen(officer, target);
    }

    public void begin(Player officer, Player target) {
        if (sessions.containsKey(target.getUniqueId())) {
            messages.send(officer, "arrest.already");
            return;
        }
        ArrestSession session = new ArrestSession(officer.getUniqueId(), target.getUniqueId(),
                target.getLocation().clone(), System.currentTimeMillis());
        session.walkSpeed = target.getWalkSpeed();
        session.flySpeed = target.getFlySpeed();
        session.allowFlight = target.getAllowFlight();
        session.flying = target.isFlying();
        target.setWalkSpeed(0f);
        target.setFlySpeed(0f);
        target.setFlying(false);
        target.setAllowFlight(false);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 900, 250, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 900, 128, false, false, false));
        sessions.put(target.getUniqueId(), session);
        messages.send(officer, "arrest.started", "player", target.getName());
        messages.send(target, "arrest.criminal-notified", "officer", officer.getName());
        database.recordArrest(officer.getUniqueId(), target.getUniqueId(), "BATON");
    }

    public void toggleEscort(Player officer, Player target) {
        ArrestSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            messages.send(officer, "arrest.not-arrested");
            return;
        }
        if (!session.officer.equals(officer.getUniqueId())) {
            messages.send(officer, "arrest.already");
            return;
        }
        session.escorting = !session.escorting;
        if (session.escorting) {
            messages.send(officer, "arrest.escort-start", "player", target.getName());
        } else {
            messages.send(officer, "arrest.escort-stop");
        }
    }

    public void jail(Player officer, Player target) {
        ArrestSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            messages.send(officer, "arrest.not-arrested");
            return;
        }
        if (!session.officer.equals(officer.getUniqueId()) && !officer.hasPermission("city.admin")) {
            messages.send(officer, "arrest.already");
            return;
        }
        double distance = Math.max(1, config.get(ConfigManager.POLICE).getDouble("arrest.jail-distance", 10));
        if (officer.getLocation().getWorld() != target.getLocation().getWorld()
                || officer.getLocation().distance(target.getLocation()) > distance) {
            messages.send(officer, "arrest.far", "distance", String.format(java.util.Locale.US, "%.0f", distance));
            return;
        }
        int minutes = config.get(ConfigManager.PRISON).getInt("prison.arrest-sentence-minutes", 15);
        prison.jail(target, minutes, "arrest", officer.getName());
        profiles.addArrests(profiles.getOrCreate(officer), 1);
        police.reward(officer, police.arrestReward(), "ARREST_REWARD", target.getName());
        database.recordArrest(officer.getUniqueId(), target.getUniqueId(), "JAIL");
        messages.send(officer, "arrest.jailed", "player", target.getName());
        messages.send(officer, "arrest.reward", "amount", economy.format(police.arrestReward()));
        restore(session);
        sessions.remove(target.getUniqueId());
    }

    public void release(Player target, boolean notify) {
        ArrestSession session = sessions.remove(target.getUniqueId());
        if (session == null) {
            if (notify) {
                messages.send(target, "arrest.not-arrested");
            }
            return;
        }
        restore(session);
        if (notify) {
            messages.send(target, "prison.release");
        }
    }

    public void releaseByAdmin(Player admin, Player target) {
        if (!sessions.containsKey(target.getUniqueId())) {
            messages.send(admin, "arrest.not-arrested");
            return;
        }
        release(target, true);
        messages.send(admin, "arrest.released", "player", target.getName());
    }

    private void fineForCitizen(Player officer, Player target) {
        double amount = police.citizenFine();
        if (amount <= 0) {
            messages.send(officer, "arrest.citizen-fine", "amount", economy.format(0));
            return;
        }
        String key = officer.getUniqueId() + "|" + target.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0,
                config.get(ConfigManager.POLICE).getInt("police.citizen-fine-cooldown-seconds", 30)) * 1000L;
        Long last = fineCooldowns.get(key);
        if (last != null && now - last < cooldown) {
            return;
        }
        fineCooldowns.put(key, now);
        if (police.fine(officer, amount, "Hit citizen " + target.getName())) {
            messages.send(officer, "arrest.citizen-fine",
                    "amount", economy.format(amount));
            messages.send(target, "arrest.citizen-notify", "officer", officer.getName());
        } else {
            messages.send(officer, "police.purchase-failed", "reason", "insufficient funds for the fine");
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        int maxSeconds = Math.max(10, config.get(ConfigManager.POLICE).getInt("arrest.max-seconds", 300));
        for (Map.Entry<UUID, ArrestSession> entry : new HashMap<>(sessions).entrySet()) {
            ArrestSession session = entry.getValue();
            Player target = Bukkit.getPlayer(entry.getKey());
            Player officer = Bukkit.getPlayer(session.officer);
            if (target == null || !target.isOnline()) {
                sessions.remove(entry.getKey());
                continue;
            }
            if (officer == null || !officer.isOnline()) {
                release(target, true);
                continue;
            }
            if (now - session.startedAt > maxSeconds * 1000L) {
                release(target, false);
                messages.send(target, "arrest.timeout");
                messages.send(officer, "arrest.timeout");
                continue;
            }
            if (session.escorting) {
                Location destination = officer.getLocation().clone();
                if (target.getWorld() != destination.getWorld()
                        || target.getLocation().distanceSquared(destination) > 4.0) {
                    session.teleporting = true;
                    try {
                        target.teleport(destination);
                        target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    } finally {
                        session.teleporting = false;
                    }
                }
            }
        }
    }

    private void restore(ArrestSession session) {
        Player target = Bukkit.getPlayer(session.criminal);
        if (target == null) {
            return;
        }
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.removePotionEffect(PotionEffectType.JUMP_BOOST);
        target.setWalkSpeed(session.walkSpeed);
        target.setFlySpeed(session.flySpeed);
        target.setAllowFlight(session.allowFlight);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        ArrestSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.teleporting) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (to.getWorld() != session.anchor.getWorld()
                || to.distanceSquared(session.anchor) > 0.2) {
            Location fixed = session.anchor.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            event.setTo(fixed);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        ArrestSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && !session.teleporting) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "arrest.frozen");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())
                && config.get(ConfigManager.POLICE).getBoolean("arrest.block-commands", true)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "arrest.command-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())
                && config.get(ConfigManager.POLICE).getBoolean("arrest.block-interaction", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())
                && config.get(ConfigManager.POLICE).getBoolean("arrest.block-interaction", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && sessions.containsKey(player.getUniqueId())
                && config.get(ConfigManager.POLICE).getBoolean("arrest.block-damage", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ArrestSession asCriminal = sessions.get(player.getUniqueId());
        if (asCriminal != null) {
            restore(asCriminal);
            sessions.remove(player.getUniqueId());
        }
        for (Map.Entry<UUID, ArrestSession> entry : new HashMap<>(sessions).entrySet()) {
            if (entry.getValue().officer.equals(player.getUniqueId())) {
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null) {
                    release(target, true);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        release(event.getEntity(), false);
    }

    public static final class ArrestSession {
        private final UUID officer;
        private final UUID criminal;
        private final Location anchor;
        private final long startedAt;
        private float walkSpeed = 0.2f;
        private float flySpeed = 0.1f;
        private boolean allowFlight;
        private boolean flying;
        private boolean escorting;
        private boolean teleporting;

        private ArrestSession(UUID officer, UUID criminal, Location anchor, long startedAt) {
            this.officer = officer;
            this.criminal = criminal;
            this.anchor = anchor;
            this.startedAt = startedAt;
        }

        public UUID officer() {
            return officer;
        }

        public UUID criminal() {
            return criminal;
        }

        public boolean escorting() {
            return escorting;
        }
    }
}
