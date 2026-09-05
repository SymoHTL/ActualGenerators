package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.machine.RedstoneMode;
import dev.symo.actualgenerators.machine.TransferKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One pad's part in one kind on one channel: what it does with the items, the fluids or the
 * energy that colour carries.
 *
 * <p>A channel is a network-wide thing with a set of kinds (see {@link ChannelSettings}); this is
 * the pad's own business for one of them: whether it sends into the channel or receives out of it,
 * where it stands in the queue, how much it keeps in the block behind it, how much one send
 * carries, how long it waits between sends and what redstone it listens to. Every one of those is
 * per kind, so a pad can pour energy on white while it trickles items on the same colour. What it
 * lets through is a Filter item in the pad's slot for this channel and kind, which the pad holds
 * and this reads.
 */
public class PortChannel {
    public static final int MIN_PRIORITY = -64;
    public static final int MAX_PRIORITY = 64;

    /** Counts are items, millibuckets or FE; the ceilings are what the window can still show. */
    public static final int MAX_ITEMS = 9_999;
    public static final int MAX_MILLIBUCKETS = 999_000;
    public static final int MAX_ENERGY = 999_000_000;

    /** The longest a pad may be told to wait between sends: a minute, which is already a choice. */
    public static final int MAX_DELAY_TICKS = 1200;

    private static final String KEY_INDEX = "Channel";
    private static final String KEY_KIND = "Kind";
    private static final String KEY_INSERT = "Insert";
    private static final String KEY_EXTRACT = "Extract";
    private static final String KEY_PUSH = "Push";
    private static final String KEY_PRIORITY = "Priority";
    private static final String KEY_KEEP = "Keep";
    private static final String KEY_AMOUNT = "Amount";
    private static final String KEY_DELAY = "Delay";
    private static final String KEY_REDSTONE = "Redstone";
    private static final String KEY_SOURCE = "Source";
    /** The shape before a link was per kind: one entry per channel with a count per kind. */
    private static final String KEY_LEGACY_KEEPS = "Keeps";
    private static final String KEY_LEGACY_AMOUNTS = "Amounts";

    private final PortFace pad;
    private final int index;
    private final TransferKind kind;

    private boolean insert;
    private boolean extract;
    private boolean push;
    private int priority;
    /** Zero is off. */
    private int keep;
    /** What one send carries. Zero means the pad's tier ceiling, which is what "as much as I can" is. */
    private int amount;
    /** Ticks between sends. Zero means the pad's tier floor, which is as fast as it goes. */
    private int delay;
    private RedstoneMode redstone = RedstoneMode.ALWAYS;
    /** What a redstone sender reads off the block behind the pad. Nothing to the other kinds. */
    private SignalSource source = SignalSource.SIGNAL;

    /** When this pad may send again. Rebuilt from the delay, so not worth saving. */
    private long nextSendAt;

    /** Which receiver goes first next pass, moved along one place every pass. Not worth saving. */
    private int roundRobinCursor;

    /** The receivers this link sends to, sorted; the network drops it when a pad changes. Not saved. */
    private @Nullable List<PortFace> receivers;
    /** The slot of the block behind this pad that took the last item in, tried first for the next. Not saved. */
    private int slotHint;

    PortChannel(PortFace pad, int index, TransferKind kind) {
        this.pad = pad;
        this.index = index;
        this.kind = kind;
    }

    public int index() {
        return index;
    }

    public TransferKind kind() {
        return kind;
    }

    /** What one step of a count means for a kind: an item, a bucket, a thousand FE, an item again for stock. */
    public static int unitFor(@Nullable TransferKind kind) {
        return kind == TransferKind.FLUID || kind == TransferKind.ENERGY ? 1000 : 1;
    }

    public static int ceilingFor(@Nullable TransferKind kind) {
        if (kind == TransferKind.FLUID) {
            return MAX_MILLIBUCKETS;
        }
        return kind == TransferKind.ENERGY ? MAX_ENERGY : MAX_ITEMS;
    }

    // ------------------------------------------------------------------ role

    public boolean insertEnabled() {
        return insert;
    }

    public boolean extractEnabled() {
        return extract;
    }

