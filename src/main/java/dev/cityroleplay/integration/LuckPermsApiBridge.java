package dev.cityroleplay.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loaded reflectively by LuckPermsHook only when the server has LuckPerms, so a
 * server without it never verifies any net.luckperms.api classes.
 */
public final class LuckPermsApiBridge implements LuckPermsBridge {

    private final JavaPlugin plugin;
    private LuckPerms api;

    public LuckPermsApiBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setup() {
        api = LuckPermsProvider.get();
    }

    @Override
    public boolean available() {
        return api != null;
    }

    @Override
    public String primaryGroup(Player player) {
        if (api == null || player == null) {
            return "default";
        }
        User user = api.getUserManager().getUser(player.getUniqueId());
        return user == null ? "default" : user.getPrimaryGroup();
    }

    @Override
    public boolean inGroup(Player player, String group) {
        if (api == null || player == null || group == null || group.isBlank()) {
            return false;
        }
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return false;
        }
        if (user.getPrimaryGroup().equalsIgnoreCase(group)) {
            return true;
        }
        for (Group inherited : user.getInheritedGroups(user.getQueryOptions())) {
            if (inherited.getName().equalsIgnoreCase(group)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean groupExists(String group) {
        return api != null && api.getGroupManager().getGroup(group) != null;
    }

    @Override
    public CompletableFuture<Boolean> addToGroup(UUID uuid, String group) {
        return modifyGroup(uuid, group, true);
    }

    @Override
    public CompletableFuture<Boolean> removeFromGroup(UUID uuid, String group) {
        return modifyGroup(uuid, group, false);
    }

    private CompletableFuture<Boolean> modifyGroup(UUID uuid, String group, boolean add) {
        if (api == null) {
            return CompletableFuture.completedFuture(false);
        }
        Group target = api.getGroupManager().getGroup(group);
        if (target == null) {
            return CompletableFuture.completedFuture(false);
        }
        return api.getUserManager().loadUser(uuid).thenCompose(user -> {
            Node node = Node.builder("group." + target.getName().toLowerCase(Locale.ROOT)).build();
            if (add) {
                user.data().add(node);
            } else {
                user.data().remove(node);
            }
            return api.getUserManager().saveUser(user).thenApply(ignored -> true);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Could not modify LuckPerms group for " + uuid + ": " + throwable.getMessage());
            return false;
        });
    }
}
