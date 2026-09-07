package dev.symo.actualgenerators.gametest;

import java.util.List;
import dev.symo.actualgenerators.menu.MultiblockControllerMenu;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity.Extent;
import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.GeothermalTapBlockEntity;
import dev.symo.actualgenerators.generator.HeatPockets;
import dev.symo.actualgenerators.item.ThermalProbeItem;
import dev.symo.actualgenerators.machine.multiblock.CoilPiece;
import dev.symo.actualgenerators.machine.multiblock.MultiblockCasingBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlock;
import dev.symo.actualgenerators.menu.GeothermalTapMenu;
import dev.symo.actualgenerators.menu.MachineMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModFluids;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Geothermal Fissure Tap, the plate it stands on, and the heat pockets it draws on.
 *
 * <p>What matters: nothing happens without the plate and the cap; a formed tap makes its rating
 * only where there is heat under it and only near the world floor; the pocket goes down by what
 * the tap took; the corium comes out as a by-product, through a fluid hatch, a bucket on the
 * controller or a bucket on the window's tank, and never goes in; a bigger plate is a bigger
 * bore; and the probe says what is under a chunk before anything is built.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class GeothermalTapTests {
    private static final String ARENA = "arena";
    /** The plate's near corner; the arena's floor is y=1 and the plate replaces it. */
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);

    /** Matches the config defaults. */
    private static final int FE_PER_PLATE = 20;
    private static final int TANK_PER_PLATE = 8_000;
    private static final long CAPACITY_PER_PLATE = 40_000L;
    private static final long POCKET = 50_000_000L;

    private GeothermalTapTests() {
    }

    @GameTest(template = ARENA)
    public static void theRatingFallsOffWithHeightAboveTheWorldFloor(GameTestHelper helper) {
        int floor = -64;
        helper.assertValueEqual(GeothermalTapBlockEntity.depthFactor(floor, floor), 1.0, "on the floor: full");
        helper.assertValueEqual(GeothermalTapBlockEntity.depthFactor(floor + 8, floor), 1.0, "eight up: still full");
        helper.assertValueEqual(GeothermalTapBlockEntity.depthFactor(floor + 28, floor), 0.5, "halfway to the reach: half");
        helper.assertValueEqual(GeothermalTapBlockEntity.depthFactor(floor + 48, floor), 0.0, "at the reach: nothing");
        helper.assertValueEqual(GeothermalTapBlockEntity.depthFactor(64, floor), 0.0, "and nothing at sea level");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void aPocketIsDrawnDownAndRegrowsAtATrickle(GameTestHelper helper) {
        // Far from every test structure, so no tap shares it; set outright, so the seed's roll does not matter.
        ServerLevel level = helper.getLevel();
        HeatPockets pockets = HeatPockets.get(level);
        ChunkPos chunk = new ChunkPos(100_000, 100_000);
        long now = level.getGameTime();
        pockets.set(chunk, 1_000, 1_000_000, now);
        helper.assertValueEqual(pockets.draw(level, chunk, 700, now), 700L, "a draw takes what it asked for");
        helper.assertValueEqual(pockets.draw(level, chunk, 700, now), 300L, "and then what is left");
        long regen = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_POCKET_REGEN, 50);
        helper.assertValueEqual(pockets.remaining(level, chunk, now + 100), Math.min(1_000_000L, 100 * regen),
                "a hundred ticks later it has grown back the trickle");
        helper.assertValueEqual(pockets.capacity(level, chunk), 1_000_000L, "the capacity is what it was rolled at");

        ChunkPos barren = new ChunkPos(100_001, 100_000);
        pockets.set(barren, 0, 0, now);
        helper.assertValueEqual(pockets.draw(level, barren, 700, now), 0L, "a chunk with no pocket gives nothing");
        helper.assertValueEqual(pockets.remaining(level, barren, now + 10_000), 0L, "and never grows one");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void theProbeReadsTheChunkBeforeAnythingIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HeatPockets pockets = HeatPockets.get(level);
        long now = level.getGameTime();
        ChunkPos hot = new ChunkPos(100_002, 100_000);
        ChunkPos barren = new ChunkPos(100_003, 100_000);
        pockets.set(hot, 30_000_000L, 40_000_000L, now);
        pockets.set(barren, 0, 0, now);

        Component reading = ThermalProbeItem.reading(level, hot);
        helper.assertTrue(reading.getString().contains("30.0M") && reading.getString().contains("40.0M")
                        && reading.getString().contains("75"),
                "the probe says what is down there and how full it is, but said: " + reading.getString());
        helper.assertTrue(ThermalProbeItem.reading(level, barren).getString().contains("No heat pocket"),
                "and that there is nothing under a barren chunk");

        // The gesture: a use in the air is what a player does with it, and it must count as one.
        ServerPlayer player = LinkPortTests.testPlayer(helper);
        ItemStack probe = ModItems.THERMAL_PROBE.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, probe);
        helper.assertTrue(player.gameMode.useItem(player, level, probe, InteractionHand.MAIN_HAND).consumesAction(),
                "using the probe is a use");
        helper.assertTrue(player.getMainHandItem().is(ModItems.THERMAL_PROBE.get()), "and it is not used up");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void nothingFormsWithoutThePlateTheCapOrTheCapCentredOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tap = MultiblockRigs.tapPos(CORNER, 5, 5);
        ChunkPos chunk = new ChunkPos(helper.absolutePos(tap));
        BlockPos plateCorner = CORNER;
        BlockPos capCorner = MultiblockRigs.capCentre(CORNER, 5, 5).offset(1, 0, 1);

        helper.startSequence()
                .thenExecute(() -> {
                    HeatPockets.get(level).set(chunk, POCKET, POCKET, level.getGameTime());
                    // The tap alone, on nothing.
                    helper.setBlock(tap, ModBlocks.GEOTHERMAL_TAP.get().defaultBlockState()
                            .setValue(MultiblockControllerBlock.FACING, Direction.NORTH));
                })
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    helper.assertFalse(entity.isFormed(), "a tap on nothing is not formed");
                    helper.assertValueEqual(entity.ratedEnergyPerTick(), 0, "and rates nothing");
                    // The plate with a corner missing, and the cap.
                    MultiblockRigs.buildTap(helper, CORNER, 5, 5, pos -> !pos.equals(plateCorner));
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertFalse(tap(helper, tap).isFormed(), "a plate with a block missing is no plate");
                    helper.setBlock(plateCorner, ModBlocks.GEOTHERMAL_CASING.get());
                    helper.setBlock(capCorner, Blocks.AIR);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertFalse(tap(helper, tap).isFormed(), "a cap with a block missing is no cap");
                    helper.setBlock(capCorner, ModBlocks.MACHINE_CASING.get());
                    // One more row of plate on the east side only: the cap is no longer centred.
                    for (int z = 0; z < 5; z++) {
                        helper.setBlock(CORNER.offset(5, 0, z), ModBlocks.GEOTHERMAL_CASING.get());
                    }
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertFalse(tap(helper, tap).isFormed(), "a plate the cap is not centred on is no plate");
                    for (int z = 0; z < 5; z++) {
                        helper.setBlock(CORNER.offset(5, 0, z), Blocks.AIR);
                    }
                })
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    helper.assertTrue(entity.isFormed(), "plate, cap and tap: formed");
                    helper.assertValueEqual(entity.plates(), 25, "twenty-five plates");
                    helper.assertBlockProperty(CORNER, MultiblockCasingBlock.FORMED, formed -> formed, "the plate wears the formed look");
                    helper.assertBlockProperty(capCorner, MultiblockCasingBlock.FORMED, formed -> formed, "and so does the cap");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aFormedTapDrawsOnItsChunksPocketAndLeavesCoriumBehind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tap = MultiblockRigs.tapPos(CORNER, 5, 5);
        BlockPos hatch = MultiblockRigs.capCentre(CORNER, 5, 5).east();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(tap));
        ServerPlayer player = LinkPortTests.testPlayer(helper);
        long perMillibucket = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_FE_PER_CORIUM_MB, 100_000);
        long[] storedAtStart = new long[1];

        helper.startSequence()
                .thenExecute(() -> {
                    MultiblockRigs.buildTap(helper, CORNER, 5, 5, pos -> !pos.equals(hatch));
                    helper.setBlock(hatch, ModBlocks.FLUID_HATCH.get());
                })
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    helper.assertTrue(entity.isFormed(), "a 5x5 plate under the cap is a tap");
                    // Every pocket the plate lies over, so the seed's roll for a neighbouring chunk plays no part.
                    helper.assertTrue(entity.tappedChunks().contains(chunk), "the tap's own chunk is under the plate");
                    for (ChunkPos under : entity.tappedChunks()) {
                        HeatPockets.get(level).set(under, POCKET, POCKET, level.getGameTime());
                    }
                    // The fee one FE short of a millibucket, the way a saved tap comes back: the next FE pays out.
                    HolderLookup.Provider registries = level.registryAccess();
                    CompoundTag saved = entity.saveWithoutMetadata(registries);
                    saved.putLong("Fee", perMillibucket - 1);
                    entity.loadWithComponents(saved, registries);
                    storedAtStart[0] = entity.energyStorage().stored();
                })
                .thenExecuteAfter(100, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    // The gametest world is flat and its structures stand a few blocks over the floor: full depth.
                    helper.assertValueEqual(entity.ratedEnergyPerTick(), FE_PER_PLATE * 25, "twenty-five plates at full depth");
                    long stored = entity.energyStorage().stored();
                    long made = stored - storedAtStart[0];
                    helper.assertTrue(made > 0, "a hundred ticks over a pocket made something");
                    long remaining = HeatPockets.get(level).remaining(level, chunk, level.getGameTime());
                    helper.assertTrue(remaining < POCKET, "and the pocket is down by it");
                    helper.assertTrue(POCKET - remaining <= stored, "never by more than what was made, regrowth aside");

                    int had = entity.corium().getAmount();
                    helper.assertTrue(entity.corium().getFluid() == ModFluids.CORIUM.get(), "corium came");
                    helper.assertValueEqual((long) had, (perMillibucket - 1 + made) / perMillibucket,
                            "a millibucket for every " + perMillibucket + " FE made, the fee carried over");
                    helper.assertTrue(had >= 1, "at least the one the fee was short of");
                    helper.assertValueEqual(entity.tankCapacity(), TANK_PER_PLATE * 25, "twenty-five plates: twenty-five plates' tank");
                    helper.assertValueEqual(entity.energyStorage().capacity(), CAPACITY_PER_PLATE * 25, "and buffer");

                    helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(tap), Direction.NORTH) == null,
                            "the controller's own faces move nothing");
                    IFluidHandler door = level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(hatch), Direction.EAST);
                    helper.assertTrue(door != null, "the fluid hatch is the door");
                    helper.assertValueEqual(door.getTankCapacity(0), TANK_PER_PLATE * 25, "and shows the tank behind it");
                    helper.assertValueEqual(door.fill(new FluidStack(ModFluids.CORIUM.get(), 1_000), IFluidHandler.FluidAction.EXECUTE),
                            0, "but nothing goes in");
                    FluidStack drained = door.drain(new FluidStack(ModFluids.CORIUM.get(), 1), IFluidHandler.FluidAction.EXECUTE);
                    helper.assertValueEqual(drained.getAmount(), 1, "and the corium comes out");
                    helper.assertValueEqual(entity.corium().getAmount(), had - 1, "out of the tank");

                    // An empty bucket that finds less than a bucket's worth falls through to the window.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    LinkPortTests.rightClick(helper, player, tap, false);
                    helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the bucket stays empty");
                    helper.assertTrue(player.containerMenu instanceof GeothermalTapMenu,
                            "and the click opened the tap's window, but " + player.containerMenu.getClass().getSimpleName());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aBiggerPlateIsABiggerBore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int size = 7;
        int plates = 49;
        BlockPos tap = MultiblockRigs.tapPos(CORNER, size, size);
        ChunkPos chunk = new ChunkPos(helper.absolutePos(tap));

        helper.startSequence()
                .thenExecute(() -> {
                    HeatPockets.get(level).set(chunk, POCKET, POCKET, level.getGameTime());
                    MultiblockRigs.buildTap(helper, CORNER, size, size, pos -> true);
                })
                .thenExecuteAfter(40, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    helper.assertTrue(entity.isFormed(), "a 7x7 plate stands");
                    helper.assertValueEqual(entity.plates(), plates, "forty-nine plates");
                    helper.assertValueEqual(entity.structure().describe(), "7×2×7", "two high: the plate and the cap");
                    helper.assertValueEqual(entity.ratedEnergyPerTick(), FE_PER_PLATE * plates, "and every one is a share of the rating");
                    helper.assertValueEqual(entity.tankCapacity(), TANK_PER_PLATE * plates, "the tank grows with the plate");
                    helper.assertValueEqual(entity.energyStorage().capacity(), CAPACITY_PER_PLATE * plates, "and so does the buffer");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aBucketOnTheWindowsTankOrOnTheControllerTakesCorium(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tap = MultiblockRigs.tapPos(CORNER, 5, 5);
        ChunkPos chunk = new ChunkPos(helper.absolutePos(tap));
        ServerPlayer player = LinkPortTests.testPlayer(helper);

        helper.startSequence()
                .thenExecute(() -> {
                    HeatPockets.get(level).set(chunk, POCKET, POCKET, level.getGameTime());
                    MultiblockRigs.buildTap(helper, CORNER, 5, 5, pos -> true);
                })
                .thenExecuteAfter(20, () -> {
                    // Two and a half buckets, as if it had run for an hour: the tank is saved with the block,
                    // so it is set the way a world load sets it.
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    HolderLookup.Provider registries = level.registryAccess();
                    CompoundTag saved = entity.saveWithoutMetadata(registries);
                    CompoundTag tank = new CompoundTag();
                    tank.put("Fluid", new FluidStack(ModFluids.CORIUM.get(), 2_500).save(registries));
                    saved.put("Tank", tank);
                    entity.loadWithComponents(saved, registries);
                    helper.assertValueEqual(entity.corium().getAmount(), 2_500, "the tank holds what it was loaded with");
                })
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    int had = entity.corium().getAmount();
                    helper.assertTrue(had >= 2_000, "still at least two buckets in the tank, got " + had);

                    // The window: a bucket on the cursor, clicked on the tank.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    LinkPortTests.rightClick(helper, player, tap, false);
                    helper.assertTrue(player.containerMenu instanceof GeothermalTapMenu, "an empty hand opens the window");
                    GeothermalTapMenu menu = (GeothermalTapMenu) player.containerMenu;
                    menu.setCarried(new ItemStack(Items.BUCKET));
                    helper.assertTrue(menu.clickMenuButton(player, MachineMenu.BUTTON_TANK), "the tank takes the click");
                    helper.assertTrue(menu.getCarried().is(ModItems.CORIUM_BUCKET.get()),
                            "and the cursor holds a corium bucket, not " + menu.getCarried());
                    helper.assertValueEqual(entity.corium().getAmount(), had - 1_000, "a bucket's worth left the tank");
                    helper.assertFalse(menu.clickMenuButton(player, MachineMenu.BUTTON_TANK), "a full bucket has nowhere to go: corium never goes in");
                    // The mock player has no connection to hand the cursor back over, so empty it first.
                    menu.setCarried(ItemStack.EMPTY);
                    player.closeContainer();

                    // The block: a bucket on the controller, before any window.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    LinkPortTests.rightClick(helper, player, tap, false);
                    helper.assertTrue(player.getMainHandItem().is(ModItems.CORIUM_BUCKET.get()),
                            "a bucket on the controller comes back full, not " + player.getMainHandItem());
                    helper.assertValueEqual(entity.corium().getAmount(), had - 2_000, "and another bucket's worth is gone");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void aPlateDrinksAnEvenShareFromEveryChunkUnderIt(GameTestHelper helper) {
        helper.assertValueEqual(GeothermalTapBlockEntity.chunksOf(new BlockPos(0, 0, 0), new BlockPos(4, 1, 4)).size(), 1, "inside one chunk");
        helper.assertValueEqual(GeothermalTapBlockEntity.chunksOf(new BlockPos(14, 0, 0), new BlockPos(18, 1, 4)).size(), 2, "across a border");
        helper.assertValueEqual(GeothermalTapBlockEntity.chunksOf(new BlockPos(14, 0, 14), new BlockPos(18, 1, 18)).size(), 4, "over a corner");
        helper.assertValueEqual(GeothermalTapBlockEntity.chunksOf(new BlockPos(-2, 0, -2), new BlockPos(2, 1, 2)).size(), 4, "and over the origin");

        ServerLevel level = helper.getLevel();
        HeatPockets pockets = HeatPockets.get(level);
        long now = level.getGameTime();
        ChunkPos a = new ChunkPos(100_010, 100_000);
        ChunkPos b = new ChunkPos(100_011, 100_000);
        ChunkPos dry = new ChunkPos(100_010, 100_001);
        ChunkPos nearlyDry = new ChunkPos(100_011, 100_001);
        pockets.set(a, 1_000, 1_000, now);
        pockets.set(b, 1_000, 1_000, now);
        pockets.set(dry, 0, 0, now);
        pockets.set(nearlyDry, 10, 1_000, now);
        List<ChunkPos> under = List.of(a, b, dry, nearlyDry);
        helper.assertValueEqual(GeothermalTapBlockEntity.drawShared(pockets, level, under, 400, now), 400L,
                "four hundred wanted, four hundred drawn: the dry chunks cost only their share");
        helper.assertValueEqual(pockets.remaining(level, a, now), 710L, "a hundred as its share and the shortfall of a hundred and ninety");
        helper.assertValueEqual(pockets.remaining(level, b, now), 900L, "a hundred as its share");
        helper.assertValueEqual(pockets.remaining(level, nearlyDry, now), 0L, "the ten it had");
        helper.assertValueEqual(GeothermalTapBlockEntity.drawShared(pockets, level, under, 5_000, now), 1_610L,
                "and when they run dry, what there was");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void thePreviewSizeSticksWithTheController(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tap = MultiblockRigs.tapPos(CORNER, 5, 5);
        ServerPlayer player = LinkPortTests.testPlayer(helper);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(tap, ModBlocks.GEOTHERMAL_TAP.get().defaultBlockState()
                        .setValue(MultiblockControllerBlock.FACING, Direction.NORTH)))
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    int widest = entity.maxSize(Extent.WIDTH);
                    helper.assertValueEqual(entity.previewSize(Extent.WIDTH), 5, "a fresh tap previews the smallest plate");

                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    LinkPortTests.rightClick(helper, player, tap, false);
                    helper.assertTrue(player.containerMenu instanceof GeothermalTapMenu, "an empty hand opens the window");
                    GeothermalTapMenu menu = (GeothermalTapMenu) player.containerMenu;
                    helper.assertTrue(menu.clickMenuButton(player, MultiblockControllerMenu.previewButton(Extent.WIDTH, 2)),
                            "a stepper is a button");
                    helper.assertValueEqual(entity.previewSize(Extent.WIDTH), 7, "two wider");
                    helper.assertValueEqual(menu.previewSize(Extent.WIDTH), 7, "and the window reads it");
                    menu.clickMenuButton(player, MultiblockControllerMenu.previewButton(Extent.WIDTH, 31));
                    helper.assertValueEqual(entity.previewSize(Extent.WIDTH), widest, "never past the widest");
                    menu.clickMenuButton(player, MultiblockControllerMenu.previewButton(Extent.DEPTH, -2));
                    helper.assertValueEqual(entity.previewSize(Extent.DEPTH), 5, "never under the smallest");
                    menu.clickMenuButton(player, MultiblockControllerMenu.previewButton(Extent.HEIGHT, 1));
                    helper.assertValueEqual(entity.previewSize(Extent.HEIGHT), 2, "and the tap's height does not move");
                    player.closeContainer();

                    LinkPortTests.rightClick(helper, player, tap, false);
                    GeothermalTapMenu again = (GeothermalTapMenu) player.containerMenu;
                    helper.assertValueEqual(again.previewSize(Extent.WIDTH), widest, "the window reopens on the size it was left at");
                    player.closeContainer();

                    // Through a save.
                    HolderLookup.Provider registries = level.registryAccess();
                    CompoundTag saved = entity.saveWithoutMetadata(registries);
                    entity.setPreviewSize(Extent.WIDTH, 9);
                    entity.loadWithComponents(saved, registries);
                    helper.assertValueEqual(entity.previewSize(Extent.WIDTH), widest, "the size is saved with the block");

                    // What forms is what the preview shows next; the tap stays, so its block entity does.
                    MultiblockRigs.buildTap(helper, CORNER, 5, 5, pos -> !pos.equals(tap));
                })
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    helper.assertTrue(entity.isFormed(), "plate, cap and tap: formed");
                    helper.assertValueEqual(entity.previewSize(Extent.WIDTH), 5, "what stands is what the preview shows");
                    helper.setBlock(CORNER, Blocks.AIR);
                })
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = tap(helper, tap);
                    helper.assertFalse(entity.isFormed(), "a plate with a corner gone is no plate");
                    helper.assertValueEqual(entity.previewSize(Extent.WIDTH), 5, "and the steppers still read the plate that stood");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void thePlateWearsACoilRoundItsCentre(GameTestHelper helper) {
        BlockPos centre = CORNER.offset(2, 0, 2);
        helper.startSequence()
                .thenExecute(() -> MultiblockRigs.buildTap(helper, CORNER, 5, 5, pos -> true))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(tap(helper, MultiblockRigs.tapPos(CORNER, 5, 5)).isFormed(), "the tap formed");
                    helper.assertValueEqual(helper.getBlockState(centre).getValue(CoilPiece.COIL), CoilPiece.PLAIN, "the middle is plain plate");
                    helper.assertValueEqual(helper.getBlockState(centre.offset(2, 0, 2)).getValue(CoilPiece.COIL), CoilPiece.CORNER_SE,
                            "the outer ring wears fins: south-east of the centre, the south-east corner");
                    helper.assertValueEqual(helper.getBlockState(centre.offset(-1, 0, -1)).getValue(CoilPiece.COIL), CoilPiece.PLAIN,
                            "the ring inside it is plain");
                    helper.assertValueEqual(helper.getBlockState(centre.offset(2, 0, 0)).getValue(CoilPiece.COIL), CoilPiece.RING_Z,
                            "the east edge's fins run north-south");
                    helper.assertValueEqual(helper.getBlockState(centre.offset(1, 0, -2)).getValue(CoilPiece.COIL), CoilPiece.RING_X,
                            "the north edge's run east-west");
                    helper.assertValueEqual(CoilPiece.at(0, -3, 3), CoilPiece.RING_X, "a seven-wide plate: fins on the outside");
                    helper.assertValueEqual(CoilPiece.at(0, -2, 3), CoilPiece.PLAIN, "plain one in");
                    helper.assertValueEqual(CoilPiece.at(-1, -1, 3), CoilPiece.CORNER_NW, "fins again round the centre");
                    helper.assertTrue(helper.getBlockState(centre.offset(2, 0, 2)).getValue(MultiblockCasingBlock.FORMED), "and it is formed");
                    helper.setBlock(centre.offset(2, 0, 2), Blocks.AIR);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(!tap(helper, MultiblockRigs.tapPos(CORNER, 5, 5)).isFormed(), "a corner gone, the tap is not formed");
                    helper.assertValueEqual(helper.getBlockState(centre).getValue(CoilPiece.COIL), CoilPiece.NONE, "and the coil is gone with it");
                })
                .thenSucceed();
    }

    private static GeothermalTapBlockEntity tap(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof GeothermalTapBlockEntity tap) {
            return tap;
        }
        throw new IllegalStateException("no geothermal tap at " + pos);
    }
}