    /** Whether what the block behind the pad pushes into it leaves on this channel. */
    public boolean pushEnabled() {
        return push;
    }

    /** Whether this pad does anything at all with this kind on the channel. */
    public boolean participates() {
        return insert || extract || push;
    }

    /**
     * The two halves are separate questions on purpose: a face that can only be one or the other
     * is the single most common complaint about wired logistics, and a pad is a face.
     */
    public void toggleInsert() {
        setRole(!insert, extract);
    }

    public void toggleExtract() {
        setRole(insert, !extract);
    }

    /**
     * PU is its own switch, not a side of EX: EX is the timed pull from the block behind the pad,
     * and a hopper there would be pulled from as well as pushing.
     */
    public void togglePush() {
        setPush(!push);
    }

    public void setPush(boolean push) {
        this.push = push;
        pad.changed();
    }

    public void setRole(boolean insert, boolean extract) {
        this.insert = insert;
        this.extract = extract;
        pad.changed();
    }

    /** Back to nothing at all: off the channel, middle of the queue, no numbers, no redstone. */
    public void clear() {
        insert = false;
        extract = false;
        push = false;
        priority = 0;
        keep = 0;
        amount = 0;
        delay = 0;
        redstone = RedstoneMode.ALWAYS;
        source = SignalSource.SIGNAL;
        pad.changed();
    }

    /** {@link #clear()} without a word to the pad: for a pad being rewritten whole, which speaks once at the end. */
    void reset() {
        insert = false;
        extract = false;
        push = false;
        priority = 0;
        keep = 0;
        amount = 0;
        delay = 0;
        redstone = RedstoneMode.ALWAYS;
        source = SignalSource.SIGNAL;
    }

    /** Every number back inside its bounds, as the arrows keep them: what a link loaded off the clipboard needs. */
    void sanitize() {
        priority = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
        keep = Math.clamp(keep, 0, ceilingFor(kind));
        if (amount != 0) {
            int ceiling = pad.amountCeiling(kind);
            int next = Math.clamp(amount, unitFor(kind), ceiling);
            amount = next >= ceiling ? 0 : next;
        }
        if (delay != 0) {
            int floor = pad.delayFloor();
            int next = Math.clamp(delay, floor, MAX_DELAY_TICKS);
            delay = next <= floor ? 0 : next;
        }
    }

    // ------------------------------------------------------------------ numbers

    public int priority() {
        return priority;
    }

    /** Steps, and goes negative: "behind everything else" is a thing a player wants to say. */
    public void nudgePriority(int by) {
        setPriority((long) priority + by);
    }

    public void setPriority(long value) {
        priority = (int) Math.clamp(value, MIN_PRIORITY, MAX_PRIORITY);
        pad.changed();
    }

    /**
     * How much of this kind the pad keeps in the block behind it. Zero is off.
     *
     * <p>Same number, both directions: a receiving pad stops filling its block at this many, and a
     * sending pad leaves this many behind. The first is what makes a catalyst loop work, since a
     * crafter that uses a rune without consuming it needs exactly one rune and never a second. The
     * second is a stock that is never shipped out.
     */
    public int keepAmount() {
        return keep;
    }

    public void nudgeKeep(int by) {
        setKeep((long) keep + by);
    }

    public void setKeep(long value) {
        keep = (int) Math.clamp(value, 0, ceilingFor(kind));
        pad.changed();
    }

    /**
     * What one send carries, sending or receiving. Zero is stored for "as much as this pad can",
     * so a tier upgrade raises it without the player touching it again.
     *
     * <p>This and {@link #delayTicks()} are the whole speed of a pad: amount is the lot, delay is
     * the wait, and the rate a player gets is one divided by the other. There is no third knob and
     * no hidden interval underneath them.
     */
    public int amount() {
        int ceiling = pad.amountCeiling(kind);
        return amount <= 0 ? ceiling : Math.min(amount, ceiling);
    }

    /** Whether the amount is following the pad's tier rather than a number the player set. */
    public boolean amountIsAuto() {
        return amount <= 0;
    }

    public void nudgeAmount(int by) {
        setAmount((long) amount() + by);
    }

