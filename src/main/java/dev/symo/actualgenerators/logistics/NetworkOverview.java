package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.machine.TransferKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Everything the overview window shows for one network, as of the moment the server looked:
 * whose it is, where its centre is, every pad on it (loaded or not), every injector and every
 * channel that carries something. A snapshot: it travels in the window's opening packet and is
 * never updated, since looking is all the window does.
 */
public record NetworkOverview(UUID id, String name, int colour, LinkNetworkManager.Access access,
                              List<LinkNetworkManager.Member> members, ResourceKey<Level> home, Vec3 center,
                              List<Pad> pads, List<Injector> injectors, List<Channel> channels) {
    public static final NetworkOverview EMPTY = new NetworkOverview(new UUID(0, 0), "", 0,
            new LinkNetworkManager.Access("", false, false), List.of(), Level.OVERWORLD, Vec3.ZERO, List.of(), List.of(), List.of());

    private static final StreamCodec<ByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, vec -> vec.x,
            ByteBufCodecs.DOUBLE, vec -> vec.y,
            ByteBufCodecs.DOUBLE, vec -> vec.z,
            Vec3::new);
    private static final StreamCodec<ByteBuf, ResourceKey<Level>> DIMENSION_STREAM_CODEC =
            ResourceKey.streamCodec(Registries.DIMENSION);
    private static final StreamCodec<ByteBuf, TransferKind> KIND_STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(TransferKind::byOrdinal, TransferKind::ordinal);

    // Ten fields: more than composite takes, so written out.
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkOverview> STREAM_CODEC = StreamCodec.of(
            (buffer, overview) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, overview.id);
                buffer.writeUtf(overview.name);
                buffer.writeInt(overview.colour);
                LinkNetworkManager.Access.STREAM_CODEC.encode(buffer, overview.access);
                LinkNetworkManager.Member.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, overview.members);
                DIMENSION_STREAM_CODEC.encode(buffer, overview.home);
                VEC3_STREAM_CODEC.encode(buffer, overview.center);
                Pad.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, overview.pads);
                Injector.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, overview.injectors);
                Channel.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, overview.channels);
            },
            buffer -> new NetworkOverview(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    buffer.readUtf(),
                    buffer.readInt(),
                    LinkNetworkManager.Access.STREAM_CODEC.decode(buffer),
                    LinkNetworkManager.Member.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    DIMENSION_STREAM_CODEC.decode(buffer),
                    VEC3_STREAM_CODEC.decode(buffer),
                    Pad.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    Injector.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    Channel.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer)));

    /** One link a pad has switched on: the channel, the kind, and which of its three switches are on. */
    public record Link(int channel, TransferKind kind, boolean send, boolean receive, boolean push) {
        public static final StreamCodec<ByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Link::channel,
                KIND_STREAM_CODEC, Link::kind,
                ByteBufCodecs.BOOL, Link::send,
                ByteBufCodecs.BOOL, Link::receive,
                ByteBufCodecs.BOOL, Link::push,
                Link::new);
    }

    /** One pad. An unloaded one is a position and a face, and nothing else is known of it. */
    public record Pad(GlobalPos at, Direction face, boolean loaded, boolean inReach, int range, List<Link> links,
                      String label) {
        /** Seven fields: one more than {@code composite} takes, so written out. */
        public static final StreamCodec<ByteBuf, Pad> STREAM_CODEC = StreamCodec.of(
                (buffer, pad) -> {
                    GlobalPos.STREAM_CODEC.encode(buffer, pad.at);
                    Direction.STREAM_CODEC.encode(buffer, pad.face);
                    buffer.writeBoolean(pad.loaded);
                    buffer.writeBoolean(pad.inReach);
                    ByteBufCodecs.VAR_INT.encode(buffer, pad.range);
                    Link.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, pad.links);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, pad.label);
                },
                buffer -> new Pad(
                        GlobalPos.STREAM_CODEC.decode(buffer),
                        Direction.STREAM_CODEC.decode(buffer),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        Link.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer)));
    }

    /** One injector, with what it holds when it is loaded. */
    public record Injector(GlobalPos at, boolean loaded, long stored, long capacity) {
        public static final StreamCodec<ByteBuf, Injector> STREAM_CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, Injector::at,
                ByteBufCodecs.BOOL, Injector::loaded,
                ByteBufCodecs.VAR_LONG, Injector::stored,
                ByteBufCodecs.VAR_LONG, Injector::capacity,
                Injector::new);
    }

    /**
     * One channel that carries something: its kinds as a bitmask, the spread per kind (by
     * ordinal, one entry per kind), and how many loaded pads send and receive on it.
     */
    public record Channel(int channel, int kinds, List<Integer> spreads, int senders, int receivers) {
        public static final StreamCodec<ByteBuf, Channel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Channel::channel,
                ByteBufCodecs.VAR_INT, Channel::kinds,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Channel::spreads,
                ByteBufCodecs.VAR_INT, Channel::senders,
                ByteBufCodecs.VAR_INT, Channel::receivers,
                Channel::new);

        public boolean carries(TransferKind kind) {
            return (kinds & ChannelSettings.bit(kind)) != 0;
        }

        public Distribution spread(TransferKind kind) {
            int ordinal = kind.ordinal() < spreads.size() ? spreads.get(kind.ordinal()) : 0;
            return Distribution.values()[Math.clamp(ordinal, 0, Distribution.values().length - 1)];
        }
    }
}
