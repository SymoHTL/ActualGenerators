package dev.symo.actualgenerators.client;

import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderSet;
import net.minecraft.Util;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.machine.RedstoneMode;
import dev.symo.actualgenerators.machine.TransferKind;
import dev.symo.actualgenerators.menu.MachineLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The flat drawing the logistics windows share: a panel, a button, a slot frame, a colour per
 * channel and per network. Nothing here knows what it is drawing for.
 */
final class Chrome {
    static final ResourceLocation SHEET =
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "textures/gui/machine.png");

    private static final int SLOT_SPRITE_U = 176;
    private static final int SLOT_SPRITE_V = 154;
    private static final int SLOT_SPRITE_SIZE = 18;

    static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int PANEL_EDGE = 0xFF000000;

    static final int TEXT = 0x404040;
    static final int MUTED = 0x707070;
    static final int BAD = 0xA02020;
    static final int LABEL = 0xFFFFFF;

    static final int BUTTON_EDGE = 0xFF373737;
    static final int ON = 0xFF4CA86B;
    static final int OFF = 0xFF6B6B6B;
    static final int DARK = 0xFF5A5A5A;
    static final int STEPPER = 0xFF7A6A55;
    static final int ACCENT = 0xFF6FA8DC;
    static final int SPREAD = 0xFF8B6FD4;
    static final int WARN = 0xFFA05050;
    static final int SELECTED_EDGE = 0xFFFFFFFF;
    static final int HOVER = 0x40FFFFFF;
    static final int INK_DARK = 0xFF202020;
    static final int INK_LIGHT = 0xFFF4F4F4;
    static final int BOLT_RED = 0xFFE83030;
    static final int DROP_BLUE = 0xFF6FC3FF;
    /** The torch: a lit head on a stick, so it is not another red bolt. */
    static final int REDSTONE_RED = 0xFFFF3B3B;
    static final int TORCH_BROWN = 0xFF7A4A24;

    private Chrome() {
    }

    /** A plain vanilla-looking panel, so a window owes the machine sheet nothing. */
    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
        graphics.fill(x + 1, y + 1, x + width - 2, y + 2, PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 2, PANEL_LIGHT);
        graphics.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, PANEL_DARK);
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, PANEL_DARK);
    }

    /** The frame round a 16x16 slot at the given slot position. */
    static void slotFrame(GuiGraphics graphics, int x, int y) {
        graphics.blit(SHEET, x - 1, y - 1, SLOT_SPRITE_U, SLOT_SPRITE_V, SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE);
    }

    /** A box with an edge and a fill, lit when the mouse is over it. */
    static void box(GuiGraphics graphics, int left, int top, MachineLayout.Box box, int edge, int fill,
                    int mouseX, int mouseY) {
        int x = left + box.x();
        int y = top + box.y();
        graphics.fill(x, y, x + box.width(), y + box.height(), edge);
        graphics.fill(x + 1, y + 1, x + box.width() - 1, y + box.height() - 1, fill);
        if (isOver(mouseX, mouseY, left, top, box)) {
            graphics.fill(x + 1, y + 1, x + box.width() - 1, y + box.height() - 1, HOVER);
        }
    }

    /** A button with a label centred on it. */
    static void button(GuiGraphics graphics, Font font, int left, int top, MachineLayout.Box box, int fill,
                       Component label, int mouseX, int mouseY) {
        box(graphics, left, top, box, BUTTON_EDGE, fill, mouseX, mouseY);
        centred(graphics, font, left, top, box, label, LABEL, true);
    }

    static void centred(GuiGraphics graphics, Font font, int left, int top, MachineLayout.Box box,
                        Component text, int colour, boolean shadow) {
        int x = left + box.x() + (box.width() - font.width(text)) / 2;
        int y = top + box.y() + (box.height() - 8) / 2 + 1;
        graphics.drawString(font, text, x, y, colour, shadow);
    }

    static void text(GuiGraphics graphics, Font font, int left, int top, MachineLayout.Box box,
                     Component text, int colour) {
        graphics.drawString(font, text, left + box.x(), top + box.y(), colour, false);
    }

    static boolean isOver(double mouseX, double mouseY, int left, int top, MachineLayout.Box box) {
        return mouseX >= left + box.x() && mouseX < left + box.x() + box.width()
                && mouseY >= top + box.y() && mouseY < top + box.y() + box.height();
    }

    /** A picture drawn from rows of '#', one pixel per character. */
    static void glyph(GuiGraphics graphics, String[] rows, int x, int y, int colour) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1, colour);
                }
            }
        }
    }

    /** Sends a menu button to the server, with the click a button makes. */
    static void press(Minecraft minecraft, int containerId, int button) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(containerId, button);
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ------------------------------------------------------------------ colours

    /** A channel is a dye colour, which is quicker to tell apart than a number. */
    static int channelColour(int channel) {
        return 0xFF000000 | DyeColor.byId(channel).getTextureDiffuseColor();
    }

    static Component channelName(int channel) {
        return Component.translatable("color.minecraft." + DyeColor.byId(channel).getName());
    }

    /** A network's colour as the manager hands it down (RGB), made opaque for drawing. */
    static int networkColour(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** A count in the unit of its kind: items as they are, millibuckets in buckets, FE in k and M. */
    /** A slot count past three digits, written short so it stays inside the slot: "1.2k", "16k". */
    static String compactCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 10_000) {
            return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
        }
        return count / 1000 + "k";
    }

    /** A tick count with its unit on: "10t", so a delay is never mistaken for a count. */
    static String ticksText(int ticks) {
        return ticks + "t";
    }

    static String amountText(int value, @Nullable TransferKind kind) {
        if (kind == TransferKind.FLUID) {
            return scaled(value, 1000, "B");
        }
        if (kind == TransferKind.ENERGY) {
            return value >= 1_000_000 ? scaled(value, 1_000_000, "M") : value >= 1000 ? scaled(value, 1000, "k") : String.valueOf(value);
        }
        return String.valueOf(value);
    }

    private static String scaled(int value, int unit, String suffix) {
        return value % unit == 0
                ? value / unit + suffix
                : String.format(java.util.Locale.ROOT, "%.1f%s", value / (double) unit, suffix);
    }

    static int dim(int colour) {
        int r = ((colour >> 16) & 0xFF) / 2 + 0x20;
        int g = ((colour >> 8) & 0xFF) / 2 + 0x20;
        int b = (colour & 0xFF) / 2 + 0x20;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Dark ink on a light colour, light ink on a dark one, so yellow and black both read. */
    static int inkFor(int colour) {
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 128 ? INK_DARK : INK_LIGHT;
    }

    static int redstoneColour(RedstoneMode mode) {
        return switch (mode) {
            case ALWAYS -> 0xFF8B8B8B;
            case WITH_SIGNAL -> 0xFFC03030;
            case WITHOUT_SIGNAL -> 0xFF50A050;
            case NEVER -> 0xFF303030;
        };
    }

    // ------------------------------------------------------------------ filter entries

    /** A filter entry as it looks in a slot: the item, or the fluid's own still texture. */
    static void drawEntry(GuiGraphics graphics, FilterContents.Entry entry, int x, int y) {
        if (entry.tag().isPresent() && drawTagMember(graphics, entry, x, y)) {
            return;
        }
        if (entry.isFluid()) {
            drawFluid(graphics, entry.fluid(), x, y);
        } else if (!entry.item().isEmpty()) {
            graphics.renderFakeItem(entry.item(), x, y);
        } else if (!entry.isEmpty()) {
            // A tag typed by name with nothing under it: a hash where the picture would be.
            graphics.drawString(Minecraft.getInstance().font, "#", x + 5, y + 4, TEXT, false);
        }
    }

    /** How long a tag entry's picture rests on each thing under the tag. */
    private static final long TAG_CYCLE_MILLIS = 1000;

    /**
     * A tag entry's picture walks through everything under the tag, one a second, JEI's way, so
     * the player sees what the entry lets through rather than the one item it was set from. Drawn
     * only: the entry keeps its own picture, and nothing here reaches the server.
     */
    private static boolean drawTagMember(GuiGraphics graphics, FilterContents.Entry entry, int x, int y) {
        ResourceLocation tag = entry.tag().orElseThrow();
        long step = Util.getMillis() / TAG_CYCLE_MILLIS;
        if (entry.isFluid()) {
            HolderSet.Named<Fluid> fluids = BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, tag)).orElse(null);
            if (fluids == null || fluids.size() == 0) {
                return false;
            }
            drawFluid(graphics, new FluidStack(fluids.get((int) (step % fluids.size())), FluidType.BUCKET_VOLUME), x, y);
            return true;
        }
        HolderSet.Named<Item> items = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag)).orElse(null);
        if (items == null || items.size() == 0) {
            return false;
        }
        graphics.renderFakeItem(new ItemStack(items.get((int) (step % items.size())), 1), x, y);
        return true;
    }

    static Component entryName(FilterContents.Entry entry) {
        return entry.isFluid() ? entry.fluid().getHoverName() : entry.item().getHoverName();
    }

    /** What an entry stands for, in a few words: the thing's name, or the group it stands for. */
    static Component entryLabel(FilterContents.Entry entry) {
        return switch (entry.kind()) {
            case EXACT -> entry.matchComponents()
                    ? entryName(entry).copy().append(" \u00b7 ").append(Component.translatable("gui.actualgenerators.filter.nbt"))
                    : entryName(entry);
            case TAG -> Component.translatable("gui.actualgenerators.filter.tag",
                    "#" + entry.tag().map(ResourceLocation::toString).orElse("?"));
            case MOD -> Component.translatable("gui.actualgenerators.filter.mod", modName(entry.mod().orElse("?")));
            case DAMAGED -> Component.translatable("gui.actualgenerators.filter.damaged");
            case ENCHANTED -> Component.translatable("gui.actualgenerators.filter.enchanted");
        };
    }

    /** What a mod calls itself, for a registry namespace; the namespace when no mod owns it. */
    static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    private static void drawFluid(GuiGraphics graphics, FluidStack fluid, int x, int y) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation still = extensions.getStillTexture(fluid);
        if (still == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
        int tint = extensions.getTintColor(fluid);
        float alpha = ((tint >>> 24) & 0xFF) / 255.0F;
        graphics.blit(x, y, 0, 16, 16, sprite,
                ((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F,
                alpha == 0 ? 1.0F : alpha);
    }
}
