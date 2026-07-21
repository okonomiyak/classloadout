package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.classloadout.Config;

import javax.annotation.Nullable;
import java.util.UUID;

/** Shared placement/ticking/breakage rules for the health and ammo pack blocks. */
public abstract class AbstractResupplyPackBlock extends BaseEntityBlock {

    protected AbstractResupplyPackBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** When friendlyOnlyDestroy is on, only the placing player can make any mining progress (like bedrock for everyone else). */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (Config.FRIENDLY_ONLY_DESTROY.get() && level.getBlockEntity(pos) instanceof AbstractResupplyPackBlockEntity pack) {
            UUID owner = pack.getOwner();
            if (owner != null && !owner.equals(player.getUUID())) {
                return 0.0F;
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof AbstractResupplyPackBlockEntity pack) {
                pack.tick((ServerLevel) lvl);
            }
        };
    }
}
