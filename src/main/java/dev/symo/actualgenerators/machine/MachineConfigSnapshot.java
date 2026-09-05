package dev.symo.actualgenerators.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * A copy of a machine's configuration, as carried by a config card.
 *
 * <p>Side modes are stored relative to the machine's facing, so pasting onto a machine that
 * points elsewhere still means the same thing.
 */
public record MachineConfigSnapshot(List<Integer> sideModes, int redstoneMode, List<String> learned) {

    public static final Codec<MachineConfigSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().fieldOf("side_modes").forGetter(MachineConfigSnapshot::sideModes),
            Codec.INT.fieldOf("redstone_mode").forGetter(MachineConfigSnapshot::redstoneMode),
            Codec.STRING.listOf().optionalFieldOf("learned", List.of()).forGetter(MachineConfigSnapshot::learned)
    ).apply(instance, MachineConfigSnapshot::new));

    public static final StreamCodec<ByteBuf, MachineConfigSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MachineConfigSnapshot::sideModes,
            ByteBufCodecs.VAR_INT, MachineConfigSnapshot::redstoneMode,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MachineConfigSnapshot::learned,
            MachineConfigSnapshot::new);

    /** Reads the current configuration off a machine. */
    public static MachineConfigSnapshot copyOf(MachineBlockEntity machine) {
        return new MachineConfigSnapshot(
                machine.sideConfig().toOrdinals(),
                machine.redstoneMode().ordinal(),
                machine.learned());
    }

    /** Applies this configuration to a machine. */
    public void applyTo(MachineBlockEntity machine) {
        machine.sideConfig().loadOrdinals(sideModes);
        machine.setRedstoneMode(RedstoneMode.byOrdinal(redstoneMode));
        machine.applyLearned(learned);
        machine.invalidateCapabilitiesOnSideChange();
    }
}
