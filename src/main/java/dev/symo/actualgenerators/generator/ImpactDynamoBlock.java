package dev.symo.actualgenerators.generator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.symo.actualgenerators.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ImpactDynamoBlock extends MachineBlock {
    public static final MapCodec<ImpactDynamoBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(propertiesCodec()).apply(instance, ImpactDynamoBlock::new));

    public ImpactDynamoBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ImpactDynamoBlockEntity(pos, state);
    }

    /**
     * The whole generator, in one event.
     *
     * <p>This runs from {@code Entity#checkFallDamage} while the falling block is still moving,
     * one step before it decides whether to place itself — so telling it to drop nothing here is
     * what turns a landing into a catch. Everything else on the impact path is skipped too: a
     * dynamo that swallows an anvil takes the blow instead of whoever was standing on it.
     *
     * <p>If the dynamo declines, the block lands exactly as it would have without one.
     */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!level.isClientSide
                && entity instanceof FallingBlockEntity falling
                && level.getBlockEntity(pos) instanceof ImpactDynamoBlockEntity dynamo
                && dynamo.absorb(falling, fallDistance)) {
            falling.disableDrop();
            return;
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }
}
