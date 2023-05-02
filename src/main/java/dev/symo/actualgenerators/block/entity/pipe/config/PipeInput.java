package dev.symo.actualgenerators.block.entity.pipe.config;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class PipeInput extends PipeIO {

    public BlockEntity from;
    public IItemHandler fromHandler;

    public PipeInput(ItemPipeBlockEntity pipe, BlockEntity to, IItemHandler toHandler, Direction direction, PipeIO io) {
        this(io);
        this.pipe = pipe;
        this.from = to;
        this.fromHandler = toHandler;
        this.direction = direction;
        this.distance = pipe.getBlockPos().distSqr(to.getBlockPos());
    }

    public PipeInput(ItemPipeBlockEntity pipe, BlockEntity to, IItemHandler toHandler, Direction direction, EMode mode) {
        this(pipe, to, toHandler, direction);
        this.Mode = mode;
    }

    public PipeInput(ItemPipeBlockEntity pipe, BlockEntity to, IItemHandler toHandler, Direction direction) {
        this.Mode = EMode.EXTRACT;
        this.pipe = pipe;
        this.from = to;
        this.fromHandler = toHandler;
        this.direction = direction;
        this.distance = pipe.getBlockPos().distSqr(to.getBlockPos());
    }

    public PipeInput(PipeIO pipeIo) {
        super(pipeIo);
    }

    public static PipeInput LoadFromNBT(CompoundTag compound, ItemPipeBlockEntity pipe) {
        PipeIO pipeIo = PipeIO.LoadFromNBT(compound, pipe);
        PipeInput pipeInput = new PipeInput(pipeIo);

        BlockPos toPos = new BlockPos(compound.getInt("fromX"), compound.getInt("fromY"), compound.getInt("fromZ"));
        pipeInput.from = pipe.getLevel().getBlockEntity(toPos);
        pipeInput.fromHandler = pipeInput.from.getCapability(ForgeCapabilities.ITEM_HANDLER, pipeInput.direction.getOpposite()).orElse(null);

        return pipeInput;
    }

    @Override
    public void SaveToNBT(CompoundTag compound) {
        super.SaveToNBT(compound);
        BlockPos toPos = from.getBlockPos();
        compound.putInt("fromX", toPos.getX());
        compound.putInt("fromY", toPos.getY());
        compound.putInt("fromZ", toPos.getZ());
    }

    @Override
    public String toString() {
        return "PipeInput{" +
                "from=" + from +
                ", fromHandler=" + fromHandler +
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
