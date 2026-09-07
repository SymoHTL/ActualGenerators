package dev.symo.actualgenerators.machine.multiblock;

/**
 * What a redstone hatch does: takes a signal in for the controller's redstone mode, or gives one
 * out that says something about the controller. Mekanism's redstone adapter, one mode at a time.
 */
public enum HatchSignal {
    /** In: a signal reaching the hatch counts as a signal at the controller. Gives nothing off. */
    CONTROL,
    /** Out: full strength while the box stands. */
    FORMED,
    /** Out: full strength while the controller is working. */
    WORKING,
    /** Out: how full the controller's FE buffer is, nought to fifteen. */
    ENERGY,
    /** Out: how full the controller's item slots are, nought to fifteen. */
    ITEMS,
    /** Out: the controller's efficiency, nought to fifteen. */
    EFFICIENCY;

    private static final HatchSignal[] VALUES = values();

    public static HatchSignal byOrdinal(int ordinal) {
        return VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)];
    }

    public String key() {
        return "gui.actualgenerators.hatch.mode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
