package dev.symo.actualgenerators.compat.jade;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.PortChannel;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.machine.TransferKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/** Each pad on a port block: which network it is on, and what it does with each kind on each channel it is on. */
public class LinkPortStatusProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final LinkPortStatusProvider INSTANCE = new LinkPortStatusProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "logic_port");

    private static final String KEY_PADS = "Pads";
    private static final String KEY_FACE = "Face";
    private static final String KEY_NETWORK = "Network";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_CHANNEL = "Channel";
    private static final String KEY_KIND = "Kind";
    private static final String KEY_INSERT = "Insert";
    private static final String KEY_EXTRACT = "Extract";
    private static final String KEY_SIGNAL = "Signal";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof LinkPortBlockEntity port)
                || !(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ListTag pads = new ListTag();
        for (Direction direction : Direction.values()) {
            PortFace face = port.face(direction);
            if (face == null) {
                continue;
            }
            CompoundTag pad = new CompoundTag();
            pad.putByte(KEY_FACE, (byte) direction.get3DDataValue());
            if (face.isLinked()) {
                pad.putString(KEY_NETWORK, face.networkName());
            }
            if (face.signalOut() > 0) {
                pad.putByte(KEY_SIGNAL, (byte) face.signalOut());
            }
            ListTag channels = new ListTag();
            for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
                for (TransferKind kind : TransferKind.all()) {
                    PortChannel link = face.link(channel, kind);
                    if (!link.participates()) {
                        continue;
                    }
                    CompoundTag tag = new CompoundTag();
                    tag.putByte(KEY_CHANNEL, (byte) channel);
                    tag.putByte(KEY_KIND, (byte) kind.ordinal());
                    tag.putBoolean(KEY_INSERT, link.insertEnabled());
                    // PU sends too, on the pusher's clock rather than the pad's.
                    tag.putBoolean(KEY_EXTRACT, link.extractEnabled() || link.pushEnabled());
                    channels.add(tag);
                }
            }
            pad.put(KEY_CHANNELS, channels);
            pads.add(pad);
        }
        data.put(KEY_PADS, pads);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        for (Tag entry : accessor.getServerData().getList(KEY_PADS, Tag.TAG_COMPOUND)) {
            CompoundTag pad = (CompoundTag) entry;
            Component facing = Component.translatable("gui.actualgenerators.facing."
                    + Direction.from3DDataValue(pad.getByte(KEY_FACE)).getName());
            tooltip.add(pad.contains(KEY_NETWORK)
                    ? Component.translatable("jade.actualgenerators.link.pad", facing, pad.getString(KEY_NETWORK))
                    : Component.translatable("jade.actualgenerators.link.pad.unlinked", facing).withStyle(ChatFormatting.GRAY));
            if (pad.contains(KEY_SIGNAL)) {
                tooltip.add(Component.translatable("jade.actualgenerators.link.signal", pad.getByte(KEY_SIGNAL))
                        .withStyle(ChatFormatting.RED));
            }
            for (Tag channelEntry : pad.getList(KEY_CHANNELS, Tag.TAG_COMPOUND)) {
                CompoundTag tag = (CompoundTag) channelEntry;
                tooltip.add(Component.translatable("jade.actualgenerators.link.channel",
                        Component.translatable("color.minecraft." + DyeColor.byId(tag.getByte(KEY_CHANNEL)).getName()),
                        Component.translatable("gui.actualgenerators.link.role." + roleKey(tag)),
                        Component.translatable("gui.actualgenerators.link.kind."
                                + TransferKind.byOrdinal(tag.getByte(KEY_KIND)).name().toLowerCase(Locale.ROOT)))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static String roleKey(CompoundTag tag) {
        boolean insert = tag.getBoolean(KEY_INSERT);
        boolean extract = tag.getBoolean(KEY_EXTRACT);
        if (insert && extract) {
            return "both";
        }
        return extract ? "sends" : "receives";
    }
}
