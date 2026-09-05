package dev.symo.actualgenerators.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A machine's internal FE buffer.
 *
 * <p>Amounts are held as {@code long}. Forge Energy's own interface is {@code int}-based, which
 * tops out a little over two billion FE — fine for a furnace, not for an endgame reactor or the
 * bank that has to catch what it makes. So the buffer counts in longs internally and the
 * capability view clamps to {@link Integer#MAX_VALUE}: another mod asking how full this thing is
 * gets the largest number its API can express, and a single transfer through the capability moves
 * at most that much, which is far more than any real transfer. Nothing outside needs changing,
 * and nothing inside quietly overflows.
 *
 * <p>Use {@link #stored()} and {@link #capacity()} for the real figures; {@code getEnergyStored}
 * and {@code getMaxEnergyStored} exist for the capability and are deliberately lossy.
 *
 * <p>Capacity and transfer rates are mutable because energy upgrades change them at runtime.
 * The {@code receiveEnergy}/{@code extractEnergy} pair is what other mods see and is bound by
 * the transfer rate; {@link #generate(long)} and {@link #consume(long)} are the machine's own
 * unthrottled access to its buffer.
 */
public class MachineEnergyStorage implements IEnergyStorage {
    private static final String KEY_ENERGY = "Energy";

    private final Runnable onChanged;

    private long energy;
    private long capacity;
    // Rates stay int: a per-tick figure past two billion FE is not a balance anyone will tune.
    private int maxReceive;
    private int maxExtract;

    public MachineEnergyStorage(long capacity, int maxReceive, int maxExtract, Runnable onChanged) {
        this.capacity = Math.max(0, capacity);
        this.maxReceive = Math.max(0, maxReceive);
        this.maxExtract = Math.max(0, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!canReceive() || toReceive <= 0) {
            return 0;
        }
        int accepted = (int) Math.min(capacity - energy, Math.min(maxReceive, toReceive));
        if (accepted > 0 && !simulate) {
            energy += accepted;
            onChanged.run();
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (!canExtract() || toExtract <= 0) {
            return 0;
        }
        int extracted = (int) Math.min(energy, Math.min(maxExtract, toExtract));
        if (extracted > 0 && !simulate) {
            energy -= extracted;
            onChanged.run();
        }
        return extracted;
    }

    /** Clamped for the capability. Use {@link #stored()} for the figure this machine works with. */
    @Override
    public int getEnergyStored() {
        return (int) Math.min(energy, Integer.MAX_VALUE);
    }

    /** Clamped for the capability. Use {@link #capacity()} for the real buffer size. */
    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    /** FE actually in the buffer, unclamped. */
    public long stored() {
        return energy;
    }

    /** Buffer size, unclamped. */
    public long capacity() {
        return capacity;
    }

    /** Room left in the buffer. */
    public long room() {
        return Math.max(0, capacity - energy);
    }

    @Override
    public boolean canExtract() {
        return maxExtract > 0;
    }

    @Override
    public boolean canReceive() {
        return maxReceive > 0;
    }

    public int getMaxReceive() {
        return maxReceive;
    }

    public int getMaxExtract() {
        return maxExtract;
    }

    /** Adds energy the machine produced itself, ignoring the transfer rate. Returns what fit. */
    public long generate(long amount) {
        if (amount <= 0) {
            return 0;
        }
        long accepted = Math.min(capacity - energy, amount);
        if (accepted > 0) {
            energy += accepted;
            onChanged.run();
        }
        return accepted;
    }

    /** Spends energy on the machine's own work, ignoring the transfer rate. Returns what was spent. */
    public long consume(long amount) {
        if (amount <= 0) {
            return 0;
        }
        long spent = Math.min(energy, amount);
        if (spent > 0) {
            energy -= spent;
            onChanged.run();
        }
        return spent;
    }

    public boolean hasEnergy(long amount) {
        return energy >= amount;
    }

    public boolean isFull() {
        return energy >= capacity;
    }

    /**
     * Applies new limits after an upgrade change. Energy above the new capacity is kept only up
     * to that capacity — removing energy upgrades spills the excess rather than duplicating it.
     */
    public void setLimits(long capacity, int maxReceive, int maxExtract) {
        this.capacity = Math.max(0, capacity);
        this.maxReceive = Math.max(0, maxReceive);
        this.maxExtract = Math.max(0, maxExtract);
        if (energy > this.capacity) {
            energy = this.capacity;
            onChanged.run();
        }
    }

    public void setEnergy(long value) {
        long clamped = Math.clamp(value, 0, capacity);
        if (clamped != energy) {
            energy = clamped;
            onChanged.run();
        }
    }

    public void save(CompoundTag tag) {
        tag.putLong(KEY_ENERGY, energy);
    }

    public void load(CompoundTag tag) {
        // getLong reads zero from a tag written as an int, so a world saved before the buffer
        // counted in longs has to be read back as one.
        long saved = tag.contains(KEY_ENERGY, Tag.TAG_LONG) ? tag.getLong(KEY_ENERGY) : tag.getInt(KEY_ENERGY);
        energy = Math.clamp(saved, 0, capacity);
    }
}
