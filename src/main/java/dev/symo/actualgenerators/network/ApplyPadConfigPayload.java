package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.item.ConfigCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The answer to the config card's question: apply what the card holds to the pad that was clicked,
 * or to it and every port block reachable from it face to face (never diagonally) that has a pad on
 * the same side. The server checks the card is still in the player's hand and the pads are theirs
 * to change.
 */
public record ApplyPadConfigPayload(BlockPos pos, Direction face, boolean flood) implements CustomPacketPayload {
    public static final Type<ApplyPadConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "apply_pad_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPadConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ApplyPadConfigPayload::pos,
            Direction.STREAM_CODEC, ApplyPadConfigPayload::face,
            ByteBufCodecs.BOOL, ApplyPadConfigPayload::flood,
            ApplyPadConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApplyPadConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ConfigCardItem.applyPad(player, payload.pos(), payload.face(), payload.flood());
            }
        });
    }
}
