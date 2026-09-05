package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.logistics.EnergyInjectorBlockEntity;
import dev.symo.actualgenerators.menu.EnergyInjectorMenu;
import dev.symo.actualgenerators.menu.MachineLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** The injector's window: what it holds, and the network line that says who it is paying for. */
public class EnergyInjectorScreen extends MachineScreen<EnergyInjectorMenu> {

    public EnergyInjectorScreen(EnergyInjectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        EnergyInjectorBlockEntity injector = menu.machine();
        MachineLayout.Box box = EnergyInjectorMenu.NETWORK_BUTTON;
        boolean linked = menu.isLinked();
        Chrome.box(graphics, leftPos, topPos, box, linked ? Chrome.BUTTON_EDGE : Chrome.WARN,
                linked ? Chrome.networkColour(injector.networkColour()) : Chrome.WARN, mouseX, mouseY);
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(networkText(), box.width() - 6)),
                leftPos + box.x() + 3, topPos + box.y() + 2, Chrome.LABEL, true);
    }

    @Override
    protected void renderMachineExtras(GuiGraphics graphics) {
        renderReadout(graphics,
                Component.translatable("gui.actualgenerators.stored", formatNumber(menu.energyStored())),
                Component.empty(),
                menu.isLinked()
                        ? Component.translatable("gui.actualgenerators.injector.serving", menu.servedPorts())
                        : Component.empty());
    }

    private Component networkText() {
        if (!menu.isLinked()) {
            return Component.translatable("gui.actualgenerators.link.unlinked");
        }
        return Component.literal(menu.machine().networkName());
    }

    @Override
    protected boolean renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (Chrome.isOver(mouseX, mouseY, leftPos, topPos, EnergyInjectorMenu.NETWORK_BUTTON)) {
            graphics.renderComponentTooltip(font, List.of(networkText(),
                    Component.translatable("gui.actualgenerators.link.network.hint").withStyle(ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
            return true;
        }
        return super.renderCustomTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && minecraft != null
                && Chrome.isOver(mouseX, mouseY, leftPos, topPos, EnergyInjectorMenu.NETWORK_BUTTON)) {
            Chrome.press(minecraft, menu.containerId,
                    button == 1 ? EnergyInjectorMenu.BUTTON_NETWORK_LEAVE : EnergyInjectorMenu.BUTTON_NETWORK_PICK);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
