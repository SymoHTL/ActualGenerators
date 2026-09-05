package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.client.ClientHooks;
import dev.symo.actualgenerators.logistics.EnergyInjectorBlockEntity;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.menu.LinkPortMenu;
import dev.symo.actualgenerators.menu.NetworkPickerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * The linking tool: use it on a pad or an injector that is on no network yet and pick one.
 *
 * <p>One job. On a block with no network the click opens the picker, which lists every network
 * there is and lets a player make a new one with a name. On a block that already has one it opens
 * the block's own window, where the network line does the rest. Holding it draws every pad and
 * injector in view with its network's name, the network's centre, a line from the centre to
 * each pad and the reach, so a base can be read without opening anything; crouching and using
 * it opens the switches for what it draws (client side only, nothing reaches the server).
 */
public class LinkingToolItem extends Item {

    public LinkingToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Runs <em>before</em> the block's own right-click, and that is not a detail.
     *
     * <p>Vanilla asks the block first and only falls through to the item when the block passes or
     * the player is crouching. A pad answers "open my window", so a tool that hooked
     * {@code useOn} would only ever be reached by a crouch.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack tool, UseOnContext context) {
        Level level = context.getLevel();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (level.isClientSide) {
                ClientHooks.openOverlayOptions();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos pos = context.getClickedPos();
        BlockEntity target = level.getBlockEntity(pos);
        if (target instanceof LinkPortBlockEntity port) {
            PortFace face = port.faceFor(context.getClickedFace());
            if (face == null) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer player) {
                if (face.isLinked()) {
                    if (LinkNetworkManager.admits(player, face.networkId())) {
                        player.openMenu(new LinkPortMenu.Opener(port, face.direction()),
                                buffer -> LinkPortMenu.writeOpener(buffer, port, face.direction()));
                    }
                } else {
                    NetworkPickerMenu.open(player, serverLevel, pos, face.direction(), false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (target instanceof EnergyInjectorBlockEntity injector) {
            if (level instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer player) {
                if (injector.isLinked()) {
                    if (LinkNetworkManager.admits(player, injector.networkId())) {
                        player.openMenu(injector, buffer -> buffer.writeBlockPos(pos));
                    }
                } else {
                    NetworkPickerMenu.open(player, serverLevel, pos, null, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Crouching and using the tool on nothing in particular: the overlay switches. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                ClientHooks.openOverlayOptions();
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.actualgenerators.linking_tool.hint").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.actualgenerators.linking_tool.hint.held").withStyle(ChatFormatting.DARK_GRAY));
    }
}
