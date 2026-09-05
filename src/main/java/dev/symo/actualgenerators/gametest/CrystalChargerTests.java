package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.item.FluxCrystalItem;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.storage.CrystalChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Tests for flux crystals and the machine that fills and empties them.
 *
 * <p>The thing to guard here is conservation. A crystal is energy in item form, so anything the
 * machine loses is a quiet tax on the player and anything it gains is free power. Every test below
 * checks a total rather than a rate.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class CrystalChargerTests {
    private static final String EMPTY = "empty";
    private static final String PLATFORM = "platform";
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    private CrystalChargerTests() {
    }

    /**
     * The crystal is a standard FE item, not a proprietary one — that is the whole reason it works
     * in other mods' chargers, and it is one capability registration away from silently not.
     */
    @GameTest(template = EMPTY)
    public static void aSingleCrystalIsAnOrdinaryFeBattery(GameTestHelper helper) {
        ItemStack crystal = ModItems.FLUX_CRYSTAL.toStack();

        IEnergyStorage battery = crystal.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(battery != null, "a crystal must expose the standard FE item capability");
        helper.assertValueEqual((long) battery.getMaxEnergyStored(), FluxCrystalItem.capacity(),
                "with its full capacity");

        int accepted = battery.receiveEnergy(Integer.MAX_VALUE, false);
        helper.assertTrue(accepted > 0, "and it must actually take a charge");
        helper.assertValueEqual(FluxCrystalItem.energyOf(crystal), (long) accepted,
                "which is stored on the stack itself");
        helper.assertValueEqual(battery.getEnergyStored(), accepted, "and read back through the capability");

        // The battery-item convention: a stack is not one big cell.
        ItemStack pile = ModItems.FLUX_CRYSTAL.toStack(16);
        helper.assertTrue(pile.getCapability(Capabilities.EnergyStorage.ITEM) == null,
                "a stack must not charge as though it were a single crystal");

        helper.succeed();
    }

    /** Full crystals stack with full ones and empty with empty, or a chest of them is useless. */
    @GameTest(template = EMPTY)
    public static void crystalsAtTheSameChargeStack(GameTestHelper helper) {
        ItemStack emptyOne = ModItems.FLUX_CRYSTAL.toStack();
        ItemStack emptyTwo = ModItems.FLUX_CRYSTAL.toStack();
        ItemStack fullOne = FluxCrystalItem.withEnergy(emptyOne, 1, FluxCrystalItem.capacity());
        ItemStack fullTwo = FluxCrystalItem.withEnergy(emptyOne, 1, FluxCrystalItem.capacity());

        helper.assertTrue(ItemStack.isSameItemSameComponents(emptyOne, emptyTwo), "two empties stack");
        helper.assertTrue(ItemStack.isSameItemSameComponents(fullOne, fullTwo), "two fulls stack");
        helper.assertTrue(!ItemStack.isSameItemSameComponents(emptyOne, fullOne),
                "a full and an empty must not, or charge would vanish into a merge");
        helper.assertTrue(emptyOne.getMaxStackSize() > 1, "and they have to be stackable at all");

        helper.succeed();
    }

    /**
     * Spending a crystal in a recipe hands the vessel back, the way a milk bucket does. An empty
     * one has nothing to spend, so it must not — that would be a duplication glitch.
     */
    @GameTest(template = EMPTY)
    public static void aSpentCrystalComesBackEmpty(GameTestHelper helper) {
        ItemStack full = FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), 1, FluxCrystalItem.capacity());

        helper.assertTrue(full.hasCraftingRemainingItem(), "a charged crystal leaves its shell behind");
        helper.assertValueEqual(FluxCrystalItem.energyOf(full.getCraftingRemainingItem()), 0L,
                "and the shell comes back empty");

        ItemStack drained = ModItems.FLUX_CRYSTAL.toStack();
        helper.assertTrue(!drained.hasCraftingRemainingItem(),
                "an empty crystal has nothing to spend, so it must not hand anything back");

        helper.succeed();
    }

    /** Energy upgrades only: there is no conversion here to run faster, just a transfer. */
    @GameTest(template = PLATFORM)
    public static void theChargerTakesEnergyUpgradesAndNothingElse(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.CRYSTAL_CHARGER.get());
        CrystalChargerBlockEntity machine = machine(helper);

        IItemHandler upgrades = machine.upgradeInventory();
        helper.assertTrue(upgrades.isItemValid(UpgradeType.ENERGY.ordinal(), ModItems.ENERGY_UPGRADE.toStack()),
                "energy upgrades raise the rate it moves charge at");
        for (UpgradeType refused : new UpgradeType[]{UpgradeType.SPEED, UpgradeType.OVERCLOCK, UpgradeType.STACK}) {
            helper.assertTrue(!upgrades.isItemValid(refused.ordinal(), ModItems.upgradeItem(refused).toStack()),
                    refused + " has nothing to act on in a machine that only moves energy");
        }

        helper.succeed();
    }

    /**
     * A whole stack fed in comes back out charged, with the buffer paying exactly what the crystals
     * end up holding — not a rounded-off approximation of it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void chargingAStackCostsExactlyWhatTheStackEndsUpHolding(GameTestHelper helper) {
        int count = 16;
        // Nearly full to start with, so the whole stack finishes inside the machine's own buffer
        // rather than waiting on a supply the test would have to fake.
        long start = FluxCrystalItem.capacity() - 5_001;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(MACHINE, ModBlocks.CRYSTAL_CHARGER.get());
                    CrystalChargerBlockEntity machine = machine(helper);
                    machine.energyStorage().setEnergy(machine.energyStorage().getMaxEnergyStored());
                    machine.inputHandler().insertItem(0,
                            FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), count, start), false);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    CrystalChargerBlockEntity machine = machine(helper);
                    ItemStack output = machine.outputHandler().getStackInSlot(0);
                    long held = FluxCrystalItem.energyOf(output) * output.getCount();
                    long spent = machine.energyStorage().capacity() - machine.energyStorage().stored();

                    helper.assertValueEqual(output.getCount(), count, "every crystal in the stack comes back");
                    helper.assertTrue(machine.processing().isEmpty(), "with nothing left mid-charge");
                    helper.assertValueEqual(FluxCrystalItem.energyOf(output), FluxCrystalItem.capacity(),
                            "charged to the top, not to a rounded-down approximation");
                    helper.assertValueEqual(held - start * count, spent,
                            "and the buffer paid exactly what the crystals gained, to the FE");
                    helper.assertTrue(machine.inputHandler().getStackInSlot(0).isEmpty(),
                            "and the crystals left the input slot rather than being copied out of it");
                })
                .thenSucceed();
    }

    /**
     * The other direction, and the round trip. Energy that comes back out of a stack has to be
     * exactly what went in — a charger that loses a little on each pass is a slow leak, and one
     * that gains a little is free power.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void dischargingGivesBackEveryFeThatWentIn(GameTestHelper helper) {
        int count = 8;
        // Not full: eight full crystals hold more than the machine's buffer, and a discharger
        // stops when it has nowhere left to put the energy.
        int charge = 20_000;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(MACHINE, ModBlocks.CRYSTAL_CHARGER.get());
                    CrystalChargerBlockEntity machine = machine(helper);
                    machine.toggleMode();
                    machine.energyStorage().setEnergy(0);
                    machine.inputHandler().insertItem(0,
                            FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), count, charge), false);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    CrystalChargerBlockEntity machine = machine(helper);
                    ItemStack output = machine.outputHandler().getStackInSlot(0);

                    helper.assertTrue(machine.isDischarging(), "the machine should still be running backwards");
                    helper.assertValueEqual(output.getCount(), count, "every crystal comes back");
                    helper.assertValueEqual(FluxCrystalItem.energyOf(output), 0L, "emptied, so they stack again");
                    helper.assertValueEqual(machine.energyStorage().stored(), (long) charge * count,
                            "and the buffer holds every FE the crystals were carrying");
                })
                .thenSucceed();
    }

    /**
     * Part way through, every FE that has left the buffer is accounted for on a crystal — the one
     * being worked on or one already delivered. Breaking the machine at that moment must neither
     * mint energy nor swallow it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void everyFeThatLeavesTheBufferIsOnACrystal(GameTestHelper helper) {
        int count = 9;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(MACHINE, ModBlocks.CRYSTAL_CHARGER.get());
                    CrystalChargerBlockEntity machine = machine(helper);
                    machine.energyStorage().setEnergy(machine.energyStorage().getMaxEnergyStored());
                    machine.inputHandler().insertItem(0, ModItems.FLUX_CRYSTAL.toStack(count), false);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    CrystalChargerBlockEntity machine = machine(helper);
                    ItemStack working = machine.processing();
                    helper.assertTrue(!working.isEmpty(), "the work should be under way, not finished");

                    long held = chargeIn(working) + chargeIn(machine.outputHandler().getStackInSlot(0));
                    long spent = machine.energyStorage().capacity() - machine.energyStorage().stored();

                    helper.assertTrue(held > 0, "and some charge should have reached a crystal");
                    helper.assertValueEqual(held, spent, "every FE out of the buffer is on a crystal");
                })
                .thenSucceed();
    }

    /**
     * The reason it takes one crystal at a time: a stack feeds through steadily. Charged crystals
     * appear in the output while the rest of the stack is still queued, rather than the whole lot
     * vanishing into the machine and reappearing minutes later.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void crystalsComeOutOneAtATimeRatherThanAllAtOnce(GameTestHelper helper) {
        int count = 4;
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(MACHINE, ModBlocks.CRYSTAL_CHARGER.get());
                    CrystalChargerBlockEntity machine = machine(helper);
                    machine.energyStorage().setEnergy(machine.energyStorage().getMaxEnergyStored());
                    machine.inputHandler().insertItem(0,
                            // Four ticks of work each, so a crystal is visibly in hand mid-stack.
                            FluxCrystalItem.withEnergy(ModItems.FLUX_CRYSTAL.toStack(), count,
                                    FluxCrystalItem.capacity() - 4L * machine.ratePerTick()), false);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    CrystalChargerBlockEntity machine = machine(helper);
                    ItemStack output = machine.outputHandler().getStackInSlot(0);

                    helper.assertTrue(output.getCount() >= 1 && output.getCount() < count,
                            "some of the stack should be done and the rest still coming, got "
                                    + output.getCount() + " of " + count);
                    helper.assertValueEqual(FluxCrystalItem.energyOf(output), FluxCrystalItem.capacity(),
                            "and what has come out is finished, not part way");
                    helper.assertValueEqual(machine.processing().getCount(), 1,
                            "with exactly one crystal in hand at a time");
                })
                .thenSucceed();
    }

    private static long chargeIn(ItemStack crystals) {
        return FluxCrystalItem.energyOf(crystals) * crystals.getCount();
    }

    private static CrystalChargerBlockEntity machine(GameTestHelper helper) {
        if (helper.getBlockEntity(MACHINE) instanceof CrystalChargerBlockEntity machine) {
            return machine;
        }
        throw new IllegalStateException("no crystal charger at " + MACHINE);
    }
}
