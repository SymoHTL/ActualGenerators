package dev.symo.actualgenerators.block;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import dev.symo.actualgenerators.block.entity.pipe.config.EMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeBlock;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemPipeBlock extends BaseEntityBlock implements IForgeBlock {

    //region shape
    public static final VoxelShape BASE = Block.box(7, 7, 7, 8.75, 8.75, 8.75);
    //region north
    public static final VoxelShape NORTH_PIPE = Block.box(7.125, 7.125, 0, 8.625, 8.625, 7.25);
    public static final VoxelShape NORTH_PLANE_1 = Block.box(6, 6, 0.5, 9.75, 9.75, 0.7);
    public static final VoxelShape NORTH_PLANE_2 = Block.box(4.25, 4.25, 0, 11.5, 11.5, 0.3);
    public static final VoxelShape NORTH_PLANE_3 = Block.box(5, 5, 0.3, 10.75, 10.75, 0.5);

    public static final VoxelShape NORTH = join(NORTH_PIPE, NORTH_PLANE_1, NORTH_PLANE_2, NORTH_PLANE_3);
    //endregion

    //region south
    public static final VoxelShape SOUTH_PIPE = Block.box(7.125, 7.125, 8.75, 8.625, 8.625, 16);
    public static final VoxelShape SOUTH_PLANE_1 = Block.box(6, 6, 15.3, 9.75, 9.75, 15.5);
    public static final VoxelShape SOUTH_PLANE_2 = Block.box(4.25, 4.25, 15.7, 11.5, 11.5, 16);
    public static final VoxelShape SOUTH_PLANE_3 = Block.box(5, 5, 15.5, 10.75, 10.75, 15.7);
    public static final VoxelShape SOUTH = join(SOUTH_PIPE, SOUTH_PLANE_1, SOUTH_PLANE_2, SOUTH_PLANE_3);
    //endregion

    //region east
    public static final VoxelShape EAST_PIPE = Block.box(8.75, 7.125, 7.125, 16, 8.625, 8.625);
    public static final VoxelShape EAST_PLANE_1 = Block.box(15.55, 6, 6, 15.75, 9.75, 9.75);
    public static final VoxelShape EAST_PLANE_2 = Block.box(15.70, 6, 6, 15.75, 9.75, 9.75); // TODO ERROR
    public static final VoxelShape EAST_PLANE_3 = Block.box(15.75, 5, 5, 15.95, 10.75, 10.75);
    public static final VoxelShape EAST = join(EAST_PIPE, EAST_PLANE_1, EAST_PLANE_2, EAST_PLANE_3);
    //endregion

    //region west
    public static final VoxelShape WEST_PIPE = Block.box(0, 7.125, 7.125, 7.25, 8.625, 8.625);
    public static final VoxelShape WEST_PLANE_1 = Block.box(0.5, 6, 6, 0.7, 9.75, 9.75);
    public static final VoxelShape WEST_PLANE_2 = Block.box(0, 4.25, 4.25, 0.3, 11.5, 11.5);
    public static final VoxelShape WEST_PLANE_3 = Block.box(0.3, 5, 5, 0.5, 10.75, 10.75);
    public static final VoxelShape WEST = join(WEST_PIPE, WEST_PLANE_1, WEST_PLANE_2, WEST_PLANE_3);
    //endregion

    //region top
    public static final VoxelShape TOP_PIPE = Block.box(7.125, 8.75, 7.125, 8.625, 16, 8.625);
    public static final VoxelShape TOP_PLANE_1 = Block.box(6, 15.3, 6, 9.75, 15.55, 9.75);
    public static final VoxelShape TOP_PLANE_2 = Block.box(4.25, 15.7, 4.25, 11.5, 16, 11.5);
    public static final VoxelShape TOP_PLANE_3 = Block.box(5, 15.5, 5, 10.75, 15.7, 10.75);
    public static final VoxelShape TOP = join(TOP_PIPE, TOP_PLANE_1, TOP_PLANE_2, TOP_PLANE_3);
    //endregion

    //region bottom
    public static final VoxelShape BOTTOM_PIPE = Block.box(7.125, 0, 7.125, 8.625, 7.25, 8.625);
    public static final VoxelShape BOTTOM_PLANE_1 = Block.box(6, 0.5, 6, 9.75, 0.7, 9.75);
    public static final VoxelShape BOTTOM_PLANE_2 = Block.box(4.25, 0, 4.25, 11.5, 0.3, 11.5);
    public static final VoxelShape BOTTOM_PLANE_3 = Block.box(5, 0.3, 5, 10.75, 0.5, 10.75);
    public static final VoxelShape BOTTOM = join(BOTTOM_PIPE, BOTTOM_PLANE_1, BOTTOM_PLANE_2, BOTTOM_PLANE_3);
    //endregion

    //endregion

    private static VoxelShape join(VoxelShape... shapes) {
        VoxelShape result = Shapes.empty();
        for (VoxelShape shape : shapes) {
            result = Shapes.join(result, shape, BooleanOp.OR);
        }
        return result;
    }


    public ItemPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return (level1, blockPos, blockState, t) -> {
            if (t instanceof ItemPipeBlockEntity itemPipe) {
                itemPipe.tick(level1, blockPos, itemPipe);
            }
        };
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.getBlock() != pNewState.getBlock()) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
                itemPipe.onRemove();
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, neighborPos, isMoving);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ItemPipeBlockEntity itemPipe)
            itemPipe.onNeighborChange(state, level, pos, block, neighborPos, isMoving);
    }

    //@Override
    //public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
    //    super.onNeighborChange(state, level, pos, neighbor);
    //    BlockEntity blockEntity = level.getBlockEntity(pos);
    //    if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
    //        itemPipe.onNeighborChange(level, pos, neighbor);
    //    }
    //    updateConnections((Level) level, pos);
    //}

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState otherState, boolean bool) {
        super.onPlace(state, level, pos, otherState, bool);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ItemPipeBlockEntity itemPipe)
            itemPipe.onPlace(state, level, pos, otherState, bool);

    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape shape = BASE;
        if (world.getBlockEntity(pos) instanceof ItemPipeBlockEntity itemPipe)
            return itemPipe.getShape(shape);
        return shape;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide())
            return InteractionResult.sidedSuccess(level.isClientSide());

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!(blockEntity instanceof ItemPipeBlockEntity itemPipe))
            return InteractionResult.sidedSuccess(level.isClientSide());

        // check in which voxel shape the player clicked
        VoxelShape shape = getShape(state, level, pos, CollisionContext.empty());
        Vec3 hitVec = hit.getLocation();
        Vec3 localHitVec = hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
        if (shape.bounds().contains(localHitVec)) {
            Direction direction;
            if (NORTH.bounds().contains(localHitVec)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.NORTH);
                direction = Direction.NORTH;
            } else if (SOUTH.bounds().contains(localHitVec)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.SOUTH);
                direction = Direction.SOUTH;
            } else if (EAST.bounds().contains(localHitVec)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.EAST);
                direction = Direction.EAST;
            } else if (WEST.bounds().contains(localHitVec)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.WEST);
                direction = Direction.WEST;
            } else if (TOP.bounds().contains(localHitVec)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.UP);
                direction = Direction.UP;
            } else if (BOTTOM.bounds().contains(localHitVec)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.DOWN);
                direction = Direction.DOWN;
            } else {
                direction = null;
            }
            if (direction == null)
                return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) {
                NetworkHooks.openScreen((ServerPlayer) player, itemPipe, packetBuffer -> {
                    packetBuffer.writeBlockPos(pos);
                    packetBuffer.writeNbt(itemPipe.saveConnections());
                    packetBuffer.writeEnum(direction);
                });
                return InteractionResult.SUCCESS;
            }

        }
        return InteractionResult.PASS;
    }

    private void switchMode(ItemPipeBlockEntity pipe, Direction direction) {
        var currentInput = pipe.getInput(direction);
        var currentOutput = pipe.getOutput(direction);
        var mode = EMode.DISABLED;
        if (currentInput != null)
            mode = currentInput.Mode;
        if (currentOutput != null)
            mode = currentOutput.Mode;
        pipe.setMode(direction, mode.next());
    }