    /** At the ceiling it goes back to following the tier, so an upgrade is felt straight away. */
    public void setAmount(long value) {
        int ceiling = pad.amountCeiling(kind);
        int next = (int) Math.clamp(value, unitFor(kind), ceiling);
        amount = next >= ceiling ? 0 : next;
        pad.changed();
    }

    /** Ticks this pad waits between sends. Zero is stored for the pad's tier floor. */
    public int delayTicks() {
        int floor = pad.delayFloor();
        return delay <= 0 ? floor : Math.clamp(delay, floor, MAX_DELAY_TICKS);
    }

    public boolean delayIsAuto() {
        return delay <= 0;
    }

    public void nudgeDelay(int by) {
        setDelay((long) delayTicks() + by);
    }

    public void setDelay(long value) {
        int floor = pad.delayFloor();
        int next = (int) Math.clamp(value, floor, MAX_DELAY_TICKS);
        delay = next <= floor ? 0 : next;
        pad.changed();
    }

    // ------------------------------------------------------------------ redstone

    public RedstoneMode redstoneMode() {
        return redstone;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        redstone = mode;
        pad.changed();
    }

    public void cycleRedstoneMode() {
        setRedstoneMode(redstone.next());
    }

    /** Whether the redstone at the pad's block lets this kind move right now. */
    public boolean canRun() {
        return redstone.canRun(pad.host().isPowered());
    }

    // ------------------------------------------------------------------ what a redstone sender reads

    /** What this link reads off the block behind the pad when it sends redstone. */
    public SignalSource signalSource() {
        return source;
    }

    public void setSignalSource(SignalSource source) {
        this.source = source;
        pad.changed();
    }

    /** Three readings, so the button cycles: left is the next one, right the one before. */
    public void cycleSignalSource(boolean back) {
        setSignalSource(back ? source.previous() : source.next());
    }

    // ------------------------------------------------------------------ when it sends

    /** Whether this pad's wait is over. A pad that has never sent is due at once. */
    public boolean dueAt(long time) {
        return time >= nextSendAt;
    }

    /** When this pad is next due, for the network to sleep until. */
    public long nextSendAt() {
        return nextSendAt;
    }

    /** Records an attempt: the wait starts again whether anything moved or not. */
    public void sentAt(long time) {
        nextSendAt = time + delayTicks();
    }

    // ------------------------------------------------------------------ filter

    /** The Filter item in this channel's slot for this kind on the pad, or nothing. */
    public ItemStack filter() {
        return pad.filter(index, kind);
    }

    /**
     * Whether this pad will handle a stack in the given direction. No filter, or a blank one,
     * means anything; otherwise the receiving list or the sending list decides.
     */
    public boolean allowsItem(ItemStack stack, boolean receiving) {
        return FilterItem.allowsItem(filter(), stack, receiving);
    }

    public boolean allowsFluid(FluidStack fluid, boolean receiving) {
        return FilterItem.allowsFluid(filter(), fluid, receiving);
    }

    // ------------------------------------------------------------------ keep, both ways

    /** How many more of this item a receiving pad may put into its block. */
    public int acceptableCount(ItemStack stack, IItemHandler target) {
        return keep <= 0 ? Integer.MAX_VALUE : Math.max(0, keep - countOf(stack, target));
    }

    /** How many of this item a sending pad may still take out of its block. */
    public int surplusCount(ItemStack stack, IItemHandler source) {
        return keep <= 0 ? Integer.MAX_VALUE : Math.max(0, countOf(stack, source) - keep);
    }

    /** How much more of this fluid a receiving pad may put into its block, in millibuckets. */
    public int acceptableAmount(FluidStack fluid, IFluidHandler target) {
        return keep <= 0 ? Integer.MAX_VALUE : Math.max(0, keep - amountOf(fluid, target));
    }

    /** How much of this fluid a sending pad may still drain out of its block. */
    public int surplusAmount(FluidStack fluid, IFluidHandler source) {
        return keep <= 0 ? Integer.MAX_VALUE : Math.max(0, amountOf(fluid, source) - keep);
    }

