package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.classloadout.Config;
import uk.iwaservice.classloadout.ModRegistry;
import uk.iwaservice.classloadout.compat.SuperbWarfareCompat;
import uk.iwaservice.classloadout.compat.TaczCompat;

public class AmmoPackBlockEntity extends AbstractResupplyPackBlockEntity {

    public AmmoPackBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.AMMO_PACK_BE.get(), pos, state);
    }

    @Override
    protected void applyEffect(ServerPlayer player) {
        int amount = Config.RESUPPLY_AMMO_PER_TICK.get();
        TaczCompat.resupply(player, amount);
        SuperbWarfareCompat.resupply(player, amount);
    }
}
