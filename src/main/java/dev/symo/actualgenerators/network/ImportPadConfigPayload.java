package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.logistics.PadSnapshot;
import dev.symo.actualgenerators.menu.LinkPortMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The clipboard's text, brought up to be applied to the pad whose window is open. The server does
 * the reading: anything that is not a pad's settings is refused there, and every number in one
 * that is gets clamped as the arrows would clamp it.
 */
public record ImportPadConfigPayload(int containerId, String text) implements CustomPacketPayload {
    public static final Type<ImportPadConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "import_pad_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportPadConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ImportPadConfigPayload::containerId,
            ByteBufCodecs.stringUtf8(PadSnapshot.MAX_TEXT), ImportPadConfigPayload::text,
            ImportPadConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Applied on the server, and only through the pad window the player actually has open. */
    public static void handle(ImportPadConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof LinkPortMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.importText(payload.text());
            }
        });
    }
}
