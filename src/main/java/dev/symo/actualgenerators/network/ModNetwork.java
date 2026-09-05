package dev.symo.actualgenerators.network;

import dev.symo.actualgenerators.ActualGenerators;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The mod's packets: each is a window telling the server something a click cannot carry. */
@EventBusSubscriber(modid = ActualGenerators.MODID)
public final class ModNetwork {

    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetFilterPayload.TYPE, SetFilterPayload.STREAM_CODEC, SetFilterPayload::handle);
        registrar.playToServer(CreateNetworkPayload.TYPE, CreateNetworkPayload.STREAM_CODEC, CreateNetworkPayload::handle);
        registrar.playToServer(InviteMemberPayload.TYPE, InviteMemberPayload.STREAM_CODEC, InviteMemberPayload::handle);
        registrar.playToClient(PickerSnapshotPayload.TYPE, PickerSnapshotPayload.STREAM_CODEC, PickerSnapshotPayload::handle);
        registrar.playToServer(SetPadNumberPayload.TYPE, SetPadNumberPayload.STREAM_CODEC, SetPadNumberPayload::handle);
        registrar.playToServer(SetPadLabelPayload.TYPE, SetPadLabelPayload.STREAM_CODEC, SetPadLabelPayload::handle);
        registrar.playToServer(ImportPadConfigPayload.TYPE, ImportPadConfigPayload.STREAM_CODEC, ImportPadConfigPayload::handle);
        registrar.playToServer(ApplyPadConfigPayload.TYPE, ApplyPadConfigPayload.STREAM_CODEC, ApplyPadConfigPayload::handle);
        registrar.playToClient(ClipboardPayload.TYPE, ClipboardPayload.STREAM_CODEC, ClipboardPayload::handle);
    }
}
