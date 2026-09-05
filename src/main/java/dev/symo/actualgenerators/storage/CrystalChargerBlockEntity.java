package dev.symo.actualgenerators.storage;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.item.FluxCrystalItem;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.CrystalChargerMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The bulk charger and discharger for flux crystals.
 *
 * <p>It takes one crystal at a time out of whatever stack is waiting and drops each finished one
 * straight into the output slot, so a stack feeds through steadily rather than disappearing for
 * minutes and coming back all at once. The cost to the server is one component write per tick
 * whatever is queued behind it.
 *
 * <p>It runs one way or the other, not both. In charge mode it spends its buffer to fill crystals;
 * in discharge mode it empties them back into its buffer. Partly-charged crystals are welcome in
 * either direction — they are simply taken the rest of the way, which is how a mixed pile gets
 * normalised back into stackable fulls or stackable empties.
 *
 * <p>Nothing is lost in either direction. That is the deliberate contrast with the
 * {@link SurgeBankBlockEntity}: a bank is enormous and leaks, a crystal is dense and does not.
 * The trade is throughput — a crystal moves at the speed of an item, not a wire.
 *
 * <p>So it takes energy upgrades only. There is no conversion here to run faster, only a transfer,
 * and the rate of that transfer is exactly the machine's FE throughput. Speed and overclock have
 * nothing to act on, and a stack upgrade would only make the output land in lumps.
 */
