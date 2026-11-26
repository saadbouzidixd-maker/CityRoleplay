package dev.cityroleplay.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyService {

    boolean available();

    String name();

    double balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    boolean set(OfflinePlayer player, double amount);

    String format(double amount);
}
