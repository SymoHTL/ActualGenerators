package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.menu.NetworkPickerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "Make a new network called this, and put the block on it" -- the one picker choice that carries
 * a name, so it cannot ride on a menu button.
 */
public record CreateNetworkPayload(int containerId, String name) implements CustomPacketPayload {
    public static final Type<CreateNetworkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "create_network"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateNetworkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreateNetworkPayload::containerId,
            ByteBufCodecs.stringUtf8(LinkNetworkManager.MAX_NAME_LENGTH), CreateNetworkPayload::name,
            CreateNetworkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Applied on the server, and only through the picker the player actually has open. */
    public static void handle(CreateNetworkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof NetworkPickerMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.create(context.player(), payload.name());
            }
        });
    }
}
