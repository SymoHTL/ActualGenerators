package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineItemHandler;
import dev.symo.actualgenerators.machine.SidedEnergyWrapper;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.menu.AnnihilationFurnaceMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import dev.symo.actualgenerators.registry.ModTags;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Mass into energy. Feed it blocks, any blocks, and it pays by how much block they were.
 *
 * <p>The endgame trash can. A block's mass is read off the one number every block already
 * carries, its hardness, so cobblestone is worth a little, obsidian a lot, and a pack's own
 * blocks are worth whatever they are made of without anyone writing a recipe. Only things that
 * are blocks burn; ingots, tools and food have no place in a furnace built for rubble, and blocks
 * the tag {@code actualgenerators:annihilation_refused} names are turned away whatever they weigh.
 *
 * <p>It is a multiblock, a tall one — seven high at the least — and the box is the grade: every
 * block inside the shell is one more item per operation, so a 5×7×5 with forty-five blocks
 * inside chews forty-five times what a 3×7×3 does in the same forty ticks and makes forty-five
 * times the FE/t. There are no upgrades to fit and no tier slot; there is a bigger box.
 *
 * <p>It runs on heat, and the heat is what stands on its floor: Corium, a bucket per floor
 * block, is full heat; lava is a poor second; nothing is no heat, and a cold furnace eats nothing.
 * The heat and the load make its <b>efficiency</b>, which scales what every item pays. Load is how
 * full the slots are past the batch — a furnace can only eat a batch at a time, and the more it
 * is choked with beyond that the less each item is worth, so feeding it is something to manage
 * rather than something to bury it under. Keep the slots at the batch and the rating is what you
 * get.
 *
 * <p>The items are gone the moment an operation starts and the energy for the whole lot is paid
 * out evenly over its duration, so nothing can be pulled back out half-burned and nothing is
 * minted twice.
 */
public class AnnihilationFurnaceBlockEntity extends MultiblockControllerBlockEntity implements MenuProvider {
    public static final int INPUT_SLOTS = 4;

    private static final String KEY_INPUTS = "Inputs";
    private static final String KEY_PENDING = "Pending";
    private static final String KEY_PROGRESS = "Progress";
    private static final String KEY_DURATION = "Duration";
    private static final String KEY_RATE = "Rate";
    private static final String KEY_HEAT = "Heat";

