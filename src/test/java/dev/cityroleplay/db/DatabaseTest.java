package dev.cityroleplay.db;

import dev.cityroleplay.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(tempDir.resolve("test.db").toFile(), Logger.getLogger("DatabaseTest"));
        database.initialize();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void profileRoundTripPersistsAllFields() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = new PlayerProfile(uuid, "Alice");
        profile.policeSince(1000);
        profile.kills(4);
        profile.arrests(3);
        profile.dutySeconds(600);
        profile.policeEarnings(250.5);
        profile.criminalFlag(true);
        profile.wantedUntil(999999);
        profile.criminalSince(500);
        profile.robberies(2);
        profile.balance(1234.56);
        profile.prisonUntil(888888);
        profile.prisonReason("test");

        database.saveProfile(profile).join();
        List<PlayerProfile> loaded = database.loadProfilesSync();

        assertEquals(1, loaded.size());
        PlayerProfile read = loaded.getFirst();
        assertEquals(uuid, read.uuid());
        assertEquals("Alice", read.name());
        assertEquals(4, read.kills());
        assertEquals(3, read.arrests());
        assertEquals(600, read.dutySeconds());
        assertEquals(250.5, read.policeEarnings(), 0.001);
        assertTrue(read.criminalFlag());
        assertEquals(999999, read.wantedUntil());
        assertEquals(2, read.robberies());
        assertEquals(1234.56, read.balance(), 0.001);
        assertEquals(888888, read.prisonUntil());
        assertEquals("test", read.prisonReason());
    }

    @Test
    void transactionHistoryIsOrderedAndLimited() {
        UUID uuid = UUID.randomUUID();
        database.insertTransaction(uuid, "FIRST", 10, "one").join();
        database.insertTransaction(uuid, "SECOND", 20, "two").join();

        List<TransactionRecord> rows = database.recentTransactions(uuid, 1).join();

        assertEquals(1, rows.size());
        assertEquals("SECOND", rows.getFirst().type());
        assertEquals(20, rows.getFirst().amount(), 0.001);
    }

    @Test
    void graveCanOnlyBeHiddenOnce() {
        String world = "world";
        database.registerGrave(world, 10, 64, 10, UUID.randomUUID()).join();
        assertEquals(1, database.loadGraves().join().size());

        UUID actor = UUID.randomUUID();
        assertTrue(database.hideGrave(world, 10, 64, 10, actor, 100).join());
        assertFalse(database.hideGrave(world, 10, 64, 10, actor, 100).join());

        GraveRecord record = database.loadGraves().join().getFirst();
        assertTrue(record.hidden());
        assertTrue(database.removeGrave(world, 10, 64, 10).join());
    }

    @Test
    void robberyCooldownClaimIsSingleWinner() {
        String zone = "supermarket";
        UUID robber = UUID.randomUUID();
        long until = System.currentTimeMillis() + 60_000;

        assertTrue(database.completeRobbery(zone, robber, 500, until).join());
        assertFalse(database.completeRobbery(zone, robber, 500, until).join());
        assertTrue(database.loadRobberyCooldowns().join().get(zone) > System.currentTimeMillis());
    }

    @Test
    void purchaseCountsSerialsAndDutyTimePersist() {
        UUID uuid = UUID.randomUUID();
        database.recordPurchase(uuid, "arrest-baton", 800).join();
        database.recordPurchase(uuid, "arrest-baton", 800).join();
        assertEquals(2, database.purchaseCount(uuid, "arrest-baton").join());

        database.recordToolSerial("serial-1", "arrest-baton", uuid).join();
        assertTrue(database.loadToolSerials().join().contains("serial-1"));

        database.addDutyZoneTime("station", uuid, 30).join();
        database.addDutyZoneTime("station", uuid, 15).join();
        assertEquals(45, database.dutyZoneSeconds("station", uuid).join());
    }

    @Test
    void lookupByNameIsCaseInsensitive() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Bob");
        database.saveProfile(profile).join();

        assertTrue(database.findProfile("bob").join().isPresent());
        assertTrue(database.findProfile("nobody").join().isEmpty());
    }
}
