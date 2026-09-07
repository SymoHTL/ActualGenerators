package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.AnnihilationFurnaceBlockEntity;
import dev.symo.actualgenerators.machine.RedstoneMode;
import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.HatchSignal;
import dev.symo.actualgenerators.machine.multiblock.MultiblockCasingBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.menu.AnnihilationFurnaceMenu;
import dev.symo.actualgenerators.menu.RedstoneHatchMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.function.Predicate;

/**
 * The multiblock framework, through the one machine built on it.
 *
 * <p>What matters: a box a player builds becomes a machine the moment the last casing goes in
 * and stops being one the moment a casing comes out; the box is the grade, a bigger interior
 * eats more per operation; the heat on the floor and the load in the slots make the efficiency
 * every item is paid at; the hatches are the only doors and the redstone hatch says what it is
 * told to.
 *
 * <p>Rigs are built the way the preview shows them: the controller in the bottom row of the
 * front wall, the wall centred on it, corium sources on the floor.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class AnnihilationFurnaceTests {
    private static final String ARENA = "arena";

    /** The box's near corner; the arena's floor is y=1 and the shell's bottom layer replaces it. */
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);

    /** The smallest furnace: one column of five over one floor block. */
    private static final int WIDTH = 3;
    private static final int HEIGHT = 7;
    private static final int DEPTH = 3;
    private static final int TALL_BATCH = 5;
    /** The one floor block of a 3x7x3. */
    private static final BlockPos FLOOR = CORNER.offset(1, 1, 1);

    /** Matches the config defaults. */
    private static final long FE_PER_HARDNESS = 20_000;
    private static final long COBBLE = FE_PER_HARDNESS * 2;
    private static final int TICKS_PER_OPERATION = 40;
    private static final int LAVA_HEAT = 400;
    private static final int LOAD_FLOOR = 250;

    private AnnihilationFurnaceTests() {
    }

    // ------------------------------------------------------------------ forming

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void formsWhenAPlayerPlacesTheLastCasingAndComesApartWhenOneBreaks(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        BlockPos missing = new BlockPos(2, 4, 3);
        BlockPos beside = missing.west();
        BlockPos side = new BlockPos(3, 3, 2);
        ServerPlayer player = LinkPortTests.testPlayer(helper);

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> !pos.equals(missing));
                    heatFloor(helper, WIDTH, DEPTH, ModBlocks.CORIUM.get());
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(!furnace(helper, controller).isFormed(), "a box with a hole in it is not a box");
                    helper.assertBlockProperty(controller, MultiblockControllerBlock.FORMED, false);
                    helper.assertBlockProperty(side, MultiblockCasingBlock.FORMED, false);

                    // The last casing goes in as a player would put it: clicked on to the casing beside the gap.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.MACHINE_CASING.toStack());
                    BlockPos worldBeside = helper.absolutePos(beside);
                    BlockHitResult hit = new BlockHitResult(
                            worldBeside.getCenter().relative(Direction.EAST, 0.5), Direction.EAST, worldBeside, false);
                    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    helper.assertBlock(missing, block -> block == ModBlocks.MACHINE_CASING.get(), "the click placed the casing");
                })
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.isFormed(), "the box formed on the tick after the last casing went in");
                    helper.assertBlockProperty(controller, MultiblockControllerBlock.FORMED, true);
                    helper.assertBlockProperty(side, MultiblockCasingBlock.FORMED, true);
                    helper.assertBlockProperty(missing, MultiblockCasingBlock.FORMED, true);
                    MultiblockControllerBlockEntity.Structure structure = furnace.structure();
                    helper.assertTrue(structure != null && structure.describe().equals("3×7×3"),
                            "a 3x7x3 box is a 3x7x3 box");
                    helper.assertValueEqual(furnace.interiorVolume(), TALL_BATCH, "with five blocks inside, the floor included");
                    helper.assertValueEqual(furnace.maxBatch(), TALL_BATCH, "so five items an operation");
                    helper.assertValueEqual(furnace.heatPermille(), 1000, "and a bucket of corium on its one floor block is full heat");

                    helper.destroyBlock(side);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(!furnace(helper, controller).isFormed(), "a broken casing takes the box apart");
                    helper.assertBlockProperty(controller, MultiblockControllerBlock.FORMED, false);
                    helper.assertBlockProperty(missing, MultiblockCasingBlock.FORMED, false);
                    helper.setBlock(side, ModBlocks.MACHINE_CASING.get());
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(furnace(helper, controller).isFormed(), "and the casing going back in forms it again");
                    helper.assertBlockProperty(side, MultiblockCasingBlock.FORMED, true);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void nothingFormsRoundAFilledInteriorASecondControllerOrTooShortABox(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        BlockPos inside = new BlockPos(2, 4, 2);
        BlockPos second = new BlockPos(3, 3, 2);

        helper.startSequence()
                .thenExecute(() -> {
                    // The old minimum: a cube. Too short for a furnace.
                    buildBox(helper, WIDTH, 3, DEPTH, pos -> true);
                    heatFloor(helper, WIDTH, DEPTH, ModBlocks.CORIUM.get());
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(!furnace(helper, controller).isFormed(), "a 3x3x3 is not a furnace");
                    buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> true);
                    helper.setBlock(inside, Blocks.STONE);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(!furnace(helper, controller).isFormed(), "a box with something in it is not hollow");
                    helper.setBlock(inside, Blocks.AIR);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(furnace(helper, controller).isFormed(), "emptied, it forms");
                    helper.setBlock(second, ModBlocks.ANNIHILATION_FURNACE.get().defaultBlockState()
                            .setValue(MultiblockControllerBlock.FACING, Direction.EAST));
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(!furnace(helper, controller).isFormed(), "two controllers in one shell form nothing");
                    helper.assertTrue(!furnace(helper, second).isFormed(), "for either of them");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ the box is the grade

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aBiggerBoxEatsMorePerOperationAndPaysByHardness(GameTestHelper helper) {
        int size = 5;
        int batch = 3 * 5 * 3;
        BlockPos controller = controllerPos(size);

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, size, HEIGHT, size, pos -> true);
                    heatFloor(helper, size, size, ModBlocks.CORIUM.get());
                })
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.isFormed(), "a 5x7x5 box forms like a 3x7x3");
                    helper.assertValueEqual(furnace.interiorVolume(), batch, "forty-five blocks inside");
                    helper.assertValueEqual(furnace.maxBatch(), batch, "so forty-five items an operation");
                    helper.assertValueEqual(furnace.inputSlotLimit(), 8 * batch, "and a slot holds eight operations' worth");
                    helper.assertValueEqual(furnace.energyStorage().capacity(), batch * 1_000_000L, "the buffer follows the interior");
                    helper.assertValueEqual(furnace.energyStorage().getMaxExtract(), batch * 10_000, "as does the hatch rate");
                    helper.assertValueEqual(furnace.heatPermille(), 1000, "nine buckets on nine floor blocks is full heat");

                    ItemHandlerHelper.insertItem(furnace.inputHandler(), new ItemStack(Items.COBBLESTONE, batch), false);
                })
                .thenExecuteAfter(3, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertValueEqual(count(furnace.inputHandler()), 0, "the whole batch left the slots when the operation began");
                    helper.assertTrue(furnace.pending() > 0, "and is owed");
                })
                .thenExecuteAfter(TICKS_PER_OPERATION + 2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.energyStorage().stored() >= batch * COBBLE,
                            "forty-five cobblestone at hardness two are worth " + batch * COBBLE + " FE, got "
                                    + furnace.energyStorage().stored());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void onlyBlocksBurnAndTheirHardnessIsTheirMass(GameTestHelper helper) {
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(new ItemStack(Items.COBBLESTONE)),
                COBBLE, "cobblestone is hardness two");
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(new ItemStack(Items.DEEPSLATE)),
                FE_PER_HARDNESS * 3, "deepslate is hardness three");
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(new ItemStack(Items.OBSIDIAN)),
                1_000_000L, "obsidian at hardness fifty is capped at the ceiling");
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(new ItemStack(Items.TORCH)),
                10_000L, "a torch at hardness zero is worth the floor");
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(new ItemStack(Items.IRON_INGOT)), 0L,
                "an ingot is not a block");
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(new ItemStack(Items.BEDROCK)), 0L,
                "bedrock cannot be broken, so it has no mass to give");
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(new ItemStack(Items.SHULKER_BOX)), 0L,
                "a shulker box is refused by tag, contents and all");
        helper.assertValueEqual(AnnihilationFurnaceBlockEntity.energyFor(ItemStack.EMPTY), 0L, "nothing is worth nothing");

        BlockPos controller = controllerPos(WIDTH);
        buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> true);
        IItemHandler inputs = furnace(helper, controller).inputHandler();
        helper.assertTrue(!inputs.insertItem(0, new ItemStack(Items.IRON_INGOT), true).isEmpty(),
                "the slots refuse an ingot");
        helper.assertTrue(inputs.insertItem(0, new ItemStack(Items.COBBLESTONE), true).isEmpty(),
                "and take cobblestone");
        helper.succeed();
    }

    // ------------------------------------------------------------------ heat and load

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aColdFurnaceEatsNothingAndABucketOfLavaThroughTheControllerIsPoorHeat(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        ServerPlayer player = LinkPortTests.testPlayer(helper);

        helper.startSequence()
                .thenExecute(() -> buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> true))
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.isFormed(), "an empty floor is still a box");
                    helper.assertValueEqual(furnace.heatPermille(), 0, "but a cold one");
                    helper.assertValueEqual(furnace.efficiencyPermille(), 0, "with no efficiency at all");
                    ItemHandlerHelper.insertItem(furnace.inputHandler(), new ItemStack(Items.COBBLESTONE, TALL_BATCH), false);
                })
                .thenExecuteAfter(TICKS_PER_OPERATION + 5, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertValueEqual(count(furnace.inputHandler()), TALL_BATCH, "cold, it eats nothing");
                    helper.assertValueEqual(furnace.energyStorage().stored(), 0L, "and makes nothing");

                    // A bucket of lava on the controller goes on to the floor without opening the box.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
                    clickFront(helper, player, controller);
                    helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the bucket came back empty");
                    helper.assertBlock(FLOOR, block -> block == Blocks.LAVA, "and the lava is on the floor");
                })
                .thenExecuteAfter(2, () -> helper.assertValueEqual(furnace(helper, controller).heatPermille(), LAVA_HEAT,
                        "lava is poor heat"))
                .thenExecuteAfter(TICKS_PER_OPERATION + 5, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertValueEqual(count(furnace.inputHandler()), 0, "warm, it eats");
                    long paid = furnace.energyStorage().stored() + furnace.pending();
                    helper.assertValueEqual(paid, TALL_BATCH * COBBLE * LAVA_HEAT / 1000,
                            "and every item pays lava's share of its worth");

                    // The empty bucket on the controller takes the lava back off the floor.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    clickFront(helper, player, controller);
                    helper.assertTrue(player.getMainHandItem().is(Items.LAVA_BUCKET), "the lava is back in the bucket");
                    helper.assertBlock(FLOOR, block -> block == Blocks.AIR, "and off the floor");
                })
                .thenExecuteAfter(2, () -> helper.assertValueEqual(furnace(helper, controller).heatPermille(), 0,
                        "so the furnace is cold again"))
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void heatIsTheAverageOverTheFloor(GameTestHelper helper) {
        int size = 5;
        BlockPos controller = controllerPos(size);
        BlockPos middle = CORNER.offset(2, 1, 2);

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, size, HEIGHT, size, pos -> true);
                    helper.setBlock(middle, ModBlocks.CORIUM.get());
                })
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.isFormed(), "one bucket on a nine-block floor is a box");
                    helper.assertValueEqual(furnace.heatPermille(), 1000 / 9, "worth a ninth of the heat");
                })
                .thenExecuteAfter(40, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(middle.east())).is(
                            net.minecraft.tags.FluidTags.LAVA), "the bucket has spread over the floor by now");
                    helper.assertTrue(furnace.isFormed(), "spread corium is still a box");
                    helper.assertValueEqual(furnace.heatPermille(), 1000 / 9, "but only sources count");
                    heatFloor(helper, size, size, Blocks.LAVA);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertValueEqual(furnace(helper, controller).heatPermille(), LAVA_HEAT, "nine buckets of lava is lava's heat");
                    heatFloor(helper, size, size, ModBlocks.CORIUM.get());
                })
                .thenExecuteAfter(2, () -> helper.assertValueEqual(furnace(helper, controller).heatPermille(), 1000,
                        "nine of corium is full heat"))
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void stuffingTheSlotsPastTheBatchIsPaidLess(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        int capacity = AnnihilationFurnaceBlockEntity.INPUT_SLOTS * 64;

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> true);
                    heatFloor(helper, WIDTH, DEPTH, ModBlocks.CORIUM.get());
                })
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertValueEqual(furnace.inputSlotLimit(), 64, "a small box's slots are a stack each");
                    helper.assertValueEqual(furnace.loadPermille(0), 1000, "empty slots are no load");
                    helper.assertValueEqual(furnace.loadPermille(TALL_BATCH), 1000, "a batch held is no load either");
                    helper.assertValueEqual(furnace.loadPermille(capacity), LOAD_FLOOR, "stuffed full is the floor");
                    int half = furnace.loadPermille((capacity + TALL_BATCH) / 2);
                    helper.assertTrue(half > LOAD_FLOOR && half < 1000, "and half stuffed is between, " + half);
                    helper.assertValueEqual(furnace.efficiencyPermille(), 1000, "full heat and no load is full efficiency");

                    ItemHandlerHelper.insertItem(furnace.inputHandler(), new ItemStack(Items.COBBLESTONE, capacity), false);
                    helper.assertValueEqual(furnace.held(), capacity, "the slots took a stack each");
                    helper.assertValueEqual(furnace.efficiencyPermille(), LOAD_FLOOR, "and the furnace is at its worst");
                })
                .thenExecuteAfter(3, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertValueEqual(furnace.held(), capacity - TALL_BATCH, "one batch left the slots");
                    helper.assertValueEqual(furnace.energyStorage().stored() + furnace.pending(),
                            TALL_BATCH * COBBLE * LOAD_FLOOR / 1000, "paid at the efficiency the furnace had when it took them");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ hatches

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void theHatchesAreTheOnlyDoorsAndAHatchClickOpensTheController(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        BlockPos itemHatch = new BlockPos(3, 2, 2);
        BlockPos energyHatch = new BlockPos(2, 7, 2);
        BlockPos casing = new BlockPos(1, 2, 2);
        ServerPlayer player = LinkPortTests.testPlayer(helper);

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> !pos.equals(itemHatch) && !pos.equals(energyHatch));
                    heatFloor(helper, WIDTH, DEPTH, ModBlocks.CORIUM.get());
                    helper.setBlock(itemHatch, ModBlocks.ITEM_HATCH.get());
                    helper.setBlock(energyHatch, ModBlocks.ENERGY_HATCH.get());
                })
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.isFormed(), "hatches are shell");
                    helper.assertBlockProperty(itemHatch, MultiblockCasingBlock.FORMED, true);

                    IItemHandler items = items(helper, itemHatch, Direction.EAST);
                    helper.assertTrue(items != null, "an item hatch offers items on its outer face");
                    helper.assertTrue(items(helper, energyHatch, Direction.UP) == null, "an energy hatch offers none");
                    helper.assertTrue(items(helper, controller, Direction.NORTH) == null, "nor does the controller");
                    helper.assertTrue(items(helper, casing, Direction.WEST) == null, "nor a casing");

                    IEnergyStorage energy = energy(helper, energyHatch, Direction.UP);
                    helper.assertTrue(energy != null, "an energy hatch offers energy");
                    helper.assertTrue(energy.canExtract() && !energy.canReceive(), "out only: the furnace makes it");
                    helper.assertTrue(energy(helper, itemHatch, Direction.EAST) == null, "an item hatch offers none");
                    helper.assertTrue(energy(helper, controller, Direction.NORTH) == null, "nor does the controller");

                    helper.assertTrue(items.insertItem(0, new ItemStack(Items.COBBLESTONE), false).isEmpty(),
                            "through the hatch and into the controller");
                    helper.assertValueEqual(count(furnace.inputHandler()), 1, "where it now sits");

                    // A click on the item hatch is the controller's window, from wherever the hatch is.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    LinkPortTests.rightClick(helper, player, itemHatch, false);
                    helper.assertTrue(player.containerMenu instanceof AnnihilationFurnaceMenu,
                            "the item hatch opens the furnace, but " + player.containerMenu.getClass().getSimpleName());
                    player.closeContainer();
                })
                .thenExecuteAfter(TICKS_PER_OPERATION + 5, () -> {
                    IEnergyStorage energy = energy(helper, energyHatch, Direction.UP);
                    helper.assertTrue(energy != null && energy.getEnergyStored() >= COBBLE,
                            "the cobblestone's energy is on offer at the hatch");
                    int drawn = energy.extractEnergy(1_000, false);
                    helper.assertValueEqual(drawn, 1_000, "and can be drawn from it");
                    helper.assertValueEqual(furnace(helper, controller).energyStorage().stored(),
                            COBBLE - 1_000, "out of the controller's buffer");

                    // Take the box apart: the hatch is still there, its door is not.
                    helper.destroyBlock(new BlockPos(1, 3, 3));
                })
                .thenExecuteAfter(2, () -> {
                    IEnergyStorage energy = energy(helper, energyHatch, Direction.UP);
                    helper.assertTrue(energy != null, "the capability stays, so a cached cable is not stale");
                    helper.assertValueEqual(energy.getEnergyStored(), 0, "but it holds nothing without a box");
                    IItemHandler items = items(helper, itemHatch, Direction.EAST);
                    helper.assertTrue(items != null && items.getSlots() == 0, "and the item hatch has no slots");
                    helper.assertBlockProperty(itemHatch, MultiblockCasingBlock.FORMED, false);

                    LinkPortTests.rightClick(helper, player, itemHatch, false);
                    helper.assertTrue(!(player.containerMenu instanceof AnnihilationFurnaceMenu),
                            "and a click on it opens nothing");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aRedstoneHatchGatesTheController(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        BlockPos redstoneHatch = new BlockPos(3, 2, 2);
        BlockPos lever = redstoneHatch.east();

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> !pos.equals(redstoneHatch));
                    heatFloor(helper, WIDTH, DEPTH, ModBlocks.CORIUM.get());
                    helper.setBlock(redstoneHatch, ModBlocks.REDSTONE_HATCH.get());
                    helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
                            .setValue(LeverBlock.FACE, AttachFace.WALL)
                            .setValue(LeverBlock.FACING, Direction.EAST));
                })
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.isFormed(), "a redstone hatch is shell too");
                    helper.assertValueEqual(hatch(helper, redstoneHatch).mode(), HatchSignal.CONTROL, "and starts as a control");
                    furnace.setRedstoneMode(RedstoneMode.WITH_SIGNAL);
                    ItemHandlerHelper.insertItem(furnace.inputHandler(), new ItemStack(Items.COBBLESTONE, 4), false);
                })
                .thenExecuteAfter(TICKS_PER_OPERATION, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertValueEqual(count(furnace.inputHandler()), 4, "without a signal nothing is eaten");
                    helper.assertValueEqual(furnace.energyStorage().stored(), 0L, "and nothing is made");
                    helper.pullLever(lever);
                })
                .thenExecuteAfter(TICKS_PER_OPERATION + 25, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(count(furnace.inputHandler()) < 4, "a lever on the hatch, away from the controller, starts it");
                    helper.assertTrue(furnace.energyStorage().stored() > 0, "and it pays");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aRedstoneHatchReportsWhatItsWindowTellsItTo(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        BlockPos redstoneHatch = new BlockPos(3, 2, 2);
        BlockPos lamp = redstoneHatch.east();
        ServerPlayer player = LinkPortTests.testPlayer(helper);

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> !pos.equals(redstoneHatch));
                    heatFloor(helper, WIDTH, DEPTH, ModBlocks.CORIUM.get());
                    helper.setBlock(redstoneHatch, ModBlocks.REDSTONE_HATCH.get());
                    helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(furnace(helper, controller).isFormed(), "the box stands");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, lit -> !lit, "a control hatch gives nothing off");

                    // The hatch's own window: a click, then a row.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    LinkPortTests.rightClick(helper, player, redstoneHatch, false);
                    helper.assertTrue(player.containerMenu instanceof RedstoneHatchMenu,
                            "the redstone hatch opens its own window, but " + player.containerMenu.getClass().getSimpleName());
                    player.containerMenu.clickMenuButton(player, HatchSignal.FORMED.ordinal());
                    helper.assertValueEqual(hatch(helper, redstoneHatch).mode(), HatchSignal.FORMED, "the row sets the mode");
                    helper.assertValueEqual(hatch(helper, redstoneHatch).emitted(), 15, "and a formed box is a full signal");
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, lit -> lit, "which lights the lamp beside it");
                    player.containerMenu.clickMenuButton(player, HatchSignal.ENERGY.ordinal());
                    helper.assertValueEqual(hatch(helper, redstoneHatch).emitted(), 0, "an empty buffer is no signal");
                })
                .thenExecuteAfter(8, () -> {
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, lit -> !lit, "so the lamp goes out");
                    ItemHandlerHelper.insertItem(furnace(helper, controller).inputHandler(), new ItemStack(Items.COBBLESTONE), false);
                })
                .thenExecuteAfter(TICKS_PER_OPERATION + 5, () -> {
                    helper.assertTrue(hatch(helper, redstoneHatch).emitted() > 0, "a buffer with something in it is a signal");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, lit -> lit, "and the lamp is on again");
                    helper.destroyBlock(new BlockPos(1, 3, 3));
                })
                .thenExecuteAfter(8, () -> {
                    helper.assertValueEqual(hatch(helper, redstoneHatch).emitted(), 0, "a hatch with no box behind it reports nothing");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, lit -> !lit, "and the lamp is out");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aReportingHatchBesideTheControllerDoesNotPowerItButALeverDoes(GameTestHelper helper) {
        BlockPos controller = controllerPos(WIDTH);
        BlockPos redstoneHatch = controller.east();
        BlockPos lever = controller.north();

        helper.startSequence()
                .thenExecute(() -> {
                    buildBox(helper, WIDTH, HEIGHT, DEPTH, pos -> !pos.equals(redstoneHatch));
                    heatFloor(helper, WIDTH, DEPTH, ModBlocks.CORIUM.get());
                    helper.setBlock(redstoneHatch, ModBlocks.REDSTONE_HATCH.get());
                })
                .thenExecuteAfter(2, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.isFormed(), "the box stands");
                    hatch(helper, redstoneHatch).setMode(HatchSignal.FORMED);
                    helper.assertValueEqual(hatch(helper, redstoneHatch).emitted(), 15, "the hatch beside the controller is a full signal");
                    furnace.setRedstoneMode(RedstoneMode.WITH_SIGNAL);
                    ItemHandlerHelper.insertItem(furnace.inputHandler(), new ItemStack(Items.COBBLESTONE, 4), false);
                })
                .thenExecuteAfter(TICKS_PER_OPERATION + 5, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(!furnace.readPower(), "which the controller does not read: a hatch never feeds its own box");
                    helper.assertValueEqual(count(furnace.inputHandler()), 4, "so nothing is eaten");
                    helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
                            .setValue(LeverBlock.FACE, AttachFace.WALL)
                            .setValue(LeverBlock.FACING, Direction.NORTH));
                    helper.pullLever(lever);
                })
                .thenExecuteAfter(TICKS_PER_OPERATION + 25, () -> {
                    AnnihilationFurnaceBlockEntity furnace = furnace(helper, controller);
                    helper.assertTrue(furnace.readPower(), "a lever on the controller is read");
                    helper.assertTrue(count(furnace.inputHandler()) < 4, "and starts it");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ rig

    /** The controller: bottom row of the front wall (north, z = 1), the wall centred on it, facing north. */
    private static BlockPos controllerPos(int width) {
        return MultiblockRigs.controllerPos(CORNER, width);
    }

    /** A hollow box of casing from {@link #CORNER} with the furnace in its front wall. */
    private static void buildBox(GameTestHelper helper, int width, int height, int depth, Predicate<BlockPos> place) {
        MultiblockRigs.buildBox(helper, CORNER, ModBlocks.ANNIHILATION_FURNACE.get(), width, height, depth, place);
    }

    /** A source of the fluid on every floor block of a box from {@link #CORNER}. */
    private static void heatFloor(GameTestHelper helper, int width, int depth, Block fluid) {
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                helper.setBlock(CORNER.offset(x, 1, z), fluid);
            }
        }
    }

    /** Right-clicks the north face of the controller with what is in hand, as a player standing in front of it would. */
    private static void clickFront(GameTestHelper helper, ServerPlayer player, BlockPos controller) {
        BlockPos worldPos = helper.absolutePos(controller);
        BlockHitResult hit = new BlockHitResult(
                worldPos.getCenter().relative(Direction.NORTH, 0.5), Direction.NORTH, worldPos, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static AnnihilationFurnaceBlockEntity furnace(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof AnnihilationFurnaceBlockEntity furnace) {
            return furnace;
        }
        throw new IllegalStateException("no annihilation furnace at " + pos);
    }

    private static HatchBlockEntity hatch(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof HatchBlockEntity hatch) {
            return hatch;
        }
        throw new IllegalStateException("no hatch at " + pos);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), side);
    }

    private static IEnergyStorage energy(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pos), side);
    }

    private static int count(IItemHandler handler) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            total += handler.getStackInSlot(slot).getCount();
        }
        return total;
    }
}
