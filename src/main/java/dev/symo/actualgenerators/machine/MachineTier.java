package dev.symo.actualgenerators.machine;

import dev.symo.actualgenerators.config.ServerConfig;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The grade of a block itself: iron, gold, diamond, netherite, the ladder every pack already
 * teaches. One item goes into the block and the block gets better at what it does, Mekanism's way,
 * rather than a row of upgrades that each buy one thing.
 *
 * <p>A tier is not an upgrade and does not behave like one. Upgrades are traded against energy:
 * speed costs power superlinearly, so a fast machine is an expensive machine. A tier raises the
 * base stats at the same price per operation, which is what makes the ladder worth climbing at all.
 *
 * <p>The same ladder serves the machines and the Logic Port pads, because it is the same item and a
 * player should not have to learn two of them. On a machine it multiplies speed and batch size; on
 * a pad it multiplies how much one send carries and shortens the wait between sends.
 *
 * <p>Every number is config, per tier, as a list read by ordinal.
 */
public enum MachineTier {
    NONE,
    IRON,
    GOLD,
    DIAMOND,
    NETHERITE;

    /** What each list means before the config has loaded, which is datagen and early startup. */
    private static final double[] SPEED = {1.0, 1.5, 2.0, 3.0, 4.0};
    private static final int[] BATCH = {1, 1, 2, 2, 3};
    private static final int[] LINK_AMOUNT = {1, 2, 4, 8, 16};
    private static final int[] LINK_DELAY = {10, 8, 5, 2, 1};

    public static MachineTier byOrdinal(int ordinal) {
        MachineTier[] tiers = values();
        return tiers[Math.clamp(ordinal, 0, tiers.length - 1)];
    }

    public boolean isAbove(MachineTier other) {
        return ordinal() > other.ordinal();
    }

    /** "Iron", "Netherite", or "No tier". */
    public Component displayName() {
        return Component.translatable("gui.actualgenerators.tier." + name().toLowerCase(Locale.ROOT));
    }

    /** How much faster a machine of this grade works, at the same energy per operation. */
    public double speedMultiplier() {
        return ServerConfig.tierValue(ServerConfig.MACHINE_TIER_SPEED_MULTIPLIER, ordinal(), SPEED);
    }

    /**
     * How much more a machine of this grade does in one operation.
     *
     * <p>Batch, never a second processing line: one operation a tick stays one operation a tick,
     * so a big machine costs the server exactly what a small one does.
     */
    public int batchMultiplier() {
        return ServerConfig.tierValue(ServerConfig.MACHINE_TIER_BATCH_MULTIPLIER, ordinal(), BATCH);
    }

    /** How much more one send of a pad of this grade may carry. */
    public int linkAmountMultiplier() {
        return ServerConfig.tierValue(ServerConfig.LINK_TIER_AMOUNT_MULTIPLIER, ordinal(), LINK_AMOUNT);
    }

    /** The shortest wait between sends a pad of this grade allows, in ticks. */
    public int linkDelayTicks() {
        return Math.max(1, ServerConfig.tierValue(ServerConfig.LINK_TIER_DELAY_TICKS, ordinal(), LINK_DELAY));
    }
}
