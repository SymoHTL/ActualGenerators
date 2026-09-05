package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.ResonanceCrusherMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The crusher's window. Two things it says that no other machine does: what frequency the loaded
 * material rings at, and whether this crusher has worked that frequency out yet.
 */
public class ResonanceCrusherScreen extends MachineScreen<ResonanceCrusherMenu> {
    private static final int TEXT_X = 8;
    private static final int FIRST_LINE_Y = 56;
    private static final int SECOND_LINE_Y = 66;
    private static final int CALIBRATING_COLOUR = 0x9A5B0F;
    private static final int TUNED_COLOUR = 0x2E6B4A;

    public ResonanceCrusherScreen(ResonanceCrusherMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        int frequency = menu.frequency();
        drawReadout(graphics, TEXT_X, FIRST_LINE_Y, frequency > 0
                ? Component.translatable("gui.actualgenerators.frequency", frequency)
                : Component.translatable("gui.actualgenerators.frequency.none"));

        if (frequency <= 0) {
            drawReadout(graphics, TEXT_X, SECOND_LINE_Y,
                    Component.translatable("gui.actualgenerators.draw", formatNumber(menu.energyPerTick())));
        } else if (menu.isTuned()) {
            drawReadout(graphics, TEXT_X, SECOND_LINE_Y,
                    Component.translatable("gui.actualgenerators.crusher.tuned"), TUNED_COLOUR);
        } else {
            drawReadout(graphics, TEXT_X, SECOND_LINE_Y,
                    Component.translatable("gui.actualgenerators.crusher.calibrating"), CALIBRATING_COLOUR);
        }
    }

    /**
     * The arrow explains the calibration too: a first run that takes four times as long looks
     * broken otherwise, and "why is it so slow" should be answerable by hovering it.
     */
    @Override
    protected List<FormattedCharSequence> progressTooltip() {
        List<FormattedCharSequence> lines = new ArrayList<>(super.progressTooltip());
        if (menu.frequency() > 0) {
            lines.add(Component.translatable("gui.actualgenerators.crusher.batch", menu.batchSize())
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
            if (!menu.isTuned()) {
                lines.add(Component.translatable("gui.actualgenerators.crusher.calibrating.hint")
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        }
        return lines;
    }
}
