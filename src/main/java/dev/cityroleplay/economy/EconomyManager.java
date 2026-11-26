package dev.cityroleplay.economy;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.util.Formats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class EconomyManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ProfileService profiles;
    private final Database database;

    private EconomyService service;

    public EconomyManager(JavaPlugin plugin, ConfigManager config, ProfileService profiles, Database database) {
        this.plugin = plugin;
        this.config = config;
        this.profiles = profiles;
        this.database = database;
    }

    public void setup() {
        String provider = config.get(ConfigManager.ECONOMY).getString("economy.provider", "auto").toLowerCase();
        EconomyService vault = findVault();

        if ((provider.equals("vault") || provider.equals("auto")) && vault != null) {
            service = vault;
            plugin.getLogger().info("Economy provider: " + vault.name());
            return;
        }
        if (provider.equals("vault") && vault == null) {
            plugin.getLogger().warning("Vault economy was requested but is unavailable. Using the internal economy.");
        }
        service = new InternalEconomy(profiles, config);
        plugin.getLogger().info("Economy provider: Internal");
    }

    private EconomyService findVault() {
        if (!config.get(ConfigManager.INTEGRATIONS).getBoolean("vault.enabled", true)) {
            return null;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        try {
            Class<?> provider = Class.forName("dev.cityroleplay.economy.VaultProvider");
            Object result = provider.getMethod("find").invoke(null);
            return result instanceof EconomyService service ? service : null;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Vault economy lookup failed: " + throwable.getMessage());
            return null;
        }
    }

    public EconomyService service() {
        return service;
    }

    public boolean vaultActive() {
        return service instanceof VaultEconomy;
    }

    public int precision() {
        return Math.max(0, config.get(ConfigManager.ECONOMY).getInt("economy.precision", 2));
    }

    public double maxTransaction() {
        return config.get(ConfigManager.ECONOMY).getDouble("economy.max-transaction", 100_000_000.0);
    }

    public String format(double amount) {
        return service.format(amount);
    }

    public double balance(OfflinePlayer player) {
        return service.balance(player);
    }

    /**
     * Pays a player, logs a positive transaction, and returns false without
     * changing anything when the amount is invalid or the provider rejects it.
     */
    public boolean pay(OfflinePlayer player, double amount, String type, String detail) {
        double rounded = valid(amount);
        if (rounded <= 0 || player == null) {
            return false;
        }
        if (!service.deposit(player, rounded)) {
            return false;
        }
        log(player, type, rounded, detail);
        return true;
    }

    public boolean charge(OfflinePlayer player, double amount, String type, String detail) {
        double rounded = valid(amount);
        if (rounded <= 0 || player == null) {
            return false;
        }
        if (!service.has(player, rounded)) {
            return false;
        }
        if (!service.withdraw(player, rounded)) {
            return false;
        }
        log(player, type, rounded, detail);
        return true;
    }

    public boolean setBalance(OfflinePlayer player, double amount) {
        double rounded = valid(amount);
        if (rounded < 0 || player == null) {
            return false;
        }
        if (!service.set(player, rounded)) {
            return false;
        }
        log(player, "SET_BALANCE", rounded, "Administrative balance set");
        return true;
    }

    private void log(OfflinePlayer player, String type, double amount, String detail) {
        if (!config.get(ConfigManager.ECONOMY).getBoolean("economy.log-transactions", true)) {
            return;
        }
        database.insertTransaction(player.getUniqueId(), type, amount, detail)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Could not log transaction: " + throwable.getMessage());
                    return null;
                });
    }

    private double valid(double amount) {
        if (!Formats.isFinitePositive(amount)) {
            return 0;
        }
        if (amount > maxTransaction()) {
            return 0;
        }
        return Formats.round(amount, precision());
    }

    public Optional<EconomyService> provider() {
        return Optional.ofNullable(service);
    }
}
