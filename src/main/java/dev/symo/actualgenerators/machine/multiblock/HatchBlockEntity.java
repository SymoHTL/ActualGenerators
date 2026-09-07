package dev.symo.actualgenerators.machine.multiblock;

import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.Movers;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.RedstoneHatchMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity behind a hatch: the position of the controller it belongs to; a machine's
 * {@link SideConfig}, so every face of the hatch says what may pass through it and the hatch
 * says whether it pulls and pushes of its own accord, exactly as a machine does; and for a
 * redstone hatch what it reports and the level it is giving off.
 *
 * <p>The item, energy and fluid views it hands out are proxies, one per face, that look the
 * controller up on every call. That is one map lookup per transfer, and it buys a lot: a hatch
 * never holds a stale handler, a controller whose chunk has gone answers as empty rather than
 * swallowing items into a block entity that is about to be reloaded from disk, and forming or
 * breaking the structure needs no capability invalidation at all, because the proxy a
 * neighbour cached is still right. A face set to nothing hands out no capability, the way a
 * machine face does, and that one change does invalidate.
 *
 * <p>A hatch never ticks. A hatch that pulls or pushes is worked by its controller's transfer
 * pass ({@link #autoTransfer}), on the same schedule as every machine face, through every face
 * that is open that way and does not look into the structure.
 *
 * <p>The faces travel to the client (the update tag), where the hatch's model reads them for
 * the markers on its sides. The level a reporting hatch gives off is set by its controller
 * ({@link MultiblockControllerBlockEntity#refreshSignals}), never computed here.
 */
public class HatchBlockEntity extends BlockEntity implements MenuProvider {
    private static final String KEY_CONTROLLER = "Controller";
    private static final String KEY_MODE = "Mode";
    private static final String KEY_EMITTED = "Emitted";
    private static final String KEY_SIDES = "Sides";
    /** The proxy for a query with no side. */
    private static final int NO_SIDE = Direction.values().length;

    private @Nullable BlockPos controllerPos;
    private HatchSignal mode = HatchSignal.CONTROL;
    /** Every face open both ways when placed, nothing moving on its own: a machine's defaults. */
    private final SideConfig sides = SideConfig.of(IoMode.BOTH, IoMode.BOTH, IoMode.BOTH);
    private int emitted;
    private final IItemHandler[] itemProxies = new IItemHandler[NO_SIDE + 1];
    private final IEnergyStorage[] energyProxies = new IEnergyStorage[NO_SIDE + 1];
    private final IFluidHandler[] fluidProxies = new IFluidHandler[NO_SIDE + 1];

    /** The neighbour past each side, for a hatch that pulls or pushes; made when first needed. */
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IItemHandler, @Nullable Direction>[] itemsBeside =
            new BlockCapabilityCache[Direction.values().length];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IEnergyStorage, @Nullable Direction>[] energyBeside =
            new BlockCapabilityCache[Direction.values().length];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IFluidHandler, @Nullable Direction>[] fluidsBeside =
            new BlockCapabilityCache[Direction.values().length];

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HATCH.get(), pos, state);
    }

    public HatchKind kind() {
        return getBlockState().getBlock() instanceof HatchBlock hatch ? hatch.kind() : HatchKind.ITEM;
    }

    /** The kind of thing this hatch moves, in the chassis' terms; null for a redstone hatch. */
    public @Nullable TransferKind transferKind() {
        return switch (kind()) {
            case ITEM -> TransferKind.ITEM;
            case ENERGY -> TransferKind.ENERGY;
            case FLUID -> TransferKind.FLUID;
            case REDSTONE -> null;
        };
    }

    /** The side the player who placed the hatch was facing; the side panel is relative to it. */
    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(HatchBlock.FACING) ? state.getValue(HatchBlock.FACING) : Direction.NORTH;
    }

    void attach(BlockPos controller) {
        if (!controller.equals(controllerPos)) {
            controllerPos = controller.immutable();
            setChanged();
        }
    }

    void detach(BlockPos controller) {
        if (controller.equals(controllerPos)) {
            controllerPos = null;
            setChanged();
            emit(0);
        }
    }

    /** The formed controller this hatch is part of, or null while there is none to be found. */
    public @Nullable MultiblockControllerBlockEntity controller() {
        if (controllerPos == null || level == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller
                && controller.isFormed() ? controller : null;
    }

    // ------------------------------------------------------------------ the faces

    /** The faces as a machine keeps them: what may pass per face, and the two auto flags. */
    public SideConfig sides() {
        return sides;
    }

    /** What may pass through this face of the block. */
    public IoMode mode(Direction side) {
        TransferKind kind = transferKind();
        return kind == null ? IoMode.DISABLED : sides.get(kind, facing(), side);
    }

    public IoMode mode(RelativeSide side) {
        TransferKind kind = transferKind();
        return kind == null ? IoMode.DISABLED : sides.get(kind, side);
    }

    public boolean autoPull() {
        TransferKind kind = transferKind();
        return kind != null && sides.autoPull(kind);
    }

    public boolean autoPush() {
        TransferKind kind = transferKind();
        return kind != null && sides.autoPush(kind);
    }

    /** The side panel's face button: disabled, input, output, both, round again. Nothing on a face that looks into the structure. */
    public void cycleSide(RelativeSide side) {
        TransferKind kind = transferKind();
        if (kind == null || blocked(side)) {
            return;
        }
        sides.cycle(kind, side);
        changed(true);
    }

    /** The side panel's IN and OUT toggles. */
    public void toggleAuto(boolean push) {
        TransferKind kind = transferKind();
        if (kind == null) {
            return;
        }
        sides.toggleAuto(kind, push);
        changed(false);
    }

    /**
     * The faces that look into the structure, one bit per {@link RelativeSide} ordinal: the
     * interior, or another block of the shell. Nothing moves through them and the panel greys
     * them out. Nothing while the hatch is not part of a standing structure.
     */
    public int blockedMask() {
        MultiblockControllerBlockEntity controller = controller();
        MultiblockControllerBlockEntity.Structure box = controller == null ? null : controller.structure();
        if (box == null) {
            return 0;
        }
        int mask = 0;
        Direction facing = facing();
        for (RelativeSide side : RelativeSide.all()) {
            if (inStructure(controller, box, worldPosition.relative(side.toDirection(facing)))) {
                mask |= 1 << side.ordinal();
            }
        }
        return mask;
    }

    public boolean blocked(RelativeSide side) {
        return (blockedMask() & (1 << side.ordinal())) != 0;
    }

    private static boolean inStructure(MultiblockControllerBlockEntity controller,
                                       MultiblockControllerBlockEntity.Structure box,
                                       BlockPos pos) {
        return box.contains(pos)
                && controller.requirementAt(box.min(), box.max(), pos) != MultiblockControllerBlockEntity.Requirement.FREE;
    }

    /** A face changed: save, tell the client (the markers), and let a controller behind us run a pass at once. */
    private void changed(boolean capabilities) {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        if (capabilities) {
            invalidateCapabilities();
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        MultiblockControllerBlockEntity controller = controller();
        if (controller != null) {
            controller.requestAutoIo();
        }
    }

    /**
     * Moves things through this hatch of its own accord, for the controller's transfer pass: what
     * the controller hands out for this kind, against whatever stands past every face that is
     * open that way and does not look into the structure. Never into the structure itself: a
     * hatch pushing into the next hatch along the wall would be a loop.
     */
    void autoTransfer(ServerLevel level, MultiblockControllerBlockEntity controller, int ticks) {
        TransferKind kind = transferKind();
        if (kind == null) {
            return;
        }
        boolean pull = sides.autoPull(kind);
        boolean push = sides.autoPush(kind);
        if (!pull && !push) {
            return;
        }
        MultiblockControllerBlockEntity.Structure box = controller.structure();
        if (box == null) {
            return;
        }
        Direction facing = facing();
        for (Direction side : Direction.values()) {
            if (inStructure(controller, box, worldPosition.relative(side))) {
                continue;
            }
            IoMode mode = sides.get(kind, facing, side);
            boolean pullHere = pull && mode.canInput();
            boolean pushHere = push && mode.canOutput();
            if (!pullHere && !pushHere) {
                continue;
            }
            switch (kind) {
                case ITEM -> {
                    IItemHandler mine = controllerItems();
                    IItemHandler theirs = beside(Capabilities.ItemHandler.BLOCK, itemsBeside, level, side);
                    if (mine == null || theirs == null) {
                        continue;
                    }
                    if (pullHere && Movers.pullItems(mine, theirs)) {
                        controller.wake();
                    }
                    if (pushHere) {
                        Movers.pushItems(mine, theirs);
                    }
                }
                case ENERGY -> {
                    IEnergyStorage mine = controllerEnergy();
                    IEnergyStorage theirs = beside(Capabilities.EnergyStorage.BLOCK, energyBeside, level, side);
                    if (mine == null || theirs == null) {
                        continue;
                    }
                    int span = Math.max(ticks, 1);
                    if (pullHere && Movers.moveEnergy(theirs, mine, (long) controller.hatchEnergyRate(false) * span, ticks) > 0) {
                        controller.wake();
                    }
                    if (pushHere) {
                        Movers.moveEnergy(mine, theirs, (long) controller.hatchEnergyRate(true) * span, ticks);
                    }
                }
                case FLUID -> {
                    IFluidHandler mine = controllerFluids();
                    IFluidHandler theirs = beside(Capabilities.FluidHandler.BLOCK, fluidsBeside, level, side);
                    if (mine == null || theirs == null) {
                        continue;
                    }
                    if (pullHere && Movers.moveFluid(theirs, mine)) {
                        controller.wake();
                    }
                    if (pushHere) {
                        Movers.moveFluid(mine, theirs);
                    }
                }
                case REDSTONE -> {
                }
            }
        }
    }

    private <T> @Nullable T beside(BlockCapability<T, @Nullable Direction> capability,
                                   BlockCapabilityCache<T, @Nullable Direction>[] caches,
                                   ServerLevel level,
                                   Direction side) {
        BlockCapabilityCache<T, @Nullable Direction> cache = caches[side.ordinal()];
        if (cache == null) {
            cache = BlockCapabilityCache.create(capability, level, worldPosition.relative(side), side.getOpposite());
            caches[side.ordinal()] = cache;
        }
        return cache.getCapability();
    }

    // ------------------------------------------------------------------ redstone

    public HatchSignal mode() {
        return mode;
    }

    /** Switches what the hatch reports; a controller in reach re-reads its power and its reports at once. */
    public void setMode(HatchSignal mode) {
        if (this.mode == mode) {
            return;
        }
        this.mode = mode;
        setChanged();
        MultiblockControllerBlockEntity controller = controller();
        if (controller != null) {
            controller.onNeighbourChanged(controller.readPower());
            controller.refreshSignals();
        } else {
            emit(0);
        }
    }

    /** The level this hatch gives off on every side. */
    public int emitted() {
        return emitted;
    }

    /** Sets the level, and tells the neighbours only when it actually changed. */
    void emit(int level) {
        if (emitted == level) {
            return;
        }
        emitted = level;
        setChanged();
        Level world = this.level;
        if (world != null && !world.isClientSide) {
            world.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    // ------------------------------------------------------------------ capabilities

    /** The item view through a face, on an item hatch; null on the others and on a face set to nothing. */
    public @Nullable IItemHandler items(@Nullable Direction side) {
        if (kind() != HatchKind.ITEM || !open(side)) {
            return null;
        }
        int index = index(side);
        if (itemProxies[index] == null) {
            itemProxies[index] = new ItemProxy(side);
        }
        return itemProxies[index];
    }

    /** The energy view through a face, on an energy hatch. */
    public @Nullable IEnergyStorage energy(@Nullable Direction side) {
        if (kind() != HatchKind.ENERGY || !open(side)) {
            return null;
        }
        int index = index(side);
        if (energyProxies[index] == null) {
            energyProxies[index] = new EnergyProxy(side);
        }
        return energyProxies[index];
    }

    /** The tank view through a face, on a fluid hatch. */
    public @Nullable IFluidHandler fluids(@Nullable Direction side) {
        if (kind() != HatchKind.FLUID || !open(side)) {
            return null;
        }
        int index = index(side);
        if (fluidProxies[index] == null) {
            fluidProxies[index] = new FluidProxy(side);
        }
        return fluidProxies[index];
    }

    private static int index(@Nullable Direction side) {
        return side == null ? NO_SIDE : side.ordinal();
    }

    /** What a face lets through right now; a query with no side is answered as if both ways were open. */
    private IoMode allowed(@Nullable Direction side) {
        return side == null ? IoMode.BOTH : mode(side);
    }

    private boolean open(@Nullable Direction side) {
        return allowed(side).isActive();
    }

    private @Nullable IItemHandler controllerItems() {
        MultiblockControllerBlockEntity controller = controller();
        return controller == null ? null : controller.hatchItems();
    }

    private @Nullable IEnergyStorage controllerEnergy() {
        MultiblockControllerBlockEntity controller = controller();
        return controller == null ? null : controller.hatchEnergy();
    }

    private @Nullable IFluidHandler controllerFluids() {
        MultiblockControllerBlockEntity controller = controller();
        return controller == null ? null : controller.hatchFluids();
    }

    // ------------------------------------------------------------------ menu (redstone hatch)

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RedstoneHatchMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong(KEY_CONTROLLER, controllerPos.asLong());
        }
        tag.putInt(KEY_MODE, mode.ordinal());
        tag.put(KEY_SIDES, sides.save());
        tag.putInt(KEY_EMITTED, emitted);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains(KEY_CONTROLLER, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(KEY_CONTROLLER)) : null;
        mode = HatchSignal.byOrdinal(tag.getInt(KEY_MODE));
        if (tag.contains(KEY_SIDES, Tag.TAG_COMPOUND)) {
            sides.load(tag.getCompound(KEY_SIDES));
        }
        emitted = tag.getInt(KEY_EMITTED);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        refreshModel();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        refreshModel();
    }

    /** The markers on the sides are model data: the chunk has to be drawn again to show a change. */
    private void refreshModel() {
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Empty with no slots until a formed controller stands behind it; closed the ways the face is closed. */
    private final class ItemProxy implements IItemHandler {
        private final @Nullable Direction side;

        ItemProxy(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int getSlots() {
            IItemHandler target = controllerItems();
            return target == null ? 0 : target.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler target = controllerItems();
            return target == null ? ItemStack.EMPTY : target.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler target = controllerItems();
            return target == null || !allowed(side).canInput() ? stack : target.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler target = controllerItems();
            return target == null || !allowed(side).canOutput() ? ItemStack.EMPTY : target.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler target = controllerItems();
            return target == null ? 0 : target.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler target = controllerItems();
            return target != null && allowed(side).canInput() && target.isItemValid(slot, stack);
        }
    }

    /** Holds nothing and moves nothing until a formed controller stands behind it. */
    private final class EnergyProxy implements IEnergyStorage {
        private final @Nullable Direction side;

        EnergyProxy(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            IEnergyStorage target = controllerEnergy();
            return target == null || !allowed(side).canInput() ? 0 : target.receiveEnergy(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            IEnergyStorage target = controllerEnergy();
            return target == null || !allowed(side).canOutput() ? 0 : target.extractEnergy(toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            IEnergyStorage target = controllerEnergy();
            return target == null ? 0 : target.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            IEnergyStorage target = controllerEnergy();
            return target == null ? 0 : target.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            IEnergyStorage target = controllerEnergy();
            return target != null && allowed(side).canOutput() && target.canExtract();
        }

        @Override
        public boolean canReceive() {
            IEnergyStorage target = controllerEnergy();
            return target != null && allowed(side).canInput() && target.canReceive();
        }
    }

    /** No tanks at all until a formed controller with a tank stands behind it. */
    private final class FluidProxy implements IFluidHandler {
        private final @Nullable Direction side;

        FluidProxy(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int getTanks() {
            IFluidHandler target = controllerFluids();
            return target == null ? 0 : target.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler target = controllerFluids();
            return target == null ? FluidStack.EMPTY : target.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = controllerFluids();
            return target == null ? 0 : target.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler target = controllerFluids();
            return target != null && allowed(side).canInput() && target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler target = controllerFluids();
            return target == null || !allowed(side).canInput() ? 0 : target.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler target = controllerFluids();
            return target == null || !allowed(side).canOutput() ? FluidStack.EMPTY : target.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler target = controllerFluids();
            return target == null || !allowed(side).canOutput() ? FluidStack.EMPTY : target.drain(maxDrain, action);
        }
    }
}
