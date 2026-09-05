package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A Filter item's window: one list of entries, a row each, and one button that adds one.
 *
 * <p>The list page is the filter read at a glance: each row is the entry's picture and name, its
 * marks (IN, EX, whether it lets its match through or stops it) and a remove button. <b>Add</b>
 * asks what kind of entry, an item or a tag, and opens that entry's page; so does a click on a
 * row. The pages are the screen's, over this one menu: the item page is a slot to click the item
 * or fluid into, the entry's data (what used to be NBT) written out with a Match/Ignore switch,
 * and the three switches every entry has, IN, EX and whitelist-or-blacklist; the tag page is the
 * same slot, the same three switches and a list of the pictured thing's tags to pick from.
 *
 * <p>The slot on the pages is a picture, never an item: nothing is behind it, so no vanilla move
 * path can put a stack into it or take one out. A click on it copies what is on the cursor into
 * the entry instead, left the item, right the fluid inside it, and travels as a payload, since a
 * slot that is not a slot has no vanilla packet.
 *
 * <p>The filter itself stays in the player's inventory while its window is open, in a slot that
 * is locked so the window cannot be left pointing at whatever landed there next.
 */
public class FilterMenu extends AbstractContainerMenu {
    /** Per entry, five buttons. The id carries the entry's index. */
    public static final int BUTTON_ENTRY_BASE = 8;
    public static final int ENTRY_BUTTONS = 5;
    public static final int ENTRY_RECEIVE = 0;
    public static final int ENTRY_SEND = 1;
    /** Whitelist or blacklist, the entry's own. */
    public static final int ENTRY_MODE = 2;
    /** Whether the entry's data has to match. Exact entries only. */
    public static final int ENTRY_MATCH = 3;
    public static final int ENTRY_REMOVE = 4;

    public static final int WIDTH = MachineLayout.WIDTH;
    public static final int HEIGHT = 224;
    /** Rows shown at once; the wheel scrolls the rest. */
    public static final int ROWS = 5;
    public static final int ROW_HEIGHT = 18;
    public static final int ROW_Y = 36;

    // ------------------------------------------------------------------ the list page

    public static final MachineLayout.Box TITLE = new MachineLayout.Box("title", 8, 6, 160, 9);
    /** The one button at the top. Clicked, it becomes the two below it. */
    public static final MachineLayout.Box ADD_BUTTON = new MachineLayout.Box("add", 8, 18, 160, 14);
    public static final MachineLayout.Box ADD_ITEM_BUTTON = new MachineLayout.Box("add item", 8, 18, 79, 14);
    public static final MachineLayout.Box ADD_TAG_BUTTON = new MachineLayout.Box("add tag", 89, 18, 79, 14);
    public static final MachineLayout.Box SCROLLBAR = new MachineLayout.Box("scrollbar", 168, ROW_Y, 4, ROWS * ROW_HEIGHT);

    private static final int SLOT = 18;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = HEIGHT - 82;
    private static final int HOTBAR_Y = HEIGHT - 24;

    // ------------------------------------------------------------------ the entry pages

