package dev.cityroleplay.tools;

import org.bukkit.entity.Player;

/**
 * Isolates arrest-rope movement from the tool item. The built-in adapter uses
 * the plugin's native escort state; server owners can register another adapter
 * if they run a compatible arrest plugin.
 */
public interface ArrestRopeAdapter {

    String id();

    boolean escort(Player officer, Player target);
}
