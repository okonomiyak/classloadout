package uk.iwaservice.classloadout.compat.superbwarfare;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import net.minecraft.server.level.ServerPlayer;

/**
 * Only classloaded by {@link uk.iwaservice.classloadout.compat.SuperbWarfareCompat}
 * when SuperbWarfare is present. SuperbWarfare tracks ammo as five
 * per-player pools (handgun/rifle/shotgun/sniper/heavy) rather than per-gun
 * NBT, so there is no need to detect which weapon the player is holding -
 * topping up all five pools resupplies whatever they're carrying.
 */
public final class SwAmmoResupplier {

    public static void resupply(ServerPlayer player, int amount) {
        for (Ammo type : Ammo.values()) {
            type.add(player, amount);
        }
    }

    private SwAmmoResupplier() {}
}
