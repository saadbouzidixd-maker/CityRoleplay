package dev.cityroleplay.util;

import org.bukkit.World;

public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(org.bukkit.block.Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(World world, int x, int y, int z) {
        return new BlockKey(world.getName(), x, y, z);
    }

    public String locationString() {
        return world + " " + x + ", " + y + ", " + z;
    }
}
