package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * What one sending pad does on one channel when its wait is over: one send.
 *
 * <p>A send carries the sender's Amount and no more; how often one happens is the sender's Delay.
 * Those two numbers are the whole rate, so there is no rate here and no interval to carry between.
 *
 * <p>The three kinds are deliberately separate methods over plain capabilities rather than one
 * clever generic one: items have slots and stack sizes, fluid has a type that has to match, and
 * energy has neither. The one thing they share is the shape: offer, simulate, move, and put back
 * anything the destination changed its mind about, so nothing is ever created or destroyed by a
 * transfer that half succeeded.
 *
 * <p>Every kind is paid for out of the network's injectors, and each budget is clamped to what the
 * network can actually afford before a single thing moves.
 *
 * <p>Receivers come as the sorted list the network keeps for the sender, and each one's handler is
 * looked up as the send reaches it: a send that is spent after three of three hundred receivers
 * never asks the other two hundred and ninety-seven for anything.
 */
public final class LinkTransfer {
    private LinkTransfer() {
    }

    /** Runs one kind the channel carries, from this sender to these receivers. */
    public static boolean run(TransferKind kind,
                              int channel,
                              Distribution distribution,
                              PortFace sender,
                              List<PortFace> receivers,
                              LinkNetworkManager.PowerBank power) {
        PortChannel link = sender.link(channel, kind);
        return switch (kind) {
            case ITEM -> {
                IItemHandler items = sender.targetItems();
                if (items == null) {
                    yield false;
                }
                int fee = ServerConfig.valueOr(ServerConfig.LINK_FE_PER_ITEM, 10);
                int budget = clampToInt(Math.min(link.amount(), power.affords(fee)));
                int carried = moveItems(link, distribution, items, receivers, channel, budget);
                power.spend((long) carried * fee);
                yield carried > 0;
            }
            case FLUID -> {
                IFluidHandler fluids = sender.targetFluids();
                if (fluids == null) {
                    yield false;
                }
                // Priced by the bucket and charged by the millibucket, so a trickle is not free.
                int fee = ServerConfig.valueOr(ServerConfig.LINK_FE_PER_BUCKET, 10);
                long affordable = fee <= 0 ? Long.MAX_VALUE : power.available() * 1000L / fee;
                int budget = clampToInt(Math.min(link.amount(), affordable));
                int carried = moveFluid(link, distribution, fluids, receivers, channel, budget);
                power.spend((long) carried * fee / 1000);
                yield carried > 0;
            }
            // Redstone is not moved; the network reads and writes it itself, before the fee.
            case REDSTONE -> false;
            case ENERGY -> {
                IEnergyStorage energy = sender.targetEnergy();
                if (energy == null) {
                    yield false;
                }
                int permille = ServerConfig.valueOr(ServerConfig.LINK_FE_PERMILLE_OF_ENERGY, 20);
                long rated = link.amount();
                // Moving FE costs a cut of itself, and that cut comes out of the injectors too.
                long budget = permille <= 0 ? rated : Math.min(rated, power.available() * 1000L / permille);
                long carried = moveEnergy(distribution, energy, receivers, channel, budget, link.delayTicks());
                power.spend(carried * permille / 1000);
                yield carried > 0;
            }
        };
    }

