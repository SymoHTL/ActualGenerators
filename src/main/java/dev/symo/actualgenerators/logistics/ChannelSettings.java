package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.machine.TransferKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * What a channel is, network-wide: the kinds of thing it carries and how it spreads each of them.
 *
 * <p>XNet's shape, widened: a channel carries any set of items, fluids and energy, and every pad
 * on the network sees the same set for the same colour, so "lime is lava and power" means the
 * same thing at both ends. A channel with no kind yet carries nothing, and says so. Spread is per
 * kind, because splitting power evenly and pouring items into the nearest chest are both things
 * one colour may be asked to do.
 */
public final class ChannelSettings {
    private static final String KEY_KINDS = "Kinds";
    /** The shape written when a channel carried one kind: its ordinal, or -1 for none. */
    private static final String KEY_LEGACY_KIND = "Kind";
    private static final String KEY_DISTRIBUTIONS = "Distributions";
    /** The shape written when spread was one setting for the whole channel. */
    private static final String KEY_LEGACY_DISTRIBUTION = "Distribution";

    /** Every kind at once. */
    public static final int ALL_KINDS = (1 << TransferKind.all().length) - 1;

    /** One bit per kind, by ordinal. */
    private int kinds;
    /** Per kind. Nearest-first by default: it is what a player means by "send it over there". */
    private final Distribution[] distributions = new Distribution[TransferKind.all().length];

    public ChannelSettings() {
        Arrays.fill(distributions, Distribution.NEAREST_FIRST);
    }

    public ChannelSettings(@Nullable TransferKind kind) {
        this();
        if (kind != null) {
            setCarries(kind, true);
        }
    }

    public static int bit(TransferKind kind) {
        return 1 << kind.ordinal();
    }

    /** The kinds this channel carries, one bit per kind by ordinal; zero is nothing. */
    public int kinds() {
        return kinds;
    }

    public boolean carries(TransferKind kind) {
        return (kinds & bit(kind)) != 0;
    }

    public boolean carriesAnything() {
        return kinds != 0;
    }

    public void setKinds(int kinds) {
        this.kinds = kinds & ALL_KINDS;
    }

    public void setCarries(TransferKind kind, boolean on) {
        kinds = on ? kinds | bit(kind) : kinds & ~bit(kind);
    }

    public Distribution distribution(TransferKind kind) {
        return distributions[kind.ordinal()];
    }

    public void setDistribution(TransferKind kind, Distribution distribution) {
        distributions[kind.ordinal()] = distribution;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte(KEY_KINDS, (byte) kinds);
        byte[] spread = new byte[distributions.length];
        for (int kind = 0; kind < spread.length; kind++) {
            spread[kind] = (byte) distributions[kind].ordinal();
        }
        tag.putByteArray(KEY_DISTRIBUTIONS, spread);
        return tag;
    }

    public static ChannelSettings load(CompoundTag tag) {
        ChannelSettings settings = new ChannelSettings();
        if (tag.contains(KEY_KINDS, Tag.TAG_BYTE)) {
            settings.setKinds(tag.getByte(KEY_KINDS));
        } else if (tag.contains(KEY_LEGACY_KIND, Tag.TAG_BYTE) && tag.getByte(KEY_LEGACY_KIND) >= 0) {
            settings.setCarries(TransferKind.byOrdinal(tag.getByte(KEY_LEGACY_KIND)), true);
        }
        byte[] spread = tag.getByteArray(KEY_DISTRIBUTIONS);
        for (TransferKind kind : TransferKind.all()) {
            byte saved = kind.ordinal() < spread.length ? spread[kind.ordinal()] : tag.getByte(KEY_LEGACY_DISTRIBUTION);
            settings.setDistribution(kind, Distribution.byOrdinal(saved));
        }
        return settings;
    }
}
