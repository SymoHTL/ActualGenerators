package dev.symo.actualgenerators.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A tank seen through one face: fills only where the face lets fluid in, drains only where it lets it out. */
public record SidedFluidHandler(IFluidHandler tank, IoMode mode) implements IFluidHandler {
    @Override
    public int getTanks() {
        return tank.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int index) {
        return tank.getFluidInTank(index);
    }

    @Override
    public int getTankCapacity(int index) {
        return tank.getTankCapacity(index);
    }

    @Override
    public boolean isFluidValid(int index, FluidStack stack) {
        return mode.canInput() && tank.isFluidValid(index, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return mode.canInput() ? tank.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return mode.canOutput() ? tank.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return mode.canOutput() ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
    }
}
