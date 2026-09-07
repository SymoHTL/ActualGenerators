package dev.symo.actualgenerators.machine.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The plain wall block of every multiblock.
 *
 * <p>It has no block entity and never ticks: a wall of a few hundred of these costs the server
 * nothing. The one thing it does is tell the controllers near it that the shell changed, so a
 * structure is checked when a casing is placed or broken and at no other time. Its one state
 * says whether it is part of a box that stands; the controller sets it, so the whole shell
 * changes its look the tick the box forms.
 */
public class MultiblockCasingBlock extends Block {
    /** The same property on the casing, the hatches and the controller: one look for a formed box. */
    public static final BooleanProperty FORMED = MultiblockControllerBlock.FORMED;

    public MultiblockCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
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

    /** The interior is only ever seen from the shell round it. */
    @Override
    protected void neighborChanged(BlockState state,
                                   Level level,
                                   BlockPos pos,
                                   Block neighbourBlock,
                                   BlockPos neighbourPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        MultiblockControllerBlockEntity.besideShellChanged(level, neighbourPos);
    }
}
