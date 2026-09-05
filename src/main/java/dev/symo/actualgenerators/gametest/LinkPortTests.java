package dev.symo.actualgenerators.gametest;

import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.entity.ai.attributes.Attributes;
import dev.symo.actualgenerators.registry.ModDataComponents;
import dev.symo.actualgenerators.item.ConfigCardItem;
import dev.symo.actualgenerators.logistics.PadSnapshot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.ButtonBlock;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.logistics.Distribution;
import dev.symo.actualgenerators.logistics.EnergyInjectorBlockEntity;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.logistics.LinkPortBlock;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.NetworkOverview;
import dev.symo.actualgenerators.logistics.PortChannel;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.logistics.SignalSource;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.MachineEnergyStorage;
import dev.symo.actualgenerators.machine.MachineTier;
import dev.symo.actualgenerators.machine.RedstoneMode;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.logistics.AmountExpression;
import dev.symo.actualgenerators.logistics.ChannelSettings;
import dev.symo.actualgenerators.processing.ResonanceCrusherBlockEntity;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.EnergyInjectorMenu;
import dev.symo.actualgenerators.menu.LinkPortMenu;
import dev.symo.actualgenerators.menu.NetworkOverviewMenu;
import dev.symo.actualgenerators.menu.NetworkPickerMenu;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What a Logic Port network promises: that a channel told what to carry moves it between pads, and
 * that every switch on the pad, and every click of the tool, does exactly what it says.
 *
 * <p>Most of these run the real path — the network's own tick, its capability lookups and its
 * transfer — rather than calling the mover directly, because the interesting failures live in the
 * wiring between those and not in the arithmetic. The tool and the windows are driven through
 * {@code ServerPlayerGameMode} and the menus' own button handlers, which is what a mouse reaches.
 */
@GameTestHolder(ActualGenerators.MODID)
@PrefixGameTestTemplate(false)
public final class LinkPortTests {
    private static final String EMPTY = "empty";
    private static final String PLATFORM = "platform";
    private static final String ARENA = "arena";

    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos SOURCE_PORT = new BlockPos(1, 1, 2);
    private static final BlockPos NEAR = new BlockPos(2, 1, 1);
    private static final BlockPos NEAR_PORT = new BlockPos(2, 1, 2);
    private static final BlockPos FAR = new BlockPos(4, 1, 1);
    private static final BlockPos FAR_PORT = new BlockPos(4, 1, 2);
    private static final BlockPos INJECTOR = new BlockPos(2, 2, 4);

    // The platform template's floor lands on helper-relative y=1, so a rig that is placed rather
    // than set has to stand a layer above the one the set-block tests write straight into.
    private static final BlockPos FROM = new BlockPos(1, 2, 1);
    private static final BlockPos FROM_PAD = new BlockPos(1, 2, 2);
    private static final BlockPos TO = new BlockPos(2, 2, 1);
    private static final BlockPos TO_PAD = new BlockPos(2, 2, 2);

    // Any colour carries anything once told to; these are the ones the rigs here use.
    private static final int ITEMS = 0;
    private static final int ENERGY = 4;

    private LinkPortTests() {
    }

    // ------------------------------------------------------------------ the whole path

    /** Two pads, one channel told what it carries, one end told to send, and the stuff moves. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void twoPadsMoveItemsOnceAChannelCarriesThem(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    items(helper, SOURCE_PORT).setRole(false, true);
                    items(helper, NEAR_PORT).setRole(true, false);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 64,
                            "the receiving pad takes everything the sending pad has");
                    helper.assertValueEqual(count(helper, SOURCE, Items.COBBLESTONE), 0,
                            "and the source is emptied rather than copied");
                })
                .thenSucceed();
    }

    /** A network carries nothing until a channel is told what to carry. No colour has a job by default. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aFreshNetworkCarriesNothingUntilTold(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    items(helper, SOURCE_PORT).setRole(false, true);
                    items(helper, NEAR_PORT).setRole(true, false);

                    LinkNetworkManager manager = manager(helper);
                    UUID network = face(helper, SOURCE_PORT).networkId();
                    for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
                        helper.assertTrue(manager.channelKinds(network, channel) == 0,
                                "a fresh network's channel " + channel + " carries nothing");
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 0,
                            "roles on a channel with no kind move nothing");
                    carry(helper, ITEMS, TransferKind.ITEM);
                    helper.assertTrue(manager(helper).channelCarries(face(helper, NEAR_PORT).networkId(), ITEMS, TransferKind.ITEM),
                            "a kind set through one pad is the kind every pad on the network sees");
                })
                .thenIdle(60)
                .thenExecute(() -> helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 64,
                        "and telling the channel what it carries is all that was missing"))
                .thenSucceed();
    }

    /**
     * Energy goes the same way, through the machine's own sided capability — so a face the machine
     * keeps shut stays shut, and the pad never gets to go round it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void energyCrossesANetworkAtItsRatedSpeed(GameTestHelper helper) {
        int ticks = 120;

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SOURCE, ModBlocks.SURGE_BANK.get());
                    helper.setBlock(NEAR, ModBlocks.SURGE_BANK.get());
                    openEnergy(machine(helper, SOURCE));
                    openEnergy(machine(helper, NEAR));
                    machine(helper, SOURCE).energyStorage()
                            .setEnergy(machine(helper, SOURCE).energyStorage().capacity());

                    pad(helper, SOURCE_PORT, Direction.NORTH);
                    pad(helper, NEAR_PORT, Direction.NORTH);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ENERGY, TransferKind.ENERGY);
                    face(helper, SOURCE_PORT).link(ENERGY, TransferKind.ENERGY).setRole(false, true);
                    face(helper, NEAR_PORT).link(ENERGY, TransferKind.ENERGY).setRole(true, false);
                })
                .thenIdle(ticks)
                .thenExecute(() -> {
                    long moved = machine(helper, NEAR).energyStorage().stored();
                    PortChannel link = face(helper, SOURCE_PORT).link(ENERGY, TransferKind.ENERGY);
                    long perSend = link.amount();
                    int delay = link.delayTicks();
                    long sends = ticks / delay;

                    helper.assertTrue(moved > 0, "a linked pair moves FE at all");
                    // Amount every Delay ticks is the promise, so the window holds about that many
                    // sends' worth; slack only for the clock edges at either end.
                    helper.assertTrue(moved >= perSend * (sends - 2),
                            "an untiered pad sends " + perSend + " FE every " + delay + " ticks, so about "
                                    + perSend * sends + " FE in " + ticks + " ticks, but moved " + moved);
                })
                .thenSucceed();
    }

    /**
     * A send is Amount FE, but a receiver rated below that takes it a tick's worth per capability
     * call. One call per send moved a crusher's 1,000 FE/t into it once per Delay, a tenth of its
     * rate; the two banks above never showed it, their faces taking 20,000 at a time.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void aReceiverRatedBelowTheSendStillFillsAtItsOwnRate(GameTestHelper helper) {
        int ticks = 80;

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SOURCE, ModBlocks.SURGE_BANK.get());
                    helper.setBlock(NEAR, ModBlocks.RESONANCE_CRUSHER.get());
                    openEnergy(machine(helper, SOURCE));
                    openEnergy(machine(helper, NEAR));
                    machine(helper, SOURCE).energyStorage()
                            .setEnergy(machine(helper, SOURCE).energyStorage().capacity());

                    pad(helper, SOURCE_PORT, Direction.NORTH);
                    pad(helper, NEAR_PORT, Direction.NORTH);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ENERGY, TransferKind.ENERGY);
                    face(helper, SOURCE_PORT).link(ENERGY, TransferKind.ENERGY).setRole(false, true);
                    face(helper, NEAR_PORT).link(ENERGY, TransferKind.ENERGY).setRole(true, false);
                })
                .thenIdle(ticks)
                .thenExecute(() -> {
                    MachineEnergyStorage sink = machine(helper, NEAR).energyStorage();
                    PortChannel link = face(helper, SOURCE_PORT).link(ENERGY, TransferKind.ENERGY);
                    int rate = sink.getMaxReceive();
                    int delay = link.delayTicks();

                    helper.assertTrue(link.amount() > (long) rate * delay,
                            "the send has to be bigger than the receiver takes in a Delay, or the test proves nothing");
                    helper.assertTrue(sink.capacity() > (long) rate * ticks, "and the receiver has to outlast the test");
                    long expected = (long) rate * (ticks - 2 * delay);
                    helper.assertTrue(sink.stored() >= expected,
                            "a " + rate + " FE/t receiver should have taken about " + (long) rate * ticks
                                    + " FE in " + ticks + " ticks, but took " + sink.stored());
                })
                .thenSucceed();
    }

    /**
     * The tool through the game's own interaction order: a click on a pad opens the picker, the
     * picker makes a network or picks one, and both pads end up on the same one.
     */
    @GameTest(template = PLATFORM)
    public static void theToolPutsTwoPadsOnOneNetworkThroughThePicker(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        ServerPlayer player = testPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LINKING_TOOL.toStack());

