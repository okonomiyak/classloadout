package uk.iwaservice.classloadout.resupply;

import net.minecraft.server.level.ServerPlayer;
import uk.iwaservice.classloadout.compat.SuperbWarfareCompat;
import uk.iwaservice.classloadout.compat.TaczCompat;

/**
 * The actual heal/ammo effects a resupply source (placed pack or landed
 * thrown pack) applies to a player in range - shared so both delivery
 * mechanisms stay in sync rather than duplicating the compat calls.
 */
final class ResupplyEffects {

    static void heal(ServerPlayer player, int amount) {
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(amount);
        }
    }

    static void resupplyAmmo(ServerPlayer player, int amount) {
        TaczCompat.resupply(player, amount);
        SuperbWarfareCompat.resupply(player, amount);
    }

    private ResupplyEffects() {}
}
