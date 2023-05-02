package dev.symo.actualgenerators.block.entity.pipe.config;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;


public class PipeOutput extends PipeIO {
    public BlockEntity to;
    public IItemHandler toHandler;

    public PipeOutput(ItemPipeBlockEntity pipe, BlockEntity to, IItemHandler toHandler, Direction direction, PipeIO io) {
        this(io);
        this.pipe = pipe;
        this.to = to;
        this.toHandler = toHandler;
        this.direction = direction;
        this.distance = pipe.getBlockPos().distSqr(to.getBlockPos());
    }

    public PipeOutput(ItemPipeBlockEntity pipe, BlockEntity to, IItemHandler toHandler, Direction direction, EMode mode) {
        this(pipe, to, toHandler, direction);
        this.Mode = mode;
    }

    public PipeOutput(ItemPipeBlockEntity pipe, BlockEntity to, IItemHandler toHandler, Direction direction) {
        this.Mode = EMode.INSERT;
        this.pipe = pipe;
        this.to = to;
        this.toHandler = toHandler;
        this.direction = direction;
        this.distance = pipe.getBlockPos().distSqr(to.getBlockPos());
    }

    public PipeOutput(PipeIO pipeIo) {
        super(pipeIo);
    }

    public static PipeOutput LoadFromNBT(CompoundTag compound, ItemPipeBlockEntity pipe) {
        PipeIO pipeIo = PipeIO.LoadFromNBT(compound, pipe);
        PipeOutput pipeOutput = new PipeOutput(pipeIo);

        BlockPos toPos = new BlockPos(compound.getInt("toX"), compound.getInt("toY"), compound.getInt("toZ"));
        pipeOutput.to = pipe.getLevel().getBlockEntity(toPos);
        pipeOutput.toHandler = pipeOutput.to.getCapability(ForgeCapabilities.ITEM_HANDLER, pipeOutput.direction.getOpposite()).orElse(null);

        return pipeOutput;
    }

    @Override
    public void SaveToNBT(CompoundTag compound) {
        super.SaveToNBT(compound);
        BlockPos toPos = to.getBlockPos();
        compound.putInt("toX", toPos.getX());
        compound.putInt("toY", toPos.getY());
        compound.putInt("toZ", toPos.getZ());
    }

    @Override
    public String toString() {
        return "PipeOutput{" +
                "to=" + to +
                ", toHandler=" + toHandler +
                ", moveAmount=" + moveAmount +
                ", Priority=" + Priority +
                ", PlacementStrategy=" + PlacementStrategy +
                ", Channel=" + Channel +
                ", Mode=" + Mode +
                ", RedstoneMode=" + RedstoneMode +
                ", direction=" + direction +
                ", pipe=" + pipe +
                ", distance=" + distance +
                '}';
    }
}
