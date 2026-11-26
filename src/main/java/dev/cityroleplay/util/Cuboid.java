package dev.cityroleplay.util;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Inclusive, normalized cuboid region. Coordinates are block coordinates.
 */
public record Cuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Cuboid {
        if (minX > maxX) {
            int swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minY > maxY) {
            int swap = minY;
            minY = maxY;
            maxY = swap;
        }
        if (minZ > maxZ) {
            int swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }
    }

    public static Cuboid of(Location first, Location second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Both corners are required");
        }
        if (first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().getName().equals(second.getWorld().getName())) {
            throw new IllegalArgumentException("Both corners must be in the same world");
        }
        int x1 = first.getBlockX();
        int y1 = first.getBlockY();
        int z1 = first.getBlockZ();
        int x2 = second.getBlockX();
        int y2 = second.getBlockY();
        int z2 = second.getBlockZ();
        return new Cuboid(first.getWorld().getName(),
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return contains(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }

    public boolean contains(String worldName, double x, double y, double z) {
        return world.equals(worldName)
                && x >= minX && x <= maxX + 1.0
                && y >= minY && y <= maxY + 1.0
                && z >= minZ && z <= maxZ + 1.0;
    }

    public boolean intersects(Cuboid other) {
        return other != null
                && world.equals(other.world)
                && minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public Location center(World bukkitWorld) {
        return new Location(bukkitWorld,
                (minX + maxX) / 2.0 + 0.5,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0 + 0.5);
    }

    public String locationString() {
        return world + " [" + minX + "," + minY + "," + minZ + "] - [" + maxX + "," + maxY + "," + maxZ + "]";
    }
}
