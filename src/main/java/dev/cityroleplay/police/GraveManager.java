package dev.cityroleplay.police;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.db.GraveRecord;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.util.BlockKey;
import dev.cityroleplay.util.Scheduler;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class GraveManager {

    private final JavaPlugin plugin;
    private final Database database;
    private final Messages messages;
    private final PoliceService police;
    private final EconomyManager economy;
    private final Scheduler scheduler;
    private final Map<BlockKey, GraveRecord> graves = new HashMap<>();

    public GraveManager(JavaPlugin plugin, Database database, Messages messages,
                        PoliceService police, EconomyManager economy, Scheduler scheduler) {
        this.plugin = plugin;
        this.database = database;
        this.messages = messages;
        this.police = police;
        this.economy = economy;
        this.scheduler = scheduler;
    }

    public void load() {
        graves.clear();
        for (GraveRecord record : database.loadGraves().join()) {
            graves.put(record.key(), record);
        }
        plugin.getLogger().info("Loaded " + graves.size() + " registered graves.");
    }

    public Optional<GraveRecord> at(Block block) {
        return Optional.ofNullable(graves.get(BlockKey.of(block)));
    }

    public void register(Block block, Player actor) {
        BlockKey key = BlockKey.of(block);
        if (graves.containsKey(key)) {
            messages.send(actor, "grave.registered", "location", key.locationString());
            return;
        }
        database.registerGrave(key.world(), key.x(), key.y(), key.z(), actor.getUniqueId())
                .whenComplete((inserted, error) -> scheduler.sync(() -> {
                    if (error != null) {
                        messages.send(actor, "database-error");
                        return;
                    }
                    if (Boolean.TRUE.equals(inserted)) {
                        graves.put(key, new GraveRecord(key.world(), key.x(), key.y(), key.z(), false,
                                actor.getUniqueId().toString()));
                    }
                    messages.send(actor, "grave.registered", "location", key.locationString());
                }));
    }

    public void remove(Block block, Player actor) {
        BlockKey key = BlockKey.of(block);
        if (!graves.containsKey(key)) {
            messages.send(actor, "grave.none");
            return;
        }
        database.removeGrave(key.world(), key.x(), key.y(), key.z())
                .whenComplete((removed, error) -> scheduler.sync(() -> {
                    if (error != null) {
                        messages.send(actor, "database-error");
                        return;
                    }
                    graves.remove(key);
                    messages.send(actor, "grave.removed", "location", key.locationString());
                }));
    }

    /**
     * Hides a registered grave exactly once. The database claim is atomic, so
     * racing clicks and restarts cannot pay the same grave twice.
     */
    public CompletableFuture<Boolean> hide(Block block, Player actor, Material hideMaterial, double reward) {
        BlockKey key = BlockKey.of(block);
        GraveRecord record = graves.get(key);
        if (record == null) {
            messages.send(actor, "grave.not-registered");
            return CompletableFuture.completedFuture(false);
        }
        if (record.hidden()) {
            messages.send(actor, "grave.already");
            return CompletableFuture.completedFuture(false);
        }
        return database.hideGrave(key.world(), key.x(), key.y(), key.z(), actor.getUniqueId(), reward)
                .thenApply(hidden -> {
                    if (!Boolean.TRUE.equals(hidden)) {
                        scheduler.sync(() -> messages.send(actor, "grave.already"));
                        return false;
                    }
                    scheduler.sync(() -> {
                        graves.put(key, new GraveRecord(record.world(), record.x(), record.y(),
                                record.z(), true, record.registeredBy()));
                        block.setType(hideMaterial, false);
                        if (reward > 0) {
                            police.reward(actor, reward, "GRAVE_OPERATION", key.locationString());
                            messages.send(actor, "grave.hidden",
                                    "amount", economy.format(reward));
                        } else {
                            messages.send(actor, "grave.hidden", "amount", economy.format(0));
                        }
                    });
                    return true;
                }).exceptionally(throwable -> {
                    plugin.getLogger().warning("Grave hide failed: " + throwable.getMessage());
                    scheduler.sync(() -> messages.send(actor, "database-error"));
                    return false;
                });
    }
}
