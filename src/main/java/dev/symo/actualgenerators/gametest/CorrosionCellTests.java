package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.CorrosionCellBlockEntity;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.RedstoneMode;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.CorrosionCellMenu;
import dev.symo.actualgenerators.menu.MachineMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * In-world tests for the first real machine, which is also the first end-to-end exercise of the
 * chassis: block placement, ticking, sided capabilities and the oxidation reaction itself.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class CorrosionCellTests {
    private static final String PLATFORM = "platform";
    private static final BlockPos CELL = new BlockPos(2, 1, 2);

    private CorrosionCellTests() {
    }

    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void oxidisesCopperAndGeneratesPower(GameTestHelper helper) {
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        CorrosionCellBlockEntity hot = runHot(cell(helper));
        hot.inputHandler().insertItem(0, new ItemStack(Items.COPPER_BLOCK, 4), false);

        helper.startSequence()
                .thenIdle(oneStage(hot))
                .thenExecute(() -> {
                    CorrosionCellBlockEntity cell = cell(helper);

                    helper.assertTrue(cell.energyStorage().getEnergyStored() > 0,
                            "the cell should have generated power while oxidising");

                    ItemStack result = cell.outputHandler().getStackInSlot(0);
                    helper.assertTrue(result.is(Items.EXPOSED_COPPER),
                            "copper should have advanced one weathering stage, got " + result);
                    helper.assertTrue(cell.inputHandler().getStackInSlot(0).getCount() < 4,
                            "the oxidised copper should have left the input slot");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void advancesThroughTheWholeWeatheringChain(GameTestHelper helper) {
        // Weathered copper is the last stage that still oxidises; oxidised copper is inert.
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        CorrosionCellBlockEntity hot = runHot(cell(helper));
        hot.inputHandler().insertItem(0, new ItemStack(Items.WEATHERED_COPPER, 1), false);

        helper.startSequence()
                .thenIdle(oneStage(hot))
                .thenExecute(() -> {
                    ItemStack result = cell(helper).outputHandler().getStackInSlot(0);
                    helper.assertTrue(result.is(Items.OXIDIZED_COPPER),
                            "weathered copper should oxidise fully, got " + result);
                })
                .thenSucceed();
    }

    /**
     * The copper is spent the moment the operation starts, the way a furnace spends its fuel.
     *
     * <p>Paying first is an exploit: fit stack upgrades, let the cell bank nine items' worth of
     * energy, pull the upgrades out at 99% and have it consume one. The batch is committed up
     * front at the size it was committed at, so nothing done afterwards changes the bill.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void copperIsSpentBeforeAnyEnergyIsPaidForIt(GameTestHelper helper) {
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        CorrosionCellBlockEntity hot = runHot(cell(helper));
        hot.upgradeInventory().insertItem(UpgradeType.STACK.ordinal(),
                new ItemStack(ModItems.STACK_UPGRADE.get(), 64), false);
        hot.inputHandler().insertItem(0, new ItemStack(Items.COPPER_BLOCK, 64), false);

        int batch = hot.maxBatch();
        helper.assertTrue(batch > 1, "this test needs stack upgrades to actually raise the batch");

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    CorrosionCellBlockEntity cell = cell(helper);
                    helper.assertValueEqual(cell.processing().getCount(), batch,
                            "the batch should already be out of the input slot");
                    helper.assertValueEqual(cell.inputHandler().getStackInSlot(0).getCount(), 64 - batch,
                            "and the slot should be exactly that much lighter");
                    helper.assertTrue(cell.outputHandler().getStackInSlot(0).isEmpty(),
                            "with nothing delivered yet");

                    // The exploit: bank the operation at nine items' rate, then take the upgrades
                    // back out so it only costs one.
                    cell.upgradeInventory().extractItem(UpgradeType.STACK.ordinal(), 64, false);
                })
                .thenIdle(oneStage(hot))
                .thenExecute(() -> helper.assertValueEqual(
                        cell(helper).outputHandler().getStackInSlot(0).getCount(), batch,
                        "the whole committed batch must be delivered, not the batch it was shrunk to"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void refusesCopperThatCannotOxidise(GameTestHelper helper) {
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        IItemHandler inputs = cell(helper).inputHandler();

        ItemStack waxed = new ItemStack(Items.WAXED_COPPER_BLOCK, 1);
        helper.assertTrue(inputs.insertItem(0, waxed, true).getCount() == 1,
                "waxed copper is sealed and must be refused");

        ItemStack spent = new ItemStack(Items.OXIDIZED_COPPER, 1);
        helper.assertTrue(inputs.insertItem(0, spent, true).getCount() == 1,
                "fully oxidised copper has no reaction left and must be refused");

        ItemStack unrelated = new ItemStack(Items.IRON_INGOT, 1);
        helper.assertTrue(inputs.insertItem(0, unrelated, true).getCount() == 1,
                "non-copper must be refused");

        ItemStack fresh = new ItemStack(Items.COPPER_BLOCK, 1);
        helper.assertTrue(inputs.insertItem(0, fresh, true).isEmpty(),
                "fresh copper must be accepted");

        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void exposesEnergyOnEveryFace(GameTestHelper helper) {
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        BlockPos worldPos = helper.absolutePos(CELL);

        for (Direction side : Direction.values()) {
            IEnergyStorage energy = helper.getLevel()
                    .getCapability(Capabilities.EnergyStorage.BLOCK, worldPos, side);
            helper.assertTrue(energy != null, "every face should expose energy, " + side + " did not");
            helper.assertTrue(energy.canExtract(), "faces default to output, " + side + " did not");
            helper.assertTrue(!energy.canReceive(),
                    "a generator's output face must not accept power, " + side + " did");
        }

        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void itemFacesOnlyMoveItemsTheRightWay(GameTestHelper helper) {
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        BlockPos worldPos = helper.absolutePos(CELL);

        // Default layout: copper in through the top, results out of the bottom.
        IItemHandler top = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, worldPos, Direction.UP);
        helper.assertTrue(top != null, "the top face should accept items");
        helper.assertTrue(top.insertItem(0, new ItemStack(Items.COPPER_BLOCK, 1), true).isEmpty(),
                "the top face should take copper");
        helper.assertTrue(top.extractItem(0, 1, true).isEmpty(),
                "the top face must not let its input be pulled back out");

        IItemHandler bottom = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, worldPos, Direction.DOWN);
        helper.assertTrue(bottom != null, "the bottom face should hand out results");
        helper.assertTrue(bottom.insertItem(0, new ItemStack(Items.COPPER_BLOCK, 1), true).getCount() == 1,
                "the bottom face must not accept items pushed into the output");

        IItemHandler side = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, worldPos, Direction.NORTH);
        helper.assertTrue(side == null, "faces with no item configuration should expose nothing");

        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void stopsWhenTheBufferIsFull(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
                    CorrosionCellBlockEntity cell = cell(helper);
                    cell.inputHandler().insertItem(0, new ItemStack(Items.COPPER_BLOCK, 8), false);
                    // Fill the buffer so the reaction has nowhere to put its energy.
                    cell.energyStorage().generate(Integer.MAX_VALUE);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    CorrosionCellBlockEntity cell = cell(helper);
                    helper.assertTrue(cell.inputHandler().getStackInSlot(0).getCount() == 8,
                            "a cell with a full buffer must not burn through copper for nothing");
                    helper.assertTrue(cell.outputHandler().getStackInSlot(0).isEmpty(),
                            "no copper should have been converted while the buffer was full");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void menuButtonsReconfigureTheMachine(GameTestHelper helper) {
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        CorrosionCellBlockEntity cell = cell(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CorrosionCellMenu menu = new CorrosionCellMenu(1, player.getInventory(), cell);

        // Redstone mode cycles through every setting and back round.
        RedstoneMode first = cell.redstoneMode();
        menu.clickMenuButton(player, MachineMenu.BUTTON_CYCLE_REDSTONE);
        helper.assertTrue(cell.redstoneMode() != first, "the redstone button should change the mode");
        for (int press = 1; press < RedstoneMode.values().length; press++) {
            menu.clickMenuButton(player, MachineMenu.BUTTON_CYCLE_REDSTONE);
        }
        helper.assertValueEqual(cell.redstoneMode(), first, "cycling all the way round returns to the start");

        // Every face button must land on the face it names, for the kind it names; a kind the
        // machine does not move has no faces to configure and its buttons are refused.
        for (TransferKind kind : TransferKind.material()) {
            for (RelativeSide side : RelativeSide.all()) {
                IoMode before = cell.sideConfig().get(kind, side);
                boolean taken = menu.clickMenuButton(player, MachineMenu.BUTTON_SIDES_START + kind.ordinal() * 6 + side.ordinal());
                if (menu.supportsKind(kind)) {
                    helper.assertTrue(taken, "button for " + kind + "/" + side + " should be taken");
                    helper.assertValueEqual(cell.sideConfig().get(kind, side), before.next(),
                            "button for " + kind + "/" + side + " changed the wrong face");
                } else {
                    helper.assertTrue(!taken, "a cell has no " + kind + " faces to offer");
                    helper.assertValueEqual(cell.sideConfig().get(kind, side), before, kind + " face left alone");
                }
            }
        }
        helper.assertTrue(menu.supportsKind(TransferKind.ITEM) && menu.supportsKind(TransferKind.ENERGY)
                && !menu.supportsKind(TransferKind.FLUID), "a cell moves items and energy and has no tank");

        helper.assertTrue(!menu.clickMenuButton(player, 9999), "unknown buttons must be rejected");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void menuReportsMachineStateToTheScreen(GameTestHelper helper) {
        helper.setBlock(CELL, ModBlocks.CORROSION_CELL.get());
        CorrosionCellBlockEntity cell = cell(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CorrosionCellMenu menu = new CorrosionCellMenu(1, player.getInventory(), cell);

        helper.assertTrue(menu.energyCapacity() > 0, "the screen needs a capacity to draw the energy bar");
        helper.assertValueEqual(menu.energyStored(), 0L, "a fresh cell is empty");

        cell.energyStorage().generate(1_000);
        helper.assertValueEqual(menu.energyStored(), 1_000L, "the menu should report live energy");

        helper.assertValueEqual(menu.sideMode(TransferKind.ENERGY, RelativeSide.BACK), IoMode.OUTPUT,
                "the menu should report the machine's real side configuration");

        helper.assertValueEqual(menu.upgradeSlotCount(), 4,
                "a machine that uses every upgrade shows a slot for each");

        helper.succeed();
    }

    /**
     * Runs the cell as fast as it will go, so a test that waits for a whole oxidation stage takes
     * seconds rather than the minute the balance numbers ask for. It changes the rate, never the
     * reaction, which is what these tests are about.
     *
     * <p>Energy upgrades come with it because nothing in a test drains the buffer, and a cell that
     * fills its buffer stops working — correctly, but not while a test is trying to watch it work.
     */
    private static CorrosionCellBlockEntity runHot(CorrosionCellBlockEntity cell) {
        for (UpgradeType type : new UpgradeType[]{UpgradeType.SPEED, UpgradeType.OVERCLOCK, UpgradeType.ENERGY}) {
            cell.upgradeInventory().insertItem(type.ordinal(),
                    new ItemStack(ModItems.upgradeItem(type).get(), 64), false);
        }
        cell.overclockState().setWorkedTicks(cell.tuning().overclockRampTicks());
        return cell;
    }

    /** How long one stage takes for this cell right now, plus slack for the tick it lands on. */
    private static int oneStage(CorrosionCellBlockEntity cell) {
        return cell.ticksForOperation(cell.baseTicksPerStage()) + 20;
    }

    private static CorrosionCellBlockEntity cell(GameTestHelper helper) {
        if (helper.getBlockEntity(CELL) instanceof CorrosionCellBlockEntity cell) {
            return cell;
        }
        throw new IllegalStateException("no corrosion cell at " + CELL);
    }
}
