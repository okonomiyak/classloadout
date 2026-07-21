package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.classloadout.Config;
import uk.iwaservice.classloadout.ModRegistry;

public class HealthPackBlockEntity extends AbstractResupplyPackBlockEntity {

    public HealthPackBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.HEALTH_PACK_BE.get(), pos, state);
    }

    @Override
    protected void applyEffect(ServerPlayer player) {
        if (player.getHealth() < player.getMaxHealth()) {
            int amount = Config.RESUPPLY_HEALTH_PER_TICK.get();
            player.heal(amount);
        }
    }
}
