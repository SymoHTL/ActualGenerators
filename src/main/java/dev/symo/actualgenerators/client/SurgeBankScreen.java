package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.SurgeBankMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SurgeBankScreen extends MachineScreen<SurgeBankMenu> {

    public SurgeBankScreen(SurgeBankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        renderReadout(graphics,
                Component.translatable("gui.actualgenerators.stored", formatNumber(menu.energyStored())),
                Component.translatable("gui.actualgenerators.charge",
                        (int) Math.round(menu.chargeFraction() * 100)),
                leakLine());
    }

    /**
     * The leak, spelled out. It is the whole trade the bank asks a player to make, so it belongs
     * on the window rather than in a tooltip nobody hovers.
     */
    private Component leakLine() {
        if (menu.isLeaking()) {
            return Component.translatable("gui.actualgenerators.leaking", formatNumber(menu.leakPerSecond()))
                    .withStyle(ChatFormatting.DARK_RED);
        }
        return Component.translatable("gui.actualgenerators.holding");
    }
}
