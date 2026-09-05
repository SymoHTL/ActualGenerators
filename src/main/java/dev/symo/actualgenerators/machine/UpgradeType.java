package dev.symo.actualgenerators.machine;

/**
 * The upgrade types every machine accepts.
 *
 * <ul>
 *   <li>{@link #ENERGY} — raises FE throughput and internal buffer size.</li>
 *   <li>{@link #SPEED} — raises base speed, up to a hard ceiling.</li>
 *   <li>{@link #OVERCLOCK} — lets the machine ramp past that ceiling while it works.</li>
 *   <li>{@link #STACK} — processes more items per operation instead of more often.</li>
 * </ul>
 *
 * <p>Speed and overclock cost energy superlinearly; stack costs it linearly. That makes
 * batching the efficient way to raise throughput and overclocking the expensive one — but
 * batching only pays off when inputs arrive in bulk, so both have a place.
 */
public enum UpgradeType {
    ENERGY,
    SPEED,
    OVERCLOCK,
    STACK;

    private static final UpgradeType[] VALUES = values();

    public static UpgradeType[] all() {
        return VALUES;
    }

    public static UpgradeType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ENERGY;
    }
}
