package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;

/**
 * The block that carries the pads — up to six of them, one per face, each independent.
 *
 * <p>Nothing here ticks. The network a pad belongs to runs the transfers; a block whose pads are
 * on no network costs exactly nothing.
 *
 * <p>On the client every loaded port registers itself, so the overlay a held linking tool draws
 * can walk the pads in view without scanning chunks for them.
 */
public class LinkPortBlockEntity extends BlockEntity {
    private static final String KEY_FACES = "Faces";

    /** Client side only: every port block currently loaded, kept by load and unload rather than by search. */
    private static final Set<LinkPortBlockEntity> CLIENT_PORTS = Collections.newSetFromMap(new WeakHashMap<>());

    private final Map<Direction, PortFace> faces = new EnumMap<>(Direction.class);
    private boolean powered;
    /** While the block reads what powers it, its own output is left out; see {@link #readPower}. */
    // Static on purpose: a sending pad beside the lamp another pad lights (or beside that pad's
    // block) read the receiver's output as its own signal and held the channel high forever after
    // one button press. While any port block reads, no pad anywhere speaks. Pads never see pads.
    private static boolean muted;

    public LinkPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGIC_PORT.get(), pos, state);
    }

    public static Collection<LinkPortBlockEntity> clientPorts() {
        return CLIENT_PORTS;
    }

    // ------------------------------------------------------------------ faces

    public @Nullable PortFace face(Direction direction) {
        return faces.get(direction);
    }

    public List<PortFace> faces() {
        return new ArrayList<>(faces.values());
    }

    public boolean hasFace(Direction direction) {
        return faces.containsKey(direction);
    }

    public int faceCount() {
        return faces.size();
    }

    /** Adds a pad. It does nothing on any channel until a player says otherwise. */
    public PortFace addFace(Direction direction) {
        PortFace face = faces.computeIfAbsent(direction, side -> new PortFace(this, side));
        setChanged();
        syncToClients();
        if (level != null) {
            invalidateCapabilities();
        }
        return face;
    }

    /** Takes a pad off, leaving its network first so the record does not go stale. */
    public void removeFace(Direction direction) {
        PortFace face = faces.remove(direction);
        if (face != null && level instanceof ServerLevel serverLevel) {
            LinkNetworkManager.get(serverLevel.getServer()).leave(face);
        }
        setChanged();
        syncToClients();
        if (level != null) {
            invalidateCapabilities();
        }
    }

    /** What a block pushing into one of this block's faces talks to: the pad on that face, or nothing. */
    public @Nullable PortPassthrough passthrough(@Nullable Direction side) {
        PortFace face = side == null ? null : faces.get(side);
        return face == null ? null : face.passthrough();
    }

    /** The pad a player is looking at, or any pad if the hit does not name one. */
    public @Nullable PortFace faceFor(Direction hitSide) {
        PortFace face = faces.get(hitSide.getOpposite());
        if (face != null) {
            return face;
        }
        face = faces.get(hitSide);
        return face != null ? face : faces.values().stream().findFirst().orElse(null);
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level == null ? Level.OVERWORLD : level.dimension(), getBlockPos());
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            CLIENT_PORTS.add(this);
        } else if (level instanceof ServerLevel serverLevel) {
            // The network may have been renamed or recentred while this chunk was away.
            LinkNetworkManager manager = LinkNetworkManager.get(serverLevel.getServer());
            faces.values().forEach(manager::refresh);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_PORTS.remove(this);
        if (level instanceof ServerLevel serverLevel) {
            LinkNetworkManager manager = LinkNetworkManager.get(serverLevel.getServer());
            faces.values().forEach(manager::unloaded);
        }
    }

    // ------------------------------------------------------------------ redstone

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            // Who may run changed with it, for every pad on the block.
            faces.values().forEach(PortFace::padChanged);
        }
    }

    /** Anything that could give a sleeping network something to do says so, for every pad. */
    public void wakeNetworks() {
        faces.values().forEach(PortFace::wakeNetwork);
    }

    /** The block behind a pad changed what it holds: only a pad reading that is worth waking for. */
    public void wakeIfSensing(BlockPos neighbour) {
        for (PortFace face : faces.values()) {
            if (face.targetPos().equals(neighbour) && face.sensesContents()) {
                face.wakeNetwork();
            }
        }
    }

    /** The level the pad on a face puts into the block in front of it, strongly; nothing while muted. */
    public int signalToward(Direction face) {
        PortFace pad = muted ? null : faces.get(face);
        return pad == null ? 0 : pad.signalOut();
    }

    /** The highest level any pad here puts out: what the block gives off weakly on every side, a lever's way. */
    public int signalAround() {
        if (muted) {
            return 0;
        }
        int most = 0;
        for (PortFace pad : faces.values()) {
            most = Math.max(most, pad.signalOut());
        }
        return most;
    }

    /**
     * Whether a neighbour powers this block, not counting what this block powers itself. A
     * receiving pad strongly powers the block it is on, and that block would report the signal
     * straight back, so a pad lighting a lamp would read "powered" and hold up its other links.
     */
    public boolean readPower(Level level) {
        return whileMuted(() -> level.hasNeighborSignal(worldPosition) ? 1 : 0) != 0;
    }

    int whileMuted(IntSupplier reading) {
        boolean was = muted;
        muted = true;
        try {
            return reading.getAsInt();
        } finally {
            muted = was;
        }
    }

    public void invalidateTargets() {
        faces.values().forEach(PortFace::invalidateTargets);
    }

    // ------------------------------------------------------------------ contents

    public void dropContents(Level level, BlockPos pos) {
        for (PortFace face : faces.values()) {
            drop(level, pos, face.upgradeHandler());
            drop(level, pos, face.filterHandler());
        }
    }

    private static void drop(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    /** Every pad leaves its network — called when the block goes. */
    public void leaveAllNetworks() {
        if (level instanceof ServerLevel serverLevel) {
            LinkNetworkManager manager = LinkNetworkManager.get(serverLevel.getServer());
            faces.values().forEach(manager::leave);
        }
    }

    // ------------------------------------------------------------------ persistence

    public void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (PortFace face : faces.values()) {
            list.add(face.save(registries));
        }
        tag.put(KEY_FACES, list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        faces.clear();
        for (Tag entry : tag.getList(KEY_FACES, Tag.TAG_COMPOUND)) {
            CompoundTag faceTag = (CompoundTag) entry;
            Direction direction = PortFace.directionOf(faceTag);
            PortFace face = new PortFace(this, direction);
            face.load(faceTag, registries);
            faces.put(direction, face);
        }
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
}
