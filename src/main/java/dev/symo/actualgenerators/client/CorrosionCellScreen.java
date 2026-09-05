package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.CorrosionCellMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CorrosionCellScreen extends MachineScreen<CorrosionCellMenu> {
    public CorrosionCellScreen(CorrosionCellMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected boolean isGenerator() {
        return true;
    }
}
