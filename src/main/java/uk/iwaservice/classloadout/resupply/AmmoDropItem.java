package uk.iwaservice.classloadout.resupply;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import uk.iwaservice.classloadout.Config;

/** Dropped when a player kills another player: consumed the moment it lands in a player's inventory, resupplying the ammo of the guns they hold. */
public class AmmoDropItem extends Item {

    public AmmoDropItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            ResupplyEffects.resupplyAmmo(player, Config.KILL_AMMO_DROP_AMOUNT.get() * stack.getCount());
            stack.setCount(0);
        }
    }
}
