package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.menu.NetworkPickerMenu;
import dev.symo.actualgenerators.network.CreateNetworkPayload;
import dev.symo.actualgenerators.network.InviteMemberPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The linking tool's window: every network the player may use, a name box for a new one, and
 * for the network the block is on: sixteen colours, whose it is, whether it is public, who is
 * invited and a box to invite one more. And a way off.
 *
 * <p>A plain screen rather than a container screen: there are no slots, and the inventory key
 * must not close a window somebody is typing a name into.
 */
public class NetworkPickerScreen extends Screen implements MenuAccess<NetworkPickerMenu> {
    /** The crown on an invited player's line: make them the owner. */
    private static final String[] CROWN = {
            "#.#.#",
            "##.##",
            "#####",
            "#####"};

    /** The eye at the end of a network's row: look, without joining. */
    private static final String[] EYE = {
            ".###.",
            "#.#.#",
            ".###."};

    /** The cross before it: forget the network, once it is empty and if it is yours. */
    private static final String[] CROSS = {
            "#...#",
            ".#.#.",
            "..#..",
            ".#.#.",
            "#...#"};

    private final NetworkPickerMenu menu;
    private EditBox nameField;
    /** Only the owner gets one. */
    private @Nullable EditBox inviteField;
    private int scroll;
    private int memberScroll;
    private int left;
    private int top;
    /** The colour last clicked, shown at once; the list itself is a snapshot from when the window opened. */
    private int pickedColour = -1;
    /** The last snapshot the widgets were made for. */
    private int seenVersion;

