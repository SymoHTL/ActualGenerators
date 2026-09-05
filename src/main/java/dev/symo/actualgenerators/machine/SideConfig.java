package dev.symo.actualgenerators.machine;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-face input/output configuration for every {@link TransferKind}, stored relative to the
 * machine's facing, plus whether the machine moves each kind on its own initiative.
 *
 * <p>Those are two different questions, and a face mode alone cannot answer both. A face says what
 * is <em>allowed</em> through it — an output face lets anything pull from the machine. The auto
 * flags say whether the machine goes looking: whether it pushes into whatever happens to be next
 * to that face, and whether it reaches into a neighbour and pulls. Turning auto-output off leaves
 * the face open for a pipe to extract from while stopping the machine shoving its results into the
 * chest someone put there.
 *
 * <p>Every machine carries a full configuration even for resource kinds it does not handle,
 * which keeps copy/paste between different machines lossless.
 */
public final class SideConfig {
    private static final String KEY_MODES = "Modes";
    private static final String KEY_AUTO = "Auto";

    private static final int SIDES = RelativeSide.all().length;
    private static final int KINDS = TransferKind.material().length;
    private static final int SIZE = SIDES * KINDS;

    /** Two bits per kind: pull and push. */
    private static final int ALL_AUTO = (1 << (KINDS * 2)) - 1;

    /**
     * The default: nothing automatic, in either direction.
     *
     * <p>A freshly placed machine does what it is told and no more. Its faces are open, so pipes,
     * ports and neighbours can move things through them from the first tick — but the machine
     * itself does not reach into a chest that happens to be beside it, and does not push its
     * results into whatever it was set down next to. Both are one click each in the side panel.
     */
    private static final int DEFAULT_AUTO = 0;

    private final byte[] modes = new byte[SIZE];
    private int auto = DEFAULT_AUTO;

    private SideConfig() {
    }

    /** A configuration with every face of every kind disabled. */
    public static SideConfig empty() {
        return new SideConfig();
    }

    /** A configuration with the given mode applied to every face of each kind. */
    public static SideConfig of(IoMode itemMode, IoMode fluidMode, IoMode energyMode) {
        SideConfig config = new SideConfig();
        config.setAll(TransferKind.ITEM, itemMode);
        config.setAll(TransferKind.FLUID, fluidMode);
        config.setAll(TransferKind.ENERGY, energyMode);
        return config;
    }

    private static int index(TransferKind kind, RelativeSide side) {
        return kind.ordinal() * SIDES + side.ordinal();
    }

    public IoMode get(TransferKind kind, RelativeSide side) {
        return IoMode.byOrdinal(modes[index(kind, side)]);
    }

    /** Resolves the mode for a world direction, given the machine's facing. */
    public IoMode get(TransferKind kind, Direction facing, Direction side) {
        return get(kind, RelativeSide.fromDirection(facing, side));
    }

    public void set(TransferKind kind, RelativeSide side, IoMode mode) {
        modes[index(kind, side)] = (byte) mode.ordinal();
    }

    public void setAll(TransferKind kind, IoMode mode) {
        for (RelativeSide side : RelativeSide.all()) {
            set(kind, side, mode);
        }
    }

    public IoMode cycle(TransferKind kind, RelativeSide side) {
        IoMode next = get(kind, side).next();
        set(kind, side, next);
        return next;
    }

    // ------------------------------------------------------------------ automatic transfer

    private static int autoBit(TransferKind kind, boolean push) {
        return 1 << (kind.ordinal() * 2 + (push ? 1 : 0));
    }

    /** Whether the machine moves this kind itself in the given direction. */
    public boolean auto(TransferKind kind, boolean push) {
        return (auto & autoBit(kind, push)) != 0;
    }

    /** Whether the machine pushes this kind into neighbours on its output faces. */
    public boolean autoPush(TransferKind kind) {
        return auto(kind, true);
    }

    /** Whether the machine pulls this kind out of neighbours on its input faces. */
    public boolean autoPull(TransferKind kind) {
        return auto(kind, false);
    }

    /** True if either direction is automatic, so a machine can skip the whole pass when neither is. */
    public boolean autoAny(TransferKind kind) {
        return autoPush(kind) || autoPull(kind);
    }

    public void setAuto(TransferKind kind, boolean push, boolean enabled) {
        int bit = autoBit(kind, push);
        auto = enabled ? auto | bit : auto & ~bit;
    }

    public boolean toggleAuto(TransferKind kind, boolean push) {
        boolean next = !auto(kind, push);
        setAuto(kind, push, next);
        return next;
    }

    /** The flags as one number, for the menu sync and for a config card. */
    public int autoMask() {
        return auto;
    }

    public void setAutoMask(int mask) {
        auto = mask & ALL_AUTO;
    }

    /** True if any face is configured for this kind, so callers can skip work entirely. */
    public boolean hasAny(TransferKind kind) {
        for (RelativeSide side : RelativeSide.all()) {
            if (get(kind, side).isActive()) {
                return true;
            }
        }
        return false;
    }

    public void copyFrom(SideConfig other) {
        System.arraycopy(other.modes, 0, modes, 0, SIZE);
        auto = other.auto;
    }

    public SideConfig copy() {
        SideConfig copy = new SideConfig();
        copy.copyFrom(this);
        return copy;
    }

    /** The raw mode ordinals with the auto flags on the end, for a config card's snapshot. */
    public List<Integer> toOrdinals() {
        List<Integer> ordinals = new ArrayList<>(SIZE + 1);
        for (byte mode : modes) {
            ordinals.add((int) mode);
        }
        ordinals.add(auto);
        return ordinals;
    }

    /** Restores a snapshot. Snapshots of the wrong length are ignored rather than half-applied. */
    public void loadOrdinals(List<Integer> ordinals) {
        if (ordinals.size() != SIZE + 1) {
            return;
        }
        for (int i = 0; i < SIZE; i++) {
            modes[i] = (byte) IoMode.byOrdinal(ordinals.get(i)).ordinal();
        }
        setAutoMask(ordinals.get(SIZE));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray(KEY_MODES, modes.clone());
        tag.putInt(KEY_AUTO, auto);
        return tag;
    }

    public void load(CompoundTag tag) {
        byte[] stored = tag.getByteArray(KEY_MODES);
        if (stored.length == SIZE) {
            System.arraycopy(stored, 0, modes, 0, SIZE);
        }
        setAutoMask(tag.contains(KEY_AUTO) ? tag.getInt(KEY_AUTO) : DEFAULT_AUTO);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SideConfig other && auto == other.auto && Arrays.equals(modes, other.modes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(modes) * 31 + auto;
    }
}
