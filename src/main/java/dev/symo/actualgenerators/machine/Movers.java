package dev.symo.actualgenerators.machine;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Moving things between two handlers, the way every machine face and every hatch does it.
 *
 * <p>Items go slot by slot and whole; energy goes in one capped call per tick the pass covers,
 * because every {@code receiveEnergy} is capped at the receiver's own per-tick rate and a single
 * call carrying ten ticks' worth moves a tenth of it; fluid goes in one go, as much as the
 * destination takes of what the source gives.
 */
public final class Movers {
    private Movers() {
    }

    /** Everything extractable from {@code from} that {@code to} accepts. */
    public static boolean pushItems(IItemHandler from, IItemHandler to) {
        boolean moved = false;
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack extractable = from.extractItem(slot, Integer.MAX_VALUE, true);
            if (extractable.isEmpty()) {
                continue;
            }
            ItemStack leftover = ItemHandlerHelper.insertItem(to, extractable, false);
            int count = extractable.getCount() - leftover.getCount();
            if (count > 0) {
                from.extractItem(slot, count, false);
                moved = true;
            }
        }
        return moved;
    }

    /** Everything extractable from {@code from} that {@code into} has room for, the room checked first. */
    public static boolean pullItems(IItemHandler into, IItemHandler from) {
        boolean moved = false;
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack extractable = from.extractItem(slot, Integer.MAX_VALUE, true);
            if (extractable.isEmpty()) {
                continue;
            }
            ItemStack leftover = ItemHandlerHelper.insertItem(into, extractable, true);
            int movable = extractable.getCount() - leftover.getCount();
            if (movable <= 0) {
                continue;
            }
            ItemStack taken = from.extractItem(slot, movable, false);
            if (!taken.isEmpty()) {
                ItemHandlerHelper.insertItem(into, taken, false);
                moved = true;
            }
        }
        return moved;
    }

    /** Up to {@code budget} FE out of one storage into another, one capped call per tick covered. */
    public static long moveEnergy(IEnergyStorage from, IEnergyStorage to, long budget, int ticks) {
        long moved = 0;
        long remaining = budget;
        for (int call = 0; call < Math.max(ticks, 1) && remaining > 0; call++) {
            int offer = from.extractEnergy(offerable(remaining), true);
            int accepted = offer <= 0 ? 0 : to.receiveEnergy(offer, true);
            if (accepted <= 0) {
                break;
            }
            int taken = from.extractEnergy(accepted, false);
            to.receiveEnergy(taken, false);
            moved += taken;
            remaining -= taken;
        }
        return moved;
    }

    /** As much as {@code to} takes of what {@code from} gives, in one go. */
    public static boolean moveFluid(IFluidHandler from, IFluidHandler to) {
        // ponytail: whole tanks a pass; a fluid rate config when someone wants a slow hatch.
        return !FluidUtil.tryFluidTransfer(to, from, Integer.MAX_VALUE, true).isEmpty();
    }

    /** A budget as the capability can express it: one transfer call is int-bound whatever we hold. */
    public static int offerable(long amount) {
        return (int) Math.clamp(amount, 0, Integer.MAX_VALUE);
    }
}
