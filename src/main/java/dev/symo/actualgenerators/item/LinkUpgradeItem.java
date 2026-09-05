package dev.symo.actualgenerators.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * An upgrade that goes in a Logic Port rather than in a machine.
 *
 * <p>Separate from {@link UpgradeItem} on purpose: a port has no speed, no batch and no buffer, so
 * the four machine upgrades mean nothing in one, and a slot that accepted them and did nothing
 * would be a lie. All this class adds to a plain item is the sentence explaining what it is for.
 */
public class LinkUpgradeItem extends Item {

    public LinkUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId(stack) + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