        // Other tests share this server, so the list is never empty and the name has to be ours.
        String name = "Picker test";
        rightClick(helper, player, SOURCE_PORT, false);
        NetworkPickerMenu picker = picker(helper, player);
        helper.assertTrue(picker.current() == null, "a fresh pad is on no network");
        helper.assertTrue(picker.choices().stream().noneMatch(choice -> choice.name().equals(name)),
                "and the network is not there before it is made");
        picker.create(player, name);
        helper.assertTrue(face(helper, SOURCE_PORT).isLinked(), "creating one puts the pad on it");
        helper.assertValueEqual(face(helper, SOURCE_PORT).networkName(), name, "under the name it was given");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "and the picker closes");

        rightClick(helper, player, NEAR_PORT, false);
        helper.assertTrue(picker(helper, player).choices().stream().anyMatch(choice -> choice.name().equals(name)),
                "the second pad is offered the network the first made");
        pick(helper, player, name);
        helper.assertValueEqual(face(helper, NEAR_PORT).networkId(), face(helper, SOURCE_PORT).networkId(),
                "and picking it puts both pads on the same network, which is the entire point of the tool");

        // A linked pad hands the tool to its own window; the picker is a click further, on the network line.
        rightClick(helper, player, NEAR_PORT, false);
        padWindow(helper, player).clickMenuButton(player, LinkPortMenu.BUTTON_NETWORK_PICK);
        picker = picker(helper, player);
        helper.assertValueEqual(picker.current(), face(helper, SOURCE_PORT).networkId(),
                "the picker knows which network the pad is on");
        picker.clickMenuButton(player, NetworkPickerMenu.BUTTON_LEAVE);
        helper.assertTrue(!face(helper, NEAR_PORT).isLinked(), "and Leave takes it off");
        helper.assertTrue(player.containerMenu instanceof LinkPortMenu,
                "and goes back to the pad window it came from, but " + player.containerMenu.getClass().getSimpleName());

        helper.succeed();
    }

    /**
     * Wireless is not free, and a network nothing is paying for is not a network.
     *
     * <p>This is the whole reason the Energy Injector exists, so it is checked from the empty side:
     * the same rig that works above moves nothing at all once the injector is drained.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aNetworkWithNothingPayingForItMovesNothing(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    items(helper, SOURCE_PORT).setRole(false, true);
                    items(helper, NEAR_PORT).setRole(true, false);
                    injector(helper).energyStorage().setEnergy(0);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 0,
                            "a flat injector means the pads stop, which is what paying for it means");
                    helper.assertValueEqual(count(helper, SOURCE, Items.COBBLESTONE), 64,
                            "and nothing is lost on the way to not moving");

                    injector(helper).energyStorage()
                            .setEnergy(injector(helper).energyStorage().capacity());
                    face(helper, SOURCE_PORT).wakeNetwork();
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 64,
                        "and filling it up again starts everything moving"))
                .thenSucceed();
    }

    /**
     * Six faces in one block space, which is the unit a base actually needs: one gap between two
     * machines, one port block, both machines served.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void oneBlockCarriesAPadOnEachFace(GameTestHelper helper) {
        BlockPos gap = new BlockPos(2, 1, 2);
        BlockPos west = gap.west();
        BlockPos east = gap.east();

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(west, Blocks.BARREL);
                    helper.setBlock(east, Blocks.BARREL);
                    pad(helper, gap, Direction.WEST);
                    pad(helper, gap, Direction.EAST);

                    LinkPortBlockEntity port = port(helper, gap);
                    helper.assertValueEqual(port.faceCount(), 2, "one block, two pads");

                    PortFace fromWest = port.face(Direction.WEST);
                    PortFace toEast = port.face(Direction.EAST);
                    helper.assertTrue(fromWest != null && toEast != null, "both pads are there to configure");

                    LinkNetworkManager manager = manager(helper);
                    UUID network = manager.join(fromWest, null);
                    manager.join(toEast, network);
                    manager.setChannelKind(network, ITEMS, TransferKind.ITEM, true);
                    fromWest.link(ITEMS, TransferKind.ITEM).setRole(false, true);
                    toEast.link(ITEMS, TransferKind.ITEM).setRole(true, false);
                    helper.setBlock(INJECTOR, ModBlocks.ENERGY_INJECTOR.get());
                    injector(helper).energyStorage().setEnergy(injector(helper).energyStorage().capacity());
                    manager.joinInjector(injector(helper), network);

                    if (helper.getBlockEntity(west) instanceof Container barrel) {
                        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    }
                })
                .thenIdle(60)
                .thenExecute(() -> helper.assertValueEqual(count(helper, east, Items.COBBLESTONE), 64,
                        "two pads on one block move things between the blocks either side of it"))
                .thenSucceed();
    }

    /**
     * The whole thing the way a player does it, with nothing helped along.
     *
     * <p>Every other test here builds the rig by calling the manager and setting fields. This one
     * places the pads by right-clicking a barrel with the port item, puts them on a network with
     * the tool and its picker, powers it by clicking the injector, and sets the channel up through
     * the pad's own window buttons — through {@code ServerPlayerGameMode}, the same path a mouse
     * takes. If a player says it does not move anything, this is the test that should have caught it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void aPlayerCanBuildAWorkingLinkWithNothingButTheItems(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(FROM, Blocks.BARREL);
                    helper.setBlock(TO, Blocks.BARREL);
                    helper.setBlock(INJECTOR, ModBlocks.ENERGY_INJECTOR.get());

                    // Pads go on by clicking the south face of each barrel with the port item.
                    placePad(helper, player, FROM, Direction.SOUTH);
                    placePad(helper, player, TO, Direction.SOUTH);
                    helper.assertBlockPresent(ModBlocks.LOGIC_PORT.get(), FROM_PAD);
                    helper.assertBlockPresent(ModBlocks.LOGIC_PORT.get(), TO_PAD);

                    // The tool: name a network on the first pad, pick it on the second and the injector.
                    player.setShiftKeyDown(false);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LINKING_TOOL.toStack());
                    rightClick(helper, player, FROM_PAD, false);
                    picker(helper, player).create(player, "Base");
                    rightClick(helper, player, TO_PAD, false);
                    pick(helper, player, "Base");
                    rightClick(helper, player, INJECTOR, false);
                    pick(helper, player, "Base");

                    helper.assertTrue(face(helper, FROM_PAD).isLinked(), "the first pad is on a network");
                    helper.assertTrue(face(helper, TO_PAD).isLinked(), "so is the second");
                    helper.assertTrue(injector(helper).isLinked(), "and so is the injector paying for them");
                    helper.assertValueEqual(injector(helper).networkId(), face(helper, FROM_PAD).networkId(),
                            "all three on the same network, from three clicks of one tool");

                    // The pad windows: white carries items, one end sends, the other receives.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    rightClick(helper, player, FROM_PAD, false);
                    LinkPortMenu window = padWindow(helper, player);
                    window.clickMenuButton(player, LinkPortMenu.BUTTON_CARRY);
                    window.clickMenuButton(player, LinkPortMenu.BUTTON_EXTRACT);
                    player.closeContainer();
                    rightClick(helper, player, TO_PAD, false);
                    padWindow(helper, player).clickMenuButton(player, LinkPortMenu.BUTTON_INSERT);
                    player.closeContainer();

                    injector(helper).energyStorage().setEnergy(injector(helper).energyStorage().capacity());
                    if (helper.getBlockEntity(FROM) instanceof Container barrel) {
                        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    }
                })
                .thenIdle(80)
                .thenExecute(() -> helper.assertValueEqual(count(helper, TO, Items.COBBLESTONE), 64,
                        "and then it moves things, with nothing set up behind the scenes"))
                .thenSucceed();
    }

    /**
     * The injector is the one block that pulls energy in on its own by default, because the
     * network that would carry FE to it is the network it has to pay for first, so an injector
     * that waited to be pushed into would be a buffer nobody could fill.
     */
    @GameTest(template = PLATFORM)
    public static void anInjectorFillsItself(GameTestHelper helper) {
        helper.setBlock(INJECTOR, ModBlocks.ENERGY_INJECTOR.get());
        helper.assertTrue(injector(helper).sideConfig().autoPull(TransferKind.ENERGY),
                "an injector reaches out for power, because nothing else is going to hand it any");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the switches

    /** Every button on the pad's window, pressed the way the screen presses them. */
    @GameTest(template = PLATFORM)
    public static void thePadWindowButtonsDoWhatTheySay(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        linkAll(helper, SOURCE_PORT, NEAR_PORT);
        ServerPlayer player = testPlayer(helper);
        LinkPortMenu menu = new LinkPortMenu(1, player.getInventory(), port(helper, SOURCE_PORT), Direction.NORTH);
        PortFace pad = face(helper, SOURCE_PORT);
        PortChannel whiteItems = pad.link(0, TransferKind.ITEM);
        PortChannel whiteEnergy = pad.link(0, TransferKind.ENERGY);
        LinkNetworkManager manager = manager(helper);
        UUID network = pad.networkId();

        // The carry button: the open tab's kind on the chosen channel, for the whole network; again off.
        helper.assertValueEqual(menu.activeKind(), TransferKind.ITEM, "a fresh window opens on the item tab");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_CARRY);
        helper.assertTrue(manager.channelCarries(network, 0, TransferKind.ITEM), "ON: white carries items");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_TAB_BASE + TransferKind.ENERGY.ordinal());
        helper.assertValueEqual(menu.activeKind(), TransferKind.ENERGY, "a tab click opens that kind");
        helper.assertTrue(!manager.channelCarries(network, 0, TransferKind.ENERGY), "and changes nothing by itself");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_CARRY);
        helper.assertTrue(manager.channelCarries(network, 0, TransferKind.ITEM) && manager.channelCarries(network, 0, TransferKind.ENERGY),
                "ON on the energy tab: energy as well, not instead");
        helper.assertTrue(manager.channelCarries(face(helper, NEAR_PORT).networkId(), 0, TransferKind.ENERGY),
                "and the other pad sees the same kinds");

        // IN, EX and PU toggle the open tab's kind and no other.
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_INSERT);
        helper.assertTrue(whiteEnergy.insertEnabled() && !whiteEnergy.extractEnabled(), "IN turns receiving on");
        helper.assertTrue(!whiteItems.participates(), "for energy, not for items");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_EXTRACT);
        helper.assertTrue(whiteEnergy.insertEnabled() && whiteEnergy.extractEnabled(), "EX turns sending on beside it");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_INSERT);
        helper.assertTrue(!whiteEnergy.insertEnabled() && whiteEnergy.extractEnabled(), "IN again: receiving off");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_PUSH);
        helper.assertTrue(whiteEnergy.pushEnabled() && whiteEnergy.extractEnabled(), "PU opens the door beside EX");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_PUSH);
        helper.assertTrue(!whiteEnergy.pushEnabled() && whiteEnergy.extractEnabled(), "PU again: door shut, EX untouched");

        // Spread and redstone are the open tab's too.
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_SPREAD);
        helper.assertValueEqual(manager.channelDistribution(network, 0, TransferKind.ENERGY), Distribution.ROUND_ROBIN,
                "the spread button flips energy to round robin");
        helper.assertValueEqual(manager.channelDistribution(network, 0, TransferKind.ITEM), Distribution.NEAREST_FIRST,
                "and leaves items nearest-first");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_REDSTONE_NEXT);
        helper.assertValueEqual(whiteEnergy.redstoneMode(), RedstoneMode.WITH_SIGNAL, "left steps the redstone mode on");
        helper.assertValueEqual(whiteItems.redstoneMode(), RedstoneMode.ALWAYS, "for this kind alone");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_REDSTONE_PREV);
        helper.assertValueEqual(whiteEnergy.redstoneMode(), RedstoneMode.ALWAYS, "right steps it back");

        // The palette picks a channel and nothing else.
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_CHANNEL_BASE + 5);
        helper.assertValueEqual(menu.activeChannel(), 5, "a palette click selects the channel");
        helper.assertValueEqual(menu.activeKind(), TransferKind.ENERGY, "and keeps the tab");
        helper.assertTrue(!pad.link(5, TransferKind.ENERGY).participates(), "without touching the pad's part in it");

        // The steppers: three step sizes, up and down, and priority goes negative.
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_CHANNEL_BASE);
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_TAB_BASE + TransferKind.ITEM.ordinal());
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_PRIORITY, 1, true));
        helper.assertValueEqual(whiteItems.priority(), 8, "the middle step is eight");
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_PRIORITY, 2, false));
        helper.assertValueEqual(whiteItems.priority(), -56, "the big one is sixty-four, and it goes negative");
        helper.assertValueEqual(whiteEnergy.priority(), 0, "and energy on the same channel keeps its own");
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_KEEP, 0, true));
        helper.assertValueEqual(whiteItems.keepAmount(), 1, "keep steps by one on the item tab");
        int ceiling = pad.amountCeiling(TransferKind.ITEM);
        helper.assertTrue(whiteItems.amountIsAuto() && whiteItems.amount() == ceiling,
                "amount starts at the tier's limit, which is " + ceiling);
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_AMOUNT, 1, false));
        helper.assertValueEqual(whiteItems.amount(), ceiling - 8, "down eight from the limit");
        helper.assertTrue(!whiteItems.amountIsAuto(), "and that is a number the player set");
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_AMOUNT, 2, true));
        helper.assertTrue(whiteItems.amountIsAuto(), "up past the limit is the limit again, the tier's to raise");
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_AMOUNT, 2, false));
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_AMOUNT, 2, false));
        helper.assertValueEqual(whiteItems.amount(), 1, "and down past one is one: zero would be nothing");
        int floor = pad.delayFloor();
        helper.assertTrue(whiteItems.delayIsAuto() && whiteItems.delayTicks() == floor, "delay starts at the tier's floor");
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_DELAY, 1, true));
        helper.assertValueEqual(whiteItems.delayTicks(), floor + 8, "up eight ticks");
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_DELAY, 2, false));
        helper.assertTrue(whiteItems.delayIsAuto() && whiteItems.delayTicks() == floor, "and down past the floor is the floor");

        // A typed number lands where the arrows would have taken it, clamped the same way.
        menu.setNumber(LinkPortMenu.FIELD_KEEP, 640);
        helper.assertValueEqual(whiteItems.keepAmount(), 640, "keep takes a typed number");
        menu.setNumber(LinkPortMenu.FIELD_PRIORITY, 1_000_000);
        helper.assertValueEqual(whiteItems.priority(), PortChannel.MAX_PRIORITY, "priority stops at its end");
        menu.setNumber(LinkPortMenu.FIELD_AMOUNT, 12);
        helper.assertValueEqual(whiteItems.amount(), 12, "amount takes one");
        menu.setNumber(LinkPortMenu.FIELD_AMOUNT, Long.MAX_VALUE);
        helper.assertTrue(whiteItems.amountIsAuto(), "and past the limit follows the tier again");
        menu.setNumber(LinkPortMenu.FIELD_DELAY, 0);
        helper.assertTrue(whiteItems.delayIsAuto(), "a delay under the floor is the floor");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_TAB_BASE + TransferKind.ENERGY.ordinal());
        menu.setNumber(LinkPortMenu.FIELD_KEEP, 5);
        helper.assertValueEqual(whiteEnergy.keepAmount(), 0, "energy has no keep, typed or not");

        menu.clickMenuButton(player, LinkPortMenu.BUTTON_NETWORK_LEAVE);
        helper.assertTrue(!pad.isLinked(), "a right click on the network line takes the pad off its network");
        helper.assertValueEqual(manager.portCount(network), 1, "and the network is one pad smaller");

        helper.succeed();
    }

    /** Channels split one network into several without needing several networks. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aChannelOfItsOwnCutsAPadOutOfTheTraffic(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    carry(helper, 5, TransferKind.ITEM);
                    items(helper, SOURCE_PORT).setRole(false, true);
                    // The receiver listens on lime instead, which carries items too.
                    face(helper, NEAR_PORT).link(5, TransferKind.ITEM).setRole(true, false);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 0,
                            "a pad on another channel is not a receiver, network or no network");
                    helper.assertValueEqual(count(helper, SOURCE, Items.COBBLESTONE), 64,
                            "and nothing leaves the source when there is nowhere for it to go");
                })
                .thenSucceed();
    }

    /**
     * Priority beats distance, which is the only way to say "fill the furnaces first, the overflow
     * chest second" — and the overflow chest is usually the nearer one.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void priorityOutranksDistance(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    barrel(helper, FAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT, FAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);

                    items(helper, SOURCE_PORT).setRole(false, true);
                    items(helper, NEAR_PORT).setRole(true, false);
                    items(helper, FAR_PORT).setRole(true, false);
                    // Nearest-first is the default spread, so whoever sorts first is handed the lot.
                    items(helper, FAR_PORT).nudgePriority(1);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, FAR, Items.COBBLESTONE), 64,
                            "the higher-priority pad gets it all even though it is three times further");
                    helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 0,
                            "and the nearer one gets nothing while the other still has room");
                })
                .thenSucceed();
    }

    /** Round-robin exists so a sender with three chests fills three chests, not the first one. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void roundRobinSpreadsALoadAcrossReceivers(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    barrel(helper, FAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT, FAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);

                    items(helper, SOURCE_PORT).setRole(false, true);
                    items(helper, NEAR_PORT).setRole(true, false);
                    items(helper, FAR_PORT).setRole(true, false);
                    // Spread is the channel's setting, for everyone on it, XNet's way.
                    manager(helper).setChannelDistribution(face(helper, SOURCE_PORT).networkId(), ITEMS,
                            TransferKind.ITEM, Distribution.ROUND_ROBIN);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    int near = count(helper, NEAR, Items.COBBLESTONE);
                    int far = count(helper, FAR, Items.COBBLESTONE);
                    helper.assertValueEqual(near + far, 64, "everything still arrives somewhere");
                    helper.assertTrue(near > 0 && far > 0,
                            "and round-robin puts some in each, not all in the nearest: "
                                    + near + " near, " + far + " far");
                })
                .thenSucceed();
    }

    /** Redstone mode belongs to the link, not to the block the pad is stuck to. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aPadHeldOffByItsRedstoneModeMovesNothing(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    items(helper, SOURCE_PORT).setRole(false, true);
                    items(helper, NEAR_PORT).setRole(true, false);
                    // ALWAYS -> WITH_SIGNAL, and there is no signal.
                    items(helper, SOURCE_PORT).cycleRedstoneMode();
                })
                .thenIdle(60)
                .thenExecute(() -> helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 0,
                        "a pad waiting for a signal it never got must not send"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ the network

    /** Reach is measured from the centre of the network's pads, and the centre moves with them. */
    @GameTest(template = PLATFORM)
    public static void reachIsMeasuredFromTheNetworksCentre(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, FAR);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, FAR_PORT), network);

        Vec3 source = helper.absolutePos(SOURCE_PORT).getCenter();
        Vec3 far = helper.absolutePos(FAR_PORT).getCenter();
        helper.assertValueEqual(manager.center(network), source.add(far).scale(0.5),
                "two pads: the centre sits halfway between them");
        helper.assertValueEqual(face(helper, SOURCE_PORT).networkCenter(), manager.center(network),
                "and every pad carries a copy of it");
        helper.assertTrue(face(helper, SOURCE_PORT).inReach() && face(helper, FAR_PORT).inReach(),
                "both are within base range of it");

        manager.leave(face(helper, FAR_PORT));
        helper.assertValueEqual(manager.center(network), source,
                "a pad leaving moves the centre onto what is left");

        // The geometry on its own: the range is a radius round the centre.
        int range = ServerConfig.valueOr(ServerConfig.LINK_BASE_RANGE, 16);
        helper.assertTrue(PortFace.withinReach(new Vec3(range, 0, 0), Vec3.ZERO, range),
                "a pad exactly one range from the centre is in reach");
        helper.assertTrue(!PortFace.withinReach(new Vec3(range + 1, 0, 0), Vec3.ZERO, range),
                "and one block further is not");

        helper.succeed();
    }

    /**
     * A network is a named thing a player made: it is listed while it exists, and it exists until its
     * owner deletes it, which they can only do once nothing is on it. Its last pad leaving loses
     * nothing.
     */
    @GameTest(template = PLATFORM)
    public static void aNetworkOutlivesItsLastPadUntilItsOwnerDeletesIt(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        LinkNetworkManager manager = manager(helper);

        UUID network = manager.create("Listing test", helper.getLevel().dimension());
        manager.join(face(helper, SOURCE_PORT), network);
        manager.join(face(helper, NEAR_PORT), network);

        // Other tests share this server, so the list is searched rather than counted.
        LinkNetworkManager.Summary listed = manager.summaries().stream()
                .filter(summary -> summary.id().equals(network)).findFirst().orElse(null);
        helper.assertTrue(listed != null, "the network is listed");
        helper.assertValueEqual(listed.name(), "Listing test", "under its name");
        helper.assertValueEqual(listed.pads(), 2, "with its pads counted");
        helper.assertValueEqual(face(helper, NEAR_PORT).networkName(), "Listing test", "and every pad knows the name");

        ServerPlayer player = testPlayer(helper);
        manager.setChannelKind(network, ITEMS, TransferKind.FLUID, true);
        helper.assertTrue(!manager.delete(network, player), "a network with pads on it cannot be deleted");

        manager.leave(face(helper, SOURCE_PORT));
        manager.leave(face(helper, NEAR_PORT));
        helper.assertTrue(manager.exists(network), "a network with nothing left on it is kept");
        helper.assertTrue(manager.channelCarries(network, ITEMS, TransferKind.FLUID), "with its setup");
        helper.assertTrue(manager.summaries().stream().anyMatch(summary -> summary.id().equals(network) && summary.pads() == 0),
                "and stays on the list, empty");

        helper.assertTrue(manager.delete(network, player), "its owner deletes it once it is empty");
        helper.assertTrue(!manager.exists(network), "and then it is gone");
        helper.assertTrue(manager.summaries().stream().noneMatch(summary -> summary.id().equals(network)), "off the list too");

        UUID unnamed = manager.join(face(helper, SOURCE_PORT), null);
        helper.assertTrue(!manager.networkName(unnamed).isBlank(), "a network made without a name still gets one");

        helper.succeed();
    }

    /**
     * The card's hard constraint is one per network, and an upgrade that would do nothing is
     * refused at the slot rather than accepted and ignored.
     */
    @GameTest(template = PLATFORM)
    public static void aNetworkTakesOnlyOneUnboundCard(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        linkAll(helper, SOURCE_PORT, NEAR_PORT);

        ItemStack card = new ItemStack(ModItems.UNBOUND_LINK_CARD.get());
        helper.assertTrue(face(helper, SOURCE_PORT).upgradeHandler()
                        .insertItem(PortFace.SLOT_CARD, card.copy(), false).isEmpty(),
                "the first card goes in");
        helper.assertTrue(!face(helper, NEAR_PORT).upgradeHandler()
                        .insertItem(PortFace.SLOT_CARD, card.copy(), false).isEmpty(),
                "and the second is handed back, because the network already has one");

        helper.succeed();
    }

    // ------------------------------------------------------------------ whose network it is

    /**
     * A network is its maker's: private until shared, and only the owner opens it up, invites or
     * removes. Flux Networks' half of the design, by request.
     */
    @GameTest(template = PLATFORM)
    public static void aNetworkIsItsOwnersUntilSharedOrInvited(GameTestHelper helper) {
        ServerPlayer owner = testPlayer(helper, "ag-owner");
        ServerPlayer guest = testPlayer(helper, "ag-guest");
        LinkNetworkManager manager = manager(helper);
        // A network nobody made is everyone's until somebody claims it, and then it is theirs alone.
        UUID nobodys = manager.create("Nobody's " + UUID.randomUUID(), helper.getLevel().dimension());
        helper.assertTrue(manager.isOwner(nobodys, guest) && manager.canAccess(nobodys, guest), "nobody's is everyone's");
        helper.assertTrue(manager.claim(nobodys, owner), "until claimed");
        helper.assertTrue(!manager.claim(nobodys, guest), "once");
        helper.assertTrue(manager.isOwner(nobodys, owner) && !manager.canAccess(nobodys, guest), "and then it is the claimant's");

        UUID mine = manager.create("Private " + UUID.randomUUID(), helper.getLevel().dimension(), owner);
        helper.assertTrue(manager.isOwner(mine, owner) && !manager.isOwner(mine, guest), "the maker owns it");
        helper.assertTrue(manager.canAccess(mine, owner) && !manager.canAccess(mine, guest), "and nobody else may use it yet");
        helper.assertTrue(manager.summaries(guest).stream().noneMatch(summary -> summary.id().equals(mine)),
                "a guest's picker does not even list it");
        helper.assertTrue(manager.summaries(owner).stream().anyMatch(summary -> summary.id().equals(mine)), "the owner's does");
        ServerPlayer op = testPlayer(helper, "ag-op", 2);
        helper.assertTrue(manager.isOwner(mine, op) && manager.canAccess(mine, op), "an operator runs every network");
        helper.assertTrue(manager.summaries(op).stream().anyMatch(summary -> summary.id().equals(mine)), "and sees every one");
        helper.assertTrue(!manager.setShared(mine, guest, true) && !manager.isShared(mine), "a guest cannot open it up");
        helper.assertTrue(!manager.invite(mine, guest, guest.getUUID(), "ag-guest"), "nor let themselves in");
        helper.assertTrue(!manager.invite(mine, owner, owner.getUUID(), "ag-owner"), "and the owner needs no letting in");

        // Through the windows: a pick the guest may not make does nothing, and neither does carry.
        barrel(helper, SOURCE);
        List<LinkNetworkManager.Summary> theirs = manager.summaries(owner);
        NetworkPickerMenu forged = new NetworkPickerMenu(1, guest.getInventory(), helper.absolutePos(SOURCE_PORT),
                Direction.NORTH, new NetworkPickerMenu.Snapshot(theirs, Optional.empty(), false, List.of()), false);
        for (int index = 0; index < theirs.size(); index++) {
            if (theirs.get(index).id().equals(mine)) {
                helper.assertTrue(!forged.clickMenuButton(guest, NetworkPickerMenu.BUTTON_PICK_BASE + index),
                        "a pick of a network the guest may not use is refused");
            }
        }
        helper.assertTrue(!face(helper, SOURCE_PORT).isLinked(), "and the pad stays off it");
        manager.join(face(helper, SOURCE_PORT), mine);
        LinkPortMenu window = new LinkPortMenu(1, guest.getInventory(), port(helper, SOURCE_PORT), Direction.NORTH);
        helper.assertTrue(!window.mayChangeNetwork(), "the guest's window says the network is not theirs");
        helper.assertTrue(!window.clickMenuButton(guest, LinkPortMenu.BUTTON_CARRY), "so carried is refused");
        helper.assertTrue(!manager.channelCarries(mine, 0, TransferKind.ITEM), "and nothing changed");

        // A click on the pad opens nothing for the guest, bare hand or tool; the owner gets the window.
        guest.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        rightClick(helper, guest, SOURCE_PORT, false);
        helper.assertTrue(!(guest.containerMenu instanceof LinkPortMenu), "a guest's click on the pad opens nothing");
        guest.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LINKING_TOOL.toStack());
        rightClick(helper, guest, SOURCE_PORT, false);
        helper.assertTrue(!(guest.containerMenu instanceof LinkPortMenu), "nor does the tool's");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        rightClick(helper, owner, SOURCE_PORT, false);
        padWindow(helper, owner);
        owner.closeContainer();

        // The owner invites by id, and the guest is in until taken off again.
        // The name lookup asks who is online or known, and a test server has neither: by id is the rest of it.
        helper.assertTrue(manager.invite(mine, owner, guest.getUUID(), "ag-guest"), "the owner invites");
        helper.assertTrue(manager.canAccess(mine, guest) && !manager.isOwner(mine, guest), "the guest is in, and not the owner");
        helper.assertTrue(manager.members(mine).stream().anyMatch(member -> member.id().equals(guest.getUUID())), "and listed");

        // The network can be handed to one of the invited, and the old owner stays invited.
        helper.assertTrue(!manager.transfer(mine, guest, guest.getUUID()), "a member cannot take the network");
        helper.assertTrue(manager.transfer(mine, owner, guest.getUUID()), "the owner can hand it over");
        helper.assertTrue(manager.isOwner(mine, guest) && !manager.isOwner(mine, owner) && manager.canAccess(mine, owner),
                "and is then a guest on their old network");
        helper.assertTrue(manager.transfer(mine, guest, owner.getUUID()), "which can be handed back");
        helper.assertTrue(manager.isOwner(mine, owner) && !manager.isOwner(mine, guest) && manager.canAccess(mine, guest),
                "as it was");

        // A member's own pad comes off the network with them. Placed a layer up: the platform's
        // floor is at y=1, and a pad is placed into air, not into the floor.
        BlockPos theirBarrel = NEAR.above();
        BlockPos theirPad = NEAR_PORT.above();
        helper.setBlock(theirBarrel, Blocks.BARREL);
        placePad(helper, guest, theirBarrel, Direction.SOUTH);
        helper.assertTrue(guest.getUUID().equals(face(helper, theirPad).placer()), "a placed pad remembers who placed it");
        manager.join(face(helper, theirPad), mine);
        helper.assertTrue(face(helper, theirPad).isLinked(), "and an invited player's pad is on the network");
        helper.assertTrue(manager.summaries(guest).stream().anyMatch(summary -> summary.id().equals(mine)), "the picker shows it now");
        guest.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        rightClick(helper, guest, SOURCE_PORT, false);
        window = padWindow(helper, guest);
        helper.assertTrue(window.mayChangeNetwork() && window.clickMenuButton(guest, LinkPortMenu.BUTTON_CARRY),
                "the pad opens for the guest now, and carried is theirs to switch");
        guest.closeContainer();
        helper.assertTrue(manager.channelCarries(mine, 0, TransferKind.ITEM), "which it did");
        helper.assertTrue(!manager.removeMember(mine, guest, guest.getUUID()), "a guest cannot remove");
        helper.assertTrue(manager.removeMember(mine, owner, guest.getUUID()) && !manager.canAccess(mine, guest),
                "the owner can, and the guest is out");
        helper.assertTrue(!face(helper, theirPad).isLinked(), "and their pad came off with them");
        helper.assertTrue(face(helper, SOURCE_PORT).isLinked(), "a pad nobody placed stays on regardless");

        // Public: anyone.
        helper.assertTrue(manager.setShared(mine, owner, true) && manager.canAccess(mine, guest), "shared, the guest is in again");
        helper.assertTrue(manager.summaries(guest).stream().anyMatch(summary -> summary.id().equals(mine) && summary.access().shared()),
                "and the picker says so");
        helper.succeed();
    }

    // ------------------------------------------------------------------ at scale

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Rounds of the stress test: enough for the JIT to have seen the code. */
    private static final int ROUNDS = 20;

    /**
     * Three hundred pads on one network and fifty of them pushed into as a hopper would, then a
     * timed pass with fifty senders, once per spread. The receiver lists are sorted once and
     * kept, so what is left is the sends themselves: even split has each of fifty senders
     * putting one item into each of thirty-two barrels, the worst case; the other three hand
     * each send to one barrel. The numbers go to the log; the bounds are a regression guard,
     * not a target.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void threeHundredPadsAndFiftyPushersStayCheap(GameTestHelper helper) {
        stressNetwork(helper, Distribution.EVEN_SPLIT);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void threeHundredPadsAndFiftyPushersStayCheapNearestFirst(GameTestHelper helper) {
        stressNetwork(helper, Distribution.NEAREST_FIRST);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void threeHundredPadsAndFiftyPushersStayCheapRoundRobin(GameTestHelper helper) {
        stressNetwork(helper, Distribution.ROUND_ROBIN);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void threeHundredPadsAndFiftyPushersStayCheapRandom(GameTestHelper helper) {
        stressNetwork(helper, Distribution.RANDOM);
    }

    private static void stressNetwork(GameTestHelper helper, Distribution distribution) {
        // A checkerboard of barrels and pad blocks, so every pad face looks straight at a barrel.
        for (int x = 1; x <= 8; x++) {
            for (int y = 1; y <= 8; y++) {
                for (int z = 1; z <= 8; z++) {
                    if (((x + y + z) & 1) == 0) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.BARREL);
                    }
                }
            }
        }
        List<PortFace> pads = new ArrayList<>();
        for (int x = 1; x <= 8 && pads.size() < 300; x++) {
            for (int y = 1; y <= 8 && pads.size() < 300; y++) {
                for (int z = 1; z <= 8 && pads.size() < 300; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (((x + y + z) & 1) == 0) {
                        continue;
                    }
                    for (Direction facing : Direction.values()) {
                        if (pads.size() < 300 && helper.getBlockState(pos.relative(facing)).is(Blocks.BARREL)) {
                            pad(helper, pos, facing);
                            pads.add(port(helper, pos).face(facing));
                        }
                    }
                }
            }
        }
        helper.assertValueEqual(pads.size(), 300, "three hundred pads");

        LinkNetworkManager manager = manager(helper);
        UUID network = null;
        for (PortFace pad : pads) {
            network = manager.join(pad, network);
        }
        manager.setChannelKind(network, ITEMS, TransferKind.ITEM, true);
        manager.setChannelDistribution(network, ITEMS, TransferKind.ITEM, distribution);
        List<PortFace> pushers = pads.subList(0, 50);
        for (PortFace pad : pads) {
            pad.link(ITEMS, TransferKind.ITEM).setRole(true, false);
        }
        for (PortFace pusher : pushers) {
            pusher.link(ITEMS, TransferKind.ITEM).setPush(true);
        }
        BlockPos spot = new BlockPos(9, 1, 9);
        helper.setBlock(spot, ModBlocks.ENERGY_INJECTOR.get());
        EnergyInjectorBlockEntity injector = (EnergyInjectorBlockEntity) helper.getBlockEntity(spot);
        injector.energyStorage().setEnergy(injector.energyStorage().capacity());
        manager.joinInjector(injector, network);

        // Fifty stacks pushed, each simulated then moved, as a hopper or a pattern provider does it;
        // then fifty single items, four times over, the hopper's own rhythm. Each measured cold on
        // the first round (the test is the first time this code runs) and warm as the best of the
        // rest, so a garbage collection landing in one round does not stand for the code.
        long stackCold = 0;
        long stackWarm = 0;
        long singleCold = 0;
        long singleWarm = 0;
        for (int round = 0; round < ROUNDS; round++) {
            long start = System.nanoTime();
            for (PortFace pusher : pushers) {
                ItemStack stack = new ItemStack(Items.COBBLESTONE, 64);
                pusher.passthrough().insertItem(0, stack, true);
                helper.assertTrue(pusher.passthrough().insertItem(0, stack, false).isEmpty(), "a stack finds room");
            }
            long stackMicros = (System.nanoTime() - start) / 1_000 / pushers.size();
            start = System.nanoTime();
            for (int n = 0; n < 4; n++) {
                for (PortFace pusher : pushers) {
                    ItemStack one = new ItemStack(Items.COBBLESTONE);
                    pusher.passthrough().insertItem(0, one, true);
                    pusher.passthrough().insertItem(0, one, false);
                }
            }
            long singleMicros = (System.nanoTime() - start) / 1_000 / (4 * pushers.size());
            if (round == 0) {
                stackCold = stackMicros;
                singleCold = singleMicros;
                stackWarm = stackMicros;
                singleWarm = singleMicros;
            } else {
                stackWarm = Math.min(stackWarm, stackMicros);
                singleWarm = Math.min(singleWarm, singleMicros);
            }
        }

        // Passes with fifty senders, each due and each with a full barrel to send out of: the
        // first cold, the best of the rest warm. The clock is moved past every Delay between passes.
        for (PortFace pusher : pushers) {
            pusher.link(ITEMS, TransferKind.ITEM).setRole(true, true);
            Container barrel = (Container) helper.getLevel().getBlockEntity(pusher.targetPos());
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                barrel.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
        long passCold = 0;
        long passWarm = 0;
        long time = helper.getLevel().getGameTime();
        for (int round = 0; round < ROUNDS; round++) {
            injector.energyStorage().setEnergy(injector.energyStorage().capacity());
            long start = System.nanoTime();
            boolean moved = manager.passNow(network, time);
            long passMicros = (System.nanoTime() - start) / 1_000;
            helper.assertTrue(moved, "pass " + round + " moved something");
            if (round == 0) {
                passCold = passMicros;
                passWarm = passMicros;
            } else {
                passWarm = Math.min(passWarm, passMicros);
            }
            time += 10_000;
        }

        LOGGER.info("300 pads, 50 pushers, {}, cold then best of {} rounds: pushed stack {} / {} us, "
                        + "pushed item {} / {} us, pass of 50 senders {} / {} us",
                distribution, ROUNDS, stackCold, stackWarm, singleCold, singleWarm, passCold, passWarm);
        helper.assertTrue(stackWarm < 5_000, "a pushed stack costs under 5 ms, took " + stackWarm + " us");
        helper.assertTrue(singleWarm < 2_000, "a pushed item costs under 2 ms, took " + singleWarm + " us");
        helper.assertTrue(passWarm < 50_000, "a pass of fifty senders costs under 50 ms, took " + passWarm + " us");
        helper.succeed();
    }

    // ------------------------------------------------------------------ pushed into a pad

    /**
     * The pattern-provider case: the block behind a pad hands it items, and they go straight out
     * on the channels the pad sends on, with no injector on the network and no clock in between.
     */
    @GameTest(template = PLATFORM)
    public static void aBlockPushingIntoAPadSendsThroughTheNetworkForFree(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, NEAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.ITEM, true);
        items(helper, NEAR_PORT).setRole(true, false);

        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(SOURCE_PORT), Direction.UP) == null,
                "a face with no pad on it is nothing to push into");
        IItemHandler door = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(SOURCE_PORT), Direction.NORTH);
        helper.assertTrue(door != null, "the pad's own face answers");
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 64);
        helper.assertValueEqual(door.insertItem(0, cobble, false).getCount(), 64,
                "a pad with PU on no channel hands the whole lot back");
        items(helper, SOURCE_PORT).setRole(false, true);
        helper.assertValueEqual(door.insertItem(0, cobble, false).getCount(), 64,
                "EX is the timed pull, not the door: still handed back");
        items(helper, SOURCE_PORT).setRole(false, false);

        items(helper, SOURCE_PORT).setPush(true);
        helper.assertValueEqual(door.insertItem(0, cobble, true).getCount(), 0, "simulated: there is room for all of it");
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 0, "and a simulation moves nothing");
        helper.assertValueEqual(door.insertItem(0, cobble, false).getCount(), 0, "pushed: all of it taken");
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 64,
                "and in the barrel at the other end at once, with no injector paying for it");
        helper.assertValueEqual(count(helper, SOURCE, Items.COBBLESTONE), 0, "the pad's own block is not where it went");
        helper.assertTrue(door.getStackInSlot(0).isEmpty() && door.extractItem(0, 1, false).isEmpty(),
                "the pad holds nothing and hands nothing out");
        helper.succeed();
    }

    /** What a pushed stack still obeys: spread, the receivers' keep, and the filters. */
    @GameTest(template = PLATFORM)
    public static void aPushedStackObeysTheReceiversAndTheSpread(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        barrel(helper, FAR);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, NEAR_PORT), network);
        manager.join(face(helper, FAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.ITEM, true);
        items(helper, SOURCE_PORT).setPush(true);
        items(helper, NEAR_PORT).setRole(true, false);
        items(helper, FAR_PORT).setRole(true, false);
        IItemHandler door = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(SOURCE_PORT), Direction.NORTH);
        helper.assertTrue(door != null, "the pad's own face answers");

        // Even split shares a pushed stack as it shares a send.
        manager.setChannelDistribution(network, ITEMS, TransferKind.ITEM, Distribution.EVEN_SPLIT);
        helper.assertValueEqual(door.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).getCount(), 0, "all taken");
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 32, "half here");
        helper.assertValueEqual(count(helper, FAR, Items.COBBLESTONE), 32, "half there");

        // A hopper simulates before it moves, and a simulation must not take a turn: singles still alternate.
        for (int n = 0; n < 4; n++) {
            door.insertItem(0, new ItemStack(Items.COBBLESTONE), true);
            door.insertItem(0, new ItemStack(Items.COBBLESTONE), false);
        }
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 34, "two of four singles here");
        helper.assertValueEqual(count(helper, FAR, Items.COBBLESTONE), 34, "and two there");

        // A receiver's keep still stops it, and the rest goes to whoever is next.
        manager.setChannelDistribution(network, ITEMS, TransferKind.ITEM, Distribution.NEAREST_FIRST);
        items(helper, NEAR_PORT).setKeep(40);
        helper.assertValueEqual(door.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).getCount(), 0, "still all taken");
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 40, "the nearest stops at its keep");
        helper.assertValueEqual(count(helper, FAR, Items.COBBLESTONE), 92, "and the far one takes the rest");

        // A receiver's filter says no, and what it refuses comes back to the pusher.
        ItemStack filter = ModItems.FILTER.toStack();
        FilterItem.setContents(filter, FilterContents.EMPTY.add(FilterContents.Entry.ofItem(new ItemStack(Items.DIRT))));
        face(helper, FAR_PORT).filterHandler().setStackInSlot(PortFace.filterSlot(ITEMS, TransferKind.ITEM), filter);
        items(helper, NEAR_PORT).setRole(false, false);
        helper.assertValueEqual(door.insertItem(0, new ItemStack(Items.COBBLESTONE, 8), false).getCount(), 8,
                "cobble is not on the far pad's list, so it comes back");
        helper.assertValueEqual(door.insertItem(0, new ItemStack(Items.DIRT, 8), false).getCount(), 0, "dirt is");
        helper.assertValueEqual(count(helper, FAR, Items.DIRT), 8, "and lands");
        helper.succeed();
    }

    /**
     * Round robin hands each send whole to the next receiver in line; random to one it rolled.
     * Both roll or step AFTER the real push, so the simulated push a hopper makes first lands
     * where the real one will.
     */
    @GameTest(template = PLATFORM)
    public static void roundRobinAndRandomHandEachSendToOneReceiver(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        barrel(helper, FAR);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, NEAR_PORT), network);
        manager.join(face(helper, FAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.ITEM, true);
        items(helper, SOURCE_PORT).setPush(true);
        items(helper, NEAR_PORT).setRole(true, false);
        items(helper, FAR_PORT).setRole(true, false);
        IItemHandler door = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(SOURCE_PORT), Direction.NORTH);
        helper.assertTrue(door != null, "the pad's own face answers");

        // Round robin: a whole stack to the nearest, the next whole stack to the other.
        manager.setChannelDistribution(network, ITEMS, TransferKind.ITEM, Distribution.ROUND_ROBIN);
        helper.assertValueEqual(door.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).getCount(), 0, "all taken");
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 64, "the first send goes whole to the nearest");
        helper.assertValueEqual(count(helper, FAR, Items.COBBLESTONE), 0, "and nothing to the other");
        helper.assertValueEqual(door.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).getCount(), 0, "all taken again");
        helper.assertValueEqual(count(helper, FAR, Items.COBBLESTONE), 64, "the second goes whole to the other");
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 64, "and the nearest gets nothing this time");
        for (int n = 0; n < 4; n++) {
            door.insertItem(0, new ItemStack(Items.COBBLESTONE), true);
            door.insertItem(0, new ItemStack(Items.COBBLESTONE), false);
        }
        helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 66, "singles take turns, simulations take none");
        helper.assertValueEqual(count(helper, FAR, Items.COBBLESTONE), 66, "two each");

        // Random: the real push lands where the queue stood, which is where the simulation looked.
        manager.setChannelDistribution(network, ITEMS, TransferKind.ITEM, Distribution.RANDOM);
        PortChannel sender = items(helper, SOURCE_PORT);
        for (int n = 0; n < 40; n++) {
            int at = sender.peekRoundRobinCursor(2);
            int near = count(helper, NEAR, Items.COBBLESTONE);
            int far = count(helper, FAR, Items.COBBLESTONE);
            door.insertItem(0, new ItemStack(Items.COBBLESTONE), true);
            helper.assertTrue(count(helper, NEAR, Items.COBBLESTONE) == near && count(helper, FAR, Items.COBBLESTONE) == far,
                    "a simulation moves nothing");
            helper.assertValueEqual(door.insertItem(0, new ItemStack(Items.COBBLESTONE), false).getCount(), 0, "taken");
            helper.assertValueEqual(count(helper, at == 0 ? NEAR : FAR, Items.COBBLESTONE), (at == 0 ? near : far) + 1,
                    "and lands on the one the queue stood at");
        }
        helper.assertTrue(count(helper, NEAR, Items.COBBLESTONE) > 66 && count(helper, FAR, Items.COBBLESTONE) > 66,
                "forty rolls picked both at least once");
        helper.succeed();
    }

    /** Fluid pushed into a pad goes the same way, into a tank on the channel. */
    @GameTest(template = PLATFORM)
    public static void aPushedFluidFillsATankOnTheChannel(GameTestHelper helper) {
        barrel(helper, SOURCE);
        helper.setBlock(NEAR, Blocks.CAULDRON);
        pad(helper, NEAR_PORT, Direction.NORTH);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, NEAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.FLUID, true);
        face(helper, SOURCE_PORT).link(ITEMS, TransferKind.FLUID).setPush(true);
        face(helper, NEAR_PORT).link(ITEMS, TransferKind.FLUID).setRole(true, false);
        IFluidHandler door = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(SOURCE_PORT), Direction.NORTH);
        helper.assertTrue(door != null, "the pad's face answers for fluid");

        FluidStack bucket = new FluidStack(Fluids.WATER, 1000);
        helper.assertValueEqual(door.fill(bucket, IFluidHandler.FluidAction.SIMULATE), 1000, "simulated: a bucket fits");
        helper.assertBlock(NEAR, block -> block == Blocks.CAULDRON, "and a simulation fills nothing");
        helper.assertValueEqual(door.fill(bucket, IFluidHandler.FluidAction.EXECUTE), 1000, "pushed: a bucket taken");
        helper.assertBlock(NEAR, block -> block == Blocks.WATER_CAULDRON, "and the cauldron on the channel holds it");
        helper.assertTrue(door.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "nothing drains back out of a pad");
        helper.succeed();
    }

    /** FE pushed into a pad goes the same way, into a bank on the channel. */
    @GameTest(template = PLATFORM)
    public static void aPushedChargeReachesABankOnTheChannel(GameTestHelper helper) {
        barrel(helper, SOURCE);
        helper.setBlock(NEAR, ModBlocks.SURGE_BANK.get());
        openEnergy(machine(helper, NEAR));
        pad(helper, NEAR_PORT, Direction.NORTH);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, NEAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.ENERGY, true);
        face(helper, SOURCE_PORT).link(ITEMS, TransferKind.ENERGY).setPush(true);
        face(helper, NEAR_PORT).link(ITEMS, TransferKind.ENERGY).setRole(true, false);
        IEnergyStorage door = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(SOURCE_PORT), Direction.NORTH);
        helper.assertTrue(door != null && door.canReceive() && !door.canExtract(), "the pad's face takes FE and gives none");

        helper.assertValueEqual(door.receiveEnergy(5000, true), 5000, "simulated: the bank has room");
        helper.assertValueEqual(machine(helper, NEAR).energyStorage().stored(), 0L, "and a simulation moves nothing");
        helper.assertValueEqual(door.receiveEnergy(5000, false), 5000, "pushed: taken");
        helper.assertValueEqual(machine(helper, NEAR).energyStorage().stored(), 5000L, "and in the bank at once");
        helper.assertValueEqual(door.extractEnergy(1000, false), 0, "nothing comes back out");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the pad, in isolation

    @GameTest(template = EMPTY)
    public static void priorityStepsBothWaysAndGoesNegative(GameTestHelper helper) {
        PortChannel port = detachedPort().link(ITEMS, TransferKind.ITEM);

        helper.assertValueEqual(port.priority(), 0, "a fresh pad sits in the middle");
        port.nudgePriority(-1);
        helper.assertValueEqual(port.priority(), -1,
                "and can be told to go last, which a button that only cycles upwards cannot say");
        port.nudgePriority(-8);
        helper.assertValueEqual(port.priority(), -9, "the big step moves eight at a time");
        port.nudgePriority(1000);
        helper.assertValueEqual(port.priority(), PortChannel.MAX_PRIORITY,
                "and it stops at the end rather than wrapping round to the bottom");

        helper.succeed();
    }

    /** The filter a channel obeys is the Filter item in that channel's slot, and only that one. */
    @GameTest(template = EMPTY)
    public static void aPadReadsTheFilterInItsSlotForThatChannel(GameTestHelper helper) {
        PortFace pad = detachedPort();
        PortChannel white = pad.link(0, TransferKind.ITEM);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        ItemStack dirt = new ItemStack(Items.DIRT);

        helper.assertTrue(white.allowsItem(cobble, true) && white.allowsItem(dirt, true), "no filter: anything passes");

        ItemStack filter = ModItems.FILTER.toStack();
        FilterItem.setContents(filter, FilterContents.EMPTY.add(FilterContents.Entry.ofItem(cobble)));
        pad.filterHandler().setStackInSlot(0, filter);
        helper.assertTrue(white.allowsItem(cobble, true), "a filter in white's slot passes what it lists");
        helper.assertTrue(!white.allowsItem(dirt, true), "and stops the rest");
        helper.assertTrue(pad.link(1, TransferKind.ITEM).allowsItem(dirt, true),
                "while the next channel, with no filter of its own, still passes anything");
        helper.assertTrue(!pad.filterHandler().isItemValid(0, cobble), "and the slot takes nothing but a Filter");

        helper.succeed();
    }

    /** A pad is set up before it is on anything; the first network it joins takes its kinds. */
    @GameTest(template = PLATFORM)
    public static void aPadCanBeSetUpBeforeItJoinsAndTheNetworkTakesItsKinds(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        ServerPlayer player = testPlayer(helper);
        PortFace first = face(helper, SOURCE_PORT);
        PortFace second = face(helper, NEAR_PORT);

        LinkPortMenu menu = new LinkPortMenu(1, player.getInventory(), port(helper, SOURCE_PORT), Direction.NORTH);
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_CARRY);
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_EXTRACT);
        helper.assertTrue(!first.isLinked(), "no network yet");
        helper.assertTrue(first.carriesLocally(0, TransferKind.ITEM), "but the pad remembers what white carries");
        helper.assertTrue(menu.channelCarries(0, TransferKind.ITEM), "and its window shows it");
        helper.assertTrue(!menu.clickMenuButton(player, LinkPortMenu.BUTTON_SPREAD),
                "spread is the network's business, so that button waits for one");

        second.setLocalKind(0, TransferKind.FLUID, true);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(first, null);
        helper.assertValueEqual(manager.channelKinds(network, 0), ChannelSettings.bit(TransferKind.ITEM),
                "the first pad's kinds become the network's");
        manager.join(second, network);
        helper.assertValueEqual(manager.channelKinds(network, 0), ChannelSettings.bit(TransferKind.ITEM),
                "a later pad does not overrule a channel that already carries something");
        manager.leave(second);
        second.setLocalKind(3, TransferKind.ENERGY, true);
        manager.join(second, network);
        helper.assertTrue(manager.channelCarries(network, 3, TransferKind.ENERGY),
                "but it fills in a channel nobody had given a job");

        manager.leave(first);
        helper.assertTrue(first.carriesLocally(0, TransferKind.ITEM), "a pad that leaves keeps a copy of the kinds");
        helper.assertTrue(first.carriesLocally(3, TransferKind.ENERGY), "all of them");

        helper.succeed();
    }

    /**
     * One channel carries items and energy at once, and a sender on it moves both on its one clock:
     * the items go to the pad on the barrel, the FE to the pad on the bank, each ignoring what it
     * has no capability for. Symo's ask: "we should be able to have items, fluid and energy on
     * the same channel".
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void oneChannelCarriesItemsAndEnergyAtOnce(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(SOURCE, ModBlocks.RESONANCE_CRUSHER.get());
                    MachineBlockEntity source = machine(helper, SOURCE);
                    openEnergy(source);
                    for (RelativeSide side : RelativeSide.values()) {
                        source.sideConfig().set(TransferKind.ITEM, side, IoMode.BOTH);
                    }
                    source.invalidateCapabilitiesOnSideChange();
                    source.energyStorage().setEnergy(source.energyStorage().capacity());
                    if (source instanceof ResonanceCrusherBlockEntity crusher) {
                        crusher.outputHandler().insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
                    }
                    barrel(helper, NEAR);
                    helper.setBlock(FAR, ModBlocks.SURGE_BANK.get());
                    openEnergy(machine(helper, FAR));

                    pad(helper, SOURCE_PORT, Direction.NORTH);
                    pad(helper, NEAR_PORT, Direction.NORTH);
                    pad(helper, FAR_PORT, Direction.NORTH);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT, FAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    carry(helper, ITEMS, TransferKind.ENERGY);
                    helper.assertValueEqual(manager(helper).channelKinds(face(helper, SOURCE_PORT).networkId(), ITEMS),
                            ChannelSettings.bit(TransferKind.ITEM) | ChannelSettings.bit(TransferKind.ENERGY),
                            "white carries both");
                    face(helper, SOURCE_PORT).link(ITEMS, TransferKind.ITEM).setRole(false, true);
                    face(helper, NEAR_PORT).link(ITEMS, TransferKind.ITEM).setRole(true, false);
                    face(helper, FAR_PORT).link(ITEMS, TransferKind.ITEM).setRole(true, false);
                    // Roles are per kind: the bank end says so for energy, in its own words.
                    face(helper, SOURCE_PORT).link(ITEMS, TransferKind.ENERGY).setRole(false, true);
                    face(helper, FAR_PORT).link(ITEMS, TransferKind.ENERGY).setRole(true, false);
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 64,
                            "the items reached the barrel");
                    helper.assertTrue(machine(helper, FAR).energyStorage().stored() > 0,
                            "and the FE reached the bank, on the same channel");
                })
                .thenSucceed();
    }

    /** A linked pad or injector hands the tool straight to its own window; the picker is for the unlinked. */
    @GameTest(template = PLATFORM)
    public static void theToolOpensTheWindowOfABlockThatIsAlreadyLinked(GameTestHelper helper) {
        barrel(helper, SOURCE);
        helper.setBlock(INJECTOR, ModBlocks.ENERGY_INJECTOR.get());
        ServerPlayer player = testPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LINKING_TOOL.toStack());

        rightClick(helper, player, SOURCE_PORT, false);
        picker(helper, player).create(player, "Tool test");
        rightClick(helper, player, SOURCE_PORT, false);
        padWindow(helper, player);
        player.closeContainer();

        rightClick(helper, player, INJECTOR, false);
        pick(helper, player, "Tool test");
        helper.assertTrue(injector(helper).isLinked(), "the injector took the network from the picker");
        rightClick(helper, player, INJECTOR, false);
        helper.assertTrue(player.containerMenu instanceof EnergyInjectorMenu,
                "and the tool now opens its window rather than the picker, but "
                        + player.containerMenu.getClass().getSimpleName());
        player.containerMenu.clickMenuButton(player, EnergyInjectorMenu.BUTTON_NETWORK_LEAVE);
        helper.assertTrue(!injector(helper).isLinked(), "whose network line takes it off again");

        helper.succeed();
    }

    /** A network has a colour of its own, and the picker paints it any dye. */
    @GameTest(template = PLATFORM)
    public static void aNetworkHasAColourThePlayerCanChange(GameTestHelper helper) {
        barrel(helper, SOURCE);
        helper.setBlock(INJECTOR, ModBlocks.ENERGY_INJECTOR.get());
        ServerPlayer player = testPlayer(helper);
        LinkNetworkManager manager = manager(helper);
        PortFace pad = face(helper, SOURCE_PORT);
        UUID network = manager.join(pad, null);
        manager.joinInjector(injector(helper), network);

        helper.assertValueEqual(manager.colour(network), LinkNetworkManager.defaultColour(network),
                "a fresh network is coloured from its id, so it is told apart before anyone picks");
        helper.assertValueEqual(pad.networkColour(), manager.colour(network), "and the pad carries a copy");

        new LinkPortMenu(1, player.getInventory(), port(helper, SOURCE_PORT), Direction.NORTH)
                .clickMenuButton(player, LinkPortMenu.BUTTON_NETWORK_PICK);
        NetworkPickerMenu picker = picker(helper, player);
        picker.clickMenuButton(player, NetworkPickerMenu.BUTTON_COLOUR_BASE + DyeColor.LIME.getId());
        int lime = NetworkPickerMenu.dyeColour(DyeColor.LIME.getId());
        helper.assertValueEqual(manager.colour(network), lime, "a colour cell paints the network");
        helper.assertValueEqual(pad.networkColour(), lime, "the pad hears about it");
        helper.assertValueEqual(injector(helper).networkColour(), lime, "so does the injector");
        helper.assertTrue(player.containerMenu == picker, "and the picker stays open for another go");

        helper.succeed();
    }

    /** Delay is the other half of the rate: Amount every Delay ticks, and never more often. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void aDelayPacesASender(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    PortChannel sender = items(helper, SOURCE_PORT);
                    sender.setRole(false, true);
                    sender.nudgeAmount(4 - sender.amount());
                    sender.nudgeDelay(40 - sender.delayTicks());
                    items(helper, NEAR_PORT).setRole(true, false);
                    helper.assertValueEqual(sender.amount(), 4, "four a send");
                    helper.assertValueEqual(sender.delayTicks(), 40, "every forty ticks");
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    int moved = count(helper, NEAR, Items.COBBLESTONE);
                    helper.assertTrue(moved > 0 && moved <= 8,
                            "forty-five ticks hold at most two sends of four, but " + moved + " moved");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    int moved = count(helper, NEAR, Items.COBBLESTONE);
                    helper.assertTrue(moved >= 8 && moved <= 12,
                            "forty more ticks are one more send, so eight to twelve, but " + moved + " moved");
                })
                .thenSucceed();
    }

    /** Amount caps what one pad moves in a send, so a pad can trickle where the network would pour. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void anAmountCapsWhatOnePassMoves(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    PortChannel sender = items(helper, SOURCE_PORT);
                    sender.setRole(false, true);
                    sender.nudgeAmount(4 - sender.amount());
                    items(helper, NEAR_PORT).setRole(true, false);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    int moved = count(helper, NEAR, Items.COBBLESTONE);
                    helper.assertTrue(moved > 0 && moved < 64 && moved % 4 == 0,
                            "four a send: after forty ticks some have moved, in fours, and not all, but " + moved);
                })
                .thenSucceed();
    }

    /** A tier in the pad raises what one send carries and lowers how few ticks may pass between sends. */
    @GameTest(template = PLATFORM)
    public static void aTierRaisesAPadsAmountAndLowersItsDelay(GameTestHelper helper) {
        barrel(helper, SOURCE);
        PortFace pad = face(helper, SOURCE_PORT);
        int base = pad.amountCeiling(TransferKind.ITEM);
        int floor = pad.delayFloor();

        // Installed with the gesture the other upgrades use: a crouch and a click on the pad.
        ServerPlayer player = testPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.NETHERITE_TIER_UPGRADE.toStack());
        rightClick(helper, player, SOURCE_PORT, true);

        helper.assertValueEqual(pad.tier(), MachineTier.NETHERITE, "the click puts the tier in the pad");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "and the item is spent");
        helper.assertValueEqual(pad.amountCeiling(TransferKind.ITEM),
                Math.min(PortChannel.MAX_ITEMS, base * MachineTier.NETHERITE.linkAmountMultiplier()),
                "the amount ceiling is the tier's multiple of the base");
        helper.assertValueEqual(pad.delayFloor(), MachineTier.NETHERITE.linkDelayTicks(), "and the delay floor is the tier's");
        helper.assertTrue(pad.delayFloor() < floor, "which is lower than an untiered pad's " + floor);
        PortChannel untouched = pad.link(ITEMS, TransferKind.ITEM);
        helper.assertTrue(untouched.amountIsAuto() && untouched.amount() == pad.amountCeiling(TransferKind.ITEM),
                "an untouched channel follows the new limit on its own");

        // One per pad, and only a tier fits the tier slot.
        helper.assertValueEqual(pad.upgradeHandler().insertItem(PortFace.SLOT_TIER, ModItems.IRON_TIER_UPGRADE.toStack(), true).getCount(), 1,
                "a second tier is refused");
        helper.assertTrue(!pad.upgradeHandler().isItemValid(PortFace.SLOT_TIER, ModItems.LINK_RANGE_UPGRADE.toStack()),
                "and a range upgrade is not a tier");
        helper.succeed();
    }

    /** Keep on a sending pad is a stock that never ships: sixty-four in, eight stay behind. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aSendingPadLeavesItsKeepBehind(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    barrel(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    barrel(helper, NEAR);
                    linkAll(helper, SOURCE_PORT, NEAR_PORT);
                    carry(helper, ITEMS, TransferKind.ITEM);
                    items(helper, SOURCE_PORT).setRole(false, true);
                    items(helper, SOURCE_PORT).nudgeKeep(8);
                    items(helper, NEAR_PORT).setRole(true, false);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(count(helper, SOURCE, Items.COBBLESTONE), 8, "the stock stays");
                    helper.assertValueEqual(count(helper, NEAR, Items.COBBLESTONE), 56, "and everything above it goes");
                })
                .thenSucceed();
    }

    /**
     * The mode that makes a catalyst loop work: a crafter that uses a rune without consuming it
     * needs exactly one rune, and a pad that kept shovelling would fill it with runes for ever.
     */
    @GameTest(template = EMPTY)
    public static void keepInTargetStopsAtTheNumberItWasGiven(GameTestHelper helper) {
        PortChannel port = detachedPort().link(ITEMS, TransferKind.ITEM);
        net.neoforged.neoforge.items.ItemStackHandler target = new net.neoforged.neoforge.items.ItemStackHandler(3);
        ItemStack rune = new ItemStack(Items.COBBLESTONE);

        helper.assertValueEqual(port.acceptableCount(rune, target), Integer.MAX_VALUE,
                "with keep-in-target off a pad is not counting anything");

        port.nudgeKeep(1);
        helper.assertValueEqual(port.keepAmount(), 1, "one step is the catalyst case: one");
        helper.assertValueEqual(port.acceptableCount(rune, target), 1,
                "an empty target is one short of its one");

        target.setStackInSlot(0, rune.copy());
        helper.assertValueEqual(port.acceptableCount(rune, target), 0,
                "and once it has its one, no more are sent");

        // The same number the other way round: a sending pad leaves that many behind.
        net.neoforged.neoforge.items.ItemStackHandler source = new net.neoforged.neoforge.items.ItemStackHandler(2);
        source.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 10));
        source.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 5));
        port.nudgeKeep(7);
        helper.assertValueEqual(port.surplusCount(rune, source), 7,
                "a sender keeping eight may ship the seven above it, counted across the whole block");
        port.nudgeKeep(1000000);
        helper.assertValueEqual(port.keepAmount(), PortChannel.MAX_ITEMS,
                "and the number stops at its ceiling rather than wrapping");

        helper.succeed();
    }

    /** What a player may type into a number: sums, brackets and suffixes, or nothing at all. */
    @GameTest(template = EMPTY)
    public static void aTypedNumberIsWorkedOutBeforeItIsSet(GameTestHelper helper) {
        helper.assertValueEqual(AmountExpression.parse("400k", TransferKind.ENERGY).getAsLong(), 400_000L, "k is a thousand");
        helper.assertValueEqual(AmountExpression.parse("1.5M", TransferKind.ENERGY).getAsLong(), 1_500_000L, "M a million, whatever the case");
        helper.assertValueEqual(AmountExpression.parse("2B", TransferKind.ENERGY).getAsLong(), 2_000_000_000L, "B a billion FE");
        helper.assertValueEqual(AmountExpression.parse("2B", TransferKind.FLUID).getAsLong(), 2_000L, "and two buckets of fluid");
        helper.assertValueEqual(AmountExpression.parse("64*3", TransferKind.ITEM).getAsLong(), 192L, "a product");
        helper.assertValueEqual(AmountExpression.parse("(2k + 500) / 2", null).getAsLong(), 1_250L, "brackets, spaces and a quotient");
        helper.assertValueEqual(AmountExpression.parse("-3", null).getAsLong(), -3L, "a sign, for priority");
        helper.assertValueEqual(AmountExpression.parse("500 mB", TransferKind.FLUID).getAsLong(), 500L, "the unit is ignored");
        helper.assertValueEqual(AmountExpression.parse("20 FE", TransferKind.ENERGY).getAsLong(), 20L, "so is FE");
        helper.assertValueEqual(AmountExpression.parse("2s", null).getAsLong(), 40L, "seconds are ticks");
        helper.assertTrue(AmountExpression.parse("abc", null).isEmpty(), "words are not a number");
        helper.assertTrue(AmountExpression.parse("1/0", null).isEmpty(), "nor is dividing by zero");
        helper.assertTrue(AmountExpression.parse("(2", null).isEmpty(), "nor a bracket left open");
        helper.assertTrue(AmountExpression.parse("", null).isEmpty(), "nor nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theRoundRobinCursorWalksTheQueue(GameTestHelper helper) {
        PortChannel port = detachedPort().link(0, TransferKind.ITEM);

        helper.assertValueEqual(port.takeRoundRobinCursor(3), 0, "the first pass starts at the front");
        helper.assertValueEqual(port.takeRoundRobinCursor(3), 1, "the second one place along");
        helper.assertValueEqual(port.takeRoundRobinCursor(3), 2, "the third one further");
        helper.assertValueEqual(port.takeRoundRobinCursor(3), 0, "and then round, not off the end");
        helper.assertValueEqual(port.takeRoundRobinCursor(2), 1,
                "a shorter queue is wrapped into rather than indexed past");

        helper.succeed();
    }

    // ------------------------------------------------------------------ a player without a client

    // ------------------------------------------------------------------ redstone over the network

    /** A redstone block behind one pad lights a lamp behind another, and nothing paid for it. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aSignalCrossesTheNetworkAndNoInjectorIsAsked(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.REDSTONE_BLOCK);
        pad(helper, SOURCE_PORT, Direction.NORTH);
        // A stone block behind the receiving pad, and a lamp on the far side of the stone: the pad
        // has to power the block it is on the way a lever would, or the lamp stays dark.
        helper.setBlock(FAR, Blocks.STONE);
        pad(helper, FAR_PORT, Direction.NORTH);
        BlockPos lamp = FAR.north();
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, FAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.REDSTONE, true);
        face(helper, SOURCE_PORT).link(ITEMS, TransferKind.REDSTONE).toggleExtract();
        face(helper, FAR_PORT).link(ITEMS, TransferKind.REDSTONE).toggleInsert();

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertValueEqual(face(helper, FAR_PORT).signalOut(), 15,
                            "the receiving pad carries the redstone block's fifteen");
                    helper.assertValueEqual(helper.getLevel().getDirectSignalTo(helper.absolutePos(FAR)), 15,
                            "and powers the stone it is on, strongly");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
                    helper.assertTrue(!port(helper, FAR_PORT).isPowered(),
                            "without reading its own signal back off the stone");
                    helper.assertValueEqual(manager.injectorCount(network), 0, "and no injector was needed");
                    helper.setBlock(SOURCE, Blocks.STONE);
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertValueEqual(face(helper, FAR_PORT).signalOut(), 0, "stone behind the sender: the level drops");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                })
                .thenSucceed();
    }

    /**
     * What a lamp in the pad's place would see: a lever beside the sending pad's block, not on the
     * block behind it, is read; and a lamp beside the receiving pad's block lights, as one beside
     * a lever would. Symo's first try was a lever next to the pad, and it read nothing.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aLeverBesideThePadIsReadAndALampBesideTheOtherLights(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.STONE);
        pad(helper, SOURCE_PORT, Direction.NORTH);
        BlockPos lever = SOURCE_PORT.south();
        helper.setBlock(lever.south(), Blocks.STONE);
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.NORTH));
        helper.setBlock(FAR, Blocks.STONE);
        pad(helper, FAR_PORT, Direction.NORTH);
        BlockPos lamp = FAR_PORT.south();
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, FAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.REDSTONE, true);
        face(helper, SOURCE_PORT).link(ITEMS, TransferKind.REDSTONE).toggleExtract();
        face(helper, FAR_PORT).link(ITEMS, TransferKind.REDSTONE).toggleInsert();

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertValueEqual(face(helper, FAR_PORT).signalOut(), 0, "the lever is off: nothing yet");
                    helper.pullLever(lever);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertValueEqual(face(helper, FAR_PORT).signalOut(), 15,
                            "a lever beside the sending pad's block is read, as a lamp there would read it");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
                    helper.assertTrue(!port(helper, FAR_PORT).isPowered(), "and the receiving block does not read its own output");
                    helper.pullLever(lever);
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertValueEqual(face(helper, FAR_PORT).signalOut(), 0, "off again");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                })
                .thenSucceed();
    }

    /**
     * Pads with no label keep to themselves: a change by hand on one reaches no other pad, a pad
     * joining with no label is set up by nobody, and the network never stores anything under
     * "no label". Labelled pads on the same network replicate among themselves and nowhere else.
     */
    @GameTest(template = PLATFORM)
    public static void padsWithoutALabelKeepToThemselves(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        barrel(helper, FAR);
        linkAll(helper, SOURCE_PORT, NEAR_PORT);
        carry(helper, ITEMS, TransferKind.ITEM);
        LinkNetworkManager manager = manager(helper);
        UUID network = face(helper, SOURCE_PORT).networkId();
        ServerPlayer player = testPlayer(helper);

        // One labelled pad on the network, so a label exists to be wrongly shared.
        rightClick(helper, player, SOURCE_PORT, false);
        LinkPortMenu labelled = padWindow(helper, player);
        labelled.clickMenuButton(player, LinkPortMenu.BUTTON_EXTRACT);
        labelled.setLabel("feed", true);

        // An unlabelled pad is set up by hand: nothing else moves.
        rightClick(helper, player, NEAR_PORT, false);
        LinkPortMenu plain = padWindow(helper, player);
        plain.clickMenuButton(player, LinkPortMenu.BUTTON_INSERT);
        plain.setNumber(LinkPortMenu.FIELD_KEEP, 5);
        helper.assertValueEqual(face(helper, NEAR_PORT).label(), "", "the pad set up by hand has no label");
        helper.assertTrue(!items(helper, NEAR_PORT).extractEnabled(), "and did not take the labelled pad's settings");
        helper.assertTrue(!items(helper, SOURCE_PORT).insertEnabled() && items(helper, SOURCE_PORT).keepAmount() == 0,
                "the labelled pad did not take the unlabelled one's");
        helper.assertTrue(manager.labels(network).equals(List.of("feed")), "and nothing is stored under no label");

        // Enter on an empty label box, and a second unlabelled pad joining: still nothing moves.
        plain.setLabel("", true);
        plain.setLabel("", false);
        manager.join(face(helper, FAR_PORT), network);
        helper.assertTrue(items(helper, FAR_PORT).isBlank(), "a pad joining with no label is set up by nobody");
        helper.assertTrue(items(helper, NEAR_PORT).insertEnabled() && items(helper, NEAR_PORT).keepAmount() == 5,
                "and the unlabelled pad still has exactly what was set on it");
        helper.assertTrue(manager.labels(network).equals(List.of("feed")), "and the network still knows one label");

        helper.succeed();
    }

    /**
     * The tick the lever moves, the far pad's level moves: the neighbour change wakes the network,
     * the pass runs at the end of that same tick, and dust beside the receiving pad's block reads
     * the new level on the tick after, both on and off. A lamp still takes its own four ticks to go
     * dark, as one wired straight to a lever does; that delay is the lamp's, not the network's.
     */
    @GameTest(template = PLATFORM)
    public static void aSignalChangesOnTheTickItIsMade(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.STONE);
        pad(helper, SOURCE_PORT, Direction.NORTH);
        BlockPos lever = SOURCE_PORT.south();
        helper.setBlock(lever.south(), Blocks.STONE);
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.NORTH));
        helper.setBlock(FAR, Blocks.STONE);
        pad(helper, FAR_PORT, Direction.NORTH);
        BlockPos dust = FAR_PORT.west();
        // The set-block layer is the floor itself: the dust needs something under it, and the floor
        // block between the dust and the sender's block must not conduct, or the dust powers it and
        // it powers the sender, which is vanilla redstone latching the rig, not the network.
        helper.setBlock(dust.below(), Blocks.STONE);
        helper.setBlock(dust.west(), Blocks.GLASS);
        helper.setBlock(dust, Blocks.REDSTONE_WIRE);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, FAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.REDSTONE, true);
        face(helper, SOURCE_PORT).link(ITEMS, TransferKind.REDSTONE).toggleExtract();
        face(helper, FAR_PORT).link(ITEMS, TransferKind.REDSTONE).toggleInsert();

        helper.startSequence()
                .thenExecuteAfter(5, () -> helper.pullLever(lever))
                .thenExecuteAfter(1, () -> {
                    helper.assertValueEqual(face(helper, FAR_PORT).signalOut(), 15, "the level is across on the tick after the lever");
                    helper.assertBlockProperty(dust, RedStoneWireBlock.POWER, 15);
                    helper.pullLever(lever);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertValueEqual(face(helper, FAR_PORT).signalOut(), 0, "and gone on the tick after it goes");
                    helper.assertBlockProperty(dust, RedStoneWireBlock.POWER, 0);
                })
                .thenSucceed();
    }

    /**
     * A tight rig: the sending pad's block touches the lamp the receiving pad lights. The lamp,
     * strongly powered by the receiver, must not be what the sender reads, or a button's pulse
     * would hold the channel high forever. Pads never see pads.
     */
    @GameTest(template = PLATFORM)
    public static void aButtonPulseNextToTheLitLampDoesNotLatchTheChannel(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.STONE);
        pad(helper, SOURCE_PORT, Direction.NORTH);
        BlockPos button = SOURCE.west();
        helper.setBlock(button, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL)
                .setValue(ButtonBlock.FACING, Direction.WEST));
        BlockPos lamp = SOURCE_PORT.south();
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        BlockPos receiver = lamp.south();
        pad(helper, receiver, Direction.NORTH);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, receiver), network);
        manager.setChannelKind(network, ITEMS, TransferKind.REDSTONE, true);
        face(helper, SOURCE_PORT).link(ITEMS, TransferKind.REDSTONE).toggleExtract();
        face(helper, receiver).link(ITEMS, TransferKind.REDSTONE).toggleInsert();

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                    helper.pressButton(button);
                })
                .thenExecuteAfter(5, () -> helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true))
                .thenWaitUntil(() -> {
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                    helper.assertValueEqual(face(helper, receiver).signalOut(), 0, "the pulse is over and the channel is down with it");
                })
                .thenSucceed();
    }

    /** The comparator and stock sources read the barrel behind the pad, the stock one against Full. */
    @GameTest(template = PLATFORM)
    public static void theComparatorAndStockSourcesReadTheBarrelBehindThePad(GameTestHelper helper) {
        Container barrel = barrel(helper, SOURCE);
        helper.setBlock(FAR, Blocks.STONE);
        pad(helper, FAR_PORT, Direction.NORTH);
        LinkNetworkManager manager = manager(helper);
        UUID network = manager.join(face(helper, SOURCE_PORT), null);
        manager.join(face(helper, FAR_PORT), network);
        manager.setChannelKind(network, ITEMS, TransferKind.REDSTONE, true);
        PortChannel sender = face(helper, SOURCE_PORT).link(ITEMS, TransferKind.REDSTONE);
        PortFace receiver = face(helper, FAR_PORT);
        receiver.link(ITEMS, TransferKind.REDSTONE).toggleInsert();
        sender.toggleExtract();
        sender.setSignalSource(SignalSource.STOCK);
        sender.setKeep(64);
        long time = helper.getLevel().getGameTime();

        manager.passNow(network, time);
        helper.assertValueEqual(receiver.signalOut(), 0, "an empty barrel is level zero");
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        manager.passNow(network, ++time);
        helper.assertValueEqual(receiver.signalOut(), 8, "thirty-two of sixty-four is eight, a comparator's arithmetic");
        barrel.setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        manager.passNow(network, ++time);
        helper.assertValueEqual(receiver.signalOut(), 15, "and sixty-four is full");

        ItemStack filter = ModItems.FILTER.toStack();
        FilterItem.setContents(filter, FilterContents.EMPTY.add(FilterContents.Entry.ofItem(new ItemStack(Items.DIRT))));
        face(helper, SOURCE_PORT).filterHandler().setStackInSlot(PortFace.filterSlot(ITEMS, TransferKind.REDSTONE), filter);
        manager.passNow(network, ++time);
        helper.assertValueEqual(receiver.signalOut(), 0, "a stock filter for dirt counts no cobblestone");

        sender.setSignalSource(SignalSource.COMPARATOR);
        manager.passNow(network, ++time);
        helper.assertValueEqual(receiver.signalOut(), 1, "the comparator reading of a barrel with a stack in it is one");
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            barrel.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        manager.passNow(network, ++time);
        helper.assertValueEqual(receiver.signalOut(), 15, "and of a full one fifteen");
        helper.succeed();
    }

    /** The redstone tab through the window: a source where PU would be, Full as its one number, a filter slot of its own. */
    @GameTest(template = PLATFORM)
    public static void theRedstoneTabHasASourceOneNumberAndAFilterSlot(GameTestHelper helper) {
        barrel(helper, SOURCE);
        ServerPlayer player = testPlayer(helper);
        rightClick(helper, player, SOURCE_PORT, false);
        LinkPortMenu menu = padWindow(helper, player);
        PortChannel link = face(helper, SOURCE_PORT).link(0, TransferKind.REDSTONE);

        menu.clickMenuButton(player, LinkPortMenu.BUTTON_TAB_BASE + TransferKind.REDSTONE.ordinal());
        helper.assertValueEqual(menu.activeKind(), TransferKind.REDSTONE, "the fourth tab opens redstone");
        helper.assertTrue(menu.slots.get(PortFace.filterSlot(0, TransferKind.REDSTONE)).isActive(),
                "with a filter slot of its own, for the stock source");
        helper.assertTrue(!menu.slots.get(PortFace.filterSlot(0, TransferKind.ITEM)).isActive(),
                "and the item tab's put away");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_CARRY);
        helper.assertTrue(face(helper, SOURCE_PORT).carriesLocally(0, TransferKind.REDSTONE),
                "ON: white carries redstone, on the pad until it joins something");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_SOURCE_NEXT);
        helper.assertValueEqual(link.signalSource(), SignalSource.COMPARATOR, "left on the source: the comparator reading");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_SOURCE_NEXT);
        helper.assertValueEqual(link.signalSource(), SignalSource.STOCK, "then stock");
        menu.clickMenuButton(player, LinkPortMenu.BUTTON_SOURCE_PREV);
        helper.assertValueEqual(link.signalSource(), SignalSource.COMPARATOR, "right: back one");
        helper.assertTrue(!menu.clickMenuButton(player, LinkPortMenu.BUTTON_PUSH) && !link.pushEnabled(), "a level has no door");
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_KEEP, 1, true));
        helper.assertValueEqual(link.keepAmount(), 8, "Full steps by one item: eight on the middle step");
        menu.setNumber(LinkPortMenu.FIELD_AMOUNT, 5);
        menu.clickMenuButton(player, LinkPortMenu.nudgeButton(LinkPortMenu.FIELD_DELAY, 0, true));
        helper.assertTrue(link.amountIsAuto() && link.delayIsAuto(), "and the other numbers are not there to set");
        helper.succeed();
    }

    // ------------------------------------------------------------------ filters that stand for more

    /** A tag entry stands for everything in the tag, a mod entry for everything from the mod; the picture is only a picture. */
    @GameTest(template = EMPTY)
    public static void aTagOrModEntryPassesEverythingItStandsFor(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        FilterContents byTag = FilterContents.EMPTY.add(FilterContents.Entry.ofItem(iron).withTag(Tags.Items.INGOTS.location())
                .withDirections(true, false));
        helper.assertTrue(byTag.allowsItem(new ItemStack(Items.GOLD_INGOT), true), "#c:ingots passes a gold ingot");
        helper.assertTrue(!byTag.allowsItem(new ItemStack(Items.COBBLESTONE), true), "and stops cobblestone");
        helper.assertTrue(byTag.allowsItem(new ItemStack(Items.COBBLESTONE), false),
                "on the receiving list only: the sending list is still blank");

        FilterContents byMod = FilterContents.EMPTY.add(FilterContents.Entry.ofItem(iron).withMod("minecraft"));
        helper.assertTrue(byMod.allowsItem(new ItemStack(Items.COBBLESTONE), true), "a minecraft entry passes cobblestone");
        helper.assertTrue(!byMod.allowsItem(ModItems.MACHINE_FRAME.toStack(), true), "and stops a Machine Frame");

        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        FilterContents fluids = FilterContents.EMPTY.add(FilterContents.Entry.ofFluid(water).withTag(Tags.Fluids.WATER.location()));
        helper.assertTrue(fluids.allowsFluid(water, true), "a fluid tag entry reads the fluid's tags");
        helper.assertTrue(!fluids.allowsFluid(new FluidStack(Fluids.LAVA, 1000), true), "and stops lava");
        helper.assertTrue(fluids.allowsItem(new ItemStack(Items.WATER_BUCKET), true),
                "while the item list, with nothing on it, still passes the bucket");

        FilterContents.Entry named = FilterContents.Entry.ofItem(iron).withMatchComponents(true).withTag(Tags.Items.INGOTS.location());
        helper.assertTrue(!named.matchComponents(), "standing for a tag drops the NBT flag");
        helper.assertTrue(named.withMatchComponents(true).equals(named), "and it cannot be put back while it does");
        helper.assertValueEqual(named.itself(), FilterContents.Entry.ofItem(iron), "itself again is the plain ingot");

        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        FilterContents both = byTag.add(FilterContents.Entry.ofItem(iron).withMod("minecraft"));
        FilterContents back = FilterContents.CODEC.encodeStart(ops, both)
                .flatMap(tag -> FilterContents.CODEC.parse(ops, tag))
                .getOrThrow();
        helper.assertValueEqual(back, both, "a filter with a tag and a mod entry survives being written and read");
        helper.succeed();
    }

    // ------------------------------------------------------------------ labels, the card and the clipboard

    /**
     * Same label, same network, same settings. A change made by hand on one labelled pad reaches
     * every other pad wearing the label, a pad that takes a label the network knows takes its
     * settings, and a labelled pad joining the network takes them too. Filters come out of the
     * player's inventory, and a pad that could not be given one is counted for the window's banner.
     */
    @GameTest(template = PLATFORM)
    public static void aLabelIsOneSetOfSettingsForEveryPadWearingIt(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        barrel(helper, FAR);
        linkAll(helper, SOURCE_PORT, NEAR_PORT);
        carry(helper, ITEMS, TransferKind.ITEM);
        LinkNetworkManager manager = manager(helper);
        UUID network = face(helper, SOURCE_PORT).networkId();
        ServerPlayer player = testPlayer(helper);

        // The first pad is set up by hand, then labelled through its window.
        rightClick(helper, player, SOURCE_PORT, false);
        LinkPortMenu first = padWindow(helper, player);
        first.clickMenuButton(player, LinkPortMenu.BUTTON_EXTRACT);
        first.setNumber(LinkPortMenu.FIELD_KEEP, 7);
        first.setLabel("feed", true);
        helper.assertValueEqual(face(helper, SOURCE_PORT).label(), "feed", "the label is on the pad");
        helper.assertTrue(manager.labels(network).equals(List.of("feed")), "and the network knows it");

        // The second takes the label, and with it the first's settings.
        rightClick(helper, player, NEAR_PORT, false);
        LinkPortMenu second = padWindow(helper, player);
        second.setLabel("feed", false);
        helper.assertTrue(items(helper, NEAR_PORT).extractEnabled() && items(helper, NEAR_PORT).keepAmount() == 7,
                "taking a label takes its settings");

        // From here a change by hand on either reaches the other.
        second.setNumber(LinkPortMenu.FIELD_KEEP, 12);
        helper.assertValueEqual(items(helper, SOURCE_PORT).keepAmount(), 12, "a number typed on one pad is on the other");
        first.clickMenuButton(player, LinkPortMenu.BUTTON_INSERT);
        helper.assertTrue(items(helper, NEAR_PORT).insertEnabled(), "a switch flipped on one is flipped on the other");

        // A third pad, labelled while on no network, is set up like the label when it joins.
        face(helper, FAR_PORT).setLabel("feed");
        manager.join(face(helper, FAR_PORT), network);
        helper.assertTrue(items(helper, FAR_PORT).extractEnabled() && items(helper, FAR_PORT).keepAmount() == 12,
                "a labelled pad joining the network takes the label's settings");

        // A filter dropped into the first pad's slot: the second gets a copy on a blank from the
        // player's inventory, the third goes without and is counted.
        ItemStack filter = ModItems.FILTER.toStack();
        FilterItem.setContents(filter, FilterContents.EMPTY.add(FilterContents.Entry.ofItem(new ItemStack(Items.COBBLESTONE))));
        player.getInventory().setItem(0, ModItems.FILTER.toStack());
        first.setCarried(filter);
        first.clicked(PortFace.filterSlot(ITEMS, TransferKind.ITEM), 0, ClickType.PICKUP, player);
        helper.assertTrue(first.getCarried().isEmpty(), "the filter went into the slot");
        ItemStack nearFilter = face(helper, NEAR_PORT).filter(ITEMS, TransferKind.ITEM);
        ItemStack farFilter = face(helper, FAR_PORT).filter(ITEMS, TransferKind.ITEM);
        ItemStack given = nearFilter.isEmpty() ? farFilter : nearFilter;
        helper.assertTrue(FilterItem.contents(given).equals(FilterItem.contents(face(helper, SOURCE_PORT).filter(ITEMS, TransferKind.ITEM))),
                "one of the other two pads got a copy of the filter");
        helper.assertTrue(nearFilter.isEmpty() != farFilter.isEmpty(), "the other did not: there was one blank");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "which came out of the player's inventory");
        helper.assertValueEqual(first.missingFilters(), 1, "and the pad that went without is counted for the banner");

        // Off the label, a pad hears nothing more.
        second.setLabel("", true);
        first.setNumber(LinkPortMenu.FIELD_KEEP, 3);
        helper.assertValueEqual(items(helper, NEAR_PORT).keepAmount(), 12, "a pad that took its label off keeps its own settings");
        helper.assertValueEqual(items(helper, FAR_PORT).keepAmount(), 3, "the one still wearing it follows");
        helper.succeed();
    }

    /**
     * The config card copies a whole pad (crouch-use) and applies it to a pad, or to that pad and
     * every port block reachable from it face to face with a pad on the same side: never a pad on
     * another face, never across a gap. Used at nothing, it is blank again.
     */
    @GameTest(template = PLATFORM)
    public static void theCardCopiesAPadAndFloodsTheSameFaceAcrossTouchingPortBlocks(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        barrel(helper, FAR);
        pad(helper, NEAR_PORT, Direction.UP);
        linkAll(helper, SOURCE_PORT);
        carry(helper, ITEMS, TransferKind.ITEM);
        UUID network = face(helper, SOURCE_PORT).networkId();
        items(helper, SOURCE_PORT).setRole(false, true);
        items(helper, SOURCE_PORT).setKeep(5);
        face(helper, SOURCE_PORT).setLabel("row");
        ItemStack setFilter = ModItems.FILTER.toStack();
        FilterItem.setContents(setFilter, FilterContents.EMPTY.add(FilterContents.Entry.ofItem(new ItemStack(Items.COBBLESTONE))));
        face(helper, SOURCE_PORT).filterHandler().setStackInSlot(PortFace.filterSlot(ITEMS, TransferKind.ITEM), setFilter);

        ServerPlayer player = testPlayer(helper);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        ItemStack card = ModItems.CONFIG_CARD.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        rightClick(helper, player, SOURCE_PORT, true);
        PadSnapshot copied = card.get(ModDataComponents.PAD_CONFIG.get());
        helper.assertTrue(copied != null && copied.label().equals("row") && copied.linkCount() == 1
                && copied.network().equals(Optional.of(network)), "crouch-use copies the pad, label and network included");

        // Slot 9, not 0: the hotbar's first slot is the hand the card is in.
        player.getInventory().setItem(9, ModItems.FILTER.toStack());
        ConfigCardItem.applyPad(player, helper.absolutePos(SOURCE_PORT), Direction.NORTH, true);
        PortChannel near = items(helper, NEAR_PORT);
        helper.assertTrue(near.extractEnabled() && near.keepAmount() == 5, "the touching pad on the same face is set up like the source");
        helper.assertTrue(network.equals(face(helper, NEAR_PORT).networkId()), "and put on its network");
        helper.assertValueEqual(face(helper, NEAR_PORT).label(), "row", "with its label");
        helper.assertTrue(FilterItem.contents(face(helper, NEAR_PORT).filter(ITEMS, TransferKind.ITEM)).equals(FilterItem.contents(setFilter)),
                "its filter is a copy, on a blank out of the player's inventory");
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "which is gone from there");
        PortFace up = port(helper, NEAR_PORT).face(Direction.UP);
        helper.assertTrue(up != null && !up.link(ITEMS, TransferKind.ITEM).extractEnabled() && up.label().isEmpty(),
                "a pad on another face of the same block is left alone");
        helper.assertTrue(!items(helper, FAR_PORT).extractEnabled() && !face(helper, FAR_PORT).isLinked(),
                "and the flood does not cross a gap");

        ConfigCardItem.applyPad(player, helper.absolutePos(FAR_PORT), Direction.NORTH, false);
        helper.assertTrue(items(helper, FAR_PORT).extractEnabled() && items(helper, FAR_PORT).keepAmount() == 5, "this pad only: applied");
        helper.assertTrue(face(helper, FAR_PORT).filter(ITEMS, TransferKind.ITEM).isEmpty(),
                "with no blank filter left, its filter slot stays empty");

        // Used at nothing. The test box is walled in barriers on every side within reach, so the
        // reach is shortened to the air round the player's head instead of looking for a gap.
        player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(0.5);
        ModItems.CONFIG_CARD.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!card.has(ModDataComponents.PAD_CONFIG.get()), "used in the air, the card is blank again");
        helper.succeed();
    }

    /** Export is the pad as text; Import reads it back with every number clamped, and refuses anything that is not a pad. */
    @GameTest(template = PLATFORM)
    public static void theClipboardTextIsThePadAndComesBackClamped(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, NEAR);
        linkAll(helper, SOURCE_PORT);
        carry(helper, ITEMS, TransferKind.ITEM);
        UUID network = face(helper, SOURCE_PORT).networkId();
        items(helper, SOURCE_PORT).setRole(true, false);
        items(helper, SOURCE_PORT).setKeep(9);
        items(helper, SOURCE_PORT).setPriority(4);
        String text = PadSnapshot.of(face(helper, SOURCE_PORT)).toText();
        helper.assertTrue(PadSnapshot.parse(text).isPresent(), "the text reads back as a pad");

        ServerPlayer player = testPlayer(helper);
        rightClick(helper, player, NEAR_PORT, false);
        LinkPortMenu window = padWindow(helper, player);
        window.importText(text);
        PortChannel near = items(helper, NEAR_PORT);
        helper.assertTrue(near.insertEnabled() && near.keepAmount() == 9 && near.priority() == 4, "imported, switches and numbers");
        helper.assertTrue(network.equals(face(helper, NEAR_PORT).networkId()), "and on the network, which the player may use");

        String wild = text.replace("Priority:4", "Priority:9999");
        helper.assertTrue(!wild.equals(text), "the number is in the text as written");
        window.importText(wild);
        helper.assertValueEqual(items(helper, NEAR_PORT).priority(), PortChannel.MAX_PRIORITY,
                "a number off the clipboard is clamped as the arrows clamp it");
        window.importText("this is not a pad");
        helper.assertValueEqual(items(helper, NEAR_PORT).priority(), PortChannel.MAX_PRIORITY, "text that is not a pad changes nothing");
        helper.assertTrue(PadSnapshot.parse("{Label:\"x\"}").isEmpty(), "a compound without links is not a pad either");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the overview

    /**
     * The overview lists every pad with what it does, every injector with what it holds and every
     * channel that carries something; the picker's eye opens it without putting the pad on the
     * network, and Back is the picker again.
     */
    @GameTest(template = PLATFORM)
    public static void theOverviewListsEveryPadInjectorAndChannelAndOpensFromThePickersEye(GameTestHelper helper) {
        barrel(helper, SOURCE);
        barrel(helper, FAR);
        linkAll(helper, SOURCE_PORT, FAR_PORT);
        carry(helper, ITEMS, TransferKind.ITEM);
        items(helper, SOURCE_PORT).setRole(false, true);
        items(helper, FAR_PORT).setRole(true, false);
        face(helper, SOURCE_PORT).setLabel("in");
        LinkNetworkManager manager = manager(helper);
        UUID network = face(helper, SOURCE_PORT).networkId();

        NetworkOverview overview = manager.overview(network);
        helper.assertTrue(overview != null, "a network has an overview");
        helper.assertValueEqual(overview.pads().size(), 2, "both pads are on it");
        NetworkOverview.Pad source = overview.pads().get(0);
        helper.assertTrue(source.at().pos().equals(helper.absolutePos(SOURCE_PORT)) && source.face() == Direction.NORTH,
                "a pad is its position and its face");
        helper.assertValueEqual(source.label(), "in", "and its label");
        helper.assertTrue(source.loaded() && source.inReach() && source.range() > 0, "loaded, in reach, with a reach");
        helper.assertValueEqual(source.links().size(), 1, "one link switched on");
        NetworkOverview.Link link = source.links().get(0);
        helper.assertTrue(link.channel() == ITEMS && link.kind() == TransferKind.ITEM && link.send() && !link.receive() && !link.push(),
                "white items, sending");
        helper.assertTrue(overview.pads().get(1).links().get(0).receive(), "and the far one receives");
        helper.assertValueEqual(overview.injectors().size(), 1, "the injector is listed");
        NetworkOverview.Injector injector = overview.injectors().get(0);
        helper.assertTrue(injector.loaded() && injector.stored() == injector.capacity() && injector.capacity() > 0,
                "with what it holds, which is everything");
        helper.assertValueEqual(overview.channels().size(), 1, "one channel carries something");
        NetworkOverview.Channel white = overview.channels().get(0);
        helper.assertTrue(white.channel() == ITEMS && white.carries(TransferKind.ITEM) && !white.carries(TransferKind.ENERGY),
                "white carries items and nothing else");
        helper.assertTrue(white.senders() == 1 && white.receivers() == 1, "one sender, one receiver");
        helper.assertValueEqual(white.spread(TransferKind.ITEM), Distribution.NEAREST_FIRST, "spread as set");
        helper.assertTrue(manager.overview(UUID.randomUUID()) == null, "and a network that is not there has none");

        // Through the picker: the tool on a third, unlinked pad, the eye on our network's row.
        barrel(helper, NEAR);
        ServerPlayer player = testPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LINKING_TOOL.toStack());
        rightClick(helper, player, NEAR_PORT, false);
        NetworkPickerMenu picker = picker(helper, player);
        int index = -1;
        for (int at = 0; at < picker.choices().size(); at++) {
            if (picker.choices().get(at).id().equals(network)) {
                index = at;
            }
        }
        helper.assertTrue(index >= 0, "the picker lists the network");
        helper.assertTrue(picker.clickMenuButton(player, NetworkPickerMenu.BUTTON_VIEW_BASE + index), "the eye is a button");
        helper.assertTrue(player.containerMenu instanceof NetworkOverviewMenu shown && shown.overview().id().equals(network),
                "that opens the overview of that network");
        helper.assertTrue(!face(helper, NEAR_PORT).isLinked(), "without putting the pad on it");
        player.containerMenu.clickMenuButton(player, NetworkOverviewMenu.BUTTON_BACK);
        helper.assertTrue(player.containerMenu instanceof NetworkPickerMenu, "and Back is the picker again");
        helper.succeed();
    }

    /**
     * A server player with no connection behind it, which is what a click needs and nothing more.
     *
     * <p>{@code makeMockServerPlayerInLevel} runs the whole join, and a mod's greeting payload
     * kills it. This one skips the network entirely: a window it is asked to open is created and
     * set as its container without a packet, and closing one is just the server-side half.
     */
    static ServerPlayer testPlayer(GameTestHelper helper) {
        return testPlayer(helper, "actualgenerators-test");
    }

    static ServerPlayer testPlayer(GameTestHelper helper, String name) {
        return testPlayer(helper, name, 0);
    }

    /** @param permissionLevel 2 or more makes an operator */
    static ServerPlayer testPlayer(GameTestHelper helper, String name, int permissionLevel) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault()) {
            private int windows;

            @Override
            protected int getPermissionLevel() {
                return permissionLevel;
            }

            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
            }

            @Override
            public OptionalInt openMenu(@Nullable MenuProvider provider) {
                return openMenu(provider, buffer -> {
                });
            }

            @Override
            public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData) {
                if (provider == null) {
                    return OptionalInt.empty();
                }
                if (containerMenu != inventoryMenu) {
                    closeContainer();
                }
                AbstractContainerMenu menu = provider.createMenu(++windows, getInventory(), this);
                if (menu == null) {
                    return OptionalInt.empty();
                }
                containerMenu = menu;
                return OptionalInt.of(menu.containerId);
            }

            @Override
            public void closeContainer() {
                doCloseContainer();
            }
        };
    }

    /** Right-clicks the top of a block with whatever is in the main hand, as the game would. */
    static void rightClick(GameTestHelper helper, ServerPlayer player, BlockPos pos, boolean crouching) {
        player.setShiftKeyDown(crouching);
        BlockPos worldPos = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(worldPos.getCenter(), Direction.UP, worldPos, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** Right-clicks the given face of a block with a Logic Port in hand, as a player would. */
    private static void placePad(GameTestHelper helper, ServerPlayer player, BlockPos against, Direction face) {
        ItemStack stack = ModItems.LOGIC_PORT.toStack();
        stack.setCount(8);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // Crouching, because a barrel or a machine opens its own window otherwise -- placing a
        // block against anything with a right-click action is a crouch in vanilla too.
        player.setShiftKeyDown(true);
        BlockPos worldPos = helper.absolutePos(against);
        BlockHitResult hit = new BlockHitResult(
                worldPos.getCenter().relative(face, 0.5), face, worldPos, false);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    /** The picker the last click opened. */
    private static NetworkPickerMenu picker(GameTestHelper helper, ServerPlayer player) {
        if (player.containerMenu instanceof NetworkPickerMenu picker) {
            return picker;
        }
        throw new IllegalStateException("the click did not open the network picker, but "
                + player.containerMenu.getClass().getSimpleName());
    }

    /** The pad window the last click opened. */
    private static LinkPortMenu padWindow(GameTestHelper helper, ServerPlayer player) {
        if (player.containerMenu instanceof LinkPortMenu menu) {
            return menu;
        }
        throw new IllegalStateException("the click did not open the pad's window, but "
                + player.containerMenu.getClass().getSimpleName());
    }

    /** Clicks the named network in the open picker. */
    private static void pick(GameTestHelper helper, ServerPlayer player, String name) {
        NetworkPickerMenu picker = picker(helper, player);
        List<LinkNetworkManager.Summary> choices = picker.choices();
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).name().equals(name)) {
                picker.clickMenuButton(player, NetworkPickerMenu.BUTTON_PICK_BASE + index);
                return;
            }
        }
        throw new IllegalStateException("the picker does not offer a network called " + name);
    }

    // ------------------------------------------------------------------ helpers

    private static LinkNetworkManager manager(GameTestHelper helper) {
        return LinkNetworkManager.get(helper.getLevel().getServer());
    }

    /** A pad with no level: enough for the rules that are about the pad and nothing else. */
    private static PortFace detachedPort() {
        BlockState state = ModBlocks.LOGIC_PORT.get().defaultBlockState();
        return new PortFace(new LinkPortBlockEntity(BlockPos.ZERO, state), Direction.NORTH);
    }

    /** Puts a barrel down with a pad on its south face, and hands back the barrel. */
    private static Container barrel(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.BARREL);
        pad(helper, pos.south(), Direction.NORTH);
        if (helper.getBlockEntity(pos) instanceof Container container) {
            return container;
        }
        throw new IllegalStateException("no barrel at " + pos);
    }

    /** Puts a pad on one face of a port block, making the block if it is not there yet. */
    private static void pad(GameTestHelper helper, BlockPos pos, Direction facing) {
        BlockState existing = helper.getBlockState(pos);
        BlockState state = existing.is(ModBlocks.LOGIC_PORT.get())
                ? existing
                : ModBlocks.LOGIC_PORT.get().defaultBlockState();
        helper.setBlock(pos, state.setValue(LinkPortBlock.FACE_PROPERTIES.get(facing), true));
        port(helper, pos).addFace(facing);
    }

    private static LinkPortBlockEntity port(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
            return port;
        }
        throw new IllegalStateException("no logic port at " + pos);
    }

    /** The pad on the north face, which is the one every rig here uses. */
    private static PortFace face(GameTestHelper helper, BlockPos pos) {
        PortFace face = port(helper, pos).face(Direction.NORTH);
        if (face == null) {
            throw new IllegalStateException("no pad on the north face at " + pos);
        }
        return face;
    }

    private static PortChannel items(GameTestHelper helper, BlockPos pos) {
        return face(helper, pos).link(ITEMS, TransferKind.ITEM);
    }

    /** Tells the network the source pad is on what one of its channels carries. */
    private static void carry(GameTestHelper helper, int channel, TransferKind kind) {
        manager(helper).setChannelKind(face(helper, SOURCE_PORT).networkId(), channel, kind, true);
    }

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            return machine;
        }
        throw new IllegalStateException("no machine at " + pos);
    }

    /**
     * Puts every named pad on one fresh network and gives it an injector, the way a player would.
     *
     * <p>The injector is not optional scenery: wireless transfer is paid for in FE, and a network
     * with nothing paying for it is supposed to move nothing at all.
     */
    private static void linkAll(GameTestHelper helper, BlockPos... positions) {
        LinkNetworkManager manager = manager(helper);
        UUID network = null;
        for (BlockPos pos : positions) {
            network = manager.join(face(helper, pos), network);
        }
        helper.setBlock(INJECTOR, ModBlocks.ENERGY_INJECTOR.get());
        EnergyInjectorBlockEntity injector = injector(helper);
        injector.energyStorage().setEnergy(injector.energyStorage().capacity());
        manager.joinInjector(injector, network);
    }

    private static EnergyInjectorBlockEntity injector(GameTestHelper helper) {
        if (helper.getBlockEntity(INJECTOR) instanceof EnergyInjectorBlockEntity injector) {
            return injector;
        }
        throw new IllegalStateException("no energy injector at " + INJECTOR);
    }

    private static void openEnergy(MachineBlockEntity machine) {
        for (RelativeSide side : RelativeSide.values()) {
            machine.sideConfig().set(TransferKind.ENERGY, side, IoMode.BOTH);
        }
        machine.invalidateCapabilitiesOnSideChange();
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        if (!(helper.getBlockEntity(pos) instanceof Container container)) {
            throw new IllegalStateException("no container at " + pos);
        }
        int found = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
