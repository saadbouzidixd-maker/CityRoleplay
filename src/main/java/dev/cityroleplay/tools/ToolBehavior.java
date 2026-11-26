package dev.cityroleplay.tools;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * A real tool behavior. The behavior id is referenced by tools.yml; plugins can
 * register additional implementations through ToolRegistry#registerBehavior.
 */
public interface ToolBehavior {

    String id();

    default void onInteractBlock(ToolContext context, PlayerInteractEvent event) {
    }

    default void onInteractEntity(ToolContext context, PlayerInteractEntityEvent event) {
    }

    default void onAttack(ToolContext context, EntityDamageByEntityEvent event) {
    }

    default void onShoot(ToolContext context, EntityShootBowEvent event) {
    }

    default void onProjectileHit(ToolContext context, ProjectileHitEvent event) {
    }
}
