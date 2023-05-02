package dev.symo.actualgenerators.block.entity.pipe.config;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public class PipeIO {

    public int moveAmount = 16;
    public int Priority = 0;
    public EItemPlacementStrategy PlacementStrategy = EItemPlacementStrategy.CLOSEST;
    public EChannel Channel = EChannel.White;
    public EMode Mode = EMode.DISABLED;
    public ERedstoneMode RedstoneMode = ERedstoneMode.High;
    public Direction direction;

    public ItemPipeBlockEntity pipe;
    public double distance;

    protected PipeIO(PipeIO pipeIo) {
        this.Mode = pipeIo.Mode;
        this.pipe = pipeIo.pipe;
        this.direction = pipeIo.direction;
        this.distance = pipeIo.distance;
        this.moveAmount = pipeIo.moveAmount;
        this.Priority = pipeIo.Priority;
        this.PlacementStrategy = pipeIo.PlacementStrategy;
        this.Channel = pipeIo.Channel;
        this.RedstoneMode = pipeIo.RedstoneMode;
    }

    public PipeIO() {

    }

    protected static PipeIO LoadFromNBT(CompoundTag compound, ItemPipeBlockEntity pipe) {
        PipeIO pipeIO = new PipeIO();
        pipeIO.moveAmount = compound.getInt("moveAmount");
        pipeIO.Priority = compound.getInt("Priority");
        pipeIO.PlacementStrategy = EItemPlacementStrategy.valueOf(compound.getString("PlacementStrategy"));
        pipeIO.Channel = EChannel.valueOf(compound.getString("Channel"));
        pipeIO.RedstoneMode = ERedstoneMode.valueOf(compound.getString("RedstoneMode"));
        pipeIO.Mode = EMode.valueOf(compound.getString("Mode"));

        pipeIO.direction = Direction.values()[compound.getInt("direction")];
        pipeIO.distance = compound.getDouble("distance");

        pipeIO.pipe = pipe;

        return pipeIO;
    }

    public void SaveToNBT(CompoundTag compound) {
        compound.putInt("moveAmount", moveAmount);
        compound.putInt("Priority", Priority);
        compound.putString("PlacementStrategy", PlacementStrategy.name());
        compound.putString("Channel", Channel.name());
        compound.putString("RedstoneMode", RedstoneMode.name());
        compound.putString("Mode", Mode.name());

        compound.putInt("direction", direction.ordinal());
        compound.putDouble("distance", distance);

        BlockPos pipePos = pipe.getBlockPos();
        compound.putInt("pipeX", pipePos.getX());
        compound.putInt("pipeY", pipePos.getY());
        compound.putInt("pipeZ", pipePos.getZ());
    }
}
