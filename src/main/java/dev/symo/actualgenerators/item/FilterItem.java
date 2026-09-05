package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.menu.FilterMenu;
import dev.symo.actualgenerators.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * A filter: two rows that pass and two rows that never do, on an item a pad holds.
 *
 * <p>Use it to open it. A left click with something on the cursor writes that item into a slot, a
 * right click writes the fluid inside it, an empty hand clears the slot. Craft a set one with a
 * blank one to copy it onto both; craft a set one on its own to blank it.
 */
public class FilterItem extends Item {

    public FilterItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    public static FilterContents contents(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FILTER.get(), FilterContents.EMPTY);
    }

    /** A blank filter carries no component at all, so a fresh one and a cleared one stack. */
    public static void setContents(ItemStack stack, FilterContents contents) {
        if (contents.isBlank()) {
            stack.remove(ModDataComponents.FILTER.get());
        } else {
            stack.set(ModDataComponents.FILTER.get(), contents);
        }
    }

    public static boolean isConfigured(ItemStack stack) {
        return stack.getItem() instanceof FilterItem && !contents(stack).isBlank();
    }

    /** No filter, or a blank one, lets everything through; otherwise the list for the direction decides. */
    public static boolean allowsItem(ItemStack filter, ItemStack stack, boolean receiving) {
        return !(filter.getItem() instanceof FilterItem) || contents(filter).allowsItem(stack, receiving);
    }

    public static boolean allowsFluid(ItemStack filter, FluidStack fluid, boolean receiving) {
        return !(filter.getItem() instanceof FilterItem) || contents(filter).allowsFluid(fluid, receiving);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, viewer) -> new FilterMenu(id, inventory, slot),
                            stack.getHoverName()),
                    buffer -> buffer.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FilterContents contents = contents(stack);
        tooltip.add(contents.isBlank()
                ? Component.translatable("item.actualgenerators.filter.blank").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.actualgenerators.filter.summary",
                contents.count(true), contents.count(false)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.actualgenerators.filter.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
