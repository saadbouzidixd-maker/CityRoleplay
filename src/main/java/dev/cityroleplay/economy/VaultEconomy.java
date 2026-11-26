package dev.cityroleplay.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

public final class VaultEconomy implements EconomyService {

    private final Economy economy;

    public VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean available() {
        return economy != null && economy.isEnabled();
    }

    @Override
    public String name() {
        return economy == null ? "Vault" : "Vault (" + economy.getName() + ")";
    }

    @Override
    public double balance(OfflinePlayer player) {
        return economy == null ? 0 : economy.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    /**
     * Vault has no set operation. Apply the difference with a safe two-step
     * adjustment instead of silently pretending a set exists.
     */
    @Override
    public boolean set(OfflinePlayer player, double amount) {
        if (economy == null) {
            return false;
        }
        double current = economy.getBalance(player);
        double difference = amount - current;
        if (Math.abs(difference) < 0.000001) {
            return true;
        }
        return difference > 0 ? deposit(player, difference) : withdraw(player, -difference);
    }

    @Override
    public String format(double amount) {
        return economy == null ? String.valueOf(amount) : economy.format(amount);
    }
}