    public static final MachineLayout.Box ENTRY_TITLE = new MachineLayout.Box("entry title", 8, 6, 160, 9);
    /** The picture slot, at the standard slot size; a click on it writes the cursor into the entry. */
    public static final MachineLayout.Box ENTRY_SLOT = MachineLayout.slotFrame("entry slot", 9, 19);
    public static final MachineLayout.Box ENTRY_NAME = new MachineLayout.Box("entry name", 30, 18, 138, 9);
    /** Item page only: whether the data written below has to match. */
    public static final MachineLayout.Box ENTRY_MATCH_BUTTON = new MachineLayout.Box("entry match", 30, 29, 62, 12);
    public static final MachineLayout.Box ENTRY_RECEIVE_BUTTON = new MachineLayout.Box("entry receive", 94, 29, 18, 12);
    public static final MachineLayout.Box ENTRY_SEND_BUTTON = new MachineLayout.Box("entry send", 114, 29, 18, 12);
    public static final MachineLayout.Box ENTRY_MODE_BUTTON = new MachineLayout.Box("entry mode", 134, 29, 34, 12);
    /** Item page: the entry's components, written out, wheel to scroll. */
    /** The item page's data box: the entry's components as text to edit, Apply under it, and what came of the last Apply. */
    public static final MachineLayout.Box ENTRY_DATA = new MachineLayout.Box("entry data", 8, 44, 160, 66);
    public static final MachineLayout.Box ENTRY_APPLY_BUTTON = new MachineLayout.Box("entry apply", 8, 112, 60, 12);
    public static final MachineLayout.Box ENTRY_DATA_STATUS = new MachineLayout.Box("entry data status", 72, 114, 96, 9);
    /** The tag page's box for a tag typed by name; the pictured thing's tags list under it. */
    public static final MachineLayout.Box ENTRY_TAG_BOX = new MachineLayout.Box("entry tag box", 8, 44, 160, 12);
    /** Tag page: the pictured thing's tags, one a row. Eight show; the wheel scrolls. */
    public static final int ENTRY_ROWS = 6;
    private static final int ENTRY_ROW_Y = 58;
    private static final int ENTRY_ROW_HEIGHT = 10;
    public static final MachineLayout.Box ENTRY_BACK_BUTTON = new MachineLayout.Box("entry back", 8, 126, 60, 14);
    public static final MachineLayout.Box ENTRY_REMOVE_BUTTON = new MachineLayout.Box("entry remove", 108, 126, 60, 14);

    public static int entryButton(int index, int which) {
        return BUTTON_ENTRY_BASE + index * ENTRY_BUTTONS + which;
    }

    // The pieces of a row, as boxes, so the layout test sees each one.

    public static MachineLayout.Box rowIcon(int row) {
        return new MachineLayout.Box("row " + row + " icon", 8, ROW_Y + row * ROW_HEIGHT, 18, 18);
    }

    public static MachineLayout.Box rowLabel(int row) {
        return new MachineLayout.Box("row " + row + " label", 28, ROW_Y + row * ROW_HEIGHT + 5, 72, 9);
    }

    /** IN, EX and the mode, as small marks. */
    public static MachineLayout.Box rowMarks(int row) {
        return new MachineLayout.Box("row " + row + " marks", 102, ROW_Y + row * ROW_HEIGHT + 5, 48, 9);
    }

    public static MachineLayout.Box rowRemove(int row) {
        return new MachineLayout.Box("row " + row + " remove", 152, ROW_Y + row * ROW_HEIGHT + 2, 14, 14);
    }

    /** One line of the tag page's list. */
    public static MachineLayout.Box entryRow(int row) {
        return new MachineLayout.Box("entry row " + row, 8, ENTRY_ROW_Y + row * ENTRY_ROW_HEIGHT, 160, ENTRY_ROW_HEIGHT);
    }

    /** The list page with Add closed. */
    public static List<MachineLayout.Box> chrome() {
        return listChrome(List.of(ADD_BUTTON));
    }

    /** The list page with Add open: the two choices in its place. */
    public static List<MachineLayout.Box> addingChrome() {
        return listChrome(List.of(ADD_ITEM_BUTTON, ADD_TAG_BUTTON));
    }

    private static List<MachineLayout.Box> listChrome(List<MachineLayout.Box> top) {
        List<MachineLayout.Box> boxes = new ArrayList<>();
        boxes.add(TITLE);
        boxes.addAll(top);
        boxes.add(SCROLLBAR);
        for (int row = 0; row < ROWS; row++) {
            boxes.add(rowIcon(row));
            boxes.add(rowLabel(row));
            boxes.add(rowMarks(row));
            boxes.add(rowRemove(row));
        }
        return boxes;
    }

    public static List<MachineLayout.Box> itemEntryChrome() {
        return new ArrayList<>(List.of(ENTRY_TITLE, ENTRY_SLOT, ENTRY_NAME, ENTRY_MATCH_BUTTON, ENTRY_RECEIVE_BUTTON,
                ENTRY_SEND_BUTTON, ENTRY_MODE_BUTTON, ENTRY_DATA, ENTRY_APPLY_BUTTON, ENTRY_DATA_STATUS, ENTRY_BACK_BUTTON,
                ENTRY_REMOVE_BUTTON));
    }

