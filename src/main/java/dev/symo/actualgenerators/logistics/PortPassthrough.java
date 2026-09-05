package dev.symo.actualgenerators.logistics;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * What a block that pushes INTO a pad talks to: no inventory, no tank, no buffer. Whatever is
 * pushed in goes straight out on the channels the pad has PU on, to the receivers a send would
 * reach, and whatever nobody takes is handed back in the same call.
 *
 * <p>This is the pattern-provider case: the block behind the pad hands it things, and the pad is
 * a door onto the network rather than a chest in front of it. The pusher's own limits are the
 * only limits: no Amount, no Delay, no keep-behind and no fee. Filters, priorities, spread, the
 * receivers' keep and the pad's redstone mode all still apply.
 *
 * <p>Nothing can be taken out of it, and it reports itself empty with no room of its own, because
 * it is: a capacity would be a promise to hold something, and it holds nothing.
 */
public final class PortPassthrough implements IItemHandler, IFluidHandler, IEnergyStorage {
    private final PortFace pad;

    PortPassthrough(PortFace pad) {
        this.pad = pad;
    }

    // ------------------------------------------------------------------ items

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return pad.pushItems(stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    /** The pusher's limits are the limits. */
    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------ fluid

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return pad.pushFluid(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }

    // ------------------------------------------------------------------ energy

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return pad.pushEnergy(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return 0;
    }

    @Override
    public int getMaxEnergyStored() {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
