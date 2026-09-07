package dev.symo.actualgenerators.menu;

import java.util.ArrayList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import java.util.List;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.MachineTier;
import dev.symo.actualgenerators.machine.MachineTuning;
import dev.symo.actualgenerators.machine.RedstoneMode;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeInventory;
import dev.symo.actualgenerators.machine.UpgradeType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The menu every machine shares: upgrade slots, the player's inventory, and the synced values a
 * machine screen needs to draw itself.
 *
 * <p>Configuration changes travel back as menu button presses rather than custom packets — the
 * vanilla path already validates that the player has the menu open and is in range.
 *
 * <p>One thing to know before adding a value here: vanilla syncs container data as a
 * <em>signed 16-bit</em> number. Anything that can exceed 32,767 — an FE reading, most obviously —
 * has to travel in pieces: two slots for an int, reassembled with {@link #wide}, and four for the
 * FE amounts, which are longs, reassembled with {@link #huge}. Anything that is really a fraction
 * is sent as permille rather than as two tick counts.
 */
public abstract class MachineMenu<T extends MachineBlockEntity> extends AbstractContainerMenu {
    // --- synced value indices, shared by every machine ---
    /** FE currently stored: a long, so four slots, least significant first. */
    public static final int DATA_ENERGY = 0;
    /** Buffer size, same shape. */
    public static final int DATA_CAPACITY = 4;
    /** FE/t being generated (positive) or spent (negative). An int, so two slots. */
    public static final int DATA_RATE_LO = 8;
    public static final int DATA_RATE_HI = 9;
    /** How far up the overclock ramp the machine is, in thousandths. */
    public static final int DATA_OVERCLOCK_PERMILLE = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    /** 18 values: one per {@link TransferKind} per {@link RelativeSide}. */
    public static final int DATA_SIDES_START = 12;
    public static final int DATA_SIDES_COUNT = 18;
    /** The auto push/pull flags, as the bitmask {@link SideConfig#autoMask()} keeps them in. */
    public static final int DATA_AUTO = DATA_SIDES_START + DATA_SIDES_COUNT;
    public static final int BASE_DATA_SIZE = DATA_AUTO + 1;

    /** The scale fractions are sent on, since a fraction cannot ride a 16-bit integer. */
    public static final int PERMILLE = 1000;

    // --- button ids ---
    public static final int BUTTON_CYCLE_REDSTONE = 0;
    /** Flips a two-way machine round. Ignored by machines that only run one way. */
    public static final int BUTTON_TOGGLE_MODE = 1;
    /** Moves fluid between the tank and whatever container the cursor carries. */
    public static final int BUTTON_TANK = 2;
    /** Face buttons are {@code BUTTON_SIDES_START + kind * 6 + side}. */
    public static final int BUTTON_SIDES_START = 100;
    /** Auto push/pull toggles are {@code BUTTON_AUTO_START + kind * 2 + (push ? 1 : 0)}. */
    public static final int BUTTON_AUTO_START = 200;

    public static final int UPGRADE_SLOT_X = 8;
    public static final int UPGRADE_SLOT_Y = 78;
    /** The tier slot: the block's own grade, left of the process row rather than in the upgrade queue. */
    public static final int TIER_SLOT_X = 8;
    public static final int TIER_SLOT_Y = 34;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 112;
    private static final int HOTBAR_Y = 170;

    protected final T machine;
    protected final ContainerData data;
    private final ContainerLevelAccess access;
    private int upgradeSlotCount;
    private int tierSlotCount;

    protected MachineMenu(MenuType<?> type,
                          int containerId,
                          Inventory playerInventory,
                          T machine,
                          ContainerData data) {
        super(type, containerId);
        this.machine = machine;
        this.data = data;
        this.access = ContainerLevelAccess.create(machine.getLevel(), machine.getBlockPos());

        addMachineSlots();
        addUpgradeSlots();
        addTierSlot();
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    /** Machine-specific slots. Added first so their indices are stable and low. */
    protected abstract void addMachineSlots();

    /** The block a player must still be standing near for the menu to stay open. */
    protected abstract Block machineBlock();

    /**
     * One slot per upgrade type the machine can use, packed left to right. A machine that takes
     * only energy upgrades shows one slot rather than three it would refuse.
     */
    private void addUpgradeSlots() {
        int shown = 0;
        for (UpgradeType type : UpgradeType.all()) {
            if (!machine.acceptsUpgrade(type)) {
                continue;
            }
            addSlot(new UpgradeSlot(machine.upgradeInventory(), type,
                    UPGRADE_SLOT_X + shown * 18, UPGRADE_SLOT_Y));
            shown++;
        }
        upgradeSlotCount = shown;
    }

    /** How many upgrade slots this menu actually shows. */
    public int upgradeSlotCount() {
        return upgradeSlotCount;
    }

    /** The tier slot, on machines that take a tier. Generators do not, so theirs never appears. */
    private void addTierSlot() {
        if (machine.acceptsTier()) {
            addSlot(new TierSlot(machine.tierSlot(), TIER_SLOT_X, TIER_SLOT_Y));
            tierSlotCount = 1;
        }
    }

    /** The one slot that holds the block's own tier; the screen hints and explains it. */
    public static class TierSlot extends SlotItemHandler {
        TierSlot(IItemHandler handler, int x, int y) {
            super(handler, 0, x, y);
        }
    }

    public MachineTier tier() {
        return machine.tier();
    }

    /** An upgrade slot that knows what belongs in it, so the screen can label and hint it. */
    public static class UpgradeSlot extends SlotItemHandler {
        private final UpgradeType type;

        UpgradeSlot(UpgradeInventory inventory, UpgradeType type, int x, int y) {
            super(inventory, type.ordinal(), x, y);
            this.type = type;
        }

        public UpgradeType upgradeType() {
            return type;
        }
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

    // ------------------------------------------------------------------ synced values

    /** Reassembles an int that was too wide for one 16-bit data slot. */
    protected int wide(int lowIndex) {
        return (data.get(lowIndex + 1) << 16) | (data.get(lowIndex) & 0xFFFF);
    }

    /** Reassembles a long from four slots, least significant first. */
    protected long huge(int firstIndex) {
        long value = 0;
        for (int piece = 3; piece >= 0; piece--) {
            value = (value << 16) | (data.get(firstIndex + piece) & 0xFFFFL);
        }
        return value;
    }

    public long energyStored() {
        return huge(DATA_ENERGY);
    }

    public long energyCapacity() {
        return huge(DATA_CAPACITY);
    }

    /** FE/t the machine is producing (positive) or spending (negative) right now. */
    public int energyRate() {
        return wide(DATA_RATE_LO);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_MODE));
    }

    /**
     * True when this machine's window draws the overclock ramp bar.
     *
     * <p>False for a machine that converts nothing, where the bar could never move — a battery is
     * not slow, it is just full.
     */
    public boolean hasRamp() {
        return true;
    }

    /**
     * True when this machine's window draws the tall gauge on the right.
     *
     * <p>It lives on the menu rather than the screen so that the layout test, which cannot load a
     * client class, knows whether {@link MachineLayout#gauge()} is occupied.
     */
    public boolean hasGauge() {
        return false;
    }

    /**
     * True when this machine's window draws the mode button, for a machine that runs two ways.
     *
     * <p>Here for the same reason as {@link #hasGauge()}: the layout test has to know whether
     * {@link MachineLayout#mode()} is occupied, and it cannot ask a screen.
     */
    public boolean hasModeButton() {
        return false;
    }

    /**
     * True when this machine's window draws the progress arrow.
     *
     * <p>Same reason as {@link #hasGauge()}: the layout test has to know whether
     * {@link MachineLayout#arrow()} is occupied, and it cannot ask a screen.
     */
    public boolean hasProgressArrow() {
        return false;
    }

    /**
     * True when this machine's window has the side configuration button and panel.
     *
     * <p>False for a multiblock controller: its own faces move nothing, the hatches in its shell
     * do, and a panel of six faces that cannot be opened would be a lie.
     */
    public boolean hasSideConfig() {
        return true;
    }

    /**
     * Whether the machine handles this kind at all, so the side panel offers faces only for what
     * it can move: a bank with six item faces to configure would be six lies.
     */
    public boolean supportsKind(TransferKind kind) {
        return switch (kind) {
            case ITEM -> machine.itemsForSide(null) != null;
            case FLUID -> machine.fluidsForSide(null) != null;
            case ENERGY -> true;
            case REDSTONE -> false;
        };
    }

    /** The kinds the side panel has tabs for, in tab order. */
    public List<TransferKind> sideKinds() {
        List<TransferKind> kinds = new ArrayList<>(3);
        for (TransferKind kind : TransferKind.material()) {
            if (supportsKind(kind)) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    /** Whether the side panel starts open: a window opened to set up one face has nothing else to show first. */
    public boolean sidePanelOpenAtStart() {
        return false;
    }

    /** A face nothing can be set on: it looks into a structure. Machines have none. */
    public boolean sideBlocked(RelativeSide side) {
        return false;
    }

    /** Whether the window draws a fluid tank beside the gauge. */
    public boolean hasTank() {
        return false;
    }

    /** The tank's capacity in millibuckets, on a window that has one. */
    public int tankCapacity() {
        return 0;
    }

    /** What the tank holds, for drawing; a window that has one says what and how much. */
    public FluidStack tankFluid() {
        return FluidStack.EMPTY;
    }

    /**
     * Boxes this window draws beyond the shared chrome, so the layout test can see them. A
     * screen with coordinates the test cannot see is a screen that will overlap sooner or later.
     */
    public List<MachineLayout.Box> extraChrome() {
        return List.of();
    }

    /** How far through the current operation, 0 to 1. Zero for machines with no operation. */
    public double progress() {
        return 0;
    }

    /** True when the ramp is a warm-up towards the rating rather than an overclock past it. */
    public boolean usesWarmupRamp() {
        return machine.usesWarmupRamp();
    }

    /** How far up the overclock ramp the machine is, 0 to 1. */
    public double overclockProgress() {
        return Math.clamp(data.get(DATA_OVERCLOCK_PERMILLE) / (double) PERMILLE, 0.0, 1.0);
    }

    public IoMode sideMode(TransferKind kind, RelativeSide side) {
        return IoMode.byOrdinal(data.get(DATA_SIDES_START + kind.ordinal() * 6 + side.ordinal()));
    }

    /** Whether the machine moves this kind itself, as opposed to merely allowing it through a face. */
    public boolean autoEnabled(TransferKind kind, boolean push) {
        return (data.get(DATA_AUTO) & (1 << (kind.ordinal() * 2 + (push ? 1 : 0)))) != 0;
    }

    // ------------------------------------------------------------------ upgrade readouts

    /**
     * The numbers behind the upgrade slots, so a player can see what one more would buy before
     * spending it. Read straight off the machine rather than synced: the tuning comes from the
     * server config, which clients already have a copy of, and the upgrade counts arrive with the
     * slot contents.
     */
    public MachineTuning tuning() {
        return machine.tuning();
    }

    public int installedUpgrades(UpgradeType type) {
        return machine.upgradeInventory().count(type);
    }

    /** How many upgrades of a type this machine counts, for the empty-slot hint. */
    public int maxUpgrades(UpgradeType type) {
        return machine.maxUpgrades(type);
    }

    public long baseCapacity() {
        return machine.baseCapacity();
    }

    public int baseTransferRate() {
        return machine.baseTransferRate();
    }

    public T machine() {
        return machine;
    }

    // ------------------------------------------------------------------ buttons

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CYCLE_REDSTONE) {
            machine.setRedstoneMode(machine.redstoneMode().next());
            return true;
        }
        if (id == BUTTON_TANK) {
            return hasTank() && moveFluidThroughCursor(player);
        }
        if (!hasSideConfig() && id >= BUTTON_SIDES_START) {
            return false;
        }
        int sideButton = id - BUTTON_SIDES_START;
        if (sideButton >= 0 && sideButton < TransferKind.material().length * 6) {
            TransferKind kind = TransferKind.byOrdinal(sideButton / 6);
            if (!supportsKind(kind)) {
                return false;
            }
            RelativeSide side = RelativeSide.byOrdinal(sideButton % 6);
            machine.sideConfig().cycle(kind, side);
            machine.invalidateCapabilitiesOnSideChange();
            return true;
        }
        int autoButton = id - BUTTON_AUTO_START;
        if (autoButton >= 0 && autoButton < TransferKind.material().length * 2) {
            TransferKind kind = TransferKind.byOrdinal(autoButton / 2);
            if (!supportsKind(kind)) {
                return false;
            }
            // No capability invalidation: the faces still expose exactly what they did. Only the
            // machine's own initiative changed, so waking it is enough.
            machine.sideConfig().toggleAuto(kind, autoButton % 2 == 1);
            machine.wake();
            machine.requestAutoIo();
            machine.setChanged();
            return true;
        }
        return false;
    }

    /**
     * A container on the cursor, clicked on the tank: fills it from the tank, or empties it into
     * the tank, whichever the tank's own faces allow. A stack of containers fills one and puts
     * it in the player's inventory, or drops it, like a bucket on the block does. The cursor is
     * replaced by the server's answer; nothing is guessed on the client.
     */
    private boolean moveFluidThroughCursor(Player player) {
        IFluidHandler tank = machine.fluidsForSide(null);
        ItemStack carried = getCarried();
        if (tank == null || carried.isEmpty() || carried.getCapability(Capabilities.FluidHandler.ITEM) == null) {
            return false;
        }
        IItemHandler inventory = new PlayerMainInvWrapper(player.getInventory());
        FluidActionResult result = FluidUtil.tryFillContainerAndStow(carried, tank, inventory, Integer.MAX_VALUE, player, true);
        if (!result.isSuccess()) {
            result = FluidUtil.tryEmptyContainerAndStow(carried, tank, inventory, Integer.MAX_VALUE, player, true);
        }
        if (!result.isSuccess()) {
            return false;
        }
        setCarried(result.getResult());
        return true;
    }

    // ------------------------------------------------------------------ vanilla plumbing

    @Override
    public boolean stillValid(Player player) {
        if (machine instanceof MultiblockControllerBlockEntity controller) {
            // Opened from a hatch at the far end of the box, the controller can be its whole length away.
            return access.evaluate((level, pos) -> level.getBlockState(pos).is(machineBlock())
                    && player.canInteractWithBlock(pos, 4.0 + controller.reach()), true);
        }
        return stillValid(access, player, machineBlock());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = machineSlotCount() + upgradeSlotCount + tierSlotCount;

        if (index < machineSlots) {
            // Machine or upgrade slot -> player inventory.
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            // Player inventory -> the first machine slot that will take it.
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** How many slots {@link #addMachineSlots()} added. */
    protected abstract int machineSlotCount();

    /**
     * The server-side view of a machine, mirrored to the client each tick.
     *
     * <p>Everything every machine reports lives here; a subclass only supplies the handful of
     * values that are its own, through {@link #extra}.
     */
    public abstract static class MachineData<M extends MachineBlockEntity> implements ContainerData {
        protected final M machine;
        private final int size;

        protected MachineData(M machine, int size) {
            this.machine = machine;
            this.size = size;
        }

        @Override
        public final int get(int index) {
            if (index >= DATA_ENERGY && index < DATA_ENERGY + 4) {
                return piece(machine.energyStorage().stored(), index - DATA_ENERGY);
            }
            if (index >= DATA_CAPACITY && index < DATA_CAPACITY + 4) {
                return piece(machine.energyStorage().capacity(), index - DATA_CAPACITY);
            }
            return switch (index) {
                case DATA_RATE_LO -> low(machine.displayedEnergyRate());
                case DATA_RATE_HI -> high(machine.displayedEnergyRate());
                case DATA_OVERCLOCK_PERMILLE ->
                        (int) Math.round(machine.overclockState().progress(machine.tuning()) * PERMILLE);
                case DATA_REDSTONE_MODE -> machine.redstoneMode().ordinal();
                case DATA_AUTO -> machine.sideConfig().autoMask();
                default -> index >= DATA_SIDES_START && index < DATA_SIDES_START + DATA_SIDES_COUNT
                        ? sideMode(index)
                        : extra(index);
            };
        }

        /** Values specific to one machine, at indices from {@code BASE_DATA_SIZE} up. */
        protected abstract int extra(int index);

        private int sideMode(int index) {
            int offset = index - DATA_SIDES_START;
            TransferKind kind = TransferKind.byOrdinal(offset / 6);
            RelativeSide side = RelativeSide.byOrdinal(offset % 6);
            return machine.sideConfig().get(kind, side).ordinal();
        }

        protected static int low(int value) {
            return value & 0xFFFF;
        }

        protected static int high(int value) {
            return value >> 16;
        }

        /** One 16-bit slice of a long, counted from the least significant end. */
        protected static int piece(long value, int index) {
            return (int) ((value >>> (16 * index)) & 0xFFFFL);
        }

        /** The four slices a long travels in, for a machine's own extra long readouts. */
        protected static int pieceOf(long value, int firstIndex, int index) {
            return piece(value, index - firstIndex);
        }

        /** Turns a fraction into something a 16-bit slot can carry. */
        protected static int permille(double fraction) {
            return (int) Math.round(Math.clamp(fraction, 0.0, 1.0) * PERMILLE);
        }

        @Override
        public void set(int index, int value) {
            // Server side is authoritative; the client's copy is the SimpleContainerData.
        }

        @Override
        public final int getCount() {
            return size;
        }
    }
}
