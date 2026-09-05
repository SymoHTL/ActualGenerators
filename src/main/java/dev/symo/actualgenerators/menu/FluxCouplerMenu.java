package dev.symo.actualgenerators.menu;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.item.FluxCouplerItem;
import dev.symo.actualgenerators.item.FluxCrystalItem;
import dev.symo.actualgenerators.item.UpgradeItem;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.registry.ModDataComponents;
import dev.symo.actualgenerators.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The flux coupler's window: crystals in, the one being drawn from, empties out, and the energy
 * upgrade that decides how fast it hands charge over.
 *
 * <p>The coupler is an item rather than a block, so there is no block entity to hold any of this.
 * The slots live in a component on the stack itself and the menu addresses that stack by its
 * inventory index — which is also why the slot holding it is locked while the window is open: a
 * coupler that could be picked up and dropped while its own inventory is on screen would leave the
 * menu pointing at whatever landed in the slot next.
 *
 * <p>Between the component and the screen sits an ordinary {@link SimpleContainer}. That is not
 * ceremony: vanilla's own menu code takes the stack a slot hands out and edits it in place —
 * {@code moveItemStackTo} does exactly this — which works for a container holding live stacks and
 * silently destroys items for a component-backed handler, because what it hands out is a copy of
 * something immutable. So the container holds the live stacks, every change is written straight
 * back onto the item, and anything the coupler does to the component while the window is open is
 * read back in on the next tick.
 */
public class FluxCouplerMenu extends AbstractContainerMenu {
    /** Flips the coupler on or off. Same id as the machine menus use, for one habit rather than two. */
    public static final int BUTTON_TOGGLE_ACTIVE = MachineMenu.BUTTON_TOGGLE_MODE;

    // Slot spacing is 42 rather than the usual 36: an arrow is 24 wide and a slot frame is 18,
    // so anything tighter draws the arrow across the next slot. It used to.
    public static final int INPUT_SLOT_X = 38;
    public static final int INPUT_SLOT_Y = 34;
    public static final int WORKING_SLOT_X = 80;
    public static final int WORKING_SLOT_Y = 34;
    public static final int OUTPUT_SLOT_X = 122;
    public static final int OUTPUT_SLOT_Y = 34;
    public static final int UPGRADE_SLOT_X = MachineMenu.UPGRADE_SLOT_X;
    public static final int UPGRADE_SLOT_Y = MachineMenu.UPGRADE_SLOT_Y;

    /** Crystals move left to right, so an arrow sits in each gap. */
    public static final MachineLayout.Box DRAIN_ARROW =
            new MachineLayout.Box("drain arrow", INPUT_SLOT_X + 17, INPUT_SLOT_Y + 2, 24, 13);
    public static final MachineLayout.Box RETIRE_ARROW =
            new MachineLayout.Box("retire arrow", WORKING_SLOT_X + 17, WORKING_SLOT_Y + 2, 24, 13);
    /** Where the FE/t line is written. */
    public static final MachineLayout.Box RATE_READOUT = new MachineLayout.Box("rate readout", 12, 60, 60, 9);
    /** The on/off switch, in the same corner a machine keeps its redstone button. */
    public static final MachineLayout.Box TOGGLE_BUTTON = MachineLayout.REDSTONE_BUTTON;

    /**
     * Everything this window draws that is not a slot, for the layout guardrail.
     *
     * <p>The coupler is not a machine, but its window is still UI, and UI that overlaps is a bug
     * whoever is looking at it has to notice. So it publishes its boxes the same way.
     */
    public static List<MachineLayout.Box> chrome() {
        return List.of(MachineLayout.ENERGY_FILL.grow(1), TOGGLE_BUTTON, DRAIN_ARROW, RETIRE_ARROW, RATE_READOUT);
    }

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 112;
    private static final int HOTBAR_Y = 170;
    private static final int COUPLER_SLOTS = FluxCouplerItem.SLOT_COUNT;

    private final Inventory playerInventory;
    /** Where the coupler sits in the player's inventory; the stack itself is fetched on demand. */
    private final int couplerSlot;
    private final CouplerContainer contents;