    private final MachineItemHandler inputs = new MachineItemHandler(INPUT_SLOTS, this::inputSlotLimit, this::onInputsChanged) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || energyFor(stack) > 0;
        }
    };

    /** Energy hatches hand this out: the buffer, one way. */
    private final IEnergyStorage hatchEnergy = new SidedEnergyWrapper(energy, IoMode.OUTPUT);

    /** FE still owed for the lot that has already been annihilated. */
    private long pending;
    private int progress;
    private int duration;
    /** FE/t the running operation pays, for the readouts. */
    private int rate;
    /** What the floor is worth, in thousandths; read off the box on every walk over it. */
    private int heatPermille;

    public AnnihilationFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANNIHILATION_FURNACE.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.ANNIHILATION_CAPACITY, 1_000_000L),
                () -> ServerConfig.valueOr(ServerConfig.ANNIHILATION_TRANSFER, 10_000));
    }

    // ------------------------------------------------------------------ what a block is worth

    /**
     * FE for one of these, or zero for something the furnace will not take.
     *
     * <p>Hardness times the configured FE per point, clamped between the configured floor and
     * ceiling. Blocks that cannot be broken have no mass to speak of and are refused, as is
     * anything the refusal tag names.
     */
    public static long energyFor(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return 0;
        }
        Block block = blockItem.getBlock();
        if (block.defaultBlockState().is(ModTags.Blocks.ANNIHILATION_REFUSED)) {
            return 0;
        }
        float hardness = block.defaultDestroyTime();
        if (hardness < 0) {
            return 0;
        }
        long perPoint = ServerConfig.valueOr(ServerConfig.ANNIHILATION_FE_PER_HARDNESS, 20_000);
        long floor = ServerConfig.valueOr(ServerConfig.ANNIHILATION_MIN_FE_PER_BLOCK, 10_000L);
        long ceiling = ServerConfig.valueOr(ServerConfig.ANNIHILATION_MAX_FE_PER_BLOCK, 1_000_000L);
        return Math.clamp(Math.round((double) hardness * perPoint), Math.min(floor, ceiling), ceiling);
    }

    public static int baseTicksPerOperation() {
        return ServerConfig.valueOr(ServerConfig.ANNIHILATION_TICKS_PER_OPERATION, 40);
    }

    // ------------------------------------------------------------------ the structure's say

    @Override
    public int maxSize(Extent extent) {
        return extent == Extent.HEIGHT
                ? ServerConfig.valueOr(ServerConfig.ANNIHILATION_MAX_HEIGHT, 15)
                : ServerConfig.valueOr(ServerConfig.ANNIHILATION_MAX_SIZE, 7);
    }

    /** A tall box: the heat is on the floor and the work is stacked above it. */
    @Override
    public int minSize(Extent extent) {
        return extent == Extent.HEIGHT ? ServerConfig.valueOr(ServerConfig.ANNIHILATION_MIN_HEIGHT, 7) : 3;
    }

    /** Air anywhere; on the floor, any heating fluid too, poured or spread. */
    @Override
    public boolean floorTakesFluid() {
        return true;
    }

    @Override
    public boolean interiorAccepts(BlockPos pos, BlockState state, boolean floor) {
        return state.isAir() || (floor && heatOf(state.getFluidState()) > 0);
    }

    /** Items per operation: one per block of interior, times the config's multiplier. */
    @Override
    public int maxBatch() {
        return Math.max(1, interiorVolume() * ServerConfig.valueOr(ServerConfig.ANNIHILATION_ITEMS_PER_INTERIOR_BLOCK, 1));
    }

    @Override
    public boolean acceptsUpgrade(UpgradeType type) {
        // The box is the upgrade.
        return false;
    }

    @Override
    public @Nullable IItemHandler hatchItems() {
        return inputs;
    }

    @Override
    public @Nullable IEnergyStorage hatchEnergy() {
        return hatchEnergy;
    }

    @Override
    protected void onStructureChanged() {
        // A smaller box mid-operation still owes what it took; the slot limit follows the batch.
        setChanged();
    }

    /** Reads the floor: every source block on it is worth its fluid's share of full heat. */
    @Override
    protected void onStructureValidated(ServerLevel level) {
        int measured = 0;
        Structure box = structure();
        if (box != null) {
            long total = 0;
            for (BlockPos pos : box.floor()) {
                FluidState fluid = level.getFluidState(pos);
                if (fluid.isSource()) {
                    total += heatOf(fluid);
                }
            }
            measured = (int) (total / box.floorArea());
        }
        if (measured != heatPermille) {
            heatPermille = measured;
            wake();
            setChanged();
            syncToClients();
        }
    }

    // ------------------------------------------------------------------ heat and load

    /** What one floor block of a fluid is worth, in thousandths of full heat; zero for anything else. */
    public static int heatOf(FluidState fluid) {
        if (fluid.is(ModTags.Fluids.ANNIHILATION_HEAT_STRONG)) {
            return ServerConfig.valueOr(ServerConfig.ANNIHILATION_HEAT_STRONG_PERMILLE, 1000);
        }
        if (fluid.is(ModTags.Fluids.ANNIHILATION_HEAT_WEAK)) {
            return ServerConfig.valueOr(ServerConfig.ANNIHILATION_HEAT_WEAK_PERMILLE, 400);
        }
        return 0;
    }

    /** The floor's heat, in thousandths: a bucket of corium on every floor block is a thousand. */
    public int heatPermille() {
        return heatPermille;
    }

    /** Items in the slots right now. */
    public int held() {
        int total = 0;
        for (int slot = 0; slot < inputs.getSlots(); slot++) {
            total += inputs.getStackInSlot(slot).getCount();
        }
        return total;
    }

    /**
     * How well the slots are managed, in thousandths: a thousand up to a batch held, falling in a
     * straight line to the configured floor with the slots stuffed full.
     */
    public int loadPermille() {
        return loadPermille(held());
    }

    public int loadPermille(int held) {
        int batch = maxBatch();
        if (held <= batch) {
            return 1000;
        }
        long capacity = (long) INPUT_SLOTS * inputSlotLimit();
        int floor = ServerConfig.valueOr(ServerConfig.ANNIHILATION_LOAD_FLOOR_PERMILLE, 250);
        if (capacity <= batch) {
            return floor;
        }
        long over = Math.min(held, capacity) - batch;
        return (int) (1000 - (1000 - floor) * over / (capacity - batch));
    }

    /** Heat times load: what every item pays is this share of its worth. */
    @Override
    public int efficiencyPermille() {
        return (int) ((long) heatPermille * loadPermille() / 1000);
    }

    // ------------------------------------------------------------------ work

    @Override
    protected int baseEnergyPerTick() {
        return rate;
    }

    @Override
    public int displayedEnergyRate() {
        return pending > 0 ? rate : 0;
    }

    @Override
    public double progressFraction() {
        return duration > 0 ? progress / (double) duration : 0;
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isFormed()) {
            return false;
        }
        if (pending <= 0) {
            if (energy.isFull() || !commit()) {
                return false;
            }
        }
        return payOut();
    }

    /**
     * Takes one operation's worth out of the slots and works out what it is owed.
     *
     * <p>Consume first, like every other machine: once the lot is gone it belongs to the furnace,
     * so nothing can be paid for and then pulled back out. The lot is a batch across all four
     * slots, mixed freely — the price is per item, not per operation — and the efficiency the
     * furnace had at the moment it took the lot is the efficiency the lot is paid at. A cold
     * furnace takes nothing.
     */
    private boolean commit() {
        int efficiency = efficiencyPermille();
        if (efficiency <= 0) {
            return false;
        }
        int room = maxBatch();
        long total = 0;
        for (int slot = 0; slot < inputs.getSlots() && room > 0; slot++) {
            ItemStack stack = inputs.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            long each = energyFor(stack);
            if (each <= 0) {
                // A config reload can make a slot's contents worthless; leave them for the player.
                continue;
            }
            ItemStack taken = inputs.extractItem(slot, Math.min(room, stack.getCount()), false);
            total += each * taken.getCount();
            room -= taken.getCount();
        }
        if (total <= 0) {
            return false;
        }
        pending = Math.max(1, total * efficiency / 1000);
        progress = 0;
        duration = ticksForOperation(baseTicksPerOperation());
        rate = (int) Math.min(Integer.MAX_VALUE, Math.max(1, pending / duration));
        setChanged();
        return true;
    }

    /**
     * Pays the tick's share of what is owed. The share is what is left over what is left of the
     * duration, so the last tick clears the remainder exactly and the total is never off by a
     * rounding. A full buffer stalls the payment rather than losing it.
     */
    private boolean payOut() {
        int remaining = Math.max(1, duration - progress);
        long due = pending / remaining;
        long accepted = energy.generate(due);
        pending -= accepted;
        if (accepted < due) {
            return false;
        }
        progress++;
        if (progress >= duration && pending <= 0) {
            progress = 0;
            duration = 0;
            pending = 0;
        }
        setChanged();
        return true;
    }

    private void onInputsChanged() {
        wake();
        setChanged();
    }

    public IItemHandler inputHandler() {
        return inputs;
    }

    /** FE the running operation still has to pay; zero between operations. */
    public long pending() {
        return pending;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        for (int slot = 0; slot < inputs.getSlots(); slot++) {
            ItemStack stack = inputs.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.annihilation_furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AnnihilationFurnaceMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INPUTS, inputs.serializeNBT(registries));
        tag.putLong(KEY_PENDING, pending);
        tag.putInt(KEY_PROGRESS, progress);
        tag.putInt(KEY_DURATION, duration);
        tag.putInt(KEY_RATE, rate);
        tag.putInt(KEY_HEAT, heatPermille);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_INPUTS)) {
            inputs.deserializeNBT(registries, tag.getCompound(KEY_INPUTS));
        }
        pending = tag.getLong(KEY_PENDING);
        progress = tag.getInt(KEY_PROGRESS);
        duration = tag.getInt(KEY_DURATION);
        rate = tag.getInt(KEY_RATE);
        heatPermille = tag.getInt(KEY_HEAT);
    }
}
