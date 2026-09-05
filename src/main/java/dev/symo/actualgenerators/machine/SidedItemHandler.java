package dev.symo.actualgenerators.machine;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The inventory view a machine face exposes to the outside world.
 *
 * <p>Machines keep their input and output slots in separate handlers and fill their own outputs
 * directly. This wrapper is what the rest of the world sees: it presents the input slots first
 * and the output slots after, and enforces the direction of travel — you may insert into inputs
 * and extract from outputs, never the reverse. A face configured for only one direction simply
 * gets the handler it is allowed.
 */
public final class SidedItemHandler implements IItemHandler {
    private final @Nullable IItemHandler inputs;
    private final @Nullable IItemHandler outputs;
    private final int inputSlots;
    private final int outputSlots;

    public SidedItemHandler(@Nullable IItemHandler inputs, @Nullable IItemHandler outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.inputSlots = inputs == null ? 0 : inputs.getSlots();
        this.outputSlots = outputs == null ? 0 : outputs.getSlots();
    }

    public boolean isEmptyView() {
        return inputSlots + outputSlots == 0;
    }

    @Override
    public int getSlots() {
        return inputSlots + outputSlots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (isInput(slot)) {
            return inputs.getStackInSlot(slot);
        }
        if (isOutput(slot)) {
            return outputs.getStackInSlot(slot - inputSlots);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // Outputs are results; nothing may be pushed back into them.
        return isInput(slot) ? inputs.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        // Inputs are the machine's to consume; only results may be taken.
        return isOutput(slot) ? outputs.extractItem(slot - inputSlots, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        if (isInput(slot)) {
            return inputs.getSlotLimit(slot);
        }
        return isOutput(slot) ? outputs.getSlotLimit(slot - inputSlots) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isInput(slot) && inputs.isItemValid(slot, stack);
    }

    private boolean isInput(int slot) {
        return inputs != null && slot >= 0 && slot < inputSlots;
    }

    private boolean isOutput(int slot) {
        return outputs != null && slot >= inputSlots && slot < inputSlots + outputSlots;
    }
}
