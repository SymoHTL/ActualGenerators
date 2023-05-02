package dev.symo.actualgenerators.net;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.net.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {

    private static SimpleChannel INSTANCE;

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(ActualGenerators.MOD_ID, "main_channel"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(SwitchPipeModeC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SwitchPipeModeC2SPacket::new)
                .encoder(SwitchPipeModeC2SPacket::toBytes)
                .consumerMainThread(SwitchPipeModeC2SPacket::handle)
                .add();

        net.messageBuilder(SwitchChannelC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SwitchChannelC2SPacket::new)
                .encoder(SwitchChannelC2SPacket::toBytes)
                .consumerMainThread(SwitchChannelC2SPacket::handle)
                .add();

        net.messageBuilder(SetPriorityC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetPriorityC2SPacket::new)
                .encoder(SetPriorityC2SPacket::toBytes)
                .consumerMainThread(SetPriorityC2SPacket::handle)
                .add();

        net.messageBuilder(SwitchRedstoneModeC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SwitchRedstoneModeC2SPacket::new)
                .encoder(SwitchRedstoneModeC2SPacket::toBytes)
                .consumerMainThread(SwitchRedstoneModeC2SPacket::handle)
                .add();

        net.messageBuilder(PipeChangeS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PipeChangeS2CPacket::new)
                .encoder(PipeChangeS2CPacket::toBytes)
                .consumerMainThread(PipeChangeS2CPacket::handle)
                .add();

    }


    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToClient(MSG message, ServerPlayer player) {
        INSTANCE.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
