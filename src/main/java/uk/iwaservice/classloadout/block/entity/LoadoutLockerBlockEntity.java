package uk.iwaservice.classloadout.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.classloadout.ModRegistry;

public class LoadoutLockerBlockEntity extends BlockEntity {
    public LoadoutLockerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistry.LOADOUT_LOCKER_BLOCK_ENTITY.get(), pos, blockState);
    }
}
