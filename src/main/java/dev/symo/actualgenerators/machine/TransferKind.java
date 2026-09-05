package dev.symo.actualgenerators.machine;

/**
 * The kinds of thing that cross a face: the three a machine is configured for, and a redstone
 * level, which only a Logic Port carries.
 *
 * <p>Every machine carries a configuration for all three material kinds even if it has no
 * inventory or tanks — that keeps configuration copy/paste between different machines lossless.
 * Machine code walks {@link #material()} and never sees redstone; the port's channels walk
 * {@link #all()}.
 */
public enum TransferKind {
    ITEM,
    FLUID,
    ENERGY,
    /** A redstone level, 0 to 15, read off one block and put into another. Nothing is moved. */
    REDSTONE;

    private static final TransferKind[] VALUES = values();
    private static final TransferKind[] MATERIAL = {ITEM, FLUID, ENERGY};

    public static TransferKind[] all() {
        return VALUES;
    }

    /** The kinds a machine face is configured for: the ones that are actually moved. */
    public static TransferKind[] material() {
        return MATERIAL;
    }

    public boolean isMaterial() {
        return this != REDSTONE;
    }

    public static TransferKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ITEM;
    }
}
