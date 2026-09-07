package dev.symo.actualgenerators.machine.multiblock;

import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * A machine that is the controller of a structure of casings: a hollow box, unless the machine
 * says it is another shape ({@link #requirementAt}, {@link #locate}, {@link #bounds}).
 *
 * <p>The box is what a player builds: a cuboid shell of {@link MultiblockCasingBlock casing} and
 * {@link HatchBlock hatches} with this block set into the front wall facing out, and nothing but
 * air inside — or whatever else the machine says it accepts on the floor. Each of the three
 * extents has its own smallest and largest size ({@link #minSize}, {@link #maxSize}), and the
 * interior volume is the machine's grade: a bigger box does more per operation, which is what
 * tiers do for the single-block machines.
 *
 * <p>The structure is never polled. It is checked when the controller loads, when a casing,
 * hatch or controller is placed or broken within its reach, when something changes inside a
 * standing box, and when a chunk its box reaches into loads; each of those sets a flag that the
 * next tick spends on one walk over the box, and a shell that nobody touches costs nothing at
 * all. The controller's own faces move nothing: items, energy and redstone go through the
 * hatches, and the shell blocks wear the {@code formed} state while the box stands.
 */
public abstract class MultiblockControllerBlockEntity extends MachineBlockEntity {
    private static final String KEY_STRUCTURE = "Structure";
    private static final String KEY_PREVIEW = "Preview";

    /** The three sizes of a box, as the player sees them from the front. */
    public enum Extent {
        WIDTH, HEIGHT, DEPTH
    }

    /**
     * Every loaded controller, per level, so a casing that changes can ask the handful of
     * controllers that could care rather than the other way round. Server thread only.
     */
    private static final Map<ServerLevel, Map<BlockPos, MultiblockControllerBlockEntity>> LOADED = new HashMap<>();
    /** The client's controllers, registered on load like the pads are. */
    private static final Set<MultiblockControllerBlockEntity> CLIENT = new HashSet<>();

    private @Nullable Structure structure;
    private boolean revalidate;
    /**
     * The size the preview shows, per extent; zero for "the smallest". The controller's, saved
     * with it and synced through its menu, so it sticks through the window closing, the preview
     * being switched off and the structure coming and going.
     */
    private final int[] preview = new int[Extent.values().length];

    protected MultiblockControllerBlockEntity(BlockEntityType<?> type,
                                              BlockPos pos,
                                              BlockState state,
                                              LongSupplier baseCapacity,
                                              IntSupplier baseTransferRate) {
        super(type, pos, state, baseCapacity, baseTransferRate,
                SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED));
    }

    // ------------------------------------------------------------------ subclass contract

    /** The longest a box may be along one extent, outer casing to outer casing. */
    public abstract int maxSize(Extent extent);

    /** The shortest, never under three: a wall, something inside, a wall. */
    public int minSize(Extent extent) {
        return 3;
    }

    /** How a size steps in the preview: one by default; a shape that has to stay centred steps by two. */
    public int sizeStep(Extent extent) {
        return 1;
    }

    /** The size the preview shows for this extent: what was last set or last stood, else the smallest. */
    public int previewSize(Extent extent) {
        int set = preview[extent.ordinal()];
        return set == 0 ? minSize(extent) : Math.clamp(set, minSize(extent), maxSize(extent));
    }

    public void setPreviewSize(Extent extent, int size) {
        preview[extent.ordinal()] = Math.clamp(size, minSize(extent), maxSize(extent));
        setChanged();
    }

    /** What stands is what the preview shows next: after it comes apart, the steppers read the size it had. */
    private void rememberSize(Structure found) {
        boolean acrossX = facing(getBlockState()).getAxis() == Direction.Axis.X;
        preview[Extent.WIDTH.ordinal()] = acrossX ? found.sizeZ() : found.sizeX();
        preview[Extent.HEIGHT.ordinal()] = found.sizeY();
        preview[Extent.DEPTH.ordinal()] = acrossX ? found.sizeX() : found.sizeZ();
    }

    /** The longest edge of any extent: how far a change can be from the controller and still matter. */
    public final int reach() {
        return Math.max(maxSize(Extent.WIDTH), Math.max(maxSize(Extent.HEIGHT), maxSize(Extent.DEPTH)));
    }

    /**
     * Whether a block strictly inside the shell is allowed there. Air, unless the machine says
     * otherwise; {@code floor} is true on the lowest interior layer, where a furnace takes its
     * heating fluid.
     */
    public boolean interiorAccepts(BlockPos pos, BlockState state, boolean floor) {
        return state.isAir();
    }

    /** What an item hatch in the shell exposes, or null for a machine that takes no items. */
    public abstract @Nullable IItemHandler hatchItems();

    /** What an energy hatch in the shell exposes, or null for a machine with no energy to offer. */
    public abstract @Nullable IEnergyStorage hatchEnergy();

    /** What a fluid hatch in the shell hands out, or null for a machine with no tank. */
    public @Nullable IFluidHandler hatchFluids() {
        return null;
    }

    /** Whether the preview shows the floor wanting a fluid poured on it. */
    public boolean floorTakesFluid() {
        return false;
    }

    /** What a position inside a structure's bounding box has to hold. */
    public enum Requirement {
        /** Machine Casing, a hatch, or the controller itself. */
        SHELL,
        /** The machine's own casing, {@link #specialCasing()}. */
        SPECIAL,
        /** Whatever {@link #interiorAccepts} allows. */
        INTERIOR,
        /** Not part of the structure; anything may stand here. */
        FREE
    }

    /**
     * What must stand at {@code pos} in a structure spanning {@code min} to {@code max}: a hollow
     * box of casing round an interior, unless the machine is another shape.
     */
    public Requirement requirementAt(BlockPos min, BlockPos max, BlockPos pos) {
        return Structure.onSurface(min, max, pos) ? Requirement.SHELL : Requirement.INTERIOR;
    }

    /** The block a {@link Requirement#SPECIAL} position wants; null for a shape that has none. */
    public @Nullable Block specialCasing() {
        return null;
    }

    /**
     * The two corners, {@code [min, max]}, of a structure of the given size standing on this
     * controller, the way the preview draws it and the guide describes it: for a box, the
     * controller in the bottom row of the front wall with the wall centred on it (one more to
     * the right when the width is even), the depth running away behind.
     */
    public BlockPos[] bounds(int width, int height, int depth) {
        Direction facing = facing(getBlockState());
        Direction right = facing.getClockWise();
        BlockPos corner = worldPosition.relative(right.getOpposite(), width / 2);
        BlockPos far = corner.relative(right, width - 1).above(height - 1).relative(facing.getOpposite(), depth - 1);
        return corners(corner, far);
    }

    /** The label the preview hangs over a structure of this size. */
    public Component previewLabel(int width, int height, int depth) {
        return Component.translatable("gui.actualgenerators.multiblock.preview.label",
                width + "×" + height + "×" + depth, (width - 2) * (height - 2) * (depth - 2));
    }

    /** {@code [min, max]} of the box with these two opposite corners. */
    protected static BlockPos[] corners(BlockPos a, BlockPos b) {
        return new BlockPos[]{
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))};
    }

    /** Called after the structure formed, changed shape or came apart. */
    protected void onStructureChanged() {
    }

    /**
     * Called after every walk over the box, whether or not its shape changed: what is inside a
     * standing box may have, and this is where a machine reads it.
     */
    protected void onStructureValidated(ServerLevel level) {
    }

    /** How well the machine is doing what it does, in thousandths; what an efficiency hatch reports. */
    public int efficiencyPermille() {
        return 1000;
    }

    /** Every hatch set to pull or push does so, through its faces that are not part of the structure. */
    @Override
    protected void autoIoBeyondFaces(ServerLevel level, int ticks) {
        if (structure == null) {
            return;
        }
        for (BlockPos pos : structure.hatches()) {
            if (level.getBlockEntity(pos) instanceof HatchBlockEntity hatch) {
                hatch.autoTransfer(level, this, ticks);
            }
        }
    }

    /** FE/t one hatch may move a tick, in or out: the chassis rate, which follows the structure. */
    public int hatchEnergyRate(boolean push) {
        return push ? energyStorage().getMaxExtract() : energyStorage().getMaxReceive();
    }

    // ------------------------------------------------------------------ what stands

    public boolean isFormed() {
        return structure != null;
    }

    public @Nullable Structure structure() {
        return structure;
    }

    /** Blocks inside the shell; zero while nothing is formed. */
    public int interiorVolume() {
        return structure == null ? 0 : structure.interior();
    }

    /**
     * The controller's own neighbours, or any control hatch in the shell. Hatches are never read:
     * a reporting hatch beside the controller would otherwise switch on the thing it reports on.
     */
    public boolean readPower() {
        Level level = this.level;
        if (level == null) {
            return false;
        }
        if (signalAround(level, worldPosition) > 0) {
            return true;
        }
        if (structure != null) {
            for (BlockPos pos : structure.redstoneHatches()) {
                if (level.getBlockEntity(pos) instanceof HatchBlockEntity hatch
                        && hatch.mode() == HatchSignal.CONTROL
                        && signalAround(level, pos) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The strongest signal reaching a block from any neighbour that is not a hatch. */
    static int signalAround(Level level, BlockPos pos) {
        int best = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).getBlock() instanceof HatchBlock) {
                continue;
            }
            best = Math.max(best, level.getSignal(neighbour, direction));
        }
        return best;
    }

    /** What a reporting hatch in the given mode gives off right now, nought to fifteen. */
    public int report(HatchSignal mode) {
        return switch (mode) {
            case CONTROL -> 0;
            case FORMED -> isFormed() ? 15 : 0;
            case WORKING -> isFormed() && isWorking() ? 15 : 0;
            case ENERGY -> level(energy.stored(), energy.capacity());
            case ITEMS -> {
                IItemHandler items = hatchItems();
                if (items == null) {
                    yield 0;
                }
                long held = 0;
                long room = 0;
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    held += items.getStackInSlot(slot).getCount();
                    room += items.getSlotLimit(slot);
                }
                yield level(held, room);
            }
            case EFFICIENCY -> isFormed() ? level(efficiencyPermille(), 1000) : 0;
        };
    }

    /** A comparator's scale: nothing is nought, anything at all is at least one, full is fifteen. */
    private static int level(long part, long whole) {
        if (whole <= 0 || part <= 0) {
            return 0;
        }
        return Mth.lerpDiscrete((float) Math.min(1.0, part / (double) whole), 0, 15);
    }

    /** Hands every reporting hatch its level; a hatch whose level did not change tells nobody. */
    public void refreshSignals() {
        Level level = this.level;
        if (structure == null || level == null || level.isClientSide) {
            return;
        }
        for (BlockPos pos : structure.redstoneHatches()) {
            if (level.getBlockEntity(pos) instanceof HatchBlockEntity hatch) {
                hatch.emit(report(hatch.mode()));
            }
        }
    }

    // ------------------------------------------------------------------ when to look

    /** Something in reach changed: look again on the next tick, and no sooner than that. */
    public void requestRevalidation() {
        revalidate = true;
        wake();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (revalidate) {
            revalidate = false;
            validate(level);
            state = getBlockState();
        }
        super.serverTick(level, pos, state);
        refreshSignals();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            LOADED.computeIfAbsent(server, ignored -> new HashMap<>()).put(worldPosition, this);
            revalidate = true;
        } else if (level != null) {
            CLIENT.add(this);
        }
    }

    /** Every controller the client has loaded, for the overlay; never scan chunks for them. */
    public static Collection<MultiblockControllerBlockEntity> clientControllers() {
        return CLIENT;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT.remove(this);
        if (level instanceof ServerLevel server) {
            Map<BlockPos, MultiblockControllerBlockEntity> controllers = LOADED.get(server);
            if (controllers != null) {
                controllers.remove(worldPosition, this);
            }
        }
    }

    @Override
    public void onRemovedFromWorld(Level level, BlockPos pos) {
        super.onRemovedFromWorld(level, pos);
        if (structure != null) {
            detach(level, structure);
            structure = null;
        }
    }

    /** A casing, hatch or controller was placed or broken: wake whichever controllers it could be part of. */
    public static void shellChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Map<BlockPos, MultiblockControllerBlockEntity> controllers = LOADED.get(server);
        if (controllers == null) {
            return;
        }
        for (MultiblockControllerBlockEntity controller : controllers.values()) {
            if (controller.watches(pos)) {
                controller.requestRevalidation();
            }
        }
    }

    /** A chunk came back: any controller whose box reaches into it may have missed a change there. */
    public static void chunkLoaded(ServerLevel level, ChunkPos chunk) {
        Map<BlockPos, MultiblockControllerBlockEntity> controllers = LOADED.get(level);
        if (controllers == null) {
            return;
        }
        for (MultiblockControllerBlockEntity controller : controllers.values()) {
            if (controller.touches(chunk)) {
                controller.requestRevalidation();
            }
        }
    }

    /**
     * A block beside a shell block changed. Air appearing matters to a box waiting to form — the
     * torch a player left inside and has just picked up — and anything at all changing inside a
     * standing box matters to it: a fluid poured on the floor is what a furnace runs on. Nothing
     * else does, so a redstone clock against a casing costs nothing.
     */
    public static void besideShellChanged(Level level, BlockPos changed) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Map<BlockPos, MultiblockControllerBlockEntity> controllers = LOADED.get(server);
        if (controllers == null) {
            return;
        }
        boolean air = level.getBlockState(changed).isAir();
        for (MultiblockControllerBlockEntity controller : controllers.values()) {
            boolean matters = controller.structure == null
                    ? air && controller.watches(changed)
                    : controller.structure.isInside(changed);
            if (matters) {
                controller.requestRevalidation();
            }
        }
    }

    public static void levelUnloaded(LevelAccessor level) {
        if (level instanceof ServerLevel server) {
            LOADED.remove(server);
        }
        CLIENT.removeIf(controller -> controller.getLevel() == level);
    }

    /** Formed: anything in the box. Unformed: anything a box of the largest size could reach. */
    private boolean watches(BlockPos pos) {
        if (structure != null) {
            return structure.contains(pos);
        }
        int reach = reach();
        return Math.abs(pos.getX() - worldPosition.getX()) <= reach
                && Math.abs(pos.getY() - worldPosition.getY()) <= reach
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= reach;
    }

    private boolean touches(ChunkPos chunk) {
        int reach = reach();
        int minX = structure != null ? structure.min().getX() : worldPosition.getX() - reach;
        int maxX = structure != null ? structure.max().getX() : worldPosition.getX() + reach;
        int minZ = structure != null ? structure.min().getZ() : worldPosition.getZ() - reach;
        int maxZ = structure != null ? structure.max().getZ() : worldPosition.getZ() + reach;
        return chunk.getMinBlockX() <= maxX && chunk.getMaxBlockX() >= minX
                && chunk.getMinBlockZ() <= maxZ && chunk.getMaxBlockZ() >= minZ;
    }

    // ------------------------------------------------------------------ the walk

    private void validate(ServerLevel level) {
        if (!areaLoaded(level)) {
            // Nothing in an unloaded chunk changes; the chunk load event brings us back here.
            return;
        }
        Structure found = find(level);
        if (Objects.equals(found, structure)) {
            if (found != null) {
                attach(level, found);
            }
            onStructureValidated(level);
            return;
        }
        if (structure != null) {
            detach(level, structure);
        }
        structure = found;
        if (found != null) {
            attach(level, found);
            rememberSize(found);
        }
        // The buffer and the hatch rate follow the interior, the way they follow the batch.
        applyUpgrades();
        setPowered(readPower());
        BlockState state = getBlockState();
        if (state.hasProperty(MultiblockControllerBlock.FORMED)
                && state.getValue(MultiblockControllerBlock.FORMED) != (found != null)) {
            level.setBlock(worldPosition, state.setValue(MultiblockControllerBlock.FORMED, found != null), Block.UPDATE_ALL);
        }
        wake();
        setChanged();
        syncToClients();
        onStructureChanged();
        onStructureValidated(level);
    }

    /** Every chunk a box of the largest size round the controller could reach into. */
    private boolean areaLoaded(ServerLevel level) {
        int reach = reach();
        return level.isLoaded(worldPosition.offset(-reach, 0, -reach))
                && level.isLoaded(worldPosition.offset(reach, 0, -reach))
                && level.isLoaded(worldPosition.offset(-reach, 0, reach))
                && level.isLoaded(worldPosition.offset(reach, 0, reach));
    }

    /**
     * The structure this controller is part of, or null when there is none: {@link #locate} says
     * where one would stand from the shell round the controller, and one walk over it checks
     * every position against {@link #requirementAt}.
     */
    private @Nullable Structure find(ServerLevel level) {
        BlockPos[] corners = locate(level);
        if (corners == null) {
            return null;
        }
        BlockPos min = corners[0];
        BlockPos max = corners[1];
        List<BlockPos> hatches = new ArrayList<>();
        List<BlockPos> redstone = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            switch (requirementAt(min, max, pos)) {
                case FREE -> {
                }
                case INTERIOR -> {
                    if (!interiorAccepts(pos, state, pos.getY() == min.getY() + 1)) {
                        return null;
                    }
                }
                case SPECIAL -> {
                    Block special = specialCasing();
                    if (special == null || !state.is(special)) {
                        return null;
                    }
                }
                case SHELL -> {
                    if (state.is(ModBlocks.MACHINE_CASING.get()) || pos.equals(worldPosition)) {
                        continue;
                    }
                    if (state.getBlock() instanceof HatchBlock hatch) {
                        hatches.add(pos.immutable());
                        if (hatch.kind() == HatchKind.REDSTONE) {
                            redstone.add(pos.immutable());
                        }
                        continue;
                    }
                    // Anything else in the wall, a second controller included, and this is not a structure.
                    return null;
                }
            }
        }
        return new Structure(min, max, List.copyOf(hatches), List.copyOf(redstone));
    }

    /**
     * Where the structure round this controller would stand, {@code [min, max]}, or null when
     * the shell does not add up to one. The controller sits somewhere in a box's front wall, so
     * the row and the column through it span that wall, and a corner of the wall then gives the
     * depth; another shape walks its own way.
     */
    protected @Nullable BlockPos[] locate(ServerLevel level) {
        int maxWidth = maxSize(Extent.WIDTH);
        int maxHeight = maxSize(Extent.HEIGHT);
        int maxDepth = maxSize(Extent.DEPTH);
        Direction facing = facing(getBlockState());
        Direction back = facing.getOpposite();
        Direction right = facing.getClockWise();

        int left = extent(level, worldPosition, right.getOpposite(), maxWidth);
        int rightward = extent(level, worldPosition, right, maxWidth);
        int down = extent(level, worldPosition, Direction.DOWN, maxHeight);
        int up = extent(level, worldPosition, Direction.UP, maxHeight);
        BlockPos corner = worldPosition.relative(right.getOpposite(), left).below(down);
        int width = left + rightward + 1;
        int height = down + up + 1;
        int depth = extent(level, corner, back, maxDepth) + 1;
        if (width < minSize(Extent.WIDTH) || width > maxWidth
                || height < minSize(Extent.HEIGHT) || height > maxHeight
                || depth < minSize(Extent.DEPTH) || depth > maxDepth) {
            return null;
        }
        BlockPos far = corner.relative(right, width - 1).above(height - 1).relative(back, depth - 1);
        return corners(corner, far);
    }

    /** How many shell blocks run on from {@code from} in {@code direction}, up to {@code max}. */
    private static int extent(ServerLevel level, BlockPos from, Direction direction, int max) {
        for (int step = 1; step <= max; step++) {
            if (!isShell(level.getBlockState(from.relative(direction, step)))) {
                return step - 1;
            }
        }
        return max;
    }

    /**
     * The state a shell block wears while the structure stands, or loose again: {@code FORMED}
     * already set, and whatever else a controller's shell shows (the tap's coil).
     */
    protected BlockState shellState(Structure box, BlockPos pos, BlockState state, boolean formed) {
        return state;
    }

    /** Casing, a hatch or a controller: what a wall is made of. */
    public static boolean isShell(BlockState state) {
        return state.is(ModBlocks.MACHINE_CASING.get())
                || state.getBlock() instanceof HatchBlock
                || state.getBlock() instanceof MultiblockControllerBlock;
    }

    private void attach(Level level, Structure found) {
        for (BlockPos pos : found.hatches()) {
            if (level.getBlockEntity(pos) instanceof HatchBlockEntity hatch) {
                hatch.attach(worldPosition);
            }
        }
        markShell(level, found, true);
    }

    private void detach(Level level, Structure old) {
        for (BlockPos pos : old.hatches()) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof HatchBlockEntity hatch) {
                hatch.detach(worldPosition);
            }
        }
        markShell(level, old, false);
    }

    /**
     * Sets or clears {@code formed} on every shell block. Clients only, and no neighbour updates:
     * a look is not a change, and a wall of casings telling each other about it would set every
     * controller in reach walking its box again.
     */
    private void markShell(Level level, Structure box, boolean formed) {
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            Requirement want = requirementAt(box.min(), box.max(), pos);
            if (want != Requirement.SHELL && want != Requirement.SPECIAL || pos.equals(worldPosition) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(MultiblockCasingBlock.FORMED)) {
                continue;
            }
            BlockState marked = shellState(box, pos, state.setValue(MultiblockCasingBlock.FORMED, formed), formed);
            if (marked != state) {
                level.setBlock(pos, marked, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (structure != null) {
            tag.put(KEY_STRUCTURE, structure.save());
        }
        // A copy: the tag keeps the array it is handed, and the chunk is written on another thread later.
        tag.putIntArray(KEY_PREVIEW, preview.clone());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Before the chassis loads: it sizes the buffer from the interior, and then fills it.
        structure = tag.contains(KEY_STRUCTURE, Tag.TAG_COMPOUND) ? Structure.load(tag.getCompound(KEY_STRUCTURE)) : null;
        int[] savedPreview = tag.getIntArray(KEY_PREVIEW);
        if (savedPreview.length == preview.length) {
            System.arraycopy(savedPreview, 0, preview, 0, preview.length);
        }
        super.loadAdditional(tag, registries);
    }

    /**
     * A formed box: its two corners, and where the hatches are.
     *
     * @param min the corner with the smallest coordinates, a casing
     * @param max the corner with the largest, also a casing
     */
    public record Structure(BlockPos min, BlockPos max, List<BlockPos> hatches, List<BlockPos> redstoneHatches) {
        private static final String KEY_MIN = "Min";
        private static final String KEY_MAX = "Max";
        private static final String KEY_HATCHES = "Hatches";
        private static final String KEY_REDSTONE = "Redstone";

        public int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        public int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        public int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }

        /** Blocks inside the shell. */
        public int interior() {
            return (sizeX() - 2) * (sizeY() - 2) * (sizeZ() - 2);
        }

        /** Blocks on the lowest interior layer: the floor a furnace is heated from. */
        public int floorArea() {
            return (sizeX() - 2) * (sizeZ() - 2);
        }

        /** The lowest interior layer, corner to corner. */
        public Iterable<BlockPos> floor() {
            return BlockPos.betweenClosed(min.getX() + 1, min.getY() + 1, min.getZ() + 1,
                    max.getX() - 1, min.getY() + 1, max.getZ() - 1);
        }

        /** Inside the box, shell included. */
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        /** Strictly inside: what the box is built round, not the shell. */
        public boolean isInside(BlockPos pos) {
            return pos.getX() > min.getX() && pos.getX() < max.getX()
                    && pos.getY() > min.getY() && pos.getY() < max.getY()
                    && pos.getZ() > min.getZ() && pos.getZ() < max.getZ();
        }

        /** Whether a position inside the box is part of its shell rather than its interior. */
        public static boolean onSurface(BlockPos min, BlockPos max, BlockPos pos) {
            return pos.getX() == min.getX() || pos.getX() == max.getX()
                    || pos.getY() == min.getY() || pos.getY() == max.getY()
                    || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
        }

        /** The outer size as a player would say it: {@code 5×7×5}. */
        public String describe() {
            return sizeX() + "×" + sizeY() + "×" + sizeZ();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(KEY_MIN, min.asLong());
            tag.putLong(KEY_MAX, max.asLong());
            tag.putLongArray(KEY_HATCHES, hatches.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray(KEY_REDSTONE, redstoneHatches.stream().mapToLong(BlockPos::asLong).toArray());
            return tag;
        }

        public static Structure load(CompoundTag tag) {
            return new Structure(BlockPos.of(tag.getLong(KEY_MIN)), BlockPos.of(tag.getLong(KEY_MAX)),
                    positions(tag.getLongArray(KEY_HATCHES)), positions(tag.getLongArray(KEY_REDSTONE)));
        }

        private static List<BlockPos> positions(long[] packed) {
            return Arrays.stream(packed).mapToObj(BlockPos::of).toList();
        }
    }
}
