package uk.iwaservice.classloadout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Places facing the player (front toward them, like a furnace) via the
 * standard {@link HorizontalDirectionalBlock#FACING} property - see the
 * matching {@code y} rotations in {@code blockstates/loadout_station.json}.
 *
 * <p>Collision/selection shape matches the Blockbench model (a desk:
 * tabletop, four corner brackets, four legs) instead of a full cube. It's
 * the same regardless of {@code FACING} - unlike the loadout locker, this
 * shape (with the asymmetric back rim excluded, see below) happens to be
 * 4-fold rotationally symmetric, so there's nothing to rotate.
 *
 * <p>The model's back rim (y=19-20) and its two side supports (y=16-19)
 * intentionally poke above the block's own height range as a purely visual
 * overhang, so they're left out of the collision shape entirely - a solid
 * hitbox extending into the block above would be surprising to stand near.
 */
public class LoadoutStationBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            px(0, 14, 0, 16, 16, 16),
            px(1, 13, 11, 5, 14, 15),
            px(11, 13, 11, 15, 14, 15),
            px(1, 13, 1, 5, 14, 5),
            px(11, 13, 1, 15, 14, 5),
            px(12, 0, 2, 14, 13, 4),
            px(2, 0, 2, 4, 13, 4),
            px(12, 0, 12, 14, 13, 14),
            px(2, 0, 12, 4, 13, 14));

    private static VoxelShape px(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

    public LoadoutStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