    private static int countOf(ItemStack stack, IItemHandler handler) {
        int held = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(inSlot, stack)) {
                held += inSlot.getCount();
            }
        }
        return held;
    }

    private static int amountOf(FluidStack fluid, IFluidHandler handler) {
        int held = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack inTank = handler.getFluidInTank(tank);
            if (FluidStack.isSameFluidSameComponents(inTank, fluid)) {
                held += inTank.getAmount();
            }
        }
        return held;
    }

    // ------------------------------------------------------------------ round robin

    @Nullable List<PortFace> receivers() {
        return receivers;
    }

    void setReceivers(@Nullable List<PortFace> receivers) {
        this.receivers = receivers;
    }

    int slotHint() {
        return slotHint;
    }

    void setSlotHint(int slot) {
        slotHint = slot;
    }

    public int takeRoundRobinCursor(int receivers) {
        int cursor = peekRoundRobinCursor(receivers);
        roundRobinCursor = cursor + 1;
        return cursor;
    }

    /**
     * Where the queue stands without moving it along: for a simulated push, which must land where
     * the real one will. A hopper simulates before it moves, and a cursor that advanced for both
     * put every item on the same receiver.
     */
    public int peekRoundRobinCursor(int receivers) {
        return receivers <= 0 ? 0 : Math.floorMod(roundRobinCursor, receivers);
    }

    /**
     * Where random's queue stands, then rolls where the next send starts. The roll comes after
     * for the same reason the cursor is peeked: the simulated push and the real one must agree.
     */
    public int takeRandomCursor(int receivers, RandomSource random) {
        int cursor = peekRoundRobinCursor(receivers);
        roundRobinCursor = receivers <= 0 ? 0 : random.nextInt(receivers);
        return cursor;
    }

    // ------------------------------------------------------------------ persistence

    /** Whether there is anything here worth writing down. Most of a pad's forty-eight are blank. */
    public boolean isBlank() {
        return !insert && !extract && !push && priority == 0 && keep == 0 && amount == 0 && delay == 0
                && redstone == RedstoneMode.ALWAYS && source == SignalSource.SIGNAL;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte(KEY_INDEX, (byte) index);
        tag.putByte(KEY_KIND, (byte) kind.ordinal());
        tag.putBoolean(KEY_INSERT, insert);
        tag.putBoolean(KEY_EXTRACT, extract);
        tag.putBoolean(KEY_PUSH, push);
        tag.putInt(KEY_PRIORITY, priority);
        tag.putInt(KEY_KEEP, keep);
        tag.putInt(KEY_AMOUNT, amount);
        tag.putInt(KEY_DELAY, delay);
        tag.putByte(KEY_REDSTONE, (byte) redstone.ordinal());
        tag.putByte(KEY_SOURCE, (byte) source.ordinal());
        return tag;
    }

    public static int indexOf(CompoundTag tag) {
        return tag.getByte(KEY_INDEX);
    }

    /** The kind an entry is for, or null for one written when a link was per channel. */
    public static @Nullable TransferKind kindOf(CompoundTag tag) {
        return tag.contains(KEY_KIND, Tag.TAG_BYTE) ? TransferKind.byOrdinal(tag.getByte(KEY_KIND)) : null;
    }

    /** Reads an entry, this kind's count out of a per-channel one, so an old pad keeps its numbers. */
    public void load(CompoundTag tag) {
        insert = tag.getBoolean(KEY_INSERT);
        extract = tag.getBoolean(KEY_EXTRACT);
        push = tag.getBoolean(KEY_PUSH);
        priority = tag.getInt(KEY_PRIORITY);
        keep = tag.contains(KEY_KEEP, Tag.TAG_INT) ? tag.getInt(KEY_KEEP) : legacyCount(tag, KEY_LEGACY_KEEPS);
        amount = tag.contains(KEY_AMOUNT, Tag.TAG_INT) ? tag.getInt(KEY_AMOUNT) : legacyCount(tag, KEY_LEGACY_AMOUNTS);
        delay = tag.getInt(KEY_DELAY);
        redstone = RedstoneMode.byOrdinal(tag.getByte(KEY_REDSTONE));
        source = SignalSource.byOrdinal(tag.getByte(KEY_SOURCE));
    }

    private int legacyCount(CompoundTag tag, String key) {
        int[] saved = tag.getIntArray(key);
        return kind.ordinal() < saved.length ? saved[kind.ordinal()] : 0;
    }
}
