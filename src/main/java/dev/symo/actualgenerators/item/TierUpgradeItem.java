package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.MachineTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A tier of the block itself: iron, gold, diamond, netherite. One goes into a machine or a pad and
 * the block is a better block, Mekanism's installer by another name.
 *
 * <p>In a machine it multiplies speed and batch at the same FE per operation, which is exactly
 * what a speed upgrade does not do. In a pad it multiplies what one send carries and lowers how
 * few ticks may pass between sends. The numbers are config, read through {@link MachineTier}.
 *
 * <p>Crouch and use it on the block, the way the other upgrades go in: a higher tier swaps in and
 * hands the fitted one back, a lower or equal one is refused. Generators refuse every tier, since
 * they have no fuel to trade for the speed and a free multiplier on FE is not a tier, it is a bug.
 */
public class TierUpgradeItem extends Item {
    private final MachineTier tier;

    public TierUpgradeItem(MachineTier tier, Properties properties) {
        super(properties.stacksTo(16));
        this.tier = tier;
    }

    public MachineTier tier() {
        return tier;
    }

    /** The tier of whatever sits in a tier slot: none for an empty slot or anything else. */
    public static MachineTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof TierUpgradeItem item ? item.tier : MachineTier.NONE;
    }

    /** "2" rather than "2.0", "1.5" as it is. */
    public static String multiplier(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Where a tier goes in a block: the handler and the slot in it. */
    private record TierSlot(IItemHandler handler, int slot) {
    }

    private static @Nullable TierSlot tierSlotAt(Level level, BlockPos pos, Direction clickedFace) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            return machine.acceptsTier() ? new TierSlot(machine.tierSlot(), 0) : null;
        }
        if (level.getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
            PortFace face = port.faceFor(clickedFace);
            return face == null ? null : new TierSlot(face.upgradeHandler(), PortFace.SLOT_TIER);
        }
        return null;
    }

    /**
     * Crouch and use on a machine or a pad to install it. Never plain use: every block of ours
     * opens a window on a plain click, so the item is only ever asked on a crouch.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        TierSlot target = tierSlotAt(level, pos, context.getClickedFace());
        if (target == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = context.getItemInHand();
        ItemStack fitted = target.handler().getStackInSlot(target.slot());
        if (!tier.isAbove(tierOf(fitted))) {
            player.displayClientMessage(Component.translatable("item.actualgenerators.tier_upgrade.lower",
                    tierOf(fitted).displayName()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        ItemStack old = target.handler().extractItem(target.slot(), 1, false);
        ItemStack refused = target.handler().insertItem(target.slot(), held.copyWithCount(1), false);
        if (!refused.isEmpty()) {
            target.handler().insertItem(target.slot(), old, false);
            player.displayClientMessage(Component.translatable("item.actualgenerators.tier_upgrade.refused")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        held.shrink(1);
        if (!old.isEmpty()) {
            player.getInventory().placeItemBackInInventory(old);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.6F);
        player.displayClientMessage(Component.translatable("item.actualgenerators.tier_upgrade.installed",
                tier.displayName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.actualgenerators.tier_upgrade.tooltip",
                multiplier(tier.speedMultiplier()), tier.batchMultiplier(),
                tier.linkAmountMultiplier(), tier.linkDelayTicks()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.actualgenerators.tier_upgrade.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
