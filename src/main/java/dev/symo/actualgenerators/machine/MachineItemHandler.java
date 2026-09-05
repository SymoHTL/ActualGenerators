package dev.symo.actualgenerators.machine;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.function.IntSupplier;

/**
 * A machine's item slots, which hold more than a stack once the machine works in bigger lots.
 *
 * <p>The limit is a supplier, so it follows the stack upgrades and the tier as they come and go:
 * an input slot holds a configured number of operations' worth of items, an output slot as many
 * operations' worth of results, and never less than a vanilla stack. That is what makes a machine's throughput the
 * batch and only the batch. An AE2 pattern provider pushes until insertion is refused, so the slot
 * limit <em>is</em> the items per tick a machine can be fed, and a slot capped at sixty-four would
 * cap a netherite machine at sixty-four a tick whatever its batch said. More slots would only move
 * the same ceiling; a bigger slot removes it.
 *
 * <p>Two things vanilla does at sixty-four are undone here. Its stack codec refuses a count over
 * ninety-nine and {@code ItemStack.save} throws on it, so the count is written beside the item
 * rather than inside it, the way AE2 and Sophisticated Storage do; the shape a plain handler wrote
 * before still loads. And extraction is not capped at the item's stack size, because the machine
 * takes a whole operation out at once; everything outside asks for what it can hold anyway.
 */
public class MachineItemHandler extends ItemStackHandler {
    private static final String KEY_ITEMS = "Items";
    private static final String KEY_SIZE = "Size";
    private static final String KEY_SLOT = "Slot";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_COUNT = "Count";
    /** The shape a plain {@link ItemStackHandler} writes carries the item's own id inline. */
    private static final String KEY_VANILLA_ID = "id";

    private final IntSupplier limit;
    private final Runnable onChange;

    public MachineItemHandler(int slots, IntSupplier limit, Runnable onChange) {
        super(slots);
        this.limit = limit;
        this.onChange = onChange;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Math.max(64, limit.getAsInt());
    }

    /** The item's own stack size is not the ceiling here; an unstackable item still stays one. */
    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return stack.isStackable() ? getSlotLimit(slot) : 1;
    }

    /** The same rule, for whoever sizes a batch against the room a slot has. */
    public int limitFor(int slot, ItemStack stack) {
        return getStackLimit(slot, stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        validateSlotIndex(slot);
        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, existing.getCount());
        if (!simulate) {
            if (taken == existing.getCount()) {
                stacks.set(slot, ItemStack.EMPTY);
            } else {
                existing.shrink(taken);
            }
            onContentsChanged(slot);
        }
        return existing.copyWithCount(taken);
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChange.run();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_SLOT, slot);
            entry.putInt(KEY_COUNT, stack.getCount());
            entry.put(KEY_ITEM, stack.copyWithCount(1).save(provider));
            items.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put(KEY_ITEMS, items);
        tag.putInt(KEY_SIZE, stacks.size());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        setSize(tag.contains(KEY_SIZE, Tag.TAG_INT) ? tag.getInt(KEY_SIZE) : stacks.size());
        for (Tag element : tag.getList(KEY_ITEMS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            int slot = entry.getInt(KEY_SLOT);
            if (slot < 0 || slot >= stacks.size()) {
                continue;
            }
            if (entry.contains(KEY_VANILLA_ID)) {
                stacks.set(slot, ItemStack.parseOptional(provider, entry));
                continue;
            }
            ItemStack stack = ItemStack.parseOptional(provider, entry.getCompound(KEY_ITEM));
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, entry.getInt(KEY_COUNT)));
            }
            stacks.set(slot, stack);
        }
        onLoad();
    }
}
