package dev.cityroleplay.selection;

import dev.cityroleplay.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SelectionManager {

    private final Map<UUID, Location> first = new HashMap<>();
    private final Map<UUID, Location> second = new HashMap<>();

    public void setFirst(Player player, Location location) {
        first.put(player.getUniqueId(), location.clone());
    }

    public void setSecond(Player player, Location location) {
        second.put(player.getUniqueId(), location.clone());
    }

    public Optional<Location> first(Player player) {
        return Optional.ofNullable(first.get(player.getUniqueId()));
    }

    public Optional<Location> second(Player player) {
        return Optional.ofNullable(second.get(player.getUniqueId()));
    }

    public Optional<Cuboid> selection(Player player) {
        Location one = first.get(player.getUniqueId());
        Location two = second.get(player.getUniqueId());
        if (one == null || two == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Cuboid.of(one, two));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void clear(Player player) {
        first.remove(player.getUniqueId());
        second.remove(player.getUniqueId());
    }
}
