package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.ImpactDynamoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class ImpactDynamoScreen extends GeneratorScreen<ImpactDynamoMenu> {
    /** The warm fill, reused: an impact reads as heat rather than as another charge level. */
    private static final int IMPACT_SPRITE_U = 192;
    private static final int IMPACT_SPRITE_V = 80;

    public ImpactDynamoScreen(ImpactDynamoMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        int max = menu.maxFallDistance();
        renderGauge(graphics, max <= 0 ? 0.0 : menu.lastFallDistance() / (double) max,
                IMPACT_SPRITE_U, IMPACT_SPRITE_V);
        renderReadout(graphics,
                Component.translatable("gui.actualgenerators.impact", formatNumber(menu.lastImpactEnergy())),
                Component.translatable("gui.actualgenerators.fell", menu.lastFallDistance(), max));
    }

    @Override
    protected List<FormattedCharSequence> gaugeTooltip() {
        return List.of(
                Component.translatable("gui.actualgenerators.fell",
                        menu.lastFallDistance(), menu.maxFallDistance()).getVisualOrderText(),
                Component.translatable("gui.actualgenerators.fell.hint").getVisualOrderText());
    }
}
