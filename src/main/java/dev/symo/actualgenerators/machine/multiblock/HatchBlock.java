package dev.symo.actualgenerators.machine.multiblock;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import dev.symo.actualgenerators.menu.MultiblockControllerMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.SimpleMenuProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A casing with a way through it.
 *
 * <p>A multiblock's controller has no faces of its own: everything that goes in or out goes
 * through a hatch somewhere in the shell, Modern Industrialization's way. A hatch holds nothing
 * and ticks never; it exposes whichever of the controller's capabilities its kind stands for,
 * resolved on every call, so an unformed structure or an unloaded controller simply answers with
 * an empty handler and nothing has to be invalidated.
 *
 * <p>A hatch has a front, the way every machine has: the side facing the player who placed it.
 * Its six faces are set up in the machine side panel every other machine has, relative to that
 * front, and faces that look into the structure are greyed out there. Using an item, energy or
 * fluid hatch opens the controller's own window with that panel beside it, so a player at the
 * back of a fifteen-block box never has to walk round to the front and never needs a second
 * window. A redstone hatch opens a window of its own: what it reports ({@link HatchSignal}).
 */
public class HatchBlock extends BaseEntityBlock {
    /** The side facing the player who placed it; the side panel's faces are relative to it. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final HatchKind kind;
    private final MapCodec<HatchBlock> codec;

    public HatchBlock(HatchKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = RecordCodecBuilder.mapCodec(instance ->
                instance.group(propertiesCodec()).apply(instance, props -> new HatchBlock(kind, props)));
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MultiblockCasingBlock.FORMED, false));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public HatchKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MultiblockCasingBlock.FORMED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HatchBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof HatchBlockEntity hatch) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (kind == HatchKind.REDSTONE) {
            serverPlayer.openMenu(hatch, buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        MultiblockControllerBlockEntity controller = hatch.controller();
        if (controller instanceof MenuProvider provider) {
            // The controller's own window, told which hatch it was opened from: the server-side
            // menu is handed the block entity, the client reads its position after the controller's.
            BlockPos controllerPos = controller.getBlockPos();
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> {
                AbstractContainerMenu menu = provider.createMenu(id, inventory, opener);
                if (menu instanceof MultiblockControllerMenu<?> window) {
                    window.withHatch(hatch);
                }
                return menu;
            }, provider.getDisplayName()), buffer -> {
                buffer.writeBlockPos(controllerPos);
                buffer.writeBlockPos(pos);
            });
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("block.actualgenerators.hatch.unformed"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            MultiblockControllerBlockEntity.shellChanged(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this)) {
            MultiblockControllerBlockEntity.shellChanged(level, pos);
        }
    }

    // ------------------------------------------------------------------ redstone

    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind == HatchKind.REDSTONE;
    }

    /** A reporting hatch gives its level off on every side, the way a lever powers what it touches. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == HatchKind.REDSTONE && level.getBlockEntity(pos) instanceof HatchBlockEntity hatch
                ? hatch.emitted()
                : 0;
    }

    /** A control hatch hands the signal reaching it to the controller, the tick it changes. */
    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighbourBlock,
                                   BlockPos neighbourPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        MultiblockControllerBlockEntity.besideShellChanged(level, neighbourPos);
        if (kind == HatchKind.REDSTONE && !level.isClientSide
                && level.getBlockEntity(pos) instanceof HatchBlockEntity hatch
                && hatch.mode() == HatchSignal.CONTROL) {
            MultiblockControllerBlockEntity controller = hatch.controller();
            if (controller != null) {
                controller.onNeighbourChanged(controller.readPower());
            }
        }
    }
}
