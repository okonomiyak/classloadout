package uk.iwaservice.classloadout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import uk.iwaservice.classloadout.loadout.LoadoutManager;
import uk.iwaservice.classloadout.network.NetworkHandler;

/**
 * Right-clicking (OP only) opens {@code GuardSpawnerEditorScreen} with this
 * block's current config, sent fresh from the server every time (config
 * isn't broadcast globally like the whitelist/spawn kit - only the editing
 * OP ever needs one block's data). While configured, the block watches for
 * its tagged entity going missing and respawns it on top of itself after
 * the configured delay - see {@code ServerEvents#tickGuardSpawners}.
 */
public class GuardSpawnerBlock extends Block {

    public GuardSpawnerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                  BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("classloadout.msg.guardspawner_op_only"), true);
            return InteractionResult.CONSUME;
        }
        GlobalPos gpos = GlobalPos.of(level.dimension(), pos);
        LoadoutManager manager = LoadoutManager.get(serverPlayer.server);
        NetworkHandler.sendOpenGuardSpawnerEditor(serverPlayer, pos, manager.getGuardSpawnerEntity(gpos),
                manager.getGuardSpawnerDelaySeconds(gpos), manager.getGuardSpawnerItems(gpos));
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            LoadoutManager.get(((net.minecraft.server.level.ServerLevel) level).getServer())
                    .removeGuardSpawner(GlobalPos.of(level.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
