package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import dev.symo.actualgenerators.menu.AnnihilationFurnaceMenu;
import dev.symo.actualgenerators.menu.MachineLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.Arrays;
import java.util.List;

/**
 * Three short lines on the left: the box, the batch it buys, and what is coming out of it. Along
 * the bottom, before the box stands: the preview row every controller has. Once it stands: heat
 * and load on the left, and the efficiency bar they make.
 */
public class AnnihilationFurnaceScreen extends MachineScreen<AnnihilationFurnaceMenu> {
    private static final int EFFICIENCY_SPRITE_U = 176;
    private static final int EFFICIENCY_SPRITE_V = 180;
    private static final int EFFICIENCY_FRAME_U = 176;
    private static final int EFFICIENCY_FRAME_V = 198;

    private final MultiblockPreviewRow preview;

    public AnnihilationFurnaceScreen(AnnihilationFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        preview = new MultiblockPreviewRow(this, menu);
    }

    @Override
    protected boolean isGenerator() {
        return true;
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        MultiblockControllerBlockEntity.Structure structure = menu.structure();
        if (structure == null) {
            Component[] lines = MultiblockPreviewRow.unformedLines(menu);
            renderReadout(graphics, lines[0], lines[1], lines[2]);
            return;
        }
        int rate = menu.energyRate();
        renderReadout(graphics,
                Component.literal(structure.describe()),
                Component.translatable("gui.actualgenerators.multiblock.batch", menu.batch()),
                rate > 0
                        ? Component.translatable("gui.actualgenerators.rate.generating", formatNumber(rate))
                        : Component.translatable("gui.actualgenerators.rate.idle"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (menu.structure() == null) {
            preview.render(graphics, mouseX, mouseY);
        } else {
            renderEfficiencyRow(graphics);
        }
    }

    private void renderEfficiencyRow(GuiGraphics graphics) {
        MachineLayout.Box row = MachineLayout.MULTIBLOCK_ROW;
        int heat = menu.heatPermille();
        drawReadout(graphics, row.x(), row.y() + 1,
                heat > 0
                        ? Component.translatable("gui.actualgenerators.furnace.heat", percentOf(heat))
                        : Component.translatable("gui.actualgenerators.furnace.heat.none"),
                heat > 0 ? READOUT_COLOUR : Chrome.BAD);
        drawReadout(graphics, row.x(), row.y() + 10,
                Component.translatable("gui.actualgenerators.furnace.load", menu.held(), menu.batch()),
                menu.loadPermille() < 1000 ? Chrome.BAD : READOUT_COLOUR);

        MachineLayout.Box bar = AnnihilationFurnaceMenu.EFFICIENCY_BAR;
        graphics.blit(texture(), leftPos + bar.x() - 1, topPos + bar.y() - 1,
                EFFICIENCY_FRAME_U, EFFICIENCY_FRAME_V, bar.width() + 2, bar.height() + 2);
        int filled = bar.width() * Math.clamp(menu.efficiencyPermille(), 0, 1000) / 1000;
        if (filled > 0) {
            graphics.blit(texture(), leftPos + bar.x(), topPos + bar.y(),
                    EFFICIENCY_SPRITE_U, EFFICIENCY_SPRITE_V, filled, bar.height());
        }
        Component label = Component.literal(percentOf(menu.efficiencyPermille()) + "%");
        graphics.drawString(font, label, leftPos + bar.x() + (bar.width() - font.width(label)) / 2,
                topPos + bar.y() + 4, 0xFFFFFF, true);
    }

    private static int percentOf(int permille) {
        return (permille + 5) / 10;
    }

    @Override
    protected boolean renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.structure() == null) {
            if (preview.renderTooltip(graphics, mouseX, mouseY)) {
                return true;
            }
        } else if (Chrome.isOver(mouseX, mouseY, leftPos, topPos, AnnihilationFurnaceMenu.EFFICIENCY_BAR)) {
            graphics.renderTooltip(font, lines(
                    Component.translatable("gui.actualgenerators.furnace.efficiency", percentOf(menu.efficiencyPermille())),
                    Component.translatable("gui.actualgenerators.furnace.efficiency.parts",
                            percentOf(menu.heatPermille()), percentOf(menu.loadPermille())),
                    Component.translatable("gui.actualgenerators.furnace.efficiency.hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
            return true;
        } else if (Chrome.isOver(mouseX, mouseY, leftPos, topPos, MachineLayout.MULTIBLOCK_ROW)) {
            boolean heatLine = mouseY < topPos + MachineLayout.MULTIBLOCK_ROW.y() + 9;
            graphics.renderTooltip(font, lines(
                    Component.translatable(heatLine
                            ? "gui.actualgenerators.furnace.heat.hint"
                            : "gui.actualgenerators.furnace.load.hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
            return true;
        }
        return super.renderCustomTooltips(graphics, mouseX, mouseY);
    }

    private static List<FormattedCharSequence> lines(Component... lines) {
        return Arrays.stream(lines).map(Component::getVisualOrderText).toList();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.structure() == null && preview.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
