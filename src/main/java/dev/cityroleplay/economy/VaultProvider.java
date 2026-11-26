package dev.cityroleplay.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Loaded reflectively only after Vault is confirmed present, avoiding class
 * verification failures on servers without Vault.
 */
public final class VaultProvider {

    private VaultProvider() {
    }

    public static EconomyService find() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            return null;
        }
        VaultEconomy economy = new VaultEconomy(registration.getProvider());
        return economy.available() ? economy : null;
    }
}
