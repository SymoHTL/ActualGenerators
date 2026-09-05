package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.PhotovoreMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import dev.symo.actualgenerators.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A block that grazes on light.
 *
 * <p>It reaches out for placed light sources, eats the nearest one, and turns it into FE by how
 * brightly it burned. Nothing is piped in: the fuel is whatever you lit the room with, which is
 * the whole joke — plant one carelessly and it will quietly clear your torches, and then the room
 * fills with something else.
 *
 * <p>What counts as food is the {@code photovore_food} block tag, not "anything that emits
 * light". A whitelist keeps it from eating beacons, portals, lava or fire — fire in particular
 * would regrow off netherrack forever and turn this into a free generator.
 *
 * <p>Finding food is the one thing here that cannot be event driven: nothing notifies a block
 * that a torch went up six blocks away. So the search is a poll, and it is fenced in on three
 * sides — it only runs when the machine has eaten everything it knew about, only when there is
 * room in the buffer for more power, and never more often than {@code scanIntervalTicks}. What it
 * finds is cached and worked through one meal at a time, so a scan is rare rather than routine.
 */
public class PhotovoreBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_PROGRESS = "Progress";

    /** Bounds both the memory this holds and the work of sorting a scan's results. */
    private static final int MAX_REMEMBERED = 32;

    private final List<BlockPos> food = new ArrayList<>();
    private int progress;
    private long nextScanTick;

    public PhotovoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHOTOVORE.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.PHOTOVORE_CAPACITY, 50_000),
                () -> ServerConfig.valueOr(ServerConfig.PHOTOVORE_TRANSFER, 500),
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
    protected int baseEnergyPerTick() {
        // Meals pay in a lump when they finish, so there is no meaningful per-tick rate.
        return 0;
    }

    public int energyPerLightLevel() {
        return ServerConfig.valueOr(ServerConfig.PHOTOVORE_FE_PER_LIGHT_LEVEL, 300);
    }

    public int radius() {
        return ServerConfig.valueOr(ServerConfig.PHOTOVORE_RADIUS, 5);
    }

    public int baseGrazeTicks() {
        return ServerConfig.valueOr(ServerConfig.PHOTOVORE_GRAZE_TICKS, 200);
    }

    private int scanIntervalTicks() {
        return ServerConfig.valueOr(ServerConfig.PHOTOVORE_SCAN_INTERVAL_TICKS, 200);
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        if (energy.isFull()) {
            return false;
        }

        findFoodIfDue(level, pos);
        if (food.isEmpty()) {
            progress = 0;
            return false;
        }

        if (++progress < ticksForOperation(baseGrazeTicks())) {
            setChanged();
            return true;
        }

        progress = 0;
        int earned = eat(level);
        if (earned <= 0) {
            // Everything it remembered had already been taken; look again rather than idling on
            // a stale list.
            food.clear();
            return false;
        }
        energy.generate(earned);
        setChanged();
        return true;
    }

    /**
     * Eats up to a batch of remembered light sources and returns what they were worth.
     *
     * <p>Positions are re-checked here rather than every tick: a torch someone else took is
     * simply skipped, and the cost of noticing is paid once per meal instead of continuously.
     */
    private int eat(ServerLevel level) {
        int batch = maxBatch();
        int light = 0;
        int eaten = 0;

        while (eaten < batch && !food.isEmpty()) {
            BlockPos target = food.remove(0);
            BlockState state = level.getBlockState(target);
            if (!isFood(state)) {
                continue;
            }
            light += state.getLightEmission();
            level.destroyBlock(target, false);
            eaten++;
        }

        if (light <= 0) {
            return 0;
        }
        double scaled = (double) light * energyPerLightLevel() * tuning().generatorEfficiency(totalSpeedMultiplier());
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(scaled));
    }

    /** What one meal would earn right now, for the screen. */
    public int nextMealEnergy() {
        if (level == null || food.isEmpty()) {
            return 0;
        }
        int batch = maxBatch();
        int light = 0;
        for (int i = 0; i < Math.min(batch, food.size()); i++) {
            BlockState state = level.getBlockState(food.get(i));
            if (isFood(state)) {
                light += state.getLightEmission();
            }
        }
        double scaled = (double) light * energyPerLightLevel() * tuning().generatorEfficiency(totalSpeedMultiplier());
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(scaled));
    }

    // ------------------------------------------------------------------ finding light

    /** Light sources it currently knows about. */
    public int foodInRange() {
        return food.size();
    }

    public int progress() {
        return progress;
    }

    private void findFoodIfDue(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (!food.isEmpty() || now < nextScanTick) {
            return;
        }
        nextScanTick = now + scanIntervalTicks();
        scan(level, pos);
    }

    /** Sweeps the cube around the machine and remembers the nearest light, closest first. */
    private void scan(BlockGetter level, BlockPos origin) {
        int radius = radius();
        List<BlockPos> found = new ArrayList<>();

        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (candidate.equals(origin)) {
                continue;
            }
            if (isFood(level.getBlockState(candidate))) {
                found.add(candidate.immutable());
            }
        }

        found.sort(Comparator.comparingDouble(candidate -> candidate.distSqr(origin)));
        food.clear();
        food.addAll(found.subList(0, Math.min(found.size(), MAX_REMEMBERED)));
    }

    /** A block is food if the pack says so and it is actually lit. */
    public static boolean isFood(BlockState state) {
        return state.is(ModTags.Blocks.PHOTOVORE_FOOD) && state.getLightEmission() > 0;
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.photovore");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PhotovoreMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_PROGRESS, progress);
        // The remembered positions are a cache, not state -- they are rebuilt on the first tick.
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt(KEY_PROGRESS);
    }
}
