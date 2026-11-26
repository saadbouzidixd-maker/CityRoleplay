package dev.cityroleplay.tools;

import dev.cityroleplay.police.ArrestManager;
import org.bukkit.entity.Player;

public final class NativeArrestRopeAdapter implements ArrestRopeAdapter {

    private final ArrestManager arrests;

    public NativeArrestRopeAdapter(ArrestManager arrests) {
        this.arrests = arrests;
    }

    @Override
    public String id() {
        return "native";
    }

    @Override
    public boolean escort(Player officer, Player target) {
        if (!arrests.session(target.getUniqueId()).isPresent()) {
            return false;
        }
        arrests.toggleEscort(officer, target);
        return true;
    }
}
