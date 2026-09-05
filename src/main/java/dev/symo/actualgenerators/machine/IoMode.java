package dev.symo.actualgenerators.machine;

/**
 * What a machine face does with a given kind of resource.
 */
public enum IoMode {
    DISABLED,
    INPUT,
    OUTPUT,
    BOTH;

    private static final IoMode[] VALUES = values();

    public boolean canInput() {
        return this == INPUT || this == BOTH;
    }

    public boolean canOutput() {
        return this == OUTPUT || this == BOTH;
    }

    public boolean isActive() {
        return this != DISABLED;
    }

    /** Cycles through the modes, for click-to-configure UI. */
    public IoMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static IoMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DISABLED;
    }
}
