package dev.symo.actualgenerators.logistics;

import java.util.Locale;

/**
 * What a pad sending redstone reads off the block behind it.
 *
 * <p>Three readings, each the thing a vanilla component would give: the signal the block gives
 * off (a lamp in the pad's place), the block's comparator reading (a comparator behind it), and
 * a count of what the pad's filter lets through against a number the player set (a level
 * emitter). Three states, so the button cycles; left is next and right is back.
 */
public enum SignalSource {
    /** The redstone signal the block gives off toward the pad, as a lamp in the pad's place would see it. */
    SIGNAL("RS"),
    /** The block's comparator reading: how full a chest is, what a jukebox plays. */
    COMPARATOR("CP"),
    /** How much of what the pad's filter lets through the block holds, against the pad's Full-at number. */
    STOCK("ST");

    private static final SignalSource[] VALUES = values();

    private final String shortLabel;

    SignalSource(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    /** Two letters, for a button. */
    public String shortLabel() {
        return shortLabel;
    }

    public SignalSource next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public SignalSource previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public static SignalSource byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SIGNAL;
    }

    public String translationKey() {
        return "gui.actualgenerators.link.source." + name().toLowerCase(Locale.ROOT);
    }
}
