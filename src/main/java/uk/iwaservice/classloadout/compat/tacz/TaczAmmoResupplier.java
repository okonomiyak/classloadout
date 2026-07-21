package uk.iwaservice.classloadout.compat.tacz;

import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Only classloaded by {@link uk.iwaservice.classloadout.compat.TaczCompat}
 * when TACZ is present. Tops up "dummy ammo" (the internal reserve counter
 * some TACZ guns use instead of consuming inventory ammo box items) for any
 * TACZ gun held in either hand. Guns that use real inventory ammo boxes
 * ({@code useInventoryAmmo() == true}) are intentionally left alone -
 * resolving which ammo box item matches a given gun id is out of scope here.
 */
public final class TaczAmmoResupplier {

    public static void resupply(ServerPlayer player, int amount) {
        for (InteractionHand hand : InteractionHand.values()) {
            resupplyHand(player, player.getItemInHand(hand), amount);
        }
    }

    private static void resupplyHand(ServerPlayer player, ItemStack stack, int amount) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null || !gun.useDummyAmmo(stack)) {
            return;
        }
        int current = gun.getDummyAmmoAmount(stack);
        int max = gun.hasMaxDummyAmmo(stack) ? gun.getMaxDummyAmmoAmount(stack) : Integer.MAX_VALUE;
        if (current < max) {
            gun.addDummyAmmoAmount(stack, Math.min(amount, max - current));
        }
    }

    private TaczAmmoResupplier() {}
}
