package dev.cityroleplay.db;

import dev.cityroleplay.profile.PlayerProfile;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-connection SQLite layer. Every runtime query runs on one dedicated
 * database thread, so the server thread never blocks on JDBC. Startup schema
 * creation and the initial profile load are intentionally synchronous.
 */
public final class Database {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS player_profiles (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                first_seen INTEGER NOT NULL,
                last_seen INTEGER NOT NULL,
                police_since INTEGER NOT NULL DEFAULT 0,
                kills INTEGER NOT NULL DEFAULT 0,
                arrests INTEGER NOT NULL DEFAULT 0,
                duty_seconds INTEGER NOT NULL DEFAULT 0,
                police_earnings REAL NOT NULL DEFAULT 0,
                criminal INTEGER NOT NULL DEFAULT 0,
                wanted_until INTEGER NOT NULL DEFAULT 0,
                criminal_since INTEGER NOT NULL DEFAULT 0,
                robberies INTEGER NOT NULL DEFAULT 0,
                balance REAL NOT NULL DEFAULT 0,
                prison_until INTEGER NOT NULL DEFAULT 0,
                prison_reason TEXT NOT NULL DEFAULT ''
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                detail TEXT,
                created_at INTEGER NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_transactions_uuid ON transactions(uuid, created_at DESC)",
            """
            CREATE TABLE IF NOT EXISTS graves (
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                registered_by TEXT,
                registered_at INTEGER NOT NULL,
                hidden INTEGER NOT NULL DEFAULT 0,
                hidden_by TEXT,
                hidden_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(world, x, y, z)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS grave_ops (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                reward REAL NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(world, x, y, z)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS purchases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                tool_id TEXT NOT NULL,
                price REAL NOT NULL,
                created_at INTEGER NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_purchases ON purchases(uuid, tool_id)",
            """
            CREATE TABLE IF NOT EXISTS tool_serials (
                serial TEXT PRIMARY KEY,
                tool_id TEXT NOT NULL,
                owner TEXT,
                created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arrests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                officer TEXT NOT NULL,
                criminal TEXT NOT NULL,
                kind TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS robberies (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                zone_id TEXT NOT NULL,
                reward REAL NOT NULL,
                created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS robbery_cooldowns (
                zone_id TEXT PRIMARY KEY,
                until INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS duty_zone_time (
                zone_id TEXT NOT NULL,
                uuid TEXT NOT NULL,
                seconds INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(zone_id, uuid)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS prison_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                until INTEGER NOT NULL,
                reason TEXT,
                officer TEXT,
                created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """
    };

    private static final String PROFILE_COLUMNS =
            "uuid,name,first_seen,last_seen,police_since,kills,arrests,duty_seconds,"
                    + "police_earnings,criminal,wanted_until,criminal_since,robberies,balance,"
                    + "prison_until,prison_reason";

    private final File file;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CityRoleplay-Database");
        thread.setDaemon(true);
        return thread;
    });

    private Connection connection;

    public Database(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void initialize() throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Could not create the plugin data directory: " + parent.getAbsolutePath());
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is missing", exception);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            for (String sql : SCHEMA) {
                statement.execute(sql);
            }
        }
    }

    public List<PlayerProfile> loadProfilesSync() throws SQLException {
        List<PlayerProfile> profiles = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT " + PROFILE_COLUMNS + " FROM player_profiles")) {
            while (result.next()) {
                profiles.add(readProfile(result));
            }
        }
        return profiles;
    }

    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return run("save profile " + profile.uuid(), conn -> {
            String sql = """
                    INSERT INTO player_profiles (""" + PROFILE_COLUMNS + """
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name=excluded.name,
                        first_seen=excluded.first_seen,
                        last_seen=excluded.last_seen,
                        police_since=excluded.police_since,
                        kills=excluded.kills,
                        arrests=excluded.arrests,
                        duty_seconds=excluded.duty_seconds,
                        police_earnings=excluded.police_earnings,
                        criminal=excluded.criminal,
                        wanted_until=excluded.wanted_until,
                        criminal_since=excluded.criminal_since,
                        robberies=excluded.robberies,
                        balance=excluded.balance,
                        prison_until=excluded.prison_until,
                        prison_reason=excluded.prison_reason
                    """;
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                bindProfile(statement, profile);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Optional<PlayerProfile>> findProfile(String name) {
        return run("find profile " + name, conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "SELECT " + PROFILE_COLUMNS + " FROM player_profiles WHERE lower(name)=lower(?) LIMIT 1")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readProfile(result)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Void> insertTransaction(UUID uuid, String type, double amount, String detail) {
        return run("insert transaction", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO transactions(uuid,type,amount,detail,created_at) VALUES(?,?,?,?,?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type);
                statement.setDouble(3, amount);
                statement.setString(4, detail == null ? "" : detail);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<TransactionRecord>> recentTransactions(UUID uuid, int limit) {
        return run("recent transactions", conn -> {
            String sql = uuid == null
                    ? "SELECT uuid,type,amount,detail,created_at FROM transactions ORDER BY id DESC LIMIT ?"
                    : "SELECT uuid,type,amount,detail,created_at FROM transactions WHERE uuid=? ORDER BY id DESC LIMIT ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                if (uuid == null) {
                    statement.setInt(1, limit);
                } else {
                    statement.setString(1, uuid.toString());
                    statement.setInt(2, limit);
                }
                try (ResultSet result = statement.executeQuery()) {
                    List<TransactionRecord> rows = new ArrayList<>();
                    while (result.next()) {
                        rows.add(new TransactionRecord(
                                UUID.fromString(result.getString("uuid")),
                                result.getString("type"),
                                result.getDouble("amount"),
                                result.getString("detail"),
                                result.getLong("created_at")));
                    }
                    return rows;
                }
            }
        });
    }

    public CompletableFuture<List<GraveRecord>> loadGraves() {
        return run("load graves", conn -> {
            List<GraveRecord> rows = new ArrayList<>();
            try (Statement statement = conn.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT world,x,y,z,hidden,registered_by FROM graves ORDER BY world,x,z")) {
                while (result.next()) {
                    rows.add(new GraveRecord(
                            result.getString("world"),
                            result.getInt("x"),
                            result.getInt("y"),
                            result.getInt("z"),
                            result.getInt("hidden") == 1,
                            result.getString("registered_by")));
                }
            }
            return rows;
        });
    }

    public CompletableFuture<Boolean> registerGrave(String world, int x, int y, int z, UUID actor) {
        return run("register grave", conn -> {
            try (PreparedStatement statement = conn.prepareStatement("""
                    INSERT OR IGNORE INTO graves(world,x,y,z,registered_by,registered_at,hidden)
                    VALUES(?,?,?,?,?,?,0)
                    """)) {
                statement.setString(1, world);
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                statement.setString(5, actor == null ? "" : actor.toString());
                statement.setLong(6, System.currentTimeMillis());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> removeGrave(String world, int x, int y, int z) {
        return run("remove grave", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "DELETE FROM graves WHERE world=? AND x=? AND y=? AND z=?")) {
                statement.setString(1, world);
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                return statement.executeUpdate() == 1;
            }
        });
    }

    /**
     * Atomically hides a grave once. The unique grave_ops row makes a duplicate
     * reward impossible even if two interactions race or the server restarts.
     */
    public CompletableFuture<Boolean> hideGrave(String world, int x, int y, int z,
                                                UUID actor, double reward) {
        return run("hide grave", conn -> {
            conn.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement statement = conn.prepareStatement("""
                        UPDATE graves SET hidden=1, hidden_by=?, hidden_at=?
                        WHERE world=? AND x=? AND y=? AND z=? AND hidden=0
                        """)) {
                    statement.setString(1, actor == null ? "" : actor.toString());
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setString(3, world);
                    statement.setInt(4, x);
                    statement.setInt(5, y);
                    statement.setInt(6, z);
                    updated = statement.executeUpdate();
                }
                if (updated != 1) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement statement = conn.prepareStatement("""
                        INSERT INTO grave_ops(uuid,world,x,y,z,reward,created_at)
                        VALUES(?,?,?,?,?,?,?)
                        """)) {
                    statement.setString(1, actor == null ? "" : actor.toString());
                    statement.setString(2, world);
                    statement.setInt(3, x);
                    statement.setInt(4, y);
                    statement.setInt(5, z);
                    statement.setDouble(6, reward);
                    statement.setLong(7, System.currentTimeMillis());
                    statement.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException exception) {
                conn.rollback();
                throw exception;
            } finally {
                conn.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Integer> purchaseCount(UUID uuid, String toolId) {
        return run("purchase count", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "SELECT COUNT(*) FROM purchases WHERE uuid=? AND tool_id=?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, toolId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    public CompletableFuture<Void> recordPurchase(UUID uuid, String toolId, double price) {
        return run("record purchase", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO purchases(uuid,tool_id,price,created_at) VALUES(?,?,?,?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, toolId);
                statement.setDouble(3, price);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> recordToolSerial(String serial, String toolId, UUID owner) {
        return run("record tool serial", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "INSERT OR IGNORE INTO tool_serials(serial,tool_id,owner,created_at) VALUES(?,?,?,?)")) {
                statement.setString(1, serial);
                statement.setString(2, toolId);
                statement.setString(3, owner == null ? "" : owner.toString());
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Set<String>> loadToolSerials() {
        return run("load tool serials", conn -> {
            Set<String> serials = new HashSet<>();
            try (Statement statement = conn.createStatement();
                 ResultSet result = statement.executeQuery("SELECT serial FROM tool_serials")) {
                while (result.next()) {
                    serials.add(result.getString(1));
                }
            }
            return serials;
        });
    }

    public CompletableFuture<Void> recordArrest(UUID officer, UUID criminal, String kind) {
        return run("record arrest", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO arrests(officer,criminal,kind,created_at) VALUES(?,?,?,?)")) {
                statement.setString(1, officer == null ? "" : officer.toString());
                statement.setString(2, criminal.toString());
                statement.setString(3, kind);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Map<String, Long>> loadRobberyCooldowns() {
        return run("load robbery cooldowns", conn -> {
            Map<String, Long> cooldowns = new HashMap<>();
            try (Statement statement = conn.createStatement();
                 ResultSet result = statement.executeQuery("SELECT zone_id,until FROM robbery_cooldowns")) {
                while (result.next()) {
                    cooldowns.put(result.getString(1), result.getLong(2));
                }
            }
            return cooldowns;
        });
    }

    /**
     * Records a robbery and its cooldown in one transaction. Returns false when
     * the location is still cooling down, so a payout can never be duplicated.
     */
    public CompletableFuture<Boolean> completeRobbery(String zoneId, UUID uuid, double reward, long cooldownUntil) {
        return run("complete robbery", conn -> {
            conn.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                Long existing = null;
                try (PreparedStatement statement = conn.prepareStatement(
                        "SELECT until FROM robbery_cooldowns WHERE zone_id=?")) {
                    statement.setString(1, zoneId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            existing = result.getLong(1);
                        }
                    }
                }
                if (existing != null && existing > now) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement statement = conn.prepareStatement(
                        "INSERT INTO robberies(uuid,zone_id,reward,created_at) VALUES(?,?,?,?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, zoneId);
                    statement.setDouble(3, reward);
                    statement.setLong(4, now);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = conn.prepareStatement("""
                        INSERT INTO robbery_cooldowns(zone_id,until) VALUES(?,?)
                        ON CONFLICT(zone_id) DO UPDATE SET until=excluded.until
                        """)) {
                    statement.setString(1, zoneId);
                    statement.setLong(2, cooldownUntil);
                    statement.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException exception) {
                conn.rollback();
                throw exception;
            } finally {
                conn.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> addDutyZoneTime(String zoneId, UUID uuid, long seconds) {
        if (seconds <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return run("add duty zone time", conn -> {
            try (PreparedStatement statement = conn.prepareStatement("""
                    INSERT INTO duty_zone_time(zone_id,uuid,seconds) VALUES(?,?,?)
                    ON CONFLICT(zone_id,uuid) DO UPDATE SET seconds=seconds+excluded.seconds
                    """)) {
                statement.setString(1, zoneId);
                statement.setString(2, uuid.toString());
                statement.setLong(3, seconds);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Long> dutyZoneSeconds(String zoneId, UUID uuid) {
        return run("duty zone seconds", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "SELECT seconds FROM duty_zone_time WHERE zone_id=? AND uuid=?")) {
                statement.setString(1, zoneId);
                statement.setString(2, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : 0L;
                }
            }
        });
    }

    public CompletableFuture<Void> recordPrison(UUID uuid, long until, String reason, String officer) {
        return run("record prison", conn -> {
            try (PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO prison_records(uuid,until,reason,officer,created_at) VALUES(?,?,?,?,?)")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, until);
                statement.setString(3, reason == null ? "" : reason);
                statement.setString(4, officer == null ? "" : officer);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not close the SQLite connection", exception);
            }
        }
    }

    private <T> CompletableFuture<T> run(String action, SqlWork<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.apply(connection);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Database operation failed: " + action, exception);
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private void bindProfile(PreparedStatement statement, PlayerProfile profile) throws SQLException {
        statement.setString(1, profile.uuid().toString());
        statement.setString(2, profile.name());
        statement.setLong(3, profile.firstSeen());
        statement.setLong(4, profile.lastSeen());
        statement.setLong(5, profile.policeSince());
        statement.setInt(6, profile.kills());
        statement.setInt(7, profile.arrests());
        statement.setLong(8, profile.dutySeconds());
        statement.setDouble(9, profile.policeEarnings());
        statement.setInt(10, profile.criminalFlag() ? 1 : 0);
        statement.setLong(11, profile.wantedUntil());
        statement.setLong(12, profile.criminalSince());
        statement.setInt(13, profile.robberies());
        statement.setDouble(14, profile.balance());
        statement.setLong(15, profile.prisonUntil());
        statement.setString(16, profile.prisonReason());
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws Exception;
    }

    private PlayerProfile readProfile(ResultSet result) throws SQLException {
        PlayerProfile profile = new PlayerProfile(
                UUID.fromString(result.getString("uuid")),
                result.getString("name"));
        profile.firstSeen(result.getLong("first_seen"));
        profile.lastSeen(result.getLong("last_seen"));
        profile.policeSince(result.getLong("police_since"));
        profile.kills(result.getInt("kills"));
        profile.arrests(result.getInt("arrests"));
        profile.dutySeconds(result.getLong("duty_seconds"));
        profile.policeEarnings(result.getDouble("police_earnings"));
        profile.criminalFlag(result.getInt("criminal") == 1);
        profile.wantedUntil(result.getLong("wanted_until"));
        profile.criminalSince(result.getLong("criminal_since"));
        profile.robberies(result.getInt("robberies"));
        profile.balance(result.getDouble("balance"));
        profile.prisonUntil(result.getLong("prison_until"));
        profile.prisonReason(result.getString("prison_reason"));
        return profile;
    }
}