    public static List<MachineLayout.Box> tagEntryChrome() {
        List<MachineLayout.Box> boxes = new ArrayList<>(List.of(ENTRY_TITLE, ENTRY_SLOT, ENTRY_NAME, ENTRY_RECEIVE_BUTTON,
                ENTRY_SEND_BUTTON, ENTRY_MODE_BUTTON, ENTRY_TAG_BOX));
        for (int row = 0; row < ENTRY_ROWS; row++) {
            boxes.add(entryRow(row));
        }
        boxes.add(ENTRY_BACK_BUTTON);
        boxes.add(ENTRY_REMOVE_BUTTON);
        return boxes;
    }

    private final Inventory playerInventory;
    private final int hostSlot;

    public FilterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readVarInt());
    }

    public FilterMenu(int containerId, Inventory playerInventory, int hostSlot) {
        super(ModMenus.FILTER.get(), containerId);
        this.playerInventory = playerInventory;
        this.hostSlot = hostSlot;
        addPlayerInventory(playerInventory);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                addSlot(playerSlot(inventory, index, PLAYER_INVENTORY_X + column * SLOT, PLAYER_INVENTORY_Y + row * SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(playerSlot(inventory, column, PLAYER_INVENTORY_X + column * SLOT, HOTBAR_Y));
        }
    }

    private Slot playerSlot(Inventory inventory, int index, int x, int y) {
        return index == hostSlot ? new LockedSlot(inventory, index, x, y) : new Slot(inventory, index, x, y);
    }

    // ------------------------------------------------------------------ readouts

    /** The filter this window edits, read back from the inventory each time. */
    public ItemStack filter() {
        ItemStack stack = playerInventory.getItem(hostSlot);
        return stack.getItem() instanceof FilterItem ? stack : ItemStack.EMPTY;
    }

    public FilterContents contents() {
        return FilterItem.contents(filter());
    }

    public FilterContents.Entry entry(int index) {
        return contents().entry(index);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack filter = filter();
        if (filter.isEmpty() || id < BUTTON_ENTRY_BASE) {
            return false;
        }
        FilterContents contents = contents();
        int index = (id - BUTTON_ENTRY_BASE) / ENTRY_BUTTONS;
        int which = (id - BUTTON_ENTRY_BASE) % ENTRY_BUTTONS;
        FilterContents.Entry entry = contents.entry(index);
        if (entry.isEmpty()) {
            return false;
        }
        FilterItem.setContents(filter, switch (which) {
            case ENTRY_RECEIVE -> contents.set(index, entry.withDirections(!entry.receive(), entry.send()));
            case ENTRY_SEND -> contents.set(index, entry.withDirections(entry.receive(), !entry.send()));
            case ENTRY_MODE -> contents.set(index, entry.withBlacklist(!entry.blacklist()));
            case ENTRY_MATCH -> contents.set(index, entry.withMatchComponents(!entry.matchComponents()));
            default -> contents.remove(index);
        });
        return true;
    }

    /** What a click on the picture slot writes: the item on the cursor, or on a right click the fluid inside it. */
    public static FilterContents.Entry entryFor(ItemStack carried, boolean rightClick) {
        if (carried.isEmpty()) {
            return FilterContents.Entry.EMPTY;
        }
        if (rightClick) {
            FluidStack fluid = FilterContents.fluidIn(carried);
            if (!fluid.isEmpty()) {
                return FilterContents.Entry.ofFluid(fluid);
            }
        }
        return FilterContents.Entry.ofItem(carried);
    }

    /** Writes one entry onto the filter: in place for an index on the list, at the end for any other. */
    public void setEntry(int index, FilterContents.Entry entry) {
        ItemStack filter = filter();
        if (filter.isEmpty()) {
            return;
        }
        FilterItem.setContents(filter, contents().set(index, entry));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Nothing here takes items, so a shift-click has nowhere to send them.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !filter().isEmpty();
    }

    /** The filter cannot be moved out from under its own window. */
    private static class LockedSlot extends Slot {
        LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
