package uk.iwaservice.classloadout.resupply;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import uk.iwaservice.classloadout.Config;

/**
 * Single-use self-heal item: right-click to instantly restore
 * {@code bandageHealAmount} health to yourself, then the item is consumed
 * (unless the player is in creative mode). Only usable while missing health,
 * so it can't be wasted on a full-health player by accident - unlike the
 * health pack gadgets, this only ever affects the user, never nearby allies.
 */
public class BandageItem extends Item {

    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() >= player.getMaxHealth()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            player.heal(Config.BANDAGE_HEAL_AMOUNT.get());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
