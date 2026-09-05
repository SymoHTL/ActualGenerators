package dev.symo.actualgenerators.client;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.jetbrains.annotations.Nullable;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.logistics.NetworkOverview;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.menu.NetworkOverviewMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * A network, read: one scrolling list of lines under its name. The channels that carry
 * something, then every pad (a dot for its reach: green in, red out, grey unloaded), then every
 * injector. Hovering a line tells the rest. A plain screen: nothing here is a slot.
 */
public class NetworkOverviewScreen extends Screen implements MenuAccess<NetworkOverviewMenu> {
    private static final int IN_REACH = 0xFF4CA86B;
    private static final int OUT_OF_REACH = 0xFFE04040;
    private static final int UNLOADED = 0xFF8A8A8A;
    private static final String SEPARATOR = " · ";

    /** One line of the list: its words, their colour, a dot before them (or none), and what hovering it says. */
    private record Line(Component text, int colour, int dot, List<Component> tooltip, @Nullable GlobalPos target) {
        Line(Component text, int colour, int dot, List<Component> tooltip) {
            this(text, colour, dot, tooltip, null);
        }

        static Line header(Component text) {
            return new Line(text, Chrome.MUTED, 0, List.of());
        }
    }

    private final NetworkOverviewMenu menu;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private int left;
    private int top;

    public NetworkOverviewScreen(NetworkOverviewMenu menu, Inventory playerInventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public NetworkOverviewMenu getMenu() {
        return menu;
    }

    @Override
    protected void init() {
        left = (width - NetworkOverviewMenu.WIDTH) / 2;
        top = (height - NetworkOverviewMenu.HEIGHT) / 2;
        lines.clear();
        NetworkOverview overview = menu.overview();

        lines.add(Line.header(Component.translatable("gui.actualgenerators.overview.channels")));
        if (overview.channels().isEmpty()) {
            lines.add(new Line(Component.translatable("gui.actualgenerators.overview.channels.none"), Chrome.MUTED, 0, List.of()));
        }
        for (NetworkOverview.Channel channel : overview.channels()) {
            lines.add(channelLine(channel));
        }

        lines.add(Line.header(Component.translatable("gui.actualgenerators.overview.pads", overview.pads().size())));
        for (NetworkOverview.Pad pad : overview.pads()) {
            lines.add(padLine(overview, pad));
        }

        lines.add(Line.header(Component.translatable("gui.actualgenerators.overview.injectors", overview.injectors().size())));
        if (overview.injectors().isEmpty()) {
            lines.add(new Line(Component.translatable("gui.actualgenerators.overview.injectors.none"), Chrome.BAD, 0, List.of()));
        }
        for (NetworkOverview.Injector injector : overview.injectors()) {
            lines.add(injectorLine(overview, injector));
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - NetworkOverviewMenu.ROWS));
    }

    // ------------------------------------------------------------------ the lines

