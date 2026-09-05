package dev.symo.actualgenerators.machine;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The view of a machine's energy buffer that a particular face exposes to the outside world.
 *
 * <p>The buffer itself stays fully capable; this wrapper is what enforces the face's
 * {@link IoMode}, so a face set to output cannot be used to push energy in.
 */
public record SidedEnergyWrapper(MachineEnergyStorage delegate, IoMode mode) implements IEnergyStorage {

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return mode.canInput() ? delegate.receiveEnergy(toReceive, simulate) : 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return mode.canOutput() ? delegate.extractEnergy(toExtract, simulate) : 0;
    }

    @Override
    public int getEnergyStored() {
        return delegate.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return delegate.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return mode.canOutput() && delegate.canExtract();
    }

    @Override
    public boolean canReceive() {
        return mode.canInput() && delegate.canReceive();
    }
}
