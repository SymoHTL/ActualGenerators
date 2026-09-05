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
 * "Make this number of the pad's window exactly that" -- a typed value, which a menu button
 * cannot carry. The menu clamps it the way the arrows would.
 */
public record SetPadNumberPayload(int containerId, int field, long value) implements CustomPacketPayload {
    public static final Type<SetPadNumberPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "set_pad_number"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPadNumberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetPadNumberPayload::containerId,
            ByteBufCodecs.VAR_INT, SetPadNumberPayload::field,
            ByteBufCodecs.VAR_LONG, SetPadNumberPayload::value,
            SetPadNumberPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Applied on the server, and only through the pad window the player actually has open. */
    public static void handle(SetPadNumberPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof LinkPortMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.setNumber(payload.field(), payload.value());
            }
        });
    }
}
