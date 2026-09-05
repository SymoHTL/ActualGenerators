package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.logistics.NetworkOverview;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One network, read: whose it is, its centre, every pad on it (loaded or not, in reach or not,
 * and what each one does), every injector and what it holds, every channel and what it carries.
 * Opened from the picker's eye on a network's row; Back is the picker again.
 *
 * <p>Nothing here changes anything, so what it shows is a {@link NetworkOverview} taken when it
 * opened and written into the opening packet.
 */
public class NetworkOverviewMenu extends AbstractContainerMenu {
    public static final int BUTTON_BACK = 0;

    public static final int WIDTH = MachineLayout.WIDTH;
    public static final int HEIGHT = 234;
    /** Lines shown at once; the wheel scrolls the rest. */
    public static final int ROWS = 15;
    public static final int ROW_HEIGHT = 12;
    private static final int ROW_Y = 30;

    public static final MachineLayout.Box TITLE = new MachineLayout.Box("title", 8, 6, 160, 9);
    public static final MachineLayout.Box SUBTITLE = new MachineLayout.Box("subtitle", 8, 17, 160, 9);
    public static final MachineLayout.Box SCROLLBAR = new MachineLayout.Box("scrollbar", 168, ROW_Y, 4, ROWS * ROW_HEIGHT);
    public static final MachineLayout.Box BACK_BUTTON = new MachineLayout.Box("back button", 8, 214, 60, 14);

    public static MachineLayout.Box row(int index) {
        return new MachineLayout.Box("row " + index, 8, ROW_Y + index * ROW_HEIGHT, 158, ROW_HEIGHT);
    }

    /** Everything the window draws, for the layout test. */
    public static List<MachineLayout.Box> chrome() {
        List<MachineLayout.Box> boxes = new ArrayList<>(List.of(TITLE, SUBTITLE, SCROLLBAR, BACK_BUTTON));
        for (int index = 0; index < ROWS; index++) {
            boxes.add(row(index));
        }
        return boxes;
    }

    private final BlockPos pos;
    /** The pad's direction, or null when the block is an injector: what the picker needs to come back. */
    private final @Nullable Direction face;
    private final boolean returnToBlock;
    private final NetworkOverview overview;

    /** Server side. */
    public NetworkOverviewMenu(int containerId, Inventory playerInventory, BlockPos pos, @Nullable Direction face,
                               boolean returnToBlock, NetworkOverview overview) {
        super(ModMenus.NETWORK_OVERVIEW.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.returnToBlock = returnToBlock;
        this.overview = overview;
    }

    /** Client side: the overview arrives in the opening packet. */
    public NetworkOverviewMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos(), readFace(buffer), buffer.readBoolean(),
                NetworkOverview.STREAM_CODEC.decode(buffer));
    }

    private static @Nullable Direction readFace(RegistryFriendlyByteBuf buffer) {
        byte value = buffer.readByte();
        return value < 0 ? null : Direction.from3DDataValue(value);
    }

    /** Opens the overview over the picker that asked for it; Back reopens that picker. */
    public static void open(ServerPlayer player, BlockPos pos, @Nullable Direction face, boolean returnToBlock,
                            NetworkOverview overview) {
        player.openMenu(
                new SimpleMenuProvider((id, inventory, viewer) ->
                        new NetworkOverviewMenu(id, inventory, pos, face, returnToBlock, overview),
                        Component.literal(overview.name())),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(face == null ? -1 : face.get3DDataValue());
                    buffer.writeBoolean(returnToBlock);
                    NetworkOverview.STREAM_CODEC.encode(buffer, overview);
                });
    }

    public NetworkOverview overview() {
        return overview;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_BACK && player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel level) {
            NetworkPickerMenu.open(serverPlayer, level, pos, face, returnToBlock);
            return true;
        }
        return false;
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
