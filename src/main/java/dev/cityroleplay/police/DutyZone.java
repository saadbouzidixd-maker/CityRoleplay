package dev.cityroleplay.police;

import dev.cityroleplay.util.Cuboid;

import java.util.Locale;

public record DutyZone(
        String id,
        String displayName,
        Cuboid cuboid,
        double reward,
        long intervalSeconds,
        String permission,
        String group,
        boolean enabled,
        boolean requireMovement,
        int movementTimeoutSeconds
) {

    public boolean eligibleFor(String playerPermission, boolean inGroup) {
        if (!enabled) {
            return false;
        }
        if (permission != null && !permission.isBlank() && !playerPermission.equalsIgnoreCase(permission)) {
            return false;
        }
        return group == null || group.isBlank() || inGroup;
    }

    public boolean matchesPermission(java.util.function.Predicate<String> predicate) {
        return permission == null || permission.isBlank() || predicate.test(permission);
    }

    public String key() {
        return id.toLowerCase(Locale.ROOT);
    }
}