/*package dev.symo.actualgenerators.block;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import dev.symo.actualgenerators.block.entity.pipe.config.EMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeBlock;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemPipeBlock extends BaseEntityBlock implements IForgeBlock {

    public static final BooleanProperty NORTH_CONNECTION = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH_CONNECTION = BooleanProperty.create("south");
    public static final BooleanProperty EAST_CONNECTION = BooleanProperty.create("east");
    public static final BooleanProperty WEST_CONNECTION = BooleanProperty.create("west");
    public static final BooleanProperty TOP_CONNECTION = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM_CONNECTION = BooleanProperty.create("bottom");
    private static final VoxelShape BASE = Block.box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape WEST = Block.box(0, 7, 7, 6, 9, 9);
    private static final VoxelShape EAST = Block.box(10, 7, 7, 16, 9, 9);
    private static final VoxelShape NORTH = Block.box(7, 7, 0, 9, 9, 6);
    private static final VoxelShape SOUTH = Block.box(7, 7, 10, 9, 9, 16);
    private static final VoxelShape BOTTOM = Block.box(7, 0, 7, 9, 6, 9);
    private static final VoxelShape TOP = Block.box(7, 10, 7, 9, 16, 9);

    public ItemPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(NORTH_CONNECTION, false)
                        .setValue(SOUTH_CONNECTION, false)
                        .setValue(EAST_CONNECTION, false)
                        .setValue(WEST_CONNECTION, false)
                        .setValue(TOP_CONNECTION, false)
                        .setValue(BOTTOM_CONNECTION, false)
        );
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return (level1, blockPos, blockState, t) -> {
            if (t instanceof ItemPipeBlockEntity itemPipe) {
                itemPipe.tick(level1, blockPos, itemPipe);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH_CONNECTION, SOUTH_CONNECTION, EAST_CONNECTION, WEST_CONNECTION, TOP_CONNECTION,
                BOTTOM_CONNECTION);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.getBlock() != pNewState.getBlock()) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
                itemPipe.onRemove();
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, neighborPos, isMoving);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
            itemPipe.onNeighborChange(level, pos, neighborPos);
        }
        updateConnections(level, pos);
    }

    //@Override
    //public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
    //    super.onNeighborChange(state, level, pos, neighbor);
    //    BlockEntity blockEntity = level.getBlockEntity(pos);
    //    if (blockEntity instanceof ItemPipeBlockEntity itemPipe) {
    //        itemPipe.onNeighborChange(level, pos, neighbor);
    //    }
    //    updateConnections((Level) level, pos);
    //}

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState otherState, boolean bool) {
        super.onPlace(state, level, pos, otherState, bool);
        updateConnections(level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean north = state.getValue(NORTH_CONNECTION);
        boolean south = state.getValue(SOUTH_CONNECTION);
        boolean east = state.getValue(EAST_CONNECTION);
        boolean west = state.getValue(WEST_CONNECTION);
        boolean top = state.getValue(TOP_CONNECTION);
        boolean bottom = state.getValue(BOTTOM_CONNECTION);
        VoxelShape shape = BASE;
        if (west) shape = Shapes.join(shape, WEST, BooleanOp.OR);
        if (east) shape = Shapes.join(shape, EAST, BooleanOp.OR);
        if (north) shape = Shapes.join(shape, NORTH, BooleanOp.OR);
        if (south) shape = Shapes.join(shape, SOUTH, BooleanOp.OR);
        if (bottom) shape = Shapes.join(shape, BOTTOM, BooleanOp.OR);
        if (top) shape = Shapes.join(shape, TOP, BooleanOp.OR);

        return shape;
    }

    public void updateConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        state = state.setValue(NORTH_CONNECTION, false);
        state = state.setValue(SOUTH_CONNECTION, false);
        state = state.setValue(EAST_CONNECTION, false);
        state = state.setValue(WEST_CONNECTION, false);
        state = state.setValue(TOP_CONNECTION, false);
        state = state.setValue(BOTTOM_CONNECTION, false);

        for (Direction direction : Direction.values()) {
            BlockEntity neighborBlockEntity = level.getBlockEntity(pos.relative(direction));
            if (neighborBlockEntity != null && neighborBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).isPresent()
                    || neighborBlockEntity instanceof ItemPipeBlockEntity) {
                switch (direction) {
                    case NORTH -> state = state.setValue(NORTH_CONNECTION, true);
                    case SOUTH -> state = state.setValue(SOUTH_CONNECTION, true);
                    case EAST -> state = state.setValue(EAST_CONNECTION, true);
                    case WEST -> state = state.setValue(WEST_CONNECTION, true);
                    case UP -> state = state.setValue(TOP_CONNECTION, true);
                    case DOWN -> state = state.setValue(BOTTOM_CONNECTION, true);
                    default -> throw new IllegalArgumentException();
                }
            }
        }

        level.setBlockAndUpdate(pos, state);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide())
            return InteractionResult.sidedSuccess(level.isClientSide());

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!(blockEntity instanceof ItemPipeBlockEntity itemPipe))
            return InteractionResult.sidedSuccess(level.isClientSide());

        // check in which voxel shape the player clicked
        VoxelShape shape = getShape(state, level, pos, CollisionContext.empty());
        Vec3 hitVec = hit.getLocation();
        Vec3 localHitVec = hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
        if (shape.bounds().contains(localHitVec)) {
            Direction direction;
            if (NORTH.bounds().contains(localHitVec) && state.getValue(NORTH_CONNECTION)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.NORTH);
                direction = Direction.NORTH;
            } else if (SOUTH.bounds().contains(localHitVec) && state.getValue(SOUTH_CONNECTION)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.SOUTH);
                direction = Direction.SOUTH;
            } else if (EAST.bounds().contains(localHitVec) && state.getValue(EAST_CONNECTION)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.EAST);
                direction = Direction.EAST;
            } else if (WEST.bounds().contains(localHitVec) && state.getValue(WEST_CONNECTION)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.WEST);
                direction = Direction.WEST;
            } else if (TOP.bounds().contains(localHitVec) && state.getValue(TOP_CONNECTION)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.UP);
                direction = Direction.UP;
            } else if (BOTTOM.bounds().contains(localHitVec) && state.getValue(BOTTOM_CONNECTION)) {
                if (player.isShiftKeyDown())
                    switchMode(itemPipe, Direction.DOWN);
                direction = Direction.DOWN;
            } else {
                direction = null;
            }
            if (direction == null)
                return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) {
                NetworkHooks.openScreen((ServerPlayer) player, itemPipe, packetBuffer -> {
                    packetBuffer.writeBlockPos(pos);
                    packetBuffer.writeNbt(itemPipe.saveConnections());
                    packetBuffer.writeEnum(direction);
                });
                return InteractionResult.SUCCESS;
            }

        }
        return InteractionResult.PASS;
    }

    private void switchMode(ItemPipeBlockEntity pipe, Direction direction) {
        var currentInput = pipe.getInput(direction);
        var currentOutput = pipe.getOutput(direction);
        var mode = EMode.DISABLED;
        if (currentInput != null)
            mode = currentInput.Mode;
        if (currentOutput != null)
            mode = currentOutput.Mode;
        pipe.setMode(direction, mode.next());
    }

}

*/
}

