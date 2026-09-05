package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.MachineConfigSnapshot;
import dev.symo.actualgenerators.machine.MachineItemHandler;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.processing.ResonanceCrusherBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * Tests for the crusher and the mechanic it is built around: a material has to be calibrated once
 * before it is crushed properly, and that calibration is worth carrying around.
 *
 * <p>The bonus a tuned crusher shakes loose is random, so nothing here asserts an exact yield on a
 * tuned run. What is asserted instead is what must hold every time: a calibration run pays the
 * plain yield and nothing more, the batch never promises the output slot more than it can hold,
 * and no operation ever runs on power the machine does not have.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class ResonanceCrusherTests {
    private static final String PLATFORM = "platform";
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final BlockPos SECOND_MACHINE = new BlockPos(0, 1, 0);
    private static final String IRON_RECIPE = "actualgenerators:crushing/iron_ore";

    private ResonanceCrusherTests() {
    }

    /**
     * The whole loop end to end: an ore goes in, twice as much raw material comes out, and the
     * crusher comes away knowing a frequency it did not know before.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 800)
    public static void crushingAnOreCalibratesItAndPaysDouble(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = place(helper, MACHINE);
                    crusher.inputHandler().insertItem(0, new ItemStack(Items.IRON_ORE), false);

                    helper.assertTrue(!crusher.isTuned(), "a new crusher knows no frequencies");
                    helper.assertValueEqual(crusher.baseTicksPerOperation(),
                            100 * ResonanceCrusherBlockEntity.calibrationMultiplier(),
                            "so the first run is a calibration and takes that much longer");
                    helper.assertTrue(crusher.frequency() > 0, "it can still read the frequency off the ore");
                })
                .thenExecuteFor(500, () -> keepPowered(helper, MACHINE))
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = crusher(helper, MACHINE);
                    ItemStack output = crusher.outputHandler().getStackInSlot(0);

                    helper.assertTrue(output.is(ModItems.IRON_DUST.get()), "iron ore crushes into iron dust");
                    helper.assertValueEqual(output.getCount(), 2,
                            "the calibration run pays the plain yield, with no bonus on top");
                    helper.assertTrue(crusher.inputHandler().getStackInSlot(0).isEmpty(), "and eats the ore");
                    helper.assertTrue(crusher.learned().contains(IRON_RECIPE), "and the frequency is now known");

                    // What "known" is worth, read the way the machine reads it: with ore loaded.
                    crusher.inputHandler().insertItem(0, new ItemStack(Items.IRON_ORE), false);
                    helper.assertTrue(crusher.isTuned(), "so the next ore of the same kind is familiar");
                    helper.assertValueEqual(crusher.baseTicksPerOperation(), 100,
                            "and takes the ordinary time rather than the calibration one");
                })
                .thenSucceed();
    }

    /**
     * Raw ore is crushable too, which is the whole reason the dust tier exists.
     *
     * <p>Raw iron cannot crush into more raw iron without minting metal, so it crushes into a dust
     * that smelts back into exactly one ingot — and the doubling lives in the crusher rather than
     * in the furnace.
     */
    @GameTest(template = PLATFORM)
    public static void rawOreCrushesToo(GameTestHelper helper) {
        ResonanceCrusherBlockEntity crusher = place(helper, MACHINE);

        for (ItemStack raw : List.of(new ItemStack(Items.RAW_IRON), new ItemStack(Items.RAW_COPPER),
                new ItemStack(Items.RAW_GOLD))) {
            crusher.inputHandler().extractItem(0, 64, false);
            crusher.inputHandler().insertItem(0, raw, false);

            helper.assertTrue(crusher.frequency() > 0, raw.getHoverName().getString() + " has to ring somewhere");
            helper.assertValueEqual(crusher.currentBatch(), 1,
                    "and be something the crusher will actually take on");
        }

        helper.succeed();
    }

    /** Knowing one frequency is not knowing another: calibration is per material. */
    @GameTest(template = PLATFORM)
    public static void calibrationIsPerMaterial(GameTestHelper helper) {
        ResonanceCrusherBlockEntity crusher = place(helper, MACHINE);
        crusher.applyLearned(List.of(IRON_RECIPE));

        crusher.inputHandler().insertItem(0, new ItemStack(Items.IRON_ORE), false);
        helper.assertTrue(crusher.isTuned(), "the frequency it was taught is the one it knows");
        int ironFrequency = crusher.frequency();

        crusher.inputHandler().extractItem(0, 64, false);
        crusher.inputHandler().insertItem(0, new ItemStack(Items.COBBLESTONE), false);
        helper.assertTrue(!crusher.isTuned(), "cobble is a different material and needs its own calibration");
        helper.assertTrue(crusher.frequency() != ironFrequency, "and it rings at its own frequency");

        helper.succeed();
    }

    /** The point of writing a frequency down: a card carries it to the next crusher on the base. */
    @GameTest(template = PLATFORM)
    public static void aConfigCardCarriesTheCalibration(GameTestHelper helper) {
        ResonanceCrusherBlockEntity taught = place(helper, MACHINE);
        taught.applyLearned(List.of(IRON_RECIPE));

        ResonanceCrusherBlockEntity fresh = place(helper, SECOND_MACHINE);
        fresh.inputHandler().insertItem(0, new ItemStack(Items.IRON_ORE), false);
        helper.assertTrue(!fresh.isTuned(), "the second crusher starts knowing nothing");

        MachineConfigSnapshot.copyOf(taught).applyTo(fresh);

        helper.assertTrue(fresh.isTuned(), "pasting the card teaches it what the first one worked out");
        helper.assertValueEqual(fresh.baseTicksPerOperation(), 100,
                "so it never pays the calibration for a material somebody already did");

        helper.succeed();
    }

    /** No power, no crushing — and, just as important, no quiet progress towards a free one. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void anUnpoweredCrusherDoesNothingAtAll(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = place(helper, MACHINE);
                    crusher.energyStorage().setEnergy(0);
                    crusher.inputHandler().insertItem(0, new ItemStack(Items.IRON_ORE, 4), false);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = crusher(helper, MACHINE);
                    helper.assertValueEqual(crusher.progress(), 0, "an unpowered crusher makes no progress");
                    helper.assertValueEqual(crusher.inputHandler().getStackInSlot(0).getCount(), 4,
                            "and eats nothing");
                    helper.assertTrue(crusher.outputHandler().getStackInSlot(0).isEmpty(), "and pays nothing");
                })
                .thenSucceed();
    }

    /**
     * The output slot is the limit, not a suggestion.
     *
     * <p>A tuned crusher may shake an extra item loose per item in the batch, so the room it needs
     * is the yield <em>plus</em> the bonus that might all land at once. With less room than that it
     * has to wait rather than start an operation it cannot pay out.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aBatchIsNeverBiggerThanTheOutputSlotCanTake(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = place(helper, MACHINE);
                    crusher.applyLearned(List.of(IRON_RECIPE));
                    crusher.inputHandler().insertItem(0, new ItemStack(Items.IRON_ORE, 8), false);
                    // Two spaces left: not enough for one ore's two raw iron plus a possible bonus.
                    crusher.outputHandler().insertItem(0, new ItemStack(Items.RAW_IRON, 62), false);

                    helper.assertValueEqual(crusher.currentBatch(), 0,
                            "with less room than the worst case there is no batch to run");
                })
                .thenExecuteFor(100, () -> keepPowered(helper, MACHINE))
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = crusher(helper, MACHINE);
                    helper.assertValueEqual(crusher.outputHandler().getStackInSlot(0).getCount(), 62,
                            "so nothing is crushed into a slot that cannot hold the result");
                    helper.assertValueEqual(crusher.inputHandler().getStackInSlot(0).getCount(), 8,
                            "and the ore is still there waiting for room");
                })
                .thenSucceed();
    }

    /**
     * A slot holds a configured number of operations' worth and never less than a stack, so the
     * batch is the only cap on what a machine can be fed: a pattern provider pushes until the slot
     * says no, and a fitted machine visibly holds more than a bare one. Vanilla's stack codec stops
     * at ninety-nine, so the count is saved beside the item, and the old shape still loads.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void slotsGrowWithTheBatchAndACountPastAStackSurvivesASave(GameTestHelper helper) {
        ResonanceCrusherBlockEntity crusher = place(helper, MACHINE);
        helper.assertValueEqual(crusher.inputHandler().getSlotLimit(0), 64, "an unupgraded crusher's slot is a plain stack");

        crusher.tierSlot().insertItem(0, ModItems.NETHERITE_TIER_UPGRADE.toStack(), false);
        crusher.upgradeInventory().insertItem(UpgradeType.STACK.ordinal(), new ItemStack(ModItems.STACK_UPGRADE.get(), 64), false);
        int batch = crusher.maxBatch();
        helper.assertTrue(batch > 1, "a stack row and a netherite tier make a real batch, got " + batch);
        int operations = MachineBlockEntity.slotOperations();
        helper.assertValueEqual(crusher.inputHandler().getSlotLimit(0), crusher.inputSlotLimit(), "the input slot follows the machine's sizing");
        helper.assertValueEqual(crusher.inputSlotLimit(), Math.max(64, operations * batch),
                "which is the configured operations' worth of ore");
        helper.assertTrue(crusher.inputSlotLimit() > 64,
                "so a netherite machine with a full stack row holds more than a stack, at " + crusher.inputSlotLimit());
        helper.assertTrue(crusher.outputHandler().getSlotLimit(0) >= operations * batch * 3,
                "and the output slot holds as many operations of two raw ore plus a bonus each");

        // The handler on its own, sized past a stack, since the default balance stops short of it.
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        MachineItemHandler big = new MachineItemHandler(1, () -> 300, () -> { });
        ItemStack refused = big.insertItem(0, new ItemStack(Items.IRON_ORE, 305), false);
        helper.assertValueEqual(refused.getCount(), 5, "a slot takes exactly its limit, past a vanilla stack");
        helper.assertValueEqual(big.extractItem(0, 200, false).getCount(), 200,
                "and hands a whole operation out at once, past the vanilla cap on extraction");
        big.insertItem(0, new ItemStack(Items.IRON_ORE, 200), false);
        MachineItemHandler loaded = new MachineItemHandler(1, () -> 300, () -> { });
        loaded.deserializeNBT(registries, big.serializeNBT(registries));
        helper.assertValueEqual(loaded.getStackInSlot(0).getCount(), 300,
                "a count past ninety-nine survives a save, which vanilla's stack codec would refuse");

        ItemStackHandler old = new ItemStackHandler(1);
        old.setStackInSlot(0, new ItemStack(Items.IRON_ORE, 7));
        MachineItemHandler reader = new MachineItemHandler(1, () -> 64, () -> { });
        reader.deserializeNBT(registries, old.serializeNBT(registries));
        helper.assertValueEqual(reader.getStackInSlot(0).getCount(), 7, "and the shape a plain handler wrote still loads");
        helper.succeed();
    }

    /**
     * Progress belongs to the material it was earned on.
     *
     * <p>Otherwise a player could nearly finish a cheap crush, swap in something expensive, and
     * have the machine hand it over on the next tick.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void progressDoesNotCarryOverToADifferentMaterial(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = place(helper, MACHINE);
                    crusher.inputHandler().insertItem(0, new ItemStack(Items.COBBLESTONE, 4), false);
                })
                .thenExecuteFor(60, () -> keepPowered(helper, MACHINE))
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = crusher(helper, MACHINE);
                    helper.assertTrue(crusher.progress() > 10, "the cobble should be well under way");
                    // The same figure the arrow and the Jade tooltip both read.
                    helper.assertTrue(crusher.progressFraction() > 0 && crusher.progressFraction() < 1,
                            "and be part of the way through, not none of it and not all of it");

                    crusher.inputHandler().extractItem(0, 64, false);
                    crusher.inputHandler().insertItem(0, new ItemStack(Items.IRON_ORE), false);
                })
                .thenExecuteFor(2, () -> keepPowered(helper, MACHINE))
                .thenExecute(() -> {
                    ResonanceCrusherBlockEntity crusher = crusher(helper, MACHINE);
                    helper.assertTrue(crusher.progress() <= 2,
                            "swapping the material starts the clock again, it does not inherit it");
                    helper.assertTrue(crusher.progressFraction() < 0.1,
                            "so the readout starts over with it");
                    helper.assertTrue(crusher.outputHandler().getStackInSlot(0).isEmpty(),
                            "so nothing falls out for free");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ helpers

    private static ResonanceCrusherBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.RESONANCE_CRUSHER.get());
        ResonanceCrusherBlockEntity crusher = crusher(helper, pos);
        crusher.energyStorage().setEnergy(crusher.energyStorage().capacity());
        return crusher;
    }

    private static ResonanceCrusherBlockEntity crusher(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof ResonanceCrusherBlockEntity crusher) {
            return crusher;
        }
        throw new IllegalStateException("no resonance crusher at " + pos);
    }

    /** Stands in for a grid: the crusher spends power every tick and something has to supply it. */
    private static void keepPowered(GameTestHelper helper, BlockPos pos) {
        ResonanceCrusherBlockEntity crusher = crusher(helper, pos);
        crusher.energyStorage().setEnergy(crusher.energyStorage().capacity());
    }
}
