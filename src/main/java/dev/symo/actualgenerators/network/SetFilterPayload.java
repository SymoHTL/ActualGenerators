package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.menu.FilterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A filter entry set from something other than a click on the add slot: a drag out of JEI, or a
 * choice made in the entry window.
 *
 * <p>The add slot is filled by a click, and a click already reaches the server. Neither of the
 * others does: nothing is on the cursor and no vanilla packet carries an entry, so this one does.
 * An index on the list replaces that entry; any other index adds one at the end.
 */
public record SetFilterPayload(int containerId, int index, FilterContents.Entry entry) implements CustomPacketPayload {
    public static final Type<SetFilterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "set_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFilterPayload::containerId,
            ByteBufCodecs.VAR_INT, SetFilterPayload::index,
            FilterContents.Entry.STREAM_CODEC, SetFilterPayload::entry,
            SetFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Applied on the server, and only to the filter window the player actually has open. */
    public static void handle(SetFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.setEntry(payload.index(), payload.entry());
            }
        });
    }
}
