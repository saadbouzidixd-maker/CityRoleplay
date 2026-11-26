package dev.cityroleplay.tools;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.police.ArrestManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ArrestRopeBehavior implements ToolBehavior {

    private final Messages messages;
    private final ArrestManager arrests;
    private final Map<String, ArrestRopeAdapter> adapters = new HashMap<>();

    public ArrestRopeBehavior(Messages messages, ArrestManager arrests) {
        this.messages = messages;
        this.arrests = arrests;
    }

    public void registerAdapter(ArrestRopeAdapter adapter) {
        adapters.put(adapter.id().toLowerCase(Locale.ROOT), adapter);
    }

    @Override
    public String id() {
        return "arrest_rope";
    }

    @Override
    public void onInteractEntity(ToolContext context, PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        event.setCancelled(true);
        String adapterId = context.tool().configString("adapter", "native").toLowerCase(Locale.ROOT);
        ArrestRopeAdapter adapter = adapters.get(adapterId);
        if (adapter == null) {
            messages.send(context.player(), "arrest.rope-adapter-missing", "adapter", adapterId);
            return;
        }
        if (!adapter.escort(context.player(), target)) {
            messages.send(context.player(), "arrest.not-arrested");
        }
    }
}
