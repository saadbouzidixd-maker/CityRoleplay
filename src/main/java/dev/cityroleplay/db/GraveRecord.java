package dev.cityroleplay.db;

import dev.cityroleplay.util.BlockKey;

public record GraveRecord(String world, int x, int y, int z, boolean hidden, String registeredBy) {

    public BlockKey key() {
        return new BlockKey(world, x, y, z);
    }

    public String locationString() {
        return world + " " + x + ", " + y + ", " + z;
    }
}
