package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.network.SetPadLabelPayload;
import dev.symo.actualgenerators.network.ImportPadConfigPayload;
import dev.symo.actualgenerators.logistics.PadSnapshot;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.item.FilterItem;
import dev.symo.actualgenerators.logistics.AmountExpression;
import dev.symo.actualgenerators.logistics.ChannelSettings;
import dev.symo.actualgenerators.logistics.Distribution;
import dev.symo.actualgenerators.logistics.PortChannel;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.logistics.SignalSource;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.LinkPortMenu;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.network.SetPadNumberPayload;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * The pad's window, drawn from the job it does.
 *
 * <p>Along the top sits the palette of sixteen channels, each cell showing its colour, a small
 * picture per kind it carries and whether this pad sends or receives on it. Below is the chosen
 * channel's name with the open kind's, then four tabs, one picture per kind. The open tab is one
 * link: whether the channel carries the kind, IN, EX and PU, spread, redstone, the Filter slot
 * and the four numbers, then the network line. The redstone tab has no door and no spread, one
 * number, and a source button where PU would be. Every box is one the menu lists, so the layout
 * test sees exactly what the player does.
 *
 * <p>A left click does the thing. A right click on an arrow is the bigger step. A click on a
 * number opens it for typing, with sums and suffixes ({@code 400k}, {@code 64*3}); Enter sets
 * it, Escape leaves it. The tooltips say which, in a line each.
 */
public class LinkPortScreen extends AbstractContainerScreen<LinkPortMenu> {

    /** Seven-by-seven pictures of the four kinds, for the tabs: a chest, a drop, a bolt, a torch's head. */
    static final String[][] GLYPHS = {
            {
                    ".#####.",
                    "#.....#",
                    "#######",
                    "#..#..#",
                    "#.....#",
                    "#.....#",
                    ".#####."},
            {
                    "...#...",
                    "..###..",
                    ".#####.",
                    "#######",
                    "#######",
                    ".#####.",
                    "..###.."},
            {
                    "....##.",
                    "...##..",
                    "..##...",
                    ".#####.",
                    "...##..",
                    "..##...",
                    ".##...."},
            {
                    "..###..",
                    "..###..",
                    "..###..",
                    ".......",
                    ".......",
                    ".......",
                    "......."}};

    /** The torch's stick, drawn under its head in brown: a red silhouette alone was a second bolt. */
    static final String[] STICK = {
            ".......",
            ".......",
            ".......",
            "...#...",
            "...#...",
            "...#...",
            "...#..."};

    /** Five-by-five versions for the palette, two by two in one cell. */
    static final String[][] MINI = {
            {
                    ".###.",
                    "#...#",
                    "#####",
                    "#.#.#",
                    ".###."},
            {
                    "..#..",
                    ".###.",
                    "#####",
                    "#####",
                    ".###."},
            {
                    "..##.",
                    ".##..",
                    ".###.",
                    "..#..",
                    ".#..."},
            {
                    ".###.",
                    ".###.",
                    ".....",
                    ".....",
                    "....."}};

    static final String[] MINI_STICK = {
            ".....",
            ".....",
            "..#..",
            "..#..",
            "..#.."};

    /** Where each kind's small picture sits in a palette cell, by ordinal: two rows of two. */
    private static final int[] MINI_X = {2, 8, 2, 8};
    private static final int[] MINI_Y = {2, 2, 8, 8};

    private static final String[] FIELD_CAPTIONS = {
            "gui.actualgenerators.link.caption.keep",
            "gui.actualgenerators.link.caption.amount",
            "gui.actualgenerators.link.caption.priority",
            "gui.actualgenerators.link.caption.delay"};

    private static final int EDITOR_INK = 0xFFE0E0E0;
    private static final int EDITOR_BAD = 0xFFFF5050;

    /** The number being typed, while one is; or the label, with {@link #editing} at {@link #EDIT_LABEL}. */
    private @Nullable EditBox editor;
    private int editing = -1;
    private static final int EDIT_LABEL = -2;
    private static final int SUGGESTIONS = 6;
    private static final int LABEL_INK = 0xFFF0E68C;
    private static final String[] EXPORT_GLYPH = {
            "...#...",
            "..###..",
            ".#.#.#.",
            "...#...",
            "...#...",
            "#.....#",
            "#######"};
    private static final String[] IMPORT_GLYPH = {
            "...#...",
            "...#...",
            "...#...",
            ".#.#.#.",
            "..###..",
            "#..#..#",
            "#######"};
    /** A label the network already knows, typed or picked: the two-way question stands until it is answered. */
    private @Nullable String chosenLabel;
    /** The banner counts the player has already looked at, packed; the banner hides until they change. */
    private int bannerSeen;

    public LinkPortScreen(LinkPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LinkPortMenu.WIDTH;
        this.imageHeight = LinkPortMenu.HEIGHT;
        this.inventoryLabelY = LinkPortMenu.HEIGHT - 94;
    }

    @Override
    protected void init() {
        super.init();
        editor = null;
        editing = -1;
        chosenLabel = null;
    }

