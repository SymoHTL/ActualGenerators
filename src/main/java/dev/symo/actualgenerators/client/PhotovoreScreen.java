package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.PhotovoreMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class PhotovoreScreen extends GeneratorScreen<PhotovoreMenu> {
    private static final int LIGHT_SPRITE_U = 192;
    private static final int LIGHT_SPRITE_V = 80;

    public PhotovoreScreen(PhotovoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        renderGauge(graphics, menu.progress(), LIGHT_SPRITE_U, LIGHT_SPRITE_V);
        renderReadout(graphics,
                Component.translatable("gui.actualgenerators.food", menu.foodInRange()),
                Component.translatable("gui.actualgenerators.meal", formatNumber(menu.mealEnergy())));
    }

    @Override
    protected List<FormattedCharSequence> gaugeTooltip() {
        return List.of(
                Component.translatable("gui.actualgenerators.food", menu.foodInRange()).getVisualOrderText(),
                Component.translatable("gui.actualgenerators.food.hint")
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText());
    }
}
