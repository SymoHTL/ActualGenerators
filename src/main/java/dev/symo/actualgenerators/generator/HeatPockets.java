package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.config.ServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The heat under every chunk: what a Geothermal Fissure Tap draws on.
 *
 * <p>A chunk either has a pocket or it does not, and how much is in it is rolled once from the
 * world seed and the chunk position the first time anything asks, so every tap in a chunk shares
 * one pocket and a world can be rebuilt from its seed. A pocket refills at a trickle, worked out
 * lazily from how long it has been since it was last touched — nothing ticks, and a chunk nobody
 * taps costs nothing. One record per level, saved with it.
 */
public class HeatPockets extends SavedData {
    private static final String NAME = ActualGenerators.MODID + "_heat_pockets";
    private static final String KEY_POCKETS = "Pockets";
    private static final String KEY_CHUNK = "Chunk";
    private static final String KEY_REMAINING = "Remaining";
    private static final String KEY_CAPACITY = "Capacity";
    private static final String KEY_TOUCHED = "Touched";

    private static final class Pocket {
        long remaining;
        long capacity;
        long touched;

        Pocket(long remaining, long capacity, long touched) {
            this.remaining = remaining;
            this.capacity = capacity;
            this.touched = touched;
        }
    }

    private final Map<Long, Pocket> pockets = new HashMap<>();

    public static HeatPockets get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(HeatPockets::new, HeatPockets::load), NAME);
    }

    /**
     * Takes up to {@code wanted} FE out of the chunk's pocket and says how much came out. Regrowth
     * since the last touch is added first.
     */
    public long draw(ServerLevel level, ChunkPos chunk, long wanted, long now) {
        Pocket pocket = pocket(level, chunk);
        regrow(pocket, now);
        long taken = Math.min(wanted, pocket.remaining);
        pocket.remaining -= taken;
        if (taken > 0) {
            setDirty();
        }
        return taken;
    }

    /** FE left in the chunk's pocket right now, regrowth included. */
    public long remaining(ServerLevel level, ChunkPos chunk, long now) {
        Pocket pocket = pocket(level, chunk);
        regrow(pocket, now);
        return pocket.remaining;
    }

    /** What the chunk's pocket holds when full; zero for a chunk that has none. */
    public long capacity(ServerLevel level, ChunkPos chunk) {
        return pocket(level, chunk).capacity;
    }

    /** Sets a chunk's pocket outright: for tests, and for an operator who wants one somewhere. */
    public void set(ChunkPos chunk, long remaining, long capacity, long now) {
        pockets.put(chunk.toLong(), new Pocket(remaining, capacity, now));
        setDirty();
    }

    private Pocket pocket(ServerLevel level, ChunkPos chunk) {
        return pockets.computeIfAbsent(chunk.toLong(), key -> {
            Pocket rolled = roll(level.getSeed(), chunk, level.getGameTime());
            setDirty();
            return rolled;
        });
    }

    /** The one roll a chunk ever gets: whether there is a pocket, and how big. */
    private static Pocket roll(long seed, ChunkPos chunk, long now) {
        RandomSource random = RandomSource.create(seed ^ (chunk.toLong() * 0x9E3779B97F4A7C15L));
        int chance = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_POCKET_CHANCE, 60);
        if (random.nextInt(100) >= chance) {
            return new Pocket(0, 0, now);
        }
        long min = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_POCKET_MIN, 500_000_000L);
        long max = Math.max(min, ServerConfig.valueOr(ServerConfig.GEOTHERMAL_POCKET_MAX, 2_000_000_000L));
        long capacity = min + (long) (random.nextDouble() * (max - min));
        return new Pocket(capacity, capacity, now);
    }

    private void regrow(Pocket pocket, long now) {
        long elapsed = now - pocket.touched;
        pocket.touched = now;
        if (elapsed <= 0 || pocket.capacity <= 0 || pocket.remaining >= pocket.capacity) {
            return;
        }
        long regen = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_POCKET_REGEN, 50);
        pocket.remaining = Math.min(pocket.capacity, pocket.remaining + regen * elapsed);
        setDirty();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Pocket> entry : pockets.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong(KEY_CHUNK, entry.getKey());
            one.putLong(KEY_REMAINING, entry.getValue().remaining);
            one.putLong(KEY_CAPACITY, entry.getValue().capacity);
            one.putLong(KEY_TOUCHED, entry.getValue().touched);
            list.add(one);
        }
        tag.put(KEY_POCKETS, list);
        return tag;
    }

    private static HeatPockets load(CompoundTag tag, HolderLookup.Provider registries) {
        HeatPockets pockets = new HeatPockets();
        for (Tag entry : tag.getList(KEY_POCKETS, Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) entry;
            pockets.pockets.put(one.getLong(KEY_CHUNK),
                    new Pocket(one.getLong(KEY_REMAINING), one.getLong(KEY_CAPACITY), one.getLong(KEY_TOUCHED)));
        }
        return pockets;
    }
}
