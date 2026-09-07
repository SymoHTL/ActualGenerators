package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity.Extent;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.menu.MultiblockControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The bottom row of a controller's window while no structure stands: the preview switch and the
 * three sizes it shows, stepped with the arrows. One row for every controller, so a screen owns
 * one of these and hands it the frame, the hover and the click.
 *
 * <p>The sizes are the controller's ({@link MultiblockControllerMenu#previewSize}): a step is a
 * menu button, the server clamps and keeps it, and the synced value is what is drawn, so a size
 * sticks through the window closing, the preview being switched off and the structure coming
 * and going. Only the switch is the client's: the hologram is drawn from {@link
 * MultiblockPreview}, which follows the synced size while the window is open.
 */
final class MultiblockPreviewRow {
    /** The arrows' column inside a stepper: where a click steps rather than reads. */
    private static final int ARROW_COLUMN = 25;

    private static final String[] EYE = {
            ".....####.....",
            "...##....##...",
            "..#...##...#..",
            ".#...####...#.",
            "..#...##...#..",
            "...##....##...",
            ".....####.....",
    };
    private static final String[] UP = {"..#..", ".###.", "#####"};
    private static final String[] DOWN = {"#####", ".###.", "..#.."};

    private final MachineScreen<?> screen;
    private final MultiblockControllerMenu<?> menu;

    MultiblockPreviewRow(MachineScreen<?> screen, MultiblockControllerMenu<?> menu) {
        this.screen = screen;
        this.menu = menu;
    }

    private BlockPos controller() {
        return menu.machine().getBlockPos();
    }

    private MultiblockPreview.Size size() {
        return new MultiblockPreview.Size(menu.previewSize(Extent.WIDTH), menu.previewSize(Extent.HEIGHT),
                menu.previewSize(Extent.DEPTH));
    }

    /** The three readout lines of a controller with no structure: not formed, the sizes it takes, what to build. */
    static Component[] unformedLines(MultiblockControllerMenu<?> menu) {
        return new Component[]{
                Component.translatable("gui.actualgenerators.multiblock.unformed").withStyle(ChatFormatting.DARK_RED),
                Component.translatable("gui.actualgenerators.multiblock.size_hint",
                        menu.minSize(Extent.WIDTH) + "-" + menu.maxSize(Extent.WIDTH),
                        menu.minSize(Extent.HEIGHT) + "-" + menu.maxSize(Extent.HEIGHT),
                        menu.minSize(Extent.DEPTH) + "-" + menu.maxSize(Extent.DEPTH)),
                Component.translatable("gui.actualgenerators.multiblock.shell_hint"),
        };
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        Font font = Minecraft.getInstance().font;
        boolean shown = MultiblockPreview.isShown(controller());
        if (shown) {
            // A step comes back from the server a tick later; the hologram follows it from here.
            MultiblockPreview.show(controller(), size());
        }
        MachineLayout.Box button = MachineLayout.PREVIEW_BUTTON;
        Chrome.box(graphics, left, top, button, Chrome.BUTTON_EDGE, shown ? Chrome.ACCENT : Chrome.DARK, mouseX, mouseY);
        Chrome.glyph(graphics, EYE, left + button.x() + 2, top + button.y() + 5, shown ? Chrome.INK_DARK : Chrome.INK_LIGHT);

        for (Extent extent : Extent.values()) {
            MachineLayout.Box box = MachineLayout.stepper(extent);
            int x = left + box.x();
            int y = top + box.y();
            Chrome.box(graphics, left, top, box, Chrome.BUTTON_EDGE, Chrome.DARK, -1, -1);
            graphics.drawString(font, extent.name().substring(0, 1), x + 4, y + 5, Chrome.INK_LIGHT & 0xFFFFFF, false);
            graphics.drawString(font, String.valueOf(menu.previewSize(extent)), x + 12, y + 5, Chrome.LABEL, true);
            boolean overUp = MachineScreen.isOver(mouseX, mouseY, x + ARROW_COLUMN - 1, y + 1, 8, 8);
            boolean overDown = MachineScreen.isOver(mouseX, mouseY, x + ARROW_COLUMN - 1, y + 9, 8, 8);
            Chrome.glyph(graphics, UP, x + ARROW_COLUMN, y + 4, overUp ? Chrome.SELECTED_EDGE : Chrome.INK_LIGHT);
            Chrome.glyph(graphics, DOWN, x + ARROW_COLUMN, y + 11, overDown ? Chrome.SELECTED_EDGE : Chrome.INK_LIGHT);
        }
    }

    /** @return true if something was drawn */
    boolean renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        Font font = Minecraft.getInstance().font;
        if (Chrome.isOver(mouseX, mouseY, left, top, MachineLayout.PREVIEW_BUTTON)) {
            graphics.renderTooltip(font, lines(
                    Component.translatable("gui.actualgenerators.multiblock.preview"),
                    Component.translatable("gui.actualgenerators.multiblock.preview.hint").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
            return true;
        }
        for (Extent extent : Extent.values()) {
            if (Chrome.isOver(mouseX, mouseY, left, top, MachineLayout.stepper(extent))) {
                graphics.renderTooltip(font, lines(
                        Component.translatable("gui.actualgenerators.multiblock." + extent.name().toLowerCase(Locale.ROOT),
                                menu.previewSize(extent), menu.minSize(extent), menu.maxSize(extent)),
                        Component.translatable("gui.actualgenerators.multiblock.stepper.hint",
                                menu.sizeStep(extent), 4 * menu.sizeStep(extent)).withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    /** @return true if the click was the row's */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        if (button == 0 && Chrome.isOver(mouseX, mouseY, left, top, MachineLayout.PREVIEW_BUTTON)) {
            if (MultiblockPreview.isShown(controller())) {
                MultiblockPreview.hide(controller());
            } else {
                MultiblockPreview.show(controller(), size());
            }
            screen.playClick();
            return true;
        }
        for (Extent extent : Extent.values()) {
            MachineLayout.Box box = MachineLayout.stepper(extent);
            int x = left + box.x() + ARROW_COLUMN - 1;
            int y = top + box.y();
            boolean up = MachineScreen.isOver(mouseX, mouseY, x, y + 1, 8, 8);
            boolean down = MachineScreen.isOver(mouseX, mouseY, x, y + 9, 8, 8);
            if (up || down) {
                int step = (button == 1 || Screen.hasShiftDown() ? 4 : 1) * menu.sizeStep(extent);
                screen.pressButton(MultiblockControllerMenu.previewButton(extent, up ? step : -step));
                return true;
            }
        }
        return false;
    }

    private static List<FormattedCharSequence> lines(Component... lines) {
        return Arrays.stream(lines).map(Component::getVisualOrderText).toList();
    }
}
