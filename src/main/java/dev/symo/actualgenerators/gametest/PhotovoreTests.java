package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.PhotovoreBlockEntity;
import dev.symo.actualgenerators.menu.PhotovoreMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the generator that eats the room's lighting.
 *
 * <p>The interesting promises here are about restraint: it must eat only what the food tag lists,
 * it must leave everything else alone, and it must pay by how brightly the block burned.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class PhotovoreTests {
    private static final String TOWER = "tower";
    private static final BlockPos PHOTOVORE = new BlockPos(2, 1, 2);

    /** Matches the config default. */
    private static final int FE_PER_LIGHT = 300;

    private PhotovoreTests() {
    }

    @GameTest(template = TOWER, timeoutTicks = 600)
    public static void eatsATorchAndPaysByItsLight(GameTestHelper helper) {
        BlockPos torch = new BlockPos(2, 2, 2);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(PHOTOVORE, ModBlocks.PHOTOVORE.get());
                    helper.setBlock(torch, Blocks.TORCH);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(photovore(helper).foodInRange(), 1,
                        "it should have noticed the torch"))
                // One meal is 200 ticks at base speed.
                .thenIdle(220)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, torch);

                    int light = Blocks.TORCH.defaultBlockState().getLightEmission();
                    helper.assertValueEqual(photovore(helper).energyStorage().getEnergyStored(),
                            light * FE_PER_LIGHT, "a torch is worth its light level");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 600)
    public static void brighterFoodIsWorthMore(GameTestHelper helper) {
        BlockPos glowstone = new BlockPos(2, 2, 2);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(PHOTOVORE, ModBlocks.PHOTOVORE.get());
                    helper.setBlock(glowstone, Blocks.GLOWSTONE);
                })
                .thenIdle(230)
                .thenExecute(() -> {
                    int glow = Blocks.GLOWSTONE.defaultBlockState().getLightEmission();
                    int torch = Blocks.TORCH.defaultBlockState().getLightEmission();
                    helper.assertTrue(glow > torch, "glowstone is the brighter block");
                    helper.assertValueEqual(photovore(helper).energyStorage().getEnergyStored(),
                            glow * FE_PER_LIGHT, "and should pay more than a torch would");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 600)
    public static void leavesAloneWhatIsNotOnTheMenu(GameTestHelper helper) {
        BlockPos beacon = new BlockPos(2, 2, 2);
        BlockPos stone = new BlockPos(3, 2, 2);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(PHOTOVORE, ModBlocks.PHOTOVORE.get());
                    // A beacon is bright, valuable, and must never be food.
                    helper.setBlock(beacon, Blocks.BEACON);
                    helper.setBlock(stone, Blocks.STONE);
                })
                .thenIdle(240)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.BEACON, beacon);
                    helper.assertBlockPresent(Blocks.STONE, stone);
                    helper.assertValueEqual(photovore(helper).foodInRange(), 0, "neither block is food");
                    helper.assertValueEqual(photovore(helper).energyStorage().getEnergyStored(), 0,
                            "and nothing was earned");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void generatesNothingInAnEmptyRoom(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(PHOTOVORE, ModBlocks.PHOTOVORE.get()))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(photovore(helper).foodInRange(), 0, "nothing to eat");
                    helper.assertValueEqual(photovore(helper).energyStorage().getEnergyStored(), 0,
                            "and so nothing generated");
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER)
    public static void knowsWhatCountsAsFood(GameTestHelper helper) {
        helper.assertTrue(PhotovoreBlockEntity.isFood(Blocks.TORCH.defaultBlockState()), "torches are food");
        helper.assertTrue(PhotovoreBlockEntity.isFood(Blocks.GLOWSTONE.defaultBlockState()), "glowstone is food");
        helper.assertTrue(PhotovoreBlockEntity.isFood(Blocks.SEA_LANTERN.defaultBlockState()), "sea lanterns are food");

        // Fire would regrow off netherrack forever, so it is deliberately not on the tag.
        helper.assertTrue(!PhotovoreBlockEntity.isFood(Blocks.FIRE.defaultBlockState()), "fire must not be food");
        helper.assertTrue(!PhotovoreBlockEntity.isFood(Blocks.LAVA.defaultBlockState()), "lava must not be food");
        helper.assertTrue(!PhotovoreBlockEntity.isFood(Blocks.BEACON.defaultBlockState()), "beacons must not be food");
        helper.assertTrue(!PhotovoreBlockEntity.isFood(Blocks.STONE.defaultBlockState()), "dark blocks are not food");

        helper.succeed();
    }

    @GameTest(template = TOWER, timeoutTicks = 300)
    public static void eatsTheNearestLightFirst(GameTestHelper helper) {
        BlockPos near = new BlockPos(2, 2, 2);
        BlockPos far = new BlockPos(2, 5, 2);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(PHOTOVORE, ModBlocks.PHOTOVORE.get());
                    helper.setBlock(near, Blocks.GLOWSTONE);
                    helper.setBlock(far, Blocks.GLOWSTONE);
                })
                .thenIdle(230)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, near);
                    helper.assertBlockPresent(Blocks.GLOWSTONE, far);
                })
                .thenSucceed();
    }

    @GameTest(template = TOWER, timeoutTicks = 200)
    public static void menuReportsTheRoomToTheScreen(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(PHOTOVORE, ModBlocks.PHOTOVORE.get());
                    helper.setBlock(new BlockPos(2, 2, 2), Blocks.TORCH);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    PhotovoreMenu menu = new PhotovoreMenu(1, player.getInventory(), photovore(helper));

                    helper.assertValueEqual(menu.foodInRange(), 1, "the screen should see the torch");
                    helper.assertValueEqual(menu.mealEnergy(),
                            Blocks.TORCH.defaultBlockState().getLightEmission() * FE_PER_LIGHT,
                            "and what the next meal is worth");
                    helper.assertValueEqual(menu.upgradeSlotCount(), 4,
                            "it burns fuel, so all four upgrades apply");
                })
                .thenSucceed();
    }

    private static PhotovoreBlockEntity photovore(GameTestHelper helper) {
        if (helper.getBlockEntity(PHOTOVORE) instanceof PhotovoreBlockEntity photovore) {
            return photovore;
        }
        throw new IllegalStateException("no photovore at " + PHOTOVORE);
    }
}
