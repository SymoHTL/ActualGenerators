package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.item.TierUpgradeItem;
import dev.symo.actualgenerators.machine.MachineTier;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One pad: everything a single face of a Logic Port block is and knows.
 *
 * <p>A port block holds up to six of these, one per direction, each on its own network with its
 * own channels, filters, upgrades and redstone. A machine has six faces and a base has gaps one
 * block wide, so a pad that ate a whole block space to serve one neighbour was the wrong unit.
 *
 * <p>What the pad does is said per channel and per kind ({@link PortChannel}): sixteen channels,
 * each carrying any set of items, fluids and energy for the whole network, and for each kind on
 * each of them the pad either sends, receives, or stays out of it, with its own numbers, filter
 * and redstone. A fresh pad does nothing on any of them until told to.
 *
 * <p>A pad keeps a copy of what its network is called and where its centre is. The network
 * manager writes it on every change of membership, and it is what the client draws and what the
 * reach check reads, on both sides.
 *
 * <p>A face has no inventory, tank or buffer: it reads and writes the block in front of it through
 * that block's own sided capability, so a machine's face rules still apply and a pad never gets to
 * go round them.
 */
public class PortFace {
    /** Sixteen channels because there are sixteen dyes, and a colour is quicker to read than a number. */
    public static final int CHANNELS = 16;

    public static final int UPGRADE_SLOTS = 3;
    public static final int SLOT_RANGE = 0;
    public static final int SLOT_CARD = 1;
    public static final int SLOT_TIER = 2;

    /** A Filter slot per channel for items, one for fluids and one for the redstone tab's stock; energy has none. */
    private static final int FILTERS_PER_CHANNEL = 3;
    public static final int FILTER_SLOTS = CHANNELS * FILTERS_PER_CHANNEL;

    private static final String KEY_FACE = "Face";
    private static final String KEY_NETWORK = "Network";
    private static final String KEY_NETWORK_NAME = "NetworkName";
    private static final String KEY_NETWORK_HOME = "NetworkHome";
    private static final String KEY_NETWORK_X = "NetworkX";
    private static final String KEY_NETWORK_Y = "NetworkY";
    private static final String KEY_NETWORK_Z = "NetworkZ";
    private static final String KEY_NETWORK_COLOUR = "NetworkColour";
    /** The shape before a channel carried more than one kind: an ordinal per channel, -1 for none. */
    private static final String KEY_KINDS = "Kinds";
    private static final String KEY_KIND_MASKS = "KindMasks";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_UPGRADES = "Upgrades";
    private static final String KEY_FILTERS = "Filters";
    private static final String KEY_PLACER = "Placer";
    private static final String KEY_SIGNAL_OUT = "SignalOut";
    private static final String KEY_LABEL = "Label";

    private final LinkPortBlockEntity host;
    private final Direction direction;

    private @Nullable UUID networkId;
    /** Who placed the pad, so a network that stops admitting them can take it off; null for one nobody placed. */
    private @Nullable UUID placer;
    private String networkName = "";
    private @Nullable ResourceKey<Level> networkHome;
    private Vec3 networkCenter = Vec3.ZERO;
    private int networkColour;
    /**
     * What each channel carries as far as this pad knows: what a player set before the pad was on
     * any network, or a copy of the last network's kinds after it left one. The first network the
     * pad joins takes these for every channel nobody has given a job yet.
     */
    private final int[] localKinds = new int[CHANNELS];

    /** By channel, then by kind ordinal. */
    private final PortChannel[][] links = new PortChannel[CHANNELS][TransferKind.all().length];

    /** The redstone level this pad puts into the block behind it: the highest channel it receives on. */
    private int signalOut;
    /** The pad's label. Every pad with the same label on the same network is set up the same; see {@link LinkNetworkManager#publish}. */
    private String label = "";

