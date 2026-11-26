package dev.cityroleplay.tools;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.util.ItemUtil;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class ToolListener implements Listener {

    private final ToolService tools;
    private final ToolRegistry registry;
    private final Messages messages;

    public ToolListener(ToolService tools, ToolRegistry registry, Messages messages) {
        this.tools = tools;
        this.registry = registry;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Optional<ToolService.ValidatedTool> validated = tools.validate(event.getPlayer(),
                event.getItemDrop().getItemStack());
        if (validated.isPresent() && validated.get().definition().soulbound()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "police.tool-soulbound");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        use(event.getPlayer(), event.getItem(), (context, behavior) ->
                behavior.onInteractBlock(context, event));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        use(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand(),
                (context, behavior) -> behavior.onInteractEntity(context, event));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        use(player, player.getInventory().getItemInMainHand(),
                (context, behavior) -> behavior.onAttack(context, event));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        use(player, event.getBow(), (context, behavior) -> behavior.onShoot(context, event));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getShooter() instanceof Player player)) {
            return;
        }
        ItemStack item = arrow.getItem();
        Optional<String> toolId = ItemUtil.arrowOwnerTool(item);
        if (toolId.isEmpty()) {
            return;
        }
        Optional<ToolDefinition> definition = registry.tool(toolId.get());
        if (definition.isEmpty()) {
            return;
        }
        registry.behavior(definition.get().behavior()).ifPresent(behavior -> {
            if (tools.canUse(player, definition.get())) {
                behavior.onProjectileHit(new ToolContext(player, item, definition.get(), null), event);
            }
        });
    }

    private void use(Player player, ItemStack item, Dispatch dispatch) {
        Optional<ToolService.ValidatedTool> validated = tools.validate(player, item);
        if (validated.isEmpty()) {
            return;
        }
        ToolDefinition definition = validated.get().definition();
        if (!tools.canUse(player, definition)) {
            return;
        }
        registry.behavior(definition.behavior()).ifPresent(behavior ->
                dispatch.run(new ToolContext(player, item, definition, validated.get().reference()), behavior));
    }

    private interface Dispatch {
        void run(ToolContext context, ToolBehavior behavior);
    }
}
