package dev.cityroleplay.tools;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.police.GraveManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GraveShovelBehavior implements ToolBehavior {

    private final JavaPlugin plugin;
    private final GraveManager graves;
    private final Messages messages;

    public GraveShovelBehavior(JavaPlugin plugin, GraveManager graves, Messages messages) {
        this.plugin = plugin;
        this.graves = graves;
        this.messages = messages;
    }

    @Override
    public String id() {
        return "grave_shovel";
    }

    @Override
    public void onInteractBlock(ToolContext context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        Material hideMaterial = Material.matchMaterial(context.tool().configString("hide-material", "AIR"));
        if (hideMaterial == null) {
            messages.send(context.player(), "grave.material-invalid");
            return;
        }
        graves.hide(block, context.player(), hideMaterial, context.tool().configDouble("reward", 100.0));
    }
}
