package dev.cityroleplay.tools;

import dev.cityroleplay.police.ArrestManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class ArrestBatonBehavior implements ToolBehavior {

    private final ArrestManager arrests;

    public ArrestBatonBehavior(ArrestManager arrests) {
        this.arrests = arrests;
    }

    @Override
    public String id() {
        return "arrest_baton";
    }

    @Override
    public void onAttack(ToolContext context, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player officer = context.player();
        if (officer.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        arrests.handleBatonHit(officer, target, event);
    }

    @Override
    public void onInteractEntity(ToolContext context, PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        event.setCancelled(true);
        if (arrests.session(target.getUniqueId()).isPresent()) {
            arrests.toggleEscort(context.player(), target);
        }
    }
}
