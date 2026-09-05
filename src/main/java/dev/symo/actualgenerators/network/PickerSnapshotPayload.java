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
 * A fresh look at the picker's network for a window that is already open, after the owner did
 * something to who may use it. Sent down instead of opening the window again, because a reopened
 * window drops the cursor in the middle of the screen.
 */
public record PickerSnapshotPayload(int containerId, NetworkPickerMenu.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<PickerSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "picker_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickerSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PickerSnapshotPayload::containerId,
            NetworkPickerMenu.Snapshot.STREAM_CODEC, PickerSnapshotPayload::snapshot,
            PickerSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Applied on the client, and only to the picker the player still has open. */
    public static void handle(PickerSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof NetworkPickerMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.update(payload.snapshot());
            }
        });
    }
}
