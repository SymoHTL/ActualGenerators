package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.client.ClientHooks;
import dev.symo.actualgenerators.logistics.LinkNetworkManager;
import dev.symo.actualgenerators.logistics.LinkPortBlockEntity;
import dev.symo.actualgenerators.logistics.PadSnapshot;
import dev.symo.actualgenerators.logistics.PortFace;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.machine.MachineConfigSnapshot;
import dev.symo.actualgenerators.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The cheap early-game tool that copies a whole configuration and pastes it somewhere else. Crouch
 * and use to copy; use to paste; use at nothing to blank the card.
 *
 * <p>Two kinds of thing fit on it, one at a time. A machine's configuration is stored relative to
 * the machine's facing, so a pasted machine keeps the same layout regardless of which way it was
 * placed. A pad's ({@link PadSnapshot}) is everything the pad is: links, kinds, filters, upgrades,
 * label and network. Used on a pad, the card asks ({@link dev.symo.actualgenerators.client.PadApplyScreen})
 * whether it means this pad or this pad and every neighbour with a pad on the same face, neighbour
 * to neighbour and never diagonally; {@link #applyPad} is the answer.
 */
public class ConfigCardItem extends Item {
    /** How many port blocks one paste may walk out to: a wall of pads, not a whole base. */
    private static final int FLOOD_LIMIT = 256;

    public ConfigCardItem(Properties properties) {
        super(properties);
    }

    /**
     * Runs <em>before</em> the block's own right-click, which is the only way a tool click ever
     * reaches a tool: vanilla asks the block first, and a machine's or a pad's answer is "open my window".
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.getBlockEntity(pos) instanceof LinkPortBlockEntity port) {
            PortFace face = port.faceFor(context.getClickedFace());
            return face == null ? InteractionResult.PASS : usePad(stack, level, pos, face, player);
        }
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean copying = player != null && player.isSecondaryUseActive();
        if (copying) {
            stack.set(ModDataComponents.MACHINE_CONFIG.get(), MachineConfigSnapshot.copyOf(machine));
            stack.remove(ModDataComponents.PAD_CONFIG.get());
            feedback(player, "item.actualgenerators.config_card.copied");
        } else {
            MachineConfigSnapshot stored = stack.get(ModDataComponents.MACHINE_CONFIG.get());
            if (stored == null) {
                feedback(player, stack.has(ModDataComponents.PAD_CONFIG.get())
                        ? "item.actualgenerators.config_card.machine.pad"
                        : "item.actualgenerators.config_card.empty");
                return InteractionResult.CONSUME;
            }
            stored.applyTo(machine);
            feedback(player, "item.actualgenerators.config_card.pasted");
        }
        return InteractionResult.CONSUME;
    }

    /** Crouch copies the pad; a plain use with a pad on the card asks the question, and the answer comes back as a payload. */
    private InteractionResult usePad(ItemStack card, Level level, BlockPos pos, PortFace face, @Nullable Player player) {
        boolean copying = player != null && player.isSecondaryUseActive();
        if (level.isClientSide) {
            if (!copying && card.has(ModDataComponents.PAD_CONFIG.get())) {
                ClientHooks.openPadApply(pos, face.direction());
            }
            return InteractionResult.SUCCESS;
        }
        if (copying) {
            if (player instanceof ServerPlayer serverPlayer && !LinkNetworkManager.admits(serverPlayer, face.networkId())) {
                return InteractionResult.CONSUME;
            }
            card.set(ModDataComponents.PAD_CONFIG.get(), PadSnapshot.of(face));
            card.remove(ModDataComponents.MACHINE_CONFIG.get());
            feedback(player, "item.actualgenerators.config_card.pad.copied");
        } else if (!card.has(ModDataComponents.PAD_CONFIG.get())) {
            feedback(player, card.has(ModDataComponents.MACHINE_CONFIG.get())
                    ? "item.actualgenerators.config_card.pad.machine"
                    : "item.actualgenerators.config_card.empty");
        }
        return InteractionResult.CONSUME;
    }

    /** Used at nothing, a card that holds something is blank again. Anything else it is pointed at is a paste. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack card = player.getItemInHand(hand);
        if (getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() != HitResult.Type.MISS
                || (!card.has(ModDataComponents.MACHINE_CONFIG.get()) && !card.has(ModDataComponents.PAD_CONFIG.get()))) {
            return InteractionResultHolder.pass(card);
        }
        if (!level.isClientSide) {
            clear(card);
            feedback(player, "item.actualgenerators.config_card.cleared");
        }
        return InteractionResultHolder.sidedSuccess(card, level.isClientSide);
    }

    public static void clear(ItemStack card) {
        card.remove(ModDataComponents.MACHINE_CONFIG.get());
        card.remove(ModDataComponents.PAD_CONFIG.get());
    }

    /**
     * The card's question answered, on the server: the pad on {@code face} of the block at
     * {@code pos}, and with {@code flood} every port block reachable from it face to face that has
     * a pad on the same side. A block with no pad on that side ends the walk there, a pad on a
     * network the player may not use is skipped and counted, and the card has to be in a hand.
     */
    public static void applyPad(ServerPlayer player, BlockPos pos, Direction face, boolean flood) {
        PadSnapshot snapshot = player.getMainHandItem().get(ModDataComponents.PAD_CONFIG.get());
        if (snapshot == null) {
            snapshot = player.getOffhandItem().get(ModDataComponents.PAD_CONFIG.get());
        }
        if (snapshot == null || !player.canInteractWithBlock(pos, 4.0)) {
            return;
        }
        Level level = player.level();
        LinkNetworkManager manager = LinkNetworkManager.get(player.server);
        PadSnapshot.Applied applied = PadSnapshot.Applied.NONE;
        int refused = 0;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos);
        queue.add(pos);
        int walked = 0;
        while (!queue.isEmpty() && walked++ < FLOOD_LIMIT) {
            BlockPos at = queue.poll();
            if (!(level.getBlockEntity(at) instanceof LinkPortBlockEntity port)) {
                continue;
            }
            PortFace pad = port.face(face);
            if (pad == null) {
                continue;
            }
            if (pad.networkId() == null || manager.canAccess(pad.networkId(), player)) {
                applied = applied.plus(snapshot.applyTo(pad, player, true));
            } else {
                refused++;
            }
            if (!flood) {
                break;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        MutableComponent message = Component.translatable("item.actualgenerators.config_card.pad.pasted", applied.pads());
        if (applied.lacksAnything()) {
            message.append(" · ").append(Component.translatable("item.actualgenerators.config_card.pad.missing",
                    Math.max(applied.missingFilters(), applied.missingUpgrades())));
        }
        if (refused > 0) {
            message.append(" · ").append(Component.translatable("item.actualgenerators.config_card.pad.refused", refused));
        }
        player.displayClientMessage(message, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PadSnapshot pad = stack.get(ModDataComponents.PAD_CONFIG.get());
        boolean machine = stack.has(ModDataComponents.MACHINE_CONFIG.get());
        if (pad != null) {
            tooltip.add((pad.label().isEmpty()
                    ? Component.translatable("item.actualgenerators.config_card.tooltip.pad", pad.linkCount())
                    : Component.translatable("item.actualgenerators.config_card.tooltip.pad.label", pad.label()))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(machine
                    ? "item.actualgenerators.config_card.tooltip.stored"
                    : "item.actualgenerators.config_card.tooltip.empty").withStyle(ChatFormatting.GRAY));
        }
        if (pad != null || machine) {
            tooltip.add(Component.translatable("item.actualgenerators.config_card.tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void feedback(@Nullable Player player, String translationKey) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }
}
