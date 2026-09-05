package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.menu.NetworkPickerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "Invite this player onto the network the block is on" -- the other picker choice that carries
 * a name, so it cannot ride on a menu button. The server decides whether the sender may.
 */
public record InviteMemberPayload(int containerId, String name) implements CustomPacketPayload {
    /** A player name is at most sixteen characters. */
    public static final int MAX_NAME_LENGTH = 16;

    public static final Type<InviteMemberPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "invite_member"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InviteMemberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InviteMemberPayload::containerId,
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), InviteMemberPayload::name,
            InviteMemberPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Applied on the server, and only through the picker the player actually has open. */
    public static void handle(InviteMemberPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof NetworkPickerMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.invite(context.player(), payload.name());
            }
        });
    }
}
