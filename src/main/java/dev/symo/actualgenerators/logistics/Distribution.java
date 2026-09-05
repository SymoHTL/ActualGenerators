package dev.symo.actualgenerators.logistics;

import java.util.Locale;

/**
 * How a sending port spreads what it has across the ports willing to take it.
 *
 * <p>All of them are ordered by priority first; that is not a distribution mode, it is the rule.
 * This only decides what happens between ports of <em>equal</em> priority.
 *
 * <p>Three of the four hand a whole send to ONE port and cost one insert; even split shares every
 * send out across all of them and costs one insert per port, which is the price of three chests
 * filling together rather than in turn. The order is the saved order: even split was the first
 * mode there was and keeps ordinal zero for worlds that saved it.
 */
public enum Distribution {
    /** Everyone gets a share of every send, and who is served first moves along by one each send. */
    EVEN_SPLIT("ES"),
    /** The closest port is filled before the next one is offered anything. The default. */
    NEAREST_FIRST("NF"),
    /** Each send goes whole to the next port in line, the line moving along by one each send. */
    ROUND_ROBIN("RR"),
    /** Each send goes whole to a port picked at random. */
    RANDOM("RN");

    private static final Distribution[] VALUES = values();

    private final String shortLabel;

    Distribution(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    /** Two letters for the button. */
    public String shortLabel() {
        return shortLabel;
    }

    /** Whether a send is shared out across every receiver rather than handed to one. */
    public boolean splits() {
        return this == EVEN_SPLIT;
    }

    /** Whether the front of the queue moves between sends. */
    public boolean rotates() {
        return this != NEAREST_FIRST;
    }

    public Distribution next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static Distribution byOrdinal(int ordinal) {
        return VALUES[Math.floorMod(ordinal, VALUES.length)];
    }

    public String translationKey() {
        return "gui.actualgenerators.link.distribution." + name().toLowerCase(Locale.ROOT);
    }
}
