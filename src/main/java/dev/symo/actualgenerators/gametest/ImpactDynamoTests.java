package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.ImpactDynamoBlockEntity;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.ImpactDynamoMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Tests for the generator that is paid by things landing on it.
 *
 * <p>The promises worth pinning down are the two halves of a catch: the block must not place
 * itself and must not drop as an item either — it has to end up inside the dynamo — and the
 * energy has to follow how far it actually fell. The refusal path matters just as much: a dynamo
 * with nowhere to put the salvage has to let the block land, not quietly delete it.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class ImpactDynamoTests {
    private static final String TOWER = "tower";
    private static final String PLATFORM = "platform";
    private static final BlockPos DYNAMO = new BlockPos(2, 1, 2);
    /** Directly on the dynamo's lid: where a block lands if it is not caught. */
    private static final BlockPos LANDING = new BlockPos(2, 2, 2);
    private static final BlockPos DROP_FROM = new BlockPos(2, 12, 2);

    /** Matches the config defaults; the tests assert against real numbers, not the config. */
    private static final int FE_PER_BLOCK = 40;
    private static final int MAX_FALL = 64;
    /** A dynamo that has not been fed yet is cold, and generatorWarmupFloor defaults to a half. */
    private static final int COLD_FE_PER_BLOCK = FE_PER_BLOCK / 2;

    private ImpactDynamoTests() {
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void catchesFallingSandAndKeepsBoth(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
                    helper.setBlock(DROP_FROM, Blocks.SAND);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    ImpactDynamoBlockEntity dynamo = dynamo(helper);

                    helper.assertBlockPresent(Blocks.AIR, LANDING);
                    helper.assertItemEntityCountIs(Items.SAND, LANDING, 4.0, 0);

                    ItemStack salvaged = dynamo.outputHandler().getStackInSlot(0);
                    helper.assertTrue(salvaged.is(Items.SAND), "the sand should be inside the dynamo");
                    helper.assertValueEqual(salvaged.getCount(), 1, "one block fell, one block banked");

                    int fell = dynamo.lastFallDistance();
                    helper.assertTrue(fell >= 8 && fell <= MAX_FALL,
                            "a ten-block drop should read as roughly ten, got " + fell);
                    helper.assertValueEqual(dynamo.lastImpactEnergy(), fell * COLD_FE_PER_BLOCK,
                            "the catch should be worth the distance times the cold per-block rate");
                    helper.assertValueEqual(dynamo.energyStorage().getEnergyStored(), fell * COLD_FE_PER_BLOCK,
                            "and that is what should be in the buffer");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void aLongerDropIsWorthMore(GameTestHelper helper) {
        BlockPos lowDrop = new BlockPos(2, 6, 2);
        int[] shallow = new int[1];
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
                    helper.setBlock(lowDrop, Blocks.SAND);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    shallow[0] = dynamo(helper).lastImpactEnergy();
                    helper.assertTrue(shallow[0] > 0, "the shallow drop should have paid something");
                    helper.setBlock(DROP_FROM, Blocks.GRAVEL);
                })
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(dynamo(helper).lastImpactEnergy() > shallow[0],
                        "falling further has to pay better, or height buys nothing"))
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void refusesTheCatchWhenTheSalvageHasNowhereToGo(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
                    IItemHandler outputs = dynamo(helper).outputHandler();
                    for (int slot = 0; slot < outputs.getSlots(); slot++) {
                        outputs.insertItem(slot, new ItemStack(Items.COBBLESTONE, 64), false);
                    }
                    helper.setBlock(DROP_FROM, Blocks.SAND);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    // Backed up rather than destructive: the block lands where a player can see it.
                    helper.assertBlockPresent(Blocks.SAND, LANDING);
                    helper.assertValueEqual(dynamo(helper).energyStorage().getEnergyStored(), 0,
                            "a dynamo that could not take the block must not take the energy either");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void aDropIsCappedAtTheConfiguredDistance(GameTestHelper helper) {
        helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
        ImpactDynamoBlockEntity dynamo = dynamo(helper);

        // Straight at the block entity: a fall long enough to test the cap does not fit in a
        // structure, and the cap is arithmetic rather than physics.
        FallingBlockEntity falling = FallingBlockEntity.fall(
                helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 2)), Blocks.SAND.defaultBlockState());
        falling.discard();

        helper.assertTrue(dynamo.absorb(falling, 1000.0F), "it should still take an absurd drop");
        helper.assertValueEqual(dynamo.lastFallDistance(), MAX_FALL, "but only count it up to the cap");
        helper.assertValueEqual(dynamo.lastImpactEnergy(), MAX_FALL * COLD_FE_PER_BLOCK,
                "so free height cannot become free power");

        helper.succeed();
    }

    /**
     * The warm-up ramp. A dynamo pays the cold rate until it has been working, climbs to the full
     * rate while it is being fed, and never goes past it.
     */
    @GameTest(template = PLATFORM)
    public static void aWarmedDynamoPaysTheFullRateAndNoMore(GameTestHelper helper) {
        helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
        ImpactDynamoBlockEntity dynamo = dynamo(helper);

        helper.assertValueEqual(dynamo.energyForFall(MAX_FALL), MAX_FALL * COLD_FE_PER_BLOCK,
                "a dynamo nobody has fed yet pays the cold rate");

        // Banked directly: the alternative is dropping blocks on it for a real minute.
        dynamo.overclockState().setWorkedTicks(Integer.MAX_VALUE);
        helper.assertValueEqual(dynamo.energyForFall(MAX_FALL), MAX_FALL * FE_PER_BLOCK,
                "and a fully warmed one pays exactly the rating, never more");

        helper.succeed();
    }

    /** A landing counts as work for a while, so a shaft that keeps feeding keeps the dynamo warm. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aLandingKeepsTheDynamoWarmAndThenItCoolsOff(GameTestHelper helper) {
        int[] warmed = new int[1];
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
                    ImpactDynamoBlockEntity dynamo = dynamo(helper);
                    FallingBlockEntity falling = FallingBlockEntity.fall(
                            helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 2)),
                            Blocks.SAND.defaultBlockState());
                    falling.discard();
                    helper.assertTrue(dynamo.absorb(falling, 10.0F), "it should take the drop");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    warmed[0] = dynamo(helper).overclockState().workedTicks();
                    helper.assertTrue(warmed[0] > 0, "a landing should have started the ramp climbing");
                })
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(
                        dynamo(helper).overclockState().workedTicks() < warmed[0],
                        "and a dynamo nothing is falling on should cool back down"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void refusesUpgradesItCannotUse(GameTestHelper helper) {
        helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
        ImpactDynamoBlockEntity dynamo = dynamo(helper);

        helper.assertTrue(dynamo.upgradeInventory()
                        .isItemValid(UpgradeType.ENERGY.ordinal(), new ItemStack(ModItems.ENERGY_UPGRADE.get())),
                "energy upgrades still raise its buffer and throughput");

        // Nothing about a landing happens on a schedule, so there is no speed to buy.
        for (UpgradeType refused : new UpgradeType[]{UpgradeType.SPEED, UpgradeType.OVERCLOCK, UpgradeType.STACK}) {
            helper.assertValueEqual(dynamo.maxUpgrades(refused), 0, refused + " should count for nothing");
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ImpactDynamoMenu menu = new ImpactDynamoMenu(1, player.getInventory(), dynamo);
        helper.assertValueEqual(menu.upgradeSlotCount(), 1, "the screen should show one slot");
        helper.assertValueEqual(menu.maxFallDistance(), MAX_FALL, "and the drop the gauge is scaled against");

        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void salvageLeavesByTheBottomOnly(GameTestHelper helper) {
        helper.setBlock(DYNAMO, ModBlocks.IMPACT_DYNAMO.get());
        ImpactDynamoBlockEntity dynamo = dynamo(helper);
        BlockPos worldPos = helper.absolutePos(DYNAMO);

        helper.assertValueEqual(dynamo.sideConfig().get(TransferKind.ITEM, RelativeSide.BOTTOM), IoMode.OUTPUT,
                "salvage should fall out of the bottom by default");

        for (Direction side : Direction.values()) {
            IEnergyStorage energy = helper.getLevel()
                    .getCapability(Capabilities.EnergyStorage.BLOCK, worldPos, side);
            helper.assertTrue(energy != null, "every face should expose energy, " + side + " did not");
        }

        IItemHandler bottom = helper.getLevel()
                .getCapability(Capabilities.ItemHandler.BLOCK, worldPos, Direction.DOWN);
        helper.assertTrue(bottom != null, "the bottom face should expose the salvage buffer");
        helper.assertTrue(bottom.insertItem(0, new ItemStack(Items.COBBLESTONE), true).getCount() == 1,
                "but nothing may be pushed back in through it");

        IItemHandler top = helper.getLevel()
                .getCapability(Capabilities.ItemHandler.BLOCK, worldPos, Direction.UP);
        helper.assertTrue(top == null, "the lid is for landing on, not for piping into");

        helper.succeed();
    }

    private static ImpactDynamoBlockEntity dynamo(GameTestHelper helper) {
        if (helper.getBlockEntity(DYNAMO) instanceof ImpactDynamoBlockEntity dynamo) {
            return dynamo;
        }
        throw new IllegalStateException("no impact dynamo at " + DYNAMO);
    }
}
