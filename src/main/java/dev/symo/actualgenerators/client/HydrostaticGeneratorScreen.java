package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.HydrostaticGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class HydrostaticGeneratorScreen extends GeneratorScreen<HydrostaticGeneratorMenu> {
    private static final int WATER_SPRITE_U = 176;
    private static final int WATER_SPRITE_V = 80;

    public HydrostaticGeneratorScreen(HydrostaticGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        int max = menu.maxColumn();
        renderGauge(graphics, max <= 0 ? 0.0 : menu.column() / (double) max, WATER_SPRITE_U, WATER_SPRITE_V);
        renderReadout(graphics,
                Component.translatable("gui.actualgenerators.depth", menu.column(), max),
                Component.translatable("gui.actualgenerators.output", formatNumber(menu.energyPerTick())));
    }

    @Override
    protected List<FormattedCharSequence> gaugeTooltip() {
        return List.of(
                Component.translatable("gui.actualgenerators.depth", menu.column(), menu.maxColumn())
                        .getVisualOrderText(),
                Component.translatable("gui.actualgenerators.depth.hint").getVisualOrderText());
    }
}
