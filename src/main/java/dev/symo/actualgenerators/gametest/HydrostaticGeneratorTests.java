package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.HydrostaticGeneratorBlockEntity;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.HydrostaticGeneratorMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Tests for the generator whose output the world sets rather than its inventory.
 *
 * <p>These are the first exercise of a machine with no items at all, so they also cover the
 * chassis behaving sensibly when {@code autoInputHandler} and {@code autoOutputHandler} are both
 * absent.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class HydrostaticGeneratorTests {
    private static final String TOWER = "tower";
    private static final String PLATFORM = "platform";
    private static final BlockPos GENERATOR = new BlockPos(2, 1, 2);

    /** Matches the config default; the tests assert against real numbers, not the config. */
    private static final int FE_PER_BLOCK = 2;
    private static final int MAX_COLUMN = 16;

    private HydrostaticGeneratorTests() {
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void outputScalesWithTheWaterColumn(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get());
                    flood(helper, 4);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    HydrostaticGeneratorBlockEntity generator = generator(helper);
                    helper.assertValueEqual(generator.columnHeight(), 4, "four water blocks above");
                    helper.assertValueEqual(generator.ratedEnergyPerTick(), 4 * FE_PER_BLOCK,
                            "the rating should be the column times the per-block rate");
                    helper.assertTrue(generator.energyStorage().getEnergyStored() > 0,
                            "a flooded generator should be filling its buffer");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void generatesNothingWithoutWater(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get()))
                .thenIdle(40)
                .thenExecute(() -> {
                    HydrostaticGeneratorBlockEntity generator = generator(helper);
                    helper.assertValueEqual(generator.columnHeight(), 0, "no water, no column");
                    helper.assertValueEqual(generator.energyStorage().getEnergyStored(), 0,
                            "a dry generator must produce nothing");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 300)
    public static void capsAtTheConfiguredDepth(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get());
                    flood(helper, MAX_COLUMN + 2);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    HydrostaticGeneratorBlockEntity generator = generator(helper);
                    helper.assertValueEqual(generator.columnHeight(), MAX_COLUMN,
                            "water past the cap should not be counted");
                    helper.assertValueEqual(generator.ratedEnergyPerTick(), MAX_COLUMN * FE_PER_BLOCK,
                            "the rating should stop climbing at the cap");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 300)
    public static void stopsWhenTheColumnIsDrained(GameTestHelper helper) {
        int[] chargeWhenDrained = new int[1];
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get());
                    flood(helper, 4);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(generator(helper).energyStorage().getEnergyStored() > 0,
                            "it should have been generating before the shaft was drained");
                    for (int y = 1; y <= 4; y++) {
                        helper.setBlock(GENERATOR.above(y), Blocks.AIR);
                    }
                })
                // Breaking the block directly above is a neighbour change, so the re-measure is
                // immediate; the extra ticks are for the buffer to prove it stopped growing.
                .thenIdle(10)
                .thenExecute(() -> {
                    HydrostaticGeneratorBlockEntity generator = generator(helper);
                    helper.assertValueEqual(generator.columnHeight(), 0, "the column is gone");
                    chargeWhenDrained[0] = generator.energyStorage().getEnergyStored();
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertValueEqual(
                        generator(helper).energyStorage().getEnergyStored(), chargeWhenDrained[0],
                        "a drained generator must not keep earning"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void onlyStillWaterCarriesTheColumn(GameTestHelper helper) {
        helper.assertTrue(HydrostaticGeneratorBlockEntity.countsTowardColumn(Blocks.WATER.defaultBlockState()),
                "a water source is the whole point");

        BlockState flowing = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
        helper.assertTrue(!HydrostaticGeneratorBlockEntity.countsTowardColumn(flowing),
                "flowing water must not count, or one bucket would beat a flooded shaft");

        BlockState waterloggedStairs = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true);
        helper.assertTrue(HydrostaticGeneratorBlockEntity.countsTowardColumn(waterloggedStairs),
                "waterlogged blocks hold just as much water as an open shaft");

        helper.assertTrue(!HydrostaticGeneratorBlockEntity.countsTowardColumn(Blocks.LAVA.defaultBlockState()),
                "lava is not water");
        helper.assertTrue(!HydrostaticGeneratorBlockEntity.countsTowardColumn(Blocks.GLASS.defaultBlockState()),
                "a dry block ends the column");
        helper.assertTrue(!HydrostaticGeneratorBlockEntity.countsTowardColumn(Blocks.AIR.defaultBlockState()),
                "air ends the column");

        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void refusesUpgradesItCannotUse(GameTestHelper helper) {
        helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get());
        HydrostaticGeneratorBlockEntity generator = generator(helper);

        helper.assertTrue(generator.upgradeInventory()
                        .isItemValid(UpgradeType.ENERGY.ordinal(), new ItemStack(ModItems.ENERGY_UPGRADE.get())),
                "energy upgrades still raise its buffer and throughput");

        // It burns no fuel, so speed would be free power rather than a trade.
        for (UpgradeType refused : new UpgradeType[]{UpgradeType.SPEED, UpgradeType.OVERCLOCK, UpgradeType.STACK}) {
            ItemStack upgrade = ModItems.upgradeItem(refused).toStack();
            helper.assertTrue(!generator.upgradeInventory().isItemValid(refused.ordinal(), upgrade),
                    refused + " should be refused by a generator the world drives");
            helper.assertTrue(generator.upgradeInventory().insertItem(refused.ordinal(), upgrade, true).getCount() == 1,
                    refused + " must not be insertable either");
            helper.assertValueEqual(generator.maxUpgrades(refused), 0, refused + " should count for nothing");
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        HydrostaticGeneratorMenu menu = new HydrostaticGeneratorMenu(1, player.getInventory(), generator);
        helper.assertValueEqual(menu.upgradeSlotCount(), 1,
                "the screen should show one slot, not three it would refuse");

        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void movesPowerButNeverItems(GameTestHelper helper) {
        helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get());
        BlockPos worldPos = helper.absolutePos(GENERATOR);

        for (Direction side : Direction.values()) {
            IEnergyStorage energy = helper.getLevel()
                    .getCapability(Capabilities.EnergyStorage.BLOCK, worldPos, side);
            helper.assertTrue(energy != null, "every face should expose energy, " + side + " did not");
            helper.assertTrue(energy.canExtract(), "faces default to output, " + side + " did not");

            IItemHandler items = helper.getLevel()
                    .getCapability(Capabilities.ItemHandler.BLOCK, worldPos, side);
            helper.assertTrue(items == null,
                    "a generator with no inventory must expose none, " + side + " did");
        }

        helper.succeed();
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void menuReportsTheShaftToTheScreen(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get());
                    flood(helper, 3);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    HydrostaticGeneratorBlockEntity generator = generator(helper);
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    HydrostaticGeneratorMenu menu =
                            new HydrostaticGeneratorMenu(1, player.getInventory(), generator);

                    helper.assertValueEqual(menu.column(), 3, "the screen should see the real column");
                    helper.assertValueEqual(menu.maxColumn(), MAX_COLUMN, "and the depth it is capped at");
                    helper.assertValueEqual(menu.energyPerTick(), generator.generatedEnergyPerTick(),
                            "and the live output, warm-up and all");
                    helper.assertValueEqual(menu.sideMode(TransferKind.ENERGY, RelativeSide.TOP), IoMode.OUTPUT,
                            "power leaves every face by default");
                    helper.assertValueEqual(menu.sideMode(TransferKind.ITEM, RelativeSide.TOP), IoMode.DISABLED,
                            "and no face moves items");
                })
                .thenSucceed();
    }

    /**
     * The warm-up ramp: a shaft just flooded is worth less than its rating and climbs towards it,
     * but never past it. Without this a generator the world pays would be flat from the first tick.
     */
    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void aColdShaftClimbsTowardsItsRatingWithoutPassingIt(GameTestHelper helper) {
        int[] cold = new int[1];
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(GENERATOR, ModBlocks.HYDROSTATIC_GENERATOR.get());
                    flood(helper, MAX_COLUMN);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    HydrostaticGeneratorBlockEntity generator = generator(helper);
                    cold[0] = generator.generatedEnergyPerTick();
                    helper.assertTrue(cold[0] > 0, "a cold generator still earns something");
                    helper.assertTrue(cold[0] < generator.ratedEnergyPerTick(),
                            "but less than the shaft is rated for, got " + cold[0]);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    HydrostaticGeneratorBlockEntity generator = generator(helper);
                    helper.assertTrue(generator.generatedEnergyPerTick() > cold[0],
                            "and it should have warmed up by now");
                    helper.assertTrue(generator.generatedEnergyPerTick() <= generator.ratedEnergyPerTick(),
                            "a warm-up must never conjure more than the rating");
                })
                .thenSucceed();
    }

    /** Stacks still water directly on top of the generator. */
    private static void flood(GameTestHelper helper, int height) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(GENERATOR.above(y), Blocks.WATER);
        }
    }

    private static HydrostaticGeneratorBlockEntity generator(GameTestHelper helper) {
        if (helper.getBlockEntity(GENERATOR) instanceof HydrostaticGeneratorBlockEntity generator) {
            return generator;
        }
        throw new IllegalStateException("no hydrostatic generator at " + GENERATOR);
    }
}
