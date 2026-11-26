package dev.cityroleplay.tools;

import dev.cityroleplay.config.Messages;
import dev.cityroleplay.police.PoliceService;
import dev.cityroleplay.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class CrossbowBehavior implements ToolBehavior {

    private final Messages messages;
    private final PoliceService police;

    public CrossbowBehavior(Messages messages, PoliceService police) {
        this.messages = messages;
        this.police = police;
    }

    @Override
    public String id() {
        return "crossbow";
    }

    @Override
    public void onShoot(ToolContext context, EntityShootBowEvent event) {
        Player player = context.player();
        if (context.tool().configBoolean("police-only", true) && !police.isPolice(player)) {
            event.setCancelled(true);
            messages.send(player, "crossbow.not-police");
            return;
        }
        Material ammo = Material.matchMaterial(context.tool().configString("ammo-material", "ARROW"));
        if (ammo == null) {
            event.setCancelled(true);
            messages.send(player, "crossbow.bad-ammo");
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(ammo), 1)
                && !player.getInventory().contains(ammo)) {
            event.setCancelled(true);
            messages.send(player, "crossbow.no-ammo");
            return;
        }
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            ItemStack marked = new ItemStack(Material.ARROW);
            ItemUtil.markArrow(marked, context.tool().id(), player.getUniqueId());
            arrow.setItem(marked);
        }
    }

    @Override
    public void onProjectileHit(ToolContext context, ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player target)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        if (police.isPolice(target)) {
            return;
        }
        int ticks = Math.max(0, context.tool().configInt("slow-ticks", 60));
        int amplifier = Math.max(0, context.tool().configInt("slow-amplifier", 1));
        if (ticks > 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier, false, false, false));
            messages.send(shooter, "crossbow.hit", "player", target.getName());
        }
    }
}
