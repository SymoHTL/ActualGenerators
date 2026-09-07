package dev.symo.actualgenerators.generator;

import dev.symo.actualgenerators.machine.multiblock.CoilPiece;
import net.minecraft.core.SectionPos;
import java.util.List;
import java.util.ArrayList;
import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.SidedEnergyWrapper;
import dev.symo.actualgenerators.machine.SidedFluidHandler;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.menu.GeothermalTapMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Taps the heat pockets under its plate of Geothermal Casing, laid on the ground.
 *
 * <p>Real geothermal: the heat is down there, it is finite, and it is not everywhere. A tap makes
 * its rating only while the {@link HeatPockets pockets} of the chunks its plate lies in have
 * heat in them, and only near the world floor — full within a configured depth of it, fading to
 * nothing a configured distance up, measured at the plate, so a plate laid on bedrock is what
 * earns the whole rating. A plate across a chunk corner drinks from all four pockets, an even
 * share from each; every tap in a chunk shares its pocket, a drained one regrows at a trickle,
 * and a chunk that rolled no pocket gives nothing at all: relocate.
 *
 * <p>It is a flat multiblock, not a box: a square plate of {@link ModBlocks#GEOTHERMAL_CASING
 * Geothermal Casing}, five to a configured size on a side and always odd, with a 3×3 cap of
 * Machine Casing centred on top of it and this controller in the middle of one of the cap's
 * sides, facing out. The plate is the bore: every plate block is one more share of the
 * configured FE/t, and the buffer, the hatch rate and the corium tank grow with it. A bigger
 * plate drains the pocket that much faster, which is the trade. No upgrades, no tier; a warm-up
 * ramp, like every generator paid by the world. Hatches go in the cap: energy leaves through
 * energy hatches, corium through fluid hatches, and a bucket on the controller takes a bucket's
 * worth.
 *
 * <p>As it works it leaves <b>Corium</b> behind, a millibucket for every configured FE, in a
 * tank of its own. Nothing puts anything in, and a full tank simply stops collecting.
 */
public class GeothermalTapBlockEntity extends MultiblockControllerBlockEntity implements MenuProvider {
    private static final String KEY_TANK = "Tank";
    private static final String KEY_FEE = "Fee";

    /** The plate is one block thick and the cap one more. */
    private static final int HEIGHT = 2;
    /** The smallest plate: one ring round the cap. */
    private static final int MIN_PLATE = 5;

