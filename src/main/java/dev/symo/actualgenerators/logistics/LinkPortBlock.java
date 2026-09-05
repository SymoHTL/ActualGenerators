package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.menu.LinkPortMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pads: two pixels of hardware on the face of whatever they serve, up to six in one block space.
 *
 * <p>A machine has six faces and a base has gaps one block wide, so one pad per block position was
 * the wrong unit — a single block between two machines now serves both. Each face is a separate
 * port with its own network, filters and switches; the block is only the thing they are screwed to.
 *
 * <p>It is waterloggable, because a pad that a bucket of water washes off the wall is not hardware.
 */
public class LinkPortBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final Map<Direction, BooleanProperty> FACE_PROPERTIES = Map.of(
            Direction.DOWN, BlockStateProperties.DOWN,
            Direction.UP, BlockStateProperties.UP,
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.EAST, BlockStateProperties.EAST);

    private static final Map<Direction, VoxelShape> FACE_SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(2, 2, 0, 14, 14, 2),
            Direction.SOUTH, Block.box(2, 2, 14, 14, 14, 16),
            Direction.WEST, Block.box(0, 2, 2, 2, 14, 14),
            Direction.EAST, Block.box(14, 2, 2, 16, 14, 14),
            Direction.DOWN, Block.box(2, 0, 2, 14, 2, 14),
            Direction.UP, Block.box(2, 14, 2, 14, 16, 14)));

    public LinkPortBlock(Properties properties) {
        super(properties);
        BlockState state = getStateDefinition().any().setValue(WATERLOGGED, false);
        for (BooleanProperty property : FACE_PROPERTIES.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(LinkPortBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
        FACE_PROPERTIES.values().forEach(builder::add);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkPortBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The two-pixel plate a pad on this face occupies, for anything that wants to draw round it. */
    public static VoxelShape shape(Direction direction) {
        return FACE_SHAPES.get(direction);
    }

    public static boolean hasFace(BlockState state, Direction direction) {
        BooleanProperty property = FACE_PROPERTIES.get(direction);
        return property != null && state.hasProperty(property) && state.getValue(property);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = Shapes.empty();
        for (Direction direction : Direction.values()) {
            if (hasFace(state, direction)) {
                shape = Shapes.or(shape, FACE_SHAPES.get(direction));
            }
        }
        return shape.isEmpty() ? FACE_SHAPES.get(Direction.NORTH) : shape;
    }

    // ------------------------------------------------------------------ placement

    /**
     * Placed against the face that was clicked, pointing back into it — and merged into a port
     * block that is already there rather than refusing to go.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        BlockState base = existing.is(this) ? existing : defaultBlockState()
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos())
                        .getType() == Fluids.WATER);
        if (context.getLevel().getBlockState(context.getClickedPos().relative(face)).isAir()) {
            return null;
        }
        return base.setValue(FACE_PROPERTIES.get(face), true);
    }

    /** A port item aimed at a port block that has no pad on that face adds one instead of bouncing. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        return context.getItemInHand().is(asItem()) && !hasFace(state, face);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof LinkPortBlockEntity port)) {
            return;
        }
        UUID placedBy = placer instanceof Player player ? player.getUUID() : null;
        for (Direction direction : Direction.values()) {
            if (hasFace(state, direction) && !port.hasFace(direction)) {
                port.addFace(direction).setPlacer(placedBy);
            }
        }
        port.setPowered(port.readPower(level));
    }

    /** A pad whose block went away goes with it; the rest of the block stays. */
    @Override
    protected BlockState updateShape(BlockState state,
                                     Direction direction,
                                     BlockState neighbourState,
                                     LevelAccessor level,
                                     BlockPos pos,
                                     BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (hasFace(state, direction) && neighbourState.isAir()) {
            BlockState without = state.setValue(FACE_PROPERTIES.get(direction), false);
            if (level.getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
                port.removeFace(direction);
            }
            return anyFace(without) ? without : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    private static boolean anyFace(BlockState state) {
        for (Direction direction : Direction.values()) {
            if (hasFace(state, direction)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return anyFace(state);
    }

    // ------------------------------------------------------------------ interaction

    /**
     * Opens the window for the pad the player is looking at.
     *
     * <p>A pad is approached from its open side, so the block face under the cursor is the opposite
     * of the pad's own direction.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof LinkPortBlockEntity port
                && player instanceof ServerPlayer serverPlayer) {
            PortFace face = port.faceFor(hit.getDirection());
            if (face == null) {
                return InteractionResult.PASS;
            }
            // A linked pad is its network's: the window is for the owner and the invited.
            if (LinkNetworkManager.admits(serverPlayer, face.networkId())) {
                serverPlayer.openMenu(new LinkPortMenu.Opener(port, face.direction()),
                        buffer -> LinkPortMenu.writeOpener(buffer, port, face.direction()));
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * A port item aimed at a face that has no pad adds one; anything else falls through so the
     * item gets its turn. The linking tool does not even need that: it runs before the block.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighbourBlock,
                                   BlockPos neighbourPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
            port.setPowered(port.readPower(level));
            // Something next door changed, which is exactly when a sleeping network is worth
            // waking: a hopper filled a chest, a machine finished, a lever moved.
            port.wakeNetworks();
        }
    }

    /** The block behind a pad changed what it holds, the way a comparator hears it: a stock sensor looks again. */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
            port.wakeIfSensing(neighbor);
        }
    }

    // ------------------------------------------------------------------ redstone out

    /**
     * A pad receiving redstone is a lever screwed onto the block it is on: it powers that block
     * strongly, at the level of the highest channel it receives on, and everything else round the
     * port block weakly, as a lever does. {@code side} is the way the asker looks, so the pad a
     * strong signal means is on the opposite face.
     */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof LinkPortBlockEntity port ? port.signalAround() : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return signalOf(level, pos, side);
    }

    private static int signalOf(BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof LinkPortBlockEntity port ? port.signalToward(side.getOpposite()) : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
            if (level instanceof ServerLevel) {
                port.leaveAllNetworks();
            }
            port.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
