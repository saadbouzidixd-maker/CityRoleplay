package dev.cityroleplay.config;

import dev.cityroleplay.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Messages {

    private final ConfigManager config;

    public Messages(ConfigManager config) {
        this.config = config;
    }

    public String raw(String key) {
        String value = config.get(ConfigManager.MESSAGES).getString(key);
        return value == null ? "&cMissing message: " + key : value;
    }

    public String format(String key, Object... placeholders) {
        String value = raw(key);
        if (placeholders != null) {
            for (int index = 0; index + 1 < placeholders.length; index += 2) {
                value = value.replace("%" + placeholders[index] + "%", String.valueOf(placeholders[index + 1]));
            }
        }
        return value;
    }

    public Component component(String key, Object... placeholders) {
        return ItemUtil.text(format(key, placeholders));
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(ItemUtil.text(raw("prefix")).append(component(key, placeholders)));
    }

    public void sendNoPrefix(CommandSender sender, String key, Object... placeholders) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(component(key, placeholders));
    }

    public void send(Player player, Component component) {
        if (player != null) {
            player.sendMessage(component);
        }
    }
}
