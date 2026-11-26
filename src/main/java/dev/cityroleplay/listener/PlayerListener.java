package dev.cityroleplay.listener;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.criminal.CriminalService;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.police.DutyZoneManager;
import dev.cityroleplay.police.PoliceService;
import dev.cityroleplay.prison.PrisonManager;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ProfileService profiles;
    private final PoliceService police;
    private final CriminalService criminals;
    private final PrisonManager prison;
    private final DutyZoneManager dutyZones;
    private final EconomyManager economy;

    public PlayerListener(JavaPlugin plugin, ConfigManager config, Messages messages,
                          ProfileService profiles, PoliceService police, CriminalService criminals,
                          PrisonManager prison, DutyZoneManager dutyZones, EconomyManager economy) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.police = police;
        this.criminals = criminals;
        this.prison = prison;
        this.dutyZones = dutyZones;
        this.economy = economy;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isPolice = police.isPolice(player);
        profiles.onJoin(player, isPolice);
        police.ensureJoiningDate(player);
        prison.onJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.onQuit(event.getPlayer());
        dutyZones.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        dutyZones.handleDeath(victim);
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        boolean validPoliceKill = police.isPolice(killer) && criminals.isCriminal(victim);
        if (validPoliceKill) {
            PlayerProfile profile = profiles.getOrCreate(killer);
            profiles.addKills(profile, 1);
            police.reward(killer, police.killReward(), "POLICE_KILL_REWARD", victim.getName());
            messages.send(killer, "police.kill-reward",
                    "player", victim.getName(),
                    "amount", economy.format(police.killReward()));
            int minutes = config.get(ConfigManager.PRISON).getInt("prison.death-sentence-minutes", 10);
            prison.scheduleAfterRespawn(victim, minutes, "killed by police", killer.getName());
            return;
        }

        criminals.handleWitnessedKill(victim, killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> prison.applyPending(player));
    }
}
