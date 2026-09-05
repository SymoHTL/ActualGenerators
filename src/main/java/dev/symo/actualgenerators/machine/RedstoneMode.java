package dev.symo.actualgenerators.machine;

/**
 * When a machine is allowed to run relative to the redstone signal it receives.
 */
public enum RedstoneMode {
    /** Redstone is ignored entirely. */
    ALWAYS,
    /** Runs only while powered. */
    WITH_SIGNAL,
    /** Runs only while unpowered. */
    WITHOUT_SIGNAL,
    /** Never runs; a hard off switch. */
    NEVER;

    private static final RedstoneMode[] VALUES = values();

    public boolean canRun(boolean powered) {
        return switch (this) {
            case ALWAYS -> true;
            case WITH_SIGNAL -> powered;
            case WITHOUT_SIGNAL -> !powered;
            case NEVER -> false;
        };
    }

    public RedstoneMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static RedstoneMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ALWAYS;
    }
}
