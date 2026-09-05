package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.logistics.EnergyInjectorBlockEntity;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.NetworkOverview;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.network.PickerSnapshotPayload;
import dev.symo.actualgenerators.registry.ModMenus;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The window the linking tool opens: which network should this pad (or injector) be on?
 *
 * <p>It lists every network the player may use, offers a name box for a new one, and for the
 * network the block is on now: a row of sixteen colours, who owns it, whether it is public, who is
 * invited and a box to invite one more. And a way off it. An eye at the end of every row opens
 * that network's overview ({@link NetworkOverviewMenu}). Picking is a menu button; creating and
 * inviting carry a name, so they come over on small payloads of their own. The window closes once
 * a network is chosen; the owner's controls act on the spot and the window stays.
 *
 * <p>What the window shows is a {@link Snapshot} taken when it opened, written into the opening
 * packet. A choice is checked against the live record when it arrives, and an owner's action
 * sends a fresh snapshot down rather than opening the window again: a reopened window puts the
 * cursor back in the middle of the screen.
 */
public class NetworkPickerMenu extends AbstractContainerMenu {
    public static final int BUTTON_LEAVE = 0;
    /** {@code BUTTON_PICK_BASE + n} joins the n-th network in the list. */
    public static final int BUTTON_PICK_BASE = 1;
    /** {@code BUTTON_COLOUR_BASE + dye} paints the current network a dye's colour. */
    public static final int BUTTON_COLOUR_BASE = 64;
    /** Opens the current network to everyone, or closes it again. */
    public static final int BUTTON_SHARED = 80;
    /** Makes a network nobody owns the player's. */
    public static final int BUTTON_CLAIM = 81;
    /** {@code BUTTON_MEMBER_BASE + n} takes the n-th invited player off the current network. */
    public static final int BUTTON_MEMBER_BASE = 82;
    /** {@code BUTTON_TRANSFER_BASE + n} hands the network to the n-th invited player. */
    public static final int BUTTON_TRANSFER_BASE = 128;
    /** {@code BUTTON_VIEW_BASE + n} opens the n-th network's overview, without joining it. */
    public static final int BUTTON_VIEW_BASE = 192;
    /** {@code BUTTON_DELETE_BASE + n} forgets the n-th network: its owner's call, and only while nothing is on it. */
    public static final int BUTTON_DELETE_BASE = 256;

    public static final int WIDTH = MachineLayout.WIDTH;
    public static final int HEIGHT = 234;
    public static final int ROWS = 6;
    public static final int ROW_HEIGHT = 14;
    private static final int ROW_Y = 40;
    public static final int COLOURS = 16;
    public static final int MEMBER_ROWS = 3;
    private static final int MEMBER_Y = 174;
    private static final int MEMBER_HEIGHT = 12;

    public static final MachineLayout.Box TITLE = new MachineLayout.Box("title", 8, 6, 160, 9);
    public static final MachineLayout.Box NAME_FIELD = new MachineLayout.Box("name field", 8, 20, 116, 14);
    public static final MachineLayout.Box CREATE_BUTTON = new MachineLayout.Box("create button", 128, 20, 40, 14);
    /** Under the colours: whose the network is (a Claim button while it is nobody's), and whether it is public. */
    public static final MachineLayout.Box OWNER_LINE = new MachineLayout.Box("owner line", 8, 140, 100, 12);
    public static final MachineLayout.Box SHARED_BUTTON = new MachineLayout.Box("shared button", 112, 140, 56, 12);
    /** The owner's invite box and button; the invited are listed under them. */
    public static final MachineLayout.Box INVITE_FIELD = new MachineLayout.Box("invite field", 8, 156, 116, 14);
    public static final MachineLayout.Box INVITE_BUTTON = new MachineLayout.Box("invite button", 128, 156, 40, 14);
    public static final MachineLayout.Box LEAVE_BUTTON = new MachineLayout.Box("leave button", 8, 214, 60, 14);

    public static MachineLayout.Box row(int index) {
        return new MachineLayout.Box("row " + index, 8, ROW_Y + index * ROW_HEIGHT, 134, ROW_HEIGHT - 1);
    }

    /** The cross after a network's row: forget the network, when it is the player's and has nothing on it. */
    public static MachineLayout.Box rowDelete(int index) {
        return new MachineLayout.Box("row " + index + " delete", 144, ROW_Y + index * ROW_HEIGHT, 12, ROW_HEIGHT - 1);
    }

