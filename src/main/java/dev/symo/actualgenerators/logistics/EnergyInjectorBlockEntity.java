package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.SideConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.EnergyInjectorMenu;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What pays for a wireless network.
 *
 * <p>Moving something without a pipe costs FE, and this is where that FE comes from: put an
 * injector on a network with the linking tool exactly as you would a pad, keep it fed, and every
 * pad on that network works. A network with no injector on it, or with flat ones, moves nothing —
 * which is the whole point of charging for it.
 *
 * <p>It is a buffer and nothing else: no recipe, no speed, no work. Energy upgrades only, same as
 * every other block that stores rather than converts. It keeps a copy of its network's name and
 * colour for its window and for the overlay, the way a pad does.
 */
public class EnergyInjectorBlockEntity extends MachineBlockEntity implements MenuProvider {
    private static final String KEY_NETWORK = "Network";
    private static final String KEY_NETWORK_NAME = "NetworkName";
    private static final String KEY_NETWORK_COLOUR = "NetworkColour";
    private static final String KEY_PLACER = "Placer";

    /** Client side only: every loaded injector, for the overlay a held linking tool draws. */
    private static final Set<EnergyInjectorBlockEntity> CLIENT_INJECTORS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private @Nullable UUID networkId;
    private @Nullable UUID placer;
    private String networkName = "";
    private int networkColour;

    public EnergyInjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_INJECTOR.get(),
                pos,
                state,
                () -> ServerConfig.valueOr(ServerConfig.LINK_INJECTOR_CAPACITY, 200_000L),
                () -> ServerConfig.valueOr(ServerConfig.LINK_INJECTOR_TRANSFER, 5_000),
                defaultSides());
    }

    public static Collection<EnergyInjectorBlockEntity> clientInjectors() {
        return CLIENT_INJECTORS;
    }

    /**
     * Power in through every face; nothing ever comes back out of one on purpose.
     *
     * <p>The one block in the mod that pulls on its own initiative, and deliberately: every other
     * machine leaves both auto flags off so a placed machine never surprises anyone, but a buffer
     * whose entire job is to be fed and which cannot push anything anywhere is not a surprise. It
     * is also the only way out of a circle — the network that would carry FE to an injector is the
     * network that injector has to pay for first.
     */
    private static SideConfig defaultSides() {
        SideConfig config = SideConfig.of(IoMode.DISABLED, IoMode.DISABLED, IoMode.DISABLED);
        config.setAll(TransferKind.ENERGY, IoMode.INPUT);
        config.setAuto(TransferKind.ENERGY, false, true);
        return config;
    }

    @Override
    public boolean acceptsUpgrade(UpgradeType type) {
        return type == UpgradeType.ENERGY;
    }

    @Override
    protected boolean tickWork(ServerLevel level, BlockPos pos, BlockState state) {
        // Nothing of its own to do. The networks it is linked to spend what it holds, when they run.
        return false;
    }

    @Override
    protected int baseEnergyPerTick() {
        // It neither makes power nor spends it on itself; the network spends it, out of the buffer.
        return 0;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            CLIENT_INJECTORS.add(this);
        } else if (level instanceof ServerLevel serverLevel) {
            LinkNetworkManager.get(serverLevel.getServer()).refreshInjector(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_INJECTORS.remove(this);
        if (level instanceof ServerLevel serverLevel) {
            LinkNetworkManager.get(serverLevel.getServer()).unloadedInjector(this);
        }
    }

    // ------------------------------------------------------------------ network membership

    /** Who placed the injector, so a network that stops admitting them can take it off. */
    public @Nullable UUID placer() {
        return placer;
    }

    public void setPlacer(@Nullable UUID placer) {
        this.placer = placer;
        setChanged();
    }

    public @Nullable UUID networkId() {
        return networkId;
    }

    public boolean isLinked() {
        return networkId != null;
    }

    /** What the network is called, as of the last time the manager told this injector. */
    public String networkName() {
        return networkName;
    }

    /** The network's colour, RGB without alpha. */
    public int networkColour() {
        return networkColour;
    }

    void setNetworkId(@Nullable UUID id) {
        this.networkId = id;
        if (id == null) {
            networkName = "";
            networkColour = 0;
        }
        sync();
    }

    /** The manager's word on the network, written whenever it changes. */
    void setNetworkInfo(String name, int colour) {
        if (name.equals(networkName) && colour == networkColour) {
            return;
        }
        networkName = name;
        networkColour = colour;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level == null ? Level.OVERWORLD : level.dimension(), getBlockPos());
    }

    /** How many pads this injector is paying for. Zero means it is powering nothing yet. */
    public int servedPorts() {
        return level instanceof ServerLevel serverLevel
                ? LinkNetworkManager.get(serverLevel.getServer()).portCount(networkId)
                : 0;
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.actualgenerators.energy_injector");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new EnergyInjectorMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkId != null) {
            tag.putUUID(KEY_NETWORK, networkId);
            tag.putString(KEY_NETWORK_NAME, networkName);
            tag.putInt(KEY_NETWORK_COLOUR, networkColour);
        }
        if (placer != null) {
            tag.putUUID(KEY_PLACER, placer);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        networkId = tag.hasUUID(KEY_NETWORK) ? tag.getUUID(KEY_NETWORK) : null;
        networkName = tag.getString(KEY_NETWORK_NAME);
        networkColour = tag.getInt(KEY_NETWORK_COLOUR);
        placer = tag.hasUUID(KEY_PLACER) ? tag.getUUID(KEY_PLACER) : null;
    }
}
