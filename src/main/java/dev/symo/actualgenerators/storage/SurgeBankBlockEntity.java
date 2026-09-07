package dev.symo.actualgenerators.storage;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.SurgeBankMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A burst battery: an enormous buffer with an enormous throughput, and a slow leak for sitting on
 * a charge it is not using.
 *
 * <p>Banks that touch share what they hold. There is no structure to form and no controller to
 * elect — each bank simply hands a neighbour half of whatever that neighbour is short by — so a
 * wall of them behaves as one pool, and breaking one out of the middle can neither duplicate nor
 * lose a charge, because energy is taken out of one block before it is put into the other and
 * anything refused is put straight back. Setting a face to disabled cuts the bank in two there.
 *
 * <p>The leak is the trade. A bank being pushed into or pulled out of loses nothing; one holding
 * a charge with nothing happening bleeds a fraction of it away every second. It is a buffer for a
 * base whose draw spikes, not a vault to keep a week of production in.
 *
 * <p>It stores rather than converts, so speed, overclock and stack upgrades have nothing to act
 * on: it takes energy upgrades only, which is exactly what a battery wants anyway.
 */
public class SurgeBankBlockEntity extends MachineBlockEntity implements MenuProvider {
    /** A second of game time; the leak and the rate readout are both per-second figures. */
    private static final int SECOND = 20;

    /** The charge as it was last looked at, so the bank can tell movement from its own leak. */
    private long seenEnergy = -1;
    private long idleSinceTick;
    private long nextLeakTick;

    private int netRate;
    private long lastSampleTick;
    private long lastSampleEnergy;

    public SurgeBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SURGE_BANK.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.SURGE_BANK_CAPACITY, 4_000_000L),
                () -> ServerConfig.valueOr(ServerConfig.SURGE_BANK_TRANSFER, 20_000),
                defaultSides());
    }

    /**
     * Power in through every face but the front, which is where it comes back out, and the bank
     * goes looking from the first tick: it pulls from whatever is on its input faces and pushes
     * into whatever is at the front. A battery that had to be told to charge was a battery
     * nobody asked for.
     *
     * <p>Not everything both ways: two banks set to push at each other would spend the rest of the
     * game handing the same energy back and forth; the one-way faces are what stop that.
     */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.INPUT);
        config.set(TransferKind.ENERGY, RelativeSide.FRONT, IoMode.OUTPUT);
        config.setAuto(TransferKind.ENERGY, false, true);
        config.setAuto(TransferKind.ENERGY, true, true);
        return config;
    }

    // ------------------------------------------------------------------ work

    @Override
    public boolean acceptsUpgrade(UpgradeType type) {
        return type == UpgradeType.ENERGY;
    }

    @Override
    protected int baseEnergyPerTick() {
        // It neither makes power nor spends it. It only holds it.
        return 0;
    }

    @Override
    public int displayedEnergyRate() {
        return netRate;
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        long now = level.getGameTime();
        balanceWithNeighbours(level, pos, state);
        sampleRate(now);
        leakIfIdle(now);
        // A bank has no work to report. Saying so keeps it in the chassis' idle back-off, which is
        // what a block whose whole job is to sit still ought to be doing.
        return false;
    }

    // ------------------------------------------------------------------ sharing with neighbours

    private void balanceWithNeighbours(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = facing(state);
        for (Direction side : Direction.values()) {
            if (!sideConfig.get(TransferKind.ENERGY, facing, side).isActive()) {
                continue;
            }
            if (level.getBlockEntity(pos.relative(side)) instanceof SurgeBankBlockEntity neighbour) {
                share(neighbour);
            }
        }
    }

    /**
     * Hands a poorer neighbour half of what it is short by.
     *
     * <p>Only the fuller of a pair ever moves anything, so the two banks do not fight over the
     * same energy, and half the gap each pass converges on an even split without oscillating.
     */
    private void share(SurgeBankBlockEntity neighbour) {
        long gap = energy.stored() - neighbour.energyStorage().stored();
        if (gap <= 1) {
            return;
        }

        // Unthrottled on purpose: banks in contact are one pool, and the transfer rate governs
        // what the outside world may pull through a face, not how charge moves inside the pool.
        long moved = energy.consume(gap / 2);
        long refused = moved - neighbour.energyStorage().generate(moved);
        if (refused > 0) {
            energy.generate(refused);
        }
    }

    // ------------------------------------------------------------------ the leak

    /** Permille of the stored charge lost each second once the bank has gone quiet. */
    public int leakPermillePerSecond() {
        return ServerConfig.valueOr(ServerConfig.SURGE_BANK_LEAK_PERMILLE, 2);
    }

    /** How long the bank must go untouched before it starts leaking. */
    public int leakIdleTicks() {
        return ServerConfig.valueOr(ServerConfig.SURGE_BANK_LEAK_IDLE_TICKS, 100);
    }

    /** FE the bank would lose this second at its current charge. Zero when the leak is off. */
    public long leakPerSecond() {
        long stored = energy.stored();
        int permille = leakPermillePerSecond();
        if (stored <= 0 || permille <= 0) {
            return 0;
        }
        return Math.max(1, stored / 1000L * permille + stored % 1000L * permille / 1000L);
    }

    /** Whether it is actually bleeding right now, rather than merely capable of it. */
    public boolean isLeaking() {
        return level != null
                && leakPerSecond() > 0
                && energy.stored() == seenEnergy
                && level.getGameTime() - idleSinceTick >= leakIdleTicks();
    }

    private void leakIfIdle(long now) {
        long stored = energy.stored();
        if (stored != seenEnergy) {
            // Something moved. A bank in use does not leak; the clock starts when it goes quiet.
            seenEnergy = stored;
            idleSinceTick = now;
            return;
        }
        if (leakPerSecond() <= 0 || now - idleSinceTick < leakIdleTicks() || now < nextLeakTick) {
            return;
        }

        nextLeakTick = now + SECOND;
        energy.consume(leakPerSecond());
        // Its own leak is not movement, or the bank would keep resetting its own idle clock.
        seenEnergy = energy.stored();
        setChanged();
    }

    /**
     * Net FE/t over the gap since the last look, for the screen.
     *
     * <p>A bank reports no work, so this runs on the chassis' idle interval rather than every
     * tick — which is fine, since an average over a second is what a player can read anyway.
     */
    private void sampleRate(long now) {
        long elapsed = now - lastSampleTick;
        long stored = energy.stored();
        if (elapsed > 0 && elapsed <= 10L * SECOND) {
            netRate = (int) Math.clamp((stored - lastSampleEnergy) / elapsed, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        lastSampleEnergy = stored;
        lastSampleTick = now;
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.surge_bank");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SurgeBankMenu(containerId, playerInventory, this);
    }
}
