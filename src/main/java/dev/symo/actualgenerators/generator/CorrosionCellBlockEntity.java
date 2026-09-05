package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.CorrosionCellMenu;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A galvanic pile that drives copper up the oxidation ladder and harvests the reaction.
 *
 * <p>Copper goes in, the next weathering stage comes out, and FE flows the whole time. Nothing
 * is destroyed — oxidised copper is a perfectly good building block, so the cell converts
 * rather than consumes.
 *
 * <p>It is deliberately a trickle rather than a burst: the rate stays low even fully overclocked,
 * and what makes the copper worth feeding in is how long each stage takes. Since speed pays a
 * generator sublinearly, running one hot burns through the copper for less total energy — batching
 * is the way to scale it up, which is the trade the whole upgrade system is built around.
 *
 * <p>What counts as oxidisable comes from NeoForge's {@code OXIDIZABLES} data map rather than a
 * hardcoded list, so every vanilla copper variant works, and so does any copper another mod or
 * a datapack adds. Waxed copper has no entry there and is rejected for free — which is exactly
 * right, since wax is what stops the reaction.
 */
public class CorrosionCellBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_INPUT = "Input";
    private static final String KEY_OUTPUT = "Output";
    private static final String KEY_PROGRESS = "Progress";
    private static final String KEY_PROCESSING = "Processing";

    private final ItemStackHandler inputs = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || nextStageOf(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            wake();
            setChanged();
        }
    };

    /**
     * Plain handler so the machine can fill it. Insertion from outside is blocked by the sided
     * view the capability exposes, not here.
     */
    private final ItemStackHandler outputs = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            wake();
            setChanged();
        }
    };

    /** The batch that has already been taken out of the input slot and is being worked on. */
    private ItemStack processing = ItemStack.EMPTY;
    private int progress;

    public CorrosionCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORROSION_CELL.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.CORROSION_CELL_CAPACITY, 100_000),
                () -> ServerConfig.valueOr(ServerConfig.CORROSION_CELL_TRANSFER, 1_000),
                defaultSides());
    }

    /** Copper in from the top, results out of the bottom, power out of every face. */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.OUTPUT);
        config.set(TransferKind.ITEM, RelativeSide.TOP, IoMode.INPUT);
        config.set(TransferKind.ITEM, RelativeSide.BOTTOM, IoMode.OUTPUT);
        return config;
    }

    // ------------------------------------------------------------------ work

    @Override
    protected int baseEnergyPerTick() {
        return ServerConfig.valueOr(ServerConfig.CORROSION_CELL_FE_PER_TICK, 33);
    }

    public int baseTicksPerStage() {
        return ServerConfig.valueOr(ServerConfig.CORROSION_CELL_TICKS_PER_STAGE, 1200);
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        int duration = ticksForOperation(baseTicksPerStage());

        // A finished batch with nowhere to go blocks the cell: it must not be paid for a second
        // time, and it must not evaporate, so nothing else happens until the output has room.
        if (!processing.isEmpty() && progress >= duration) {
            return deliver();
        }
        // A full buffer means the reaction has nowhere to go; idle rather than waste copper.
        if (energy.isFull()) {
            return false;
        }
        if (processing.isEmpty() && !commitBatch()) {
            return false;
        }

        energy.generate(generatedEnergyPerTick(processing.getCount()));
        progress++;
        setChanged();
        if (progress >= duration) {
            deliver();
        }
        return true;
    }

    /**
     * Takes the copper out of the input slot before a single FE has been paid for it.
     *
     * <p>Every furnace-shaped machine has to decide when the input is spent, and paying first is
     * an exploit: a player could bank almost all of an operation's energy and then pull the stack
     * out -- or pull the stack upgrades that set the batch size -- and be paid nine items' worth
     * for one. The batch is committed up front, at the size it was committed at, and nothing that
     * happens afterwards can shrink it.
     */
    private boolean commitBatch() {
        int batch = currentBatch();
        if (batch <= 0) {
            return false;
        }
        processing = inputs.extractItem(0, batch, false);
        progress = 0;
        return !processing.isEmpty();
    }

    /** Hands the finished batch to the output slot. False while that slot has no room for it. */
    private boolean deliver() {
        Item result = nextStageOf(processing);
        if (result == null) {
            // The input slot only accepts copper that oxidises, so this should be unreachable.
            // Hand the batch back rather than destroy it if it ever is not.
            processing = inputs.insertItem(0, processing, false);
            progress = 0;
            setChanged();
            return processing.isEmpty();
        }

        ItemStack finished = new ItemStack(result, processing.getCount());
        if (!outputs.insertItem(0, finished, true).isEmpty()) {
            return false;
        }
        outputs.insertItem(0, finished, false);
        processing = ItemStack.EMPTY;
        progress = 0;
        setChanged();
        return true;
    }

    /**
     * FE/t at the current speed and batch. Linear in batch, sublinear in speed — overclocking
     * this cell buys burst power and wastes copper doing it.
     */
    public int generatedEnergyPerTick(int batchSize) {
        return tuning().generatorEnergyPerTick(baseEnergyPerTick(), totalSpeedMultiplier(), batchSize);
    }

    /** Items one operation would advance right now, bounded by the input and the room to put it. */
    public int currentBatch() {
        ItemStack input = inputs.getStackInSlot(0);
        Item result = nextStageOf(input);
        if (result == null) {
            return 0;
        }
        return Math.max(0, Math.min(Math.min(maxBatch(), input.getCount()), roomFor(result)));
    }

    @Override
    public int displayedEnergyRate() {
        int batch = processing.isEmpty() ? currentBatch() : processing.getCount();
        return batch <= 0 ? 0 : generatedEnergyPerTick(batch);
    }

    /** The batch already taken out of the input slot, empty when the cell is between operations. */
    public ItemStack processing() {
        return processing;
    }

    /** How many of {@code result} the output slot can still take. */
    private int roomFor(Item result) {
        ItemStack existing = outputs.getStackInSlot(0);
        int limit = Math.min(outputs.getSlotLimit(0), new ItemStack(result).getMaxStackSize());
        if (existing.isEmpty()) {
            return limit;
        }
        return existing.is(result) ? Math.max(0, limit - existing.getCount()) : 0;
    }

    /** The next weathering stage of a stack, or null if it does not oxidise. */
    public static @Nullable Item nextStageOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Block block = Block.byItem(stack.getItem());
        if (block == Blocks.AIR) {
            return null;
        }
        Block next = DataMapHooks.getNextOxidizedStage(block);
        if (next == null) {
            return null;
        }
        Item nextItem = next.asItem();
        return nextItem == Items.AIR ? null : nextItem;
    }

    // ------------------------------------------------------------------ inventory access

    public IItemHandler inputHandler() {
        return inputs;
    }

    public IItemHandler outputHandler() {
        return outputs;
    }

    public int progress() {
        return progress;
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
        return Component.translatable("block.actualgenerators.corrosion_cell");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CorrosionCellMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INPUT, inputs.serializeNBT(registries));
        tag.put(KEY_OUTPUT, outputs.serializeNBT(registries));
        tag.put(KEY_PROCESSING, processing.saveOptional(registries));
        tag.putInt(KEY_PROGRESS, progress);
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
        progress = tag.getInt(KEY_PROGRESS);
    }
}
