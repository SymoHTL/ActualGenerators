package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.network.ApplyPadConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The config card's one question, asked when it is used on a pad: this pad, or this pad and every
 * neighbour with a pad on the same face, neighbour to neighbour, never diagonally. Vanilla widgets:
 * it is a question, not a machine.
 */
public class PadApplyScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int PITCH = 24;

    private final BlockPos pos;
    private final Direction face;

    public PadApplyScreen(BlockPos pos, Direction face) {
        super(Component.translatable("gui.actualgenerators.card.apply.title"));
        this.pos = pos;
        this.face = face;
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.actualgenerators.card.apply.this"), button -> send(false))
                .bounds(x, y, BUTTON_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.actualgenerators.card.apply.flood"), button -> send(true))
                .bounds(x, y + PITCH, BUTTON_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x, y + PITCH * 2 + 6, BUTTON_WIDTH, 20)
                .build());
    }

    private void send(boolean flood) {
        PacketDistributor.sendToServer(new ApplyPadConfigPayload(pos, face, flood));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
