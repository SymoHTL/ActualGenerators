package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What the held tool draws: four switches, saved to the client config as they are flipped.
 * Opened by crouching and using the tool. Vanilla widgets: it is a settings page, not a machine.
 */
public class LinkOverlayOptionsScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int PITCH = 24;

    public LinkOverlayOptionsScreen() {
        super(Component.translatable("gui.actualgenerators.link.overlay.options"));
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 60;
        addToggle(x, y, "pads", ClientConfig.OVERLAY_PADS);
        addToggle(x, y + PITCH, "lines", ClientConfig.OVERLAY_LINES);
        addToggle(x, y + PITCH * 2, "sphere", ClientConfig.OVERLAY_REACH);
        addToggle(x, y + PITCH * 3, "through_walls", ClientConfig.OVERLAY_THROUGH_WALLS);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, y + PITCH * 4 + 6, BUTTON_WIDTH, 20)
                .build());
    }

    private void addToggle(int x, int y, String key, ModConfigSpec.BooleanValue value) {
        addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.get(value))
                .create(x, y, BUTTON_WIDTH, 20, Component.translatable("gui.actualgenerators.link.overlay." + key),
                        (button, on) -> ClientConfig.set(value, on)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 78, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
