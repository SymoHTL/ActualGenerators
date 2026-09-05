package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.HydrostaticGeneratorMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Harvests the pressure of the water standing above it.
 *
 * <p>Flood a shaft and every metre of it pays. The generator burns nothing and needs no sky, so
 * the only thing that decides its output is the column you built — which is also why it refuses
 * speed and overclock upgrades: with no fuel to trade away, running it "faster" would simply
 * conjure energy. Energy upgrades still apply, since buffer size and throughput are its own.
 *
 * <p>The ramp it does have is a warm-up: a shaft just flooded makes a fraction of what it is worth
 * and climbs to the full rating while it keeps running, so it never exceeds the column.
 *
 * <p>Only still water counts. Falling water down a shaft is one source block and a rope of
 * flowing water beneath it, which would make a one-bucket build as good as a flooded one.
 * Waterlogged blocks do count, so a shaft of waterlogged stairs or panes is a legitimate build.
 *
 * <p>The column is measured on a timer rather than every tick, and immediately whenever the
 * block right above changes — the case a player actually notices.
 */
public class HydrostaticGeneratorBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_COLUMN = "Column";

    private int column;
    private long nextMeasureTick;

    public HydrostaticGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HYDROSTATIC_GENERATOR.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.HYDROSTATIC_CAPACITY, 20_000),
                () -> ServerConfig.valueOr(ServerConfig.HYDROSTATIC_TRANSFER, 200),
                defaultSides());
    }

    /** Power out of every face; it moves no items at all. */
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
        return ratedEnergyPerTick();
    }

    /** FE/t the shaft is worth at full pressure, before the warm-up ramp. */
    public int ratedEnergyPerTick() {
        return energyPerColumnBlock() * column;
    }

    /** FE/t it is actually making: the shaft's rating, scaled by however warm it has got. */
    public int generatedEnergyPerTick() {
        return warmed(ratedEnergyPerTick());
    }

    @Override
    public int displayedEnergyRate() {
        return generatedEnergyPerTick();
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        measureColumnIfDue(level, pos);
        if (column <= 0 || energy.isFull()) {
            return false;
        }
        energy.generate(generatedEnergyPerTick());
        return true;
    }

    // ------------------------------------------------------------------ the water column

    /** Water blocks standing above this generator, capped by config. */
    public int columnHeight() {
        return column;
    }

    public int maxColumn() {
        return ServerConfig.valueOr(ServerConfig.HYDROSTATIC_MAX_COLUMN, 16);
    }

    public int energyPerColumnBlock() {
        return ServerConfig.valueOr(ServerConfig.HYDROSTATIC_FE_PER_COLUMN_BLOCK, 2);
    }

    private int columnRecheckTicks() {
        return ServerConfig.valueOr(ServerConfig.HYDROSTATIC_COLUMN_RECHECK_TICKS, 40);
    }

    /**
     * Re-measures on the configured interval. Game time is the clock rather than a countdown,
     * because an idle machine only reaches {@link #tickWork} every few ticks and a countdown
     * would stretch with it.
     */
    private void measureColumnIfDue(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (now < nextMeasureTick) {
            return;
        }
        nextMeasureTick = now + columnRecheckTicks();

        int measured = measureColumn(level, pos);
        if (measured != column) {
            column = measured;
            setChanged();
        }
    }

    /** Walks up until the water stops, or until the configured ceiling. */
    private int measureColumn(BlockGetter level, BlockPos pos) {
        int max = maxColumn();
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int height = 0;
        while (height < max) {
            cursor.move(Direction.UP);
            if (!countsTowardColumn(level.getBlockState(cursor))) {
                break;
            }
            height++;
        }
        return height;
    }

    /**
     * Whether a block carries the column. Still water only — including waterlogged blocks, which
     * hold just as much water as an open shaft does.
     */
    public static boolean countsTowardColumn(BlockState state) {
        FluidState fluid = state.getFluidState();
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }

    @Override
    public void onNeighbourChanged(boolean nowPowered) {
        super.onNeighbourChanged(nowPowered);
        // Something changed next to us -- possibly the block holding the column up.
        nextMeasureTick = 0;
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.hydrostatic_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new HydrostaticGeneratorMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_COLUMN, column);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        column = tag.getInt(KEY_COLUMN);
    }
}
