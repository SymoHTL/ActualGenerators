package dev.symo.actualgenerators.compat.jade;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.machine.IoMode;
import dev.symo.actualgenerators.machine.RelativeSide;
import dev.symo.actualgenerators.machine.multiblock.HatchBlockEntity;
import dev.symo.actualgenerators.machine.multiblock.HatchKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * What the face of a hatch you are looking at does: what may pass through it and whether the
 * structure pulls or pushes through it, or that it looks into the structure; and whether a
 * structure stands behind the hatch at all.
 */
public class HatchStatusProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final HatchStatusProvider INSTANCE = new HatchStatusProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "hatch");
    private static final String KEY_MODE = "Mode";
    private static final String KEY_PULL = "Pull";
    private static final String KEY_PUSH = "Push";
    private static final String KEY_BLOCKED = "Blocked";
    private static final String KEY_FORMED = "Formed";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof HatchBlockEntity hatch)) {
            return;
        }
        data.putBoolean(KEY_FORMED, hatch.controller() != null);
        if (hatch.kind() == HatchKind.REDSTONE) {
            return;
        }
        Direction side = accessor.getSide();
        RelativeSide relative = RelativeSide.fromDirection(hatch.facing(), side);
        data.putBoolean(KEY_BLOCKED, hatch.blocked(relative));
        data.putInt(KEY_MODE, hatch.mode(side).ordinal());
        data.putBoolean(KEY_PULL, hatch.autoPull());
        data.putBoolean(KEY_PUSH, hatch.autoPush());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains(KEY_MODE)) {
            if (data.getBoolean(KEY_BLOCKED)) {
                tooltip.add(Component.translatable("gui.actualgenerators.side.blocked").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                IoMode mode = IoMode.byOrdinal(data.getInt(KEY_MODE));
                MutableComponent line = Component.translatable("gui.actualgenerators.mode." + mode.name().toLowerCase(Locale.ROOT))
                        .withStyle(mode.isActive() ? ChatFormatting.GRAY : ChatFormatting.RED);
                if (data.getBoolean(KEY_PULL) && mode.canInput()) {
                    line.append(" \u00b7 ").append(Component.translatable("jade.actualgenerators.hatch.pulls").withStyle(ChatFormatting.GREEN));
                }
                if (data.getBoolean(KEY_PUSH) && mode.canOutput()) {
                    line.append(" \u00b7 ").append(Component.translatable("jade.actualgenerators.hatch.pushes").withStyle(ChatFormatting.GREEN));
                }
                tooltip.add(line);
            }
        }
        if (data.contains(KEY_FORMED) && !data.getBoolean(KEY_FORMED)) {
            tooltip.add(Component.translatable("jade.actualgenerators.hatch.unformed").withStyle(ChatFormatting.RED));
        }
    }
}