    public FluxCouplerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readVarInt());
    }

    public FluxCouplerMenu(int containerId, Inventory playerInventory, int couplerSlot) {
        super(ModMenus.FLUX_COUPLER.get(), containerId);
        this.playerInventory = playerInventory;
        this.couplerSlot = couplerSlot;
        this.contents = new CouplerContainer();

        addSlot(new CrystalSlot(contents, FluxCouplerItem.SLOT_INPUT, INPUT_SLOT_X, INPUT_SLOT_Y));
        addSlot(new WorkingSlot(contents, FluxCouplerItem.SLOT_WORKING, WORKING_SLOT_X, WORKING_SLOT_Y));
        addSlot(new ResultSlot(contents, FluxCouplerItem.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
        addSlot(new UpgradeSlot(contents, FluxCouplerItem.SLOT_UPGRADE, UPGRADE_SLOT_X, UPGRADE_SLOT_Y));

        addPlayerInventory(playerInventory);
    }

    /** The coupler this menu is editing, read back from the inventory each time it is needed. */
    public ItemStack coupler() {
        ItemStack stack = playerInventory.getItem(couplerSlot);
        return stack.getItem() instanceof FluxCouplerItem ? stack : ItemStack.EMPTY;
    }

    public boolean isActive() {
        return FluxCouplerItem.isActive(coupler());
    }

    /** FE/t it is currently handing out, upgrades included. */
    public int ratePerTick() {
        return FluxCouplerItem.ratePerTick(coupler());
    }

    public long chargeHeld() {
        return FluxCouplerItem.chargeHeld(coupler());
    }

    public long chargeCapacity() {
        return FluxCouplerItem.chargeCapacity(coupler());
    }

    /** How much of the crystal in hand has been used, 0 to 1 — what the first arrow shows. */
    public double progress() {
        ItemStack working = contents.getItem(FluxCouplerItem.SLOT_WORKING);
        if (!(working.getItem() instanceof FluxCrystalItem)) {
            return 0;
        }
        long capacity = FluxCrystalItem.capacity();
        return capacity <= 0 ? 0 : Math.clamp(1.0 - FluxCrystalItem.energyOf(working) / (double) capacity, 0.0, 1.0);
    }

    /** FE still in the crystal it has in hand. */
    public long chargeInHand() {
        return FluxCrystalItem.energyOf(contents.getItem(FluxCouplerItem.SLOT_WORKING));
    }

    public int installedUpgrades() {
        return FluxCouplerItem.upgradeCount(coupler());
    }

    public static int maxUpgrades() {
        return ServerConfig.tuning().maxUpgrades(UpgradeType.ENERGY);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                addSlot(playerSlot(inventory, index,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(playerSlot(inventory, column, PLAYER_INVENTORY_X + column * 18, HOTBAR_Y));
        }
    }

    /** The slot the coupler itself is in stays put while its window is open. */
    private Slot playerSlot(Inventory inventory, int index, int x, int y) {
        return index == couplerSlot ? new LockedSlot(inventory, index, x, y) : new Slot(inventory, index, x, y);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_TOGGLE_ACTIVE) {
            return false;
        }
        ItemStack coupler = coupler();
        if (coupler.isEmpty()) {
            return false;
        }
        FluxCouplerItem.setActive(coupler, !FluxCouplerItem.isActive(coupler));
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return !coupler().isEmpty();
    }

    /**
     * Picks up anything the coupler did to itself since the last tick.
     *
     * <p>It goes on working while its window is open, so the component can change under the
     * container. Reloading first means a click never writes a stale view back over it.
     */
    @Override
    public void broadcastChanges() {
        contents.refreshFromItem();
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        contents.writeToItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < COUPLER_SLOTS) {
            if (!moveItemStackTo(stack, COUPLER_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, COUPLER_SLOTS, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * The coupler's four slots as a plain container, kept in step with the component on the item.
     *
     * <p>Every change writes straight through, so nothing is ever only half-saved: if the window is
     * closed, the game quits, or the coupler is handed to someone else, what is on the item is what
     * was on the screen.
     */
    private class CouplerContainer extends SimpleContainer {
        /** The component as we last read or wrote it, so the coupler's own edits can be told apart. */
        private @Nullable ItemContainerContents known;

        CouplerContainer() {
            super(FluxCouplerItem.SLOT_COUNT);
            refreshFromItem();
        }

        /** Reloads from the item when something other than this container changed it. */
        void refreshFromItem() {
            ItemStack coupler = coupler();
            if (coupler.isEmpty()) {
                return;
            }
            ItemContainerContents current = coupler.getOrDefault(ModDataComponents.SLOTS.get(), ItemContainerContents.EMPTY);
            if (current == known) {
                return;
            }
            for (int slot = 0; slot < FluxCouplerItem.SLOT_COUNT; slot++) {
                // Trailing empty slots are not stored, so the component can be shorter than four.
                setItemQuietly(slot, slot < current.getSlots() ? current.getStackInSlot(slot) : ItemStack.EMPTY);
            }
            known = current;
        }

        void writeToItem() {
            ItemStack coupler = coupler();
            if (coupler.isEmpty()) {
                return;
            }
            List<ItemStack> stacks = new ArrayList<>(FluxCouplerItem.SLOT_COUNT);
            for (int slot = 0; slot < FluxCouplerItem.SLOT_COUNT; slot++) {
                stacks.add(getItem(slot));
            }
            ItemContainerContents written = ItemContainerContents.fromItems(stacks);
            coupler.set(ModDataComponents.SLOTS.get(), written);
            known = written;
        }

        /** Puts a stack in place without the write-back that {@link #setItem} would trigger. */
        private void setItemQuietly(int slot, ItemStack stack) {
            super.setItem(slot, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            writeToItem();
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            writeToItem();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack removed = super.removeItem(slot, amount);
            writeToItem();
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack removed = super.removeItemNoUpdate(slot);
            writeToItem();
            return removed;
        }
    }

    /** Crystals only. */
    private static class CrystalSlot extends Slot {
        CrystalSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof FluxCrystalItem;
        }
    }

    /**
     * The crystal the coupler currently has in hand. It is a readout, not a slot to trade through:
     * taking a part-drained crystal out mid-pass, or dropping a fresh one in, is the coupler's job
     * to arrange, and it does it from the input slot.
     */
    private static class WorkingSlot extends Slot {
        WorkingSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Spent crystals may be taken out but never put back. */
    private static class ResultSlot extends Slot {
        ResultSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Energy upgrades, as many as the tuning counts — the same ceiling a machine slot has. */
    private static class UpgradeSlot extends Slot {
        UpgradeSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem upgrade && upgrade.type() == UpgradeType.ENERGY;
        }

        @Override
        public int getMaxStackSize() {
            return Math.max(1, maxUpgrades());
        }
    }

    /** The coupler cannot be moved out from under its own window. */
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
