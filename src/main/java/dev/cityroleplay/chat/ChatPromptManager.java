package dev.cityroleplay.chat;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.util.Scheduler;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatPromptManager implements Listener {

    private final Messages messages;
    private final Scheduler scheduler;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPromptManager(Messages messages, Scheduler scheduler) {
        this.messages = messages;
        this.scheduler = scheduler;
    }

    public void ask(Player player, String questionKey, Consumer<String> handler) {
        prompts.put(player.getUniqueId(), new Prompt(handler));
        messages.sendNoPrefix(player, questionKey);
    }

    public void askRaw(Player player, String question, Consumer<String> handler) {
        prompts.put(player.getUniqueId(), new Prompt(handler));
        player.sendMessage(dev.cityroleplay.util.ItemUtil.text(question));
    }

    public boolean isPrompting(Player player) {
        return prompts.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        prompts.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        scheduler.sync(() -> {
            if (input.equalsIgnoreCase("cancel")) {
                messages.send(event.getPlayer(), "admin.cancelled");
                return;
            }
            try {
                prompt.handler().accept(input);
            } catch (RuntimeException exception) {
                messages.send(event.getPlayer(), "invalid-number", "value", input);
            }
        });
    }

    private record Prompt(Consumer<String> handler) {
    }
}
