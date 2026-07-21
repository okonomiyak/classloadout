package uk.iwaservice.classloadout.resupply;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import uk.iwaservice.classloadout.Config;

import java.util.function.BiFunction;

/**
 * Shared {@link Item} for both thrown resupply packs. Throwing is gated only
 * by {@code throwCooldownSeconds} (vanilla's {@link net.minecraft.world.item.ItemCooldowns},
 * the same mechanism ender pearls use) - the item itself is never consumed,
 * so it stays usable turn after turn once the cooldown clears. The
 * {@code maxActivePacksPerPlayer} check is shared with the placed packs via
 * {@link ResupplyPackRegistry}.
 */
public class ThrowableResupplyItem extends Item {

    private final BiFunction<Level, Player, AbstractThrownResupplyEntity> factory;

    public ThrowableResupplyItem(Properties properties, BiFunction<Level, Player, AbstractThrownResupplyEntity> factory) {
        super(properties);
        this.factory = factory;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        if (ResupplyPackRegistry.countActive(serverPlayer.getUUID()) >= Config.MAX_ACTIVE_PACKS_PER_PLAYER.get()) {
            serverPlayer.sendSystemMessage(Component.translatable("classloadout.msg.pack_limit_reached"));
            return InteractionResultHolder.fail(stack);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            AbstractThrownResupplyEntity projectile = factory.apply(level, player);
            projectile.setItem(stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(projectile);
        }

        player.getCooldowns().addCooldown(this, Config.THROW_COOLDOWN_SECONDS.get() * 20);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
