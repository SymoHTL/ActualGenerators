package dev.symo.actualgenerators.client;

import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.item.TierUpgradeItem;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.MachineTier;
import dev.symo.actualgenerators.machine.MachineTuning;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.machine.UpgradeType;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.menu.MachineMenu;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen every machine shares: energy, the overclock ramp, upgrade slots, the redstone mode
 * button, and the per-face configuration panel.
 *
 * <p>Configuration edits go back to the server as menu button presses, so the server revalidates
 * every change rather than trusting the client.
 */
public abstract class MachineScreen<M extends MachineMenu<?>> extends AbstractContainerScreen<M> {
    protected static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "textures/gui/machine.png");

    // Positions come from MachineLayout so a server-side test can check them for clashes; only
    // the sprite coordinates, which are art rather than layout, live here.
    private static final int ENERGY_X = MachineLayout.ENERGY_FILL.x();
    private static final int ENERGY_Y = MachineLayout.ENERGY_FILL.y();
    private static final int ENERGY_WIDTH = MachineLayout.ENERGY_FILL.width();
    private static final int ENERGY_HEIGHT = MachineLayout.ENERGY_FILL.height();
    private static final int ENERGY_SPRITE_U = 176;
    private static final int ENERGY_SPRITE_V = 16;

    private static final int OVERCLOCK_X = MachineLayout.OVERCLOCK_FILL.x();
    private static final int OVERCLOCK_Y = MachineLayout.OVERCLOCK_FILL.y();
    private static final int OVERCLOCK_WIDTH = MachineLayout.OVERCLOCK_FILL.width();
    private static final int OVERCLOCK_HEIGHT = MachineLayout.OVERCLOCK_FILL.height();
    private static final int OVERCLOCK_SPRITE_U = 176;
    private static final int OVERCLOCK_SPRITE_V = 180;
    private static final int OVERCLOCK_FRAME_U = 176;
    private static final int OVERCLOCK_FRAME_V = 198;

    private static final int REDSTONE_BUTTON_X = MachineLayout.REDSTONE_BUTTON.x();
    private static final int REDSTONE_BUTTON_Y = MachineLayout.REDSTONE_BUTTON.y();
    private static final int CONFIG_BUTTON_X = MachineLayout.CONFIG_BUTTON.x();
    private static final int CONFIG_BUTTON_Y = MachineLayout.CONFIG_BUTTON.y();
    private static final int MODE_BUTTON_X = MachineLayout.MODE_BUTTON.x();
    private static final int MODE_BUTTON_Y = MachineLayout.MODE_BUTTON.y();
    private static final int BUTTON_SIZE = MachineLayout.REDSTONE_BUTTON.width();

    // Slots, arrows and gauges are drawn rather than baked into the window: what a machine
    // shows varies per machine, and one window that stays the same is easier to keep right.
    protected static final int SLOT_SPRITE_U = 176;
    protected static final int SLOT_SPRITE_V = 154;
    protected static final int SLOT_SPRITE_SIZE = 18;
    /** Four 16x16 hints in {@link dev.symo.actualgenerators.machine.UpgradeType} order. */
    private static final int GHOST_SPRITE_U = 176;
    private static final int GHOST_SPRITE_V = 136;
    private static final int GHOST_SIZE = 16;
    /** The tier slot's hint, on the sheet after the four upgrade hints. */
    private static final int TIER_GHOST_U = 240;

    protected static final int READOUT_COLOUR = 0x404040;

    /** The frame round a gauge or a tank, one pixel larger than the fill all round. */
    protected static final int GAUGE_FRAME_U = 208;
    protected static final int GAUGE_FRAME_V = 16;
    protected static final int GAUGE_FRAME_WIDTH = 18;
    protected static final int GAUGE_FRAME_HEIGHT = 55;

    // The plain-text lines down the left of a window with no item slots to fill it.
    private static final int TEXT_X = 12;
    private static final int FIRST_LINE_Y = 26;
    private static final int SECOND_LINE_Y = 40;
    private static final int THIRD_LINE_Y = 54;

    protected static final int ARROW_X = MachineLayout.PROGRESS_ARROW.x();
    protected static final int ARROW_Y = MachineLayout.PROGRESS_ARROW.y();
    protected static final int ARROW_WIDTH = MachineLayout.PROGRESS_ARROW.width();
    protected static final int ARROW_HEIGHT = MachineLayout.PROGRESS_ARROW.height();
    private static final int ARROW_SPRITE_U = 176;
    private static final int ARROW_TRACK_U = 200;
    private static final int ARROW_SPRITE_V = 0;

    // Side configuration panel, drawn beside the window.
    private static final int PANEL_GAP = 4;
    private static final int PANEL_WIDTH = 62;
    // Tabs, three rows of faces, then the two auto-transfer toggles: 20 down to the cross,
    // 3 * FACE_SIZE for it, 2 to breathe, 16 for the toggles, 2 to spare.
    private static final int PANEL_HEIGHT = 96;
    private static final int FACE_SIZE = 18;
    private static final int AUTO_Y = 76;
    private static final int AUTO_WIDTH = 26;
    private static final int AUTO_HEIGHT = 16;

    private boolean sideConfigOpen;
    private TransferKind selectedKind = TransferKind.ITEM;

    /** The kind whose faces the panel shows: the first the machine moves, until a tab is picked. */
    private TransferKind selectedKind() {
        if (!menu.supportsKind(selectedKind)) {
            List<TransferKind> kinds = menu.sideKinds();
            selectedKind = kinds.isEmpty() ? TransferKind.ENERGY : kinds.get(0);
        }
        return selectedKind;
    }

    protected MachineScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        sideConfigOpen = menu.sidePanelOpenAtStart();
        this.imageWidth = MachineLayout.WIDTH;
        this.imageHeight = MachineLayout.HEIGHT;
        // Clear of the upgrade row, which ends at 96.
        this.inventoryLabelY = 100;
    }

    /**
     * The sheet this screen draws from. Sprites sit at the same coordinates on every sheet, so a
     * machine only needs its own when the window itself differs — a generator with no item slots
     * should not show empty ones.
     */
    protected ResourceLocation texture() {
        return TEXTURE;
    }

    /**
     * True when this machine is paid in FE rather than paying it, which flips what a worse number
     * looks like: a consumer's price for running hot is power drawn, a generator's is yield lost.
     */
    protected boolean isGenerator() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (sideConfigOpen) {
            renderSideConfigPanel(graphics, mouseX, mouseY);
        }
        // Ours first: an upgrade slot's tooltip replaces the plain item one rather than
        // stacking a second box on top of it.
        if (!renderCustomTooltips(graphics, mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture(), leftPos, topPos, 0, 0, imageWidth, imageHeight);

        renderSlotFrames(graphics);
        renderEnergyBar(graphics);
        if (menu.hasTank()) {
            renderTank(graphics);
        }
        if (menu.hasRamp()) {
            renderOverclockBar(graphics);
        }
        if (menu.hasProgressArrow()) {
            renderProgressArrow(graphics, menu.progress());
        }
        renderMachineExtras(graphics);

        drawButton(graphics, leftPos + REDSTONE_BUTTON_X, topPos + REDSTONE_BUTTON_Y,
                redstoneColour(), mouseX, mouseY);
        if (menu.hasSideConfig()) {
            drawButton(graphics, leftPos + CONFIG_BUTTON_X, topPos + CONFIG_BUTTON_Y,
                    sideConfigOpen ? 0xFF6FA8DC : 0xFF8B8B8B, mouseX, mouseY);
        }
        if (menu.hasModeButton()) {
            drawButton(graphics, leftPos + MODE_BUTTON_X, topPos + MODE_BUTTON_Y, modeColour(), mouseX, mouseY);
        }
    }

    /** The colour of the mode button, for a machine that has one. */
    protected int modeColour() {
        return 0xFF8B8B8B;
    }

    /** What the mode button says it would do, for a machine that has one. */
    protected Component modeTooltip() {
        return Component.empty();
    }

    /** Machine-specific overlays, such as a progress arrow. */
    protected void renderMachineExtras(GuiGraphics graphics) {
    }

    /** The tank beside the gauge: the fluid's own sprite, filled from the bottom, in the gauge's frame. */
    protected void renderTank(GuiGraphics graphics) {
        MachineLayout.Box tank = MachineLayout.TANK_FILL;
        graphics.blit(texture(), leftPos + tank.x() - 1, topPos + tank.y() - 1,
                GAUGE_FRAME_U, GAUGE_FRAME_V, GAUGE_FRAME_WIDTH, GAUGE_FRAME_HEIGHT);
        FluidStack fluid = menu.tankFluid();
        int capacity = menu.tankCapacity();
        if (fluid.isEmpty() || capacity <= 0) {
            return;
        }
        int filled = Math.max(1, (int) (tank.height() * Math.clamp(fluid.getAmount() / (double) capacity, 0.0, 1.0)));
        Chrome.drawFluidColumn(graphics, fluid, leftPos + tank.x(), topPos + tank.y() + tank.height() - filled,
                tank.width(), filled);
    }

    /** What the tank holds, and that a container on the cursor fills from it. */
    protected List<FormattedCharSequence> tankTooltip() {
        FluidStack fluid = menu.tankFluid();
        Component line = fluid.isEmpty()
                ? Component.translatable("gui.actualgenerators.tank.empty", formatNumber(menu.tankCapacity()))
                : Component.translatable("gui.actualgenerators.tank", fluid.getHoverName(),
                        formatNumber(fluid.getAmount()), formatNumber(menu.tankCapacity()));
        return List.of(line.getVisualOrderText(),
                Component.translatable("gui.actualgenerators.tank.hint").withStyle(ChatFormatting.GRAY).getVisualOrderText());
    }

    /**
     * Machine-specific hover text.
     *
     * @return true if something was drawn, in which case the vanilla item tooltip is skipped
     */
    protected boolean renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineMenu.UpgradeSlot upgrade = hoveredUpgradeSlot(mouseX, mouseY);
        if (upgrade != null) {
            graphics.renderTooltip(font, upgradeTooltip(upgrade.upgradeType()), mouseX, mouseY);
        } else if (hoveredTierSlot(mouseX, mouseY)) {
            graphics.renderTooltip(font, tierTooltip(), mouseX, mouseY);
        } else if (isOver(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT)) {
            graphics.renderTooltip(font, energyTooltip(), mouseX, mouseY);
        } else if (menu.hasTank() && Chrome.isOver(mouseX, mouseY, leftPos, topPos, MachineLayout.TANK_FILL)) {
            graphics.renderTooltip(font, tankTooltip(), mouseX, mouseY);
        } else if (menu.hasRamp()
                && isOver(mouseX, mouseY, leftPos + OVERCLOCK_X, topPos + OVERCLOCK_Y, OVERCLOCK_WIDTH, OVERCLOCK_HEIGHT)) {
            graphics.renderTooltip(font, rampTooltip(), mouseX, mouseY);
        } else if (isOver(mouseX, mouseY, leftPos + REDSTONE_BUTTON_X, topPos + REDSTONE_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            graphics.renderTooltip(font, List.of(
                            Component.translatable("gui.actualgenerators.redstone",
                                    Component.translatable(redstoneKey())).getVisualOrderText()),
                    mouseX, mouseY);
        } else if (menu.hasSideConfig()
                && isOver(mouseX, mouseY, leftPos + CONFIG_BUTTON_X, topPos + CONFIG_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("gui.actualgenerators.side_config").getVisualOrderText()), mouseX, mouseY);
        } else if (menu.hasModeButton()
                && isOver(mouseX, mouseY, leftPos + MODE_BUTTON_X, topPos + MODE_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            graphics.renderTooltip(font, List.of(modeTooltip().getVisualOrderText()), mouseX, mouseY);
        } else if (menu.hasProgressArrow()
                && isOver(mouseX, mouseY, leftPos + ARROW_X, topPos + ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT)) {
            graphics.renderTooltip(font, progressTooltip(), mouseX, mouseY);
        } else {
            return false;
        }
        return true;
    }

    /**
     * What the arrow is actually at. A bar that only fills is a guess; a number is not, and the
     * difference matters when a player is working out whether a machine is stuck or merely slow.
     */
    protected List<FormattedCharSequence> progressTooltip() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.actualgenerators.progress", percent(menu.progress()))
                .getVisualOrderText());
        if (menu.progress() <= 0) {
            lines.add(Component.translatable("gui.actualgenerators.progress.idle")
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        return lines;
    }

    /** Stored, capacity, and what the machine is doing to that number right now. */
    private List<FormattedCharSequence> energyTooltip() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.actualgenerators.energy",
                formatNumber(menu.energyStored()), formatNumber(menu.energyCapacity())).getVisualOrderText());

        int rate = menu.energyRate();
        if (rate > 0) {
            lines.add(Component.translatable("gui.actualgenerators.rate.generating", formatNumber(rate))
                    .withStyle(ChatFormatting.GREEN).getVisualOrderText());
        } else if (rate < 0) {
            lines.add(Component.translatable("gui.actualgenerators.rate.consuming", formatNumber(-rate))
                    .withStyle(ChatFormatting.RED).getVisualOrderText());
        } else {
            lines.add(Component.translatable("gui.actualgenerators.rate.idle")
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        return lines;
    }

    /**
     * The ramp, read out. A machine that burns fuel is overclocking past its rating; one the world
     * pays is warming up towards it, so the same bar has to say two different things.
     */
    private List<FormattedCharSequence> rampTooltip() {
        boolean warmup = menu.usesWarmupRamp();
        String key = warmup ? "warmup" : "overclock";
        double progress = menu.overclockProgress();

        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.actualgenerators." + key, percent(progress)).getVisualOrderText());
        if (warmup) {
            lines.add(Component.translatable("gui.actualgenerators.warmup.output",
                            percent(menu.tuning().warmupMultiplier(progress)))
                    .withStyle(ChatFormatting.GREEN).getVisualOrderText());
        }
        lines.add(Component.translatable("gui.actualgenerators." + key + ".hint")
                .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        return lines;
    }

    /**
     * What this upgrade is doing for the machine and what one more would add — numbers only, green
     * for what it buys and red for what it costs.
     *
     * <p>The point is that nobody should have to guess or go and read a wiki: the slot itself says
     * what is installed, what it is worth in the machine's own numbers, and what the next one buys.
     */
    private List<FormattedCharSequence> upgradeTooltip(UpgradeType type) {
        int installed = menu.installedUpgrades(type);
        int max = menu.maxUpgrades(type);

        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(ModItems.upgradeItem(type).toStack().getHoverName().getVisualOrderText());
        lines.add(Component.translatable("gui.actualgenerators.upgrade.installed", installed, max)
                .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        for (Component effect : upgradeEffects(type, installed, max)) {
            lines.add(effect.getVisualOrderText());
        }
        return lines;
    }

    private List<Component> upgradeEffects(UpgradeType type, int installed, int max) {
        MachineTuning tuning = menu.tuning();
        int next = Math.min(installed + 1, max);
        boolean maxed = next == installed;

        return switch (type) {
            case ENERGY -> List.of(
                    gain("buffer", tuning.capacity(menu.baseCapacity(), installed),
                            tuning.capacity(menu.baseCapacity(), next), maxed),
                    gain("transfer", tuning.transferRate(menu.baseTransferRate(), installed),
                            tuning.transferRate(menu.baseTransferRate(), next), maxed));
            case SPEED -> {
                int overclock = menu.installedUpgrades(UpgradeType.OVERCLOCK);
                yield List.of(
                        gain("speed", tuning.speedMultiplier(installed), tuning.speedMultiplier(next), maxed),
                        priceOfSpeed(tuning,
                                tuning.totalSpeedMultiplier(installed, overclock, 1.0),
                                tuning.totalSpeedMultiplier(next, overclock, 1.0), maxed));
            }
            case OVERCLOCK -> {
                int speed = menu.installedUpgrades(UpgradeType.SPEED);
                double now = tuning.totalSpeedMultiplier(speed, installed, 1.0);
                double then = tuning.totalSpeedMultiplier(speed, next, 1.0);
                yield List.of(gain("overclock", now, then, maxed), priceOfSpeed(tuning, now, then, maxed));
            }
            case STACK -> {
                int now = tuning.maxBatch(installed);
                int then = tuning.maxBatch(next);
                Component batch = gain("batch", now, then, maxed);
                // A generator paid per item earns linearly with the batch and pays nothing extra
                // for it; only a consumer's power bill grows.
                yield isGenerator()
                        ? List.of(batch)
                        : List.of(batch, cost("power", now, then, maxed));
            }
        };
    }

    /**
     * The price of running hot. A consumer pays it in power drawn, a generator in yield per item;
     * either way it is the number that gets worse, so it is the red one.
     */
    private Component priceOfSpeed(MachineTuning tuning, double now, double then, boolean maxed) {
        return isGenerator()
                ? percentCost("yield", tuning.generatorEfficiency(now), tuning.generatorEfficiency(then), maxed)
                : cost("power", tuning.energyCostMultiplier(now), tuning.energyCostMultiplier(then), maxed);
    }

    private static Component gain(String key, long now, long next, boolean maxed) {
        return line(key, formatNumber(now), formatNumber(next), maxed, ChatFormatting.GREEN);
    }

    private static Component gain(String key, double now, double next, boolean maxed) {
        return line(key, formatMultiplier(now), formatMultiplier(next) + "x", maxed, ChatFormatting.GREEN);
    }

    private static Component cost(String key, double now, double next, boolean maxed) {
        return line(key, formatMultiplier(now), formatMultiplier(next) + "x", maxed, ChatFormatting.RED);
    }

    /** A cost expressed as a percentage that falls as the upgrade goes in. */
    private static Component percentCost(String key, double now, double next, boolean maxed) {
        return line(key, String.valueOf(percent(now)), percent(next) + "%", maxed, ChatFormatting.RED);
    }

    /**
     * One effect line: what the machine is at now, and what it would be with one more upgrade.
     *
     * <p>The second figure is the value after the next upgrade rather than the difference. A
     * signed delta reads as something already happening — a red "(-18%)" beside "100%" looks like
     * a penalty being applied right now — where "100% → 82%" can only mean one thing.
     */
    private static Component line(String key, String value, String next, boolean maxed, ChatFormatting colour) {
        Component change = maxed
                ? Component.translatable("gui.actualgenerators.upgrade.maxed").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("gui.actualgenerators.upgrade.next", next);
        return Component.translatable("gui.actualgenerators.upgrade." + key, value, change).withStyle(colour);
    }

    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100);
    }

    /** Grouped digits, because six-figure FE numbers are unreadable without them. */
    protected static String formatNumber(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    protected static String formatMultiplier(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /**
     * Draws the frame for each upgrade slot the machine shows, and behind an empty one a faded
     * hint of the upgrade that belongs there — so a machine with one slot reads as "energy goes
     * here" rather than "one mystery slot".
     */
    private void renderSlotFrames(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (!(slot instanceof SlotItemHandler)) {
                continue;
            }
            graphics.blit(texture(), leftPos + slot.x - 1, topPos + slot.y - 1,
                    SLOT_SPRITE_U, SLOT_SPRITE_V, SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE);
            if (slot instanceof MachineMenu.UpgradeSlot upgradeSlot && !slot.hasItem()) {
                graphics.blit(texture(), leftPos + slot.x, topPos + slot.y,
                        GHOST_SPRITE_U + upgradeSlot.upgradeType().ordinal() * GHOST_SIZE, GHOST_SPRITE_V,
                        GHOST_SIZE, GHOST_SIZE);
            }
            if (slot instanceof MachineMenu.TierSlot && !slot.hasItem()) {
                graphics.blit(texture(), leftPos + slot.x, topPos + slot.y,
                        TIER_GHOST_U, GHOST_SPRITE_V, GHOST_SIZE, GHOST_SIZE);
            }
        }
    }

    /**
     * The standard left-to-right progress arrow, empty groove and all. Machines that show one
     * put it in the same place, so a player learns it once.
     */
    protected void renderProgressArrow(GuiGraphics graphics, double fraction) {
        graphics.blit(texture(), leftPos + ARROW_X, topPos + ARROW_Y,
                ARROW_TRACK_U, ARROW_SPRITE_V, ARROW_WIDTH, ARROW_HEIGHT);
        int filled = (int) (ARROW_WIDTH * Math.clamp(fraction, 0.0, 1.0));
        if (filled > 0) {
            graphics.blit(texture(), leftPos + ARROW_X, topPos + ARROW_Y,
                    ARROW_SPRITE_U, ARROW_SPRITE_V, filled, ARROW_HEIGHT);
        }
    }

    protected void renderReadout(GuiGraphics graphics, Component first, Component second) {
        drawReadout(graphics, TEXT_X, FIRST_LINE_Y, first);
        drawReadout(graphics, TEXT_X, SECOND_LINE_Y, second);
    }

    protected void renderReadout(GuiGraphics graphics, Component first, Component second, Component third) {
        renderReadout(graphics, first, second);
        drawReadout(graphics, TEXT_X, THIRD_LINE_Y, third);
    }

    /** One line of dark readout text, positioned relative to the window. */
    protected void drawReadout(GuiGraphics graphics, int x, int y, Component line) {
        drawReadout(graphics, x, y, line, READOUT_COLOUR);
    }

    /** The same, in a colour of its own — for a line that is bad news rather than information. */
    protected void drawReadout(GuiGraphics graphics, int x, int y, Component line, int colour) {
        graphics.drawString(font, line, leftPos + x, topPos + y, colour, false);
    }

    /** A machine slot can hold more than a stack; a count that would run out of the slot is written short. */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        ItemStack stack = slot.getItem();
        if (!(slot instanceof SlotItemHandler) || stack.getCount() < 1000) {
            super.renderSlot(graphics, slot);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        graphics.renderItem(stack, slot.x, slot.y, slot.x + slot.y * imageWidth);
        graphics.renderItemDecorations(font, stack, slot.x, slot.y, Chrome.compactCount(stack.getCount()));
        graphics.pose().popPose();
    }

    private boolean hoveredTierSlot(int mouseX, int mouseY) {
        for (Slot slot : menu.slots) {
            if (slot instanceof MachineMenu.TierSlot
                    && isOver(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, GHOST_SIZE, GHOST_SIZE)) {
                return true;
            }
        }
        return false;
    }

    /** The tier and its two numbers, both green: a tier only ever buys, there is no price line. */
    private List<FormattedCharSequence> tierTooltip() {
        MachineTier tier = menu.tier();
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.actualgenerators.tier.slot", tier.displayName()).getVisualOrderText());
        lines.add(Component.translatable("gui.actualgenerators.tier.speed", TierUpgradeItem.multiplier(tier.speedMultiplier()))
                .withStyle(ChatFormatting.GREEN).getVisualOrderText());
        lines.add(Component.translatable("gui.actualgenerators.tier.batch", tier.batchMultiplier())
                .withStyle(ChatFormatting.GREEN).getVisualOrderText());
        lines.add(Component.translatable("gui.actualgenerators.tier.hint")
                .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }

    /** The upgrade slot under the cursor, if any — empty or not. */
    private MachineMenu.@org.jetbrains.annotations.Nullable UpgradeSlot hoveredUpgradeSlot(int mouseX, int mouseY) {
        for (Slot slot : menu.slots) {
            if (slot instanceof MachineMenu.UpgradeSlot upgradeSlot
                    && isOver(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, GHOST_SIZE, GHOST_SIZE)) {
                return upgradeSlot;
            }
        }
        return null;
    }

    private void renderEnergyBar(GuiGraphics graphics) {
        long capacity = menu.energyCapacity();
        if (capacity <= 0) {
            return;
        }
        int filled = (int) (ENERGY_HEIGHT * Math.clamp(menu.energyStored() / (double) capacity, 0.0, 1.0));
        if (filled <= 0) {
            return;
        }
        // Fill upwards from the bottom of the bar.
        int top = ENERGY_Y + (ENERGY_HEIGHT - filled);
        graphics.blit(texture(), leftPos + ENERGY_X, topPos + top,
                ENERGY_SPRITE_U, ENERGY_SPRITE_V + (ENERGY_HEIGHT - filled), ENERGY_WIDTH, filled);
    }

    /**
     * The overclock ramp, in the empty space beside the upgrade row, with the number written on
     * it. It is the one reading that changes while you watch, so it is worth the space.
     */
    private void renderOverclockBar(GuiGraphics graphics) {
        graphics.blit(texture(), leftPos + OVERCLOCK_X - 1, topPos + OVERCLOCK_Y - 1,
                OVERCLOCK_FRAME_U, OVERCLOCK_FRAME_V, OVERCLOCK_WIDTH + 2, OVERCLOCK_HEIGHT + 2);

        double progress = menu.overclockProgress();
        int filled = (int) (OVERCLOCK_WIDTH * progress);
        if (filled > 0) {
            graphics.blit(texture(), leftPos + OVERCLOCK_X, topPos + OVERCLOCK_Y,
                    OVERCLOCK_SPRITE_U, OVERCLOCK_SPRITE_V, filled, OVERCLOCK_HEIGHT);
        }

        Component label = Component.literal(Math.round(progress * 100) + "%");
        graphics.drawString(font, label,
                leftPos + OVERCLOCK_X + (OVERCLOCK_WIDTH - font.width(label)) / 2,
                topPos + OVERCLOCK_Y + 4, 0xFFFFFF, true);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int colour, int mouseX, int mouseY) {
        graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, colour);
        if (isOver(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE)) {
            graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, 0x40FFFFFF);
        }
    }

    private int redstoneColour() {
        return switch (menu.redstoneMode()) {
            case ALWAYS -> 0xFF8B8B8B;
            case WITH_SIGNAL -> 0xFFC03030;
            case WITHOUT_SIGNAL -> 0xFF50A050;
            case NEVER -> 0xFF303030;
        };
    }

    private String redstoneKey() {
        return "gui.actualgenerators.redstone." + menu.redstoneMode().name().toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ side configuration

    private int panelLeft() {
        return leftPos + imageWidth + PANEL_GAP;
    }

    private int panelTop() {
        return topPos + 8;
    }

    private void renderSideConfigPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelLeft();
        int y = panelTop();

        graphics.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + PANEL_HEIGHT + 1, 0xFF373737);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFFC6C6C6);

        // Resource kind tabs: only the kinds this machine moves.
        List<TransferKind> kinds = menu.sideKinds();
        for (int tab = 0; tab < kinds.size(); tab++) {
            TransferKind kind = kinds.get(tab);
            int tabX = x + 2 + tab * 20;
            boolean active = kind == selectedKind();
            graphics.fill(tabX, y + 2, tabX + 18, y + 16, active ? 0xFF6FA8DC : 0xFF8B8B8B);
            graphics.drawString(font, kindLabel(kind), tabX + 6, y + 5, active ? 0xFFFFFF : 0x404040, false);
        }

        // The faces, laid out as a cross so they map onto the machine in front of you.
        for (RelativeSide side : RelativeSide.all()) {
            int[] cell = faceCell(side);
            int faceX = x + 4 + cell[0] * FACE_SIZE;
            int faceY = y + 20 + cell[1] * FACE_SIZE;
            IoMode mode = menu.sideMode(selectedKind(), side);
            boolean blocked = menu.sideBlocked(side);

            graphics.fill(faceX, faceY, faceX + FACE_SIZE - 1, faceY + FACE_SIZE - 1, 0xFF373737);
            if (blocked) {
                graphics.fill(faceX + 1, faceY + 1, faceX + FACE_SIZE - 2, faceY + FACE_SIZE - 2, BLOCKED_COLOUR);
            } else {
                fillMode(graphics, faceX + 1, faceY + 1, faceX + FACE_SIZE - 2, faceY + FACE_SIZE - 2, mode);
            }
            graphics.drawString(font, faceLabel(side), faceX + 5, faceY + 5, blocked ? 0x707070 : 0x202020, false);

            if (isOver(mouseX, mouseY, faceX, faceY, FACE_SIZE, FACE_SIZE)) {
                if (!blocked) {
                    graphics.fill(faceX + 1, faceY + 1, faceX + FACE_SIZE - 2, faceY + FACE_SIZE - 2, 0x40FFFFFF);
                }
                List<net.minecraft.util.FormattedCharSequence> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.actualgenerators.side." + side.name().toLowerCase(java.util.Locale.ROOT))
                        .getVisualOrderText());
                tooltip.add((blocked
                        ? Component.translatable("gui.actualgenerators.side.blocked").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.translatable("gui.actualgenerators.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)))
                        .getVisualOrderText());
                graphics.renderTooltip(font, tooltip, mouseX, mouseY);
            }
        }

        renderAutoToggles(graphics, x, y, mouseX, mouseY);
    }

    /**
     * The two toggles under the cross: whether the machine pulls this kind in and pushes it out of
     * its own accord. Separate from the faces above them, which say what is allowed through — a
     * face left open with auto-output off is one a pipe may extract from and the machine will not
     * shove anything into.
     */
    private void renderAutoToggles(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        for (boolean push : new boolean[]{false, true}) {
            int buttonX = autoLeft(x, push);
            int buttonY = y + AUTO_Y;
            boolean on = menu.autoEnabled(selectedKind(), push);

            graphics.fill(buttonX, buttonY, buttonX + AUTO_WIDTH, buttonY + AUTO_HEIGHT, 0xFF373737);
            graphics.fill(buttonX + 1, buttonY + 1, buttonX + AUTO_WIDTH - 1, buttonY + AUTO_HEIGHT - 1,
                    on ? 0xFF5EAC8D : 0xFF8B8B8B);

            String label = push ? "OUT" : "IN";
            graphics.drawString(font, label,
                    buttonX + (AUTO_WIDTH - font.width(label)) / 2, buttonY + 4,
                    on ? 0xFFFFFF : 0x404040, false);

            if (isOver(mouseX, mouseY, buttonX, buttonY, AUTO_WIDTH, AUTO_HEIGHT)) {
                graphics.fill(buttonX + 1, buttonY + 1, buttonX + AUTO_WIDTH - 1, buttonY + AUTO_HEIGHT - 1, 0x40FFFFFF);
                graphics.renderTooltip(font, autoTooltip(push, on), mouseX, mouseY);
            }
        }
    }

    private List<FormattedCharSequence> autoTooltip(boolean push, boolean on) {
        Component state = Component.translatable("gui.actualgenerators.auto." + (on ? "on" : "off"));
        return List.of(
                Component.translatable("gui.actualgenerators.auto." + (push ? "out" : "in"), state)
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY).getVisualOrderText(),
                Component.translatable("gui.actualgenerators.auto.hint")
                        .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
    }

    private static int autoLeft(int panelX, boolean push) {
        return panelX + 4 + (push ? AUTO_WIDTH + 2 : 0);
    }

    /**
     * The cube unfolded: front in the middle of the cross, back tucked into the corner under the
     * left face, which is where the rest of the genre puts it.
     */
    private static int[] faceCell(RelativeSide side) {
        return switch (side) {
            case TOP -> new int[]{1, 0};
            case LEFT -> new int[]{0, 1};
            case FRONT -> new int[]{1, 1};
            case RIGHT -> new int[]{2, 1};
            case BACK -> new int[]{0, 2};
            case BOTTOM -> new int[]{1, 2};
        };
    }

    private static final int INPUT_COLOUR = 0xFF3E9BD8;
    private static final int OUTPUT_COLOUR = 0xFFD8813E;
    private static final int BLOCKED_COLOUR = 0xFF4A4A4A;

    private static int modeColour(IoMode mode) {
        return switch (mode) {
            case DISABLED -> 0xFF8B8B8B;
            case INPUT -> INPUT_COLOUR;
            case OUTPUT -> OUTPUT_COLOUR;
            case BOTH -> INPUT_COLOUR;
        };
    }

    /**
     * A face in its mode's colour; "both" is the two colours split along the diagonal, input in
     * the upper right and output in the lower left, the same split the markers on a hatch wear.
     */
    private static void fillMode(GuiGraphics graphics, int x0, int y0, int x1, int y1, IoMode mode) {
        if (mode != IoMode.BOTH) {
            graphics.fill(x0, y0, x1, y1, modeColour(mode));
            return;
        }
        int width = x1 - x0;
        int height = y1 - y0;
        for (int row = 0; row < height; row++) {
            int split = x0 + width * row / Math.max(height - 1, 1);
            if (split > x0) {
                graphics.fill(x0, y0 + row, split, y0 + row + 1, OUTPUT_COLOUR);
            }
            if (split < x1) {
                graphics.fill(split, y0 + row, x1, y0 + row + 1, INPUT_COLOUR);
            }
        }
    }

    private static String kindLabel(TransferKind kind) {
        return switch (kind) {
            case ITEM -> "I";
            case FLUID -> "F";
            case ENERGY -> "E";
            case REDSTONE -> "R";
        };
    }

    private static String faceLabel(RelativeSide side) {
        return switch (side) {
            case FRONT -> "F";
            case BACK -> "B";
            case LEFT -> "L";
            case RIGHT -> "R";
            case TOP -> "U";
            case BOTTOM -> "D";
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOver(mouseX, mouseY, leftPos + REDSTONE_BUTTON_X, topPos + REDSTONE_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            pressButton(MachineMenu.BUTTON_CYCLE_REDSTONE);
            return true;
        }
        if (menu.hasTank() && !menu.getCarried().isEmpty()
                && Chrome.isOver(mouseX, mouseY, leftPos, topPos, MachineLayout.TANK_FILL)) {
            // A container on the cursor, on the tank: the server fills or empties it.
            pressButton(MachineMenu.BUTTON_TANK);
            return true;
        }
        if (menu.hasModeButton()
                && isOver(mouseX, mouseY, leftPos + MODE_BUTTON_X, topPos + MODE_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            pressButton(MachineMenu.BUTTON_TOGGLE_MODE);
            return true;
        }
        if (menu.hasSideConfig()
                && isOver(mouseX, mouseY, leftPos + CONFIG_BUTTON_X, topPos + CONFIG_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            sideConfigOpen = !sideConfigOpen;
            playClick();
            return true;
        }
        if (sideConfigOpen && handleSideConfigClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSideConfigClick(double mouseX, double mouseY) {
        int x = panelLeft();
        int y = panelTop();

        List<TransferKind> kinds = menu.sideKinds();
        for (int tab = 0; tab < kinds.size(); tab++) {
            TransferKind kind = kinds.get(tab);
            int tabX = x + 2 + tab * 20;
            if (isOver(mouseX, mouseY, tabX, y + 2, 18, 14)) {
                selectedKind = kind;
                playClick();
                return true;
            }
        }

        for (RelativeSide side : RelativeSide.all()) {
            int[] cell = faceCell(side);
            int faceX = x + 4 + cell[0] * FACE_SIZE;
            int faceY = y + 20 + cell[1] * FACE_SIZE;
            if (isOver(mouseX, mouseY, faceX, faceY, FACE_SIZE, FACE_SIZE)) {
                if (!menu.sideBlocked(side)) {
                    pressButton(MachineMenu.BUTTON_SIDES_START + selectedKind().ordinal() * 6 + side.ordinal());
                }
                return true;
            }
        }

        for (boolean push : new boolean[]{false, true}) {
            if (isOver(mouseX, mouseY, autoLeft(x, push), y + AUTO_Y, AUTO_WIDTH, AUTO_HEIGHT)) {
                pressButton(MachineMenu.BUTTON_AUTO_START + selectedKind().ordinal() * 2 + (push ? 1 : 0));
                return true;
            }
        }
        return false;
    }

    void pressButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            playClick();
        }
    }

    protected void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    protected static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