    private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return PortFace.this.isUpgradeValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_RANGE ? maxRangeUpgrades() : 1;
        }

        @Override
        protected void onLoad() {
            // A pad saved when this handler had fewer slots would otherwise come back too narrow.
            if (stacks.size() < UPGRADE_SLOTS) {
                NonNullList<ItemStack> widened = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
                for (int slot = 0; slot < stacks.size(); slot++) {
                    widened.set(slot, stacks.get(slot));
                }
                stacks = widened;
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }
    };

    /** One Filter item per channel and filterable kind, in the slot the window shows for that link. */
    private final ItemStackHandler filters = new ItemStackHandler(FILTER_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof FilterItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onLoad() {
            // A pad saved with one Filter per channel spreads them out: each one was for items. One
            // saved with two per channel, items and fluids, moves them over to make room for the
            // redstone tab's.
            if (stacks.size() < FILTER_SLOTS) {
                int perChannel = stacks.size() >= CHANNELS * 2 ? 2 : 1;
                NonNullList<ItemStack> widened = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
                for (int slot = 0; slot < stacks.size() && slot < CHANNELS * perChannel; slot++) {
                    widened.set((slot / perChannel) * FILTERS_PER_CHANNEL + slot % perChannel, stacks.get(slot));
                }
                stacks = widened;
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }
    };

    private @Nullable BlockCapabilityCache<IItemHandler, @Nullable Direction> itemTarget;
    private @Nullable BlockCapabilityCache<IFluidHandler, @Nullable Direction> fluidTarget;
    private @Nullable BlockCapabilityCache<IEnergyStorage, @Nullable Direction> energyTarget;

    /** What a block pushing into this pad talks to; made when first asked for. */
    private @Nullable PortPassthrough passthrough;
    /** A push that lands on another port and would come straight back stops here instead. */
    private static int pushing;

    public PortFace(LinkPortBlockEntity host, Direction direction) {
        this.host = host;
        this.direction = direction;
        for (int index = 0; index < CHANNELS; index++) {
            for (TransferKind kind : TransferKind.all()) {
                links[index][kind.ordinal()] = new PortChannel(this, index, kind);
            }
        }
    }

    public LinkPortBlockEntity host() {
        return host;
    }

    public Direction direction() {
        return direction;
    }

    public @Nullable Level level() {
        return host.getLevel();
    }

    public BlockPos hostPos() {
        return host.getBlockPos();
    }

    public PortRef ref() {
        return new PortRef(host.globalPos(), direction);
    }

    // ------------------------------------------------------------------ channels

    /** What this pad does with one kind on one channel. */
    public PortChannel link(int channel, TransferKind kind) {
        return links[Math.floorMod(channel, CHANNELS)][kind.ordinal()];
    }

    /** Whether this pad sends or receives anything on any channel at all. */
    public boolean participatesAnywhere() {
        for (PortChannel[] perKind : links) {
            for (PortChannel link : perKind) {
                if (link.participates()) {
                    return true;
                }
            }
        }
        return false;
    }

    public ItemStackHandler filterHandler() {
        return filters;
    }

    /** Where a channel's Filter for a kind sits in {@link #filterHandler()}; -1 for energy, which has none. */
    public static int filterSlot(int channel, TransferKind kind) {
        int column = switch (kind) {
            case ITEM -> 0;
            case FLUID -> 1;
            case REDSTONE -> 2;
            case ENERGY -> -1;
        };
        return column < 0 ? -1 : Math.floorMod(channel, CHANNELS) * FILTERS_PER_CHANNEL + column;
    }

    public ItemStack filter(int channel, TransferKind kind) {
        int slot = filterSlot(channel, kind);
        return slot < 0 ? ItemStack.EMPTY : filters.getStackInSlot(slot);
    }

    // ------------------------------------------------------------------ the block it serves

    public BlockPos targetPos() {
        return hostPos().relative(direction);
    }

    public @Nullable IItemHandler targetItems() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (itemTarget == null) {
            itemTarget = BlockCapabilityCache.create(
                    Capabilities.ItemHandler.BLOCK, serverLevel, targetPos(), direction.getOpposite());
        }
        return itemTarget.getCapability();
    }

    public @Nullable IFluidHandler targetFluids() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (fluidTarget == null) {
            fluidTarget = BlockCapabilityCache.create(
                    Capabilities.FluidHandler.BLOCK, serverLevel, targetPos(), direction.getOpposite());
        }
        return fluidTarget.getCapability();
    }

    public @Nullable IEnergyStorage targetEnergy() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (energyTarget == null) {
            energyTarget = BlockCapabilityCache.create(
                    Capabilities.EnergyStorage.BLOCK, serverLevel, targetPos(), direction.getOpposite());
        }
        return energyTarget.getCapability();
    }

    public void invalidateTargets() {
        itemTarget = null;
        fluidTarget = null;
        energyTarget = null;
    }

    // ------------------------------------------------------------------ pushed into the pad

    /** What a block pushing into this pad's face talks to; see {@link PortPassthrough}. */
    public PortPassthrough passthrough() {
        if (passthrough == null) {
            passthrough = new PortPassthrough(this);
        }
        return passthrough;
    }

    private @Nullable LinkNetworkManager manager() {
        return networkId != null && level() instanceof ServerLevel serverLevel
                ? LinkNetworkManager.get(serverLevel.getServer())
                : null;
    }

    /**
     * Whether something pushed into the pad may leave through a channel: the link's PU switch is
     * on and its redstone allows. PU is its own switch because EX is the timed pull, and a hopper
     * behind the pad would be pulled from as well as pushing. What a push skips is the clock, the
     * Amount, the keep-behind and the fee.
     */
    private boolean pushesOn(PortChannel link) {
        return link.pushEnabled() && link.canRun();
    }

    /**
     * Items the block behind this pad pushed into it, sent on at once.
     *
     * @return what nobody took
     */
    public ItemStack pushItems(ItemStack stack, boolean simulate) {
        LinkNetworkManager manager = manager();
        if (manager == null || stack.isEmpty() || pushing > 0 || !inReach()) {
            return stack;
        }
        ItemStack remaining = stack;
        pushing++;
        try {
            for (int channel = 0; channel < CHANNELS && !remaining.isEmpty(); channel++) {
                PortChannel link = link(channel, TransferKind.ITEM);
                if (!pushesOn(link)) {
                    continue;
                }
                List<PortFace> receivers = manager.receiversFor(this, channel, TransferKind.ITEM, simulate);
                if (!receivers.isEmpty()) {
                    remaining = LinkTransfer.pushItems(link,
                            manager.channelDistribution(networkId, channel, TransferKind.ITEM), remaining,
                            receivers, channel, simulate);
                }
            }
        } finally {
            pushing--;
        }
        return remaining;
    }

    /** @return how much of the pushed fluid went somewhere, in millibuckets */
    public int pushFluid(FluidStack fluid, IFluidHandler.FluidAction action) {
        LinkNetworkManager manager = manager();
        if (manager == null || fluid.isEmpty() || pushing > 0 || !inReach()) {
            return 0;
        }
        int carried = 0;
        pushing++;
        try {
            for (int channel = 0; channel < CHANNELS && carried < fluid.getAmount(); channel++) {
                PortChannel link = link(channel, TransferKind.FLUID);
                if (!pushesOn(link)) {
                    continue;
                }
                List<PortFace> receivers = manager.receiversFor(this, channel, TransferKind.FLUID, action.simulate());
                if (!receivers.isEmpty()) {
                    carried += LinkTransfer.pushFluid(link,
                            manager.channelDistribution(networkId, channel, TransferKind.FLUID),
                            fluid.copyWithAmount(fluid.getAmount() - carried), receivers, channel, action);
                }
            }
        } finally {
            pushing--;
        }
        return carried;
    }

    /** @return how much of the pushed FE went somewhere */
    public int pushEnergy(int amount, boolean simulate) {
        LinkNetworkManager manager = manager();
        if (manager == null || amount <= 0 || pushing > 0 || !inReach()) {
            return 0;
        }
        int carried = 0;
        pushing++;
        try {
            for (int channel = 0; channel < CHANNELS && carried < amount; channel++) {
                PortChannel link = link(channel, TransferKind.ENERGY);
                if (!pushesOn(link)) {
                    continue;
                }
                List<PortFace> receivers = manager.receiversFor(this, channel, TransferKind.ENERGY, simulate);
                if (!receivers.isEmpty()) {
                    carried += LinkTransfer.pushEnergy(
                            manager.channelDistribution(networkId, channel, TransferKind.ENERGY), amount - carried,
                            receivers, simulate);
                }
            }
        } finally {
            pushing--;
        }
        return carried;
    }

    // ------------------------------------------------------------------ redstone over the network

    /** The redstone level this pad gives the block behind it, from the channels it receives on. */
    public int signalOut() {
        return signalOut;
    }

    /**
     * The network's word on what this pad emits. The neighbours are told the way a lever tells
     * them: the port's own, and those of the block the pad is on, so a lamp behind a powered
     * stone hears it too.
     *
     * @return whether it changed
     */
    public boolean setSignalOut(int level) {
        int clamped = Math.clamp(level, 0, 15);
        if (clamped == signalOut) {
            return false;
        }
        signalOut = clamped;
        host.setChanged();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.updateNeighborsAt(hostPos(), ModBlocks.LOGIC_PORT.get());
            serverLevel.updateNeighborsAt(targetPos(), ModBlocks.LOGIC_PORT.get());
        }
        return true;
    }

    /**
     * What this pad reads for one channel, 0 to 15, by the link's {@link SignalSource}: the power
     * reaching the pad's block from any side (a lever beside it, dust into it, the block behind it
     * with a lever on it: what a lamp in the pad's place would see), or the comparator reading or
     * the stock of the block behind it. The block's own output is left out of the reading, or a
     * pad that sends and receives on one channel would hold itself high forever.
     */
    public int readSignal(int channel) {
        Level level = level();
        if (level == null) {
            return 0;
        }
        PortChannel link = link(channel, TransferKind.REDSTONE);
        BlockPos target = targetPos();
        return switch (link.signalSource()) {
            case SIGNAL -> host.whileMuted(() -> level.getBestNeighborSignal(hostPos()));
            case COMPARATOR -> {
                BlockState state = level.getBlockState(target);
                yield state.hasAnalogOutputSignal() ? state.getAnalogOutputSignal(level, target) : 0;
            }
            case STOCK -> stockLevel(link);
        };
    }

    /** A comparator's arithmetic over what the link's filter lets through: full at the link's number, one at the first. */
    private int stockLevel(PortChannel link) {
        IItemHandler handler = targetItems();
        if (handler == null) {
            return 0;
        }
        ItemStack filter = link.filter();
        long count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && FilterItem.allowsItem(filter, stack, true) && FilterItem.allowsItem(filter, stack, false)) {
                count += stack.getCount();
            }
        }
        int full = link.keepAmount();
        if (count <= 0) {
            return 0;
        }
        if (full <= 0 || count >= full) {
            return 15;
        }
        return 1 + (int) (14 * count / full);
    }

    /** Whether a redstone link here reads what the block holds, so a change in that is worth a look. */
    public boolean sensesContents() {
        for (PortChannel[] perKind : links) {
            PortChannel link = perKind[TransferKind.REDSTONE.ordinal()];
            if (link.extractEnabled() && link.signalSource() != SignalSource.SIGNAL) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ network membership

    public @Nullable UUID networkId() {
        return networkId;
    }

    public boolean isLinked() {
        return networkId != null;
    }

    /** What the network is called, as of the last time the manager told this pad. */
    public String networkName() {
        return networkName;
    }

    /** The dimension the network's centre is in. */
    public @Nullable ResourceKey<Level> networkHome() {
        return networkHome;
    }

    /** The mean position of every pad on the network, in its home dimension. */
    public Vec3 networkCenter() {
        return networkCenter;
    }

    /** The network's colour, RGB without alpha; zero until the manager has said. */
    public int networkColour() {
        return networkColour;
    }

    /** What a channel carries on this pad while it is on no network: one bit per kind, by ordinal. */
    public int localKinds(int channel) {
        return localKinds[Math.floorMod(channel, CHANNELS)];
    }

    public boolean carriesLocally(int channel, TransferKind kind) {
        return (localKinds(channel) & ChannelSettings.bit(kind)) != 0;
    }

    public void setLocalKind(int channel, TransferKind kind, boolean on) {
        int index = Math.floorMod(channel, CHANNELS);
        int bit = ChannelSettings.bit(kind);
        localKinds[index] = on ? localKinds[index] | bit : localKinds[index] & ~bit;
        changed();
    }

    /** All sixteen at once, from the network a pad is leaving. */
    void rememberKinds(int[] kinds) {
        System.arraycopy(kinds, 0, localKinds, 0, CHANNELS);
        changed();
    }

    public @Nullable UUID placer() {
        return placer;
    }

    public void setPlacer(@Nullable UUID placer) {
        this.placer = placer;
        changed();
    }

    public String label() {
        return label;
    }

    /** Writes the label on the pad and nothing more; the network side of a label is {@link LinkNetworkManager#relabel}. */
    public void setLabel(String label) {
        String clean = LinkNetworkManager.cleanLabel(label);
        if (clean.equals(this.label)) {
            return;
        }
        this.label = clean;
        host.setChanged();
        host.syncToClients();
    }

    /** What a channel carries as this pad sees it: the network's word once it is on one, its own memory before that. */
    public int currentKinds(int channel) {
        if (networkId != null && level() instanceof ServerLevel serverLevel) {
            return LinkNetworkManager.get(serverLevel.getServer()).channelKinds(networkId, channel);
        }
        return localKinds(channel);
    }

    void setNetworkId(@Nullable UUID id) {
        this.networkId = id;
        // Whatever the links had sorted was for the old network.
        for (PortChannel[] perKind : links) {
            for (PortChannel link : perKind) {
                link.setReceivers(null);
            }
        }
        if (id == null) {
            networkName = "";
            networkHome = null;
            networkCenter = Vec3.ZERO;
            networkColour = 0;
            setSignalOut(0);
        }
        host.setChanged();
        host.syncToClients();
    }

    /** The manager's word on the network, written whenever a pad joins or leaves it. */
    void setNetworkInfo(String name, int colour, ResourceKey<Level> home, Vec3 center) {
        if (name.equals(networkName) && colour == networkColour && home.equals(networkHome)
                && center.equals(networkCenter)) {
            return;
        }
        networkName = name;
        networkColour = colour;
        networkHome = home;
        networkCenter = center;
        host.setChanged();
        host.syncToClients();
    }

    // ------------------------------------------------------------------ upgrades

    public ItemStackHandler upgradeHandler() {
        return upgrades;
    }

    /** Anything on the pad changed: save it, show it, and give a sleeping network a reason to look. */
    void changed() {
        host.setChanged();
        host.syncToClients();
        padChanged();
    }

    /** Something about this pad that decides who sends where moved: the network re-sorts, and runs. */
    public void padChanged() {
        if (level() instanceof ServerLevel serverLevel && networkId != null) {
            LinkNetworkManager.get(serverLevel.getServer()).padChanged(networkId);
        }
    }

    /** Anything that could give a sleeping network something to do says so. */
    public void wakeNetwork() {
        if (level() instanceof ServerLevel serverLevel && networkId != null) {
            LinkNetworkManager.get(serverLevel.getServer()).wake(networkId);
        }
    }

    // ------------------------------------------------------------------ reach

    public static int maxRangeUpgrades() {
        return ServerConfig.valueOr(ServerConfig.LINK_MAX_RANGE_UPGRADES, 4);
    }

    public int rangeUpgrades() {
        return upgrades.getStackInSlot(SLOT_RANGE).getCount();
    }

    /** True while the pad holds the card that trades a hard constraint for unlimited reach. */
    public boolean isUnbound() {
        return !upgrades.getStackInSlot(SLOT_CARD).isEmpty();
    }

    // ------------------------------------------------------------------ tier

    /** The grade of this pad, from the tier upgrade in its slot. */
    public MachineTier tier() {
        return TierUpgradeItem.tierOf(upgrades.getStackInSlot(SLOT_TIER));
    }

    /**
     * The most one send of this pad may carry, of the kind the channel carries.
     *
     * <p>The base is config per kind and the tier multiplies it. A pad's rate is this divided by
     * {@link #delayFloor()} at its fastest, and the player may set both lower.
     */
    public int amountCeiling(@Nullable TransferKind kind) {
        int base = kind == TransferKind.FLUID
                ? ServerConfig.valueOr(ServerConfig.LINK_AMOUNT_MILLIBUCKETS, 1_000)
                : kind == TransferKind.ENERGY
                ? ServerConfig.valueOr(ServerConfig.LINK_AMOUNT_ENERGY, 20_000)
                : ServerConfig.valueOr(ServerConfig.LINK_AMOUNT_ITEMS, 32);
        long raised = (long) base * tier().linkAmountMultiplier();
        return (int) Math.clamp(raised, 1, PortChannel.ceilingFor(kind));
    }

    /** The shortest wait between sends this pad allows, in ticks. A tier buys a shorter one. */
    public int delayFloor() {
        return tier().linkDelayTicks();
    }

    public int range() {
        return ServerConfig.valueOr(ServerConfig.LINK_BASE_RANGE, 16)
                + rangeUpgrades() * ServerConfig.valueOr(ServerConfig.LINK_RANGE_PER_UPGRADE, 16);
    }

    public boolean reachesOtherDimensions() {
        return rangeUpgrades() >= maxRangeUpgrades();
    }

    /**
     * Whether this pad is close enough to its network's centre to take part.
     *
     * <p>Reach is measured from the centre of all the pads, not pad to pad: the centre moves as
     * pads join and leave, and every pad is judged against the same point. A pad in another
     * dimension than the centre needs the top range tier; the unbound card reaches anywhere in
     * the centre's dimension and never out of it.
     */
    public boolean inReach() {
        if (networkId == null || networkHome == null) {
            return false;
        }
        Level level = level();
        boolean home = level != null && level.dimension().equals(networkHome);
        if (isUnbound()) {
            return home;
        }
        if (!home) {
            return reachesOtherDimensions();
        }
        return withinReach(Vec3.atCenterOf(hostPos()), networkCenter, range());
    }

    /** The one line of geometry behind {@link #inReach()}, on its own so a test can pin it down. */
    public static boolean withinReach(Vec3 pad, Vec3 center, int range) {
        return pad.distanceToSqr(center) <= (double) range * range;
    }

    /**
     * What the upgrade slots will take. The card is refused outright when the network already has
     * one, rather than accepted and quietly ignored.
     */
    private boolean isUpgradeValid(int slot, ItemStack stack) {
        if (slot == SLOT_RANGE) {
            return stack.is(ModItems.LINK_RANGE_UPGRADE.get());
        }
        if (slot == SLOT_TIER) {
            return stack.getItem() instanceof TierUpgradeItem;
        }
        if (!stack.is(ModItems.UNBOUND_LINK_CARD.get())) {
            return false;
        }
        return !(level() instanceof ServerLevel serverLevel) || networkId == null
                || LinkNetworkManager.get(serverLevel.getServer()).unboundPortCount(networkId, ref()) == 0;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByte(KEY_FACE, (byte) direction.get3DDataValue());
        if (placer != null) {
            tag.putUUID(KEY_PLACER, placer);
        }
        if (signalOut != 0) {
            tag.putByte(KEY_SIGNAL_OUT, (byte) signalOut);
        }
        if (!label.isEmpty()) {
            tag.putString(KEY_LABEL, label);
        }
        if (networkId != null) {
            tag.putUUID(KEY_NETWORK, networkId);
            tag.putString(KEY_NETWORK_NAME, networkName);
            if (networkHome != null) {
                tag.putString(KEY_NETWORK_HOME, networkHome.location().toString());
            }
            tag.putDouble(KEY_NETWORK_X, networkCenter.x);
            tag.putDouble(KEY_NETWORK_Y, networkCenter.y);
            tag.putDouble(KEY_NETWORK_Z, networkCenter.z);
            tag.putInt(KEY_NETWORK_COLOUR, networkColour);
        }
        byte[] kinds = new byte[CHANNELS];
        for (int channel = 0; channel < CHANNELS; channel++) {
            kinds[channel] = (byte) localKinds[channel];
        }
        tag.putByteArray(KEY_KIND_MASKS, kinds);
        ListTag list = new ListTag();
        for (PortChannel[] perKind : links) {
            for (PortChannel link : perKind) {
                if (!link.isBlank()) {
                    list.add(link.save());
                }
            }
        }
        tag.put(KEY_CHANNELS, list);
        tag.put(KEY_UPGRADES, upgrades.serializeNBT(registries));
        tag.put(KEY_FILTERS, filters.serializeNBT(registries));
        return tag;
    }

    public static Direction directionOf(CompoundTag tag) {
        return Direction.from3DDataValue(tag.getByte(KEY_FACE));
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        networkId = tag.hasUUID(KEY_NETWORK) ? tag.getUUID(KEY_NETWORK) : null;
        placer = tag.hasUUID(KEY_PLACER) ? tag.getUUID(KEY_PLACER) : null;
        signalOut = Math.clamp(tag.getByte(KEY_SIGNAL_OUT), 0, 15);
        label = LinkNetworkManager.cleanLabel(tag.getString(KEY_LABEL));
        networkName = tag.getString(KEY_NETWORK_NAME);
        ResourceLocation home = ResourceLocation.tryParse(tag.getString(KEY_NETWORK_HOME));
        networkHome = home == null ? null : ResourceKey.create(Registries.DIMENSION, home);
        networkCenter = new Vec3(tag.getDouble(KEY_NETWORK_X), tag.getDouble(KEY_NETWORK_Y), tag.getDouble(KEY_NETWORK_Z));
        networkColour = tag.getInt(KEY_NETWORK_COLOUR);
        byte[] masks = tag.getByteArray(KEY_KIND_MASKS);
        byte[] legacy = tag.getByteArray(KEY_KINDS);
        for (int channel = 0; channel < CHANNELS; channel++) {
            if (channel < masks.length) {
                localKinds[channel] = masks[channel] & ChannelSettings.ALL_KINDS;
            } else {
                localKinds[channel] = channel < legacy.length && legacy[channel] >= 0
                        ? ChannelSettings.bit(TransferKind.byOrdinal(legacy[channel])) : 0;
            }
        }
        for (int index = 0; index < CHANNELS; index++) {
            for (TransferKind kind : TransferKind.all()) {
                links[index][kind.ordinal()] = new PortChannel(this, index, kind);
            }
        }
        for (Tag entry : tag.getList(KEY_CHANNELS, Tag.TAG_COMPOUND)) {
            CompoundTag linkTag = (CompoundTag) entry;
            int channel = PortChannel.indexOf(linkTag);
            TransferKind kind = PortChannel.kindOf(linkTag);
            if (kind != null) {
                link(channel, kind).load(linkTag);
            } else {
                // Written when a link was per channel: the same roles for every kind, each its own count.
                for (TransferKind each : TransferKind.all()) {
                    link(channel, each).load(linkTag);
                }
            }
        }
        if (tag.contains(KEY_UPGRADES)) {
            upgrades.deserializeNBT(registries, tag.getCompound(KEY_UPGRADES));
        }
        if (tag.contains(KEY_FILTERS)) {
            filters.deserializeNBT(registries, tag.getCompound(KEY_FILTERS));
        }
    }
}
