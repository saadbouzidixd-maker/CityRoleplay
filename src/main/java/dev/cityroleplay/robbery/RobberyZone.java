package dev.cityroleplay.robbery;

import dev.cityroleplay.util.Cuboid;

public record RobberyZone(
        String id,
        String displayName,
        Cuboid cuboid,
        long durationSeconds,
        double reward,
        long cooldownSeconds,
        boolean enabled,
        int minOnlinePlayers,
        int maxRobbers,
        boolean policeNotify
) {
}
