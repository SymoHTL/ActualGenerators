package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Energy in a bottle: a stackable item holding a fixed amount of FE.
 *
 * <p>It is deliberately not a proprietary cell. The charge lives in a plain number data
 * component and is exposed through the <em>standard</em> FE item capability, so any other mod's
 * battery slot drains a crystal and any other mod's charger fills one, with no integration code
 * on either side. The same goes for automation: crystals are items, so anything that moves items
 * moves energy.
 *
 * <p>Two rules make that work in practice. The capability is only offered at a stack size of one,
 * which is the convention every battery item follows and which stops a charger from filling a
 * stack of sixty-four through a single slot. And because a crystal's charge is its only component,
 * crystals at the same charge carry identical components and therefore stack — full ones together,
 * empty ones together — so a chest or an ME cell holds them by the stack rather than one per slot.
 *
 * <p>Crystals are never mandatory. Energy transport is the job of Logic Ports; this is the big
 * dumb battery for the cases where an item is more convenient than a network.
 */
public class FluxCrystalItem extends Item {
    /** A charge bar in the mod's own flux colour, so a glance at the hotbar tells you the state. */
    private static final int BAR_COLOUR = 0x4FD8E8;

    public FluxCrystalItem(Properties properties) {
        // A default of zero, so an empty crystal carries no component at all and stacks with every
        // other empty one; a charged crystal carries the one number and stacks with its equals.
        super(properties.component(ModDataComponents.ENERGY.get(), 0L));
    }

    /** FE a single crystal holds when full. */
    public static long capacity() {
        return ServerConfig.valueOr(ServerConfig.FLUX_CRYSTAL_CAPACITY, 200_000L);
    }

    /** FE a crystal will take or give per tick through the standard capability. */
    public static int transferRate() {
        return ServerConfig.valueOr(ServerConfig.FLUX_CRYSTAL_TRANSFER, 2_000);
    }

    /**
     * The rate a particular crystal moves charge at.
     *
     * <p>One kind of crystal exists today, so this is that one rate. It takes the stack anyway
     * because the crystal is what sets the pace, not whatever is holding it: when there are tiers,
     * a better crystal is a faster one everywhere it is used, without anything that draws on one
     * having to learn about tiers.
     */
    public static int transferRate(ItemStack stack) {
        return stack.getItem() instanceof FluxCrystalItem ? transferRate() : 0;
    }

    /** FE in a stack's crystals, per crystal. Zero for anything that is not a crystal. */
    public static long energyOf(ItemStack stack) {
        return stack.getItem() instanceof FluxCrystalItem
                ? Math.clamp(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0L), 0, capacity())
                : 0;
    }

    /** The same stack at a different charge. Mutates in place — components are per-stack. */
    public static void setEnergy(ItemStack stack, long energy) {
        stack.set(ModDataComponents.ENERGY.get(), Math.clamp(energy, 0, capacity()));
    }

    /** A crystal, or a stack of them, at the given charge. */
    public static ItemStack withEnergy(ItemStack template, int count, long energy) {
        ItemStack stack = template.copyWithCount(count);
        setEnergy(stack, energy);
        return stack;
    }

    /**
     * Spending a crystal in a recipe hands the empty one back, the way a milk bucket hands back
     * the bucket. That is what makes energy a craftable ingredient rather than a consumable: the
     * vessel survives, only the charge is spent.
     *
     * <p>An already-empty crystal has nothing to spend, so it has no remainder — otherwise a
     * recipe using empties as a material would duplicate them.
     */
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return energyOf(stack) > 0;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if (energyOf(stack) <= 0) {
            return ItemStack.EMPTY;
        }
        return withEnergy(stack, 1, 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // Always: the charge is the whole point of the item, not an exception like damage.
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (int) Math.round(13.0 * energyOf(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.actualgenerators.energy",
                        formatNumber(energyOf(stack)), formatNumber(capacity()))
                .withStyle(ChatFormatting.GRAY));
    }

    private static String formatNumber(long value) {
        return String.format("%,d", value);
    }
}
