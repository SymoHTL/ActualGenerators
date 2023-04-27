package dev.symo.actualgenerators.block.entity.pipe.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public class Connection {
    private final BlockPos pos;
    private final Direction direction;
    private final int distance;
    private LazyOptional<IItemHandler> itemHandler;
    private LazyOptional<IEnergyStorage> energyHandler;
    private LazyOptional<IFluidHandler> fluidHandler;

    public Connection(BlockPos pos, Direction direction, int distance) {
        this.pos = pos;
        this.direction = direction;
        this.distance = distance;
        this.itemHandler = LazyOptional.empty();
        this.energyHandler = LazyOptional.empty();
        this.fluidHandler = LazyOptional.empty();
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "pos=" + pos +
                ", direction=" + direction +
                ", distance=" + distance +
                '}';
    }

    public LazyOptional<IItemHandler> getItemHandler(Level level) {
        if (!itemHandler.isPresent()) {
            itemHandler = getCapabilityRaw(level, ForgeCapabilities.ITEM_HANDLER);
        }
        return itemHandler;
    }

    public LazyOptional<IEnergyStorage> getEnergyHandler(Level level) {
        if (!energyHandler.isPresent()) {
            energyHandler = getCapabilityRaw(level, ForgeCapabilities.ENERGY);
        }
        return energyHandler;
    }

    public LazyOptional<IFluidHandler> getFluidHandler(Level level) {
        if (!fluidHandler.isPresent()) {
            fluidHandler = getCapabilityRaw(level, ForgeCapabilities.FLUID_HANDLER);
        }
        return fluidHandler;
    }


    public <T> LazyOptional<T> getCapability(Level level, Capability<T> capability) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return getItemHandler(level).cast();
        } else if (capability == ForgeCapabilities.ENERGY) {
            return getEnergyHandler(level).cast();
        } else if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return getFluidHandler(level).cast();
        }
        return LazyOptional.empty();
    }

    private <T> LazyOptional<T> getCapabilityRaw(Level level, Capability<T> capability) {
        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) {
            return LazyOptional.empty();
        }
        if (te instanceof PipeTileEntity) {
            return LazyOptional.empty();
        }
        return te.getCapability(capability, direction);
    }

}