public class CrystalChargerBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_INPUT = "Input";
    private static final String KEY_OUTPUT = "Output";
    private static final String KEY_PROCESSING = "Processing";
    private static final String KEY_MOVED = "Moved";
    private static final String KEY_NEEDED = "Needed";
    private static final String KEY_STARTED = "Started";
    private static final String KEY_DISCHARGING = "Discharging";

    private final ItemStackHandler inputs = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || stack.getItem() instanceof FluxCrystalItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            wake();
            setChanged();
        }
    };

    private final ItemStackHandler outputs = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            wake();
            setChanged();
        }
    };

    /** The one crystal taken out of the input slot, its charge written in as the work goes on. */
    private ItemStack processing = ItemStack.EMPTY;
    /** FE moved into or out of that crystal so far, and what it needs in total. */
    private long moved;
    private long needed;

    /** The crystal's charge when it was committed, so the running total writes back exactly. */
    private long started;

    private boolean discharging;

    public CrystalChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_CHARGER.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.CRYSTAL_CHARGER_CAPACITY, 400_000L),
                () -> ServerConfig.valueOr(ServerConfig.CRYSTAL_CHARGER_TRANSFER, 5_000),
                defaultSides());
    }

    /**
     * Crystals in from the top, finished ones out of the bottom, power in from every face but the
     * front, which is where a discharged crystal's energy comes back out.
     *
     * <p>Not both ways on every face: a charger that pushes its own buffer at a neighbour is
     * fighting itself for the energy it is trying to store. The one output face is the deliberate
     * one, and the player moves it.
     */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.INPUT);
        config.set(TransferKind.ENERGY, RelativeSide.FRONT, IoMode.OUTPUT);
        config.set(TransferKind.ITEM, RelativeSide.TOP, IoMode.INPUT);
        config.set(TransferKind.ITEM, RelativeSide.BOTTOM, IoMode.OUTPUT);
        return config;
    }

    // ------------------------------------------------------------------ work

    @Override
    public boolean acceptsUpgrade(UpgradeType type) {
        return type == UpgradeType.ENERGY;
    }

    @Override
    protected int baseEnergyPerTick() {
        // Its rate is its throughput, not a separate consumption figure.
        return 0;
    }

    /** FE it moves per tick: whatever its faces can carry, which is what energy upgrades raise. */
    public int ratePerTick() {
        return energy.getMaxExtract();
    }

    @Override
    public int displayedEnergyRate() {
        if (processing.isEmpty()) {
            return 0;
        }
        int step = (int) Math.min(ratePerTick(), needed - moved);
        return discharging ? step : -step;
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        if (!processing.isEmpty() && moved >= needed) {
            return deliver();
        }
        if (processing.isEmpty() && !commit()) {
            return false;
        }

        long step = Math.min(ratePerTick(), needed - moved);
        step = discharging ? Math.min(step, energy.room()) : Math.min(step, energy.stored());
        if (step <= 0) {
            return false;
        }

        if (discharging) {
            energy.generate(step);
        } else {
            energy.consume(step);
        }
        moved += step;
        FluxCrystalItem.setEnergy(processing, discharging ? started - moved : started + moved);
        setChanged();

        if (moved >= needed) {
            deliver();
        }
        return true;
    }

    /**
     * Takes one crystal out of the input slot before a single FE moves.
     *
     * <p>One at a time, even when a whole stack is waiting: a finished crystal drops into the
     * output slot as soon as it is done, so a stack trickles through instead of disappearing for
     * minutes and landing in a lump. It costs nothing to do it this way — the throughput is the FE
     * rate either way, and a crystal is one component write per tick whatever is queued behind it.
     *
     * <p>The crystal leaves the slot first, as in every other machine here: whatever is being paid
     * for has to be committed up front, or a player can bank most of a charge and pull it back out.
     */
    private boolean commit() {
        ItemStack input = inputs.getStackInSlot(0);
        if (input.isEmpty() || !(input.getItem() instanceof FluxCrystalItem)) {
            return false;
        }
        long charge = FluxCrystalItem.energyOf(input);
        long need = discharging ? charge : FluxCrystalItem.capacity() - charge;
        if (need <= 0 || !hasRoomInOutput(input)) {
            return false;
        }

        processing = inputs.extractItem(0, 1, false);
        started = charge;
        moved = 0;
        needed = need;
        return !processing.isEmpty();
    }

    /** Hands the finished crystal to the output slot. False while that slot has no room. */
    private boolean deliver() {
        FluxCrystalItem.setEnergy(processing, discharging ? 0 : FluxCrystalItem.capacity());
        if (!outputs.insertItem(0, processing, true).isEmpty()) {
            return false;
        }
        outputs.insertItem(0, processing, false);
        processing = ItemStack.EMPTY;
        started = 0;
        moved = 0;
        needed = 0;
        setChanged();
        return true;
    }

    /** Whether a finished crystal would fit in the output slot, which is what gates the next one. */
    private boolean hasRoomInOutput(ItemStack template) {
        ItemStack finished = FluxCrystalItem.withEnergy(template, 1, discharging ? 0 : FluxCrystalItem.capacity());
        return outputs.insertItem(0, finished, true).isEmpty();
    }

    // ------------------------------------------------------------------ mode

    /** True when it is emptying crystals into its buffer rather than filling them from it. */
    public boolean isDischarging() {
        return discharging;
    }

    /**
     * Flips the machine round, handing back whatever it was part way through.
     *
     * <p>A half-charged batch is returned to the input slot rather than finished the old way: the
     * player has just said they want it going the other direction, and a partial crystal is a
     * perfectly good input for either mode.
     */
    public void toggleMode() {
        discharging = !discharging;
        if (!processing.isEmpty()) {
            processing = inputs.insertItem(0, processing, false);
            if (!processing.isEmpty() && level != null) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                        processing);
                processing = ItemStack.EMPTY;
            }
            started = 0;
            moved = 0;
            needed = 0;
        }
        wake();
        setChanged();
    }

    // ------------------------------------------------------------------ readouts

    /** 0 to 1 through the current batch. */
    public double progress() {
        return needed <= 0 ? 0.0 : Math.clamp(moved / (double) needed, 0.0, 1.0);
    }

    /** The stack being worked on, empty between batches. */
    public ItemStack processing() {
        return processing;
    }

    // ------------------------------------------------------------------ inventory access

    public IItemHandler inputHandler() {
        return inputs;
    }

    public IItemHandler outputHandler() {
        return outputs;
    }

    @Override
    protected @Nullable IItemHandler autoInputHandler() {
        return inputs;
    }

    @Override
    protected @Nullable IItemHandler autoOutputHandler() {
        return outputs;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        dropAll(level, pos, inputs);
        dropAll(level, pos, outputs);
        if (!processing.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), processing);
        }
    }

    private static void dropAll(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.crystal_charger");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CrystalChargerMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INPUT, inputs.serializeNBT(registries));
        tag.put(KEY_OUTPUT, outputs.serializeNBT(registries));
        tag.put(KEY_PROCESSING, processing.saveOptional(registries));
        tag.putLong(KEY_MOVED, moved);
        tag.putLong(KEY_NEEDED, needed);
        tag.putLong(KEY_STARTED, started);
        tag.putBoolean(KEY_DISCHARGING, discharging);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_INPUT)) {
            inputs.deserializeNBT(registries, tag.getCompound(KEY_INPUT));
        }
        if (tag.contains(KEY_OUTPUT)) {
            outputs.deserializeNBT(registries, tag.getCompound(KEY_OUTPUT));
        }
        processing = ItemStack.parseOptional(registries, tag.getCompound(KEY_PROCESSING));
        moved = tag.getLong(KEY_MOVED);
        needed = tag.getLong(KEY_NEEDED);
        started = tag.getLong(KEY_STARTED);
        discharging = tag.getBoolean(KEY_DISCHARGING);
    }
}
