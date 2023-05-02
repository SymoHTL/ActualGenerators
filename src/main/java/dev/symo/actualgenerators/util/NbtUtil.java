package dev.symo.actualgenerators.util;

import dev.symo.actualgenerators.block.entity.pipe.config.PipeIO;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class NbtUtil {
    public static CompoundTag writePipeIOArrayToNBT(CompoundTag compound, PipeIO[] pipeIOArray) {
        ListTag pipeIOList = new ListTag();

        for (PipeIO pipeIO : pipeIOArray) {
            CompoundTag pipeIOTag = new CompoundTag();

            pipeIOTag.putInt("moveAmount", pipeIO.moveAmount);
            pipeIOTag.putInt("Priority", pipeIO.Priority);
            pipeIOTag.putString("PlacementStrategy", pipeIO.PlacementStrategy.name());
            pipeIOTag.putString("Channel", pipeIO.Channel.name());
            pipeIOTag.putString("RedstoneMode", pipeIO.RedstoneMode.name());

            pipeIOTag.putInt("direction", pipeIO.direction.ordinal());
            pipeIOTag.putDouble("distance", pipeIO.distance);

            BlockPos pipePos = pipeIO.pipe.getBlockPos();
            pipeIOTag.putInt("pipeX", pipePos.getX());
            pipeIOTag.putInt("pipeY", pipePos.getY());
            pipeIOTag.putInt("pipeZ", pipePos.getZ());

            pipeIOList.add(pipeIOTag);
        }

        compound.put("pipeIOArray", pipeIOList);
        return compound;
    }

}
