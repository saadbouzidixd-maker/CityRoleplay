package dev.cityroleplay.integration;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LuckPermsBridge {

    void setup();

    boolean available();

    String primaryGroup(Player player);

    boolean inGroup(Player player, String group);

    boolean groupExists(String group);

    CompletableFuture<Boolean> addToGroup(UUID uuid, String group);

    CompletableFuture<Boolean> removeFromGroup(UUID uuid, String group);
}
