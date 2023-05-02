package dev.symo.actualgenerators.net.packet;

import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import dev.symo.actualgenerators.block.entity.pipe.config.ERedstoneMode;
import dev.symo.actualgenerators.net.ModMessages;
import dev.symo.actualgenerators.screen.ItemPipeBlockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SwitchRedstoneModeC2SPacket {
    private final BlockPos pos;
    private final ERedstoneMode mode;
    private final Direction direction;
    private final boolean isInput;

    public SwitchRedstoneModeC2SPacket(ERedstoneMode mode, Direction direction, BlockPos pos, boolean isInput) {
        this.mode = mode;
        this.direction = direction;
        this.pos = pos;
        this.isInput = isInput;
    }

    public SwitchRedstoneModeC2SPacket(FriendlyByteBuf buf) {
        mode = buf.readEnum(ERedstoneMode.class);
        direction = buf.readEnum(Direction.class);
        pos = buf.readBlockPos();
        isInput = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeEnum(direction);
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
                pipe.setRedstoneMode(direction, isInput, mode);
                ModMessages.sendToClient(new PipeChangeS2CPacket(pipe.saveConnections()), player);
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