    /** The eye at the end of a row: the network's overview. */
    public static MachineLayout.Box rowView(int index) {
        return new MachineLayout.Box("row " + index + " view", 156, ROW_Y + index * ROW_HEIGHT, 12, ROW_HEIGHT - 1);
    }

    /** One of the sixteen colour cells, in dye order. */
    public static MachineLayout.Box colourCell(int dye) {
        return new MachineLayout.Box("colour " + dye, 8 + dye * 10, 128, 10, 10);
    }

    /** One invited player's line, the crown that makes them the owner, and the cross that takes them off. */
    public static MachineLayout.Box memberRow(int index) {
        return new MachineLayout.Box("member " + index, 8, MEMBER_Y + index * MEMBER_HEIGHT, 130, MEMBER_HEIGHT - 1);
    }

    public static MachineLayout.Box memberTransfer(int index) {
        return new MachineLayout.Box("member " + index + " transfer", 140, MEMBER_Y + index * MEMBER_HEIGHT, 12,
                MEMBER_HEIGHT - 1);
    }

    public static MachineLayout.Box memberRemove(int index) {
        return new MachineLayout.Box("member " + index + " remove", 156, MEMBER_Y + index * MEMBER_HEIGHT, 12,
                MEMBER_HEIGHT - 1);
    }

