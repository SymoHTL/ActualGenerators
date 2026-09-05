package dev.symo.actualgenerators.logistics;

import net.minecraft.ChatFormatting;
import com.mojang.authlib.GameProfile;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.machine.TransferKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.UUID;

/**
 * Every Logic Port network on the server, and the thing that actually moves anything.
 *
 * <p>A network is made by a player, has a name, and is the thing pads and injectors join. Its
 * centre is the mean position of its pads, moved every time one joins or leaves, and a pad has to
 * be within its own reach of that centre to take part. The name and the centre are copied onto
 * every pad on the network as they change, which is what the client draws.
 *
 * <p>Pads do not tick; networks do, and only the ones with something to do. A network with no
 * sending pad never runs at all; one whose last pass moved nothing drops to a much longer interval
 * until an event wakes it. Placing, breaking, configuring or powering a pad, or a neighbour of one
 * changing, is such an event.
 *
 * <p>A network is sixteen channels, XNet's way: each channel carries any set of items, fluids,
 * energy and redstone for every pad on the network, and each pad says per channel and kind
 * whether it sends on it, receives on it, or stays out. A fresh network carries nothing on any of
 * them until told. Redstone is the odd one: nothing is moved, a level is read at one end and put
 * out at the other, and it costs no FE ({@link #runSignals}).
 *
 * <p>Every sending pad has its own clock: an Amount it carries per send and a Delay it waits
 * between sends. A pass runs the senders whose wait is over and the network then sleeps until the
 * next one is due, so the rate a player set is the rate a player gets, and the server does the
 * arithmetic exactly as often as the pads asked for and no more.
 *
 * <p>Moving things wirelessly costs FE, and that FE comes from the Energy Injectors on the network.
 * A network with no injector, or with empty ones, moves nothing.
 */
@EventBusSubscriber(modid = ActualGenerators.MODID)
public class LinkNetworkManager extends SavedData {
    private static final String NAME = ActualGenerators.MODID + "_link_networks";

    private static final String KEY_NETWORKS = "Networks";
    private static final String KEY_ID = "Id";
    private static final String KEY_NAME = "Name";
    private static final String KEY_HOME = "Home";
    private static final String KEY_COLOUR = "Colour";
    private static final String KEY_PORTS = "Ports";
    private static final String KEY_INJECTORS = "Injectors";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_DIMENSION = "Dim";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_FACE = "Face";
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_OWNER_NAME = "OwnerName";
    private static final String KEY_SHARED = "Shared";
    private static final String KEY_MEMBERS = "Members";
    private static final String KEY_LABELS = "Labels";
    /** A label is a word or two on a pad line, not a sentence. */
    public static final int MAX_LABEL_LENGTH = 24;

    public static final int MAX_NAME_LENGTH = 24;

    private static final SavedData.Factory<LinkNetworkManager> FACTORY =
            new SavedData.Factory<>(LinkNetworkManager::new, LinkNetworkManager::load, null);

    private final Map<UUID, Network> networks = new HashMap<>();
    private @Nullable MinecraftServer server;