    public NetworkPickerScreen(NetworkPickerMenu menu, Inventory playerInventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public NetworkPickerMenu getMenu() {
        return menu;
    }

    @Override
    protected void init() {
        left = (width - NetworkPickerMenu.WIDTH) / 2;
        top = (height - NetworkPickerMenu.HEIGHT) / 2;
        nameField = field(NetworkPickerMenu.NAME_FIELD, LinkNetworkManager.MAX_NAME_LENGTH,
                Component.translatable("gui.actualgenerators.picker.name"));
        nameField.setValue(menu.suggestedName());
        addRenderableWidget(nameField);
        setInitialFocus(nameField);
        inviteField = null;
        seenVersion = menu.version();
        addInviteFieldIfOwner();
    }

    private void addInviteFieldIfOwner() {
        if (inviteField == null && menu.current() != null && menu.owns()) {
            inviteField = field(NetworkPickerMenu.INVITE_FIELD, InviteMemberPayload.MAX_NAME_LENGTH,
                    Component.translatable("gui.actualgenerators.picker.invite.name"));
            addRenderableWidget(inviteField);
        }
    }

    private EditBox field(MachineLayout.Box box, int maxLength, Component label) {
        EditBox field = new EditBox(font, left + box.x() + 3, top + box.y() + 3, box.width() - 6, box.height() - 6, label);
        field.setBordered(false);
        field.setMaxLength(maxLength);
        return field;
    }

    /** A fresh snapshot: the invite box empties, the list starts at the top, a new owner gets the box. */
    private void catchUp() {
        if (menu.version() != seenVersion) {
            seenVersion = menu.version();
            memberScroll = 0;
            if (inviteField != null) {
                inviteField.setValue("");
            }
            addInviteFieldIfOwner();
        }
    }

    // ------------------------------------------------------------------ drawing

    /** The panel goes under the widgets, so it is part of the background. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        catchUp();
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Chrome.panel(graphics, left, top, NetworkPickerMenu.WIDTH, NetworkPickerMenu.HEIGHT);
        Chrome.text(graphics, font, left, top, NetworkPickerMenu.TITLE, title, Chrome.TEXT);
        Chrome.box(graphics, left, top, NetworkPickerMenu.NAME_FIELD, Chrome.BUTTON_EDGE, 0xFF000000, -1, -1);
        Chrome.button(graphics, font, left, top, NetworkPickerMenu.CREATE_BUTTON, Chrome.ON,
                Component.translatable("gui.actualgenerators.picker.create"), mouseX, mouseY);
        drawNetworks(graphics, mouseX, mouseY);
        if (menu.current() != null) {
            drawCurrent(graphics, mouseX, mouseY);
        }
        Chrome.button(graphics, font, left, top, NetworkPickerMenu.LEAVE_BUTTON,
                menu.current() == null ? Chrome.OFF : Chrome.WARN,
                Component.translatable("gui.actualgenerators.picker.leave"), mouseX, mouseY);
    }

    /** The tooltip goes over the widgets, so it comes last. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> tooltip = tooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void drawNetworks(GuiGraphics graphics, int mouseX, int mouseY) {
        List<LinkNetworkManager.Summary> choices = menu.choices();
        if (choices.isEmpty()) {
            Chrome.text(graphics, font, left, top, NetworkPickerMenu.row(0).grow(-2),
                    Component.translatable("gui.actualgenerators.picker.empty"), Chrome.MUTED);
        }
        for (int index = 0; index < NetworkPickerMenu.ROWS && scroll + index < choices.size(); index++) {
            LinkNetworkManager.Summary summary = choices.get(scroll + index);
            MachineLayout.Box row = NetworkPickerMenu.row(index);
            boolean current = summary.id().equals(menu.current());
            Chrome.box(graphics, left, top, row, current ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE,
                    current ? Chrome.ACCENT : Chrome.DARK, mouseX, mouseY);
            int x = left + row.x();
            int y = top + row.y();
            graphics.fill(x + 3, y + 3, x + 11, y + 11, Chrome.networkColour(colourOf(summary)));
            graphics.drawString(font, summary.name(), x + 15, y + 3, Chrome.LABEL, true);
            Component count = Component.translatable("gui.actualgenerators.picker.row", summary.pads(), summary.injectors());
            graphics.drawString(font, count, x + row.width() - 4 - font.width(count), y + 3, 0xFFCCCCCC, true);
            MachineLayout.Box bin = NetworkPickerMenu.rowDelete(index);
            boolean deletable = deletable(summary);
            Chrome.box(graphics, left, top, bin, Chrome.BUTTON_EDGE, deletable ? Chrome.WARN : Chrome.DARK,
                    deletable ? mouseX : -1, deletable ? mouseY : -1);
            Chrome.glyph(graphics, CROSS, left + bin.x() + 4, top + bin.y() + 4, deletable ? 0xFFFFFFFF : 0xFF808080);
            MachineLayout.Box eye = NetworkPickerMenu.rowView(index);
            Chrome.box(graphics, left, top, eye, Chrome.BUTTON_EDGE, Chrome.DARK, mouseX, mouseY);
            Chrome.glyph(graphics, EYE, left + eye.x() + 4, top + eye.y() + 5, 0xFFFFFFFF);
        }
    }

    /** The network the block is on: its colours, whose it is, whether it is public, who is invited. */
    private void drawCurrent(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean owns = menu.owns();
        int hoverX = owns ? mouseX : -1;
        int hoverY = owns ? mouseY : -1;
        int shown = currentColour();
        for (int dye = 0; dye < NetworkPickerMenu.COLOURS; dye++) {
            int rgb = NetworkPickerMenu.dyeColour(dye);
            Chrome.box(graphics, left, top, NetworkPickerMenu.colourCell(dye),
                    rgb == shown ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE, Chrome.networkColour(rgb), hoverX, hoverY);
        }
        LinkNetworkManager.Access access = currentAccess();
        MachineLayout.Box ownerLine = NetworkPickerMenu.OWNER_LINE;
        if (access.ownerName().isEmpty()) {
            Chrome.button(graphics, font, left, top, ownerLine, Chrome.ACCENT,
                    Component.translatable("gui.actualgenerators.picker.claim"), mouseX, mouseY);
        } else {
            // A name can be sixteen characters; the line is not, so it stops where the button starts.
            String owner = font.plainSubstrByWidth(ownerLine(access).getString(), ownerLine.width() - 4);
            graphics.drawString(font, owner, left + ownerLine.x() + 2, top + ownerLine.y() + 2, Chrome.MUTED, false);
        }
        Chrome.button(graphics, font, left, top, NetworkPickerMenu.SHARED_BUTTON, access.shared() ? Chrome.ON : Chrome.OFF,
                Component.translatable(access.shared()
                        ? "gui.actualgenerators.picker.shared.public"
                        : "gui.actualgenerators.picker.shared.private"), hoverX, hoverY);
        if (owns) {
            Chrome.box(graphics, left, top, NetworkPickerMenu.INVITE_FIELD, Chrome.BUTTON_EDGE, 0xFF000000, -1, -1);
            Chrome.button(graphics, font, left, top, NetworkPickerMenu.INVITE_BUTTON, Chrome.ON,
                    Component.translatable("gui.actualgenerators.picker.invite"), mouseX, mouseY);
        }
        List<LinkNetworkManager.Member> members = menu.members();
        if (members.isEmpty()) {
            Chrome.text(graphics, font, left, top, NetworkPickerMenu.memberRow(0).grow(-2),
                    Component.translatable("gui.actualgenerators.picker.members.none"), Chrome.MUTED);
        }
        for (int index = 0; index < NetworkPickerMenu.MEMBER_ROWS && memberScroll + index < members.size(); index++) {
            MachineLayout.Box row = NetworkPickerMenu.memberRow(index);
            Chrome.box(graphics, left, top, row, Chrome.BUTTON_EDGE, Chrome.DARK, -1, -1);
            graphics.drawString(font, members.get(memberScroll + index).name(),
                    left + row.x() + 4, top + row.y() + 2, Chrome.LABEL, true);
            if (owns) {
                MachineLayout.Box crown = NetworkPickerMenu.memberTransfer(index);
                Chrome.box(graphics, left, top, crown, Chrome.BUTTON_EDGE, Chrome.ACCENT, mouseX, mouseY);
                Chrome.glyph(graphics, CROWN, left + crown.x() + 4, top + crown.y() + 4,
                        Chrome.inkFor(Chrome.ACCENT) | 0xFF000000);
                Chrome.button(graphics, font, left, top, NetworkPickerMenu.memberRemove(index), Chrome.WARN,
                        Component.literal("x"), mouseX, mouseY);
            }
        }
    }

    private static Component ownerLine(LinkNetworkManager.Access access) {
        return access.ownerName().isEmpty()
                ? Component.translatable("gui.actualgenerators.picker.owner.none")
                : Component.translatable("gui.actualgenerators.picker.owner", access.ownerName());
    }

    private static Component sharedLine(LinkNetworkManager.Access access) {
        return Component.translatable(access.shared()
                ? "gui.actualgenerators.picker.shared.public.tip"
                : "gui.actualgenerators.picker.shared.private.tip");
    }

    private static Component hint(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** A network the player runs, with nothing on it: the only kind the cross forgets. */
    private static boolean deletable(LinkNetworkManager.Summary summary) {
        return summary.access().mine() && summary.pads() == 0 && summary.injectors() == 0;
    }

    private List<Component> tooltip(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        List<LinkNetworkManager.Summary> choices = menu.choices();
        for (int index = 0; index < NetworkPickerMenu.ROWS && scroll + index < choices.size(); index++) {
            if (over(mouseX, mouseY, NetworkPickerMenu.rowDelete(index))) {
                LinkNetworkManager.Summary summary = choices.get(scroll + index);
                if (deletable(summary)) {
                    lines.add(Component.translatable("gui.actualgenerators.picker.delete"));
                    lines.add(hint("gui.actualgenerators.picker.delete.hint"));
                } else if (!summary.access().mine()) {
                    lines.add(Component.translatable("gui.actualgenerators.picker.delete.owner"));
                } else {
                    lines.add(Component.translatable("gui.actualgenerators.picker.delete.full", summary.pads(), summary.injectors()));
                }
                return lines;
            }
            if (over(mouseX, mouseY, NetworkPickerMenu.rowView(index))) {
                lines.add(Component.translatable("gui.actualgenerators.picker.view"));
                lines.add(hint("gui.actualgenerators.picker.view.hint"));
                return lines;
            }
            if (over(mouseX, mouseY, NetworkPickerMenu.row(index))) {
                LinkNetworkManager.Access access = choices.get(scroll + index).access();
                lines.add(ownerLine(access));
                lines.add(sharedLine(access));
                lines.add(hint("gui.actualgenerators.picker.row.hint"));
                return lines;
            }
        }
        if (menu.current() == null) {
            return lines;
        }
        boolean owns = menu.owns();
        LinkNetworkManager.Access access = currentAccess();
        for (int dye = 0; dye < NetworkPickerMenu.COLOURS; dye++) {
            if (over(mouseX, mouseY, NetworkPickerMenu.colourCell(dye))) {
                lines.add(Component.translatable("gui.actualgenerators.picker.colour",
                        Component.translatable("color.minecraft." + DyeColor.byId(dye).getName())));
                lines.add(hint(owns ? "gui.actualgenerators.picker.colour.hint" : "gui.actualgenerators.picker.owner.only"));
                return lines;
            }
        }
        if (over(mouseX, mouseY, NetworkPickerMenu.OWNER_LINE)) {
            lines.add(access.ownerName().isEmpty()
                    ? Component.translatable("gui.actualgenerators.picker.claim.tip")
                    : ownerLine(access));
            if (access.ownerName().isEmpty()) {
                lines.add(hint("gui.actualgenerators.picker.claim.hint"));
            }
            return lines;
        }
        if (over(mouseX, mouseY, NetworkPickerMenu.SHARED_BUTTON)) {
            lines.add(sharedLine(access));
            lines.add(hint(owns ? "gui.actualgenerators.picker.shared.hint" : "gui.actualgenerators.picker.owner.only"));
            return lines;
        }
        if (owns && over(mouseX, mouseY, NetworkPickerMenu.INVITE_BUTTON)) {
            lines.add(Component.translatable("gui.actualgenerators.picker.invite.tip"));
            lines.add(hint("gui.actualgenerators.picker.invite.hint"));
            return lines;
        }
        List<LinkNetworkManager.Member> members = menu.members();
        for (int index = 0; owns && index < NetworkPickerMenu.MEMBER_ROWS && memberScroll + index < members.size(); index++) {
            String name = members.get(memberScroll + index).name();
            if (over(mouseX, mouseY, NetworkPickerMenu.memberRemove(index))) {
                lines.add(Component.translatable("gui.actualgenerators.picker.member.remove", name));
                lines.add(hint("gui.actualgenerators.picker.member.remove.hint"));
                return lines;
            }
            if (over(mouseX, mouseY, NetworkPickerMenu.memberTransfer(index))) {
                lines.add(Component.translatable("gui.actualgenerators.picker.member.transfer", name));
                lines.add(hint("gui.actualgenerators.picker.member.transfer.hint"));
                return lines;
            }
        }
        return lines;
    }

    /** The colour a row shows: the one just picked for the current network, else the snapshot's. */
    private int colourOf(LinkNetworkManager.Summary summary) {
        return summary.id().equals(menu.current()) ? currentColour() : summary.colour();
    }

    private int currentColour() {
        if (pickedColour >= 0) {
            return pickedColour;
        }
        LinkNetworkManager.Summary current = currentSummary();
        return current == null ? 0 : current.colour();
    }

    private LinkNetworkManager.Access currentAccess() {
        LinkNetworkManager.Summary current = currentSummary();
        return current == null ? new LinkNetworkManager.Access("", false, false) : current.access();
    }

    private @Nullable LinkNetworkManager.Summary currentSummary() {
        for (LinkNetworkManager.Summary summary : menu.choices()) {
            if (summary.id().equals(menu.current())) {
                return summary;
            }
        }
        return null;
    }

    private boolean over(double mouseX, double mouseY, MachineLayout.Box box) {
        return Chrome.isOver(mouseX, mouseY, left, top, box);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (over(mouseX, mouseY, NetworkPickerMenu.CREATE_BUTTON)) {
                create();
                return true;
            }
            if (over(mouseX, mouseY, NetworkPickerMenu.LEAVE_BUTTON) && menu.current() != null) {
                press(NetworkPickerMenu.BUTTON_LEAVE);
                return true;
            }
            for (int index = 0; index < NetworkPickerMenu.ROWS && scroll + index < menu.choices().size(); index++) {
                if (over(mouseX, mouseY, NetworkPickerMenu.rowDelete(index))) {
                    if (deletable(menu.choices().get(scroll + index))) {
                        press(NetworkPickerMenu.BUTTON_DELETE_BASE + scroll + index);
                    }
                    return true;
                }
                if (over(mouseX, mouseY, NetworkPickerMenu.rowView(index))) {
                    press(NetworkPickerMenu.BUTTON_VIEW_BASE + scroll + index);
                    return true;
                }
                if (over(mouseX, mouseY, NetworkPickerMenu.row(index))) {
                    press(NetworkPickerMenu.BUTTON_PICK_BASE + scroll + index);
                    return true;
                }
            }
            if (menu.current() != null) {
                if (currentAccess().ownerName().isEmpty() && over(mouseX, mouseY, NetworkPickerMenu.OWNER_LINE)) {
                    press(NetworkPickerMenu.BUTTON_CLAIM);
                    return true;
                }
                if (menu.owns() && clickedOwnerControl(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickedOwnerControl(double mouseX, double mouseY) {
        for (int dye = 0; dye < NetworkPickerMenu.COLOURS; dye++) {
            if (over(mouseX, mouseY, NetworkPickerMenu.colourCell(dye))) {
                pickedColour = NetworkPickerMenu.dyeColour(dye);
                press(NetworkPickerMenu.BUTTON_COLOUR_BASE + dye);
                return true;
            }
        }
        if (over(mouseX, mouseY, NetworkPickerMenu.SHARED_BUTTON)) {
            press(NetworkPickerMenu.BUTTON_SHARED);
            return true;
        }
        if (over(mouseX, mouseY, NetworkPickerMenu.INVITE_BUTTON)) {
            invite();
            return true;
        }
        List<LinkNetworkManager.Member> members = menu.members();
        for (int index = 0; index < NetworkPickerMenu.MEMBER_ROWS && memberScroll + index < members.size(); index++) {
            if (over(mouseX, mouseY, NetworkPickerMenu.memberRemove(index))) {
                press(NetworkPickerMenu.BUTTON_MEMBER_BASE + memberScroll + index);
                return true;
            }
            if (over(mouseX, mouseY, NetworkPickerMenu.memberTransfer(index))) {
                press(NetworkPickerMenu.BUTTON_TRANSFER_BASE + memberScroll + index);
                return true;
            }
        }
        return false;
    }

    /** The wheel scrolls whichever list is under it: the invited over their rows, the networks elsewhere. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MachineLayout.Box first = NetworkPickerMenu.memberRow(0);
        MachineLayout.Box last = NetworkPickerMenu.memberRow(NetworkPickerMenu.MEMBER_ROWS - 1);
        int step = (int) Math.signum(scrollY);
        if (menu.current() != null && mouseY >= top + first.y() && mouseY < top + last.y() + last.height()) {
            int most = Math.max(0, menu.members().size() - NetworkPickerMenu.MEMBER_ROWS);
            memberScroll = Mth.clamp(memberScroll - step, 0, most);
        } else {
            int most = Math.max(0, menu.choices().size() - NetworkPickerMenu.ROWS);
            scroll = Mth.clamp(scroll - step, 0, most);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (inviteField != null && inviteField.isFocused()) {
                invite();
            } else {
                create();
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void create() {
        PacketDistributor.sendToServer(new CreateNetworkPayload(menu.containerId, nameField.getValue()));
    }

    private void invite() {
        if (inviteField != null && !inviteField.getValue().isBlank()) {
            PacketDistributor.sendToServer(new InviteMemberPayload(menu.containerId, inviteField.getValue().strip()));
        }
    }

    private void press(int button) {
        if (minecraft != null) {
            Chrome.press(minecraft, menu.containerId, button);
        }
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
