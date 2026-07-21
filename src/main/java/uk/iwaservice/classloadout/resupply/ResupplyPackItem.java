package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.classloadout.Config;

import javax.annotation.Nullable;

/**
 * Shared {@link BlockItem} for both resupply pack blocks. Placement is
 * ordinary block-item placement; the only additions are the
 * {@code maxActivePacksPerPlayer} pre-check and stamping the placer's UUID
 * onto the freshly placed block entity as its owner.
 */
public class ResupplyPackItem extends BlockItem {

    public ResupplyPackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player
                && ResupplyPackRegistry.countActive(player.getUUID()) >= Config.MAX_ACTIVE_PACKS_PER_PLAYER.get()) {
            player.sendSystemMessage(Component.translatable("classloadout.msg.pack_limit_reached"));
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!level.isClientSide && player != null
                && level.getBlockEntity(pos) instanceof AbstractResupplyPackBlockEntity pack) {
            pack.setOwner(player.getUUID());
        }
        return result;
    }
}
