package dev.symo.actualgenerators.menu;

import net.neoforged.neoforge.network.PacketDistributor;
import dev.symo.actualgenerators.network.ClipboardPayload;
import dev.symo.actualgenerators.logistics.PadSnapshot;
import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.logistics.ChannelSettings;
import dev.symo.actualgenerators.logistics.Distribution;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.PortChannel;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * One pad's window: sixteen channels along the top, the chosen channel's four kinds as tabs,
 * and on the open tab everything this pad does with that kind on that channel.
 *
 * <p>Three levels, each in its own band. The palette picks a channel. The tabs under it pick a
 * kind: items, fluids, energy or redstone. The rest of the window is that one link
 * ({@link PortChannel}): whether the channel carries the kind at all, IN, EX and PU, spread,
 * redstone, the Filter item and the four numbers. None of it is shared with another kind, so what
 * a click changes is exactly what the open tab says. The redstone tab is the short one: no door,
 * no spread and one number, and where PU would be sits what a sender reads off its block.
 *
 * <p>A left click does the thing. A right click on an arrow is the bigger step, on the redstone
 * button the previous mode, on the network line it leaves. Every number can also be typed: a
 * click on the value opens it for the keyboard and a
 * {@link dev.symo.actualgenerators.network.SetPadNumberPayload} carries what was typed, which
 * {@link #setNumber} clamps the way the arrows would.
 *
 * <p>A pad can be set up before it is on any network. What a channel carries then lives on the
 * pad ({@link PortFace#localKinds}); the first network it joins takes those kinds for every
 * channel nobody has given a job yet, and a pad that leaves keeps a copy. Once linked, the
 * network's side (kinds, spread, who is on it) comes through the container data, because only
 * the server knows it; the pad's own side (roles, numbers, upgrades, filters, the network's name,
 * colour and centre) is read off the block entity, which the port syncs on every change.
 *
 * <p>Every box the screen draws is listed in {@link #chrome()}, so the layout test can see that no
 * two of them share a pixel.
 */
public class LinkPortMenu extends AbstractContainerMenu {
    public static final int DATA_CHANNEL = 0;
    /** The kind whose tab is open, as an ordinal. */
    public static final int DATA_KIND = 1;
    /** A nibble per channel, four channels per slot: one bit per kind the channel carries. */
    public static final int DATA_KINDS_BASE = 2;
    private static final int CHANNELS_PER_KIND_SLOT = 4;
    private static final int KIND_SLOTS = PortFace.CHANNELS / CHANNELS_PER_KIND_SLOT;
    /** Two bits per kind of the chosen channel: the spread's ordinal. */
    public static final int DATA_SPREAD = DATA_KINDS_BASE + KIND_SLOTS;
    public static final int DATA_PORTS = DATA_SPREAD + 1;
    public static final int DATA_INJECTORS = DATA_PORTS + 1;
    /** Whether the viewer may use the window at all: the network's owner, the invited, or anyone on a public one. */
    public static final int DATA_ACCESS = DATA_INJECTORS + 1;
    /** How many pads with this pad's label could not be given a filter or an upgrade the last time it was published. */
    public static final int DATA_MISSING_FILTERS = DATA_ACCESS + 1;
    public static final int DATA_MISSING_UPGRADES = DATA_MISSING_FILTERS + 1;
    public static final int DATA_SIZE = DATA_MISSING_UPGRADES + 1;

    /** Makes the channel carry the open tab's kind, for the whole network; again stops it. */
    public static final int BUTTON_CARRY = 0;
    public static final int BUTTON_INSERT = 1;
    public static final int BUTTON_EXTRACT = 2;
    public static final int BUTTON_PUSH = 3;
    public static final int BUTTON_SPREAD = 4;
    public static final int BUTTON_REDSTONE_NEXT = 5;
    public static final int BUTTON_REDSTONE_PREV = 6;
    public static final int BUTTON_NETWORK_PICK = 7;
    public static final int BUTTON_NETWORK_LEAVE = 8;
    /** The redstone tab's source, where the other tabs have PU: what a sender reads off its block. */
    public static final int BUTTON_SOURCE_NEXT = 9;
    public static final int BUTTON_SOURCE_PREV = 10;
    /** Export: the pad's settings, as text, onto the player's clipboard. Import comes up as a payload with the text. */
    public static final int BUTTON_EXPORT = 11;
    /** {@code BUTTON_TAB_BASE + kind.ordinal()} opens that kind's tab. */
    public static final int BUTTON_TAB_BASE = 12;
    /** {@code BUTTON_CHANNEL_BASE + n} picks channel n, straight off the palette. */
    public static final int BUTTON_CHANNEL_BASE = 16;
    /** See {@link #nudgeButton}: which number, how far, and which way. */
    public static final int BUTTON_NUDGE_BASE = 32;

    public static final int FIELD_KEEP = 0;
    public static final int FIELD_AMOUNT = 1;
    public static final int FIELD_PRIORITY = 2;
    public static final int FIELD_DELAY = 3;
    public static final int FIELD_COUNT = 4;
    /** A plain click, a shift-click (or right click), a ctrl-click. */
    public static final int[] STEPS = {1, 8, 64};

    public static final int WIDTH = MachineLayout.WIDTH;
    public static final int HEIGHT = 236;

    // ------------------------------------------------------------------ where things are

    /** The top strip: which pad this is, and its three upgrade slots. */
    /** The label box, where a title would be: type a label, or pick one the network knows. */
    public static final MachineLayout.Box LABEL_BOX = new MachineLayout.Box("label", 8, 4, 106, 12);
    /** A row of the label box's list, over the palette while it is open; not part of {@link #chrome()}, it is a popover. */
    public static MachineLayout.Box labelRow(int index) {
        return new MachineLayout.Box("label row " + index, 8, 18 + index * 11, 106, 11);
    }
    /** When a picked label is one the network knows: a caption naming it, then the two answers, take its settings or push this pad's onto it. */
    public static final MachineLayout.Box LABEL_CAPTION = new MachineLayout.Box("label caption", 8, 18, 106, 9);
    public static final MachineLayout.Box LABEL_PULL_BUTTON = new MachineLayout.Box("label pull", 8, 29, 106, 12);
    public static final MachineLayout.Box LABEL_PUSH_BUTTON = new MachineLayout.Box("label push", 8, 43, 106, 12);
    /** The bay on the right, outside the window proper, Mekanism's way: Export and Import, so the window stays clear. */
    public static final int BAY_WIDTH = 24;
    public static final MachineLayout.Box EXPORT_TAB = new MachineLayout.Box("export tab", WIDTH, 6, BAY_WIDTH, 22);
    public static final MachineLayout.Box IMPORT_TAB = new MachineLayout.Box("import tab", WIDTH, 30, BAY_WIDTH, 22);
    private static final int UPGRADE_SLOT_Y = 5;
    private static final int TIER_SLOT_X = 117;
    private static final int RANGE_SLOT_X = 135;
    private static final int CARD_SLOT_X = 153;

    /** The palette: two rows of eight channels. */
    public static final int PALETTE_X = 8;
    public static final int PALETTE_Y = 24;
    public static final int PALETTE_COLUMNS = 8;
    public static final int CELL_WIDTH = 20;
    public static final int CELL_HEIGHT = 14;

    /** The chosen channel: its colour, its name and the open tab's kind, in words. */
    public static final MachineLayout.Box SWATCH = new MachineLayout.Box("channel swatch", 10, 54, 8, 8);
    public static final MachineLayout.Box CHANNEL_NAME = new MachineLayout.Box("channel name", 22, 54, 146, 9);

    /** One tab per kind, side by side: four glyphs, since four names do not fit. */
    private static final int TAB_X = 8;
    private static final int TAB_Y = 66;
    private static final int TAB_WIDTH = 38;
    private static final int TAB_PITCH = 40;
    private static final int TAB_HEIGHT = 13;

    /** The open tab's switches in a row, with its Filter slot at the end. */
    private static final int ROW_Y = 82;
    private static final int ROW_HEIGHT = 12;
    public static final MachineLayout.Box CARRY_BUTTON = new MachineLayout.Box("carry button", 8, ROW_Y, 28, ROW_HEIGHT);
    public static final MachineLayout.Box INSERT_BUTTON = new MachineLayout.Box("receive button", 40, ROW_Y, 18, ROW_HEIGHT);
    public static final MachineLayout.Box EXTRACT_BUTTON = new MachineLayout.Box("send button", 60, ROW_Y, 18, ROW_HEIGHT);
    public static final MachineLayout.Box PUSH_BUTTON = new MachineLayout.Box("push button", 80, ROW_Y, 18, ROW_HEIGHT);
    /** In PU's place on the redstone tab, which has no door: what a sender reads. Not listed twice in {@link #chrome()}. */
    public static final MachineLayout.Box SOURCE_BUTTON = new MachineLayout.Box("source button", 80, ROW_Y, 18, ROW_HEIGHT);
    public static final MachineLayout.Box SPREAD_BUTTON = new MachineLayout.Box("spread button", 102, ROW_Y, 24, ROW_HEIGHT);
    public static final MachineLayout.Box REDSTONE_BUTTON = new MachineLayout.Box("redstone button", 130, ROW_Y, 14, ROW_HEIGHT);
    private static final int FILTER_SLOT_X = 148;
    private static final int FILTER_SLOT_Y = ROW_Y;

    /** The four numbers: two columns of caption, value and arrows, two rows. */
    private static final int FIELD_Y = 97;
    private static final int FIELD_ROW = 15;
    private static final int[] CAPTION_X = {8, 74};
    private static final int[] CAPTION_WIDTH = {26, 22};
    private static final int[] VALUE_X = {34, 96};
    private static final int[] VALUE_WIDTH = {26, 30};
    private static final int[] ARROW_X = {61, 127};

    /** The network line: a button that reads as text. */
    public static final MachineLayout.Box NETWORK_BUTTON = new MachineLayout.Box("network button", 8, 128, 160, 12);

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = HEIGHT - 82;
    private static final int HOTBAR_Y = HEIGHT - 24;

    /**
     * Slot indices: a Filter slot per channel and filterable kind, all in one place and one shown,
     * in the pad's own slot order so a menu index is the handler index; then the three upgrades.
     */
    private static final int SLOT_RANGE = PortFace.FILTER_SLOTS;
    private static final int SLOT_CARD = SLOT_RANGE + 1;
    private static final int SLOT_TIER = SLOT_CARD + 1;
    private static final int PORT_SLOTS = SLOT_TIER + 1;

    /** Where one channel's cell on the palette is. */
    public static MachineLayout.Box channelCell(int channel) {
        return new MachineLayout.Box("channel " + channel,
                PALETTE_X + (channel % PALETTE_COLUMNS) * CELL_WIDTH,
                PALETTE_Y + (channel / PALETTE_COLUMNS) * CELL_HEIGHT,
                CELL_WIDTH, CELL_HEIGHT);
    }

    /** The tab that opens one kind: a chest, a drop, a bolt, a torch. */
    public static MachineLayout.Box tabButton(TransferKind kind) {
        return new MachineLayout.Box("tab " + kind.name().toLowerCase(Locale.ROOT),
                TAB_X + kind.ordinal() * TAB_PITCH, TAB_Y, TAB_WIDTH, TAB_HEIGHT);
    }

    /** A field's caption, muted text that names the number. */
    public static MachineLayout.Box fieldCaption(int field) {
        int column = field % 2;
        return new MachineLayout.Box("field " + field + " caption",
                CAPTION_X[column], fieldY(field) + 3, CAPTION_WIDTH[column], 9);
    }

    /** A field's value: plain text, no box, right-aligned against its arrows. A click opens it for typing. */
    public static MachineLayout.Box fieldValue(int field) {
        int column = field % 2;
        return new MachineLayout.Box("field " + field + " value",
                VALUE_X[column], fieldY(field), VALUE_WIDTH[column], 13);
    }

    public static MachineLayout.Box fieldUp(int field) {
        return new MachineLayout.Box("field " + field + " up", ARROW_X[field % 2], fieldY(field), 7, 6);
    }

    public static MachineLayout.Box fieldDown(int field) {
        return new MachineLayout.Box("field " + field + " down", ARROW_X[field % 2], fieldY(field) + 7, 7, 6);
    }

    private static int fieldY(int field) {
        return FIELD_Y + (field / 2) * FIELD_ROW;
    }

    /** Everything the window draws that is not a slot, so the layout test can see all of it. */
    public static List<MachineLayout.Box> chrome() {
        List<MachineLayout.Box> boxes = new ArrayList<>();
        boxes.add(LABEL_BOX);
        boxes.add(EXPORT_TAB);
        boxes.add(IMPORT_TAB);
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            boxes.add(channelCell(channel));
        }
        boxes.add(SWATCH);
        boxes.add(CHANNEL_NAME);
        for (TransferKind kind : TransferKind.all()) {
            boxes.add(tabButton(kind));
        }
        boxes.add(CARRY_BUTTON);
        boxes.add(INSERT_BUTTON);
        boxes.add(EXTRACT_BUTTON);
        boxes.add(PUSH_BUTTON);
        boxes.add(SPREAD_BUTTON);
        boxes.add(REDSTONE_BUTTON);
        for (int field = 0; field < FIELD_COUNT; field++) {
            boxes.add(fieldCaption(field));
            boxes.add(fieldValue(field));
            boxes.add(fieldUp(field));
            boxes.add(fieldDown(field));
        }
        boxes.add(NETWORK_BUTTON);
        return boxes;
    }

    /** The button id that moves one of the four numbers by one of the three steps, either way. */
    public static int nudgeButton(int field, int stepIndex, boolean up) {
        return BUTTON_NUDGE_BASE + field * 8 + stepIndex * 2 + (up ? 1 : 0);
    }

    // ------------------------------------------------------------------ state

    private final LinkPortBlockEntity port;
    private final Direction facing;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final Player viewer;

    private int channel;
    private TransferKind tab = TransferKind.ITEM;
    /** The labels the pad's network knew when the window opened, for the label box's list. */
    private final List<String> labels;
    private int missingFilters;
    private int missingUpgrades;

    /** Server side: reads live values straight off the pad and its network. */
    public LinkPortMenu(int containerId, Inventory playerInventory, LinkPortBlockEntity port, Direction facing) {
        this(containerId, playerInventory, port, facing, null, labelsOf(port, facing));
    }

    /** Client side: values arrive through the container data sync. */
    public LinkPortMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, readPort(playerInventory, extraData.readBlockPos()),
                Direction.from3DDataValue(extraData.readByte()), new SimpleContainerData(DATA_SIZE),
                extraData.readList(FriendlyByteBuf::readUtf));
    }

    private LinkPortMenu(int containerId, Inventory playerInventory, LinkPortBlockEntity port,
                         Direction facing, @Nullable ContainerData synced, List<String> labels) {
        super(ModMenus.LOGIC_PORT.get(), containerId);
        this.port = port;
        this.facing = facing;
        this.labels = labels;
        this.data = synced != null ? synced : new PortData();
        this.access = ContainerLevelAccess.create(port.getLevel(), port.getBlockPos());
        this.viewer = playerInventory.player;

        // The link a player most likely came for: the first one this pad does anything on.
        PortFace face = face();
        if (face != null) {
            search:
            for (int index = 0; index < PortFace.CHANNELS; index++) {
                for (TransferKind kind : TransferKind.all()) {
                    if (face.link(index, kind).participates()) {
                        channel = index;
                        tab = kind;
                        break search;
                    }
                }
            }
        }

        IItemHandler filters = face != null ? face.filterHandler() : new ItemStackHandler(PortFace.FILTER_SLOTS);
        for (int index = 0; index < PortFace.CHANNELS; index++) {
            addSlot(new FilterSlot(filters, index, TransferKind.ITEM, FILTER_SLOT_X, FILTER_SLOT_Y));
            addSlot(new FilterSlot(filters, index, TransferKind.FLUID, FILTER_SLOT_X, FILTER_SLOT_Y));
            addSlot(new FilterSlot(filters, index, TransferKind.REDSTONE, FILTER_SLOT_X, FILTER_SLOT_Y));
        }
        IItemHandler upgrades = face != null ? face.upgradeHandler() : new ItemStackHandler(PortFace.UPGRADE_SLOTS);
        addSlot(new UpgradeSlot(upgrades, PortFace.SLOT_RANGE, RANGE_SLOT_X, UPGRADE_SLOT_Y));
        addSlot(new UpgradeSlot(upgrades, PortFace.SLOT_CARD, CARD_SLOT_X, UPGRADE_SLOT_Y));
        addSlot(new UpgradeSlot(upgrades, PortFace.SLOT_TIER, TIER_SLOT_X, UPGRADE_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    /** How a port block hands one of its pads to a player. */
    public record Opener(LinkPortBlockEntity port, Direction facing) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.translatable("block.actualgenerators.logic_port.face",
                    Component.translatable("gui.actualgenerators.facing." + facing.getName()));
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
            return new LinkPortMenu(containerId, inventory, port, facing);
        }
    }

    public static void writeOpener(FriendlyByteBuf buffer, LinkPortBlockEntity port, Direction facing) {
        buffer.writeBlockPos(port.getBlockPos());
        buffer.writeByte(facing.get3DDataValue());
        buffer.writeCollection(labelsOf(port, facing), FriendlyByteBuf::writeUtf);
    }

    /** The labels the pad's network knows, on the server; nothing off it or for an unlinked pad. */
    private static List<String> labelsOf(LinkPortBlockEntity port, Direction facing) {
        PortFace face = port.face(facing);
        if (face == null || !(port.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        return LinkNetworkManager.get(level.getServer()).labels(face.networkId());
    }

    /** The labels the network knew when the window opened, alphabetical. */
    public List<String> labels() {
        return labels;
    }

    public String label() {
        PortFace face = face();
        return face == null ? "" : face.label();
    }

    /** Pads wearing this pad's label that could not be given a filter the last time its settings went out. */
    public int missingFilters() {
        return data.get(DATA_MISSING_FILTERS);
    }

    public int missingUpgrades() {
        return data.get(DATA_MISSING_UPGRADES);
    }

    /**
     * The label typed or picked in the window. {@code push} says which way the settings go when
     * the network already knows the label; see {@link LinkNetworkManager#relabel}.
     */
    public void setLabel(String label, boolean push) {
        PortFace face = face();
        if (face == null || !mayUse(face, viewer)) {
            return;
        }
        Optional<LinkNetworkManager> manager = manager(face);
        if (manager.isPresent()) {
            remember(manager.get().relabel(face, label, push, viewer));
        } else {
            face.setLabel(label);
        }
    }

    /** The clipboard's text, applied to this pad: refused whole when it is not a pad's settings. */
    public void importText(String text) {
        PortFace face = face();
        if (face == null || !mayUse(face, viewer)) {
            return;
        }
        Optional<PadSnapshot> snapshot = PadSnapshot.parse(text);
        if (snapshot.isEmpty()) {
            viewer.displayClientMessage(Component.translatable("gui.actualgenerators.link.import.bad"), true);
            return;
        }
        snapshot.get().applyTo(face, viewer, true);
        viewer.displayClientMessage(Component.translatable("gui.actualgenerators.link.import.done"), true);
        afterChange();
    }

    /** A setting was changed by hand: a labelled pad's settings go out to every pad wearing the label. */
    private void afterChange() {
        PortFace face = face();
        if (face == null || viewer.level().isClientSide || face.label().isEmpty()) {
            return;
        }
        manager(face).ifPresent(manager -> remember(manager.publish(face, viewer)));
    }

    private void remember(PadSnapshot.Applied applied) {
        missingFilters = applied.missingFilters();
        missingUpgrades = applied.missingUpgrades();
    }

    private static LinkPortBlockEntity readPort(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
            return port;
        }
        throw new IllegalStateException("no logic port at " + pos);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * 18, HOTBAR_Y));
        }
    }

    // ------------------------------------------------------------------ slots

    /** The Filter item for one kind on one channel. All sit in the same place; only the open one shows. */
    public class FilterSlot extends SlotItemHandler {
        private final int channel;
        private final TransferKind kind;

        FilterSlot(IItemHandler handler, int channel, TransferKind kind, int x, int y) {
            super(handler, PortFace.filterSlot(channel, kind), x, y);
            this.channel = channel;
            this.kind = kind;
        }

        @Override
        public boolean isActive() {
            return channel == activeChannel() && kind == activeKind();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof FilterItem;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            afterChange();
        }
    }

    /** An upgrade slot, which the screen draws a hint sprite in so it says what it wants. */
    public class UpgradeSlot extends SlotItemHandler {
        UpgradeSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            afterChange();
        }
    }

    // ------------------------------------------------------------------ readouts

    /** The pad this window belongs to, or null once the block has gone. */
    public @Nullable PortFace face() {
        return port.face(facing);
    }

    /** What this pad does with the open tab's kind on the chosen channel, or null once the block has gone. */
    public @Nullable PortChannel link() {
        PortFace face = face();
        return face == null ? null : face.link(activeChannel(), activeKind());
    }

    public Direction facing() {
        return facing;
    }

    public int activeChannel() {
        return Math.floorMod(data.get(DATA_CHANNEL), PortFace.CHANNELS);
    }

    /** The kind whose tab is open. */
    public TransferKind activeKind() {
        return TransferKind.byOrdinal(data.get(DATA_KIND));
    }

    /**
     * What a channel carries, one bit per kind by ordinal: the network's word once the pad is
     * linked, the pad's own before that. Zero is nothing yet.
     */
    public int channelKinds(int channel) {
        int packed = data.get(DATA_KINDS_BASE + channel / CHANNELS_PER_KIND_SLOT);
        return (packed >> ((channel % CHANNELS_PER_KIND_SLOT) * 4)) & ChannelSettings.ALL_KINDS;
    }

    public boolean channelCarries(int channel, TransferKind kind) {
        return (channelKinds(channel) & ChannelSettings.bit(kind)) != 0;
    }

    public int activeKinds() {
        return channelKinds(activeChannel());
    }

    /** Whether the chosen channel carries the open tab's kind. */
    public boolean activeCarried() {
        return channelCarries(activeChannel(), activeKind());
    }

    /** How the chosen channel spreads one kind. */
    public Distribution channelDistribution(TransferKind kind) {
        return Distribution.byOrdinal((data.get(DATA_SPREAD) >> (kind.ordinal() * 2)) & 3);
    }

    public boolean isLinked() {
        PortFace face = face();
        return face != null && face.isLinked();
    }

    /** How many pads share this pad's network, this one included. */
    public int networkSize() {
        return data.get(DATA_PORTS);
    }

    /** How many Energy Injectors are paying for it. */
    public int injectorCount() {
        return data.get(DATA_INJECTORS);
    }

    /** Whether the viewer may change what the network carries and how it spreads. */
    public boolean mayChangeNetwork() {
        return data.get(DATA_ACCESS) != 0;
    }

    /** The number a field shows for the open link. */
    public int fieldValueOf(int field) {
        PortChannel link = link();
        if (link == null) {
            return 0;
        }
        return switch (field) {
            case FIELD_KEEP -> link.keepAmount();
            case FIELD_AMOUNT -> link.amount();
            case FIELD_PRIORITY -> link.priority();
            case FIELD_DELAY -> link.delayTicks();
            default -> 0;
        };
    }

    /** Whether a field is following the pad's tier rather than showing a number the player set. */
    public boolean fieldIsAuto(int field) {
        PortChannel link = link();
        if (link == null) {
            return false;
        }
        return switch (field) {
            case FIELD_AMOUNT -> link.amountIsAuto();
            case FIELD_DELAY -> link.delayIsAuto();
            default -> false;
        };
    }

    /**
     * Whether a field means anything for a kind: energy has no stock to keep, and redstone has
     * only the one number, where Keep sits, which is what the stock source calls full.
     */
    public static boolean fieldApplies(int field, @Nullable TransferKind kind) {
        if (kind == TransferKind.REDSTONE) {
            return field == FIELD_KEEP;
        }
        return field != FIELD_KEEP || kind != TransferKind.ENERGY;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean clickMenuButton(Player player, int id) {
        PortFace face = face();
        if (face == null || !mayUse(face, player)) {
            return false;
        }
        if (id >= BUTTON_CHANNEL_BASE && id < BUTTON_CHANNEL_BASE + PortFace.CHANNELS) {
            channel = id - BUTTON_CHANNEL_BASE;
            return true;
        }
        if (id >= BUTTON_TAB_BASE && id < BUTTON_TAB_BASE + TransferKind.all().length) {
            tab = TransferKind.byOrdinal(id - BUTTON_TAB_BASE);
            return true;
        }
        if (id >= BUTTON_NUDGE_BASE && id < BUTTON_NUDGE_BASE + FIELD_COUNT * 8) {
            if (!nudge(face, id - BUTTON_NUDGE_BASE)) {
                return false;
            }
            afterChange();
            return true;
        }
        if (id == BUTTON_EXPORT) {
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new ClipboardPayload(PadSnapshot.of(face).toText()));
                serverPlayer.displayClientMessage(Component.translatable("gui.actualgenerators.link.export.done"), true);
            }
            return true;
        }
        PortChannel link = face.link(channel, tab);
        int chosen = channel;
        TransferKind kind = tab;
        switch (id) {
            case BUTTON_CARRY -> setKind(face, kind, !carried(face));
            case BUTTON_INSERT -> link.toggleInsert();
            case BUTTON_EXTRACT -> link.toggleExtract();
            case BUTTON_PUSH -> {
                if (kind == TransferKind.REDSTONE) {
                    return false;
                }
                link.togglePush();
            }
            case BUTTON_SOURCE_NEXT -> link.cycleSignalSource(false);
            case BUTTON_SOURCE_PREV -> link.cycleSignalSource(true);
            case BUTTON_SPREAD -> {
                if (!face.isLinked() || kind == TransferKind.REDSTONE) {
                    return false;
                }
                manager(face).ifPresent(manager -> manager.setChannelDistribution(face.networkId(), chosen, kind,
                        manager.channelDistribution(face.networkId(), chosen, kind).next()));
            }
            case BUTTON_REDSTONE_NEXT -> link.cycleRedstoneMode();
            case BUTTON_REDSTONE_PREV -> {
                link.cycleRedstoneMode();
                link.cycleRedstoneMode();
                link.cycleRedstoneMode();
            }
            case BUTTON_NETWORK_PICK -> {
                if (player instanceof ServerPlayer serverPlayer && face.level() instanceof ServerLevel level) {
                    NetworkPickerMenu.open(serverPlayer, level, port.getBlockPos(), facing, true);
                }
            }
            case BUTTON_NETWORK_LEAVE -> manager(face).ifPresent(manager -> manager.leave(face));
            default -> {
                return false;
            }
        }
        if (id != BUTTON_NETWORK_PICK && id != BUTTON_NETWORK_LEAVE) {
            afterChange();
        }
        return true;
    }

    /** A typed number for the open link, clamped exactly as the arrows clamp. */
    public void setNumber(int field, long value) {
        PortFace face = face();
        if (face == null || !mayUse(face, viewer) || field < 0 || field >= FIELD_COUNT || !fieldApplies(field, tab)) {
            return;
        }
        PortChannel link = face.link(channel, tab);
        switch (field) {
            case FIELD_KEEP -> link.setKeep(value);
            case FIELD_AMOUNT -> link.setAmount(value);
            case FIELD_PRIORITY -> link.setPriority(value);
            default -> link.setDelay(value);
        }
        afterChange();
    }

    /** A linked pad is its network's: every click is the owner's or the invited's. An unlinked one is anyone's. */
    private static boolean mayUse(PortFace face, Player player) {
        return !face.isLinked() || manager(face).map(manager -> manager.canAccess(face.networkId(), player)).orElse(true);
    }

    /** Writes the kind where it lives: on the network once linked, on the pad before that. */
    private void setKind(PortFace face, TransferKind kind, boolean on) {
        int chosen = channel;
        if (face.isLinked()) {
            manager(face).ifPresent(manager -> manager.setChannelKind(face.networkId(), chosen, kind, on));
        } else {
            face.setLocalKind(chosen, kind, on);
        }
    }

    /** Whether the chosen channel carries the open tab's kind, as the server knows it. */
    private boolean carried(PortFace face) {
        return (kindsOf(face, channel, manager(face).orElse(null)) & ChannelSettings.bit(tab)) != 0;
    }

    private boolean nudge(PortFace face, int code) {
        int field = code / 8;
        int stepIndex = (code % 8) / 2;
        if (field >= FIELD_COUNT || stepIndex >= STEPS.length || !fieldApplies(field, tab)) {
            return false;
        }
        int step = STEPS[stepIndex] * ((code & 1) != 0 ? 1 : -1);
        PortChannel link = face.link(channel, tab);
        int unit = PortChannel.unitFor(tab);
        switch (field) {
            case FIELD_KEEP -> link.nudgeKeep(step * unit);
            case FIELD_AMOUNT -> link.nudgeAmount(step * unit);
            case FIELD_PRIORITY -> link.nudgePriority(step);
            case FIELD_DELAY -> link.nudgeDelay(step);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Optional<LinkNetworkManager> manager(PortFace face) {
        return face.level() instanceof ServerLevel serverLevel
                ? Optional.of(LinkNetworkManager.get(serverLevel.getServer()))
                : Optional.empty();
    }

    static int kindsOf(PortFace face, int channel, @Nullable LinkNetworkManager manager) {
        if (face.isLinked()) {
            return manager == null ? 0 : manager.channelKinds(face.networkId(), channel);
        }
        return face.localKinds(channel);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PORT_SLOTS) {
            if (!moveItemStackTo(stack, PORT_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int filter = PortFace.filterSlot(activeChannel(), activeKind());
            boolean moved = filter >= 0 && stack.getItem() instanceof FilterItem
                    && moveItemStackTo(stack, filter, filter + 1, false);
            if (!moved && !moveItemStackTo(stack, SLOT_RANGE, PORT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        PortFace face = face();
        return face != null && stillValid(access, player, ModBlocks.LOGIC_PORT.get()) && mayUse(face, player);
    }

    /** The server-side view of the network and the window, mirrored to the client each tick. */
    private class PortData implements ContainerData {
        @Override
        public int get(int index) {
            PortFace face = face();
            LinkNetworkManager manager = face != null && face.level() instanceof ServerLevel serverLevel
                    ? LinkNetworkManager.get(serverLevel.getServer())
                    : null;
            UUID network = face == null ? null : face.networkId();
            return switch (index) {
                case DATA_CHANNEL -> channel;
                case DATA_KIND -> tab.ordinal();
                case DATA_SPREAD -> manager == null ? 0 : packedSpread(manager, network, channel);
                case DATA_PORTS -> manager == null ? 0 : manager.portCount(network);
                case DATA_INJECTORS -> manager == null ? 0 : manager.injectorCount(network);
                case DATA_ACCESS -> manager == null || network == null || manager.canAccess(network, viewer) ? 1 : 0;
                case DATA_MISSING_FILTERS -> missingFilters;
                case DATA_MISSING_UPGRADES -> missingUpgrades;
                default -> {
                    if (index >= DATA_KINDS_BASE && index < DATA_KINDS_BASE + KIND_SLOTS) {
                        yield face == null ? 0 : packedKinds(face, manager, (index - DATA_KINDS_BASE) * CHANNELS_PER_KIND_SLOT);
                    }
                    yield 0;
                }
            };
        }

        private static int packedKinds(PortFace face, @Nullable LinkNetworkManager manager, int first) {
            int packed = 0;
            for (int offset = 0; offset < CHANNELS_PER_KIND_SLOT; offset++) {
                packed |= kindsOf(face, first + offset, manager) << (offset * 4);
            }
            return packed;
        }

        private static int packedSpread(LinkNetworkManager manager, @Nullable UUID network, int channel) {
            int packed = 0;
            for (TransferKind kind : TransferKind.all()) {
                packed |= manager.channelDistribution(network, channel, kind).ordinal() << (kind.ordinal() * 2);
            }
            return packed;
        }

        @Override
        public void set(int index, int value) {
            // Server side is authoritative; the client's copy is the SimpleContainerData.
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    }
}
