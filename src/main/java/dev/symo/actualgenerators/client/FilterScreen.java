package dev.symo.actualgenerators.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.menu.FilterMenu;
import dev.symo.actualgenerators.menu.MachineLayout;
import dev.symo.actualgenerators.network.SetFilterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A Filter item's window, in three pages over one menu.
 *
 * <p>The <b>list</b> page: one Add button, then a row per entry (picture, name, its marks, a
 * remove button). Add asks item or tag and opens that entry's page for a new entry; a click on a
 * row opens the page for that one. The <b>item</b> page: a slot to click the item into (right for
 * the fluid inside it), its data as text to edit with Apply under it and a Match/Ignore switch, and
 * IN, EX and whitelist-or-blacklist. The <b>tag</b> page: the same slot and switches, a box to type
 * a tag by name, and the pictured thing's tags to pick from instead. Back or Escape is the list.
 *
 * <p>Every box drawn is one of {@link FilterMenu}'s, so the layout test sees them all. The two text
 * editors are vanilla widgets over those boxes; while one has focus every key is its own, the
 * inventory key included.
 */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    /** Which page is up. */
    public enum Page { LIST, ITEM, TAG }

    private static final int EDITOR_INK = 0xFFE0E0E0;
    private static final int EDITOR_BAD = 0xFFFF5050;
    private static final int DATA_TEXT_LIMIT = 1 << 14;

    private Page page = Page.LIST;
    /** Whether Add has opened into its two choices. */
    private boolean adding;
    /** The entry a page edits; -1 for a new one nothing has been written into yet. */
    private int index = -1;
    /** The first entry shown on the list; the wheel moves it. */
    private int first;
    private int tagScroll;
    /** The item page's data as text; the tag page's box for a tag typed by name. Each lives while its page is up. */
    private @Nullable MultiLineEditBox dataEditor;
    private @Nullable EditBox tagEditor;
    /** What the data editor was last loaded with, so a change from the server shows and typing is not overwritten. */
    private String dataShown = "";
    /** What became of the last Apply. */
    private Component dataStatus = Component.empty();
    private int dataStatusColour = Chrome.MUTED;

    public FilterScreen(FilterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = FilterMenu.WIDTH;
        this.imageHeight = FilterMenu.HEIGHT;
        this.inventoryLabelY = FilterMenu.HEIGHT - 94;
    }

    @Override
    protected void init() {
        super.init();
        refreshEditors();
    }

    /** The editors belong to their pages: the item page's data box, the tag page's name box. */
    private void refreshEditors() {
        if (dataEditor != null) {
            removeWidget(dataEditor);
            dataEditor = null;
        }
        if (tagEditor != null) {
            removeWidget(tagEditor);
            tagEditor = null;
        }
        dataStatus = Component.empty();
        FilterContents.Entry entry = menu.entry(index);
        if (page == Page.ITEM) {
            MachineLayout.Box box = FilterMenu.ENTRY_DATA;
            MultiLineEditBox editor = new MultiLineEditBox(font, leftPos + box.x(), topPos + box.y(), box.width(), box.height(),
                    Component.translatable("gui.actualgenerators.filter.entry.data.placeholder"),
                    Component.translatable("gui.actualgenerators.filter.entry.data"));
            editor.setCharacterLimit(DATA_TEXT_LIMIT);
            dataShown = dataText(entry);
            editor.setValue(dataShown);
            dataEditor = addRenderableWidget(editor);
        } else if (page == Page.TAG) {
            MachineLayout.Box box = FilterMenu.ENTRY_TAG_BOX;
            EditBox editor = new EditBox(font, leftPos + box.x() + 3, topPos + box.y() + 2, box.width() - 6, 9,
                    Component.translatable("gui.actualgenerators.filter.entry.tag.box"));
            editor.setBordered(false);
            editor.setMaxLength(128);
            editor.setTextColor(EDITOR_INK);
            editor.setHint(Component.translatable("gui.actualgenerators.filter.entry.tag.box").withStyle(ChatFormatting.DARK_GRAY));
            editor.setValue(tagText(entry));
            editor.setResponder(text -> editor.setTextColor(EDITOR_INK));
            tagEditor = addRenderableWidget(editor);
        }
    }

    /** The server's word on the entry reaches the editors when nobody is typing in them. */
    @Override
    protected void containerTick() {
        super.containerTick();
        FilterContents.Entry entry = menu.entry(index);
        if (dataEditor != null && !dataEditor.isFocused()) {
            String text = dataText(entry);
            if (!text.equals(dataShown)) {
                dataShown = text;
                dataEditor.setValue(text);
            }
        }
        if (tagEditor != null && !tagEditor.isFocused()) {
            String text = tagText(entry);
            if (!text.equals(tagEditor.getValue())) {
                tagEditor.setValue(text);
            }
        }
    }

    // ------------------------------------------------------------------ for whoever drops things in

    public Page page() {
        return page;
    }

    /** The index of the entry on the top row of the list. */
    public int firstRow() {
        return first;
    }

    /** The entry an open page edits, or -1 for a new one. */
    public int editingIndex() {
        return index;
    }

    /**
     * Writes a picture into the entry a page edits, or adds one for a new entry, keeping the
     * switches the entry had. On the tag page a tag already chosen stays, the picture being only a
     * picture there; a fresh tag entry takes the picture's first tag. For a new entry, the page
     * moves on to the index the entry lands at, which is the end of the list.
     */
    public void acceptPicture(int at, FilterContents.Entry picture) {
        if (picture.isEmpty()) {
            return;
        }
        FilterContents.Entry existing = menu.entry(at);
        FilterContents.Entry entry = existing.isEmpty() ? picture : existing.repictured(picture);
        if (page == Page.TAG) {
            FilterContents.Entry pictured = entry;
            entry = existing.tag().map(pictured::withTag).orElseGet(() -> withFirstTag(pictured));
        }
        send(at, entry);
        if (at < 0 || existing.isEmpty()) {
            index = Math.min(menu.contents().size(), FilterContents.MAX_ENTRIES);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (page == Page.ITEM && !dataStatus.getString().isEmpty() && over(mouseX, mouseY, FilterMenu.ENTRY_DATA_STATUS)) {
            graphics.renderTooltip(font, font.split(dataStatus, 220), mouseX, mouseY);
            return;
        }
        List<Component> lines = page == Page.LIST ? listTooltip(mouseX, mouseY) : pageTooltip(mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component heading = switch (page) {
            case LIST -> title;
            case ITEM -> Component.translatable("gui.actualgenerators.filter.entry.item");
            case TAG -> Component.translatable("gui.actualgenerators.filter.entry.tag");
        };
        graphics.drawString(font, heading, FilterMenu.TITLE.x(), FilterMenu.TITLE.y(), Chrome.TEXT, false);
        if (page == Page.LIST) {
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Chrome.TEXT, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Chrome.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Chrome.slotFrame(graphics, leftPos + slot.x, topPos + slot.y);
        }
        switch (page) {
            case LIST -> drawList(graphics, mouseX, mouseY);
            case ITEM, TAG -> drawEntryPage(graphics, mouseX, mouseY);
        }
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (adding) {
            Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ADD_ITEM_BUTTON, Chrome.ACCENT,
                    Component.translatable("gui.actualgenerators.filter.add.item"), mouseX, mouseY);
            Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ADD_TAG_BUTTON, Chrome.ACCENT,
                    Component.translatable("gui.actualgenerators.filter.add.tag"), mouseX, mouseY);
        } else {
            Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ADD_BUTTON, Chrome.DARK,
                    Component.translatable("gui.actualgenerators.filter.add"), mouseX, mouseY);
        }

        FilterContents contents = menu.contents();
        first = Math.clamp(first, 0, Math.max(0, contents.size() - FilterMenu.ROWS));
        if (contents.size() == 0) {
            Chrome.text(graphics, font, leftPos, topPos, FilterMenu.rowLabel(0),
                    Component.translatable("gui.actualgenerators.filter.empty"), Chrome.MUTED);
        }
        for (int row = 0; row < FilterMenu.ROWS; row++) {
            FilterContents.Entry entry = contents.entry(first + row);
            if (entry.isEmpty()) {
                break;
            }
            MachineLayout.Box icon = FilterMenu.rowIcon(row);
            Chrome.drawEntry(graphics, entry, leftPos + icon.x() + 1, topPos + icon.y() + 1);
            MachineLayout.Box label = FilterMenu.rowLabel(row);
            graphics.drawString(font, font.split(Chrome.entryLabel(entry), label.width()).get(0),
                    leftPos + label.x(), topPos + label.y(), Chrome.TEXT, false);
            MachineLayout.Box marks = FilterMenu.rowMarks(row);
            int x = leftPos + marks.x();
            int y = topPos + marks.y();
            graphics.drawString(font, Component.translatable("gui.actualgenerators.filter.receive.short"), x, y,
                    entry.receive() ? Chrome.ON : Chrome.MUTED, false);
            graphics.drawString(font, Component.translatable("gui.actualgenerators.filter.send.short"), x + 16, y,
                    entry.send() ? Chrome.ON : Chrome.MUTED, false);
            graphics.drawString(font, Component.translatable(entry.blacklist()
                            ? "gui.actualgenerators.filter.mode.block.short" : "gui.actualgenerators.filter.mode.allow.short"),
                    x + 34, y, entry.blacklist() ? Chrome.WARN : Chrome.ON, false);
            Chrome.button(graphics, font, leftPos, topPos, FilterMenu.rowRemove(row), Chrome.DARK,
                    Component.literal("×"), mouseX, mouseY);
        }
        if (contents.size() > FilterMenu.ROWS) {
            scrollbar(graphics, FilterMenu.SCROLLBAR, first, contents.size(), FilterMenu.ROWS);
        }
    }

    private void drawEntryPage(GuiGraphics graphics, int mouseX, int mouseY) {
        FilterContents.Entry entry = menu.entry(index);
        Chrome.slotFrame(graphics, leftPos + FilterMenu.ENTRY_SLOT.x() + 1, topPos + FilterMenu.ENTRY_SLOT.y() + 1);
        if (entry.isEmpty()) {
            Chrome.text(graphics, font, leftPos, topPos, FilterMenu.ENTRY_NAME,
                    Component.translatable("gui.actualgenerators.filter.entry.slot.empty"), Chrome.MUTED);
        } else {
            Chrome.drawEntry(graphics, entry, leftPos + FilterMenu.ENTRY_SLOT.x() + 1, topPos + FilterMenu.ENTRY_SLOT.y() + 1);
            Chrome.text(graphics, font, leftPos, topPos, FilterMenu.ENTRY_NAME, Chrome.entryLabel(entry), Chrome.TEXT);
        }

        boolean set = !entry.isEmpty();
        if (page == Page.ITEM) {
            boolean match = entry.matchComponents();
            Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ENTRY_MATCH_BUTTON,
                    !set || entry.isGroup() ? Chrome.DARK : match ? Chrome.ACCENT : Chrome.OFF,
                    Component.translatable(match ? "gui.actualgenerators.filter.entry.match" : "gui.actualgenerators.filter.entry.ignore"),
                    mouseX, mouseY);
        }
        Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ENTRY_RECEIVE_BUTTON,
                !set ? Chrome.DARK : entry.receive() ? Chrome.ON : Chrome.OFF,
                Component.translatable("gui.actualgenerators.filter.receive.short"), mouseX, mouseY);
        Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ENTRY_SEND_BUTTON,
                !set ? Chrome.DARK : entry.send() ? Chrome.ON : Chrome.OFF,
                Component.translatable("gui.actualgenerators.filter.send.short"), mouseX, mouseY);
        Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ENTRY_MODE_BUTTON,
                !set ? Chrome.DARK : entry.blacklist() ? Chrome.WARN : Chrome.ON,
                Component.translatable(entry.blacklist()
                        ? "gui.actualgenerators.filter.mode.block" : "gui.actualgenerators.filter.mode.allow"), mouseX, mouseY);

        if (page == Page.ITEM) {
            // The data box itself is the editor widget, drawn with the other widgets.
            Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ENTRY_APPLY_BUTTON, set ? Chrome.ACCENT : Chrome.DARK,
                    Component.translatable("gui.actualgenerators.filter.entry.data.apply"), mouseX, mouseY);
            if (!dataStatus.getString().isEmpty()) {
                MachineLayout.Box status = FilterMenu.ENTRY_DATA_STATUS;
                graphics.drawString(font, font.split(dataStatus, status.width()).get(0),
                        leftPos + status.x(), topPos + status.y(), dataStatusColour, false);
            }
        } else {
            Chrome.box(graphics, leftPos, topPos, FilterMenu.ENTRY_TAG_BOX, Chrome.BUTTON_EDGE, Chrome.DARK, mouseX, mouseY);
            drawTags(graphics, entry, mouseX, mouseY);
        }

        Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ENTRY_BACK_BUTTON, Chrome.DARK,
                Component.translatable("gui.actualgenerators.filter.entry.back"), mouseX, mouseY);
        Chrome.button(graphics, font, leftPos, topPos, FilterMenu.ENTRY_REMOVE_BUTTON, set ? Chrome.WARN : Chrome.DARK,
                Component.translatable("gui.actualgenerators.filter.remove.short"), mouseX, mouseY);
    }

    /** The tags of the pictured thing, one a row, the entry's own framed. */
    private void drawTags(GuiGraphics graphics, FilterContents.Entry entry, int mouseX, int mouseY) {
        List<ResourceLocation> tags = tagsOf(entry);
        if (tags.isEmpty()) {
            Chrome.text(graphics, font, leftPos, topPos, FilterMenu.entryRow(0),
                    Component.translatable(pictureless(entry)
                            ? "gui.actualgenerators.filter.entry.tags.none.empty"
                            : "gui.actualgenerators.filter.entry.tags.none"), Chrome.MUTED);
            return;
        }
        tagScroll = Math.clamp(tagScroll, 0, Math.max(0, tags.size() - FilterMenu.ENTRY_ROWS));
        for (int row = 0; row < FilterMenu.ENTRY_ROWS && tagScroll + row < tags.size(); row++) {
            ResourceLocation tag = tags.get(tagScroll + row);
            boolean chosen = entry.tag().filter(tag::equals).isPresent();
            MachineLayout.Box box = FilterMenu.entryRow(row);
            Chrome.box(graphics, leftPos, topPos, box, chosen ? Chrome.SELECTED_EDGE : Chrome.BUTTON_EDGE,
                    chosen ? Chrome.ACCENT : Chrome.DARK, mouseX, mouseY);
            graphics.drawString(font, font.split(Component.literal("#" + tag), box.width() - 6).get(0),
                    leftPos + box.x() + 3, topPos + box.y() + 1, Chrome.LABEL, false);
        }
    }

    private void scrollbar(GuiGraphics graphics, MachineLayout.Box bar, int at, int total, int shown) {
        int x = leftPos + bar.x();
        int y = topPos + bar.y();
        graphics.fill(x, y, x + bar.width(), y + bar.height(), Chrome.DARK);
        int thumb = Math.max(8, bar.height() * shown / total);
        int travel = bar.height() - thumb;
        int from = y + travel * at / Math.max(1, total - shown);
        graphics.fill(x, from, x + bar.width(), from + thumb, Chrome.ACCENT);
    }

    // ------------------------------------------------------------------ the entry's data, as text

    /** The entry's components as text to edit: the patch as SNBT over lines; nothing when there is none. */
    private String dataText(FilterContents.Entry entry) {
        if (entry.isEmpty() || minecraft == null || minecraft.level == null) {
            return "";
        }
        DataComponentPatch patch = entry.isFluid() ? entry.fluid().getComponentsPatch() : entry.item().getComponentsPatch();
        if (patch.isEmpty()) {
            return "";
        }
        return DataComponentPatch.CODEC.encodeStart(ops(), patch).result().map(NbtUtils::prettyPrint).orElse("");
    }

    private RegistryOps<Tag> ops() {
        return minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    /**
     * Apply: the text becomes the entry's data. The picture is rebuilt from a plain item or fluid
     * with the patch put on, so a line deleted from the text is a component gone; text that is not
     * a component patch is refused with the reason, and nothing changes. Data worth typing is data
     * worth matching, so a non-empty patch switches Match on.
     */
    private void applyData() {
        FilterContents.Entry entry = menu.entry(index);
        if (dataEditor == null || entry.isEmpty() || minecraft == null || minecraft.level == null) {
            return;
        }
        String text = dataEditor.getValue().strip();
        DataComponentPatch patch;
        try {
            patch = text.isEmpty() ? DataComponentPatch.EMPTY : DataComponentPatch.CODEC.parse(ops(), TagParser.parseTag(text)).getOrThrow();
        } catch (CommandSyntaxException | IllegalStateException e) {
            status(Component.translatable("gui.actualgenerators.filter.entry.data.bad", e.getMessage() == null ? "?" : e.getMessage()), true);
            return;
        }
        FilterContents.Entry picture;
        if (entry.isFluid()) {
            picture = FilterContents.Entry.ofFluid(new FluidStack(entry.fluid().getFluidHolder(), entry.fluid().getAmount(), patch));
        } else {
            ItemStack fresh = new ItemStack(entry.item().getItemHolder(), 1);
            fresh.applyComponents(patch);
            String problem = ItemStack.validateComponents(fresh.getComponents()).error().map(error -> error.message()).orElse(null);
            if (problem != null) {
                status(Component.translatable("gui.actualgenerators.filter.entry.data.bad", problem), true);
                return;
            }
            picture = FilterContents.Entry.ofItem(fresh);
        }
        FilterContents.Entry next = entry.repictured(picture);
        if (!patch.isEmpty()) {
            next = next.withMatchComponents(true);
        }
        send(index, next);
        setFocused(null);
        status(Component.translatable("gui.actualgenerators.filter.entry.data.applied"), false);
    }

    private void status(Component text, boolean bad) {
        dataStatus = text;
        dataStatusColour = bad ? Chrome.BAD : Chrome.TEXT;
    }

    // ------------------------------------------------------------------ tags

    private static String tagText(FilterContents.Entry entry) {
        return entry.tag().map(tag -> "#" + tag).orElse("");
    }

    /** Enter in the tag box: the tag typed, with or without its '#', is what the entry stands for. */
    private void applyTag() {
        if (tagEditor == null) {
            return;
        }
        String typed = tagEditor.getValue().strip();
        if (typed.startsWith("#")) {
            typed = typed.substring(1);
        }
        ResourceLocation tag = typed.isEmpty() ? null : ResourceLocation.tryParse(typed);
        if (tag == null) {
            tagEditor.setTextColor(EDITOR_BAD);
            return;
        }
        FilterContents.Entry entry = menu.entry(index);
        boolean fresh = entry.isEmpty();
        // A typed tag on an entry with no picture gets the tag's own picture, switches kept.
        send(index, fresh ? FilterContents.Entry.ofTag(tag)
                : pictureless(entry) ? entry.repictured(FilterContents.Entry.ofTag(tag)) : entry.withTag(tag));
        if (fresh) {
            index = Math.min(menu.contents().size(), FilterContents.MAX_ENTRIES);
        }
        setFocused(null);
    }

    /** Whether the entry has nothing to show in the slot: a tag typed by name with no item under it. */
    private static boolean pictureless(FilterContents.Entry entry) {
        return entry.item().isEmpty() && entry.fluid().isEmpty();
    }

    /** The pictured thing's tags, by name. */
    private static List<ResourceLocation> tagsOf(FilterContents.Entry entry) {
        if (pictureless(entry)) {
            return List.of();
        }
        List<ResourceLocation> tags = new ArrayList<>();
        (entry.isFluid() ? entry.fluid().getFluidHolder().tags() : entry.item().getTags())
                .map(TagKey::location)
                .forEach(tags::add);
        tags.sort(Comparator.comparing(ResourceLocation::toString));
        return tags;
    }

    /** A tag entry from a fresh picture: its first tag, or the picture itself when it has none. */
    private static FilterContents.Entry withFirstTag(FilterContents.Entry entry) {
        List<ResourceLocation> tags = tagsOf(entry);
        return tags.isEmpty() ? entry.itself() : entry.withTag(tags.get(0));
    }

    // ------------------------------------------------------------------ tooltips

    private List<Component> listTooltip(int mouseX, int mouseY) {
        if (adding) {
            if (over(mouseX, mouseY, FilterMenu.ADD_ITEM_BUTTON)) {
                return List.of(Component.translatable("gui.actualgenerators.filter.add.item.tip"));
            }
            if (over(mouseX, mouseY, FilterMenu.ADD_TAG_BUTTON)) {
                return List.of(Component.translatable("gui.actualgenerators.filter.add.tag.tip"));
            }
        } else if (over(mouseX, mouseY, FilterMenu.ADD_BUTTON)) {
            return List.of(Component.translatable("gui.actualgenerators.filter.add.tip"),
                    Component.translatable("gui.actualgenerators.filter.add.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (int row = 0; row < FilterMenu.ROWS; row++) {
            FilterContents.Entry entry = menu.entry(first + row);
            if (entry.isEmpty()) {
                break;
            }
            if (over(mouseX, mouseY, FilterMenu.rowIcon(row)) || over(mouseX, mouseY, FilterMenu.rowLabel(row))) {
                return List.of(Chrome.entryLabel(entry),
                        Component.translatable("gui.actualgenerators.filter.row.hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (over(mouseX, mouseY, FilterMenu.rowMarks(row))) {
                return List.of(marksText(entry),
                        Component.translatable("gui.actualgenerators.filter.row.hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (over(mouseX, mouseY, FilterMenu.rowRemove(row))) {
                return List.of(Component.translatable("gui.actualgenerators.filter.remove"));
            }
        }
        return List.of();
    }

    private static Component marksText(FilterContents.Entry entry) {
        Component direction = Component.translatable(entry.receive() && entry.send()
                ? "gui.actualgenerators.filter.marks.both"
                : entry.receive() ? "gui.actualgenerators.filter.marks.receive"
                : entry.send() ? "gui.actualgenerators.filter.marks.send" : "gui.actualgenerators.filter.marks.none");
        return direction.copy().append(" · ").append(Component.translatable(entry.blacklist()
                ? "gui.actualgenerators.filter.blacklist" : "gui.actualgenerators.filter.whitelist"));
    }

    private List<Component> pageTooltip(int mouseX, int mouseY) {
        FilterContents.Entry entry = menu.entry(index);
        if (over(mouseX, mouseY, FilterMenu.ENTRY_SLOT)) {
            return List.of(Component.translatable("gui.actualgenerators.filter.entry.slot"),
                    Component.translatable("gui.actualgenerators.filter.add.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (page == Page.ITEM && over(mouseX, mouseY, FilterMenu.ENTRY_MATCH_BUTTON)) {
            return List.of(Component.translatable(entry.isGroup()
                    ? "gui.actualgenerators.filter.entry.group" : "gui.actualgenerators.filter.entry.hint"));
        }
        if (page == Page.ITEM && over(mouseX, mouseY, FilterMenu.ENTRY_APPLY_BUTTON)) {
            return List.of(Component.translatable("gui.actualgenerators.filter.entry.data.apply.tip"));
        }
        if (page == Page.TAG && over(mouseX, mouseY, FilterMenu.ENTRY_TAG_BOX) && (tagEditor == null || !tagEditor.isFocused())) {
            return List.of(Component.translatable("gui.actualgenerators.filter.entry.tag.box.tip"));
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_RECEIVE_BUTTON)) {
            return List.of(Component.translatable(entry.receive()
                            ? "gui.actualgenerators.filter.receive.on" : "gui.actualgenerators.filter.receive.off"),
                    Component.translatable("gui.actualgenerators.filter.switch.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_SEND_BUTTON)) {
            return List.of(Component.translatable(entry.send()
                            ? "gui.actualgenerators.filter.send.on" : "gui.actualgenerators.filter.send.off"),
                    Component.translatable("gui.actualgenerators.filter.switch.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_MODE_BUTTON)) {
            return List.of(Component.translatable(entry.blacklist()
                            ? "gui.actualgenerators.filter.mode.block.tip" : "gui.actualgenerators.filter.mode.allow.tip"),
                    Component.translatable("gui.actualgenerators.filter.switch.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (page == Page.TAG) {
            List<ResourceLocation> tags = tagsOf(entry);
            for (int row = 0; row < FilterMenu.ENTRY_ROWS && tagScroll + row < tags.size(); row++) {
                if (over(mouseX, mouseY, FilterMenu.entryRow(row))) {
                    return List.of(Component.translatable("gui.actualgenerators.filter.entry.row.hint").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_REMOVE_BUTTON) && !entry.isEmpty()) {
            return List.of(Component.translatable("gui.actualgenerators.filter.remove"));
        }
        return List.of();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // A click away from a focused editor takes the keys back from it.
        if (getFocused() instanceof AbstractWidget widget && !widget.isMouseOver(mouseX, mouseY)) {
            setFocused(null);
        }
        if (button != 0 && button != 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        boolean handled = page == Page.LIST ? clickList(mouseX, mouseY, button) : clickPage(mouseX, mouseY, button);
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickList(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (adding) {
            if (over(mouseX, mouseY, FilterMenu.ADD_ITEM_BUTTON)) {
                open(Page.ITEM, -1);
                return true;
            }
            if (over(mouseX, mouseY, FilterMenu.ADD_TAG_BUTTON)) {
                open(Page.TAG, -1);
                return true;
            }
            adding = false;
        } else if (over(mouseX, mouseY, FilterMenu.ADD_BUTTON)) {
            adding = true;
            return true;
        }
        for (int row = 0; row < FilterMenu.ROWS; row++) {
            int at = first + row;
            FilterContents.Entry entry = menu.entry(at);
            if (entry.isEmpty()) {
                break;
            }
            if (over(mouseX, mouseY, FilterMenu.rowIcon(row)) || over(mouseX, mouseY, FilterMenu.rowLabel(row))
                    || over(mouseX, mouseY, FilterMenu.rowMarks(row))) {
                open(entry.kind() == FilterContents.Kind.TAG ? Page.TAG : Page.ITEM, at);
                return true;
            }
            if (over(mouseX, mouseY, FilterMenu.rowRemove(row))) {
                press(FilterMenu.entryButton(at, FilterMenu.ENTRY_REMOVE));
                return true;
            }
        }
        return false;
    }

    private boolean clickPage(double mouseX, double mouseY, int button) {
        FilterContents.Entry entry = menu.entry(index);
        if (over(mouseX, mouseY, FilterMenu.ENTRY_SLOT)) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                acceptPicture(index, FilterMenu.entryFor(carried, button == 1));
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_BACK_BUTTON)) {
            open(Page.LIST, -1);
            return true;
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_REMOVE_BUTTON)) {
            if (!entry.isEmpty()) {
                press(FilterMenu.entryButton(index, FilterMenu.ENTRY_REMOVE));
            }
            open(Page.LIST, -1);
            return true;
        }
        if (entry.isEmpty()) {
            return false;
        }
        if (page == Page.ITEM && over(mouseX, mouseY, FilterMenu.ENTRY_MATCH_BUTTON)) {
            if (!entry.isGroup()) {
                press(FilterMenu.entryButton(index, FilterMenu.ENTRY_MATCH));
            }
            return true;
        }
        if (page == Page.ITEM && over(mouseX, mouseY, FilterMenu.ENTRY_APPLY_BUTTON)) {
            applyData();
            return true;
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_RECEIVE_BUTTON)) {
            press(FilterMenu.entryButton(index, FilterMenu.ENTRY_RECEIVE));
            return true;
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_SEND_BUTTON)) {
            press(FilterMenu.entryButton(index, FilterMenu.ENTRY_SEND));
            return true;
        }
        if (over(mouseX, mouseY, FilterMenu.ENTRY_MODE_BUTTON)) {
            press(FilterMenu.entryButton(index, FilterMenu.ENTRY_MODE));
            return true;
        }
        if (page == Page.TAG) {
            List<ResourceLocation> tags = tagsOf(entry);
            for (int row = 0; row < FilterMenu.ENTRY_ROWS && tagScroll + row < tags.size(); row++) {
                if (over(mouseX, mouseY, FilterMenu.entryRow(row))) {
                    send(index, entry.withTag(tags.get(tagScroll + row)));
                    return true;
                }
            }
        }
        return false;
    }

    private void open(Page next, int at) {
        page = next;
        index = at;
        adding = false;
        tagScroll = 0;
        setFocused(null);
        refreshEditors();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY);
        switch (page) {
            case LIST -> first = Math.clamp(first - step, 0, Math.max(0, menu.contents().size() - FilterMenu.ROWS));
            case ITEM -> {
                return dataEditor != null && dataEditor.isMouseOver(mouseX, mouseY)
                        && dataEditor.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            case TAG -> tagScroll = Math.max(0, tagScroll - step);
        }
        return true;
    }

    /** While an editor has focus every key is its own, the inventory key included; Escape on a page is the list again. */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (dataEditor != null && dataEditor.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
                return true;
            }
            dataEditor.keyPressed(key, scanCode, modifiers);
            return true;
        }
        if (tagEditor != null && tagEditor.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                applyTag();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
                return true;
            }
            tagEditor.keyPressed(key, scanCode, modifiers);
            return true;
        }
        if (page != Page.LIST && key == GLFW.GLFW_KEY_ESCAPE) {
            open(Page.LIST, -1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (dataEditor != null && dataEditor.isFocused()) {
            return dataEditor.charTyped(character, modifiers);
        }
        if (tagEditor != null && tagEditor.isFocused()) {
            return tagEditor.charTyped(character, modifiers);
        }
        return super.charTyped(character, modifiers);
    }

    private void send(int at, FilterContents.Entry entry) {
        PacketDistributor.sendToServer(new SetFilterPayload(menu.containerId, at, entry));
    }

    private void press(int button) {
        if (minecraft != null) {
            Chrome.press(minecraft, menu.containerId, button);
        }
    }

    private boolean over(double mouseX, double mouseY, MachineLayout.Box box) {
        return Chrome.isOver(mouseX, mouseY, leftPos, topPos, box);
    }
}
