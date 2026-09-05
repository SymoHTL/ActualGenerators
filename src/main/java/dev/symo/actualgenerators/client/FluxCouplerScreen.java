package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.item.FluxCouplerItem;
import dev.symo.actualgenerators.menu.FluxCouplerMenu;
import dev.symo.actualgenerators.menu.MachineLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Locale;

/**
 * The coupler's window. It borrows the machine sheet and the machine layout on purpose: it is not
 * a machine, but it should not feel like a different mod either.
 *
 * <p>The bar on the right is the charge in the crystals it is holding rather than a buffer of its
 * own — the coupler has none, which is the whole point of it.
 */
public class FluxCouplerScreen extends AbstractContainerScreen<FluxCouplerMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "textures/gui/machine.png");

    private static final int ENERGY_X = MachineLayout.ENERGY_FILL.x();
    private static final int ENERGY_Y = MachineLayout.ENERGY_FILL.y();
    private static final int ENERGY_WIDTH = MachineLayout.ENERGY_FILL.width();
    private static final int ENERGY_HEIGHT = MachineLayout.ENERGY_FILL.height();
    private static final int ENERGY_SPRITE_U = 176;
    private static final int ENERGY_SPRITE_V = 16;

    private static final int TOGGLE_X = MachineLayout.REDSTONE_BUTTON.x();
    private static final int TOGGLE_Y = MachineLayout.REDSTONE_BUTTON.y();
    private static final int BUTTON_SIZE = MachineLayout.REDSTONE_BUTTON.width();

    private static final int SLOT_SPRITE_U = 176;
    private static final int SLOT_SPRITE_V = 154;
    private static final int SLOT_SPRITE_SIZE = 18;
    private static final int GHOST_SPRITE_U = 176;
    private static final int GHOST_SPRITE_V = 136;
    private static final int GHOST_SIZE = 16;

    private static final int ARROW_WIDTH = FluxCouplerMenu.DRAIN_ARROW.width();
    private static final int ARROW_HEIGHT = FluxCouplerMenu.DRAIN_ARROW.height();
    private static final int ARROW_SPRITE_U = 176;
    private static final int ARROW_TRACK_U = 200;
    private static final int ARROW_SPRITE_V = 0;

    private static final int READOUT_COLOUR = 0x404040;

    public FluxCouplerScreen(FluxCouplerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MachineLayout.WIDTH;
        this.imageHeight = MachineLayout.HEIGHT;
        this.inventoryLabelY = 100;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderCustomTooltips(graphics, mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        renderSlotFrames(graphics);
        renderChargeBar(graphics);
        renderArrows(graphics);
        drawButton(graphics, leftPos + TOGGLE_X, topPos + TOGGLE_Y, toggleColour(), mouseX, mouseY);

        graphics.drawString(font, Component.translatable("gui.actualgenerators.output",
                        String.format(Locale.ROOT, "%,d", menu.ratePerTick())),
                leftPos + 12, topPos + 60, READOUT_COLOUR, false);
    }

    /** Crystals travel left to right through the window, so the arrows say which way. */
    private void renderArrows(GuiGraphics graphics) {
        for (MachineLayout.Box arrow : List.of(FluxCouplerMenu.DRAIN_ARROW, FluxCouplerMenu.RETIRE_ARROW)) {
            graphics.blit(TEXTURE, leftPos + arrow.x(), topPos + arrow.y(),
                    ARROW_TRACK_U, ARROW_SPRITE_V, ARROW_WIDTH, ARROW_HEIGHT);
        }
        // The first arrow is how far through the crystal in hand the coupler is, so a player can
        // see at a glance whether it is nearly out.
        int drained = (int) Math.round(ARROW_WIDTH * menu.progress());
        if (drained > 0) {
            graphics.blit(TEXTURE, leftPos + FluxCouplerMenu.DRAIN_ARROW.x(), topPos + FluxCouplerMenu.DRAIN_ARROW.y(),
                    ARROW_SPRITE_U, ARROW_SPRITE_V, drained, ARROW_HEIGHT);
        }
    }

    private void renderSlotFrames(GuiGraphics graphics) {
        // The coupler's own slots are the first four; the rest are the player's, which the window
        // already has frames painted for.
        for (int index = 0; index < FluxCouplerItem.SLOT_COUNT; index++) {
            Slot slot = menu.slots.get(index);
            graphics.blit(TEXTURE, leftPos + slot.x - 1, topPos + slot.y - 1,
                    SLOT_SPRITE_U, SLOT_SPRITE_V, SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE);
        }
        // The upgrade slot draws the same faded hint the machines use, so it reads as one system.
        Slot upgrade = menu.slots.get(FluxCouplerItem.SLOT_UPGRADE);
        if (!upgrade.hasItem()) {
            graphics.blit(TEXTURE, leftPos + upgrade.x, topPos + upgrade.y,
                    GHOST_SPRITE_U, GHOST_SPRITE_V, GHOST_SIZE, GHOST_SIZE);
        }
    }

    private void renderChargeBar(GuiGraphics graphics) {
        long capacity = menu.chargeCapacity();
        if (capacity <= 0) {
            return;
        }
        int filled = (int) (ENERGY_HEIGHT * Math.clamp(menu.chargeHeld() / (double) capacity, 0.0, 1.0));
        if (filled <= 0) {
            return;
        }
        int top = ENERGY_Y + (ENERGY_HEIGHT - filled);
        graphics.blit(TEXTURE, leftPos + ENERGY_X, topPos + top,
                ENERGY_SPRITE_U, ENERGY_SPRITE_V + (ENERGY_HEIGHT - filled), ENERGY_WIDTH, filled);
    }

    private boolean renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isOver(mouseX, mouseY, leftPos + TOGGLE_X, topPos + TOGGLE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("item.actualgenerators.flux_coupler."
                                    + (menu.isActive() ? "on" : "off"))
                            .withStyle(menu.isActive() ? ChatFormatting.GREEN : ChatFormatting.RED)
                            .getVisualOrderText(),
                    Component.translatable("gui.actualgenerators.coupler.toggle")
                            .withStyle(ChatFormatting.GRAY).getVisualOrderText()), mouseX, mouseY);
            return true;
        }
        if (isOver(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT)) {
            graphics.renderTooltip(font, chargeTooltip(), mouseX, mouseY);
            return true;
        }
        if (isOver(mouseX, mouseY, leftPos + FluxCouplerMenu.DRAIN_ARROW.x(),
                topPos + FluxCouplerMenu.DRAIN_ARROW.y(), ARROW_WIDTH, ARROW_HEIGHT)) {
            graphics.renderTooltip(font, drainTooltip(), mouseX, mouseY);
            return true;
        }
        Slot upgrade = menu.slots.get(FluxCouplerItem.SLOT_UPGRADE);
        if (isOver(mouseX, mouseY, leftPos + upgrade.x, topPos + upgrade.y, GHOST_SIZE, GHOST_SIZE)) {
            graphics.renderTooltip(font, upgradeTooltip(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private List<FormattedCharSequence> drainTooltip() {
        if (menu.slots.get(FluxCouplerItem.SLOT_WORKING).getItem().isEmpty()) {
            return List.of(Component.translatable("gui.actualgenerators.progress.idle")
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        return List.of(
                Component.translatable("gui.actualgenerators.coupler.progress",
                        Math.round(menu.progress() * 100)).getVisualOrderText(),
                Component.translatable("gui.actualgenerators.coupler.left",
                                String.format(Locale.ROOT, "%,d", menu.chargeInHand()))
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText());
    }

    private List<FormattedCharSequence> chargeTooltip() {
        return List.of(
                Component.translatable("gui.actualgenerators.energy",
                        String.format(Locale.ROOT, "%,d", menu.chargeHeld()),
                        String.format(Locale.ROOT, "%,d", menu.chargeCapacity())).getVisualOrderText(),
                Component.translatable("gui.actualgenerators.coupler.crystals")
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText());
    }

    private List<FormattedCharSequence> upgradeTooltip() {
        return List.of(
                Component.translatable("gui.actualgenerators.upgrade.installed",
                                menu.installedUpgrades(), FluxCouplerMenu.maxUpgrades())
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText(),
                Component.translatable("gui.actualgenerators.upgrade.transfer",
                                String.format(Locale.ROOT, "%,d", menu.ratePerTick()), "")
                        .withStyle(ChatFormatting.GREEN).getVisualOrderText());
    }

    private int toggleColour() {
        return menu.isActive() ? 0xFF5EAC8D : 0xFF8B8B8B;
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int colour, int mouseX, int mouseY) {
        graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, colour);
        if (isOver(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE)) {
            graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, 0x40FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOver(mouseX, mouseY, leftPos + TOGGLE_X, topPos + TOGGLE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FluxCouplerMenu.BUTTON_TOGGLE_ACTIVE);
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
