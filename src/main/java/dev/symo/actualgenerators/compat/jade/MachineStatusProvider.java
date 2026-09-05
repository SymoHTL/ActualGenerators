package dev.symo.actualgenerators.compat.jade;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.machine.MachineBlockEntity;
import dev.symo.actualgenerators.processing.ResonanceCrusherBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.Locale;

/**
 * What a machine is up to, as the same two bars its own window draws.
 *
 * <p>Every machine has a ramp, so every machine gets that bar — a generator warming up towards its
 * rating reads exactly like a crusher overclocking past its speed ceiling, which is the point: one
 * mechanic, one readout, whether or not the thing in front of you makes power or spends it. The
 * work bar only appears on machines that have work to be part of the way through.
 *
 * <p>Everything here is fetched from the server on the tick Jade asks for it: a ramp is not part of
 * what a machine syncs to clients on its own, and a tooltip lagging seconds behind the bar in the
 * window would be worse than no tooltip at all.
 *
 * <p>Energy is deliberately absent. Machines expose the ordinary Forge Energy capability and Jade's
 * own universal provider already draws a bar for anything that does.
 */
public class MachineStatusProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final MachineStatusProvider INSTANCE = new MachineStatusProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "machine");

    private static final String KEY_PROGRESS = "Progress";
    private static final String KEY_RAMP = "Ramp";
    /** What the ramp is currently worth: a speed multiplier, or a share of the rating. */
    private static final String KEY_RAMP_VALUE = "RampValue";
    private static final String KEY_WARMUP = "Warmup";
    private static final String KEY_FREQUENCY = "Frequency";
    private static final String KEY_TUNED = "Tuned";

    // Read off the GUI sheet rather than picked: the arrow's copper and the ramp bar's violet, with
    // the next shade down the same palette ramp as the gradient. A tooltip that recoloured what the
    // window draws would be a second thing to learn.
    private static final int PROGRESS_COLOUR = 0xFFB8693D;
    private static final int PROGRESS_COLOUR_DARK = 0xFF8A4729;
    private static final int RAMP_COLOUR = 0xFF8458AC;
    private static final int RAMP_COLOUR_DARK = 0xFF603884;

    /** White with the rest of the tooltip; Jade's automatic pick comes out black on these. */
    private static final int TEXT_COLOUR = 0xFFFFFFFF;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MachineBlockEntity machine)) {
            return;
        }

        double fraction = machine.progressFraction();
        if (fraction >= 0) {
            data.putInt(KEY_PROGRESS, percent(fraction));
        }

        boolean warmup = machine.usesWarmupRamp();
        double ramp = machine.overclockState().progress(machine.tuning());
        data.putInt(KEY_RAMP, percent(ramp));
        data.putBoolean(KEY_WARMUP, warmup);
        // A warming generator is worth a share of its rating; everything else is worth a speed
        // multiplier. Both travel in hundredths so the tooltip does no arithmetic of its own.
        data.putInt(KEY_RAMP_VALUE, warmup
                ? percent(machine.tuning().warmupMultiplier(ramp))
                : percent(machine.totalSpeedMultiplier()));

        if (machine instanceof ResonanceCrusherBlockEntity crusher) {
            int frequency = crusher.frequency();
            if (frequency > 0) {
                data.putInt(KEY_FREQUENCY, frequency);
                data.putBoolean(KEY_TUNED, crusher.isTuned());
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        IElementHelper elements = IElementHelper.get();

        if (data.contains(KEY_PROGRESS)) {
            int progress = data.getInt(KEY_PROGRESS);
            tooltip.add(bar(elements, progress, PROGRESS_COLOUR, PROGRESS_COLOUR_DARK,
                    Component.translatable("jade.actualgenerators.progress", progress)));
        }

        if (data.contains(KEY_RAMP)) {
            // One colour for both, because the window draws one bar for both. Only the label
            // differs: a generator warming up towards its rating is not overclocking past it.
            boolean warmup = data.getBoolean(KEY_WARMUP);
            int ramp = data.getInt(KEY_RAMP);
            int value = data.getInt(KEY_RAMP_VALUE);
            tooltip.add(bar(elements, ramp, RAMP_COLOUR, RAMP_COLOUR_DARK,
                    warmup
                            ? Component.translatable("jade.actualgenerators.warmup", ramp, value)
                            : Component.translatable("jade.actualgenerators.overclock", ramp, multiplier(value))));
        }

        if (data.contains(KEY_FREQUENCY)) {
            // Whether the crusher knows this material yet is the difference between a run that
            // takes the usual time and one that takes several times as long, so it goes on the
            // tooltip rather than being something you find out by waiting.
            tooltip.add(Component.translatable(
                    data.getBoolean(KEY_TUNED)
                            ? "jade.actualgenerators.tuned"
                            : "jade.actualgenerators.calibrating",
                    data.getInt(KEY_FREQUENCY)));
        }
    }

    private static IElement bar(IElementHelper elements, int percent, int colour, int shade, Component text) {
        return elements.progress(percent / 100.0F, text,
                elements.progressStyle().color(colour, shade).textColor(TEXT_COLOUR),
                BoxStyle.getNestedBox(), true);
    }

    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100);
    }

    /** Hundredths back to the {@code 1.35x} the machine's own window writes. */
    private static String multiplier(int hundredths) {
        return String.format(Locale.ROOT, "%.2f", hundredths / 100.0);
    }
}
