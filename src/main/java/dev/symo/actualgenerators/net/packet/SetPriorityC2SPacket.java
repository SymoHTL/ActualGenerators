package dev.symo.actualgenerators.net.packet;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import dev.symo.actualgenerators.net.ModMessages;
import dev.symo.actualgenerators.screen.ItemPipeBlockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetPriorityC2SPacket {
    private final BlockPos pos;
    private final int priority;
    private final Direction direction;
    private final boolean isInput;

    public SetPriorityC2SPacket(int priority, Direction direction, BlockPos pos, boolean isInput) {
        this.priority = Math.min(100, Math.max(-100, priority));
        this.direction = direction;
        this.pos = pos;
        this.isInput = isInput;
    }

    public SetPriorityC2SPacket(FriendlyByteBuf buf) {
        priority = buf.readInt();
        direction = Direction.from3DDataValue(buf.readInt());
        pos = buf.readBlockPos();
        isInput = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(priority);
        buf.writeInt(direction.get3DDataValue());
        buf.writeBlockPos(pos);
        buf.writeBoolean(isInput);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // read custom packet data
            var player = context.getSender();
            if (player == null) return;
            var menu = player.containerMenu;
            if (menu == null) return;
            if (menu instanceof ItemPipeBlockMenu) {
                var blockEntity = player.level.getBlockEntity(pos);
                if (blockEntity == null) return;
                if (!(blockEntity instanceof ItemPipeBlockEntity pipe)) return;
                pipe.setPriority(direction, isInput, priority);
                ModMessages.sendToClient(new PipeChangeS2CPacket(pipe.saveConnections()), player);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