    /**
     * The corium tank. Its capacity follows the plate and the config, so it is set fresh on every
     * fill: {@code FluidTank.fill} reads the field, never {@code getCapacity()}.
     */
    private final FluidTank tank = new FluidTank(1) {
        @Override
        public int getCapacity() {
            return tankCapacity();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            setCapacity(tankCapacity());
            return super.fill(resource, action);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    /** The tank as the hatches and the world see it: out, never in. */
    private final IFluidHandler tankOut = new SidedFluidHandler(tank, IoMode.OUTPUT);
    /** Energy hatches hand this out: the buffer, one way. */
    private final IEnergyStorage hatchEnergy = new SidedEnergyWrapper(energy, IoMode.OUTPUT);

    /** FE made since the last millibucket of corium. */
    private long fee;
    /** The pockets under the plate, summed, as of the last tick, for the readouts. */
    private long pocketRemaining;
    private long pocketCapacity;
    /** The chunks the plate lies in, for the plate it was worked out for. */
    private List<ChunkPos> chunks = List.of();
    private @Nullable Structure chunksFor;

    public GeothermalTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GEOTHERMAL_TAP.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.GEOTHERMAL_CAPACITY, 40_000L),
                () -> ServerConfig.valueOr(ServerConfig.GEOTHERMAL_TRANSFER, 400));
    }

    // ------------------------------------------------------------------ the shape

    @Override
    public int minSize(Extent extent) {
        return extent == Extent.HEIGHT ? HEIGHT : MIN_PLATE;
    }

    /** Odd on a side, so the cap sits centred; an even config value rounds down. */
    @Override
    public int maxSize(Extent extent) {
        if (extent == Extent.HEIGHT) {
            return HEIGHT;
        }
        int size = Math.max(MIN_PLATE, ServerConfig.valueOr(ServerConfig.GEOTHERMAL_MAX_SIZE, 15));
        return size % 2 == 0 ? size - 1 : size;
    }

    @Override
    public int sizeStep(Extent extent) {
        return extent == Extent.HEIGHT ? 1 : 2;
    }

    @Override
    public @Nullable Block specialCasing() {
        return ModBlocks.GEOTHERMAL_CASING.get();
    }

    /** The bottom layer is the plate; on top of it only the 3×3 cap round the middle is anything. */
    @Override
    public Requirement requirementAt(BlockPos min, BlockPos max, BlockPos pos) {
        if (pos.getY() == min.getY()) {
            return Requirement.SPECIAL;
        }
        int centreX = (min.getX() + max.getX()) / 2;
        int centreZ = (min.getZ() + max.getZ()) / 2;
        return Math.abs(pos.getX() - centreX) <= 1 && Math.abs(pos.getZ() - centreZ) <= 1
                ? Requirement.SHELL
                : Requirement.FREE;
    }

    /** The plate wears the coil: each block the piece of the ring it lies on round the centre. */
    @Override
    protected BlockState shellState(Structure box, BlockPos pos, BlockState state, boolean formed) {
        if (!state.hasProperty(CoilPiece.COIL)) {
            return state;
        }
        int dx = pos.getX() - (box.min().getX() + box.max().getX()) / 2;
        int dz = pos.getZ() - (box.min().getZ() + box.max().getZ()) / 2;
        return state.setValue(CoilPiece.COIL, formed ? CoilPiece.at(dx, dz, box.sizeX() / 2) : CoilPiece.NONE);
    }

    /** The plate centred under the cap, the cap centred behind the controller. */
    @Override
    public BlockPos[] bounds(int width, int height, int depth) {
        Direction facing = facing(getBlockState());
        Direction right = facing.getClockWise();
        BlockPos plateCentre = worldPosition.relative(facing.getOpposite()).below();
        BlockPos near = plateCentre.relative(right.getOpposite(), width / 2).relative(facing, depth / 2);
        BlockPos far = plateCentre.relative(right, width / 2).relative(facing.getOpposite(), depth / 2).above();
        return corners(near, far);
    }

    /**
     * Walks the plate from the block under the cap's centre out to its edges. The cap has to be
     * centred on it, so the runs both ways have to match; an oversized plate is a plate with a
     * ring nobody counts.
     */
    @Override
    protected @Nullable BlockPos[] locate(ServerLevel level) {
        Direction facing = facing(getBlockState());
        Direction right = facing.getClockWise();
        Block plate = ModBlocks.GEOTHERMAL_CASING.get();
        BlockPos plateCentre = worldPosition.relative(facing.getOpposite()).below();
        if (!level.getBlockState(plateCentre).is(plate)) {
            return null;
        }
        int halfWidth = maxSize(Extent.WIDTH) / 2;
        int halfDepth = maxSize(Extent.DEPTH) / 2;
        int rightward = run(level, plateCentre, right, plate, halfWidth);
        int leftward = run(level, plateCentre, right.getOpposite(), plate, halfWidth);
        int forward = run(level, plateCentre, facing, plate, halfDepth);
        int backward = run(level, plateCentre, facing.getOpposite(), plate, halfDepth);
        if (rightward != leftward || forward != backward
                || 2 * rightward + 1 < minSize(Extent.WIDTH) || 2 * forward + 1 < minSize(Extent.DEPTH)) {
            return null;
        }
        BlockPos near = plateCentre.relative(right.getOpposite(), leftward).relative(facing, forward);
        BlockPos far = plateCentre.relative(right, rightward).relative(facing.getOpposite(), backward).above();
        return corners(near, far);
    }

    /** How many of {@code block} run on from {@code from} in {@code direction}, up to {@code max}. */
    private static int run(ServerLevel level, BlockPos from, Direction direction, Block block, int max) {
        for (int step = 1; step <= max; step++) {
            if (!level.getBlockState(from.relative(direction, step)).is(block)) {
                return step - 1;
            }
        }
        return max;
    }

    @Override
    public Component previewLabel(int width, int height, int depth) {
        return Component.translatable("gui.actualgenerators.tap.preview.label",
                width + "×" + height + "×" + depth, width * depth);
    }

    /** Blocks in the plate: the bore. Zero while nothing is formed. */
    public int plates() {
        Structure plate = structure();
        return plate == null ? 0 : plate.sizeX() * plate.sizeZ();
    }

    @Override
    public @Nullable IItemHandler hatchItems() {
        return null;
    }

    @Override
    public @Nullable IEnergyStorage hatchEnergy() {
        return hatchEnergy;
    }

    @Override
    public @Nullable IFluidHandler hatchFluids() {
        return tankOut;
    }

    /** Every plate block is one more share, and the buffer and hatch rate follow it. */
    @Override
    public int maxBatch() {
        return Math.max(1, plates());
    }

    @Override
    public boolean acceptsUpgrade(UpgradeType type) {
        // The plate is the upgrade.
        return false;
    }

    @Override
    protected void onStructureChanged() {
        wake();
        setChanged();
    }

    // ------------------------------------------------------------------ work

    @Override
    public boolean usesWarmupRamp() {
        return true;
    }

    @Override
    protected int baseEnergyPerTick() {
        return ratedEnergyPerTick();
    }

    /**
     * FE/t the tap is worth at full warmth: the configured rate per plate block, scaled by how
     * deep the plate lies; nothing without a plate or a pocket.
     */
    public int ratedEnergyPerTick() {
        if (!isFormed() || pocketRemaining <= 0) {
            return 0;
        }
        int base = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_FE_PER_TICK, 20);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base * (double) plates() * depthFactor()));
    }

    /** FE/t it is actually making: the rating, scaled by however warm it has got. */
    public int generatedEnergyPerTick() {
        return warmed(ratedEnergyPerTick());
    }

    @Override
    public int displayedEnergyRate() {
        return generatedEnergyPerTick();
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isFormed()) {
            return false;
        }
        HeatPockets pockets = HeatPockets.get(level);
        List<ChunkPos> chunks = tappedChunks();
        long now = level.getGameTime();
        long remaining = 0;
        long capacity = 0;
        for (ChunkPos chunk : chunks) {
            remaining += pockets.remaining(level, chunk, now);
            capacity += pockets.capacity(level, chunk);
        }
        pocketRemaining = remaining;
        pocketCapacity = capacity;
        if (energy.isFull()) {
            return false;
        }
        int want = generatedEnergyPerTick();
        if (want <= 0) {
            return false;
        }
        long drawn = drawShared(pockets, level, chunks, want, now);
        if (drawn <= 0) {
            return false;
        }
        pocketRemaining -= drawn;
        long made = energy.generate(drawn);
        collectCorium(made);
        return made > 0;
    }

    /**
     * Up to {@code want} FE out of the pockets under the plate: an even share from every one, so
     * they drain level, then whatever is still wanted from whichever of them has it, so a dry
     * chunk costs the tap nothing but its share.
     */
    public static long drawShared(HeatPockets pockets, ServerLevel level, List<ChunkPos> chunks, long want, long now) {
        long drawn = 0;
        for (ChunkPos chunk : chunks) {
            drawn += pockets.draw(level, chunk, want / chunks.size(), now);
        }
        for (ChunkPos chunk : chunks) {
            if (drawn >= want) {
                break;
            }
            drawn += pockets.draw(level, chunk, want - drawn, now);
        }
        return drawn;
    }

    /** The chunks the plate lies in, up to four; every one's pocket is tapped. Nothing while nothing stands. */
    public List<ChunkPos> tappedChunks() {
        Structure plate = structure();
        if (plate == null) {
            return List.of();
        }
        if (plate != chunksFor) {
            chunks = chunksOf(plate.min(), plate.max());
            chunksFor = plate;
        }
        return chunks;
    }

    /** Every chunk a structure spanning these corners lies in. */
    public static List<ChunkPos> chunksOf(BlockPos min, BlockPos max) {
        List<ChunkPos> found = new ArrayList<>(4);
        for (int x = SectionPos.blockToSectionCoord(min.getX()); x <= SectionPos.blockToSectionCoord(max.getX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(min.getZ()); z <= SectionPos.blockToSectionCoord(max.getZ()); z++) {
                found.add(new ChunkPos(x, z));
            }
        }
        return List.copyOf(found);
    }

    /** A millibucket of corium for every configured FE made; what the tank cannot hold is lost. */
    private void collectCorium(long made) {
        fee += made;
        long perMillibucket = Math.max(1, ServerConfig.valueOr(ServerConfig.GEOTHERMAL_FE_PER_CORIUM_MB, 100_000));
        if (fee < perMillibucket) {
            return;
        }
        int millibuckets = (int) Math.min(Integer.MAX_VALUE, fee / perMillibucket);
        fee %= perMillibucket;
        tank.fill(new FluidStack(ModFluids.CORIUM.get(), millibuckets), IFluidHandler.FluidAction.EXECUTE);
    }

    // ------------------------------------------------------------------ depth and pocket

    /** How much of the rating the plate's depth is worth, nought to one; nothing without a plate. */
    public double depthFactor() {
        Structure plate = structure();
        return plate == null || level == null ? 0 : depthFactor(plate.min().getY(), level.getMinBuildHeight());
    }

    /**
     * Full within {@code fullDepth} of the world floor, nothing {@code reach} or more above it,
     * a straight line between.
     */
    public static double depthFactor(int y, int minBuildHeight) {
        int depth = y - minBuildHeight;
        int full = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_FULL_DEPTH, 8);
        int reach = Math.max(full + 1, ServerConfig.valueOr(ServerConfig.GEOTHERMAL_REACH, 48));
        if (depth <= full) {
            return 1.0;
        }
        if (depth >= reach) {
            return 0.0;
        }
        return (reach - depth) / (double) (reach - full);
    }

    public long pocketRemaining() {
        return pocketRemaining;
    }

    public long pocketCapacity() {
        return pocketCapacity;
    }

    /** The tank grows with the plate: the configured capacity per plate block. */
    public int tankCapacity() {
        long perBlock = ServerConfig.valueOr(ServerConfig.GEOTHERMAL_TANK, 8_000);
        return (int) Math.min(Integer.MAX_VALUE, perBlock * Math.max(1, plates()));
    }

    public FluidStack corium() {
        return tank.getFluid();
    }

    @Override
    protected @Nullable IFluidHandler fluidTank() {
        return tankOut;
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.geothermal_tap");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GeothermalTapMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_TANK, tank.writeToNBT(registries, new CompoundTag()));
        tag.putLong(KEY_FEE, fee);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_TANK)) {
            tank.readFromNBT(registries, tag.getCompound(KEY_TANK));
        }
        fee = tag.getLong(KEY_FEE);
    }
}
