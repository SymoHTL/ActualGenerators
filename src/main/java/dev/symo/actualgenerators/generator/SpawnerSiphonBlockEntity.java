package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.SpawnerSiphonMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Taps a mob spawner and pays out what it would have spawned.
 *
 * <p>Put one against a dungeon spawner and the spawner stops producing mobs and starts producing
 * power instead: the siphon holds its countdown out of reach and banks FE for every spawn it
 * denies. Break the siphon and the spawner picks up where it left off, so this is a reversible
 * repurposing rather than a way to destroy a spawner.
 *
 * <p>Everything about the rate is read from the spawner itself — how many mobs it spawns and how
 * long it waits between attempts — rather than from vanilla numbers written down here. A spawner
 * another mod has tuned or a player has upgraded therefore pays more without this class knowing
 * such upgrades exist.
 *
 * <p>The reading and the suppression happen together on a timer, so a siphon costs one look at
 * its neighbour every couple of seconds and nothing at all in between. It burns no fuel, so it
 * takes energy upgrades only, and its ramp is a warm-up: a siphon just attached pays a fraction of
 * the spawner's rating and climbs to all of it while it stays on the job.
 */
public class SpawnerSiphonBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_RATE = "Rate";
    private static final String KEY_SPAWN_COUNT = "SpawnCount";
    private static final String KEY_AVERAGE_DELAY = "AverageDelay";

    // The spawner's own NBT keys; these are vanilla's, not ours.
    private static final String SPAWNER_DELAY = "Delay";
    private static final String SPAWNER_MIN_DELAY = "MinSpawnDelay";
    private static final String SPAWNER_MAX_DELAY = "MaxSpawnDelay";
    private static final String SPAWNER_SPAWN_COUNT = "SpawnCount";

    /** The spawner stores its countdown as a short, so nothing above this can be written back. */
    private static final int MAX_SPAWNER_DELAY = Short.MAX_VALUE;

    private @Nullable Direction spawnerSide;
    private int ratePerTick;
    private int spawnCount;
    private int averageDelay;
    private long nextSampleTick;

    public SpawnerSiphonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWNER_SIPHON.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.SPAWNER_SIPHON_CAPACITY, 100_000),
                () -> ServerConfig.valueOr(ServerConfig.SPAWNER_SIPHON_TRANSFER, 1_000),
                defaultSides());
    }

    /** Power out of every face; it moves no items. */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.OUTPUT);
        return config;
    }

    // ------------------------------------------------------------------ work

    @Override
    public boolean acceptsUpgrade(UpgradeType type) {
        return type == UpgradeType.ENERGY;
    }

    @Override
    public boolean usesWarmupRamp() {
        return true;
    }

    @Override
    protected int baseEnergyPerTick() {
        return ratePerTick;
    }

    /** FE/t the attached spawner is worth. The spawner is the rating; there is no speed to add. */
    public int ratedEnergyPerTick() {
        return ratePerTick;
    }

    /** FE/t it is actually banking: the spawner's rating, scaled by however warm it has got. */
    public int generatedEnergyPerTick() {
        return warmed(ratePerTick);
    }

    @Override
    public int displayedEnergyRate() {
        return generatedEnergyPerTick();
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        sampleIfDue(level, pos);
        if (ratePerTick <= 0 || energy.isFull()) {
            return false;
        }
        energy.generate(generatedEnergyPerTick());
        return true;
    }

    public int energyPerSpawn() {
        return ServerConfig.valueOr(ServerConfig.SPAWNER_SIPHON_FE_PER_SPAWN, 5_000);
    }

    public int sampleIntervalTicks() {
        return ServerConfig.valueOr(ServerConfig.SPAWNER_SIPHON_SAMPLE_INTERVAL_TICKS, 40);
    }

    /**
     * How far the spawner's countdown is pushed out on each reading.
     *
     * <p>Deliberately only a few readings' worth rather than something enormous: it has to
     * outlast the gap between readings by a wide margin, but a siphon that is switched off with
     * redstone should hand the spawner back within seconds rather than leaving it dead for the
     * rest of the evening.
     */
    public int suppressedDelay() {
        return Math.clamp(sampleIntervalTicks() * 3L, 100, MAX_SPAWNER_DELAY);
    }

    // ------------------------------------------------------------------ the spawner next door

    /** Whether it currently has a spawner to work on. */
    public boolean hasSpawner() {
        return spawnerSide != null;
    }

    /** Mobs the attached spawner releases per attempt, as the spawner itself reports it. */
    public int spawnCount() {
        return spawnCount;
    }

    /** Ticks the attached spawner waits between attempts, averaged over its own range. */
    public int averageDelay() {
        return averageDelay;
    }

    private void sampleIfDue(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (now < nextSampleTick) {
            return;
        }
        nextSampleTick = now + sampleIntervalTicks();
        sample(level, pos);
    }

    /** Reads the spawner's stats and, in the same pass, pushes its countdown back out of reach. */
    private void sample(ServerLevel level, BlockPos pos) {
        SpawnerBlockEntity spawner = findSpawner(level, pos);
        if (spawner == null) {
            forgetSpawner();
            return;
        }

        BaseSpawner base = spawner.getSpawner();
        CompoundTag tag = base.save(new CompoundTag());

        int count = tag.getShort(SPAWNER_SPAWN_COUNT);
        int min = tag.getShort(SPAWNER_MIN_DELAY);
        int max = tag.getShort(SPAWNER_MAX_DELAY);
        int average = Math.max(1, (min + max) / 2);

        int rate = rateFor(count, average);
        if (rate != ratePerTick || count != spawnCount || average != averageDelay) {
            ratePerTick = rate;
            spawnCount = count;
            averageDelay = average;
            setChanged();
        }

        setSpawnerDelay(spawner, base, tag, suppressedDelay());
    }

    /**
     * FE/t for a spawner releasing {@code count} mobs every {@code averageDelay} ticks.
     *
     * <p>Long arithmetic on purpose: a spawner another mod has cranked up can carry numbers that
     * would overflow an int well before the division brings them back down.
     */
    private int rateFor(int count, int averageDelay) {
        if (count <= 0 || averageDelay <= 0) {
            return 0;
        }
        long total = (long) energyPerSpawn() * count;
        return (int) Math.min(Integer.MAX_VALUE, (total + averageDelay - 1) / averageDelay);
    }

    /**
     * Writes a countdown back into a spawner.
     *
     * <p>{@code BaseSpawner} exposes no setter for its delay, so the change goes through its own
     * save/load pair. The level is deliberately passed as null: it is only used to broadcast a
     * block update for the spinning mob in the cage, and the mob has not changed.
     */
    private static void setSpawnerDelay(SpawnerBlockEntity spawner, BaseSpawner base, CompoundTag tag, int delay) {
        tag.putShort(SPAWNER_DELAY, (short) Math.clamp(delay, 0, MAX_SPAWNER_DELAY));
        base.load(null, spawner.getBlockPos(), tag);
        spawner.setChanged();
    }

    private void forgetSpawner() {
        if (spawnerSide == null && ratePerTick == 0) {
            return;
        }
        spawnerSide = null;
        ratePerTick = 0;
        spawnCount = 0;
        averageDelay = 0;
        setChanged();
    }

    /**
     * The spawner it is attached to, preferring the side it found last time.
     *
     * <p>Six block entity lookups is the worst case and it only happens when the remembered side
     * has gone away, so an attached siphon costs one lookup per reading.
     */
    private @Nullable SpawnerBlockEntity findSpawner(BlockGetter level, BlockPos pos) {
        if (spawnerSide != null
                && level.getBlockEntity(pos.relative(spawnerSide)) instanceof SpawnerBlockEntity remembered) {
            return remembered;
        }
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(pos.relative(side)) instanceof SpawnerBlockEntity found) {
                spawnerSide = side;
                return found;
            }
        }
        return null;
    }

    @Override
    public void onNeighbourChanged(boolean nowPowered) {
        super.onNeighbourChanged(nowPowered);
        // A spawner may have just been placed or broken next to us.
        nextSampleTick = 0;
    }

    /** Hands the spawner back: its countdown is restored to a normal cycle. */
    @Override
    public void onRemovedFromWorld(Level level, BlockPos pos) {
        super.onRemovedFromWorld(level, pos);
        if (level.isClientSide) {
            return;
        }
        SpawnerBlockEntity spawner = findSpawner(level, pos);
        if (spawner == null) {
            return;
        }
        BaseSpawner base = spawner.getSpawner();
        CompoundTag tag = base.save(new CompoundTag());
        setSpawnerDelay(spawner, base, tag, tag.getShort(SPAWNER_MIN_DELAY));
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.spawner_siphon");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SpawnerSiphonMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_RATE, ratePerTick);
        tag.putInt(KEY_SPAWN_COUNT, spawnCount);
        tag.putInt(KEY_AVERAGE_DELAY, averageDelay);
        // The side is not saved: the next reading finds it again, and a world edited while the
        // chunk was unloaded would make a remembered side a lie.
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ratePerTick = tag.getInt(KEY_RATE);
        spawnCount = tag.getInt(KEY_SPAWN_COUNT);
        averageDelay = tag.getInt(KEY_AVERAGE_DELAY);
    }
}
