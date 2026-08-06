package uk.iwaservice.classloadout.cover;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import uk.iwaservice.classloadout.Config;
import uk.iwaservice.classloadout.ModRegistry;

/**
 * Right-click a block face to spawn a {@link CoverEntity} on top of it -
 * same pattern as {@link uk.iwaservice.classloadout.resupply.ResupplyPackPlacerItem},
 * just against the cover-specific limit/registry instead of the resupply
 * packs' shared one.
 */
public class CoverPlacerItem extends Item {

    public CoverPlacerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (CoverRegistry.countActive(player.getUUID()) >= Config.MAX_ACTIVE_COVERS_PER_PLAYER.get()) {
            player.sendSystemMessage(Component.translatable("classloadout.msg.cover_limit_reached"));
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        CoverEntity cover = ModRegistry.COVER.get().create(level);
        if (cover == null) {
            return InteractionResult.FAIL;
        }
        cover.setOwner(player.getUUID());
        cover.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        if (!level.addFreshEntity(cover)) {
            return InteractionResult.FAIL;
        }

        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