    /** The RGB a dye paints a network. */
    public static int dyeColour(int dye) {
        return DyeColor.byId(dye).getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** Everything the window draws, for the layout test. */
    public static List<MachineLayout.Box> chrome() {
        List<MachineLayout.Box> boxes = new ArrayList<>();
        boxes.add(TITLE);
        boxes.add(NAME_FIELD);
        boxes.add(CREATE_BUTTON);
        for (int index = 0; index < ROWS; index++) {
            boxes.add(row(index));
            boxes.add(rowDelete(index));
            boxes.add(rowView(index));
        }
        for (int dye = 0; dye < COLOURS; dye++) {
            boxes.add(colourCell(dye));
        }
        boxes.add(OWNER_LINE);
        boxes.add(SHARED_BUTTON);
        boxes.add(INVITE_FIELD);
        boxes.add(INVITE_BUTTON);
        for (int index = 0; index < MEMBER_ROWS; index++) {
            boxes.add(memberRow(index));
            boxes.add(memberTransfer(index));
            boxes.add(memberRemove(index));
        }
        boxes.add(LEAVE_BUTTON);
        return boxes;
    }

    /**
     * Everything the window shows, as of the last time the server looked: the networks the viewer
     * may use, the one the block is on, whether the viewer owns that one, and who is invited to it.
     */
    public record Snapshot(List<LinkNetworkManager.Summary> choices,
                           Optional<UUID> current,
                           boolean owns,
                           List<LinkNetworkManager.Member> members) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), Optional.empty(), false, List.of());

        public static final StreamCodec<ByteBuf, Snapshot> STREAM_CODEC = StreamCodec.composite(
                LinkNetworkManager.Summary.STREAM_CODEC.apply(ByteBufCodecs.list()), Snapshot::choices,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Snapshot::current,
                ByteBufCodecs.BOOL, Snapshot::owns,
                LinkNetworkManager.Member.STREAM_CODEC.apply(ByteBufCodecs.list()), Snapshot::members,
                Snapshot::new);

        static Snapshot of(LinkNetworkManager manager, ServerPlayer player, ServerLevel level, BlockPos pos,
                           @Nullable Direction face) {
            UUID current = currentNetwork(level, pos, face);
            return new Snapshot(manager.summaries(player), Optional.ofNullable(current),
                    current != null && manager.isOwner(current, player), manager.members(current));
        }
    }

    private final BlockPos pos;
    /** The pad's direction, or null when the block is an injector. */
    private final @Nullable Direction face;
    /** Opened from the block's own window, which is reopened once the choice is made. */
    private final boolean returnToBlock;
    private Snapshot snapshot;
    /** Counts the snapshots this window has been given, so the screen can tell a new one. */
    private int version;

    /** Server side. */
    public NetworkPickerMenu(int containerId, Inventory playerInventory, BlockPos pos, @Nullable Direction face,
                             Snapshot snapshot, boolean returnToBlock) {
        super(ModMenus.NETWORK_PICKER.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.snapshot = snapshot;
        this.returnToBlock = returnToBlock;
    }

    /** Client side: the snapshot arrives in the opening packet. */
    public NetworkPickerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos(), readFace(buffer),
                Snapshot.STREAM_CODEC.decode(buffer), buffer.readBoolean());
    }

    private static @Nullable Direction readFace(RegistryFriendlyByteBuf buffer) {
        byte value = buffer.readByte();
        return value < 0 ? null : Direction.from3DDataValue(value);
    }

    /**
     * Opens the picker for a pad ({@code face} given) or an injector ({@code face} null).
     *
     * @param returnToBlock whether to reopen the block's own window afterwards, for a picker opened from it
     */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable Direction face,
                            boolean returnToBlock) {
        Snapshot snapshot = Snapshot.of(LinkNetworkManager.get(level.getServer()), player, level, pos, face);
        Component title = face == null
                ? Component.translatable("gui.actualgenerators.picker.title.injector")
                : Component.translatable("gui.actualgenerators.picker.title.pad",
                Component.translatable("gui.actualgenerators.facing." + face.getName()));
        player.openMenu(
                new SimpleMenuProvider((id, inventory, viewer) ->
                        new NetworkPickerMenu(id, inventory, pos, face, snapshot, returnToBlock), title),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(face == null ? -1 : face.get3DDataValue());
                    Snapshot.STREAM_CODEC.encode(buffer, snapshot);
                    buffer.writeBoolean(returnToBlock);
                });
    }

    private static @Nullable UUID currentNetwork(ServerLevel level, BlockPos pos, @Nullable Direction face) {
        if (level.getBlockEntity(pos) instanceof LinkPortBlockEntity port && face != null) {
            PortFace pad = port.face(face);
            return pad == null ? null : pad.networkId();
        }
        if (level.getBlockEntity(pos) instanceof EnergyInjectorBlockEntity injector) {
            return injector.networkId();
        }
        return null;
    }

    // ------------------------------------------------------------------ readouts

    public List<LinkNetworkManager.Summary> choices() {
        return snapshot.choices();
    }

    public @Nullable UUID current() {
        return snapshot.current().orElse(null);
    }

    public boolean isInjector() {
        return face == null;
    }

    /** Whether the viewer owns the current network: the colours, the switch and the invites are theirs. */
    public boolean owns() {
        return snapshot.owns();
    }

    /** Who is invited onto the current network, as of the last snapshot. */
    public List<LinkNetworkManager.Member> members() {
        return snapshot.members();
    }

    /** Goes up with every snapshot the window is given; the screen watches it. */
    public int version() {
        return version;
    }

    /** A fresh look from the server, on either side. */
    public void update(Snapshot snapshot) {
        this.snapshot = snapshot;
        version++;
    }

    /** What the name box starts with: the next number along, which is what most people would type. */
    public String suggestedName() {
        return "Network " + (choices().size() + 1);
    }

    // ------------------------------------------------------------------ choices

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        LinkNetworkManager manager = LinkNetworkManager.get(level.getServer());
        UUID current = current();
        if (id == BUTTON_LEAVE) {
            leave(manager, level);
            finish(player);
            return true;
        }
        if (id >= BUTTON_COLOUR_BASE && id < BUTTON_COLOUR_BASE + COLOURS) {
            if (current == null || !manager.isOwner(current, player)) {
                return false;
            }
            manager.setColour(current, dyeColour(id - BUTTON_COLOUR_BASE));
            return true;
        }
        if (id == BUTTON_SHARED) {
            if (current == null || !manager.setShared(current, player, !manager.isShared(current))) {
                return false;
            }
            resend(player, level);
            return true;
        }
        if (id == BUTTON_CLAIM) {
            if (current == null || !manager.claim(current, player)) {
                return false;
            }
            resend(player, level);
            return true;
        }
        List<LinkNetworkManager.Member> members = members();
        if (id >= BUTTON_MEMBER_BASE && id < BUTTON_MEMBER_BASE + members.size()) {
            if (current == null || !manager.removeMember(current, player, members.get(id - BUTTON_MEMBER_BASE).id())) {
                return false;
            }
            resend(player, level);
            return true;
        }
        if (id >= BUTTON_TRANSFER_BASE && id < BUTTON_TRANSFER_BASE + members.size()) {
            if (current == null || !manager.transfer(current, player, members.get(id - BUTTON_TRANSFER_BASE).id())) {
                return false;
            }
            resend(player, level);
            return true;
        }
        List<LinkNetworkManager.Summary> choices = choices();
        if (id >= BUTTON_DELETE_BASE && id < BUTTON_DELETE_BASE + choices.size()) {
            if (!manager.delete(choices.get(id - BUTTON_DELETE_BASE).id(), player)) {
                return false;
            }
            resend(player, level);
            return true;
        }
        if (id >= BUTTON_VIEW_BASE && id < BUTTON_VIEW_BASE + choices.size()) {
            UUID chosen = choices.get(id - BUTTON_VIEW_BASE).id();
            NetworkOverview overview = manager.canAccess(chosen, player) ? manager.overview(chosen) : null;
            if (overview == null || !(player instanceof ServerPlayer serverPlayer)) {
                return false;
            }
            NetworkOverviewMenu.open(serverPlayer, pos, face, returnToBlock, overview);
            return true;
        }
        int index = id - BUTTON_PICK_BASE;
        if (index >= 0 && index < choices.size()) {
            UUID chosen = choices.get(index).id();
            // The list is a snapshot; the network is asked again now, in case it closed meanwhile.
            if (!manager.canAccess(chosen, player)) {
                return false;
            }
            join(manager, level, chosen);
            finish(player);
            return true;
        }
        return false;
    }

    /** Makes a new network with the given name, owned by the player, and puts the block on it. */
    public void create(Player player, String name) {
        if (player.level() instanceof ServerLevel level) {
            LinkNetworkManager manager = LinkNetworkManager.get(level.getServer());
            join(manager, level, manager.create(name, level.dimension(), player));
        }
        finish(player);
    }

    /** Invites a player by name onto the current network; the owner's call. */
    public void invite(Player player, String name) {
        UUID current = current();
        if (current != null && player.level() instanceof ServerLevel level
                && LinkNetworkManager.get(level.getServer()).invite(current, player, name)) {
            resend(player, level);
        }
    }

    /** The choice is made: back to the block's window if that is where this came from, else closed. */
    private void finish(Player player) {
        if (returnToBlock && player instanceof ServerPlayer serverPlayer) {
            if (face != null && player.level().getBlockEntity(pos) instanceof LinkPortBlockEntity port
                    && port.face(face) != null) {
                serverPlayer.openMenu(new LinkPortMenu.Opener(port, face),
                        buffer -> LinkPortMenu.writeOpener(buffer, port, face));
                return;
            }
            if (face == null && player.level().getBlockEntity(pos) instanceof EnergyInjectorBlockEntity injector) {
                serverPlayer.openMenu(injector, buffer -> buffer.writeBlockPos(pos));
                return;
            }
        }
        player.closeContainer();
    }

    /**
     * Something about who may use the network changed: both ends get a fresh snapshot, this one
     * so the member buttons keep pointing at the right players, the client's so it shows it.
     */
    private void resend(Player player, ServerLevel level) {
        if (player instanceof ServerPlayer serverPlayer) {
            update(Snapshot.of(LinkNetworkManager.get(level.getServer()), serverPlayer, level, pos, face));
            PacketDistributor.sendToPlayer(serverPlayer, new PickerSnapshotPayload(containerId, snapshot));
        }
    }

    private void join(LinkNetworkManager manager, ServerLevel level, UUID id) {
        if (level.getBlockEntity(pos) instanceof LinkPortBlockEntity port && face != null) {
            PortFace pad = port.face(face);
            if (pad != null) {
                manager.join(pad, id);
            }
        } else if (level.getBlockEntity(pos) instanceof EnergyInjectorBlockEntity injector) {
            manager.joinInjector(injector, id);
        }
    }

    private void leave(LinkNetworkManager manager, ServerLevel level) {
        if (level.getBlockEntity(pos) instanceof LinkPortBlockEntity port && face != null) {
            PortFace pad = port.face(face);
            if (pad != null) {
                manager.leave(pad);
            }
        } else if (level.getBlockEntity(pos) instanceof EnergyInjectorBlockEntity injector) {
            manager.leaveInjector(injector);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.canInteractWithBlock(pos, 4.0);
    }
}
