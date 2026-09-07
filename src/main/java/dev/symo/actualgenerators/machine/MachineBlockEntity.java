package dev.symo.actualgenerators.machine;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.item.TierUpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The chassis every machine and generator in the mod is built on.
 *
 * <p>It owns the parts that are identical everywhere: the FE buffer, the upgrade slots, the
 * per-face input/output configuration, the redstone mode and the overclock ramp. Subclasses
 * supply only what makes them different, by implementing {@link #tickWork}.
 *
 * <p>Ticking is deliberately lazy. A machine that finds no work backs off and only looks
 * again every few ticks, and neighbour lookups go through {@link BlockCapabilityCache} so
 * nothing rescans the world. Anything that could give a sleeping machine something to do
 * calls {@link #wake()}.
 */
public abstract class MachineBlockEntity extends BlockEntity {
    private static final String KEY_ENERGY = "Energy";
    private static final String KEY_UPGRADES = "Upgrades";
    private static final String KEY_TIER = "Tier";
    private static final String KEY_SIDES = "Sides";
    private static final String KEY_REDSTONE = "RedstoneMode";
    private static final String KEY_OVERCLOCK = "Overclock";
    private static final String KEY_WORKING = "Working";

    protected final MachineEnergyStorage energy;
    protected final UpgradeInventory upgrades;
    protected final SideConfig sideConfig;
    protected final OverclockState overclock = new OverclockState();

    protected RedstoneMode redstoneMode = RedstoneMode.ALWAYS;

    // Suppliers rather than captured values: a config reload must reach machines that are
    // already placed, and applyUpgrades() re-reads these whenever the tuning changes.
    private final LongSupplier baseCapacity;
    private final IntSupplier baseTransferRate;

    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IEnergyStorage, @Nullable Direction>[] energyNeighbours =
            new BlockCapabilityCache[Direction.values().length];

    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IItemHandler, @Nullable Direction>[] itemNeighbours =
            new BlockCapabilityCache[Direction.values().length];

    /**
     * The block's own grade, apart from the upgrade queue: one tier item, or nothing. Only machines
     * that say so take one ({@link #acceptsTier()}); its numbers come from {@link MachineTier}.
     */
    private final ItemStackHandler tierSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || (acceptsTier() && stack.getItem() instanceof TierUpgradeItem);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            onUpgradesChanged();
            syncToClients();
        }
    };

    private MachineTuning appliedTuning = MachineTuning.DEFAULT;
    private boolean powered;
    private boolean working;
    private int autoIoCooldown;
    private int ticksSinceAutoIo;
    private int workCooldown;

    protected MachineBlockEntity(BlockEntityType<?> type,
                                 BlockPos pos,
                                 BlockState state,
                                 LongSupplier baseCapacity,
                                 IntSupplier baseTransferRate,
                                 SideConfig defaultSides) {
        super(type, pos, state);
        this.baseCapacity = baseCapacity;
        this.baseTransferRate = baseTransferRate;
        this.sideConfig = defaultSides;
        this.upgrades = new UpgradeInventory(this::acceptsUpgrade, this::maxUpgrades, this::onUpgradesChanged);

        int rate = baseTransferRate.getAsInt();
        this.energy = new MachineEnergyStorage(baseCapacity.getAsLong(), rate, rate, this::onEnergyChanged);
    }

    // ------------------------------------------------------------------ subclass contract

    /**
     * Runs one tick of whatever this machine does.
     *
     * @return true if it did useful, powered work — that is what feeds the overclock ramp
     */
    protected abstract boolean tickWork(ServerLevel level, BlockPos pos, BlockState state);

    /** Slots this machine wants emptied into neighbours on output faces. */
    protected @Nullable IItemHandler autoOutputHandler() {
        return null;
    }

    /** Slots this machine wants filled from neighbours on input faces. */
    protected @Nullable IItemHandler autoInputHandler() {
        return null;
    }

    /** The tank this machine holds, or null for the many that hold none. The faces gate it by their fluid mode. */
    protected @Nullable IFluidHandler fluidTank() {
        return null;
    }

    /** The base FE/t this machine consumes or produces before any scaling. */
    protected abstract int baseEnergyPerTick();

    /**
     * Which upgrade types this machine has any use for. Slots refuse the rest.
     *
     * <p>Most machines take all four. A generator whose output the world dictates — depth,
     * weather, sunlight — takes only energy upgrades, because speed and overclocking would
     * hand it free power rather than trading fuel for it.
     */
    public boolean acceptsUpgrade(UpgradeType type) {
        return true;
    }

    /**
     * Whether a tier goes in. Off by default and on for processing machines only: a tier makes a
     * block faster at the same price per operation, which on a generator would be free FE.
     */
    public boolean acceptsTier() {
        return false;
    }

    public ItemStackHandler tierSlot() {
        return tierSlot;
    }

    /** The grade of this block, from the tier item in its slot. */
    public MachineTier tier() {
        return TierUpgradeItem.tierOf(tierSlot.getStackInSlot(0));
    }

    /**
     * True for a generator whose ramp is a warm-up towards its rating rather than an overclock
     * past it. Such a generator burns no fuel, so it cannot trade efficiency for speed; instead
     * it starts below what it is worth and climbs to it while it keeps running.
     */
    public boolean usesWarmupRamp() {
        return false;
    }

    /** Output scale from the ramp: below one while warming up, exactly one for everything else. */
    public double warmupMultiplier() {
        MachineTuning tuning = tuning();
        return usesWarmupRamp() ? tuning.warmupMultiplier(overclock.progress(tuning)) : 1.0;
    }

    /** Scales a rating by the warm-up ramp, never quite to nothing while there is work. */
    protected int warmed(int rated) {
        if (rated <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(rated * warmupMultiplier()));
    }

    /**
     * Anything this machine has worked out for itself that a config card should carry along with
     * the rest of the setup — the Resonance Crusher's calibrated frequencies, for instance.
     *
     * <p>Strings rather than anything structured: a card is a snapshot, not a save file, and every
     * machine that has learned something so far has learned a list of ids.
     */
    public java.util.List<String> learned() {
        return java.util.List.of();
    }

    /** Takes on what another machine of the same kind had learned. */
    public void applyLearned(java.util.List<String> learned) {
    }

    /**
     * How far along the operation in progress is, from 0 to 1, or a negative number for a machine
     * that has no such thing to show.
     *
     * <p>Lives on the machine rather than in a menu because a window is not the only thing that
     * asks: a Jade tooltip reports the same figure without anything being open.
     */
    public double progressFraction() {
        return -1;
    }

    /** How many upgrades of a type this machine will count — and therefore accept. */
    public int maxUpgrades(UpgradeType type) {
        return acceptsUpgrade(type) ? tuning().maxUpgrades(type) : 0;
    }

    /**
     * FE/t this machine is moving on its own account right now: positive when it is generating,
     * negative when it is spending, zero when idle.
     *
     * <p>Purely a readout. Machines paid in lumps — a meal, an impact — report zero here and show
     * what they earned some other way, because a per-tick figure would be a lie.
     */
    public int displayedEnergyRate() {
        return 0;
    }

    /** Buffer size before energy upgrades, so a screen can show what one more would add. */
    public long baseCapacity() {
        return baseCapacity.getAsLong();
    }

    /** Throughput before energy upgrades, for the same reason. */
    public int baseTransferRate() {
        return baseTransferRate.getAsInt();
    }

    // ------------------------------------------------------------------ derived stats

    public MachineTuning tuning() {
        return ServerConfig.tuning();
    }

    public int speedUpgrades() {
        return upgrades.count(UpgradeType.SPEED);
    }

    public int overclockUpgrades() {
        return upgrades.count(UpgradeType.OVERCLOCK);
    }

    public int energyUpgrades() {
        return upgrades.count(UpgradeType.ENERGY);
    }

    public int stackUpgrades() {
        return upgrades.count(UpgradeType.STACK);
    }

    /**
     * The largest batch this machine may process in one operation. Machines should shrink this
     * to what their inputs and output room actually allow.
     */
    public int maxBatch() {
        return tuning().maxBatch(stackUpgrades()) * tier().batchMultiplier();
    }

    /** Items one operation takes per batch item. A recipe that eats two for one result says two. */
    protected int inputsPerBatchItem() {
        return 1;
    }

    /** Items one operation yields per batch item, at the worst case a batch has to fit. */
    protected int outputsPerBatchItem() {
        return 1;
    }

    /**
     * What an input slot holds: a configured number of operations' worth, never less than a stack.
     *
     * <p>The slot limit is the feed rate. An AE2 pattern provider pushes until insertion is refused,
     * so a slot capped at sixty-four would cap a machine at sixty-four a tick whatever its batch
     * said; sized from the batch, the batch is the only cap there is. Two operations would cover
     * the rate; the count is a config so a fitted machine visibly holds more than a bare one.
     */
    public int inputSlotLimit() {
        return Math.max(64, slotOperations() * maxBatch() * inputsPerBatchItem());
    }

    /** What an output slot holds: the same number of operations' worth of results. */
    public int outputSlotLimit() {
        return Math.max(64, slotOperations() * maxBatch() * outputsPerBatchItem());
    }

    /** Operations' worth a slot holds. */
    public static int slotOperations() {
        return ServerConfig.valueOr(ServerConfig.SLOT_OPERATIONS, 8);
    }

    /** Speed from the upgrade queue alone: speed upgrades and however far the overclock ramp has climbed. */
    public double upgradeSpeedMultiplier() {
        MachineTuning tuning = tuning();
        return tuning.totalSpeedMultiplier(speedUpgrades(), overclockUpgrades(), overclock.progress(tuning));
    }

    /** Everything that makes an operation shorter: the upgrade queue times the block's own tier. */
    public double totalSpeedMultiplier() {
        return upgradeSpeedMultiplier() * tier().speedMultiplier();
    }

    /** FE/t at the current multiplier for a batch of one. */
    public int currentEnergyPerTick() {
        return currentEnergyPerTick(1);
    }

    /** FE/t at the current multiplier and batch size — superlinear in speed, linear in batch. */
    public int currentEnergyPerTick(int batchSize) {
        // The tier stays outside the superlinear term on purpose: it makes operations shorter at
        // the same FE each, so the bill per tick rises with it linearly, like batch, never like speed.
        double perTick = tuning().energyPerTick(baseEnergyPerTick(), upgradeSpeedMultiplier(), batchSize)
                * tier().speedMultiplier();
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(perTick));
    }

    /**
     * True once this machine finishes an operation every tick, so extra speed buys nothing and
     * only stack upgrades raise throughput. Worth surfacing in the GUI so players are not
     * paying quadratic energy costs for speed the tick rate cannot deliver.
     */
    public boolean isSpeedSaturated(int baseTicks) {
        return tuning().isSpeedSaturated(baseTicks, totalSpeedMultiplier());
    }

    /** How long one operation takes at the current multiplier. */
    public int ticksForOperation(int baseTicks) {
        return tuning().ticksForOperation(baseTicks, totalSpeedMultiplier());
    }

    public MachineEnergyStorage energyStorage() {
        return energy;
    }

    public UpgradeInventory upgradeInventory() {
        return upgrades;
    }

    public SideConfig sideConfig() {
        return sideConfig;
    }

    public OverclockState overclockState() {
        return overclock;
    }

    public RedstoneMode redstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        this.redstoneMode = mode;
        wake();
        setChanged();
    }

    public boolean isWorking() {
        return working;
    }

    // ------------------------------------------------------------------ ticking

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        applyTuningIfChanged();

        boolean allowed = redstoneMode.canRun(powered);
        boolean didWork = false;

        if (allowed) {
            if (workCooldown > 0) {
                workCooldown--;
            } else {
                didWork = tickWork(level, pos, state);
                if (!didWork) {
                    // Nothing to do: back off instead of retrying every tick.
                    workCooldown = ServerConfig.idleRecheckTicks();
                }
            }
        }

        // The ramp only ever falls when there is no work or no power -- never between recipes.
        if (overclock.tick(didWork, tuning())) {
            setChanged();
        }

        if (didWork) {
            hurryTransferIfSlotsAreTight();
        }

        ticksSinceAutoIo++;
        if (--autoIoCooldown <= 0) {
            autoIoCooldown = ServerConfig.autoIoIntervalTicks();
            // A pass every N ticks carries N ticks' worth of energy, so the configured FE/t is the
            // rate a player actually gets. Capped at the interval so a machine coming back from an
            // unloaded chunk empties a stale gap at the rated speed rather than in one burst.
            autoIo(level, pos, state, Math.min(ticksSinceAutoIo, autoIoCooldown));
            ticksSinceAutoIo = 0;
        }

        if (didWork != working) {
            working = didWork;
            onWorkingChanged(level, pos, state, didWork);
        }
    }

    /** Called when the machine starts or stops working; syncs so renderers can react. */
    protected void onWorkingChanged(ServerLevel level, BlockPos pos, BlockState state, boolean nowWorking) {
        syncToClients();
    }

    /** Clears the idle back-off. Anything that could give the machine work calls this. */
    public void wake() {
        workCooldown = 0;
    }

    /** Makes the next tick run an automatic transfer pass instead of waiting for the interval. */
    public void requestAutoIo() {
        autoIoCooldown = 0;
    }

    /**
     * Brings the next transfer pass forward when the slots can no longer cover an operation.
     *
     * <p>A fully upgraded machine eats and produces a batch every tick, which is far more than a
     * pass every tenth tick can carry — the machine would spend nine ticks in ten waiting on its
     * own slots. So the measure of "tight" is the batch itself: an output that cannot take
     * another one, or an input that cannot fill another one, means the transfer rate has fallen
     * behind the work rate and the pass happens now.
     *
     * <p>That makes the transfer rate follow the work rate for free. An unupgraded machine
     * batching one item at a time is tight only when a slot is actually full or actually empty,
     * so it keeps to the interval; a machine chewing a stack a tick is tight every tick, and gets
     * a pass every tick, which is exactly what it needs to keep going.
     *
     * <p>Only ever called on a tick the machine did work, so an idle one costs nothing.
     */
    private void hurryTransferIfSlotsAreTight() {
        if (autoIoCooldown <= 1 || !sideConfig.autoAny(TransferKind.ITEM)) {
            return;
        }
        int batch = maxBatch();
        IItemHandler outputs = sideConfig.autoPush(TransferKind.ITEM) ? autoOutputHandler() : null;
        IItemHandler inputs = sideConfig.autoPull(TransferKind.ITEM) ? autoInputHandler() : null;
        if ((outputs != null && outputIsBackedUp(outputs, batch * outputsPerBatchItem()))
                || (inputs != null && inputIsShort(inputs, batch * inputsPerBatchItem()))) {
            requestAutoIo();
        }
    }

    /** True when some output slot has room for less than one more batch. */
    public static boolean outputIsBackedUp(IItemHandler outputs, int batch) {
        for (int slot = 0; slot < outputs.getSlots(); slot++) {
            if (outputs.getSlotLimit(slot) - outputs.getStackInSlot(slot).getCount() < batch) {
                return true;
            }
        }
        return false;
    }

    /** True when some input slot holds less than one more batch. */
    public static boolean inputIsShort(IItemHandler inputs, int batch) {
        for (int slot = 0; slot < inputs.getSlots(); slot++) {
            if (inputs.getStackInSlot(slot).getCount() < batch) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ automatic transfer

    private void autoIo(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        Direction facing = facing(state);
        // A kind with both auto flags off is nobody's business but the neighbours': the faces stay
        // open for anything that comes looking, and the machine itself does not go out.
        if (sideConfig.hasAny(TransferKind.ENERGY) && sideConfig.autoAny(TransferKind.ENERGY)) {
            autoIoEnergy(level, pos, facing, ticks);
        }
        if (sideConfig.hasAny(TransferKind.ITEM) && sideConfig.autoAny(TransferKind.ITEM)) {
            autoIoItems(level, pos, facing);
        }
        autoIoBeyondFaces(level, ticks);
    }

    /**
     * The same pass, for whatever a machine moves through that is not one of its own six faces:
     * a multiblock's hatches. Nothing by default.
     */
    protected void autoIoBeyondFaces(ServerLevel level, int ticks) {
    }

    private void autoIoEnergy(ServerLevel level, BlockPos pos, Direction facing, int ticks) {
        boolean push = sideConfig.autoPush(TransferKind.ENERGY);
        boolean pull = sideConfig.autoPull(TransferKind.ENERGY);
        for (Direction side : Direction.values()) {
            IoMode mode = sideConfig.get(TransferKind.ENERGY, facing, side);
            if (!mode.isActive()) {
                continue;
            }
            IEnergyStorage neighbour = energyNeighbour(level, pos, side);
            if (neighbour == null) {
                continue;
            }
            if (push && mode.canOutput() && energy.canExtract()) {
                pushEnergy(neighbour, budget(energy.getMaxExtract(), ticks), ticks);
            }
            if (pull && mode.canInput() && !energy.isFull()) {
                pullEnergy(neighbour, budget(energy.getMaxReceive(), ticks), ticks);
            }
        }
    }

    /** What a rate of {@code perTick} FE/t adds up to over {@code ticks}, without overflowing. */
    private static long budget(int perTick, int ticks) {
        return (long) perTick * Math.max(ticks, 1);
    }

    /** A budget as the capability can express it: one transfer call is int-bound whatever we hold. */
    private static int offerable(long amount) {
        return (int) Math.clamp(amount, 0, Integer.MAX_VALUE);
    }

    /**
     * Offers the budget in one capability call per tick the pass covers, not all at once.
     *
     * <p>Every {@code receiveEnergy} call is capped at the receiver's own per-tick rate, so a
     * single call carrying ten ticks' worth moves a tenth of it: a 1,000 FE/t generator fed a
     * 1,000 FE/t crusher at 100 FE/t, and read in the world as "it only transfers every second".
     * As many calls as ticks is what a cable ticking beside the machine would have made; the loop
     * ends at the first call that moves nothing, so a full neighbour costs one call.
     */
    private void pushEnergy(IEnergyStorage neighbour, long budget, int ticks) {
        long remaining = Math.min(budget, energy.stored());
        for (int call = 0; call < Math.max(ticks, 1) && remaining > 0; call++) {
            int accepted = neighbour.receiveEnergy(offerable(remaining), false);
            if (accepted <= 0) {
                return;
            }
            energy.consume(accepted);
            remaining -= accepted;
        }
    }

    /** The same, drawing: a neighbour's {@code extractEnergy} is capped per call just the same. */
    private void pullEnergy(IEnergyStorage neighbour, long budget, int ticks) {
        long remaining = Math.min(budget, energy.room());
        for (int call = 0; call < Math.max(ticks, 1) && remaining > 0; call++) {
            int drawn = neighbour.extractEnergy(offerable(remaining), false);
            if (drawn <= 0) {
                return;
            }
            energy.generate(drawn);
            remaining -= drawn;
            wake();
        }
    }

    private void autoIoItems(ServerLevel level, BlockPos pos, Direction facing) {
        IItemHandler outputs = sideConfig.autoPush(TransferKind.ITEM) ? autoOutputHandler() : null;
        IItemHandler inputs = sideConfig.autoPull(TransferKind.ITEM) ? autoInputHandler() : null;
        if (outputs == null && inputs == null) {
            return;
        }
        for (Direction side : Direction.values()) {
            IoMode mode = sideConfig.get(TransferKind.ITEM, facing, side);
            if (!mode.isActive()) {
                continue;
            }
            IItemHandler neighbour = itemNeighbour(level, pos, side);
            if (neighbour == null) {
                continue;
            }
            if (mode.canOutput() && outputs != null) {
                pushItems(outputs, neighbour);
            }
            if (mode.canInput() && inputs != null) {
                pullItems(inputs, neighbour);
            }
        }
    }

    private void pushItems(IItemHandler from, IItemHandler to) {
        Movers.pushItems(from, to);
    }

    private void pullItems(IItemHandler into, IItemHandler from) {
        if (Movers.pullItems(into, from)) {
            wake();
        }
    }

    private @Nullable IEnergyStorage energyNeighbour(ServerLevel level, BlockPos pos, Direction side) {
        int index = side.ordinal();
        BlockCapabilityCache<IEnergyStorage, @Nullable Direction> cache = energyNeighbours[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(
                    Capabilities.EnergyStorage.BLOCK, level, pos.relative(side), side.getOpposite());
            energyNeighbours[index] = cache;
        }
        return cache.getCapability();
    }

    /** Cached like the energy one: a machine running flat out looks its neighbours up every tick. */
    private @Nullable IItemHandler itemNeighbour(ServerLevel level, BlockPos pos, Direction side) {
        int index = side.ordinal();
        BlockCapabilityCache<IItemHandler, @Nullable Direction> cache = itemNeighbours[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(
                    Capabilities.ItemHandler.BLOCK, level, pos.relative(side), side.getOpposite());
            itemNeighbours[index] = cache;
        }
        return cache.getCapability();
    }

    // ------------------------------------------------------------------ capabilities

    /** The energy view a given face exposes, or null if that face is disabled for energy. */
    public @Nullable IEnergyStorage energyForSide(@Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        IoMode mode = sideConfig.get(TransferKind.ENERGY, facing(getBlockState()), side);
        return mode.isActive() ? new SidedEnergyWrapper(energy, mode) : null;
    }

    /** The inventory view a given face exposes, or null if that face moves no items. */
    public @Nullable IItemHandler itemsForSide(@Nullable Direction side) {
        IItemHandler machineInputs = autoInputHandler();
        IItemHandler machineOutputs = autoOutputHandler();
        if (machineInputs == null && machineOutputs == null) {
            return null;
        }
        if (side == null) {
            return new SidedItemHandler(machineInputs, machineOutputs);
        }
        IoMode mode = sideConfig.get(TransferKind.ITEM, facing(getBlockState()), side);
        if (!mode.isActive()) {
            return null;
        }
        SidedItemHandler view = new SidedItemHandler(
                mode.canInput() ? machineInputs : null,
                mode.canOutput() ? machineOutputs : null);
        return view.isEmptyView() ? null : view;
    }

    /** The tank view a given face exposes, or null if the machine has none or that face moves no fluid. */
    public @Nullable IFluidHandler fluidsForSide(@Nullable Direction side) {
        IFluidHandler tank = fluidTank();
        if (tank == null) {
            return null;
        }
        if (side == null) {
            return tank;
        }
        IoMode mode = sideConfig.get(TransferKind.FLUID, facing(getBlockState()), side);
        return mode.isActive() ? new SidedFluidHandler(tank, mode) : null;
    }

    protected Direction facing(BlockState state) {
        return state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    // ------------------------------------------------------------------ state changes

    private void applyTuningIfChanged() {
        MachineTuning current = tuning();
        if (current != appliedTuning) {
            appliedTuning = current;
            applyUpgrades();
        }
    }

    private void onUpgradesChanged() {
        applyUpgrades();
        wake();
        setChanged();
    }

    /**
     * Recomputes buffer size and throughput from the installed upgrades and the current config.
     * Runs on upgrade changes, on load, and whenever the tuning is rebuilt after a config reload —
     * which is what lets a reload reach machines already in the world.
     *
     * <p>Energy upgrades scale both by their own factor. The batch and the tier scale them too:
     * a machine batching twenty-seven at four times the speed draws that many times the FE, so
     * the buffer holds the same seconds of work as a bare one and the faces can still feed it.
     * Generators and the storage blocks batch one at no tier, so nothing changes for them.
     */
    protected void applyUpgrades() {
        MachineTuning tuning = tuning();
        int installed = energyUpgrades();
        double work = workFactor();
        long capacity = (long) Math.min(Long.MAX_VALUE, Math.round(tuning.capacity(baseCapacity.getAsLong(), installed) * work));
        int rate = (int) Math.min(Integer.MAX_VALUE, Math.round(tuning.transferRate(baseTransferRate.getAsInt(), installed) * work));
        energy.setLimits(capacity, rate, rate);
    }

    /** How many times a bare machine's FE this one draws at base speed: batch times tier speed. */
    public double workFactor() {
        return maxBatch() * tier().speedMultiplier();
    }

    private void onEnergyChanged() {
        setChanged();
    }

    /**
     * Tells the world that this machine's faces now expose different capabilities, so
     * neighbours re-resolve theirs instead of holding a stale view.
     */
    public void invalidateCapabilitiesOnSideChange() {
        if (level != null && !level.isClientSide) {
            level.invalidateCapabilities(getBlockPos());
            wake();
            setChanged();
            syncToClients();
        }
    }

    /** Called by the block when a neighbour changes, so cached redstone state stays current. */
    public void onNeighbourChanged(boolean nowPowered) {
        if (nowPowered != powered) {
            powered = nowPowered;
            setChanged();
        }
        wake();
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    protected void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            setChanged();
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag energyTag = new CompoundTag();
        energy.save(energyTag);
        tag.put(KEY_ENERGY, energyTag);
        tag.put(KEY_UPGRADES, upgrades.serializeNBT(registries));
        tag.put(KEY_TIER, tierSlot.serializeNBT(registries));
        tag.put(KEY_SIDES, sideConfig.save());
        tag.putByte(KEY_REDSTONE, (byte) redstoneMode.ordinal());
        tag.putInt(KEY_OVERCLOCK, overclock.workedTicks());
        tag.putBoolean(KEY_WORKING, working);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_UPGRADES)) {
            upgrades.deserializeNBT(registries, tag.getCompound(KEY_UPGRADES));
        }
        if (tag.contains(KEY_TIER)) {
            tierSlot.deserializeNBT(registries, tag.getCompound(KEY_TIER));
        }
        if (tag.contains(KEY_SIDES)) {
            sideConfig.load(tag.getCompound(KEY_SIDES));
        }
        redstoneMode = RedstoneMode.byOrdinal(tag.getByte(KEY_REDSTONE));
        overclock.setWorkedTicks(tag.getInt(KEY_OVERCLOCK));
        working = tag.getBoolean(KEY_WORKING);

        // Capacity depends on the upgrades, so size the buffer before restoring its contents.
        applyUpgrades();
        if (tag.contains(KEY_ENERGY)) {
            energy.load(tag.getCompound(KEY_ENERGY));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Called once, just before the machine block leaves the world for good.
     *
     * <p>Unlike {@code setRemoved()}, this does not fire on chunk unload, so it is the right
     * place to undo anything a machine did to its neighbours — the neighbours are still loaded
     * and the change is meant to be permanent.
     */
    public void onRemovedFromWorld(Level level, BlockPos pos) {
    }

    /** Drops the upgrade slots and the tier when the machine is broken. */
    public void dropContents(Level level, BlockPos pos) {
        ItemStack tier = tierSlot.getStackInSlot(0);
        if (!tier.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), tier);
        }
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }
}
