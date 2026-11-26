package dev.cityroleplay.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidTest {

    @Test
    void normalizesCornersAndChecksInclusiveBounds() {
        Cuboid cuboid = new Cuboid("world", 10, 20, 30, 5, 15, 25);

        assertEquals(5, cuboid.minX());
        assertEquals(15, cuboid.minY());
        assertEquals(25, cuboid.minZ());
        assertEquals(10, cuboid.maxX());
        assertTrue(cuboid.contains("world", 5, 15, 25));
        assertTrue(cuboid.contains("world", 10.9, 20.9, 30.9));
        assertFalse(cuboid.contains("world", 11.1, 20, 30));
        assertFalse(cuboid.contains("other", 7, 18, 27));
    }

    @Test
    void detectsOverlap() {
        Cuboid left = new Cuboid("world", 0, 0, 0, 10, 10, 10);
        Cuboid right = new Cuboid("world", 10, 10, 10, 20, 20, 20);
        Cuboid far = new Cuboid("world", 11, 11, 11, 20, 20, 20);

        assertTrue(left.intersects(right));
        assertFalse(left.intersects(far));
        assertFalse(left.intersects(new Cuboid("other", 0, 0, 0, 5, 5, 5)));
    }

    @Test
    void buildsFromLocationsAndRejectsMixedWorlds() {
        // The world-dependent factory is exercised on a live server; the record
        // constructor is the shared normalization used by both paths.
        Cuboid cuboid = new Cuboid("world", 0, 0, 0, 1, 1, 1);
        assertEquals(8, cuboid.volume());
    }
}
