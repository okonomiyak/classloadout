package uk.iwaservice.classloadout.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Only classloaded by {@link uk.iwaservice.classloadout.compat.TaczCompat}
 * when TACZ is present. Tops up ammo for any TACZ gun held in either hand.
 *
 * <p>{@link IGun#useDummyAmmo} is only true for guns that carry a
 * {@code DummyAmmo} NBT tag (an internal reserve some custom/addon guns use
 * instead of a real ammo item) - those get that reserve topped up directly.
 * Every other gun, including TACZ's default "magazine"-fed stock guns (AK47,
 * M4, ...) which are neither dummy-ammo nor {@code useInventoryAmmo()}, is
 * reloaded from real ammo items taken out of the player's inventory - so
 * those instead get real ammo, resolved via the gun's own {@code ammo} id
 * ({@link com.tacz.guns.resource.pojo.data.gun.GunData#getAmmoId()}, looked
 * up through {@link TimelessAPI#getCommonGunIndex}), with a matching TACZ
 * ammo box ({@link IAmmoBox}, a single-item "container" for one ammo type)
 * already in the player's inventory preferred over adding loose ammo items:
 * a matching box gets topped up (up to its own capacity - never overflowing
 * into loose ammo even once the box is full) and only when no matching box
 * exists at all do loose ammo items get added instead.
 */
public final class TaczAmmoResupplier {

    public static void resupply(ServerPlayer player, int amount) {
        for (InteractionHand hand : InteractionHand.values()) {
            resupplyHand(player, player.getItemInHand(hand), amount);
        }
    }

    private static void resupplyHand(ServerPlayer player, ItemStack stack, int amount) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return;
        }
        if (gun.useDummyAmmo(stack)) {
            int current = gun.getDummyAmmoAmount(stack);
            int max = gun.hasMaxDummyAmmo(stack) ? gun.getMaxDummyAmmoAmount(stack) : Integer.MAX_VALUE;
            if (current < max) {
                gun.addDummyAmmoAmount(stack, Math.min(amount, max - current));
            }
            return;
        }
        ResourceLocation ammoId = TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .map(index -> index.getGunData().getAmmoId())
                .orElse(null);
        if (ammoId != null) {
            resupplyAmmoPreferBox(player, ammoId, amount);
        }
    }

    /**
     * Tops up the first matching ammo box found anywhere in the player's inventory, if any -
     * whether that leaves it topped up, already full, or (for a creative box) untouched, a
     * matching box being present at all means loose ammo items are never added on top. Only
     * without any matching box does this fall back to giving loose ammo items.
     */
    private static void resupplyAmmoPreferBox(ServerPlayer player, ResourceLocation ammoId, int amount) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof IAmmoBox box) || !ammoId.equals(box.getAmmoId(stack))) {
                continue;
            }
            if (!box.isCreative(stack) && !box.isAllTypeCreative(stack)) {
                int current = box.getAmmoCount(stack);
                int max = maxAmmoBoxCapacity(ammoId, box.getAmmoLevel(stack));
                if (current < max) {
                    box.setAmmoCount(stack, Math.min(max, current + amount));
                }
            }
            return;
        }
        giveLooseAmmo(player, ammoId, amount);
    }

    private static void giveLooseAmmo(ServerPlayer player, ResourceLocation ammoId, int amount) {
        ItemStack template = TaczAmmoResolver.buildStack(ammoId);
        if (template.isEmpty()) {
            return;
        }
        int maxStack = template.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            ItemStack give = template.copyWithCount(Math.min(maxStack, remaining));
            remaining -= give.getCount();
            if (!player.getInventory().add(give)) {
                player.drop(give, false);
            }
        }
    }

    private static int maxAmmoBoxCapacity(ResourceLocation ammoId, int level) {
        return TimelessAPI.getCommonAmmoIndex(ammoId)
                .map(index -> index.getStackSize() * SyncConfig.AMMO_BOX_STACK_SIZE.get() * (level + 1))
                .orElse(0);
    }

    private TaczAmmoResupplier() {}
}
