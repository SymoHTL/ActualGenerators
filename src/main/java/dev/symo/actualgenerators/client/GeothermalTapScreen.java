package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.menu.GeothermalTapMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * The pocket in the gauge, the corium in the tank beside it (drawn and clicked by the machine
 * window, like every tank), and three lines: the pocket, the box and its depth, the output.
 * Before the box stands, the preview row every controller has.
 */
public class GeothermalTapScreen extends GeneratorScreen<GeothermalTapMenu> {
    private static final int HEAT_SPRITE_U = 192;
    private static final int HEAT_SPRITE_V = 80;

    private final MultiblockPreviewRow preview;

    public GeothermalTapScreen(GeothermalTapMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        preview = new MultiblockPreviewRow(this, menu);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        renderGauge(graphics, menu.pocketPermille() / 1000.0, HEAT_SPRITE_U, HEAT_SPRITE_V);
        MultiblockControllerBlockEntity.Structure structure = menu.structure();
        if (structure == null) {
            Component[] lines = MultiblockPreviewRow.unformedLines(menu);
            renderReadout(graphics, lines[0], lines[1], lines[2]);
            return;
        }
        int pocket = menu.pocketPermille();
        int depth = menu.depthPermille();
        int rate = menu.energyRate();
        renderReadout(graphics,
                pocket > 0
                        ? Component.translatable("gui.actualgenerators.pocket", (pocket + 5) / 10)
                        : Component.translatable("gui.actualgenerators.pocket.none").withStyle(ChatFormatting.DARK_RED),
                depth > 0
                        ? Component.translatable("gui.actualgenerators.tap.box", structure.describe(), (depth + 5) / 10)
                        : Component.translatable("gui.actualgenerators.depth_factor.none").withStyle(ChatFormatting.DARK_RED),
                rate > 0
                        ? Component.translatable("gui.actualgenerators.rate.generating", formatNumber(rate))
                        : Component.translatable("gui.actualgenerators.rate.idle"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (!menu.formed()) {
            preview.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected List<FormattedCharSequence> gaugeTooltip() {
        int pocket = menu.pocketPermille();
        return List.of(
                (pocket > 0
                        ? Component.translatable("gui.actualgenerators.pocket", (pocket + 5) / 10)
                        : Component.translatable("gui.actualgenerators.pocket.none")).getVisualOrderText(),
                Component.translatable("gui.actualgenerators.pocket.hint").withStyle(ChatFormatting.GRAY).getVisualOrderText());
    }

    @Override
    protected boolean renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.formed() && preview.renderTooltip(graphics, mouseX, mouseY)) {
            return true;
        }
        return super.renderCustomTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!menu.formed() && preview.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
