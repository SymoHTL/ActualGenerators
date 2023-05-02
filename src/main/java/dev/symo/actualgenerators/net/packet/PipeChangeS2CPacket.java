package dev.symo.actualgenerators.net.packet;

import dev.symo.actualgenerators.screen.ItemPipeBlockMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PipeChangeS2CPacket {
    private final CompoundTag tag;

    public PipeChangeS2CPacket(CompoundTag tag) {
        this.tag = tag;
    }

    public PipeChangeS2CPacket(FriendlyByteBuf buf) {
        tag = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(tag);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            var menu = player.containerMenu;
            if (menu == null) return;
            if (menu instanceof ItemPipeBlockMenu pipeMenu) {
                pipeMenu.updateIO(tag);
            }

        });
        context.setPacketHandled(true);
        return true;
    }

}
