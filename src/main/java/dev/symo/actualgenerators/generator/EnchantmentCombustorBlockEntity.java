package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.EnchantmentCombustorMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Burns the enchantments off an item and keeps the item.
 *
 * <p>A grindstone gives the levels back to the player; this gives them to the grid. Feed it the
 * enchanted junk a mob farm produces, or books bought from a librarian, and it pays out by how
 * much enchantment was on the item — every level of every enchantment counts, so one heavily
 * kitted tool is worth far more than the pile of single-enchantment books that made it.
 *
 * <p>Nothing is destroyed: the stripped item comes out the other side, and an enchanted book
 * comes back as a plain one. That makes the combustor a disenchanter that happens to pay, which
 * is the point — the fuel is the enchantment, not the gear.
 *
 * <p>Enchanted items do not stack, so a batch of them is not a thing that can exist: it takes
 * energy, speed and overclock upgrades but refuses stack upgrades rather than pretending.
 */
public class EnchantmentCombustorBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_INPUT = "Input";
    private static final String KEY_OUTPUT = "Output";
    private static final String KEY_PROGRESS = "Progress";
    private static final String KEY_PROCESSING = "Processing";

    private final ItemStackHandler inputs = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || totalEnchantmentLevels(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            wake();
            setChanged();
        }
    };

    /** Plain handler so the machine can fill it; the sided view is what blocks outside insertion. */
    private final ItemStackHandler outputs = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            wake();
            setChanged();
        }
    };

    /** The item that has already been taken out of the input slot and is being burned. */
    private ItemStack processing = ItemStack.EMPTY;
    private int progress;

    public EnchantmentCombustorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTMENT_COMBUSTOR.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.COMBUSTOR_CAPACITY, 100_000),
                () -> ServerConfig.valueOr(ServerConfig.COMBUSTOR_TRANSFER, 1_000),
                defaultSides());
    }

    /** Gear in from the top, stripped gear out of the bottom, power out of every face. */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.OUTPUT);
        config.set(TransferKind.ITEM, RelativeSide.TOP, IoMode.INPUT);
        config.set(TransferKind.ITEM, RelativeSide.BOTTOM, IoMode.OUTPUT);
        return config;
    }

    // ------------------------------------------------------------------ work

    @Override
    public boolean acceptsUpgrade(UpgradeType type) {
        return type != UpgradeType.STACK;
    }

    /**
     * FE/t before speed scaling, for whatever is in the slot right now.
     *
     * <p>The config names a total per enchantment level and a time to burn it, so the rate is
     * simply one divided by the other — that keeps the number a player is quoted ("2,000 FE per
     * level") true no matter what the machine is chewing on.
     */
    @Override
    protected int baseEnergyPerTick() {
        return baseEnergyPerTick(loaded());
    }

    /** Whatever the rate should be read off: the committed item first, the slot otherwise. */
    private ItemStack loaded() {
        return processing.isEmpty() ? inputs.getStackInSlot(0) : processing;
    }

    private int baseEnergyPerTick(ItemStack stack) {
        int levels = totalEnchantmentLevels(stack);
        if (levels <= 0) {
            return 0;
        }
        long total = (long) energyPerEnchantmentLevel() * levels;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, total / baseTicksPerItem()));
    }

    /** FE/t at the current speed. Sublinear in speed: burning faster wastes the enchantment. */
    public int generatedEnergyPerTick() {
        return tuning().generatorEnergyPerTick(baseEnergyPerTick(), totalSpeedMultiplier(), 1);
    }

    @Override
    public int displayedEnergyRate() {
        return generatedEnergyPerTick();
    }

    public int energyPerEnchantmentLevel() {
        return ServerConfig.valueOr(ServerConfig.COMBUSTOR_FE_PER_ENCHANTMENT_LEVEL, 2_000);
    }

    public int baseTicksPerItem() {
        return ServerConfig.valueOr(ServerConfig.COMBUSTOR_TICKS_PER_ITEM, 100);
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        int duration = ticksForOperation(baseTicksPerItem());

        // A stripped item with nowhere to go blocks the combustor: it must not be paid for a
        // second time, and it must not evaporate, so nothing else happens until there is room.
        if (!processing.isEmpty() && progress >= duration) {
            return deliver();
        }
        // A full buffer means the burn has nowhere to go; idle rather than waste an enchantment.
        if (energy.isFull()) {
            return false;
        }
        if (processing.isEmpty() && !commitItem()) {
            return false;
        }

        energy.generate(generatedEnergyPerTick());
        progress++;
        setChanged();
        if (progress >= duration) {
            deliver();
        }
        return true;
    }

    /**
     * Takes the item out of the input slot before a single FE has been paid for it.
     *
     * <p>Paying first would let a player bank almost a whole item's worth of energy and then pull
     * the item back out still enchanted. Once the burn starts the item belongs to the machine.
     */
    private boolean commitItem() {
        ItemStack input = inputs.getStackInSlot(0);
        if (totalEnchantmentLevels(input) <= 0) {
            progress = 0;
            return false;
        }
        if (!canAccept(stripped(input))) {
            return false;
        }
        processing = inputs.extractItem(0, 1, false);
        progress = 0;
        return !processing.isEmpty();
    }

    /** Hands the stripped item to the output slot. False while that slot has no room for it. */
    private boolean deliver() {
        ItemStack result = stripped(processing);
        if (!outputs.insertItem(0, result, true).isEmpty()) {
            return false;
        }
        outputs.insertItem(0, result, false);
        processing = ItemStack.EMPTY;
        progress = 0;
        setChanged();
        return true;
    }

    /** Whether the output slot can take the stripped item this operation would produce. */
    private boolean canAccept(ItemStack result) {
        ItemStack existing = outputs.getStackInSlot(0);
        if (existing.isEmpty()) {
            return true;
        }
        int limit = Math.min(outputs.getSlotLimit(0), existing.getMaxStackSize());
        return ItemStack.isSameItemSameComponents(existing, result) && existing.getCount() < limit;
    }

    /**
     * The item left once its enchantments are gone.
     *
     * <p>Enchanted books are the one thing that cannot simply be stripped — an enchanted book
     * with nothing on it is a nonsense item — so they come back as the plain book they were
     * written from.
     */
    public static ItemStack stripped(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return new ItemStack(Items.BOOK);
        }
        ItemStack result = stack.copyWithCount(1);
        result.remove(DataComponents.ENCHANTMENTS);
        result.remove(DataComponents.STORED_ENCHANTMENTS);
        return result;
    }

    /**
     * Every level of every enchantment on a stack, applied and stored alike.
     *
     * <p>Levels rather than distinct enchantments, so Sharpness V is worth five times Sharpness I
     * — the reading is "how much enchantment is on this", which is what the machine burns.
     */
    public static int totalEnchantmentLevels(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return sumLevels(stack.get(DataComponents.ENCHANTMENTS))
                + sumLevels(stack.get(DataComponents.STORED_ENCHANTMENTS));
    }

    private static int sumLevels(@Nullable ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            total += enchantments.getLevel(enchantment);
        }
        return total;
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

    /** Levels left in whatever is being burned, for the screen. */
    public int loadedLevels() {
        return totalEnchantmentLevels(loaded());
    }

    /** The item already taken out of the input slot, empty when nothing is being burned. */
    public ItemStack processing() {
        return processing;
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
        return Component.translatable("block.actualgenerators.enchantment_combustor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new EnchantmentCombustorMenu(containerId, playerInventory, this);
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
