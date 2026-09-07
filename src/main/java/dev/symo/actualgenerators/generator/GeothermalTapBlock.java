package dev.symo.actualgenerators.generator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.symo.actualgenerators.machine.MachineBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The controller of a Geothermal Fissure Tap: the wellhead in the front wall of a box of casing.
 * A bucket used on it takes corium out of the tank before the window opens, like on any machine
 * with a tank.
 */
public class GeothermalTapBlock extends MultiblockControllerBlock {
    public static final MapCodec<GeothermalTapBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(propertiesCodec()).apply(instance, GeothermalTapBlock::new));

    public GeothermalTapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeothermalTapBlockEntity(pos, state);
    }
}
