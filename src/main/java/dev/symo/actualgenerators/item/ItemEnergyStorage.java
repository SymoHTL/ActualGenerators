package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The standard FE view of one of this mod's energy items.
 *
 * <p>NeoForge ships {@code ComponentEnergyStorage} for exactly this job, but it stores an
 * {@code int}, and these items count in longs for the same reason a machine buffer does. So this
 * is the same idea over a long component: the charge lives on the stack, and the two capability
 * readings clamp to what the FE interface can express.
 *
 * <p>It writes straight through to the stack it was handed, because item capabilities are
 * per-stack views rather than stored objects — whoever asked for one is holding the stack.
 */
public class ItemEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;
    private final long capacity;
    private final int transferRate;

    public ItemEnergyStorage(ItemStack stack, long capacity, int transferRate) {
        this.stack = stack;
        this.capacity = Math.max(0, capacity);
        this.transferRate = Math.max(0, transferRate);
    }

    /** FE on a stack, whatever energy item it is. */
    public static long energyOf(ItemStack stack, long capacity) {
        return Math.clamp(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0L), 0, capacity);
    }

    /** Writes a charge onto a stack. Mutates in place — components are per-stack. */
    public static void setEnergy(ItemStack stack, long energy, long capacity) {
        stack.set(ModDataComponents.ENERGY.get(), Math.clamp(energy, 0, capacity));
    }

    private long stored() {
        return energyOf(stack, capacity);
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!canReceive() || toReceive <= 0) {
            return 0;
        }
        long stored = stored();
        int accepted = (int) Math.min(capacity - stored, Math.min(transferRate, toReceive));
        if (accepted > 0 && !simulate) {
            setEnergy(stack, stored + accepted, capacity);
        }
        return Math.max(accepted, 0);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (!canExtract() || toExtract <= 0) {
            return 0;
        }
        long stored = stored();
        int extracted = (int) Math.min(stored, Math.min(transferRate, toExtract));
        if (extracted > 0 && !simulate) {
            setEnergy(stack, stored - extracted, capacity);
        }
        return Math.max(extracted, 0);
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(stored(), Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    @Override
    public boolean canExtract() {
        return transferRate > 0;
    }

    @Override
    public boolean canReceive() {
        return transferRate > 0;
    }
}