    /** No title: the label box stands where it would be. The inventory keeps its word. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Chrome.TEXT, false);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawLabelList(graphics, mouseX, mouseY);
        if (editing == EDIT_LABEL) {
            return; // The popover has the window: nothing under it explains itself while it is up.
        }
        List<Component> lines = tooltipAt(mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Chrome.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                Chrome.slotFrame(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
        // Empty slots show a faded picture of what they take, so none is a mystery.
        for (Slot slot : menu.slots) {
            if (slot.isActive() && !slot.hasItem()) {
                if (slot instanceof LinkPortMenu.FilterSlot) {
                    hint(graphics, slot, ModItems.FILTER.toStack());
                } else if (slot instanceof LinkPortMenu.UpgradeSlot upgrade) {
                    hint(graphics, slot, switch (upgrade.getSlotIndex()) {
                        case PortFace.SLOT_RANGE -> ModItems.LINK_RANGE_UPGRADE.toStack();
                        case PortFace.SLOT_TIER -> ModItems.IRON_TIER_UPGRADE.toStack();
                        default -> ModItems.UNBOUND_LINK_CARD.toStack();
                    });
                }
            }
        }

        PortFace pad = menu.face();
        drawPalette(graphics, pad, mouseX, mouseY);
        drawChannel(graphics);
        drawTabs(graphics, mouseX, mouseY);
        drawSwitches(graphics, mouseX, mouseY);
        for (int field = 0; field < LinkPortMenu.FIELD_COUNT; field++) {
            drawField(graphics, field, menu.activeKind(), mouseX, mouseY);
        }
        drawNetwork(graphics, pad, mouseX, mouseY);
        drawLabelBox(graphics, mouseX, mouseY);
        drawBay(graphics, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ the label box and the bay

    private void drawLabelBox(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineLayout.Box box = LinkPortMenu.LABEL_BOX;
        Chrome.box(graphics, leftPos, topPos, box, Chrome.BUTTON_EDGE, Chrome.DARK, mouseX, mouseY);
        int x = leftPos + box.x() + 3;
        int y = topPos + box.y() + 2;
        int banner = bannerPacked();
        if (editing != EDIT_LABEL) {
            if (banner != 0 && banner != bannerSeen) {
                graphics.drawString(font, bannerText(banner), x, y, EDITOR_BAD, false);
            } else if (menu.label().isEmpty()) {
                graphics.drawString(font, Component.translatable("gui.actualgenerators.link.label.empty"), x, y, Chrome.MUTED, false);
            } else {
                graphics.drawString(font, menu.label(), x, y, LABEL_INK, false);
            }
        }
        graphics.drawString(font, "\u25be", leftPos + box.x() + box.width() - 8, y, Chrome.MUTED, false);
    }

    /**
     * Under the open label box, on a panel of its own over the palette: the network's labels that
     * start with what is typed, the hovered one lit, or the two-way question with the label named
     * over it. Nothing under the panel can be seen or reached while it is up.
     */
    private void drawLabelList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (editing != EDIT_LABEL || editor == null) {
            return;
        }
        if (chosenLabel != null) {
            MachineLayout.Box last = LinkPortMenu.LABEL_PUSH_BUTTON;
            popoverPanel(graphics, last.y() + last.height());
            Chrome.text(graphics, font, leftPos, topPos, LinkPortMenu.LABEL_CAPTION,
                    Component.translatable("gui.actualgenerators.link.label.exists", chosenLabel), Chrome.MUTED);
            popoverButton(graphics, LinkPortMenu.LABEL_PULL_BUTTON, Chrome.ACCENT, "gui.actualgenerators.link.label.pull", mouseX, mouseY);
            popoverButton(graphics, LinkPortMenu.LABEL_PUSH_BUTTON, Chrome.STEPPER, "gui.actualgenerators.link.label.push", mouseX, mouseY);
            return;
        }
        List<String> rows = suggestions();
        MachineLayout.Box last = LinkPortMenu.labelRow(Math.max(0, rows.size() - 1));
        popoverPanel(graphics, last.y() + last.height());
        if (rows.isEmpty()) {
            Chrome.text(graphics, font, leftPos, topPos, LinkPortMenu.labelRow(0), Component.translatable(menu.labels().isEmpty()
                    ? "gui.actualgenerators.link.label.none" : "gui.actualgenerators.link.label.nomatch"), Chrome.MUTED);
            return;
        }
        for (int index = 0; index < rows.size(); index++) {
            MachineLayout.Box row = LinkPortMenu.labelRow(index);
            boolean hovered = over(mouseX, mouseY, row);
            Chrome.box(graphics, leftPos, topPos, row, hovered ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE,
                    hovered ? Chrome.ACCENT : Chrome.DARK, mouseX, mouseY);
            graphics.drawString(font, rows.get(index), leftPos + row.x() + 3, topPos + row.y() + 2, LABEL_INK, false);
        }
    }

    /** The popover's own panel: from under the label box down to {@code bottom} (window-relative), a little wider than its rows. */
    private void popoverPanel(GuiGraphics graphics, int bottom) {
        MachineLayout.Box box = LinkPortMenu.LABEL_BOX;
        int top = box.y() + box.height() - 1;
        Chrome.panel(graphics, leftPos + box.x() - 3, topPos + top, box.width() + 6, bottom - top + 3);
    }

    private void popoverButton(GuiGraphics graphics, MachineLayout.Box box, int fill, String key, int mouseX, int mouseY) {
        boolean hovered = over(mouseX, mouseY, box);
        Chrome.box(graphics, leftPos, topPos, box, hovered ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE, fill, mouseX, mouseY);
        Chrome.centred(graphics, font, leftPos, topPos, box, Component.translatable(key), Chrome.LABEL, true);
    }

    /** The network's labels that start with what is typed (every one while nothing is), at most six. */
    private List<String> suggestions() {
        String typed = editor == null ? "" : editor.getValue().strip().toLowerCase(Locale.ROOT);
        List<String> rows = new ArrayList<>();
        for (String label : menu.labels()) {
            if (rows.size() < SUGGESTIONS && label.toLowerCase(Locale.ROOT).startsWith(typed)) {
                rows.add(label);
            }
        }
        return rows;
    }

    private int bannerPacked() {
        return menu.missingFilters() << 16 | menu.missingUpgrades();
    }

    private static Component bannerText(int packed) {
        int filters = packed >>> 16;
        return filters > 0
                ? Component.translatable("gui.actualgenerators.link.label.banner.filters", filters)
                : Component.translatable("gui.actualgenerators.link.label.banner.upgrades", packed & 0xFFFF);
    }

    private void drawBay(GuiGraphics graphics, int mouseX, int mouseY) {
        bayTab(graphics, LinkPortMenu.EXPORT_TAB, EXPORT_GLYPH, mouseX, mouseY);
        bayTab(graphics, LinkPortMenu.IMPORT_TAB, IMPORT_GLYPH, mouseX, mouseY);
    }

    /** One tab of the bay: a small panel hanging off the window's right edge with a glyph in the middle. */
    private void bayTab(GuiGraphics graphics, MachineLayout.Box tab, String[] glyph, int mouseX, int mouseY) {
        int x = leftPos + tab.x() - 4;
        int y = topPos + tab.y();
        Chrome.panel(graphics, x, y, tab.width() + 4, tab.height());
        Chrome.glyph(graphics, glyph, leftPos + tab.x() + (tab.width() - 7) / 2, y + (tab.height() - 7) / 2, Chrome.INK_DARK);
        if (over(mouseX, mouseY, tab)) {
            graphics.fill(x + 2, y + 2, x + tab.width() + 2, y + tab.height() - 2, Chrome.HOVER);
        }
    }

    private void hint(GuiGraphics graphics, Slot slot, ItemStack ghost) {
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.3F);
        graphics.renderFakeItem(ghost, leftPos + slot.x, topPos + slot.y);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Sixteen cells: the colour, a small picture per kind carried, and this pad's part in it. */
    private void drawPalette(GuiGraphics graphics, @Nullable PortFace pad, int mouseX, int mouseY) {
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            MachineLayout.Box cell = LinkPortMenu.channelCell(channel);
            int kinds = menu.channelKinds(channel);
            int colour = kinds == 0 ? Chrome.dim(Chrome.channelColour(channel)) : Chrome.channelColour(channel);
            boolean selected = channel == menu.activeChannel();
            Chrome.box(graphics, leftPos, topPos, cell, selected ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE,
                    colour, mouseX, mouseY);

            int x = leftPos + cell.x();
            int y = topPos + cell.y();
            int ink = Chrome.inkFor(colour);
            for (TransferKind kind : TransferKind.all()) {
                if ((kinds & ChannelSettings.bit(kind)) != 0) {
                    kindGlyph(graphics, kind, true, x + MINI_X[kind.ordinal()], y + MINI_Y[kind.ordinal()], ink);
                }
            }
            if (pad != null) {
                boolean sends = false;
                boolean receives = false;
                for (TransferKind kind : TransferKind.all()) {
                    PortChannel link = pad.link(channel, kind);
                    sends |= link.extractEnabled() || link.pushEnabled();
                    receives |= link.insertEnabled();
                }
                if (sends) {
                    triangleUp(graphics, x + 14, y + 2, ink);
                }
                if (receives) {
                    triangleDown(graphics, x + 14, y + 8, ink);
                }
            }
        }
    }

    private void drawChannel(GuiGraphics graphics) {
        int channel = menu.activeChannel();
        int kinds = menu.activeKinds();
        Chrome.box(graphics, leftPos, topPos, LinkPortMenu.SWATCH, Chrome.BUTTON_EDGE,
                Chrome.channelColour(channel), -1, -1);
        // What the channel carries is already on its palette cell and on the tabs below; the band
        // says which channel this is and, since the tabs are pictures, which kind is open.
        Chrome.text(graphics, font, leftPos, topPos, LinkPortMenu.CHANNEL_NAME,
                Chrome.channelName(channel).copy().append(" · ").append(kindName(menu.activeKind())),
                kinds == 0 ? Chrome.MUTED : Chrome.TEXT);
    }

    /** One tab per kind: in the network's colour when the channel carries it, dark when not, framed when open. */
    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int channel = menu.activeChannel();
        for (TransferKind kind : TransferKind.all()) {
            MachineLayout.Box box = LinkPortMenu.tabButton(kind);
            boolean carried = menu.channelCarries(channel, kind);
            boolean open = kind == menu.activeKind();
            int fill = carried ? networkFill() : Chrome.DARK;
            Chrome.box(graphics, leftPos, topPos, box, open ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE,
                    fill, mouseX, mouseY);
            int ink = Chrome.inkFor(fill);
            kindGlyph(graphics, kind, false, leftPos + box.x() + (box.width() - 7) / 2, topPos + box.y() + 3, ink);
        }
    }

    /** A kind's picture: one colour for three of them, a red head on a brown stick for the torch. */
    private static void kindGlyph(GuiGraphics graphics, TransferKind kind, boolean mini, int x, int y, int itemInk) {
        if (kind == TransferKind.REDSTONE) {
            Chrome.glyph(graphics, mini ? MINI_STICK : STICK, x, y, Chrome.TORCH_BROWN);
        }
        Chrome.glyph(graphics, (mini ? MINI : GLYPHS)[kind.ordinal()], x, y, kindInk(kind, itemInk));
    }

    /** The open tab's row: carried, IN, EX, PU, spread, redstone. */
    private void drawSwitches(GuiGraphics graphics, int mouseX, int mouseY) {
        PortChannel link = menu.link();
        boolean carried = menu.activeCarried();
        int carryFill = carried ? networkFill() : Chrome.DARK;
        Chrome.box(graphics, leftPos, topPos, LinkPortMenu.CARRY_BUTTON, Chrome.BUTTON_EDGE, carryFill, mouseX, mouseY);
        Chrome.centred(graphics, font, leftPos, topPos, LinkPortMenu.CARRY_BUTTON,
                Component.literal(carried ? "ON" : "OFF"), Chrome.inkFor(carryFill), false);

        boolean insert = link != null && link.insertEnabled();
        boolean extract = link != null && link.extractEnabled();
        // A role on a kind the channel does not carry is set up for later, and reads that way.
        int idle = carried ? Chrome.OFF : Chrome.DARK;
        Chrome.button(graphics, font, leftPos, topPos, LinkPortMenu.INSERT_BUTTON, insert ? Chrome.ON : idle,
                Component.literal("IN"), mouseX, mouseY);
        Chrome.button(graphics, font, leftPos, topPos, LinkPortMenu.EXTRACT_BUTTON, extract ? Chrome.ON : idle,
                Component.literal("EX"), mouseX, mouseY);
        if (menu.activeKind() == TransferKind.REDSTONE) {
            // No door and no spread for a level: where PU sits is what a sender reads.
            Chrome.button(graphics, font, leftPos, topPos, LinkPortMenu.SOURCE_BUTTON, link == null ? Chrome.OFF : Chrome.STEPPER,
                    Component.literal(link == null ? SignalSource.SIGNAL.shortLabel() : link.signalSource().shortLabel()),
                    mouseX, mouseY);
        } else {
            boolean push = link != null && link.pushEnabled();
            Chrome.button(graphics, font, leftPos, topPos, LinkPortMenu.PUSH_BUTTON, push ? Chrome.ON : idle,
                    Component.literal("PU"), mouseX, mouseY);
            Chrome.button(graphics, font, leftPos, topPos, LinkPortMenu.SPREAD_BUTTON, menu.isLinked() ? Chrome.SPREAD : Chrome.OFF,
                    Component.literal(menu.channelDistribution(menu.activeKind()).shortLabel()),
                    mouseX, mouseY);
        }
        Chrome.button(graphics, font, leftPos, topPos, LinkPortMenu.REDSTONE_BUTTON,
                link == null ? Chrome.OFF : Chrome.redstoneColour(link.redstoneMode()),
                Component.literal("R"), mouseX, mouseY);
    }

    /** The network's colour once there is one, the accent before that. */
    private int networkFill() {
        PortFace pad = menu.face();
        return pad != null && pad.isLinked() ? Chrome.networkColour(pad.networkColour()) : Chrome.ACCENT;
    }

    /**
     * A number as quiet text: a muted caption, the value, and two small arrows. No box, because
     * four boxed steppers in a row shouted louder than the things that matter. While it is being
     * typed, the caption's room goes to the text too: a sum needs more than a value's width.
     */
    private void drawField(GuiGraphics graphics, int field, @Nullable TransferKind kind, int mouseX, int mouseY) {
        boolean enabled = LinkPortMenu.fieldApplies(field, kind);
        MachineLayout.Box caption = LinkPortMenu.fieldCaption(field);
        MachineLayout.Box value = LinkPortMenu.fieldValue(field);
        if (editing == field && editor != null) {
            int x0 = leftPos + caption.x();
            int x1 = leftPos + value.x() + value.width();
            int y0 = topPos + value.y();
            int y1 = y0 + value.height();
            graphics.fill(x0, y0, x1, y1, Chrome.BUTTON_EDGE);
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF000000);
        } else {
            Chrome.text(graphics, font, leftPos, topPos, caption, captionOf(field, kind), Chrome.MUTED);
            String text = enabled ? fieldText(field, kind) : "-";
            if (enabled && over(mouseX, mouseY, value)) {
                hover(graphics, value);
            }
            // A number the tier decided reads muted; one the player set reads as text.
            int valueInk = enabled && !menu.fieldIsAuto(field) ? Chrome.TEXT : Chrome.MUTED;
            graphics.drawString(font, text, leftPos + value.x() + value.width() - 1 - font.width(text),
                    topPos + value.y() + 3, valueInk, false);
        }

        MachineLayout.Box up = LinkPortMenu.fieldUp(field);
        MachineLayout.Box down = LinkPortMenu.fieldDown(field);
        int ink = enabled ? Chrome.TEXT : Chrome.MUTED;
        if (enabled) {
            if (over(mouseX, mouseY, up)) {
                hover(graphics, up);
            }
            if (over(mouseX, mouseY, down)) {
                hover(graphics, down);
            }
        }
        triangleUp(graphics, leftPos + up.x() + 1, topPos + up.y() + 1, ink);
        triangleDown(graphics, leftPos + down.x() + 1, topPos + down.y() + 2, ink);
    }

    /** Keep's spot on the redstone tab is the stock source's "full at", and says so. */
    private static Component captionOf(int field, @Nullable TransferKind kind) {
        return Component.translatable(field == LinkPortMenu.FIELD_KEEP && kind == TransferKind.REDSTONE
                ? "gui.actualgenerators.link.caption.full" : FIELD_CAPTIONS[field]);
    }

    private void hover(GuiGraphics graphics, MachineLayout.Box box) {
        graphics.fill(leftPos + box.x(), topPos + box.y(), leftPos + box.x() + box.width(),
                topPos + box.y() + box.height(), Chrome.HOVER);
    }

    private String fieldText(int field, @Nullable TransferKind kind) {
        int value = menu.fieldValueOf(field);
        if (field == LinkPortMenu.FIELD_PRIORITY) {
            return String.valueOf(value);
        }
        if (field == LinkPortMenu.FIELD_DELAY) {
            return Chrome.ticksText(value);
        }
        return Chrome.amountText(value, kind);
    }

    /** The network line is a button that reads as text, and reads red until there is a network. */
    private void drawNetwork(GuiGraphics graphics, @Nullable PortFace pad, int mouseX, int mouseY) {
        MachineLayout.Box box = LinkPortMenu.NETWORK_BUTTON;
        boolean linked = pad != null && pad.isLinked();
        boolean reaches = linked && pad.inReach();
        Chrome.box(graphics, leftPos, topPos, box, linked ? Chrome.BUTTON_EDGE : Chrome.WARN,
                !linked ? Chrome.PANEL : reaches ? Chrome.networkColour(pad.networkColour()) : Chrome.WARN, mouseX, mouseY);
        FormattedCharSequence line = Language.getInstance().getVisualOrder(
                font.substrByWidth(networkText(pad), box.width() - 6));
        graphics.drawString(font, line, leftPos + box.x() + 3, topPos + box.y() + 2,
                linked ? Chrome.LABEL : Chrome.BAD, linked);
    }

    private Component networkText(@Nullable PortFace pad) {
        if (pad == null || !pad.isLinked()) {
            return Component.translatable("gui.actualgenerators.link.unlinked");
        }
        MutableComponent text = Component.translatable("gui.actualgenerators.link.network",
                pad.networkName(), menu.networkSize(), menu.injectorCount());
        if (!pad.inReach()) {
            text.append(" · ").append(Component.translatable("gui.actualgenerators.link.out_of_reach"));
        }
        return text;
    }

    /** Text colours carry no alpha and {@code fill} draws nothing at alpha zero, so the ink is made opaque here. */
    private static void triangleUp(GuiGraphics graphics, int x, int y, int colour) {
        int ink = colour | 0xFF000000;
        graphics.fill(x + 2, y, x + 3, y + 1, ink);
        graphics.fill(x + 1, y + 1, x + 4, y + 2, ink);
        graphics.fill(x, y + 2, x + 5, y + 3, ink);
    }

    private static void triangleDown(GuiGraphics graphics, int x, int y, int colour) {
        int ink = colour | 0xFF000000;
        graphics.fill(x, y, x + 5, y + 1, ink);
        graphics.fill(x + 1, y + 1, x + 4, y + 2, ink);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, ink);
    }

    private static Component kindName(@Nullable TransferKind kind) {
        return Component.translatable(kind == null
                ? "gui.actualgenerators.link.kind.none"
                : "gui.actualgenerators.link.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    /** Every kind in a set, named and comma-separated; "Nothing" for none. */
    private static Component kindNames(int kinds) {
        if (kinds == 0) {
            return kindName(null);
        }
        MutableComponent names = Component.empty();
        boolean first = true;
        for (TransferKind kind : TransferKind.all()) {
            if ((kinds & ChannelSettings.bit(kind)) != 0) {
                if (!first) {
                    names.append(", ");
                }
                names.append(kindName(kind));
                first = false;
            }
        }
        return names;
    }

    /** The colour a kind is drawn in: the drop's blue, the bolt's red, the torch's, the given ink for items. */
    private static int kindInk(TransferKind kind, int itemInk) {
        return switch (kind) {
            case ITEM -> itemInk;
            case FLUID -> Chrome.DROP_BLUE;
            case ENERGY -> Chrome.BOLT_RED;
            case REDSTONE -> Chrome.REDSTONE_RED;
        };
    }

    // ------------------------------------------------------------------ typing a number

    private void openEditor(int field) {
        closeEditor();
        MachineLayout.Box caption = LinkPortMenu.fieldCaption(field);
        MachineLayout.Box value = LinkPortMenu.fieldValue(field);
        int width = value.x() + value.width() - caption.x() - 6;
        EditBox box = new EditBox(font, leftPos + caption.x() + 3, topPos + value.y() + 3, width, 9,
                Component.translatable(FIELD_CAPTIONS[field]));
        box.setBordered(false);
        box.setMaxLength(32);
        box.setTextColor(EDITOR_INK);
        box.setValue(String.valueOf(menu.fieldValueOf(field)));
        // Everything selected, so typing replaces the old number; the colour comes back on a change.
        box.setCursorPosition(box.getValue().length());
        box.setHighlightPos(0);
        box.setResponder(text -> box.setTextColor(EDITOR_INK));
        editor = addRenderableWidget(box);
        setFocused(box);
        editing = field;
    }

    private void closeEditor() {
        if (editor != null) {
            removeWidget(editor);
            editor = null;
        }
        editing = -1;
        chosenLabel = null;
    }

    /** The label box opens for typing, the network's labels listed under it; Enter is {@link #commitLabel}. */
    private void openLabelEditor() {
        closeEditor();
        MachineLayout.Box box = LinkPortMenu.LABEL_BOX;
        EditBox field = new EditBox(font, leftPos + box.x() + 3, topPos + box.y() + 2, box.width() - 14, 9,
                Component.translatable("gui.actualgenerators.link.label"));
        field.setBordered(false);
        field.setMaxLength(LinkNetworkManager.MAX_LABEL_LENGTH);
        field.setTextColor(LABEL_INK);
        field.setValue(menu.label());
        field.setCursorPosition(field.getValue().length());
        field.setHighlightPos(0);
        field.setResponder(text -> chosenLabel = null);
        editor = addRenderableWidget(field);
        setFocused(field);
        editing = EDIT_LABEL;
        chosenLabel = null;
    }

    /**
     * Enter in the label box. Nothing typed takes the label off; a label the network does not
     * know is this pad's from now on; one it does know asks which way the settings go, and Enter
     * on the question takes the label's, the answer that changes only this pad.
     */
    private void commitLabel() {
        if (editor == null) {
            return;
        }
        if (chosenLabel != null) {
            sendLabel(chosenLabel, false);
            return;
        }
        String typed = editor.getValue().strip();
        if (typed.isEmpty() || typed.equals(menu.label()) || !menu.labels().contains(typed)) {
            sendLabel(typed, true);
            return;
        }
        pickLabel(typed);
    }

    /** A label the network knows: the pad's own again is nothing to ask about; another one is the two-way question. */
    private void pickLabel(String label) {
        if (label.equals(menu.label())) {
            closeEditor();
            return;
        }
        if (editor != null) {
            editor.setValue(label);
        }
        chosenLabel = label;
    }

    private void sendLabel(String label, boolean push) {
        PacketDistributor.sendToServer(new SetPadLabelPayload(menu.containerId, label, push));
        closeEditor();
    }

    /** A click while the label box is open: a row of its list, one of the two answers, or none of the list's business. */
    private boolean clickLabelList(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (chosenLabel != null) {
            if (over(mouseX, mouseY, LinkPortMenu.LABEL_PULL_BUTTON)) {
                sendLabel(chosenLabel, false);
                return true;
            }
            if (over(mouseX, mouseY, LinkPortMenu.LABEL_PUSH_BUTTON)) {
                sendLabel(chosenLabel, true);
                return true;
            }
            return false;
        }
        List<String> rows = suggestions();
        for (int index = 0; index < rows.size(); index++) {
            if (over(mouseX, mouseY, LinkPortMenu.labelRow(index))) {
                pickLabel(rows.get(index));
                return true;
            }
        }
        return false;
    }

    /** Enter: what was typed, worked out, goes to the server; what does not work out turns red and stays. */
    private void commitEditor() {
        if (editor == null) {
            return;
        }
        if (editing == EDIT_LABEL) {
            commitLabel();
            return;
        }
        boolean counted = editing == LinkPortMenu.FIELD_KEEP || editing == LinkPortMenu.FIELD_AMOUNT;
        OptionalLong value = AmountExpression.parse(editor.getValue(), counted ? menu.activeKind() : null);
        if (value.isEmpty()) {
            editor.setTextColor(EDITOR_BAD);
            return;
        }
        PacketDistributor.sendToServer(new SetPadNumberPayload(menu.containerId, editing, value.getAsLong()));
        closeEditor();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editor != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitEditor();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeEditor();
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB && editing == EDIT_LABEL) {
                // Tab takes the first suggestion into the box, as an address bar would.
                List<String> rows = suggestions();
                if (!rows.isEmpty()) {
                    editor.setValue(rows.get(0));
                }
                return true;
            }
            // Every other key is the number's, the inventory key included.
            editor.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return editor != null ? editor.charTyped(character, modifiers) : super.charTyped(character, modifiers);
    }

    // ------------------------------------------------------------------ tooltips

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (editing == EDIT_LABEL) {
            return List.of();
        }
        if (over(mouseX, mouseY, LinkPortMenu.LABEL_BOX)) {
            int banner = bannerPacked();
            if (banner != 0 && banner != bannerSeen) {
                return List.of(bannerText(banner), hint("gui.actualgenerators.link.label.banner.hint"));
            }
            return List.of(menu.label().isEmpty()
                    ? Component.translatable("gui.actualgenerators.link.label")
                    : Component.literal(menu.label()), hint("gui.actualgenerators.link.label.hint"));
        }
        if (over(mouseX, mouseY, LinkPortMenu.EXPORT_TAB)) {
            return List.of(Component.translatable("gui.actualgenerators.link.export"), hint("gui.actualgenerators.link.export.hint"));
        }
        if (over(mouseX, mouseY, LinkPortMenu.IMPORT_TAB)) {
            return List.of(Component.translatable("gui.actualgenerators.link.import"), hint("gui.actualgenerators.link.import.hint"));
        }
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            if (over(mouseX, mouseY, LinkPortMenu.channelCell(channel))) {
                return channelTooltip(channel);
            }
        }
        for (TransferKind kind : TransferKind.all()) {
            if (over(mouseX, mouseY, LinkPortMenu.tabButton(kind))) {
                return List.of(carriedLine(kind), hint("gui.actualgenerators.link.tab.hint"));
            }
        }
        if (hoveredSlot instanceof LinkPortMenu.FilterSlot && hoveredSlot.isActive() && !hoveredSlot.hasItem()) {
            boolean stock = menu.activeKind() == TransferKind.REDSTONE;
            return List.of(Component.translatable(stock
                            ? "gui.actualgenerators.link.filter.slot.stock" : "gui.actualgenerators.link.filter.slot"),
                    hint(stock ? "gui.actualgenerators.link.filter.slot.stock.hint" : "gui.actualgenerators.link.filter.slot.hint"));
        }

        PortFace pad = menu.face();
        TransferKind kind = menu.activeKind();
        PortChannel link = menu.link();
        if (over(mouseX, mouseY, LinkPortMenu.CARRY_BUTTON)) {
            List<Component> lines = new ArrayList<>();
            lines.add(carriedLine(kind));
            if (!menu.isLinked()) {
                lines.add(Component.translatable("gui.actualgenerators.link.kind.local").withStyle(ChatFormatting.GRAY));
            } else if (!menu.mayChangeNetwork()) {
                lines.add(Component.translatable("gui.actualgenerators.link.no_access").withStyle(ChatFormatting.RED));
                return lines;
            }
            lines.add(hint("gui.actualgenerators.link.carry.hint"));
            return lines;
        }
        if (over(mouseX, mouseY, LinkPortMenu.INSERT_BUTTON)) {
            return List.of(Component.translatable("gui.actualgenerators.link.insert", onOff(link != null && link.insertEnabled())),
                    hint("gui.actualgenerators.link.insert.hint"));
        }
        if (over(mouseX, mouseY, LinkPortMenu.EXTRACT_BUTTON)) {
            return List.of(Component.translatable("gui.actualgenerators.link.extract", onOff(link != null && link.extractEnabled())),
                    hint("gui.actualgenerators.link.extract.hint"));
        }
        if (over(mouseX, mouseY, LinkPortMenu.PUSH_BUTTON)) {
            if (kind == TransferKind.REDSTONE) {
                SignalSource source = link == null ? SignalSource.SIGNAL : link.signalSource();
                return List.of(Component.translatable("gui.actualgenerators.link.source",
                                Component.translatable(source.translationKey())),
                        hint("gui.actualgenerators.link.source.hint"));
            }
            return List.of(Component.translatable("gui.actualgenerators.link.push", onOff(link != null && link.pushEnabled())),
                    hint("gui.actualgenerators.link.push.hint"));
        }
        if (over(mouseX, mouseY, LinkPortMenu.SPREAD_BUTTON) && kind != TransferKind.REDSTONE) {
            if (!menu.isLinked()) {
                return List.of(Component.translatable("gui.actualgenerators.link.spread.unlinked"));
            }
            return List.of(Component.translatable("gui.actualgenerators.link.distribution",
                            Component.translatable(menu.channelDistribution(kind).translationKey())),
                    menu.mayChangeNetwork()
                            ? hint("gui.actualgenerators.link.distribution.hint")
                            : Component.translatable("gui.actualgenerators.link.no_access").withStyle(ChatFormatting.RED));
        }
        if (over(mouseX, mouseY, LinkPortMenu.REDSTONE_BUTTON)) {
            String mode = link == null ? "always" : link.redstoneMode().name().toLowerCase(Locale.ROOT);
            return List.of(Component.translatable("gui.actualgenerators.redstone",
                            Component.translatable("gui.actualgenerators.redstone." + mode)),
                    hint("gui.actualgenerators.link.cycle.hint"));
        }
        for (int field = 0; field < LinkPortMenu.FIELD_COUNT; field++) {
            if (field == editing) {
                continue;
            }
            if (overAny(mouseX, mouseY, LinkPortMenu.fieldCaption(field), LinkPortMenu.fieldValue(field))) {
                return fieldTooltip(field, kind, "gui.actualgenerators.link.value.hint");
            }
            if (overAny(mouseX, mouseY, LinkPortMenu.fieldUp(field), LinkPortMenu.fieldDown(field))) {
                return fieldTooltip(field, kind, "gui.actualgenerators.link.stepper.hint");
            }
        }
        if (over(mouseX, mouseY, LinkPortMenu.NETWORK_BUTTON)) {
            List<Component> lines = new ArrayList<>();
            lines.add(networkText(pad).copy().withStyle(menu.isLinked() ? ChatFormatting.WHITE : ChatFormatting.RED));
            if (pad != null && pad.isLinked()) {
                lines.add(reachText(pad).withStyle(ChatFormatting.GRAY));
                lines.add(hint("gui.actualgenerators.link.network.hint"));
            } else {
                lines.add(hint("gui.actualgenerators.link.unlinked.hint"));
            }
            return lines;
        }
        return List.of();
    }

    /** "Items: carried" or "Items: not carried", for the chosen channel. */
    private Component carriedLine(TransferKind kind) {
        boolean carried = menu.channelCarries(menu.activeChannel(), kind);
        return Component.translatable(carried ? "gui.actualgenerators.link.carry.on" : "gui.actualgenerators.link.carry.off",
                kindName(kind));
    }

    private List<Component> fieldTooltip(int field, @Nullable TransferKind kind, String how) {
        if (!LinkPortMenu.fieldApplies(field, kind)) {
            return List.of(Component.translatable(kind == TransferKind.REDSTONE
                    ? "gui.actualgenerators.link.field.redstone" : "gui.actualgenerators.link.keep.energy"));
        }
        int value = menu.fieldValueOf(field);
        String text = fieldText(field, kind);
        Component headline = switch (field) {
            case LinkPortMenu.FIELD_PRIORITY -> Component.translatable("gui.actualgenerators.link.priority", value);
            case LinkPortMenu.FIELD_KEEP -> kind == TransferKind.REDSTONE
                    ? value > 0
                    ? Component.translatable("gui.actualgenerators.link.full", text)
                    : Component.translatable("gui.actualgenerators.link.full.off")
                    : value > 0
                    ? Component.translatable("gui.actualgenerators.link.keep", text)
                    : Component.translatable("gui.actualgenerators.link.keep.off");
            case LinkPortMenu.FIELD_DELAY -> Component.translatable(menu.fieldIsAuto(field)
                            ? "gui.actualgenerators.link.delay.auto" : "gui.actualgenerators.link.delay",
                    value, String.format(Locale.ROOT, "%.2f", value / 20.0));
            default -> Component.translatable(menu.fieldIsAuto(field)
                    ? "gui.actualgenerators.link.amount.auto" : "gui.actualgenerators.link.amount", text);
        };
        String explanation = switch (field) {
            case LinkPortMenu.FIELD_PRIORITY -> "gui.actualgenerators.link.priority.hint";
            case LinkPortMenu.FIELD_KEEP -> kind == TransferKind.REDSTONE
                    ? "gui.actualgenerators.link.full.hint" : "gui.actualgenerators.link.keep.hint";
            case LinkPortMenu.FIELD_DELAY -> "gui.actualgenerators.link.delay.hint";
            default -> "gui.actualgenerators.link.amount.hint";
        };
        return List.of(headline, hint(explanation), hint(how));
    }

    private List<Component> channelTooltip(int channel) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.actualgenerators.link.channel", Chrome.channelName(channel))
                .append(": ").append(kindNames(menu.channelKinds(channel))));
        PortFace pad = menu.face();
        if (pad != null) {
            for (TransferKind kind : TransferKind.all()) {
                PortChannel link = pad.link(channel, kind);
                if (link.participates()) {
                    boolean sends = link.extractEnabled() || link.pushEnabled();
                    String role = link.insertEnabled() && sends ? "both" : sends ? "sends" : "receives";
                    lines.add(Component.translatable("gui.actualgenerators.link.kind_role", kindName(kind),
                            Component.translatable("gui.actualgenerators.link.role." + role)).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        lines.add(hint("gui.actualgenerators.link.channel.hint"));
        return lines;
    }

    private static MutableComponent reachText(PortFace pad) {
        if (pad.isUnbound()) {
            return Component.translatable("gui.actualgenerators.link.range.unbound");
        }
        return Component.translatable(pad.reachesOtherDimensions()
                ? "gui.actualgenerators.link.range.cross"
                : "gui.actualgenerators.link.range", pad.range());
    }

    private static Component hint(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "gui.actualgenerators.auto.on" : "gui.actualgenerators.auto.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editor != null && editing == EDIT_LABEL) {
            if (clickLabelList(mouseX, mouseY, button)) {
                return true;
            }
            if (editor.isMouseOver(mouseX, mouseY)) {
                editor.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            // The popover has the window: a click anywhere else, either button, closes it and reaches nothing under it.
            closeEditor();
            return true;
        }
        if (editor != null) {
            if (editor.isMouseOver(mouseX, mouseY)) {
                return editor.mouseClicked(mouseX, mouseY, button);
            }
            // A click anywhere else is the player moving on; the typed text was not sent.
            closeEditor();
        }
        if (button != 0 && button != 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        boolean right = button == 1;
        if (!right && over(mouseX, mouseY, LinkPortMenu.LABEL_BOX)) {
            bannerSeen = bannerPacked();
            openLabelEditor();
            return true;
        }
        if (!right && over(mouseX, mouseY, LinkPortMenu.EXPORT_TAB)) {
            press(LinkPortMenu.BUTTON_EXPORT);
            return true;
        }
        if (!right && over(mouseX, mouseY, LinkPortMenu.IMPORT_TAB)) {
            if (minecraft != null) {
                String text = minecraft.keyboardHandler.getClipboard();
                // Four bytes a character at worst, and the payload's limit is in bytes.
                if (text.length() > PadSnapshot.MAX_TEXT / 4) {
                    text = text.substring(0, PadSnapshot.MAX_TEXT / 4);
                }
                PacketDistributor.sendToServer(new ImportPadConfigPayload(menu.containerId, text));
            }
            return true;
        }
        for (int channel = 0; channel < PortFace.CHANNELS; channel++) {
            if (over(mouseX, mouseY, LinkPortMenu.channelCell(channel))) {
                press(LinkPortMenu.BUTTON_CHANNEL_BASE + channel);
                return true;
            }
        }
        for (TransferKind kind : TransferKind.all()) {
            if (over(mouseX, mouseY, LinkPortMenu.tabButton(kind))) {
                press(LinkPortMenu.BUTTON_TAB_BASE + kind.ordinal());
                return true;
            }
        }
        if (over(mouseX, mouseY, LinkPortMenu.CARRY_BUTTON)) {
            press(LinkPortMenu.BUTTON_CARRY);
            return true;
        }
        if (over(mouseX, mouseY, LinkPortMenu.INSERT_BUTTON)) {
            press(LinkPortMenu.BUTTON_INSERT);
            return true;
        }
        if (over(mouseX, mouseY, LinkPortMenu.EXTRACT_BUTTON)) {
            press(LinkPortMenu.BUTTON_EXTRACT);
            return true;
        }
        if (over(mouseX, mouseY, LinkPortMenu.PUSH_BUTTON)) {
            if (menu.activeKind() == TransferKind.REDSTONE) {
                press(right ? LinkPortMenu.BUTTON_SOURCE_PREV : LinkPortMenu.BUTTON_SOURCE_NEXT);
            } else {
                press(LinkPortMenu.BUTTON_PUSH);
            }
            return true;
        }
        if (over(mouseX, mouseY, LinkPortMenu.SPREAD_BUTTON)) {
            if (menu.isLinked() && menu.activeKind() != TransferKind.REDSTONE) {
                press(LinkPortMenu.BUTTON_SPREAD);
            }
            return true;
        }
        if (over(mouseX, mouseY, LinkPortMenu.REDSTONE_BUTTON)) {
            press(right ? LinkPortMenu.BUTTON_REDSTONE_PREV : LinkPortMenu.BUTTON_REDSTONE_NEXT);
            return true;
        }
        for (int field = 0; field < LinkPortMenu.FIELD_COUNT; field++) {
            if (!LinkPortMenu.fieldApplies(field, menu.activeKind())) {
                continue;
            }
            if (over(mouseX, mouseY, LinkPortMenu.fieldUp(field))) {
                press(LinkPortMenu.nudgeButton(field, stepIndex(right), true));
                return true;
            }
            if (over(mouseX, mouseY, LinkPortMenu.fieldDown(field))) {
                press(LinkPortMenu.nudgeButton(field, stepIndex(right), false));
                return true;
            }
            if (overAny(mouseX, mouseY, LinkPortMenu.fieldValue(field), LinkPortMenu.fieldCaption(field))) {
                openEditor(field);
                return true;
            }
        }
        if (over(mouseX, mouseY, LinkPortMenu.NETWORK_BUTTON)) {
            press(right ? LinkPortMenu.BUTTON_NETWORK_LEAVE : LinkPortMenu.BUTTON_NETWORK_PICK);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Ctrl is the big step, shift (or the right button) the middle one. */
    private static int stepIndex(boolean big) {
        if (Screen.hasControlDown()) {
            return 2;
        }
        return big || Screen.hasShiftDown() ? 1 : 0;
    }

    private void press(int button) {
        if (minecraft != null) {
            Chrome.press(minecraft, menu.containerId, button);
        }
    }

    private boolean over(double mouseX, double mouseY, MachineLayout.Box box) {
        return Chrome.isOver(mouseX, mouseY, leftPos, topPos, box);
    }

    private boolean overAny(double mouseX, double mouseY, MachineLayout.Box... boxes) {
        for (MachineLayout.Box box : boxes) {
            if (over(mouseX, mouseY, box)) {
                return true;
            }
        }
        return false;
    }
}
