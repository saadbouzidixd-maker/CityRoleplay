package dev.cityroleplay.selection;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.util.ItemUtil;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class SelectionListener implements Listener {

    private final SelectionManager selections;
    private final Messages messages;

    public SelectionListener(SelectionManager selections, Messages messages) {
        this.selections = selections;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!ItemUtil.isSelectionWand(event.getItem())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selections.setFirst(event.getPlayer(), block.getLocation());
            messages.send(event.getPlayer(), "selection.pos1",
                    "location", block.getX() + ", " + block.getY() + ", " + block.getZ());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selections.setSecond(event.getPlayer(), block.getLocation());
            messages.send(event.getPlayer(), "selection.pos2",
                    "location", block.getX() + ", " + block.getY() + ", " + block.getZ());
        }
    }
}
