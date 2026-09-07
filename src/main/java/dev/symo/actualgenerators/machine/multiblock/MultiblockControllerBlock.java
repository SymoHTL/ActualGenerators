package dev.symo.actualgenerators.machine.multiblock;

import dev.symo.actualgenerators.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The block half of a multiblock controller: a machine block that also says, in its state,
 * whether the structure behind it stands.
 *
 * <p>The controller is part of the front wall and faces out of it; the interior is behind. Its
 * redstone reading is its own neighbours plus every redstone hatch in the shell.
 */
public abstract class MultiblockControllerBlock extends MachineBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected MultiblockControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // A second controller set into somebody's shell has to be noticed by the first one.
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

    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighbourBlock,
                                   BlockPos neighbourPos,
                                   boolean movedByPiston) {
        MultiblockControllerBlockEntity.besideShellChanged(level, neighbourPos);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.onNeighbourChanged(controller.readPower());
            return;
        }
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
    }
}
