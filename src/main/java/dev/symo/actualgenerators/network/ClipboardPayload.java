package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.client.ClientHooks;
import dev.symo.actualgenerators.logistics.PadSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Text for the player's clipboard: a pad's settings, exported. The clipboard is the client's, so
 * the server hands the text down and the client puts it there.
 */
public record ClipboardPayload(String text) implements CustomPacketPayload {
    public static final Type<ClipboardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "clipboard"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(PadSnapshot.MAX_TEXT).map(ClipboardPayload::new, ClipboardPayload::text)
                    .cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client only: this payload is never sent the other way. */
    public static void handle(ClipboardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.setClipboard(payload.text()));
    }
}
