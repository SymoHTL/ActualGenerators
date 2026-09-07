package dev.symo.actualgenerators.machine.multiblock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The plate block under a Geothermal Fissure Tap: a casing that also knows which piece of the
 * coil it is while the plate stands ({@link CoilPiece#COIL}), set by the tap along with
 * {@code FORMED}.
 */
public class GeothermalCasingBlock extends MultiblockCasingBlock {
    public GeothermalCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false).setValue(CoilPiece.COIL, CoilPiece.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CoilPiece.COIL);
    }
}
