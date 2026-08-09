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
 * standard {@link HorizontalDirectionalBlock#FACING} property. Collision/
 * selection shape matches the Blockbench model (a body recessed 3px off the
 * north face, plus a small latch protrusion) instead of a full cube, rotated
 * to follow {@code FACING} - see the matching {@code y} rotations in
 * {@code blockstates/loadout_locker.json}.
 */
public class LoadoutLockerBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Shapes.box(0, 0, 3.0 / 16, 1, 1, 1),
            Shapes.box(12.0 / 16, 4.0 / 16, 2.0 / 16, 14.0 / 16, 12.0 / 16, 3.0 / 16));
    private static final VoxelShape SHAPE_EAST = rotateClockwise(SHAPE_NORTH);
    private static final VoxelShape SHAPE_SOUTH = rotateClockwise(SHAPE_EAST);
    private static final VoxelShape SHAPE_WEST = rotateClockwise(SHAPE_SOUTH);

    /** One 90-degree clockwise step (viewed from above, matching the "y" rotation in the blockstate JSON): (x,z) -> (1-z, x). */
    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] result = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                result[0] = Shapes.or(result[0], Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
        return result[0];
    }

    public LoadoutLockerBlock(BlockBehaviour.Properties properties) {
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
        return switch (state.getValue(FACING)) {
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }
}
