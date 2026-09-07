package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.generator.AnnihilationFurnaceBlockEntity;
import dev.symo.actualgenerators.generator.GeothermalTapBlockEntity;
import dev.symo.actualgenerators.generator.HeatPockets;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlock;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;
import dev.symo.actualgenerators.menu.AnnihilationFurnaceMenu;
import dev.symo.actualgenerators.menu.MachineMenu;
import dev.symo.actualgenerators.processing.ResonanceCrusherBlockEntity;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.storage.SurgeBankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Things that move on their own: a hatch told to pull or push, and a bank that never has to be told.
 *
 * <p>What matters: a hatch is set up in the controller's window, opened by clicking the hatch,
 * and what is set there survives a save; a hatch nobody set up moves nothing by itself; a
 * pulling item hatch empties the chest beside it into the structure; a pushing energy hatch
 * charges the machine beside it; a held hatch takes a casing's place; a side panel offers only
 * what the machine moves; and a placed bank charges its neighbour with nobody touching it. A
 * pulling fluid hatch has no target of ours to pull from that a test could fill without the
 * tap, so it is the one mover checked in the game only.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class AutoIoTests {
    private static final String ARENA = "arena";
    private static final String PLATFORM = "platform";
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);
    /** In the west wall of a 3x7x3 furnace, half way up. */
    private static final BlockPos ITEM_HATCH = CORNER.offset(0, 3, 1);
    private static final long POCKET = 50_000_000L;

    private AutoIoTests() {
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aHatchIsSetUpFaceByFaceInTheMachineSidePanelAndKeepsIt(GameTestHelper helper) {
        ServerPlayer player = LinkPortTests.testPlayer(helper);
        BlockPos controller = MultiblockRigs.controllerPos(CORNER, 3);
        helper.startSequence()
                .thenExecute(() -> MultiblockRigs.buildBox(helper, CORNER, ModBlocks.ANNIHILATION_FURNACE.get(), 3, 7, 3,
                        pos -> !pos.equals(ITEM_HATCH)))
                .thenExecute(() -> helper.setBlock(ITEM_HATCH, ModBlocks.ITEM_HATCH.get()))
                .thenExecuteAfter(5, () -> {
                    HatchBlockEntity hatch = hatch(helper, ITEM_HATCH);
                    helper.assertTrue(hatch.controller() != null, "the box formed round the hatch");
                    // The hatch stands in the west wall: only its west face looks out of the structure.
                    RelativeSide outward = RelativeSide.fromDirection(hatch.facing(), Direction.WEST);
                    helper.assertValueEqual(Integer.bitCount(hatch.blockedMask()), 5, "five faces look into the structure");
                    helper.assertTrue(!hatch.blocked(outward), "the west face does not");
                    for (RelativeSide side : RelativeSide.all()) {
                        helper.assertValueEqual(hatch.mode(side), IoMode.BOTH, "a fresh hatch is open both ways on " + side);
                    }
                    helper.assertTrue(!hatch.autoPull() && !hatch.autoPush(), "and moves nothing itself");

                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    LinkPortTests.rightClick(helper, player, ITEM_HATCH, false);
                    helper.assertTrue(player.containerMenu instanceof AnnihilationFurnaceMenu,
                            "a click on the hatch opens the controller's window, but " + player.containerMenu.getClass().getSimpleName());
                    AnnihilationFurnaceMenu window = (AnnihilationFurnaceMenu) player.containerMenu;
                    helper.assertTrue(window.hatch() == hatch, "told which hatch it came from");
                    helper.assertTrue(window.hasSideConfig() && window.sidePanelOpenAtStart(), "and shows the side panel at once");
                    helper.assertValueEqual(window.sideKinds(), List.of(TransferKind.ITEM), "with the hatch's kind and no other");
                    helper.assertValueEqual(window.sideMode(TransferKind.ITEM, outward), IoMode.BOTH, "reading the hatch's faces");
                    helper.assertTrue(!window.sideBlocked(outward), "the outward face is free");
                    RelativeSide inward = RelativeSide.fromDirection(hatch.facing(), Direction.EAST);
                    helper.assertTrue(window.sideBlocked(inward), "the face into the box is not");

                    int westButton = MachineMenu.BUTTON_SIDES_START + TransferKind.ITEM.ordinal() * 6 + outward.ordinal();
                    helper.assertTrue(window.clickMenuButton(player, westButton), "the west face is a button");
                    helper.assertValueEqual(hatch.mode(Direction.WEST), IoMode.DISABLED, "both, then nothing: the cycle starts over");
                    helper.assertTrue(helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(ITEM_HATCH), Direction.WEST) == null, "a face set to nothing hands out nothing");
                    helper.assertTrue(window.clickMenuButton(player, westButton), "again");
                    helper.assertValueEqual(hatch.mode(Direction.WEST), IoMode.INPUT, "then input");
                    helper.assertValueEqual(hatch.mode(Direction.UP), IoMode.BOTH, "the other faces are untouched");

                    int inwardButton = MachineMenu.BUTTON_SIDES_START + TransferKind.ITEM.ordinal() * 6 + inward.ordinal();
                    helper.assertTrue(!window.clickMenuButton(player, inwardButton), "a face into the box is refused");
                    helper.assertValueEqual(hatch.mode(Direction.EAST), IoMode.BOTH, "and left alone");
                    helper.assertTrue(!window.clickMenuButton(player, MachineMenu.BUTTON_SIDES_START + TransferKind.FLUID.ordinal() * 6),
                            "an item hatch has no fluid faces");

                    helper.assertTrue(window.clickMenuButton(player, MachineMenu.BUTTON_AUTO_START + TransferKind.ITEM.ordinal() * 2), "IN is a button");
                    helper.assertTrue(hatch.autoPull() && !hatch.autoPush(), "IN: the hatch pulls");
                    helper.assertTrue(window.autoEnabled(TransferKind.ITEM, false) && !window.autoEnabled(TransferKind.ITEM, true),
                            "and the window says so");

                    HolderLookup.Provider registries = helper.getLevel().registryAccess();
                    CompoundTag saved = hatch.saveWithoutMetadata(registries);
                    HatchBlockEntity copy = new HatchBlockEntity(hatch.getBlockPos(), hatch.getBlockState());
                    copy.loadWithComponents(saved, registries);
                    helper.assertValueEqual(copy.mode(Direction.WEST), IoMode.INPUT, "a save keeps the faces");
                    helper.assertTrue(copy.autoPull() && !copy.autoPush(), "and the switches");
                    player.closeContainer();

                    LinkPortTests.rightClick(helper, player, controller, false);
                    helper.assertTrue(player.containerMenu instanceof AnnihilationFurnaceMenu controllerWindow
                            && controllerWindow.hatch() == null && !controllerWindow.hasSideConfig(),
                            "the controller's own click carries no hatch and no panel");
                    player.closeContainer();
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aPullingItemHatchEmptiesTheChestBesideIt(GameTestHelper helper) {
        BlockPos barrel = ITEM_HATCH.west();
        helper.startSequence()
                .thenExecute(() -> {
                    MultiblockRigs.buildBox(helper, CORNER, ModBlocks.ANNIHILATION_FURNACE.get(), 3, 7, 3,
                            pos -> !pos.equals(ITEM_HATCH));
                    helper.setBlock(ITEM_HATCH, ModBlocks.ITEM_HATCH.get());
                    helper.setBlock(barrel, Blocks.BARREL);
                    BarrelBlockEntity chest = helper.getBlockEntity(barrel);
                    chest.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(hatch(helper, ITEM_HATCH).controller() != null, "the box formed");
                    helper.assertValueEqual(count(helper, barrel), 8, "a hatch nobody set up takes nothing on its own");
                    hatch(helper, ITEM_HATCH).sides().setAuto(TransferKind.ITEM, false, true);
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertValueEqual(count(helper, barrel), 0, "a pulling hatch emptied the barrel");
                    AnnihilationFurnaceBlockEntity furnace = helper.getBlockEntity(MultiblockRigs.controllerPos(CORNER, 3));
                    IItemHandler inside = furnace.hatchItems();
                    int held = 0;
                    for (int slot = 0; slot < inside.getSlots(); slot++) {
                        held += inside.getStackInSlot(slot).getCount();
                    }
                    helper.assertValueEqual(held, 8, "into the furnace");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aPushingEnergyHatchChargesTheMachineBesideIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tap = MultiblockRigs.tapPos(CORNER, 5, 5);
        BlockPos hatch = MultiblockRigs.capCentre(CORNER, 5, 5).east();
        BlockPos crusher = hatch.east();
        helper.startSequence()
                .thenExecute(() -> {
                    MultiblockRigs.buildTap(helper, CORNER, 5, 5, pos -> !pos.equals(hatch));
                    helper.setBlock(hatch, ModBlocks.ENERGY_HATCH.get());
                    helper.setBlock(crusher, ModBlocks.RESONANCE_CRUSHER.get());
                })
                .thenExecuteAfter(5, () -> {
                    GeothermalTapBlockEntity entity = helper.getBlockEntity(tap);
                    helper.assertTrue(entity.isFormed(), "the tap formed with the crusher standing past the cap");
                    for (ChunkPos under : entity.tappedChunks()) {
                        HeatPockets.get(level).set(under, POCKET, POCKET, level.getGameTime());
                    }
                })
                .thenExecuteAfter(60, () -> {
                    GeothermalTapBlockEntity entity = helper.getBlockEntity(tap);
                    helper.assertTrue(entity.energyStorage().stored() > 0, "the tap made something");
                    ResonanceCrusherBlockEntity target = helper.getBlockEntity(crusher);
                    helper.assertValueEqual(target.energyStorage().stored(), 0L, "a hatch nobody set up gave none of it away");
                    hatch(helper, hatch).sides().setAuto(TransferKind.ENERGY, true, true);
                })
                .thenExecuteAfter(30, () -> {
                    ResonanceCrusherBlockEntity target = helper.getBlockEntity(crusher);
                    helper.assertTrue(target.energyStorage().stored() > 0, "a pushing hatch charged the crusher");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aHeldHatchTakesACasingsPlaceAndHandsItBack(GameTestHelper helper) {
        ServerPlayer player = LinkPortTests.testPlayer(helper);
        BlockPos casing = CORNER.offset(2, 3, 1);
        helper.startSequence()
                .thenExecute(() -> MultiblockRigs.buildBox(helper, CORNER, ModBlocks.ANNIHILATION_FURNACE.get(), 3, 7, 3, pos -> true))
                .thenExecuteAfter(5, () -> {
                    AnnihilationFurnaceBlockEntity furnace = helper.getBlockEntity(MultiblockRigs.controllerPos(CORNER, 3));
                    helper.assertTrue(furnace.isFormed(), "the plain box formed");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ENERGY_HATCH.get()));
                    LinkPortTests.rightClick(helper, player, casing, false);
                    helper.assertBlockPresent(ModBlocks.ENERGY_HATCH.get(), casing);
                    // The casing comes back into the first free slot, which is the emptied hand: a bucket's way.
                    helper.assertTrue(!player.getMainHandItem().is(ModItems.ENERGY_HATCH.get()), "the hatch left the hand");
                    helper.assertValueEqual(player.getInventory().countItem(ModItems.MACHINE_CASING.get()), 1, "and the casing came back");
                })
                .thenExecuteAfter(5, () -> {
                    AnnihilationFurnaceBlockEntity furnace = helper.getBlockEntity(MultiblockRigs.controllerPos(CORNER, 3));
                    helper.assertTrue(furnace.isFormed(), "the box stands with its new door");
                    helper.assertTrue(hatch(helper, casing).controller() == furnace, "which is on it");
                    // A hatch of the same kind is not swapped for itself: the click reaches the block and opens the window.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ENERGY_HATCH.get()));
                    LinkPortTests.rightClick(helper, player, casing, false);
                    helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "the same hatch stays in the hand");
                    helper.assertTrue(player.containerMenu instanceof AnnihilationFurnaceMenu, "and the window opened");
                    player.closeContainer();
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void aBankChargesItsNeighbourWithNobodyTouchingItAndOffersOnlyEnergyFaces(GameTestHelper helper) {
        BlockPos bankPos = new BlockPos(2, 1, 2);
        BlockPos crusherPos = bankPos.north();
        ServerPlayer player = LinkPortTests.testPlayer(helper);
        helper.setBlock(bankPos, ModBlocks.SURGE_BANK.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        helper.setBlock(crusherPos, ModBlocks.RESONANCE_CRUSHER.get());
        SurgeBankBlockEntity bank = helper.getBlockEntity(bankPos);
        bank.energyStorage().generate(100_000);
        helper.assertTrue(bank.sideConfig().autoPush(TransferKind.ENERGY) && bank.sideConfig().autoPull(TransferKind.ENERGY),
                "a placed bank pulls and pushes on its own");

        helper.runAfterDelay(30, () -> {
            ResonanceCrusherBlockEntity crusher = helper.getBlockEntity(crusherPos);
            helper.assertTrue(crusher.energyStorage().stored() > 0, "the crusher at the bank's front got charged");

            LinkPortTests.rightClick(helper, player, bankPos, false);
            helper.assertTrue(player.containerMenu instanceof MachineMenu, "the bank's window opened");
            MachineMenu window = (MachineMenu) player.containerMenu;
            helper.assertTrue(!window.supportsKind(TransferKind.ITEM) && !window.supportsKind(TransferKind.FLUID),
                    "a battery has no item or fluid faces to offer");
            helper.assertTrue(window.supportsKind(TransferKind.ENERGY), "only energy");
            helper.assertValueEqual(window.sideKinds(), List.of(TransferKind.ENERGY), "one tab");
            helper.assertTrue(!window.clickMenuButton(player, MachineMenu.BUTTON_SIDES_START), "and an item face button is refused");

            LinkPortTests.rightClick(helper, player, crusherPos, false);
            MachineMenu crusherWindow = (MachineMenu) player.containerMenu;
            helper.assertTrue(crusherWindow.supportsKind(TransferKind.ITEM), "a crusher offers item faces");
            helper.assertTrue(!crusherWindow.supportsKind(TransferKind.FLUID), "but has no tank");
            player.closeContainer();
            helper.succeed();
        });
    }

    private static HatchBlockEntity hatch(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    private static int count(GameTestHelper helper, BlockPos barrel) {
        BarrelBlockEntity chest = helper.getBlockEntity(barrel);
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            count += chest.getItem(slot).getCount();
        }
        return count;
    }
}