    private static int clampToInt(long value) {
        return (int) Math.clamp(value, 0, Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------ items

    /** @return how many items actually arrived somewhere. */
    public static int moveItems(PortChannel sender,
                                Distribution distribution,
                                IItemHandler source,
                                List<PortFace> receivers,
                                int channel,
                                int budget) {
        budget = Math.min(budget, sender.amount());
        if (budget <= 0 || receivers.isEmpty()) {
            return 0;
        }
        int sinks = itemSinks(distribution, receivers);
        if (sinks == 0) {
            return 0;
        }
        int carried = 0;

        for (int slot = 0; slot < source.getSlots() && budget > 0; slot++) {
            ItemStack probe = source.extractItem(slot, budget, true);
            if (probe.isEmpty() || !sender.allowsItem(probe, false)) {
                continue;
            }
            // A sending pad that keeps a stock leaves that much of this item behind, counted
            // across the whole block rather than this one slot.
            int surplus = sender.surplusCount(probe, source);
            if (surplus <= 0) {
                continue;
            }
            // Out of what this slot actually holds, not out of the budget: a send with room for
            // 160 items and a chest holding 64 would otherwise hand all 64 to the first receiver
            // and call that an even split.
            int share = shareOf(distribution, Math.min(budget, probe.getCount()), sinks);

            for (PortFace receiver : receivers) {
                if (budget <= 0 || surplus <= 0) {
                    break;
                }
                IItemHandler handler = receiver.targetItems();
                if (handler == null) {
                    continue;
                }
                PortChannel link = receiver.link(channel, TransferKind.ITEM);
                if (!link.allowsItem(probe, true)) {
                    continue;
                }
                int offered = Math.min(Math.min(budget, share), probe.getCount());
                offered = Math.min(offered, surplus);
                offered = Math.min(offered, link.acceptableCount(probe, handler));
                // The receiver's own Amount caps what it takes in one send, as the sender's caps what goes out.
                offered = Math.min(offered, link.amount());
                if (offered <= 0) {
                    continue;
                }

                ItemStack offer = probe.copyWithCount(offered);
                int fits = offered - insert(link, handler, offer, true).getCount();
                if (fits <= 0) {
                    continue;
                }
                ItemStack taken = source.extractItem(slot, fits, false);
                if (taken.isEmpty()) {
                    continue;
                }
                ItemStack refused = insert(link, handler, taken, false);
                if (!refused.isEmpty()) {
                    // It promised and then would not take it. Back where it came from, not gone.
                    ItemHandlerHelper.insertItem(source, refused, false);
                }

                int actual = taken.getCount() - refused.getCount();
                if (actual > 0) {
                    budget -= actual;
                    surplus -= actual;
                    carried += actual;
                }
                if (budget <= 0) {
                    break;
                }
                // The probe is a copy of the slot (the contract of a simulated extract), so it can
                // be brought up to date by hand unless something went back into the source.
                if (refused.isEmpty()) {
                    probe.shrink(actual);
                } else {
                    probe = source.extractItem(slot, budget, true);
                }
                if (probe.isEmpty()) {
                    break;
                }
            }
        }
        return carried;
    }

    /**
     * {@link ItemHandlerHelper#insertItem} that starts at the slot that took the last item into
     * this block and remembers the one that takes this, rather than scanning a chest's thirty
     * full slots from the top for every item.
     */
    static ItemStack insert(PortChannel receiver, IItemHandler handler, ItemStack stack, boolean simulate) {
        int slots = handler.getSlots();
        if (slots <= 0 || stack.isEmpty()) {
            return stack;
        }
        int hint = receiver.slotHint();
        int start = hint >= 0 && hint < slots ? hint : 0;
        for (int step = 0; step < slots; step++) {
            int slot = start + step;
            if (slot >= slots) {
                slot -= slots;
            }
            int before = stack.getCount();
            stack = handler.insertItem(slot, stack, simulate);
            if (stack.getCount() < before) {
                receiver.setSlotHint(slot);
            }
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack;
    }

    // ------------------------------------------------------------------ fluid

    /** @return how many millibuckets actually arrived somewhere. */
    public static int moveFluid(PortChannel sender,
                                Distribution distribution,
                                IFluidHandler source,
                                List<PortFace> receivers,
                                int channel,
                                int budget) {
        budget = Math.min(budget, sender.amount());
        if (budget <= 0 || receivers.isEmpty()) {
            return 0;
        }
        int sinks = fluidSinks(distribution, receivers);
        if (sinks == 0) {
            return 0;
        }
        FluidStack available = source.drain(budget, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty() || !sender.allowsFluid(available, false)) {
            return 0;
        }
        int surplus = sender.surplusAmount(available, source);
        if (surplus <= 0) {
            return 0;
        }
        budget = Math.min(budget, surplus);
        int share = shareOf(distribution, Math.min(budget, available.getAmount()), sinks);
        int carried = 0;

        for (PortFace receiver : receivers) {
            if (budget <= 0) {
                break;
            }
            IFluidHandler handler = receiver.targetFluids();
            if (handler == null) {
                continue;
            }
            PortChannel link = receiver.link(channel, TransferKind.FLUID);
            FluidStack drainable = source.drain(Math.min(budget, share), IFluidHandler.FluidAction.SIMULATE);
            if (drainable.isEmpty()) {
                // Nothing left to send at all; the next sink would find the same.
                break;
            }
            if (!link.allowsFluid(drainable, true)) {
                continue;
            }
            int room = Math.min(link.acceptableAmount(drainable, handler), link.amount());
            if (room <= 0) {
                continue;
            }
            FluidStack offer = drainable.getAmount() > room ? drainable.copyWithAmount(room) : drainable;
            int fits = handler.fill(offer, IFluidHandler.FluidAction.SIMULATE);
            if (fits <= 0) {
                continue;
            }

            // By stack rather than by amount: draining "200" from a tank with two fluids in it
            // could hand back the wrong one.
            FluidStack taken = source.drain(offer.copyWithAmount(fits), IFluidHandler.FluidAction.EXECUTE);
            if (taken.isEmpty()) {
                continue;
            }
            int accepted = handler.fill(taken, IFluidHandler.FluidAction.EXECUTE);
            if (accepted < taken.getAmount()) {
                source.fill(taken.copyWithAmount(taken.getAmount() - accepted), IFluidHandler.FluidAction.EXECUTE);
            }
            if (accepted > 0) {
                budget -= accepted;
                carried += accepted;
            }
        }
        return carried;
    }

    // ------------------------------------------------------------------ energy

    /**
     * A send covers {@code calls} ticks, the sender's Delay, and makes that many capability calls
     * per sink: every {@code extractEnergy} and {@code receiveEnergy} call is capped at its
     * owner's per-tick rate, so one call for ten ticks' worth would move a tenth of the send.
     *
     * @return how much FE actually arrived somewhere.
     */
    public static long moveEnergy(Distribution distribution,
                                  IEnergyStorage source,
                                  List<PortFace> receivers,
                                  int channel,
                                  long budget,
                                  int calls) {
        if (budget <= 0 || receivers.isEmpty() || !source.canExtract()) {
            return 0;
        }
        int sinks = energySinks(distribution, receivers);
        if (sinks == 0) {
            return 0;
        }
        calls = Math.max(calls, 1);
        int available = source.extractEnergy(clampToInt(budget), true);
        if (available <= 0) {
            return 0;
        }
        // What one call can draw is a tick's worth from a rate-capped source, so the send's worth
        // is that many times over; the budget bounds it for a source that is only a store.
        long share = distribution.splits()
                ? Math.max(1, ceilDiv(Math.min(budget, (long) available * calls), sinks))
                : budget;
        long carried = 0;

        for (PortFace receiver : receivers) {
            if (budget <= 0) {
                break;
            }
            IEnergyStorage handler = receiver.targetEnergy();
            if (handler == null || !handler.canReceive()) {
                continue;
            }
            long allowance = Math.min(share, receiver.link(channel, TransferKind.ENERGY).amount());
            for (int call = 0; call < calls && budget > 0 && allowance > 0; call++) {
                int drawable = source.extractEnergy(clampToInt(Math.min(budget, allowance)), true);
                if (drawable <= 0) {
                    return carried;
                }
                int fits = handler.receiveEnergy(drawable, true);
                if (fits <= 0) {
                    break;
                }
                int drawn = source.extractEnergy(fits, false);
                if (drawn <= 0) {
                    break;
                }
                int accepted = handler.receiveEnergy(drawn, false);
                if (accepted < drawn) {
                    source.receiveEnergy(drawn - accepted, false);
                }
                if (accepted <= 0) {
                    break;
                }
                budget -= accepted;
                allowance -= accepted;
                carried += accepted;
            }
        }
        return carried;
    }

    // ------------------------------------------------------------------ pushed into a pad

    /**
     * What a block that pushes INTO a pad gets: its stack handed straight on to the channel's
     * receivers, under the pusher's limits and nobody else's. No Amount, no Delay, no keep-behind
     * and no fee; the filters at both ends, the order and the spread and the receivers' keep all
     * still apply. What a receiver turns down is not re-offered: it goes back to the pusher, which
     * is the one with a clock.
     *
     * @return what nobody would take
     */
    public static ItemStack pushItems(PortChannel sender,
                                      Distribution distribution,
                                      ItemStack stack,
                                      List<PortFace> receivers,
                                      int channel,
                                      boolean simulate) {
        if (stack.isEmpty() || receivers.isEmpty() || !sender.allowsItem(stack, false)) {
            return stack;
        }
        int sinks = itemSinks(distribution, receivers);
        if (sinks == 0) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        int share = shareOf(distribution, remaining.getCount(), sinks);
        for (PortFace receiver : receivers) {
            if (remaining.isEmpty()) {
                break;
            }
            IItemHandler handler = receiver.targetItems();
            if (handler == null) {
                continue;
            }
            PortChannel link = receiver.link(channel, TransferKind.ITEM);
            if (!link.allowsItem(remaining, true)) {
                continue;
            }
            int offered = Math.min(Math.min(share, remaining.getCount()), link.acceptableCount(remaining, handler));
            if (offered <= 0) {
                continue;
            }
            ItemStack refused = insert(link, handler, remaining.copyWithCount(offered), simulate);
            remaining.shrink(offered - refused.getCount());
        }
        return remaining;
    }

    /** @return how many millibuckets of the pushed fluid went somewhere */
    public static int pushFluid(PortChannel sender,
                                Distribution distribution,
                                FluidStack fluid,
                                List<PortFace> receivers,
                                int channel,
                                IFluidHandler.FluidAction action) {
        if (fluid.isEmpty() || receivers.isEmpty() || !sender.allowsFluid(fluid, false)) {
            return 0;
        }
        int sinks = fluidSinks(distribution, receivers);
        if (sinks == 0) {
            return 0;
        }
        int remaining = fluid.getAmount();
        int share = shareOf(distribution, remaining, sinks);
        int carried = 0;
        for (PortFace receiver : receivers) {
            if (remaining <= 0) {
                break;
            }
            IFluidHandler handler = receiver.targetFluids();
            if (handler == null) {
                continue;
            }
            PortChannel link = receiver.link(channel, TransferKind.FLUID);
            if (!link.allowsFluid(fluid, true)) {
                continue;
            }
            int offered = Math.min(Math.min(share, remaining), link.acceptableAmount(fluid, handler));
            if (offered <= 0) {
                continue;
            }
            int accepted = handler.fill(fluid.copyWithAmount(offered), action);
            remaining -= accepted;
            carried += accepted;
        }
        return carried;
    }

    /** @return how much of the pushed FE went somewhere */
    public static int pushEnergy(Distribution distribution,
                                 int amount,
                                 List<PortFace> receivers,
                                 boolean simulate) {
        if (amount <= 0 || receivers.isEmpty()) {
            return 0;
        }
        int sinks = energySinks(distribution, receivers);
        if (sinks == 0) {
            return 0;
        }
        int remaining = amount;
        int share = shareOf(distribution, amount, sinks);
        int carried = 0;
        for (PortFace receiver : receivers) {
            if (remaining <= 0) {
                break;
            }
            IEnergyStorage handler = receiver.targetEnergy();
            if (handler == null || !handler.canReceive()) {
                continue;
            }
            int accepted = handler.receiveEnergy(Math.min(share, remaining), simulate);
            remaining -= accepted;
            carried += accepted;
        }
        return carried;
    }

    // ------------------------------------------------------------------ shared

    /**
     * How many receivers have a block to write into, which is what even split shares between.
     * The other modes hand the lot to whoever comes first, so they need not count; the lookups
     * are cached per pad and cost nothing to repeat.
     */
    private static int itemSinks(Distribution distribution, List<PortFace> receivers) {
        if (!distribution.splits()) {
            return receivers.size();
        }
        int found = 0;
        for (PortFace receiver : receivers) {
            if (receiver.targetItems() != null) {
                found++;
            }
        }
        return found;
    }

    private static int fluidSinks(Distribution distribution, List<PortFace> receivers) {
        if (!distribution.splits()) {
            return receivers.size();
        }
        int found = 0;
        for (PortFace receiver : receivers) {
            if (receiver.targetFluids() != null) {
                found++;
            }
        }
        return found;
    }

    private static int energySinks(Distribution distribution, List<PortFace> receivers) {
        if (!distribution.splits()) {
            return receivers.size();
        }
        int found = 0;
        for (PortFace receiver : receivers) {
            if (receiver.targetEnergy() != null) {
                found++;
            }
        }
        return found;
    }

    /**
     * What any one receiver may be offered out of this send.
     *
     * <p>Even split shares what is on offer evenly, so a sender with three chests fills all three
     * rather than the first; every other mode hands the lot to whoever comes first and lets the
     * order do the work. What is left over when a receiver turns its share down waits for the next
     * send, which is along when the sender's delay is up: spreading is the whole reason to pick
     * this mode.
     */
    private static int shareOf(Distribution distribution, int budget, int sinks) {
        return distribution.splits()
                ? Math.max(1, (budget + sinks - 1) / sinks)
                : budget;
    }

    private static long ceilDiv(long dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
