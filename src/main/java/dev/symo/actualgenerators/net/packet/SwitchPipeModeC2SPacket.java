package dev.symo.actualgenerators.net.packet;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import dev.symo.actualgenerators.block.entity.pipe.config.EMode;
import dev.symo.actualgenerators.net.ModMessages;
import dev.symo.actualgenerators.screen.ItemPipeBlockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SwitchPipeModeC2SPacket {
    private final BlockPos pos;
    private final EMode mode;
    private final Direction direction;

    public SwitchPipeModeC2SPacket(EMode mode, Direction direction, BlockPos pos) {
        this.mode = mode;
        this.direction = direction;
        this.pos = pos;
    }

    public SwitchPipeModeC2SPacket(FriendlyByteBuf buf) {
        mode = buf.readEnum(EMode.class);
        direction = buf.readEnum(Direction.class);
        pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeEnum(direction);
        buf.writeBlockPos(pos);
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
                pipe.setMode(direction, mode);
                ModMessages.sendToClient(new PipeChangeS2CPacket(pipe.saveConnections()), player);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
