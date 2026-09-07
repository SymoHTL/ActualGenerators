package dev.symo.actualgenerators.item;

import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.generator.HeatPockets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * Reads the heat pocket under the chunk the player stands in, before a single casing is placed:
 * how much is down there, how full it is, and how deep a tap has to go for the whole rating.
 * Use it anywhere; the reading is a line on the action bar.
 */
public class ThermalProbeItem extends Item {

    public ThermalProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            player.displayClientMessage(reading(server, new ChunkPos(player.blockPosition())), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** What the probe says about a chunk: the pocket's fill and the depth that earns it, or that there is none. */
    public static Component reading(ServerLevel level, ChunkPos chunk) {
        HeatPockets pockets = HeatPockets.get(level);
        long capacity = pockets.capacity(level, chunk);
        if (capacity <= 0) {
            return Component.translatable("item.actualgenerators.thermal_probe.none").withStyle(ChatFormatting.RED);
        }
        long remaining = pockets.remaining(level, chunk, level.getGameTime());
        int fullBelow = level.getMinBuildHeight() + ServerConfig.valueOr(ServerConfig.GEOTHERMAL_FULL_DEPTH, 8);
        return Component.translatable("item.actualgenerators.thermal_probe.pocket",
                compact(remaining), compact(capacity), remaining * 100 / capacity, fullBelow);
    }

    /** 34.2M, 820k, 999: what fits on an action bar. */
    static String compact(long value) {
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000) {
            return String.format(Locale.ROOT, "%.0fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.actualgenerators.thermal_probe.hint").withStyle(ChatFormatting.GRAY));
    }
}
