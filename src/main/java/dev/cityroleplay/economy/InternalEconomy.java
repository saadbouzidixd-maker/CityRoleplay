package dev.cityroleplay.economy;

import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.util.Formats;
import org.bukkit.OfflinePlayer;

public final class InternalEconomy implements EconomyService {

    private final ProfileService profiles;
    private final ConfigManager config;

    public InternalEconomy(ProfileService profiles, ConfigManager config) {
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String name() {
        return "Internal";
    }

    @Override
    public double balance(OfflinePlayer player) {
        return profile(player).balance();
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return profile(player).balance() + 0.000001 >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        PlayerProfile profile = profile(player);
        if (amount <= 0 || profile.balance() + 0.000001 < amount) {
            return false;
        }
        profile.balance(round(profile.balance() - amount));
        profiles.save(profile);
        return true;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerProfile profile = profile(player);
        profile.balance(round(profile.balance() + amount));
        profiles.save(profile);
        return true;
    }

    @Override
    public boolean set(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return false;
        }
        PlayerProfile profile = profile(player);
        profile.balance(round(amount));
        profiles.save(profile);
        return true;
    }

    @Override
    public String format(double amount) {
        return Formats.money(amount, precision(), config.get(ConfigManager.ECONOMY).getString("economy.currency-symbol", "$"));
    }

    private PlayerProfile profile(OfflinePlayer player) {
        return profiles.getOrCreate(player);
    }

    private double round(double amount) {
        return Formats.round(amount, precision());
    }

    private int precision() {
        return Math.max(0, config.get(ConfigManager.ECONOMY).getInt("economy.precision", 2));
    }
}