    private static Line channelLine(NetworkOverview.Channel channel) {
        MutableComponent text = Chrome.channelName(channel.channel()).copy();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Chrome.channelName(channel.channel()));
        String joiner = SEPARATOR;
        for (TransferKind kind : TransferKind.all()) {
            if (!channel.carries(kind)) {
                continue;
            }
            text.append(joiner).append(kindName(kind));
            joiner = ", ";
            tooltip.add(kindName(kind).copy().append(": ")
                    .append(Component.translatable(channel.spread(kind).translationKey())).withStyle(ChatFormatting.GRAY));
        }
        text.append(SEPARATOR).append(Component.translatable("gui.actualgenerators.overview.channel.counts",
                channel.senders(), channel.receivers()));
        tooltip.add(Component.translatable("gui.actualgenerators.overview.channel.counts.tip",
                channel.senders(), channel.receivers()).withStyle(ChatFormatting.DARK_GRAY));
        return new Line(text, Chrome.LABEL, Chrome.channelColour(channel.channel()) | 0xFF000000, tooltip);
    }

    private static Line padLine(NetworkOverview overview, NetworkOverview.Pad pad) {
        MutableComponent text = position(overview, pad.at()).append(" ")
                .append(Component.translatable("gui.actualgenerators.facing.short." + pad.face().getName()));
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(position(overview, pad.at()).append(SEPARATOR)
                .append(Component.translatable("gui.actualgenerators.facing." + pad.face().getName())));
        if (!pad.label().isEmpty()) {
            text.append(" ").append(Component.literal(pad.label()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal(pad.label()).withStyle(ChatFormatting.AQUA));
        }
        int dot;
        int colour;
        if (!pad.loaded()) {
            dot = UNLOADED;
            colour = Chrome.MUTED;
            tooltip.add(Component.translatable("gui.actualgenerators.overview.pad.unloaded").withStyle(ChatFormatting.GRAY));
        } else if (pad.inReach()) {
            dot = IN_REACH;
            colour = Chrome.LABEL;
            tooltip.add(Component.translatable("gui.actualgenerators.overview.pad.reach", pad.range()).withStyle(ChatFormatting.GREEN));
        } else {
            dot = OUT_OF_REACH;
            colour = Chrome.LABEL;
            tooltip.add(Component.translatable("gui.actualgenerators.overview.pad.out", pad.range()).withStyle(ChatFormatting.RED));
        }
        if (pad.loaded() && pad.links().isEmpty()) {
            tooltip.add(Component.translatable("gui.actualgenerators.overview.pad.idle").withStyle(ChatFormatting.GRAY));
        }
        for (NetworkOverview.Link link : pad.links()) {
            // In the line: a kind letter and arrows in the channel's colour, so a row reads at a glance.
            String marks = (link.send() ? "▲" : "") + (link.receive() ? "▼" : "") + (link.push() ? "»" : "");
            text.append(" ").append(Component.literal(kindLetter(link.kind()) + marks)
                    .withStyle(style -> style.withColor(Chrome.channelColour(link.channel()) & 0xFFFFFF)));
            List<Component> roles = new ArrayList<>();
            if (link.send()) {
                roles.add(Component.translatable("gui.actualgenerators.overview.link.sends"));
            }
            if (link.receive()) {
                roles.add(Component.translatable("gui.actualgenerators.overview.link.receives"));
            }
            if (link.push()) {
                roles.add(Component.translatable("gui.actualgenerators.overview.link.door"));
            }
            MutableComponent line = Chrome.channelName(link.channel()).copy().append(SEPARATOR).append(kindName(link.kind()));
            String joiner = SEPARATOR;
            for (Component role : roles) {
                line.append(joiner).append(role);
                joiner = ", ";
            }
            tooltip.add(line.withStyle(style -> style.withColor(Chrome.channelColour(link.channel()) & 0xFFFFFF)));
        }
        tooltip.add(Component.translatable("gui.actualgenerators.overview.row.hint").withStyle(ChatFormatting.DARK_GRAY));
        return new Line(text, colour, dot, tooltip, pad.at());
    }

    private static Line injectorLine(NetworkOverview overview, NetworkOverview.Injector injector) {
        MutableComponent text = position(overview, injector.at()).append(SEPARATOR);
        if (!injector.loaded()) {
            text.append(Component.translatable("gui.actualgenerators.overview.pad.unloaded"));
            return new Line(text, Chrome.MUTED, UNLOADED, List.of(
                    Component.translatable("gui.actualgenerators.overview.row.hint").withStyle(ChatFormatting.DARK_GRAY)), injector.at());
        }
        text.append(Component.translatable("gui.actualgenerators.overview.injector.energy",
                compact(injector.stored()), compact(injector.capacity())));
        boolean flat = injector.stored() <= 0;
        return new Line(text, flat ? Chrome.BAD : Chrome.LABEL, flat ? OUT_OF_REACH : IN_REACH, List.of(
                Component.translatable("gui.actualgenerators.overview.injector.tip", injector.stored(), injector.capacity()),
                Component.translatable("gui.actualgenerators.overview.row.hint").withStyle(ChatFormatting.DARK_GRAY)), injector.at());
    }

    /** "x y z", and the dimension when it is not the network's home. */
    private static MutableComponent position(NetworkOverview overview, GlobalPos at) {
        MutableComponent text = Component.literal(at.pos().getX() + " " + at.pos().getY() + " " + at.pos().getZ());
        if (!at.dimension().equals(overview.home())) {
            text.append(SEPARATOR).append(at.dimension().location().getPath());
        }
        return text;
    }

    private static Component kindName(TransferKind kind) {
        return Component.translatable("gui.actualgenerators.link.kind." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String kindLetter(TransferKind kind) {
        return switch (kind) {
            case ITEM -> "I";
            case FLUID -> "F";
            case ENERGY -> "E";
            case REDSTONE -> "R";
        };
    }

    private static final int FLASH_ON = 0xFFFF4040;
    private static final int FLASH_OFF = 0xFF802020;

    private static String compact(long value) {
        return Chrome.compactCount((int) Math.min(Integer.MAX_VALUE, value));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        NetworkOverview overview = menu.overview();
        Chrome.panel(graphics, left, top, NetworkOverviewMenu.WIDTH, NetworkOverviewMenu.HEIGHT);

        MachineLayout.Box titleBox = NetworkOverviewMenu.TITLE;
        int x = left + titleBox.x();
        int y = top + titleBox.y();
        graphics.fill(x, y, x + 8, y + 8, Chrome.networkColour(overview.colour()));
        Component heading = Component.literal(overview.name()).append(SEPARATOR)
                .append(Component.translatable("gui.actualgenerators.picker.row", overview.pads().size(), overview.injectors().size()));
        graphics.drawString(font, font.split(heading, titleBox.width() - 11).get(0), x + 11, y, Chrome.TEXT, false);

        LinkNetworkManager.Access access = overview.access();
        Component owner = access.ownerName().isEmpty()
                ? Component.translatable("gui.actualgenerators.picker.owner.none")
                : Component.translatable("gui.actualgenerators.picker.owner", access.ownerName());
        Component subtitle = owner.copy().append(SEPARATOR)
                .append(Component.translatable(access.shared()
                        ? "gui.actualgenerators.picker.shared.public"
                        : "gui.actualgenerators.picker.shared.private"))
                .append(SEPARATOR)
                .append(Component.translatable("gui.actualgenerators.overview.centre",
                        Mth.floor(overview.center().x), Mth.floor(overview.center().y), Mth.floor(overview.center().z)));
        MachineLayout.Box subtitleBox = NetworkOverviewMenu.SUBTITLE;
        graphics.drawString(font, font.split(subtitle, subtitleBox.width()).get(0),
                left + subtitleBox.x(), top + subtitleBox.y(), Chrome.MUTED, false);

        for (int index = 0; index < NetworkOverviewMenu.ROWS && scroll + index < lines.size(); index++) {
            Line line = lines.get(scroll + index);
            MachineLayout.Box row = NetworkOverviewMenu.row(index);
            int rowX = left + row.x();
            int rowY = top + row.y();
            int textX = rowX + 2;
            boolean flashing = line.target() != null && LinkOverlayRenderer.isHighlighted(line.target());
            int red = LinkOverlayRenderer.flashOn() ? FLASH_ON : FLASH_OFF;
            if (line.target() != null && Chrome.isOver(mouseX, mouseY, left, top, row)) {
                graphics.fill(rowX, rowY, rowX + row.width(), rowY + row.height(), Chrome.HOVER);
            }
            if (flashing) {
                // The row blinks in step with the block it lit in the world, until the flash is over.
                graphics.renderOutline(rowX, rowY, row.width(), row.height(), red);
            }
            if (line.dot() != 0) {
                graphics.fill(rowX + 2, rowY + 3, rowX + 8, rowY + 9, flashing ? red : line.dot());
                textX = rowX + 11;
            }
            graphics.drawString(font, font.split(line.text(), row.width() - (textX - rowX) - 2).get(0),
                    textX, rowY + 2, line.colour(), line.colour() == Chrome.LABEL);
        }
        if (lines.size() > NetworkOverviewMenu.ROWS) {
            MachineLayout.Box bar = NetworkOverviewMenu.SCROLLBAR;
            int barX = left + bar.x();
            int barY = top + bar.y();
            graphics.fill(barX, barY, barX + bar.width(), barY + bar.height(), Chrome.DARK);
            int thumb = Math.max(8, bar.height() * NetworkOverviewMenu.ROWS / lines.size());
            int travel = bar.height() - thumb;
            int at = barY + travel * scroll / (lines.size() - NetworkOverviewMenu.ROWS);
            graphics.fill(barX, at, barX + bar.width(), at + thumb, Chrome.ACCENT);
        }
        Chrome.button(graphics, font, left, top, NetworkOverviewMenu.BACK_BUTTON, Chrome.DARK,
                Component.translatable("gui.actualgenerators.overview.back"), mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int index = 0; index < NetworkOverviewMenu.ROWS && scroll + index < lines.size(); index++) {
            Line line = lines.get(scroll + index);
            if (!line.tooltip().isEmpty() && Chrome.isOver(mouseX, mouseY, left, top, NetworkOverviewMenu.row(index))) {
                List<Component> tooltip = line.tooltip();
                if (line.target() != null && LinkOverlayRenderer.isHighlighted(line.target())) {
                    tooltip = new ArrayList<>(tooltip);
                    tooltip.add(Component.translatable("gui.actualgenerators.overview.row.flashing").withStyle(ChatFormatting.RED));
                }
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && Chrome.isOver(mouseX, mouseY, left, top, NetworkOverviewMenu.BACK_BUTTON)) {
            Chrome.press(minecraft, menu.containerId, NetworkOverviewMenu.BUTTON_BACK);
            return true;
        }
        // A pad or injector row: the block flashes red in the world for a while, through walls, so it can be found.
        if (button == 0) {
            for (int index = 0; index < NetworkOverviewMenu.ROWS && scroll + index < lines.size(); index++) {
                Line line = lines.get(scroll + index);
                if (line.target() != null && Chrome.isOver(mouseX, mouseY, left, top, NetworkOverviewMenu.row(index))) {
                    LinkOverlayRenderer.highlight(line.target());
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, lines.size() - NetworkOverviewMenu.ROWS);
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, most);
        return true;
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
