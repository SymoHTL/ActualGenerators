package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.menu.LinkPortMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The label typed or picked in a pad's window. {@code push} says which way the settings go when
 * the network already knows the label: the pad's onto the label, or the label's onto the pad. An
 * empty label takes the pad's label off.
 */
public record SetPadLabelPayload(int containerId, String label, boolean push) implements CustomPacketPayload {
    public static final Type<SetPadLabelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "set_pad_label"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPadLabelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetPadLabelPayload::containerId,
            ByteBufCodecs.stringUtf8(64), SetPadLabelPayload::label,
            ByteBufCodecs.BOOL, SetPadLabelPayload::push,
            SetPadLabelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Applied on the server, and only through the pad window the player actually has open. */
    public static void handle(SetPadLabelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof LinkPortMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.setLabel(payload.label(), payload.push());
            }
        });
    }
}
