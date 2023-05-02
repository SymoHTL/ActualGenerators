package dev.symo.actualgenerators.screen;

import dev.symo.actualgenerators.block.ModBlocks;
import dev.symo.actualgenerators.block.entity.pipe.ItemPipeBlockEntity;
import dev.symo.actualgenerators.event.EventHandler;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;


public class ItemPipeBlockMenu extends AbstractContainerMenu {

    public final ItemPipeBlockEntity pipeBlockEntity;
    public final EventHandler eventHandler = new EventHandler();
    private final Level level;
    public Direction direction;
    public boolean valid = true;

    public ItemPipeBlockMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, (ItemPipeBlockEntity) Objects.requireNonNull(inv.player.level.getBlockEntity(extraData.readBlockPos())));
        updateIO(extraData.readNbt());
        direction = extraData.readEnum(Direction.class);
    }

    public ItemPipeBlockMenu(int id, Inventory inv, ItemPipeBlockEntity pipeBlockEntity) {
        super(ModMenuTypes.ITEM_PIPE_BLOCK_MENU.get(), id);
        this.pipeBlockEntity = pipeBlockEntity;
        this.level = pipeBlockEntity.getLevel();

        addPlayerInventory(inv);
    }

    public void updateIO(CompoundTag nbt) {
        pipeBlockEntity.clearIO();
        if (nbt != null)
            pipeBlockEntity.readConnections(nbt);
        eventHandler.fire();
    }


    @Override
    public ItemStack quickMoveStack(Player player, int p_38942_) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, pipeBlockEntity.getBlockPos()), player, ModBlocks.ITEM_PIPE_BLOCK.get()) && valid;
    }


    private void addPlayerInventory(Inventory playerInventory) {
        int i = 36;
        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + i));
            }
        }

        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 161 + i));
        }
    }
}
