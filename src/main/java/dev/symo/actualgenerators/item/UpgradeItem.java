package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.UpgradeType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

import java.util.List;
import java.util.Locale;

/**
 * One of the four machine upgrades. The type is carried by the item itself so machines can
 * tally their upgrades without a registry lookup.
 *
 * <p>The tooltip says what the type does in general terms; the numbers for a particular machine
 * are on that machine's upgrade slot, where they can be real rather than approximate.
 */
public class UpgradeItem extends Item {
    private final UpgradeType type;

    public UpgradeItem(UpgradeType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public UpgradeType type() {
        return type;
    }

    /**
     * Crouch and use on a machine to slot the upgrade straight in, without opening anything.
     *
     * <p>This has to live on the item rather than on the block: crouching with a full hand makes
     * the game skip block interaction entirely and go straight to the item, which is also why a
     * plain right-click still opens the screen instead of installing something by accident.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = context.getItemInHand();
        int installed = machine.upgradeInventory().count(type);
        int moved = held.getCount()
                - machine.upgradeInventory().insertItem(type.ordinal(), held.copy(), false).getCount();

        if (moved <= 0) {
            player.displayClientMessage(Component.translatable(machine.acceptsUpgrade(type)
                            ? "item.actualgenerators.upgrade.full"
                            : "item.actualgenerators.upgrade.refused")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        held.shrink(moved);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.4F);
        player.displayClientMessage(Component.translatable("item.actualgenerators.upgrade.installed",
                moved, installed + moved, machine.maxUpgrades(type)), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.actualgenerators.upgrade." + type.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.actualgenerators.upgrade.install_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
