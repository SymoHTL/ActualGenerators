package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.machine.multiblock.HatchSignal;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.menu.RedstoneHatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Six rows, one lit. The lit one says what it is giving off. */
public class RedstoneHatchScreen extends Screen implements MenuAccess<RedstoneHatchMenu> {
    private final RedstoneHatchMenu menu;
    private int left;
    private int top;

    public RedstoneHatchScreen(RedstoneHatchMenu menu, net.minecraft.world.entity.player.Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public RedstoneHatchMenu getMenu() {
        return menu;
    }

    @Override
    protected void init() {
        left = (width - RedstoneHatchMenu.WIDTH) / 2;
        top = (height - RedstoneHatchMenu.HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Chrome.panel(graphics, left, top, RedstoneHatchMenu.WIDTH, RedstoneHatchMenu.HEIGHT);
        Chrome.text(graphics, font, left, top, RedstoneHatchMenu.TITLE, title, Chrome.TEXT);

        HatchSignal chosen = menu.mode();
        for (HatchSignal mode : HatchSignal.values()) {
            MachineLayout.Box row = RedstoneHatchMenu.row(mode.ordinal());
            boolean on = mode == chosen;
            Chrome.box(graphics, left, top, row, on ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE,
                    on ? Chrome.ACCENT : Chrome.DARK, mouseX, mouseY);
            graphics.drawString(font, Component.translatable(mode.key()),
                    left + row.x() + 5, top + row.y() + 4, Chrome.LABEL, true);
            if (on && mode != HatchSignal.CONTROL) {
                Component level = Component.translatable("gui.actualgenerators.hatch.level", menu.level());
                graphics.drawString(font, level, left + row.x() + row.width() - 5 - font.width(level),
                        top + row.y() + 4, Chrome.LABEL, true);
            }
        }

        for (HatchSignal mode : HatchSignal.values()) {
            if (Chrome.isOver(mouseX, mouseY, left, top, RedstoneHatchMenu.row(mode.ordinal()))) {
                graphics.renderTooltip(font, List.of(
                        Component.translatable(mode.key()).getVisualOrderText(),
                        Component.translatable(mode.key() + ".hint").withStyle(net.minecraft.ChatFormatting.GRAY)
                                .getVisualOrderText()), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null) {
            for (HatchSignal mode : HatchSignal.values()) {
                if (Chrome.isOver(mouseX, mouseY, left, top, RedstoneHatchMenu.row(mode.ordinal()))) {
                    Chrome.press(minecraft, menu.containerId, mode.ordinal());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