    /** What the picker shows for one network. */
    public record Summary(UUID id, String name, int colour, int pads, int injectors, Access access) {
        public static final StreamCodec<ByteBuf, Summary> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Summary::id,
                ByteBufCodecs.STRING_UTF8, Summary::name,
                ByteBufCodecs.INT, Summary::colour,
                ByteBufCodecs.VAR_INT, Summary::pads,
                ByteBufCodecs.VAR_INT, Summary::injectors,
                Access.STREAM_CODEC, Summary::access,
                Summary::new);
    }

    /** Whose a network is, and whether everyone may use it. An empty owner name is nobody's. */
    /** Whose a network is, as a window shows it; {@code mine} is whether the player asking may run it (owner, or operator). */
    public record Access(String ownerName, boolean shared, boolean mine) {
        public static final StreamCodec<ByteBuf, Access> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Access::ownerName,
                ByteBufCodecs.BOOL, Access::shared,
                ByteBufCodecs.BOOL, Access::mine,
                Access::new);
    }

    /** One invited player, by id and the name they had when invited. */
    public record Member(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.STRING_UTF8, Member::name,
                Member::new);
    }

    /** One network: its name, who is on it, what its channels carry, where its centre is, and when it last did anything. */
    private static final class Network {
        private String name = "";
        /** RGB without alpha. */
        private int colour;
        /** Who made it; null for one nobody made, which is everyone's. */
        private @Nullable UUID owner;
        private String ownerName = "";
        /** Whether everyone may use it. A network is its maker's alone until they say otherwise. */
        private boolean shared;
        /** The players the owner let in, by id, with the name they had at the time. */
        private final Map<UUID, String> members = new LinkedHashMap<>();
        private ResourceKey<Level> home = Level.OVERWORLD;
        private Vec3 center = Vec3.ZERO;
        private final Set<PortRef> ports = new LinkedHashSet<>();
        /**
         * The pads in loaded chunks, looked up once and kept until one joins, leaves, loads or
         * unloads: a push into a pad asks for them every time, and three hundred chunk lookups
         * per hopper item added up.
         */
        private @Nullable List<PortFace> loaded;
        /**
         * The pads sending on each channel and kind, out of the loaded ones, and every link
         * holding a receiver list sorted for this network ({@link PortChannel#receivers()}).
         * Both live until a pad changes, comes or goes, and a pass then costs its senders'
         * sends and nothing else.
         */
        private final List<PortFace>[] senders = newSenderLists();
        private final List<PortChannel> cachers = new ArrayList<>();
        private final Set<GlobalPos> injectors = new LinkedHashSet<>();
        /** The settings every label stands for, by label: what a pad wearing one takes when it joins or loads. */
        private final Map<String, CompoundTag> labels = new HashMap<>();
        /** The injectors in loaded chunks, kept like {@link #loaded}. */
        private @Nullable List<EnergyInjectorBlockEntity> loadedInjectors;
        private final ChannelSettings[] channels = new ChannelSettings[PortFace.CHANNELS];
        private long nextPassAt;
        /** The soonest any of its senders is due again, found during the last pass. */
        private long nextDueAt;
        /** Whether any pad put a redstone level out last pass, so a pass that finds none still clears them. */
        private boolean signalling;

        Network() {
            for (int index = 0; index < channels.length; index++) {
                channels[index] = new ChannelSettings();
            }
        }

        @SuppressWarnings("unchecked")
        private static List<PortFace>[] newSenderLists() {
            return new List[PortFace.CHANNELS * TransferKind.all().length];
        }

        /** A pad came or went: the loaded list is stale, and everything sorted from it. */
        void changed() {
            loaded = null;
            loadedInjectors = null;
            touch();
        }

        /** A pad's switches, numbers, upgrades or redstone moved: every list sorted from them is stale. */
        void touch() {
            Arrays.fill(senders, null);
            for (PortChannel link : cachers) {
                link.setReceivers(null);
            }
            cachers.clear();
        }

        ChannelSettings channel(int index) {
            return channels[Math.floorMod(index, channels.length)];
        }
    }

    public static LinkNetworkManager get(MinecraftServer server) {
        LinkNetworkManager manager = server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        manager.server = server;
        return manager;
    }

    // ------------------------------------------------------------------ networks

    /** Makes an empty network nobody owns, centred nowhere until something joins. */
    public UUID create(String name, ResourceKey<Level> home) {
        return create(name, home, null);
    }

    /** Makes an empty network owned by the player who named it, and private to them until shared. */
    public UUID create(String name, ResourceKey<Level> home, @Nullable Player owner) {
        UUID id = UUID.randomUUID();
        Network network = new Network();
        network.name = cleanName(name, id);
        network.colour = defaultColour(id);
        network.home = home;
        if (owner != null) {
            network.owner = owner.getUUID();
            network.ownerName = owner.getGameProfile().getName();
        }
        networks.put(id, network);
        setDirty();
        return id;
    }

    private String cleanName(String name, UUID id) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            trimmed = "Network " + (networks.size() + 1);
        }
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    public boolean exists(@Nullable UUID id) {
        return id != null && networks.containsKey(id);
    }

    public String networkName(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? "" : network.name;
    }

    public Vec3 center(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? Vec3.ZERO : network.center;
    }

    public ResourceKey<Level> home(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? Level.OVERWORLD : network.home;
    }

    /** The network's colour, RGB without alpha; zero for no such network. */
    public int colour(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? 0 : network.colour;
    }

    /** Paints a network. The colour is what the overlay, the picker and every window show for it. */
    public void setColour(@Nullable UUID id, int rgb) {
        Network network = id == null ? null : networks.get(id);
        if (network == null) {
            return;
        }
        network.colour = rgb & 0xFFFFFF;
        setDirty();
        broadcast(network);
    }

    /** A colour out of the id, so a network is told apart before anyone has picked one for it. */
    public static int defaultColour(UUID id) {
        return Mth.hsvToRgb((id.hashCode() & 0xFFFF) / 65536.0F, 0.65F, 1.0F) & 0xFFFFFF;
    }

    /** Every network there is, by name, for the picker. */
    public List<Summary> summaries() {
        return list(null);
    }

    /** The networks a player may use, by name, for their picker, each saying whether it is theirs to run. */
    public List<Summary> summaries(Player viewer) {
        List<Summary> found = list(viewer);
        found.removeIf(summary -> !admits(networks.get(summary.id()), viewer));
        return found;
    }

    private List<Summary> list(@Nullable Player viewer) {
        List<Summary> found = new ArrayList<>(networks.size());
        for (Map.Entry<UUID, Network> entry : networks.entrySet()) {
            Network network = entry.getValue();
            found.add(new Summary(entry.getKey(), network.name, network.colour, network.ports.size(), network.injectors.size(),
                    new Access(network.ownerName, network.shared, viewer != null && owns(network, viewer))));
        }
        found.sort(Comparator.comparing((Summary summary) -> summary.name().toLowerCase(Locale.ROOT))
                .thenComparing(summary -> summary.id().toString()));
        return found;
    }

    /**
     * Everything the overview window shows for one network, or null for one that is not there.
     * Every pad on the record is listed, loaded or not; what a pad does is read off the loaded
     * ones only, since an unloaded pad is a position and a face and nothing else.
     */
    public @Nullable NetworkOverview overview(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        if (network == null) {
            return null;
        }
        List<PortFace> loaded = loadedPorts(network);
        Map<PortRef, PortFace> byRef = new HashMap<>();
        for (PortFace face : loaded) {
            byRef.put(face.ref(), face);
        }
        List<NetworkOverview.Pad> pads = new ArrayList<>(network.ports.size());
        for (PortRef ref : network.ports) {
            PortFace face = byRef.get(ref);
            if (face == null) {
                pads.add(new NetworkOverview.Pad(ref.at(), ref.face(), false, false, 0, List.of(), ""));
                continue;
            }
            List<NetworkOverview.Link> links = new ArrayList<>();
            for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
                for (TransferKind kind : TransferKind.all()) {
                    PortChannel link = face.link(channel, kind);
                    if (link.participates()) {
                        links.add(new NetworkOverview.Link(channel, kind,
                                link.extractEnabled(), link.insertEnabled(), link.pushEnabled()));
                    }
                }
            }
            pads.add(new NetworkOverview.Pad(ref.at(), ref.face(), true, face.inReach(), face.range(), links, face.label()));
        }
        List<EnergyInjectorBlockEntity> loadedInjectors = loadedInjectors(network);
        List<NetworkOverview.Injector> injectors = new ArrayList<>(network.injectors.size());
        for (GlobalPos at : network.injectors) {
            EnergyInjectorBlockEntity found = null;
            for (EnergyInjectorBlockEntity injector : loadedInjectors) {
                if (injector.globalPos().equals(at)) {
                    found = injector;
                    break;
                }
            }
            injectors.add(found == null
                    ? new NetworkOverview.Injector(at, false, 0, 0)
                    : new NetworkOverview.Injector(at, true, found.energyStorage().stored(), found.energyStorage().capacity()));
        }
        List<NetworkOverview.Channel> channels = new ArrayList<>();
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            ChannelSettings settings = network.channels[channel];
            if (!settings.carriesAnything()) {
                continue;
            }
            List<Integer> spreads = new ArrayList<>(TransferKind.all().length);
            for (TransferKind kind : TransferKind.all()) {
                spreads.add(settings.distribution(kind).ordinal());
            }
            int senders = 0;
            int receivers = 0;
            for (PortFace face : loaded) {
                boolean sends = false;
                boolean receives = false;
                for (TransferKind kind : TransferKind.all()) {
                    if (settings.carries(kind)) {
                        PortChannel link = face.link(channel, kind);
                        sends |= link.extractEnabled() || link.pushEnabled();
                        receives |= link.insertEnabled();
                    }
                }
                senders += sends ? 1 : 0;
                receivers += receives ? 1 : 0;
            }
            channels.add(new NetworkOverview.Channel(channel, settings.kinds(), spreads, senders, receivers));
        }
        return new NetworkOverview(id, network.name, network.colour, new Access(network.ownerName, network.shared, false),
                members(id), network.home, network.center, pads, injectors, channels);
    }

    // ------------------------------------------------------------------ whose it is

    /** The maker, or an operator: a server's admins run every network, so an owner who left leaves nothing stuck. */
    private static boolean owns(Network network, Player player) {
        return network.owner == null || network.owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    private static boolean admits(Network network, Player player) {
        return network.shared || owns(network, player) || network.members.containsKey(player.getUUID());
    }

    /** The same question of a player who is not here: whoever placed a pad. Operators are not known by id. */
    private static boolean admitsId(Network network, @Nullable UUID player) {
        return player == null || network.shared || network.owner == null || network.owner.equals(player)
                || network.members.containsKey(player);
    }

    /** Whether the player made the network. One nobody made is everyone's to run. */
    public boolean isOwner(@Nullable UUID id, Player player) {
        Network network = id == null ? null : networks.get(id);
        return network != null && owns(network, player);
    }

    /** Whether the player may put a block on the network and change what its channels carry. */
    public boolean canAccess(@Nullable UUID id, Player player) {
        Network network = id == null ? null : networks.get(id);
        return network != null && admits(network, player);
    }

    /**
     * Whether a player may use a block on a network at all: open its window, click in it, put it
     * on or off a network. Nothing on an unlinked block is anyone's yet. A refusal tells the
     * player why on the action bar. Breaking the block is never refused here: that is a claim
     * mod's job.
     */
    public static boolean admits(ServerPlayer player, @Nullable UUID networkId) {
        if (networkId == null || get(player.serverLevel().getServer()).canAccess(networkId, player)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("gui.actualgenerators.link.no_access"), true);
        return false;
    }

    /** Makes a network nobody owns the player's, and private to them: the way in for the old ones. */
    public boolean claim(@Nullable UUID id, Player player) {
        Network network = id == null ? null : networks.get(id);
        if (network == null || network.owner != null) {
            return false;
        }
        network.owner = player.getUUID();
        network.ownerName = player.getGameProfile().getName();
        evict(network);
        setDirty();
        return true;
    }

    public boolean isShared(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        return network != null && network.shared;
    }

    /** Opens the network to everyone, or closes it again; the owner's call. */
    public boolean setShared(@Nullable UUID id, Player by, boolean shared) {
        Network network = id == null ? null : networks.get(id);
        if (network == null || !owns(network, by)) {
            return false;
        }
        network.shared = shared;
        if (!shared) {
            evict(network);
        }
        setDirty();
        return true;
    }

    /** Lets a player onto the network; the owner's call, and the owner needs no letting. */
    public boolean invite(@Nullable UUID id, Player by, UUID member, String memberName) {
        Network network = id == null ? null : networks.get(id);
        if (network == null || !owns(network, by) || member.equals(network.owner)) {
            return false;
        }
        network.members.put(member, memberName);
        setDirty();
        return true;
    }

    /**
     * Lets a player onto the network by name: one who is online, or one the server has met
     * before. A name it has never seen is looked up the way the whitelist would.
     */
    public boolean invite(@Nullable UUID id, Player by, String name) {
        Optional<GameProfile> profile = profileFor(name);
        return profile.isPresent() && invite(id, by, profile.get().getId(), profile.get().getName());
    }

    private Optional<GameProfile> profileFor(String name) {
        String wanted = name.strip();
        if (server == null || wanted.isEmpty()) {
            return Optional.empty();
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getGameProfile().getName().equalsIgnoreCase(wanted)) {
                return Optional.of(online.getGameProfile());
            }
        }
        return server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(wanted);
    }

    /** Takes a player off the network again, and their pads with them; the owner's call. */
    public boolean removeMember(@Nullable UUID id, Player by, UUID member) {
        Network network = id == null ? null : networks.get(id);
        if (network == null || !owns(network, by) || network.members.remove(member) == null) {
            return false;
        }
        evict(network);
        setDirty();
        return true;
    }

    /** Hands the network to one of the invited; the owner's call, and the old owner stays invited. */
    public boolean transfer(@Nullable UUID id, Player by, UUID newOwner) {
        Network network = id == null ? null : networks.get(id);
        if (network == null || !owns(network, by) || !network.members.containsKey(newOwner)) {
            return false;
        }
        String newName = network.members.remove(newOwner);
        if (network.owner != null) {
            network.members.put(network.owner, network.ownerName);
        }
        network.owner = newOwner;
        network.ownerName = newName;
        evict(network);
        setDirty();
        return true;
    }

    /**
     * Takes every loaded pad and injector placed by someone the network no longer admits off it.
     * The ones in chunks that are away go when they load ({@link #refresh}); one nobody placed
     * (from before pads remembered) stays.
     */
    private void evict(Network network) {
        for (PortFace face : loadedPorts(network)) {
            if (!admitsId(network, face.placer())) {
                leave(face);
            }
        }
        for (EnergyInjectorBlockEntity injector : loadedInjectors(network)) {
            if (!admitsId(network, injector.placer())) {
                leaveInjector(injector);
            }
        }
    }

    /** Who the owner let onto the network, in the order they were let on. */
    public List<Member> members(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        if (network == null) {
            return List.of();
        }
        List<Member> found = new ArrayList<>(network.members.size());
        for (Map.Entry<UUID, String> entry : network.members.entrySet()) {
            found.add(new Member(entry.getKey(), entry.getValue()));
        }
        return found;
    }

    // ------------------------------------------------------------------ membership

    /**
     * Puts a pad on a network, leaving whatever it was on before.
     *
     * @param id the network to join, or null to make a fresh one
     * @return the network the pad is now on, or null if there is no such network
     */
    public @Nullable UUID join(PortFace face, @Nullable UUID id) {
        if (id != null && !networks.containsKey(id)) {
            return null;
        }
        if (id != null && id.equals(face.networkId())) {
            return id;
        }
        leave(face);
        UUID joined = id != null ? id : create("", dimensionOf(face));
        Network network = networks.get(joined);
        network.ports.add(face.ref());
        network.changed();
        face.setNetworkId(joined);
        // A pad set up before it joined brings its channel kinds along, for every channel nobody
        // on the network has given a job yet. A channel that already carries something keeps it.
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            int local = face.localKinds(channel);
            if (local != 0 && !network.channel(channel).carriesAnything()) {
                network.channel(channel).setKinds(local);
            }
        }
        recenter(joined, network);
        adoptLabel(network, face);
        setDirty();
        wake(joined);
        return joined;
    }

    private static ResourceKey<Level> dimensionOf(PortFace face) {
        Level level = face.level();
        return level == null ? Level.OVERWORLD : level.dimension();
    }

    public void leave(PortFace face) {
        UUID id = face.networkId();
        if (id == null) {
            return;
        }
        Network network = networks.get(id);
        if (network != null) {
            // The pad keeps what its channels carried, so it can be set up elsewhere as it was.
            int[] kinds = new int[PortFace.CHANNELS];
            for (int channel = 0; channel < kinds.length; channel++) {
                kinds[channel] = network.channel(channel).kinds();
            }
            face.rememberKinds(kinds);
        }
        face.setNetworkId(null);
        if (network != null) {
            network.ports.remove(face.ref());
            network.changed();
            recenter(id, network);
        }
        setDirty();
    }

    /** Same two operations for the block that pays for the traffic. */
    public @Nullable UUID joinInjector(EnergyInjectorBlockEntity injector, @Nullable UUID id) {
        if (id != null && !networks.containsKey(id)) {
            return null;
        }
        leaveInjector(injector);
        UUID joined = id != null ? id : create("", injector.globalPos().dimension());
        Network network = networks.get(joined);
        network.injectors.add(injector.globalPos());
        network.changed();
        injector.setNetworkId(joined);
        injector.setNetworkInfo(network.name, network.colour);
        setDirty();
        wake(joined);
        return joined;
    }

    public void leaveInjector(EnergyInjectorBlockEntity injector) {
        UUID id = injector.networkId();
        if (id == null) {
            return;
        }
        Network network = networks.get(id);
        if (network != null) {
            network.injectors.remove(injector.globalPos());
            network.changed();
        }
        injector.setNetworkId(null);
        setDirty();
    }

    /**
     * Forgets a network, with its channels, colour, labels and members: the owner's call from the
     * picker, and only once nothing is on it. Nothing forgets a network on its own: one whose last
     * pad left keeps its setup for the next pad, since a network is a thing a player made and named.
     */
    public boolean delete(@Nullable UUID id, Player player) {
        Network network = id == null ? null : networks.get(id);
        if (network == null || !owns(network, player) || !network.ports.isEmpty() || !network.injectors.isEmpty()) {
            return false;
        }
        networks.remove(id);
        setDirty();
        return true;
    }

    /**
     * A pad that has just been loaded catches up on what its network is called and where its
     * centre is now, in case both moved while its chunk was away.
     */
    public void refresh(PortFace face) {
        UUID id = face.networkId();
        if (id == null) {
            return;
        }
        Network network = networks.get(id);
        if (network == null) {
            face.setNetworkId(null);
            return;
        }
        // Placed by someone the network stopped admitting while this chunk was away: off it now.
        if (!admitsId(network, face.placer())) {
            leave(face);
            return;
        }
        network.ports.add(face.ref());
        network.changed();
        face.setNetworkInfo(network.name, network.colour, network.home, network.center);
        adoptLabel(network, face);
    }

    /** A pad's chunk is going away, or the pad is: its network's loaded list is stale. */
    public void unloaded(PortFace face) {
        Network network = face.networkId() == null ? null : networks.get(face.networkId());
        if (network != null) {
            network.changed();
        }
    }

    /** Same for an injector. */
    public void unloadedInjector(EnergyInjectorBlockEntity injector) {
        Network network = injector.networkId() == null ? null : networks.get(injector.networkId());
        if (network != null) {
            network.changed();
        }
    }

    /** Same for an injector coming back into a loaded chunk. */
    public void refreshInjector(EnergyInjectorBlockEntity injector) {
        UUID id = injector.networkId();
        if (id == null) {
            return;
        }
        Network network = networks.get(id);
        if (network == null) {
            injector.setNetworkId(null);
            return;
        }
        if (!admitsId(network, injector.placer())) {
            leaveInjector(injector);
            return;
        }
        network.injectors.add(injector.globalPos());
        network.changed();
        injector.setNetworkInfo(network.name, network.colour);
    }

    /** Recomputes the centre and tells every loaded pad and injector on the network about it. */
    private void recenter(UUID id, Network network) {
        computeCenter(network);
        broadcast(network);
    }

    /** Every loaded pad and injector hears the network's name, colour and centre. */
    private void broadcast(Network network) {
        for (PortFace face : loadedPorts(network)) {
            face.setNetworkInfo(network.name, network.colour, network.home, network.center);
        }
        for (EnergyInjectorBlockEntity injector : loadedInjectors(network)) {
            injector.setNetworkInfo(network.name, network.colour);
        }
    }

    /**
     * The centre is the mean of the pads in the network's home dimension, and home is wherever
     * most of its pads are. Injectors do not pull on it: they pay for the network, they are not
     * part of its reach.
     */
    private static void computeCenter(Network network) {
        Map<ResourceKey<Level>, Integer> counts = new HashMap<>();
        for (PortRef ref : network.ports) {
            counts.merge(ref.at().dimension(), 1, Integer::sum);
        }
        ResourceKey<Level> home = network.home;
        int best = counts.getOrDefault(home, 0);
        for (Map.Entry<ResourceKey<Level>, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                home = entry.getKey();
            }
        }
        network.home = home;

        double x = 0;
        double y = 0;
        double z = 0;
        int counted = 0;
        for (PortRef ref : network.ports) {
            if (ref.at().dimension().equals(home)) {
                BlockPos pos = ref.at().pos();
                x += pos.getX() + 0.5;
                y += pos.getY() + 0.5;
                z += pos.getZ() + 0.5;
                counted++;
            }
        }
        network.center = counted == 0 ? Vec3.ZERO : new Vec3(x / counted, y / counted, z / counted);
    }

    /** Brings the next pass forward. Cheap enough to call from any event that might matter. */
    public void wake(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        if (network != null) {
            network.nextPassAt = 0;
        }
    }

    /**
     * A pad's switches, numbers, filters, upgrades or redstone changed: the lists the network
     * sorted from them are dropped, and a pass is due. A neighbour merely changing wakes the
     * network ({@link #wake}) without that, or a redstone clock next to a pad would have every
     * sender re-sorting three hundred receivers every tick.
     */
    public void padChanged(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        if (network != null) {
            network.touch();
            network.nextPassAt = 0;
        }
    }

    // ------------------------------------------------------------------ labels

    /** A label as stored: trimmed, no formatting codes, at most {@link #MAX_LABEL_LENGTH} characters. */
    public static String cleanLabel(@Nullable String label) {
        String clean = label == null ? null : ChatFormatting.stripFormatting(label);
        clean = clean == null ? "" : clean.strip();
        return clean.length() > MAX_LABEL_LENGTH ? clean.substring(0, MAX_LABEL_LENGTH) : clean;
    }

    /** Every label in use on a network, in alphabetical order. */
    public List<String> labels(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        if (network == null) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(network.labels.keySet());
        labels.sort(String.CASE_INSENSITIVE_ORDER);
        return labels;
    }

    /**
     * A labelled pad was changed by hand: its settings become the label's, and every other loaded
     * pad on the network wearing the label takes them, filters and upgrades out of the player's
     * inventory. Unloaded ones take them when their chunk comes back ({@link #refresh}). What the
     * others could not be given is counted, for the window's banner.
     */
    public PadSnapshot.Applied publish(PortFace face, @Nullable Player source) {
        Network network = face.networkId() == null ? null : networks.get(face.networkId());
        String label = face.label();
        if (network == null || label.isEmpty()) {
            return PadSnapshot.Applied.NONE;
        }
        PadSnapshot snapshot = PadSnapshot.of(face);
        network.labels.put(label, snapshot.tag());
        setDirty();
        PadSnapshot.Applied applied = PadSnapshot.Applied.NONE;
        for (PortFace peer : new ArrayList<>(loadedPorts(network))) {
            if (peer != face && label.equals(peer.label())) {
                applied = applied.plus(snapshot.applyTo(peer, source, false));
            }
        }
        return applied;
    }

    /**
     * A pad takes a label. When the network already knows it, {@code push} says which way the
     * settings go: this pad's onto the label and every pad wearing it, or the label's onto this
     * pad. A label the network has not seen takes this pad's settings either way. Off a network,
     * the label is only written on the pad, and counts when the pad joins one.
     */
    public PadSnapshot.Applied relabel(PortFace face, String label, boolean push, @Nullable Player source) {
        String clean = cleanLabel(label);
        face.setLabel(clean);
        Network network = face.networkId() == null ? null : networks.get(face.networkId());
        if (network == null || clean.isEmpty()) {
            return PadSnapshot.Applied.NONE;
        }
        CompoundTag stored = network.labels.get(clean);
        if (push || stored == null) {
            return publish(face, source);
        }
        return new PadSnapshot(stored).applyTo(face, source, false);
    }

    /** A labelled pad arriving on a network, by joining or by its chunk loading, is set up like its label; a new label is set up like the pad. */
    private void adoptLabel(Network network, PortFace face) {
        String label = face.label();
        if (label.isEmpty()) {
            return;
        }
        CompoundTag stored = network.labels.get(label);
        if (stored == null) {
            network.labels.put(label, PadSnapshot.of(face).tag());
            setDirty();
        } else if (!stored.equals(PadSnapshot.of(face).tag())) {
            new PadSnapshot(stored).applyTo(face, null, false);
        }
    }

    /** How many pads a network has on record, loaded or not. Zero for a network nobody made. */
    public int portCount(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? 0 : network.ports.size();
    }

    public int injectorCount(@Nullable UUID id) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? 0 : network.injectors.size();
    }

    // ------------------------------------------------------------------ channels

    /** What a channel carries, one bit per kind by ordinal; zero for a channel nobody has given a job. */
    public int channelKinds(@Nullable UUID id, int channel) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? 0 : network.channel(channel).kinds();
    }

    public boolean channelCarries(@Nullable UUID id, int channel, TransferKind kind) {
        return (channelKinds(id, channel) & ChannelSettings.bit(kind)) != 0;
    }

    /** Gives a channel one more job, or takes one away, for every pad on the network at once. */
    public void setChannelKind(@Nullable UUID id, int channel, TransferKind kind, boolean on) {
        Network network = id == null ? null : networks.get(id);
        if (network != null) {
            network.channel(channel).setCarries(kind, on);
            setDirty();
            wake(id);
        }
    }

    public Distribution channelDistribution(@Nullable UUID id, int channel, TransferKind kind) {
        Network network = id == null ? null : networks.get(id);
        return network == null ? Distribution.NEAREST_FIRST : network.channel(channel).distribution(kind);
    }

    public void setChannelDistribution(@Nullable UUID id, int channel, TransferKind kind, Distribution distribution) {
        Network network = id == null ? null : networks.get(id);
        if (network != null) {
            network.channel(channel).setDistribution(kind, distribution);
            setDirty();
            wake(id);
        }
    }

    /**
     * How many <em>other</em> pads on this network are holding an unbound card.
     *
     * <p>The card is the trade-off upgrade: unlimited reach inside one dimension, and a network
     * tolerates exactly one of them.
     */
    public int unboundPortCount(@Nullable UUID id, PortRef except) {
        Network network = id == null ? null : networks.get(id);
        if (network == null || server == null) {
            return 0;
        }
        int found = 0;
        for (PortFace face : loadedPorts(network)) {
            if (!face.ref().equals(except) && face.isUnbound()) {
                found++;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        LinkNetworkManager manager = get(server);
        if (!manager.networks.isEmpty()) {
            manager.tick(server.overworld().getGameTime());
        }
    }

    private void tick(long time) {
        int idle = Math.max(1, ServerConfig.valueOr(ServerConfig.LINK_IDLE_INTERVAL_TICKS, 40));

        for (Network network : networks.values()) {
            if (time < network.nextPassAt) {
                continue;
            }
            network.nextDueAt = Long.MAX_VALUE;
            boolean moved = runPass(network, time);
            // A network that moved something comes back when its next sender is due; one that did
            // not backs off, since nothing tells it when a chest far away has something in it again.
            network.nextPassAt = moved ? Math.max(time + 1, network.nextDueAt) : time + idle;
        }
    }

    /**
     * Runs a network's pass now, as the tick would; for measuring what one costs.
     *
     * @return whether anything moved
     */
    public boolean passNow(@Nullable UUID id, long time) {
        Network network = id == null ? null : networks.get(id);
        return network != null && runPass(network, time);
    }

    private boolean runPass(Network network, long time) {
        List<PortFace> ports = loadedPorts(network);
        runSignals(network, ports, time);
        if (ports.size() < 2) {
            return false;
        }
        PowerBank power = new PowerBank(loadedInjectors(network));
        if (power.available() <= 0) {
            return false;
        }

        boolean moved = false;
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            ChannelSettings settings = network.channel(channel);
            int kinds = settings.kinds();
            if (kinds == 0) {
                continue;
            }
            for (TransferKind kind : TransferKind.material()) {
                if ((kinds & ChannelSettings.bit(kind)) == 0) {
                    continue;
                }
                Distribution distribution = settings.distribution(kind);
                for (PortFace sender : sendersFor(network, channel, kind, ports)) {
                    PortChannel link = sender.link(channel, kind);
                    // Each link keeps its own clock: Amount every Delay ticks is what it was
                    // promised, and a pass that comes early for it leaves it alone.
                    if (link.dueAt(time)) {
                        link.sentAt(time);
                        List<PortFace> receivers = receiversFor(network, sender, channel, kind, distribution, true);
                        if (!receivers.isEmpty()) {
                            moved |= LinkTransfer.run(kind, channel, distribution, sender, receivers, power);
                        }
                    }
                    network.nextDueAt = Math.min(network.nextDueAt, link.nextSendAt());
                }
            }
        }
        return moved;
    }

    /**
     * Redstone over the network. A channel's level is the highest any of its senders reads off the
     * block behind it, and every pad receiving on the channel puts that level into the block it is
     * on. Nothing is moved, so nothing is paid for: this runs before the fee is looked at, and a
     * network with no injector still carries a signal.
     *
     * <p>Senders are read on every pass rather than on their clocks: a button's pulse is ten ticks
     * long and a Delay would miss it. A change next to a pad wakes the network the same tick, so a
     * lever reaches the far lamp in one; and while any channel carries redstone the network looks
     * again at least every idle interval, which is what catches a chest filled from above, where
     * no comparator update reaches.
     */
    private void runSignals(Network network, List<PortFace> ports, long time) {
        int[] levels = new int[PortFace.CHANNELS];
        boolean any = false;
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            if (!network.channel(channel).carries(TransferKind.REDSTONE)) {
                continue;
            }
            any = true;
            for (PortFace sender : sendersFor(network, channel, TransferKind.REDSTONE, ports)) {
                levels[channel] = Math.max(levels[channel], sender.readSignal(channel));
            }
        }
        if (!any && !network.signalling) {
            return;
        }
        boolean signalling = false;
        for (PortFace face : ports) {
            int emit = 0;
            if (any && face.inReach()) {
                for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
                    if (levels[channel] > emit) {
                        PortChannel link = face.link(channel, TransferKind.REDSTONE);
                        if (link.insertEnabled() && link.canRun()) {
                            emit = levels[channel];
                        }
                    }
                }
            }
            face.setSignalOut(emit);
            signalling |= emit > 0;
        }
        network.signalling = signalling;
        if (any) {
            int idle = Math.max(1, ServerConfig.valueOr(ServerConfig.LINK_IDLE_INTERVAL_TICKS, 40));
            network.nextDueAt = Math.min(network.nextDueAt, time + idle);
        }
    }

    /**
     * The pads of a network that are actually there right now.
     *
     * <p>A pad in an unloaded chunk is skipped and kept; one whose block is gone while the chunk
     * <em>is</em> loaded is dropped from the record, which is the only cleanup this needs.
     */
    private List<PortFace> loadedPorts(Network network) {
        if (server == null) {
            return List.of();
        }
        if (network.loaded != null) {
            return network.loaded;
        }
        List<PortFace> found = new ArrayList<>(network.ports.size());
        Iterator<PortRef> iterator = network.ports.iterator();
        while (iterator.hasNext()) {
            PortRef ref = iterator.next();
            ServerLevel level = server.getLevel(ref.at().dimension());
            if (level == null || !level.hasChunkAt(ref.at().pos())) {
                continue;
            }
            PortFace face = level.getBlockEntity(ref.at().pos()) instanceof LinkPortBlockEntity port
                    ? port.face(ref.face())
                    : null;
            if (face != null) {
                found.add(face);
            } else {
                iterator.remove();
                setDirty();
            }
        }
        network.loaded = found;
        return found;
    }

    private List<EnergyInjectorBlockEntity> loadedInjectors(Network network) {
        if (server == null) {
            return List.of();
        }
        if (network.loadedInjectors != null) {
            return network.loadedInjectors;
        }
        List<EnergyInjectorBlockEntity> found = new ArrayList<>(network.injectors.size());
        Iterator<GlobalPos> iterator = network.injectors.iterator();
        while (iterator.hasNext()) {
            GlobalPos at = iterator.next();
            ServerLevel level = server.getLevel(at.dimension());
            if (level == null || !level.hasChunkAt(at.pos())) {
                continue;
            }
            if (level.getBlockEntity(at.pos()) instanceof EnergyInjectorBlockEntity injector) {
                found.add(injector);
            } else {
                iterator.remove();
                setDirty();
            }
        }
        network.loadedInjectors = found;
        return found;
    }

    /**
     * Who this sender may offer to on a channel, in the order it should offer.
     *
     * <p>Priority decides first and is not negotiable; the channel's distribution mode only
     * settles ties. Round-robin does that by moving the front of the queue along one place every
     * pass, so a sender with more to give than any one receiver can take still spreads it around.
     */
    /**
     * The receivers a pad would send to on a channel right now, in the order a send would try
     * them: for a block pushing into the pad, which goes the same way a send does. A simulated
     * push looks at the queue without moving it along, so it lands where the real one will.
     */
    public List<PortFace> receiversFor(PortFace sender, int channel, TransferKind kind, boolean simulate) {
        UUID id = sender.networkId();
        Network network = id == null ? null : networks.get(id);
        if (network == null || !network.channel(channel).carries(kind)) {
            return List.of();
        }
        return receiversFor(network, sender, channel, kind, network.channel(channel).distribution(kind), !simulate);
    }

    /** The pads sending on a channel and kind: EX on, redstone allowing, in reach. Kept until a pad changes. */
    private static List<PortFace> sendersFor(Network network, int channel, TransferKind kind, List<PortFace> ports) {
        int slot = channel * TransferKind.all().length + kind.ordinal();
        List<PortFace> senders = network.senders[slot];
        if (senders == null) {
            senders = new ArrayList<>();
            for (PortFace face : ports) {
                PortChannel link = face.link(channel, kind);
                if (link.extractEnabled() && link.canRun() && face.inReach()) {
                    senders.add(face);
                }
            }
            network.senders[slot] = senders;
        }
        return senders;
    }

    /**
     * The receivers a sender tries on a channel, in order: highest priority first, nearest first
     * within it. Sorted once and kept on the sender's link until a pad on the network changes;
     * the modes that move the front of the queue read it through a rotated view, so the kept
     * order stays put. Callers read the list and never change it.
     */
    private List<PortFace> receiversFor(Network network, PortFace sender, int channel, TransferKind kind,
                                        Distribution distribution, boolean advance) {
        PortChannel link = sender.link(channel, kind);
        List<PortFace> sorted = link.receivers();
        if (sorted == null) {
            sorted = sortReceivers(sender, channel, kind, loadedPorts(network));
            link.setReceivers(sorted);
            network.cachers.add(link);
        }
        if (!distribution.rotates() || sorted.size() < 2) {
            return sorted;
        }
        int cursor;
        if (!advance) {
            cursor = link.peekRoundRobinCursor(sorted.size());
        } else if (distribution == Distribution.RANDOM && sender.level() != null) {
            cursor = link.takeRandomCursor(sorted.size(), sender.level().random);
        } else {
            cursor = link.takeRoundRobinCursor(sorted.size());
        }
        return cursor == 0 ? sorted : new Rotated(sorted, cursor);
    }

    /** A sorted list read from a different starting point, without copying it: round robin's turn. */
    private static final class Rotated extends AbstractList<PortFace> implements RandomAccess {
        private final List<PortFace> base;
        private final int start;

        Rotated(List<PortFace> base, int start) {
            this.base = base;
            this.start = start;
        }

        @Override
        public PortFace get(int index) {
            int at = start + index;
            return base.get(at >= base.size() ? at - base.size() : at);
        }

        @Override
        public int size() {
            return base.size();
        }
    }

    /** A receiver with the two numbers it is sorted by, computed once each rather than once per comparison. */
    private record Ranked(PortFace face, int priority, double distance) {
        private static final Comparator<Ranked> ORDER = Comparator
                .comparingInt((Ranked ranked) -> -ranked.priority())
                .thenComparingDouble(Ranked::distance);
    }

    private static List<PortFace> sortReceivers(PortFace sender, int channel, TransferKind kind, List<PortFace> ports) {
        List<Ranked> ranked = new ArrayList<>(ports.size());
        for (PortFace face : ports) {
            PortChannel link = face.link(channel, kind);
            if (face == sender || !link.insertEnabled() || !link.canRun() || !face.inReach()
                    || sameTarget(sender, face)) {
                continue;
            }
            ranked.add(new Ranked(face, link.priority(), distanceBetween(sender, face)));
        }
        ranked.sort(Ranked.ORDER);
        List<PortFace> receivers = new ArrayList<>(ranked.size());
        for (Ranked entry : ranked) {
            receivers.add(entry.face());
        }
        return receivers;
    }

    /** Two pads pointing at one chest are not a route from that chest to itself. */
    private static boolean sameTarget(PortFace a, PortFace b) {
        return a.level() == b.level() && a.targetPos().equals(b.targetPos());
    }

    private static double distanceBetween(PortFace from, PortFace to) {
        return from.level() == to.level()
                ? from.hostPos().distSqr(to.hostPos())
                : Double.MAX_VALUE;
    }

    // ------------------------------------------------------------------ persistence

    private static LinkNetworkManager load(CompoundTag tag, HolderLookup.Provider registries) {
        LinkNetworkManager manager = new LinkNetworkManager();
        for (Tag entry : tag.getList(KEY_NETWORKS, Tag.TAG_COMPOUND)) {
            CompoundTag networkTag = (CompoundTag) entry;
            Network network = new Network();
            UUID id = networkTag.getUUID(KEY_ID);
            network.name = networkTag.getString(KEY_NAME);
            network.colour = networkTag.contains(KEY_COLOUR) ? networkTag.getInt(KEY_COLOUR) : defaultColour(id);
            if (networkTag.hasUUID(KEY_OWNER)) {
                network.owner = networkTag.getUUID(KEY_OWNER);
            }
            network.ownerName = networkTag.getString(KEY_OWNER_NAME);
            network.shared = networkTag.getBoolean(KEY_SHARED);
            for (Tag memberEntry : networkTag.getList(KEY_MEMBERS, Tag.TAG_COMPOUND)) {
                CompoundTag memberTag = (CompoundTag) memberEntry;
                if (memberTag.hasUUID(KEY_ID)) {
                    network.members.put(memberTag.getUUID(KEY_ID), memberTag.getString(KEY_NAME));
                }
            }
            CompoundTag labels = networkTag.getCompound(KEY_LABELS);
            for (String label : labels.getAllKeys()) {
                // No label is not a label: nothing is ever stored under the empty string.
                if (!label.isEmpty()) {
                    network.labels.put(label, labels.getCompound(label));
                }
            }
            ResourceLocation home = ResourceLocation.tryParse(networkTag.getString(KEY_HOME));
            if (home != null) {
                network.home = ResourceKey.create(Registries.DIMENSION, home);
            }
            for (Tag portEntry : networkTag.getList(KEY_PORTS, Tag.TAG_COMPOUND)) {
                CompoundTag portTag = (CompoundTag) portEntry;
                GlobalPos at = readPos(portTag);
                if (at != null) {
                    network.ports.add(new PortRef(at, Direction.from3DDataValue(portTag.getByte(KEY_FACE))));
                }
            }
            for (Tag injectorEntry : networkTag.getList(KEY_INJECTORS, Tag.TAG_COMPOUND)) {
                GlobalPos at = readPos((CompoundTag) injectorEntry);
                if (at != null) {
                    network.injectors.add(at);
                }
            }
            ListTag channels = networkTag.getList(KEY_CHANNELS, Tag.TAG_COMPOUND);
            for (int index = 0; index < Math.min(channels.size(), PortFace.CHANNELS); index++) {
                network.channels[index] = ChannelSettings.load(channels.getCompound(index));
            }
            computeCenter(network);
            manager.networks.put(id, network);
        }
        return manager;
    }

    private static @Nullable GlobalPos readPos(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(KEY_DIMENSION));
        return dimension == null ? null : GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, dimension),
                new BlockPos(tag.getInt(KEY_X), tag.getInt(KEY_Y), tag.getInt(KEY_Z)));
    }

    private static CompoundTag writePos(GlobalPos at) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_DIMENSION, at.dimension().location().toString());
        tag.putInt(KEY_X, at.pos().getX());
        tag.putInt(KEY_Y, at.pos().getY());
        tag.putInt(KEY_Z, at.pos().getZ());
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Network> entry : networks.entrySet()) {
            Network network = entry.getValue();
            CompoundTag networkTag = new CompoundTag();
            networkTag.putUUID(KEY_ID, entry.getKey());
            networkTag.putString(KEY_NAME, network.name);
            networkTag.putString(KEY_HOME, network.home.location().toString());
            networkTag.putInt(KEY_COLOUR, network.colour);
            if (network.owner != null) {
                networkTag.putUUID(KEY_OWNER, network.owner);
            }
            networkTag.putString(KEY_OWNER_NAME, network.ownerName);
            networkTag.putBoolean(KEY_SHARED, network.shared);
            ListTag members = new ListTag();
            for (Map.Entry<UUID, String> member : network.members.entrySet()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID(KEY_ID, member.getKey());
                memberTag.putString(KEY_NAME, member.getValue());
                members.add(memberTag);
            }
            networkTag.put(KEY_MEMBERS, members);
            CompoundTag labels = new CompoundTag();
            network.labels.forEach(labels::put);
            networkTag.put(KEY_LABELS, labels);

            ListTag ports = new ListTag();
            for (PortRef ref : network.ports) {
                CompoundTag portTag = writePos(ref.at());
                portTag.putByte(KEY_FACE, (byte) ref.face().get3DDataValue());
                ports.add(portTag);
            }
            networkTag.put(KEY_PORTS, ports);

            ListTag injectors = new ListTag();
            for (GlobalPos at : network.injectors) {
                injectors.add(writePos(at));
            }
            networkTag.put(KEY_INJECTORS, injectors);

            ListTag channels = new ListTag();
            for (ChannelSettings settings : network.channels) {
                channels.add(settings.save());
            }
            networkTag.put(KEY_CHANNELS, channels);

            list.add(networkTag);
        }
        tag.put(KEY_NETWORKS, list);
        return tag;
    }

    /** Only used by the codec above; {@link Level#OVERWORLD} is where the record lives. */
    private LinkNetworkManager() {
    }

    /**
     * The FE a pass has to spend, pooled across every injector on the network.
     *
     * <p>Wireless is not free. Everything a pass moves is paid for out of this, and a network
     * with no injector on it, or with flat ones, moves nothing at all.
     */
    public static final class PowerBank {
        private final List<EnergyInjectorBlockEntity> injectors;
        private long available;

        PowerBank(List<EnergyInjectorBlockEntity> injectors) {
            this.injectors = injectors;
            long pooled = 0;
            for (EnergyInjectorBlockEntity injector : injectors) {
                pooled += injector.energyStorage().stored();
            }
            this.available = pooled;
        }

        public long available() {
            return available;
        }

        /** How many of a thing this pass can afford at the given price. Free things are unlimited. */
        public long affords(long unitCost) {
            return unitCost <= 0 ? Long.MAX_VALUE : available / unitCost;
        }

        /** Takes the FE out of the injectors, nearest the front of the list first. */
        public void spend(long fe) {
            long left = Math.min(fe, available);
            available -= left;
            for (EnergyInjectorBlockEntity injector : injectors) {
                if (left <= 0) {
                    break;
                }
                left -= injector.energyStorage().consume(left);
            }
        }
    }
}
