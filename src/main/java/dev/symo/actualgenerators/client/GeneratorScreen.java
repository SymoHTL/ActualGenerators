package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * The screen for generators fed by the world rather than by a slot.
 *
 * <p>They have no item slots, so the middle of the window is free for one tall gauge and two
 * lines of plain text — because "what is this thing actually earning me" is the only question
 * these machines have to answer.
 */
public abstract class GeneratorScreen<M extends MachineMenu<?>> extends MachineScreen<M> {
    // Hard right, just clear of the energy bar: the readout lines need the left half of the
    // window, and a gauge in the middle of them was cutting the text in half. Its position lives
    // in MachineLayout so the clash test can see it.
    protected static final int GAUGE_X = MachineLayout.GAUGE_FILL.x();
    protected static final int GAUGE_Y = MachineLayout.GAUGE_FILL.y();
    protected static final int GAUGE_WIDTH = MachineLayout.GAUGE_FILL.width();
    protected static final int GAUGE_HEIGHT = MachineLayout.GAUGE_FILL.height();


    protected GeneratorScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected boolean isGenerator() {
        return true;
    }

    /** The menu decides, so the layout test and the screen cannot disagree about it. */
    protected boolean hasGauge() {
        return menu.hasGauge();
    }

    /** Fills the gauge from the bottom. Any non-zero reading shows at least one row. */
    protected void renderGauge(GuiGraphics graphics, double fraction, int spriteU, int spriteV) {
        graphics.blit(texture(), leftPos + GAUGE_X - 1, topPos + GAUGE_Y - 1,
                GAUGE_FRAME_U, GAUGE_FRAME_V, GAUGE_FRAME_WIDTH, GAUGE_FRAME_HEIGHT);
        if (fraction <= 0.0) {
            return;
        }
        int filled = Math.max(1, (int) (GAUGE_HEIGHT * Math.clamp(fraction, 0.0, 1.0)));
        graphics.blit(texture(),
                leftPos + GAUGE_X, topPos + GAUGE_Y + (GAUGE_HEIGHT - filled),
                spriteU, spriteV + (GAUGE_HEIGHT - filled),
                GAUGE_WIDTH, filled);
    }

    /** What hovering the gauge explains. */
    protected abstract List<FormattedCharSequence> gaugeTooltip();

    @Override
    protected boolean renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hasGauge() && isOver(mouseX, mouseY, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT)) {
            graphics.renderTooltip(font, gaugeTooltip(), mouseX, mouseY);
            return true;
        }
        return super.renderCustomTooltips(graphics, mouseX, mouseY);
    }
}
