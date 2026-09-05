package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.SpawnerSiphonBlockEntity;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.SpawnerSiphonMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the generator that repurposes a mob spawner.
 *
 * <p>Two promises carry this machine. The first is that its rate comes from the spawner rather
 * than from numbers written down in our code — that is what lets a spawner another mod has
 * upgraded pay more without any integration. The second is that the arrangement is reversible:
 * take the siphon off and the spawner has to work again.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class SpawnerSiphonTests {
    private static final String PLATFORM = "platform";
    private static final BlockPos SIPHON = new BlockPos(2, 1, 2);
    private static final BlockPos SPAWNER = new BlockPos(3, 1, 2);

    /** Matches the config default. */
    private static final int FE_PER_SPAWN = 5_000;

    /** Vanilla's own defaults, which are what a freshly placed spawner reports. */
    private static final int VANILLA_SPAWN_COUNT = 4;
    private static final int VANILLA_MIN_DELAY = 200;
    private static final int VANILLA_AVERAGE_DELAY = 500;

    private SpawnerSiphonTests() {
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void readsItsRateOffTheSpawner(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SPAWNER, Blocks.SPAWNER);
                    helper.setBlock(SIPHON, ModBlocks.SPAWNER_SIPHON.get());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    SpawnerSiphonBlockEntity siphon = siphon(helper);
                    helper.assertTrue(siphon.hasSpawner(), "it should have found the spawner beside it");
                    helper.assertValueEqual(siphon.spawnCount(), VANILLA_SPAWN_COUNT,
                            "the mob count comes from the spawner");
                    helper.assertValueEqual(siphon.averageDelay(), VANILLA_AVERAGE_DELAY,
                            "and so does the delay between attempts");
                    helper.assertValueEqual(siphon.ratedEnergyPerTick(),
                            FE_PER_SPAWN * VANILLA_SPAWN_COUNT / VANILLA_AVERAGE_DELAY,
                            "a vanilla spawner should be rated at 40 FE/t");
                    helper.assertTrue(siphon.energyStorage().getEnergyStored() > 0,
                            "and it should already be filling its buffer");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void holdsTheSpawnerShutWhileItRuns(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SPAWNER, Blocks.SPAWNER);
                    helper.setBlock(SIPHON, ModBlocks.SPAWNER_SIPHON.get());
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertValueEqual(spawnerDelay(helper), siphon(helper).suppressedDelay(),
                        "the countdown should have been pushed out of reach"))
                // Long enough that an unsuppressed spawner would have fired several times over.
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(spawnerDelay(helper) > VANILLA_MIN_DELAY / 4,
                        "each reading has to push the countdown back out again"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void givesTheSpawnerBackWhenItIsBroken(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SPAWNER, Blocks.SPAWNER);
                    helper.setBlock(SIPHON, ModBlocks.SPAWNER_SIPHON.get());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(spawnerDelay(helper) > VANILLA_MIN_DELAY / 2,
                            "it should be suppressed before we break it");
                    helper.setBlock(SIPHON, Blocks.AIR);
                })
                .thenExecute(() -> helper.assertValueEqual(spawnerDelay(helper), VANILLA_MIN_DELAY,
                        "breaking the siphon must hand the spawner back, not leave it dead"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void aBetterSpawnerPaysBetter(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SPAWNER, Blocks.SPAWNER);
                    helper.setBlock(SIPHON, ModBlocks.SPAWNER_SIPHON.get());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertValueEqual(siphon(helper).ratedEnergyPerTick(), 40, "vanilla is the baseline");
                    // Exactly what an upgrade from another mod does: raise the spawner's own numbers.
                    SpawnerBlockEntity spawner = spawner(helper);
                    BaseSpawner base = spawner.getSpawner();
                    CompoundTag tag = base.save(new CompoundTag());
                    tag.putShort("SpawnCount", (short) (VANILLA_SPAWN_COUNT * 2));
                    base.load(null, spawner.getBlockPos(), tag);
                })
                .thenIdle(50)
                .thenExecute(() -> helper.assertValueEqual(siphon(helper).ratedEnergyPerTick(), 80,
                        "twice the mobs must be worth twice the power, with no code that knows why"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void generatesNothingWithoutASpawner(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(SIPHON, ModBlocks.SPAWNER_SIPHON.get()))
                .thenIdle(60)
                .thenExecute(() -> {
                    SpawnerSiphonBlockEntity siphon = siphon(helper);
                    helper.assertTrue(!siphon.hasSpawner(), "there is nothing to tap");
                    helper.assertValueEqual(siphon.generatedEnergyPerTick(), 0, "so it earns nothing");
                    helper.assertValueEqual(siphon.energyStorage().getEnergyStored(), 0, "and stores nothing");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void menuReportsTheSpawnerToTheScreen(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SPAWNER, Blocks.SPAWNER);
                    helper.setBlock(SIPHON, ModBlocks.SPAWNER_SIPHON.get());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    SpawnerSiphonBlockEntity siphon = siphon(helper);
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    SpawnerSiphonMenu menu = new SpawnerSiphonMenu(1, player.getInventory(), siphon);

                    helper.assertTrue(menu.hasSpawner(), "the screen should see the spawner");
                    helper.assertValueEqual(menu.energyPerTick(), siphon.generatedEnergyPerTick(),
                            "and the live rate, warm-up and all");
                    helper.assertValueEqual(menu.spawnCount(), VANILLA_SPAWN_COUNT, "and the mob count");
                    helper.assertValueEqual(menu.averageDelay(), VANILLA_AVERAGE_DELAY, "and the delay");

                    // It taps a spawner rather than burning anything, so there is no speed to buy.
                    helper.assertValueEqual(menu.upgradeSlotCount(), 1, "one upgrade slot, not four");
                    helper.assertValueEqual(siphon.maxUpgrades(UpgradeType.SPEED), 0,
                            "speed upgrades would conjure spawns that never happened");
                })
                .thenSucceed();
    }

    private static int spawnerDelay(GameTestHelper helper) {
        return spawner(helper).getSpawner().save(new CompoundTag()).getShort("Delay");
    }

    private static SpawnerBlockEntity spawner(GameTestHelper helper) {
        if (helper.getBlockEntity(SPAWNER) instanceof SpawnerBlockEntity spawner) {
            return spawner;
        }
        throw new IllegalStateException("no spawner at " + SPAWNER);
    }

    private static SpawnerSiphonBlockEntity siphon(GameTestHelper helper) {
        if (helper.getBlockEntity(SIPHON) instanceof SpawnerSiphonBlockEntity siphon) {
            return siphon;
        }
        throw new IllegalStateException("no spawner siphon at " + SIPHON);
    }
}
