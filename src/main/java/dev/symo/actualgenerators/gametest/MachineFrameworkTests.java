package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.CorrosionCellBlockEntity;
import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlock;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.menu.CorrosionCellMenu;
import dev.symo.actualgenerators.menu.EnergyInjectorMenu;
import dev.symo.actualgenerators.logistics.LinkPortBlock;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.menu.FilterMenu;
import dev.symo.actualgenerators.menu.FluxCouplerMenu;
import dev.symo.actualgenerators.menu.LinkPortMenu;
import dev.symo.actualgenerators.logistics.NetworkOverview;
import dev.symo.actualgenerators.menu.NetworkOverviewMenu;
import dev.symo.actualgenerators.menu.NetworkPickerMenu;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.menu.MachineMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.machine.MachineEnergyStorage;
import dev.symo.actualgenerators.machine.MachineTier;
import dev.symo.actualgenerators.machine.MachineTuning;
import dev.symo.actualgenerators.machine.OverclockState;
import dev.symo.actualgenerators.machine.RedstoneMode;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.SidedEnergyWrapper;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeInventory;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.processing.ResonanceCrusherBlockEntity;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Covers the machine framework's promises: the speed ceiling, overclocks scaling past it,
 * superlinear energy cost, and — the mechanic the whole design hangs on — an overclock that
 * survives recipe boundaries and only decays when a machine is idle or unpowered.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class MachineFrameworkTests {
    private static final String EMPTY = "empty";
    private static final String PLATFORM = "platform";
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final double EPSILON = 1.0e-6;

    private MachineFrameworkTests() {
    }

    // ------------------------------------------------------------------ upgrades and scaling

    @GameTest(template = EMPTY)
    public static void speedUpgradesStopAtTheCeiling(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;

        assertNear(helper, 1.0, tuning.speedMultiplier(0), "an unupgraded machine runs at base speed");
        assertNear(helper, 3.0, tuning.speedMultiplier(4), "four speed upgrades reach the ceiling");
        assertNear(helper, 3.0, tuning.speedMultiplier(64), "extra speed upgrades do nothing past the ceiling");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void overclockScalesPastTheSpeedCeiling(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        double ceiling = tuning.speedMultiplier(tuning.maxSpeedUpgrades());

        double cold = tuning.totalSpeedMultiplier(4, 4, 0.0);
        double hot = tuning.totalSpeedMultiplier(4, 4, 1.0);

        assertNear(helper, ceiling, cold, "a cold machine is capped at the speed ceiling");
        helper.assertTrue(hot > ceiling, "a ramped-up overclock must beat the speed ceiling");
        assertNear(helper, 9.0, hot, "four speed plus four overclock upgrades reach 9x");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void energyCostRisesSuperlinearly(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        int base = 100;

        int atBase = tuning.energyPerTick(base, 1.0);
        int atDouble = tuning.energyPerTick(base, 2.0);
        int atMax = tuning.energyPerTick(base, 9.0);

        helper.assertValueEqual(atBase, base, "base speed costs the base rate");
        helper.assertValueEqual(atDouble, base * 4, "double speed costs four times the power");
        helper.assertValueEqual(atMax, base * 81, "9x speed costs 81x the power");
        helper.assertTrue(atDouble > atBase * 2, "energy cost must be superlinear, not proportional");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void speedShortensOperations(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;

        helper.assertValueEqual(tuning.ticksForOperation(200, 1.0), 200, "base speed takes the base time");
        helper.assertValueEqual(tuning.ticksForOperation(200, 2.0), 100, "double speed halves the time");
        helper.assertTrue(tuning.ticksForOperation(1, 9.0) >= 1, "an operation never takes less than a tick");

        helper.succeed();
    }

    // ------------------------------------------------------------------ batching

    @GameTest(template = EMPTY)
    public static void stackUpgradesRaiseBatchSize(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;

        helper.assertValueEqual(tuning.maxBatch(0), 1, "an unupgraded machine processes one item at a time");
        helper.assertValueEqual(tuning.maxBatch(4), 9, "four stack upgrades process nine at a time");
        helper.assertValueEqual(tuning.maxBatch(64), 9, "batch size is capped, so one machine cannot hog a tick");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void batchCostsEnergyLinearly(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        int base = 100;

        helper.assertValueEqual(tuning.energyPerTick(base, 1.0, 1), base, "a batch of one costs the base rate");
        helper.assertValueEqual(tuning.energyPerTick(base, 1.0, 2), base * 2, "twice the batch costs twice the power");
        helper.assertValueEqual(tuning.energyPerTick(base, 1.0, 9), base * 9, "nine items cost nine times the power");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void batchingIsCheaperThanSpeedForTheSameThroughput(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        int base = 100;

        // Four times the throughput, bought two different ways.
        int viaBatch = tuning.energyPerTick(base, 1.0, 4);
        int viaSpeed = tuning.energyPerTick(base, 4.0, 1);

        helper.assertValueEqual(viaBatch, base * 4, "batching four items costs four times the power");
        helper.assertValueEqual(viaSpeed, base * 16, "running four times as fast costs sixteen times the power");
        helper.assertTrue(viaBatch < viaSpeed,
                "batching must be the efficient path and overclocking the expensive one");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void machinesNeverResolveMoreThanOneOperationPerTick(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;

        // However absurd the multiplier, the tick is the floor. This is what bounds the cost of
        // a machine to the server; throughput past this point comes from batching instead.
        helper.assertValueEqual(tuning.ticksForOperation(200, 1_000_000.0), 1,
                "an operation can never take less than one tick");
        helper.assertValueEqual(tuning.ticksForOperation(1, 9.0), 1,
                "a one-tick recipe stays at one tick");

        helper.assertTrue(!tuning.isSpeedSaturated(200, 9.0),
                "9x speed on a 200 tick recipe still has headroom");
        helper.assertTrue(tuning.isSpeedSaturated(200, 200.0),
                "once speed matches the recipe length the machine is saturated");
        helper.assertTrue(tuning.isSpeedSaturated(200, 400.0),
                "speed beyond saturation is wasted, and should be surfaced to the player");

        helper.succeed();
    }

    // ------------------------------------------------------------------ the overclock ramp

    @GameTest(template = EMPTY)
    public static void overclockReachesFullAfterTheConfiguredWork(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        OverclockState overclock = new OverclockState();

        for (int tick = 0; tick < tuning.overclockRampTicks(); tick++) {
            overclock.tick(true, tuning);
        }

        helper.assertTrue(overclock.isRampedUp(tuning), "the ramp should be full after exactly the configured ticks");
        assertNear(helper, 1.0, overclock.progress(tuning), "a full ramp reads as 1.0");

        overclock.tick(true, tuning);
        assertNear(helper, 1.0, overclock.progress(tuning), "the ramp must not climb past full");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void overclockSurvivesRecipeBoundaries(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        OverclockState overclock = new OverclockState();

        // Work through several "recipes" back to back. Nothing here signals a recipe boundary,
        // because a boundary must not mean anything to the ramp.
        for (int recipe = 0; recipe < 5; recipe++) {
            double before = overclock.progress(tuning);
            for (int tick = 0; tick < 100; tick++) {
                overclock.tick(true, tuning);
            }
            helper.assertTrue(overclock.progress(tuning) > before,
                    "the ramp must keep climbing across recipe boundaries, never reset");
        }

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void overclockDecaysOnlyWhenThereIsNoWork(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        OverclockState overclock = new OverclockState();

        for (int tick = 0; tick < tuning.overclockRampTicks(); tick++) {
            overclock.tick(true, tuning);
        }
        double full = overclock.progress(tuning);

        // Still working: the ramp holds.
        for (int tick = 0; tick < 200; tick++) {
            overclock.tick(true, tuning);
        }
        assertNear(helper, full, overclock.progress(tuning), "a busy machine never cools down");

        // Out of work: it falls.
        for (int tick = 0; tick < 100; tick++) {
            overclock.tick(false, tuning);
        }
        helper.assertTrue(overclock.progress(tuning) < full, "an idle machine must cool down");

        // And all the way to cold within the configured decay window.
        for (int tick = 0; tick < tuning.overclockDecayTicks(); tick++) {
            overclock.tick(false, tuning);
        }
        helper.assertTrue(overclock.isCold(), "the ramp should reach cold within the decay window");

        helper.succeed();
    }

    // ------------------------------------------------------------------ side configuration

    @GameTest(template = EMPTY)
    public static void sideConfigFollowsTheMachineFacing(GameTestHelper helper) {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.set(TransferKind.ENERGY, RelativeSide.BACK, IoMode.OUTPUT);

        // "Output from the back" must mean the back whichever way the machine points.
        helper.assertValueEqual(config.get(TransferKind.ENERGY, Direction.NORTH, Direction.SOUTH), IoMode.OUTPUT,
                "a machine facing north outputs to the south");
        helper.assertValueEqual(config.get(TransferKind.ENERGY, Direction.EAST, Direction.WEST), IoMode.OUTPUT,
                "a machine facing east outputs to the west");
        helper.assertValueEqual(config.get(TransferKind.ENERGY, Direction.NORTH, Direction.NORTH), IoMode.DISABLED,
                "the front stays disabled");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sideConfigRoundTripsThroughASnapshot(GameTestHelper helper) {
        SideConfig original = SideConfig.of(IoMode.INPUT, IoMode.DISABLED, IoMode.BOTH);
        original.set(TransferKind.ITEM, RelativeSide.TOP, IoMode.OUTPUT);
        original.set(TransferKind.FLUID, RelativeSide.LEFT, IoMode.INPUT);

        SideConfig pasted = SideConfig.empty();
        pasted.loadOrdinals(original.toOrdinals());

        helper.assertTrue(original.equals(pasted), "a copied configuration must paste back identically");

        // A snapshot of the wrong size is ignored rather than half-applied.
        SideConfig untouched = SideConfig.of(IoMode.INPUT, IoMode.INPUT, IoMode.INPUT);
        SideConfig expected = untouched.copy();
        untouched.loadOrdinals(java.util.List.of(1, 2, 3));
        helper.assertTrue(untouched.equals(expected), "a malformed snapshot must not be partially applied");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sideConfigSurvivesSaveAndLoad(GameTestHelper helper) {
        SideConfig original = SideConfig.of(IoMode.BOTH, IoMode.OUTPUT, IoMode.INPUT);
        original.set(TransferKind.ENERGY, RelativeSide.BOTTOM, IoMode.DISABLED);

        SideConfig loaded = SideConfig.empty();
        loaded.load(original.save());

        helper.assertTrue(original.equals(loaded), "side configuration must survive a save/load round trip");
        helper.succeed();
    }

    // ------------------------------------------------------------------ redstone

    @GameTest(template = EMPTY)
    public static void redstoneModesGateRunning(GameTestHelper helper) {
        helper.assertTrue(RedstoneMode.ALWAYS.canRun(true) && RedstoneMode.ALWAYS.canRun(false),
                "ALWAYS ignores redstone");
        helper.assertTrue(RedstoneMode.WITH_SIGNAL.canRun(true) && !RedstoneMode.WITH_SIGNAL.canRun(false),
                "WITH_SIGNAL needs power");
        helper.assertTrue(!RedstoneMode.WITHOUT_SIGNAL.canRun(true) && RedstoneMode.WITHOUT_SIGNAL.canRun(false),
                "WITHOUT_SIGNAL needs no power");
        helper.assertTrue(!RedstoneMode.NEVER.canRun(true) && !RedstoneMode.NEVER.canRun(false),
                "NEVER is a hard off switch");

        helper.succeed();
    }

    // ------------------------------------------------------------------ energy buffer

    @GameTest(template = EMPTY)
    public static void energyBufferRespectsItsTransferRate(GameTestHelper helper) {
        MachineEnergyStorage storage = new MachineEnergyStorage(10_000, 100, 100, () -> {
        });

        helper.assertValueEqual(storage.receiveEnergy(1_000, false), 100, "a single insert is capped by the rate");
        helper.assertValueEqual(storage.getEnergyStored(), 100, "only the accepted energy is stored");

        helper.assertValueEqual(storage.extractEnergy(1_000, true), 100, "a simulated extract is capped too");
        helper.assertValueEqual(storage.getEnergyStored(), 100, "simulating must not move energy");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void internalTransfersIgnoreTheTransferRate(GameTestHelper helper) {
        MachineEnergyStorage storage = new MachineEnergyStorage(10_000, 100, 100, () -> {
        });

        // A generator's own output is not throttled by what its faces can push.
        helper.assertValueEqual(storage.generate(5_000), 5_000L, "internal generation ignores the transfer rate");
        helper.assertValueEqual(storage.consume(4_000), 4_000L, "internal consumption ignores the transfer rate");
        helper.assertValueEqual(storage.getEnergyStored(), 1_000, "the buffer tracks both");

        helper.assertValueEqual(storage.consume(9_999), 1_000L, "consuming cannot go below empty");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void shrinkingCapacityDoesNotDuplicateEnergy(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        long baseCapacity = 10_000;

        long upgraded = tuning.capacity(baseCapacity, 4);
        helper.assertValueEqual(upgraded, 50_000L, "four energy upgrades give five times the buffer");

        MachineEnergyStorage storage = new MachineEnergyStorage(upgraded, 1_000, 1_000, () -> {
        });
        storage.generate(upgraded);
        helper.assertValueEqual(storage.stored(), upgraded, "the upgraded buffer fills");

        // Pulling the upgrades back out must spill the excess, not keep it around.
        storage.setLimits(baseCapacity, 1_000, 1_000);
        helper.assertValueEqual(storage.stored(), baseCapacity, "energy is clamped to the smaller buffer");
        helper.assertValueEqual(storage.capacity(), baseCapacity, "capacity actually shrank");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void facesOnlyAllowWhatTheyAreConfiguredFor(GameTestHelper helper) {
        MachineEnergyStorage storage = new MachineEnergyStorage(10_000, 100, 100, () -> {
        });
        storage.generate(5_000);

        SidedEnergyWrapper outputFace = new SidedEnergyWrapper(storage, IoMode.OUTPUT);
        helper.assertValueEqual(outputFace.receiveEnergy(100, false), 0, "an output face refuses incoming energy");
        helper.assertValueEqual(outputFace.extractEnergy(100, false), 100, "an output face gives energy out");
        helper.assertTrue(!outputFace.canReceive(), "an output face reports that it cannot receive");

        SidedEnergyWrapper inputFace = new SidedEnergyWrapper(storage, IoMode.INPUT);
        helper.assertValueEqual(inputFace.extractEnergy(100, false), 0, "an input face refuses to give energy out");
        helper.assertValueEqual(inputFace.receiveEnergy(100, false), 100, "an input face accepts energy");

        SidedEnergyWrapper both = new SidedEnergyWrapper(storage, IoMode.BOTH);
        helper.assertTrue(both.canReceive() && both.canExtract(), "a face set to both does both");

        helper.succeed();
    }

    // ------------------------------------------------------------------ upgrade slots

    @GameTest(template = EMPTY)
    public static void upgradeSlotsStopAtWhatTheMachineCounts(GameTestHelper helper) {
        int ceiling = MachineTuning.DEFAULT.maxUpgrades(UpgradeType.SPEED);
        UpgradeInventory upgrades = new UpgradeInventory(type -> true, type -> ceiling, () -> {
        });

        int speedSlot = UpgradeType.SPEED.ordinal();
        ItemStack wholeStack = new ItemStack(ModItems.SPEED_UPGRADE.get(), 64);
        ItemStack refused = upgrades.insertItem(speedSlot, wholeStack, false);

        helper.assertValueEqual(upgrades.getStackInSlot(speedSlot).getCount(), ceiling,
                "the slot should take only the upgrades the machine will count");
        helper.assertValueEqual(refused.getCount(), 64 - ceiling, "the rest belongs back in the player's hand");
        helper.assertValueEqual(upgrades.count(UpgradeType.SPEED), ceiling, "and that is what the machine sees");

        helper.assertTrue(upgrades.insertItem(speedSlot, new ItemStack(ModItems.SPEED_UPGRADE.get()), false)
                        .getCount() == 1,
                "a full slot takes no more");

        helper.succeed();
    }

    /**
     * The cap has to hold on the path a player actually uses. Clicking a stack into a slot never
     * calls {@code insertItem} — the slot sizes the click itself and writes the stack in — so
     * testing the handler alone would miss a slot that still swallowed all sixty-four.
     */
    @GameTest(template = EMPTY)
    public static void clickingAStackInIsCappedToo(GameTestHelper helper) {
        int ceiling = MachineTuning.DEFAULT.maxUpgrades(UpgradeType.SPEED);
        UpgradeInventory upgrades = new UpgradeInventory(type -> true, type -> ceiling, () -> {
        });
        SlotItemHandler slot = new SlotItemHandler(upgrades, UpgradeType.SPEED.ordinal(), 0, 0);

        ItemStack carried = new ItemStack(ModItems.SPEED_UPGRADE.get(), 64);
        ItemStack leftInHand = slot.safeInsert(carried, carried.getCount());

        helper.assertValueEqual(slot.getItem().getCount(), ceiling,
                "clicking a full stack in should leave only what the machine counts");
        helper.assertValueEqual(leftInHand.getCount(), 64 - ceiling, "the rest stays on the cursor");
        helper.assertValueEqual(slot.getMaxStackSize(carried), ceiling,
                "and the slot should report the ceiling, since that is what sizes the click");

        helper.succeed();
    }

    // ------------------------------------------------------------------ the screen's numbers

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void bigEnergyNumbersSurviveTheSyncToTheScreen(GameTestHelper helper) {
        // Container data is sent as a signed 16-bit value, so anything past 32,767 has to travel
        // as a pair. A corrosion cell's buffer is 400,000, which is exactly the trap.
        helper.setBlock(MACHINE, ModBlocks.CORROSION_CELL.get());
        if (!(helper.getBlockEntity(MACHINE) instanceof CorrosionCellBlockEntity cell)) {
            throw new IllegalStateException("no corrosion cell at " + MACHINE);
        }
        long capacity = cell.energyStorage().capacity();
        helper.assertTrue(capacity > Short.MAX_VALUE,
                "this test only proves anything with a buffer past the 16-bit line, got " + capacity);
        cell.energyStorage().setEnergy(capacity);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CorrosionCellMenu menu = new CorrosionCellMenu(1, player.getInventory(), cell);

        helper.assertValueEqual(menu.energyCapacity(), capacity,
                "the screen must see the real buffer size, not a wrapped short");
        helper.assertValueEqual(menu.energyStored(), capacity,
                "and the real charge in it");

        // And past the 32-bit line as well, which is the reason a buffer counts in longs at all.
        long vast = 9_000_000_000L;
        cell.energyStorage().setLimits(vast, 1_000, 1_000);
        cell.energyStorage().setEnergy(vast - 1);

        helper.assertValueEqual(menu.energyCapacity(), vast, "a buffer past an int survives the sync");
        helper.assertValueEqual(menu.energyStored(), vast - 1, "and so does the charge in it");

        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void theScreenReportsWhatTheMachineIsEarning(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.CORROSION_CELL.get());
        if (!(helper.getBlockEntity(MACHINE) instanceof CorrosionCellBlockEntity cell)) {
            throw new IllegalStateException("no corrosion cell at " + MACHINE);
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CorrosionCellMenu idle = new CorrosionCellMenu(1, player.getInventory(), cell);
        helper.assertValueEqual(idle.energyRate(), 0, "an empty cell is earning nothing");

        cell.inputHandler().insertItem(0, new ItemStack(Items.COPPER_BLOCK, 4), false);
        CorrosionCellMenu loaded = new CorrosionCellMenu(2, player.getInventory(), cell);
        helper.assertTrue(loaded.energyRate() > 0, "a loaded cell should report a live FE/t");
        helper.assertValueEqual(loaded.energyRate(), cell.displayedEnergyRate(),
                "and it should be the machine's own number");

        helper.succeed();
    }

    // ------------------------------------------------------------------ installing without the screen

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void crouchingWithAnUpgradeInstallsItWithoutOpeningAnything(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.CORROSION_CELL.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        int ceiling = MachineTuning.DEFAULT.maxUpgrades(UpgradeType.SPEED);
        ItemStack held = new ItemStack(ModItems.SPEED_UPGRADE.get(), 64);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        useOnMachine(helper, player, InteractionHand.MAIN_HAND);

        if (!(helper.getBlockEntity(MACHINE) instanceof CorrosionCellBlockEntity cell)) {
            throw new IllegalStateException("no corrosion cell at " + MACHINE);
        }
        helper.assertValueEqual(cell.speedUpgrades(), ceiling,
                "it should take as many as it counts, straight from the hand");
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 64 - ceiling,
                "and leave the rest in the hand rather than eating the stack");

        // A second try has nowhere to put them, and must not quietly swallow any.
        useOnMachine(helper, player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 64 - ceiling,
                "a full machine must refuse rather than consume");

        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void crouchingWithARefusedUpgradeChangesNothing(GameTestHelper helper) {
        // The hydrostatic generator burns no fuel, so it takes energy upgrades only.
        helper.setBlock(MACHINE, ModBlocks.HYDROSTATIC_GENERATOR.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SPEED_UPGRADE.get(), 8));

        useOnMachine(helper, player, InteractionHand.MAIN_HAND);

        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 8,
                "a refused upgrade stays in the hand");
        if (!(helper.getBlockEntity(MACHINE) instanceof MachineBlockEntity machine)) {
            throw new IllegalStateException("no machine at " + MACHINE);
        }
        helper.assertValueEqual(machine.speedUpgrades(), 0, "and never reaches the machine");

        helper.succeed();
    }

    /**
     * A tier upgrades the machine itself: operations get shorter and batches bigger, and the FE per
     * operation stays exactly what it was. That flat price is what separates a tier from an overclock.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aTierSpeedsAMachineUpAtAFlatPricePerOperation(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.RESONANCE_CRUSHER.get());
        if (!(helper.getBlockEntity(MACHINE) instanceof ResonanceCrusherBlockEntity crusher)) {
            throw new IllegalStateException("no crusher at " + MACHINE);
        }
        int baseTicks = 100;
        int ticksBefore = crusher.ticksForOperation(baseTicks);
        int batchBefore = crusher.maxBatch();
        long pricePerOperation = (long) crusher.currentEnergyPerTick(1) * ticksBefore;

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.IRON_TIER_UPGRADE.toStack());
        useOnMachine(helper, player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(crusher.tier(), MachineTier.IRON, "a crouch-use installs the tier");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "and spends the item");

        // A higher tier swaps in and hands the lower one back; a lower one is refused.
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.NETHERITE_TIER_UPGRADE.toStack());
        useOnMachine(helper, player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(crusher.tier(), MachineTier.NETHERITE, "netherite over iron goes in");
        helper.assertValueEqual(player.getInventory().countItem(ModItems.IRON_TIER_UPGRADE.get()), 1,
                "and the iron tier comes back to the player");
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.GOLD_TIER_UPGRADE.toStack());
        useOnMachine(helper, player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(crusher.tier(), MachineTier.NETHERITE, "gold under netherite changes nothing");
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "and stays in the hand");

        double speed = MachineTier.NETHERITE.speedMultiplier();
        helper.assertValueEqual(crusher.ticksForOperation(baseTicks), (int) Math.ceil(ticksBefore / speed),
                "an operation takes the tier's share of the time");
        helper.assertValueEqual(crusher.maxBatch(), batchBefore * MachineTier.NETHERITE.batchMultiplier(),
                "and carries the tier's multiple of items");
        helper.assertValueEqual((long) crusher.currentEnergyPerTick(1) * crusher.ticksForOperation(baseTicks), pricePerOperation,
                "at the same FE per operation: faster, not hungrier");
        helper.succeed();
    }

    /** A generator has no fuel to trade for a tier's speed, so a tier on one would be free FE. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void generatorsRefuseTiers(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.CORROSION_CELL.get());
        if (!(helper.getBlockEntity(MACHINE) instanceof MachineBlockEntity cell)) {
            throw new IllegalStateException("no machine at " + MACHINE);
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.NETHERITE_TIER_UPGRADE.toStack());
        useOnMachine(helper, player, InteractionHand.MAIN_HAND);

        helper.assertTrue(!cell.acceptsTier(), "a generator takes no tier");
        helper.assertValueEqual(cell.tier(), MachineTier.NONE, "so the click installs nothing");
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "and the item stays in the hand");
        helper.assertTrue(!cell.tierSlot().isItemValid(0, ModItems.IRON_TIER_UPGRADE.toStack()), "nor does the slot take one");
        helper.succeed();
    }

    /** Crouch-uses whatever the player is holding on the test machine. */
    private static void useOnMachine(GameTestHelper helper, Player player, InteractionHand hand) {
        BlockPos worldPos = helper.absolutePos(MACHINE);
        BlockHitResult hit = new BlockHitResult(
                worldPos.getCenter(), Direction.UP, worldPos, false);
        player.getItemInHand(hand).useOn(new UseOnContext(player, hand, hit));
    }

    /**
     * The guardrail for window layout: nothing a machine window draws may sit on top of anything
     * else it draws, or hang off the edge.
     *
     * <p>This exists because it has happened twice — a readout written across a gauge, and a
     * column of slots laid over one — and both times a person had to notice. A screen cannot be
     * built on a server, so the fixed coordinates live in {@link MachineLayout}, the screens read
     * them from there, and this walks every machine's real slots against them.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void noWindowDrawsTwoThingsInTheSamePlace(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        for (DeferredBlock<? extends MachineBlock> block : List.of(
                ModBlocks.CORROSION_CELL,
                ModBlocks.HYDROSTATIC_GENERATOR,
                ModBlocks.PHOTOVORE,
                ModBlocks.IMPACT_DYNAMO,
                ModBlocks.SPAWNER_SIPHON,
                ModBlocks.ENCHANTMENT_COMBUSTOR,
                ModBlocks.RESONANCE_CRUSHER,
                ModBlocks.SURGE_BANK, ModBlocks.CRYSTAL_CHARGER, ModBlocks.ENERGY_INJECTOR)) {
            helper.setBlock(MACHINE, block.get());
            if (!(helper.getBlockEntity(MACHINE) instanceof MenuProvider provider)) {
                throw new IllegalStateException("no menu provider at " + MACHINE + " for " + block.getId());
            }
            if (!(provider.createMenu(1, player.getInventory(), player) instanceof MachineMenu<?> menu)) {
                throw new IllegalStateException(block.getId() + " does not open a machine menu");
            }
            List<MachineLayout.Box> chrome = new ArrayList<>(MachineLayout.chrome());
            if (menu.hasRamp()) {
                chrome.add(MachineLayout.ramp());
            }
            if (menu.hasGauge()) {
                chrome.add(MachineLayout.gauge());
            }
            if (menu.hasModeButton()) {
                chrome.add(MachineLayout.mode());
            }
            if (menu.hasProgressArrow()) {
                chrome.add(MachineLayout.arrow());
            }
            if (menu instanceof EnergyInjectorMenu) {
                chrome.add(EnergyInjectorMenu.NETWORK_BUTTON);
            }
            assertNothingOverlaps(helper, block.getId().getPath(), chrome, menu.slots);
        }

        // The coupler is an item, not a machine, but it borrows the same window and the rule is
        // about windows: two things in one place is a bug wherever it is drawn.
        player.getInventory().setItem(0, ModItems.FLUX_COUPLER.toStack());
        FluxCouplerMenu coupler = new FluxCouplerMenu(1, player.getInventory(), 0);
        assertNothingOverlaps(helper, "flux_coupler", FluxCouplerMenu.chrome(), coupler.slots);

        // And so does the logic port, which is a block but not a machine. Its window has the most
        // buttons of any of them, so it is the one most likely to grow into something.
        BlockPos pad = MACHINE.above();
        helper.setBlock(pad, ModBlocks.LOGIC_PORT.get().defaultBlockState()
                .setValue(LinkPortBlock.FACE_PROPERTIES.get(Direction.DOWN), true));
        if (!(helper.getBlockEntity(pad) instanceof LinkPortBlockEntity port)) {
            throw new IllegalStateException("no logic port at " + pad);
        }
        port.addFace(Direction.DOWN);
        LinkPortMenu portMenu = new LinkPortMenu(1, player.getInventory(), port, Direction.DOWN);
        // Sixteen filter slots share one spot and only the chosen channel's is active, so the
        // check sees the window as it is drawn.
        assertNothingOverlaps(helper, "logic_port", LinkPortMenu.chrome(),
                portMenu.slots.stream().filter(Slot::isActive).toList(),
                LinkPortMenu.WIDTH + LinkPortMenu.BAY_WIDTH, LinkPortMenu.HEIGHT);

        // The filter item's window and the tool's picker are windows too.
        player.getInventory().setItem(0, ModItems.FILTER.toStack());
        FilterMenu filter = new FilterMenu(1, player.getInventory(), 0);
        assertNothingOverlaps(helper, "filter", FilterMenu.chrome(), filter.slots, FilterMenu.WIDTH, FilterMenu.HEIGHT);
        assertNothingOverlaps(helper, "filter_adding", FilterMenu.addingChrome(), filter.slots, FilterMenu.WIDTH, FilterMenu.HEIGHT);
        assertNothingOverlaps(helper, "filter_item_entry", FilterMenu.itemEntryChrome(), filter.slots,
                FilterMenu.WIDTH, FilterMenu.HEIGHT);
        assertNothingOverlaps(helper, "filter_tag_entry", FilterMenu.tagEntryChrome(), filter.slots,
                FilterMenu.WIDTH, FilterMenu.HEIGHT);
        NetworkPickerMenu picker = new NetworkPickerMenu(1, player.getInventory(), pad, Direction.DOWN,
                NetworkPickerMenu.Snapshot.EMPTY, false);
        assertNothingOverlaps(helper, "network_picker", NetworkPickerMenu.chrome(), picker.slots,
                NetworkPickerMenu.WIDTH, NetworkPickerMenu.HEIGHT);
        NetworkOverviewMenu overview = new NetworkOverviewMenu(1, player.getInventory(), pad, Direction.DOWN, false,
                NetworkOverview.EMPTY);
        assertNothingOverlaps(helper, "network_overview", NetworkOverviewMenu.chrome(), overview.slots,
                NetworkOverviewMenu.WIDTH, NetworkOverviewMenu.HEIGHT);

        helper.succeed();
    }

    private static void assertNothingOverlaps(GameTestHelper helper, String machine,
                                              List<MachineLayout.Box> chrome, List<Slot> slots) {
        assertNothingOverlaps(helper, machine, chrome, slots, MachineLayout.WIDTH, MachineLayout.HEIGHT);
    }

    private static void assertNothingOverlaps(GameTestHelper helper, String machine,
                                              List<MachineLayout.Box> chrome, List<Slot> slots,
                                              int windowWidth, int windowHeight) {
        List<MachineLayout.Box> boxes = new ArrayList<>(chrome);
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            boxes.add(MachineLayout.slotFrame("slot " + index, slot.x, slot.y));
        }

        for (MachineLayout.Box box : boxes) {
            helper.assertTrue(box.fitsInWindow(windowWidth, windowHeight),
                    machine + ": " + box.name() + " hangs off the window at " + box.x() + "," + box.y());
        }
        for (int a = 0; a < boxes.size(); a++) {
            for (int b = a + 1; b < boxes.size(); b++) {
                helper.assertTrue(!boxes.get(a).overlaps(boxes.get(b)),
                        machine + ": " + boxes.get(a).name() + " overlaps " + boxes.get(b).name());
            }
        }
    }

    @GameTest(template = EMPTY)
    public static void aBankedRampStaysPinnedAtTheTopInsteadOfWrappingRound(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;
        OverclockState ramp = new OverclockState();
        ramp.setWorkedTicks(Integer.MAX_VALUE);

        ramp.tick(true, tuning);
        helper.assertValueEqual((int) Math.round(ramp.progress(tuning) * 1000), 1000,
                "another tick of work on a full ramp must leave it full, not overflow it to cold");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aWarmupRampReachesTheRatingAndStopsThere(GameTestHelper helper) {
        MachineTuning tuning = MachineTuning.DEFAULT;

        helper.assertTrue(tuning.warmupMultiplier(0.0) > 0.0 && tuning.warmupMultiplier(0.0) < 1.0,
                "a cold warm-up generator earns something, but less than its rating");
        helper.assertTrue(tuning.warmupMultiplier(0.5) > tuning.warmupMultiplier(0.0),
                "and more the further up the ramp it is");
        helper.assertValueEqual((int) Math.round(tuning.warmupMultiplier(1.0) * 1000), 1000,
                "a fully warmed one earns exactly its rating");
        helper.assertTrue(tuning.warmupMultiplier(4.0) <= 1.0,
                "and nothing past the top of the ramp adds anything, or the world would print FE");

        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyUpgradeHasItsOwnSlot(GameTestHelper helper) {
        UpgradeInventory upgrades = new UpgradeInventory(type -> type != UpgradeType.STACK, type -> 4, () -> {
        });

        helper.assertValueEqual(upgrades.getSlots(), UpgradeType.all().length,
                "one slot per upgrade type, whatever the machine accepts");

        for (UpgradeType type : UpgradeType.all()) {
            ItemStack stack = ModItems.upgradeItem(type).toStack();
            helper.assertValueEqual(UpgradeInventory.typeOf(type.ordinal()), type, "slots are indexed by type");

            boolean accepted = type != UpgradeType.STACK;
            helper.assertTrue(upgrades.isItemValid(type.ordinal(), stack) == accepted,
                    type + " should " + (accepted ? "fit" : "be refused by") + " its own slot");

            for (UpgradeType other : UpgradeType.all()) {
                if (other != type) {
                    helper.assertTrue(!upgrades.isItemValid(other.ordinal(), stack),
                            type + " must not fit the " + other + " slot");
                }
            }
        }

        helper.succeed();
    }

    /**
     * The transfer rate is FE per <em>tick</em>, and a machine has to deliver it whether the
     * transfer pass runs every tick or every tenth. Batching the passes and moving one tick's worth
     * each time silently divides every machine's rated throughput by the interval, which strands a
     * generator behind a buffer it cannot empty.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void aFullBufferEmptiesAtTheRatedRateNotTheTransferInterval(GameTestHelper helper) {
        BlockPos receiver = MACHINE.east();
        int ticks = 60;

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(MACHINE, ModBlocks.CORROSION_CELL.get());
                    helper.setBlock(receiver, ModBlocks.SURGE_BANK.get());
                    // Pushing is opt-in, so this is the setup a player would have made.
                    machine(helper, MACHINE).sideConfig().setAuto(TransferKind.ENERGY, true, true);
                    machine(helper, MACHINE).energyStorage()
                            .setEnergy(machine(helper, MACHINE).energyStorage().getMaxEnergyStored());
                })
                .thenIdle(ticks)
                .thenExecute(() -> {
                    MachineEnergyStorage source = machine(helper, MACHINE).energyStorage();
                    int rate = source.getMaxExtract();
                    int moved = machine(helper, receiver).energyStorage().getEnergyStored();

                    helper.assertTrue(source.getMaxEnergyStored() > rate * ticks,
                            "the buffer has to outlast the test, or it runs dry and proves nothing");

                    // Slack for the passes that fall outside the window, not for the interval.
                    int expected = rate * (ticks - 20);
                    helper.assertTrue(moved >= expected,
                            "a " + rate + " FE/t face should have moved about " + rate * ticks
                                    + " FE in " + ticks + " ticks, but moved " + moved);
                })
                .thenSucceed();
    }

    /**
     * The receiver's rate is per tick too, and the capability enforces it per call. A pass that
     * offered ten ticks' worth in one call moved a tenth of it into any machine whose rate was
     * below the lump: a 1,000 FE/t cell fed a 1,000 FE/t crusher at 100 FE/t, which read in the
     * world as "it only transfers every second". The bank above hid it with a 20,000 FE/t face.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void aMachineRatedBelowThePassIsStillFedAtItsOwnRate(GameTestHelper helper) {
        BlockPos receiver = MACHINE.east();
        int ticks = 60;

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(MACHINE, ModBlocks.CORROSION_CELL.get());
                    helper.setBlock(receiver, ModBlocks.RESONANCE_CRUSHER.get());
                    machine(helper, MACHINE).sideConfig().setAuto(TransferKind.ENERGY, true, true);
                    machine(helper, MACHINE).energyStorage()
                            .setEnergy(machine(helper, MACHINE).energyStorage().capacity());
                })
                .thenIdle(ticks)
                .thenExecute(() -> {
                    MachineEnergyStorage source = machine(helper, MACHINE).energyStorage();
                    MachineEnergyStorage sink = machine(helper, receiver).energyStorage();
                    int rate = Math.min(source.getMaxExtract(), sink.getMaxReceive());
                    long moved = sink.stored();

                    helper.assertTrue(source.capacity() > (long) rate * ticks && sink.capacity() > (long) rate * ticks,
                            "both buffers have to outlast the test, or it proves nothing");
                    helper.assertTrue(sink.getMaxReceive() * ServerConfig.autoIoIntervalTicks() > sink.getMaxReceive(),
                            "the receiver's per-call cap has to sit below a pass's worth, or the test proves nothing");

                    long expected = (long) rate * (ticks - 20);
                    helper.assertTrue(moved >= expected,
                            "a " + rate + " FE/t receiver should have taken about " + (long) rate * ticks
                                    + " FE in " + ticks + " ticks, but took " + moved);
                })
                .thenSucceed();
    }

    /**
     * A tiered, batching machine draws many times a bare one's FE, so its buffer and faces grow by
     * the same factor: it holds the same seconds of work, and can still be fed. Symo's ask after a
     * netherite crusher ran its hundred-thousand FE dry in a few seconds.
     */
    @GameTest(template = PLATFORM)
    public static void theBufferAndTheFacesFollowTheBatchAndTheTier(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.RESONANCE_CRUSHER.get());
        MachineBlockEntity machine = machine(helper, MACHINE);
        long bareCapacity = machine.energyStorage().capacity();
        int bareRate = machine.energyStorage().getMaxReceive();
        helper.assertValueEqual(bareCapacity, machine.baseCapacity(), "a bare machine holds its base buffer");

        machine.tierSlot().insertItem(0, ModItems.NETHERITE_TIER_UPGRADE.toStack(), false);
        machine.upgradeInventory().insertItem(UpgradeType.STACK.ordinal(), new ItemStack(ModItems.STACK_UPGRADE.get(), 64), false);
        double work = machine.workFactor();
        helper.assertTrue(work > 1.0, "a netherite tier and a stack row make it draw more, got x" + work);
        helper.assertValueEqual(machine.energyStorage().capacity(), Math.round(bareCapacity * work),
                "and the buffer grows by the same factor");
        helper.assertValueEqual(machine.energyStorage().getMaxReceive(), (int) Math.round(bareRate * work),
                "as do the faces, or nothing could feed it");

        machine.tierSlot().extractItem(0, 1, false);
        machine.upgradeInventory().extractItem(UpgradeType.STACK.ordinal(), 64, false);
        helper.assertValueEqual(machine.energyStorage().capacity(), bareCapacity, "and it all comes off again");
        helper.succeed();
    }

    /**
     * Auto-output and an output face are two different questions, and the panel now asks both. The
     * face says what is allowed through it; the flag says whether the machine goes looking for
     * somewhere to put things. Turning the flag off has to stop the pushing while leaving the face
     * open for anything that comes to pull.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void autoTransferCanBeTurnedOffWithoutClosingTheFace(GameTestHelper helper) {
        BlockPos receiver = MACHINE.east();

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(MACHINE, ModBlocks.CORROSION_CELL.get());
                    helper.setBlock(receiver, ModBlocks.SURGE_BANK.get());

                    MachineBlockEntity source = machine(helper, MACHINE);
                    source.energyStorage().setEnergy(source.energyStorage().capacity());
                    source.sideConfig().setAuto(TransferKind.ENERGY, true, false);
                    // The bank pulls of its own accord, which would move the energy anyway and
                    // prove nothing about the cell -- so it is told to sit still too.
                    machine(helper, receiver).sideConfig().setAuto(TransferKind.ENERGY, false, false);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertValueEqual(machine(helper, receiver).energyStorage().stored(), 0L,
                            "with auto-output off, the machine must not push into its neighbour");

                    IEnergyStorage face = machine(helper, MACHINE).energyForSide(Direction.EAST);
                    helper.assertTrue(face != null, "but the face itself stays open");
                    helper.assertValueEqual(face.extractEnergy(100, false), 100,
                            "and anything that comes to pull still gets what it asked for");

                    machine(helper, MACHINE).sideConfig().setAuto(TransferKind.ENERGY, true, true);
                    machine(helper, MACHINE).requestAutoIo();
                })
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(machine(helper, receiver).energyStorage().stored() > 0,
                        "and switching it back on starts the pushing again"))
                .thenSucceed();
    }

    /**
     * The item side of the same problem. A machine batching a stack an operation moves a stack a
     * tick at full speed, which a transfer pass every tenth tick cannot carry — so the pass has to
     * come forward whenever the slots can no longer cover another batch, in either direction.
     */
    @GameTest(template = EMPTY)
    public static void slotsThatCannotCoverAnotherBatchBringTheTransferPassForward(GameTestHelper helper) {
        int batch = 64;
        ItemStackHandler slots = new ItemStackHandler(1);

        slots.setStackInSlot(0, new ItemStack(Items.COPPER_BLOCK, batch));
        helper.assertTrue(MachineBlockEntity.outputIsBackedUp(slots, batch),
                "an output holding a stack has no room for another batch");
        helper.assertTrue(!MachineBlockEntity.inputIsShort(slots, batch),
                "while an input holding a stack can cover one");

        slots.setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(MachineBlockEntity.inputIsShort(slots, batch),
                "an empty input cannot cover a batch — the case the old half-full rule missed");
        helper.assertTrue(!MachineBlockEntity.outputIsBackedUp(slots, batch),
                "while an empty output has all the room there is");

        // A machine batching one item at a time keeps to the interval until a slot actually runs
        // out or actually fills, which is the behaviour that keeps idle machines cheap.
        slots.setStackInSlot(0, new ItemStack(Items.COPPER_BLOCK, 1));
        helper.assertTrue(!MachineBlockEntity.inputIsShort(slots, 1), "one item covers a batch of one");
        helper.assertTrue(!MachineBlockEntity.outputIsBackedUp(slots, 1), "and leaves room for one");

        helper.succeed();
    }

    /** A pickaxe that ate a player's upgrades would be a bug: a broken machine spills what it held. */
    @GameTest(template = PLATFORM)
    public static void breakingAMachineStillDropsWhatWasInside(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.RESONANCE_CRUSHER.get());
        machine(helper, MACHINE).upgradeInventory()
                .insertItem(UpgradeType.ENERGY.ordinal(), ModItems.ENERGY_UPGRADE.toStack(), false);
        helper.destroyBlock(MACHINE);
        helper.assertItemEntityPresent(ModItems.ENERGY_UPGRADE.get(), MACHINE, 2.0);
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            return machine;
        }
        throw new IllegalStateException("no machine at " + pos);
    }

    private static void assertNear(GameTestHelper helper, double expected, double actual, String message) {
        helper.assertTrue(Math.abs(expected - actual) < EPSILON,
                message + " (expected " + expected + ", got " + actual + ")");
    }
}
