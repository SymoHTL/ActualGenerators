package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.ImpactDynamoMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Catches falling blocks and keeps both halves of the impact: the energy and the block.
 *
 * <p>Drop sand, gravel or an anvil down a shaft onto one of these and the landing is converted
 * rather than wasted — the further it fell, the more it was worth, and the block itself ends up
 * in the dynamo's buffer instead of stacking up on the lid. Nothing is consumed, so the cost of
 * running one is the cost of lifting the material back up, which is exactly the point.
 *
 * <p>It has no work loop at all. A block landing is an event, so the whole generator is driven
 * from {@link ImpactDynamoBlock#fallOn} and the block entity never scans for anything. That also
 * means speed and overclock upgrades have nothing to act on: it takes energy upgrades only, and
 * its ramp is a warm-up rather than an overclock. Each landing keeps it warm for a while, so a
 * dynamo under a steady shaft climbs to its full rate while one fed a single block pays the cold
 * rate for it.
 *
 * <p>A dynamo with nowhere to put the salvage, or with a full buffer, simply declines the catch
 * and lets the block land normally — which a player reads immediately as "it is backed up".
 */
public class ImpactDynamoBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_OUTPUT = "Output";
    private static final String KEY_LAST_ENERGY = "LastImpactEnergy";
    private static final String KEY_LAST_DISTANCE = "LastFallDistance";

    /** Enough room that a mixed sand-and-gravel shaft does not jam on the first stray block. */
    public static final int OUTPUT_SLOTS = 3;

    private final ItemStackHandler outputs = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            wake();
            setChanged();
        }
    };

    private int lastImpactEnergy;
    private int lastFallDistance;
    private int warmTicks;

    public ImpactDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IMPACT_DYNAMO.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.IMPACT_DYNAMO_CAPACITY, 30_000),
                () -> ServerConfig.valueOr(ServerConfig.IMPACT_DYNAMO_TRANSFER, 400),
                defaultSides());
    }

    /** Salvage out of the bottom, power out of every face. Nothing is ever piped in. */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.OUTPUT);
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
        // It is paid per landing, not per tick.
        return 0;
    }

    @Override
    public boolean usesWarmupRamp() {
        return true;
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        // There is nothing to look for: everything this generator does happens when something hits
        // it. The one thing a tick is good for is holding the warm-up ramp open for a while after a
        // landing, so a dynamo being fed steadily counts as working and one that has been abandoned
        // cools down again. With no landing to remember it reports nothing and goes back to sleep
        // in the chassis' idle back-off.
        if (warmTicks <= 0) {
            return false;
        }
        warmTicks--;
        return true;
    }

    public int warmTicksPerImpact() {
        return ServerConfig.valueOr(ServerConfig.IMPACT_DYNAMO_WARM_TICKS, 40);
    }

    /** FE a fall of this many blocks is worth right now, warm-up included. */
    public int energyForFall(int distance) {
        return warmed(energyPerBlockFallen() * distance);
    }

    public int energyPerBlockFallen() {
        return ServerConfig.valueOr(ServerConfig.IMPACT_DYNAMO_FE_PER_BLOCK_FALLEN, 40);
    }

    public int maxFallDistance() {
        return ServerConfig.valueOr(ServerConfig.IMPACT_DYNAMO_MAX_FALL_DISTANCE, 64);
    }

    /**
     * Takes the impact of a block that has just landed on the dynamo.
     *
     * @return true if the dynamo swallowed it — the caller then stops the block from placing
     */
    public boolean absorb(FallingBlockEntity falling, float fallDistance) {
        if (energy.isFull()) {
            return false;
        }

        int distance = (int) Math.min(Math.floor(fallDistance), maxFallDistance());
        if (distance <= 0) {
            return false;
        }

        // Refuse rather than destroy: a dynamo with no room lets the block land where a player
        // can see it piling up.
        ItemStack salvage = salvageOf(falling);
        if (!salvage.isEmpty() && !ItemHandlerHelper.insertItem(outputs, salvage, true).isEmpty()) {
            return false;
        }

        int earned = energyForFall(distance);
        energy.generate(earned);
        if (!salvage.isEmpty()) {
            ItemHandlerHelper.insertItem(outputs, salvage, false);
        }

        lastImpactEnergy = earned;
        lastFallDistance = distance;
        warmTicks = warmTicksPerImpact();
        wake();
        requestAutoIo();
        setChanged();
        return true;
    }

    /**
     * The item a landed block leaves behind. Blocks with no item form — falling block entities
     * carrying something exotic — are still worth their impact, they just leave nothing.
     */
    private static ItemStack salvageOf(FallingBlockEntity falling) {
        return new ItemStack(falling.getBlockState().getBlock());
    }

    // ------------------------------------------------------------------ readouts

    /** FE the last catch was worth, for the screen. */
    public int lastImpactEnergy() {
        return lastImpactEnergy;
    }

    /** How far the last catch had fallen, after the cap. */
    public int lastFallDistance() {
        return lastFallDistance;
    }

    // ------------------------------------------------------------------ inventory access

    public IItemHandler outputHandler() {
        return outputs;
    }

    @Override
    protected @Nullable IItemHandler autoOutputHandler() {
        return outputs;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        for (int slot = 0; slot < outputs.getSlots(); slot++) {
            ItemStack stack = outputs.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.impact_dynamo");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ImpactDynamoMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_OUTPUT, outputs.serializeNBT(registries));
        tag.putInt(KEY_LAST_ENERGY, lastImpactEnergy);
        tag.putInt(KEY_LAST_DISTANCE, lastFallDistance);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_OUTPUT)) {
            outputs.deserializeNBT(registries, tag.getCompound(KEY_OUTPUT));
        }
        lastImpactEnergy = tag.getInt(KEY_LAST_ENERGY);
        lastFallDistance = tag.getInt(KEY_LAST_DISTANCE);
    }
}
