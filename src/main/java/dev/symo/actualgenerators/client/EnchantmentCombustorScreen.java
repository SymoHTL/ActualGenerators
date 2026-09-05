package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.EnchantmentCombustorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class EnchantmentCombustorScreen extends MachineScreen<EnchantmentCombustorMenu> {
    private static final int TEXT_X = 8;
    private static final int FIRST_LINE_Y = 56;
    private static final int SECOND_LINE_Y = 66;

    public EnchantmentCombustorScreen(EnchantmentCombustorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected boolean isGenerator() {
        return true;
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        drawReadout(graphics, TEXT_X, FIRST_LINE_Y,
                Component.translatable("gui.actualgenerators.levels", menu.loadedLevels()));
        drawReadout(graphics, TEXT_X, SECOND_LINE_Y,
                Component.translatable("gui.actualgenerators.output", formatNumber(menu.energyPerTick())));
    }
}
