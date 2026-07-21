package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class AmmoPackBlock extends AbstractResupplyPackBlock {

    public AmmoPackBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoPackBlockEntity(pos, state);
    }
}
