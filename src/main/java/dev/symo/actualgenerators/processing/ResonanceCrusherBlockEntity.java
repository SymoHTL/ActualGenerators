package dev.symo.actualgenerators.processing;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.MachineItemHandler;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.ResonanceCrusherMenu;
import dev.symo.actualgenerators.recipe.CrushingRecipe;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import dev.symo.actualgenerators.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shakes a material apart at the frequency it resonates at.
 *
 * <p>Every material has one, and the crusher does not know it until it has taken something apart
 * the slow way: the first run on an unfamiliar material is a <em>calibration</em>, which takes
 * several times as long and yields nothing but the plain result. After that the frequency is
 * written down, the material is processed at full speed, and the crusher shakes a bonus loose on
 * top of the ordinary yield.
 *
 * <p>Calibrations are per material, they survive being broken and replaced, and a config card
 * copies them — so the second crusher on a base can be born knowing what the first one learned,
 * and a player who has done the work never does it twice.
 */
public class ResonanceCrusherBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_INPUT = "Input";
    private static final String KEY_OUTPUT = "Output";
    private static final String KEY_PROGRESS = "Progress";
    private static final String KEY_TUNED = "Tuned";

    /** Both slots grow with the batch; see {@link MachineItemHandler} for why a slot is not a stack. */
    private final MachineItemHandler inputs = new MachineItemHandler(1, this::inputSlotLimit, this::slotChanged);

    /** Plain handler so the machine can fill it; the sided view is what blocks outside insertion. */
    private final MachineItemHandler outputs = new MachineItemHandler(1, this::outputSlotLimit, this::slotChanged);

    /** The most any loaded crushing recipe takes and gives per batch item, so the slots fit the worst case. */
    private @Nullable RecipeManager sizedFor;
    private int mostInputs = 1;
    private int mostOutputs = 1;

    /** Caches the last recipe looked up, so a machine chewing a stack is not a search every tick. */
    private final RecipeManager.CachedCheck<SingleRecipeInput, CrushingRecipe> recipeCheck =
            RecipeManager.createCheck(ModRecipes.CRUSHING.get());

    /** Frequencies this crusher has worked out, by recipe. Ordered so a config card is stable. */
    private final Set<ResourceLocation> tuned = new LinkedHashSet<>();

    private int progress;
    /** What the progress on the clock belongs to, so swapping the input cannot inherit it. */
    private @Nullable ResourceLocation workingOn;

    public ResonanceCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONANCE_CRUSHER.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.CRUSHER_CAPACITY, 100_000),
                () -> ServerConfig.valueOr(ServerConfig.CRUSHER_TRANSFER, 1_000),
                defaultSides());
    }

    /** Material in from the top, dust out of the bottom, power in from every other face. */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.INPUT);
        config.set(TransferKind.ITEM, RelativeSide.TOP, IoMode.INPUT);
        config.set(TransferKind.ITEM, RelativeSide.BOTTOM, IoMode.OUTPUT);
        return config;
    }

    // ------------------------------------------------------------------ balance

    @Override
    protected int baseEnergyPerTick() {
        return ServerConfig.valueOr(ServerConfig.CRUSHER_FE_PER_TICK, 120);
    }

    /** Ticks one operation takes at base speed, for whatever the crusher is chewing on. */
    public int baseTicksPerOperation() {
        RecipeHolder<CrushingRecipe> holder = loadedRecipe();
        return holder == null
                ? ServerConfig.valueOr(ServerConfig.CRUSHER_TICKS, 100)
                : baseTicksFor(holder, tuned.contains(holder.id()));
    }

    /** The same, for a recipe already in hand — a calibration takes several times as long. */
    private int baseTicksFor(RecipeHolder<CrushingRecipe> holder, boolean known) {
        int recipeTicks = holder.value().ticks();
        int ticks = recipeTicks > 0 ? recipeTicks : ServerConfig.valueOr(ServerConfig.CRUSHER_TICKS, 100);
        return known ? ticks : ticks * calibrationMultiplier();
    }

    public static int calibrationMultiplier() {
        return ServerConfig.valueOr(ServerConfig.CRUSHER_CALIBRATION_MULTIPLIER, 4);
    }

    /** The chance, per item in a batch, that a tuned crusher shakes an extra one loose. */
    public static double bonusChance() {
        return ServerConfig.valueOr(ServerConfig.CRUSHER_BONUS_PERMILLE, 250) / 1000.0;
    }

    @Override
    public int displayedEnergyRate() {
        // Negative: this one spends power rather than making it.
        return isWorking() ? -currentEnergyPerTick(Math.max(1, currentBatch())) : 0;
    }

    // ------------------------------------------------------------------ work

    /** A processing machine: a tier makes it crush faster and in bigger lots at the same FE per operation. */
    @Override
    public boolean acceptsTier() {
        return true;
    }

    private void slotChanged() {
        wake();
        setChanged();
    }

    @Override
    protected int inputsPerBatchItem() {
        sizeFromRecipes();
        return mostInputs;
    }

    /** One more than the biggest result, for the bonus a tuned run can shake loose on every item. */
    @Override
    protected int outputsPerBatchItem() {
        sizeFromRecipes();
        return mostOutputs + 1;
    }

    /** Reads the loaded crushing recipes once per reload, so a pack's fattest recipe still fits a slot. */
    private void sizeFromRecipes() {
        if (level == null || level.getRecipeManager() == sizedFor) {
            return;
        }
        int inputs = 1;
        int outputs = 1;
        for (RecipeHolder<CrushingRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.CRUSHING.get())) {
            inputs = Math.max(inputs, holder.value().inputCount());
            outputs = Math.max(outputs, holder.value().result().getCount());
        }
        mostInputs = inputs;
        mostOutputs = outputs;
        sizedFor = level.getRecipeManager();
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        RecipeHolder<CrushingRecipe> holder = recipeFor(level, inputs.getStackInSlot(0));
        if (holder == null) {
            progress = 0;
            workingOn = null;
            return false;
        }
        // Progress belongs to the material it was earned on. Swapping cobble for iron ore at the
        // last tick must not finish the iron for free.
        if (!holder.id().equals(workingOn)) {
            progress = 0;
            workingOn = holder.id();
        }

        boolean known = tuned.contains(holder.id());
        int batch = batchFor(holder.value(), known);
        if (batch <= 0) {
            return false;
        }
        int cost = currentEnergyPerTick(batch);
        if (!energy.hasEnergy(cost)) {
            return false;
        }

        energy.consume(cost);
        progress++;
        setChanged();
        if (progress >= ticksForOperation(baseTicksFor(holder, known))) {
            finish(level, holder, batch, known);
        }
        return true;
    }

    /**
     * Hands over the results and, if this was a calibration, writes the frequency down.
     *
     * <p>The calibration run itself pays nothing extra: the crusher was working out what the
     * material is, not shaking it apart properly. Everything after it does.
     */
    private void finish(ServerLevel level, RecipeHolder<CrushingRecipe> holder, int batch, boolean known) {
        CrushingRecipe recipe = holder.value();

        int count = recipe.result().getCount() * batch;
        if (known) {
            count += bonusRolls(level, batch);
        }

        inputs.extractItem(0, recipe.inputCount() * batch, false);
        ItemStack leftover = outputs.insertItem(0, recipe.result().copyWithCount(count), false);
        if (!leftover.isEmpty()) {
            // The batch was sized to fit; if a recipe or an upgrade ever makes that untrue, the
            // items go on the floor rather than quietly nowhere.
            Containers.dropItemStack(level, getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), leftover);
        }
        if (!known) {
            tuned.add(holder.id());
        }
        progress = 0;
        setChanged();
    }

    /** One roll per item in the batch, so a bigger batch is not a better bonus per item. */
    private int bonusRolls(ServerLevel level, int batch) {
        double chance = bonusChance();
        int extra = 0;
        for (int roll = 0; roll < batch; roll++) {
            if (level.getRandom().nextDouble() < chance) {
                extra++;
            }
        }
        return extra;
    }

    /**
     * How many items this operation can take on: what the input holds, what the batch allows, and
     * what the output slot has room for — including room for a bonus that might all land at once.
     */
    private int batchFor(CrushingRecipe recipe, boolean known) {
        int available = inputs.getStackInSlot(0).getCount() / Math.max(1, recipe.inputCount());
        int batch = Math.min(maxBatch(), available);
        if (batch <= 0) {
            return 0;
        }

        ItemStack result = recipe.result();
        ItemStack existing = outputs.getStackInSlot(0);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, result)) {
            return 0;
        }
        int room = outputs.limitFor(0, result) - existing.getCount();
        int perItem = result.getCount() + (known ? 1 : 0);
        return Math.min(batch, room / Math.max(1, perItem));
    }

    private @Nullable RecipeHolder<CrushingRecipe> recipeFor(ServerLevel level, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        return recipeCheck.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
    }

    /** The recipe for whatever is in the input slot, on either side. */
    public @Nullable RecipeHolder<CrushingRecipe> loadedRecipe() {
        return level instanceof ServerLevel serverLevel ? recipeFor(serverLevel, inputs.getStackInSlot(0)) : null;
    }

    /** Whether the crusher already knows the frequency of what it is holding. */
    public boolean isTuned() {
        RecipeHolder<CrushingRecipe> holder = loadedRecipe();
        return holder != null && tuned.contains(holder.id());
    }

    /** The frequency of what it is holding, or zero when it is holding nothing it can crush. */
    public int frequency() {
        RecipeHolder<CrushingRecipe> holder = loadedRecipe();
        return holder == null ? 0 : CrushingRecipe.frequencyOf(holder.id());
    }

    /** The batch the current operation would run at, for the readout. */
    public int currentBatch() {
        RecipeHolder<CrushingRecipe> holder = loadedRecipe();
        return holder == null ? 0 : batchFor(holder.value(), tuned.contains(holder.id()));
    }

    public int progress() {
        return progress;
    }

    @Override
    public double progressFraction() {
        return progress / (double) Math.max(1, ticksForOperation(baseTicksPerOperation()));
    }

    /** Recipes this crusher has calibrated. Exposed so a config card can copy them. */
    @Override
    public List<String> learned() {
        List<String> ids = new ArrayList<>(tuned.size());
        for (ResourceLocation id : tuned) {
            ids.add(id.toString());
        }
        return ids;
    }

    /**
     * Takes on calibrations copied from another crusher.
     *
     * <p>Added to rather than replacing what this one knows: pasting a card is a player teaching a
     * machine something, and teaching it one thing should not make it forget another.
     */
    @Override
    public void applyLearned(List<String> ids) {
        for (String id : ids) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) {
                tuned.add(parsed);
            }
        }
        wake();
        setChanged();
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
        return Component.translatable("block.actualgenerators.resonance_crusher");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ResonanceCrusherMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_INPUT, inputs.serializeNBT(registries));
        tag.put(KEY_OUTPUT, outputs.serializeNBT(registries));
        tag.putInt(KEY_PROGRESS, progress);
        ListTag learned = new ListTag();
        for (ResourceLocation id : tuned) {
            learned.add(StringTag.valueOf(id.toString()));
        }
        tag.put(KEY_TUNED, learned);
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
        progress = tag.getInt(KEY_PROGRESS);
        tuned.clear();
        for (Tag entry : tag.getList(KEY_TUNED, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) {
                tuned.add(id);
            }
        }
    }
}
