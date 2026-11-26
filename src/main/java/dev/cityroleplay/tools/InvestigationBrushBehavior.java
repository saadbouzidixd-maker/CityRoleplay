package dev.cityroleplay.tools;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.criminal.CriminalService;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.integration.LuckPermsHook;
import dev.cityroleplay.profile.PlayerProfile;
import dev.cityroleplay.profile.ProfileService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class InvestigationBrushBehavior implements ToolBehavior {

    private final Messages messages;
    private final EconomyManager economy;
    private final LuckPermsHook luckPerms;
    private final CriminalService criminals;
    private final ProfileService profiles;

    public InvestigationBrushBehavior(Messages messages, EconomyManager economy,
                                      LuckPermsHook luckPerms, CriminalService criminals,
                                      ProfileService profiles) {
        this.messages = messages;
        this.economy = economy;
        this.luckPerms = luckPerms;
        this.criminals = criminals;
        this.profiles = profiles;
    }

    @Override
    public String id() {
        return "investigation_brush";
    }

    @Override
    public void onInteractEntity(ToolContext context, PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            messages.send(context.player(), "brush.no-target");
            return;
        }
        event.setCancelled(true);
        PlayerProfile profile = profiles.getOrCreate(target);
        messages.sendNoPrefix(context.player(), "brush.header");
        messages.sendNoPrefix(context.player(), "brush.name", "player", target.getName());
        messages.sendNoPrefix(context.player(), "brush.group",
                "group", luckPerms.available() ? luckPerms.primaryGroup(target) : "default");
        messages.sendNoPrefix(context.player(), "brush.balance",
                "balance", economy.format(economy.balance(target)));
        if (criminals.isCriminal(target)) {
            messages.sendNoPrefix(context.player(), "brush.criminal-yes");
            if (profile.isWanted(System.currentTimeMillis())) {
                messages.sendNoPrefix(context.player(), "brush.criminal-until",
                        "date", criminals.wantedLabel(profile));
            }
        } else {
            messages.sendNoPrefix(context.player(), "brush.criminal-no");
        }
    }
}
