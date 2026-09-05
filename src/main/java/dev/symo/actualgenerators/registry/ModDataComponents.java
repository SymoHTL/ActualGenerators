package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.logistics.PadSnapshot;
import dev.symo.actualgenerators.ActualGenerators;
import com.mojang.serialization.Codec;
import dev.symo.actualgenerators.logistics.FilterContents;
import dev.symo.actualgenerators.machine.MachineConfigSnapshot;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ActualGenerators.MODID);

    /** The machine configuration a config card is currently holding. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MachineConfigSnapshot>> MACHINE_CONFIG =
            DATA_COMPONENTS.registerComponentType("machine_config", builder -> builder
                    .persistent(MachineConfigSnapshot.CODEC)
                    .networkSynchronized(MachineConfigSnapshot.STREAM_CODEC));

    /**
     * FE held by an item.
     *
     * <p>A long, because a late-game crystal outgrows an int the same way a late-game buffer does.
     * The FE item capability a crystal exposes is still the standard one; it simply clamps its
     * view, exactly as the machine buffers do. Two crystals holding the same amount carry
     * identical components, which is what lets them stack.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> ENERGY =
            DATA_COMPONENTS.registerComponentType("energy", builder -> builder
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** The slots an item carries of its own -- the flux coupler's crystals and its upgrade. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> SLOTS =
            DATA_COMPONENTS.registerComponentType("slots", builder -> builder
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** Whether a toggleable item is currently doing its job. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ACTIVE =
            DATA_COMPONENTS.registerComponentType("active", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** The two lists a Filter item holds. A blank filter carries none, so blank ones stack. */
    /** A whole pad on a config card: links, kinds, filters, upgrades, label and network. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PadSnapshot>> PAD_CONFIG =
            DATA_COMPONENTS.registerComponentType("pad_config",
                    builder -> builder.persistent(PadSnapshot.CODEC).networkSynchronized(PadSnapshot.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterContents>> FILTER =
            DATA_COMPONENTS.registerComponentType("filter", builder -> builder
                    .persistent(FilterContents.CODEC)
                    .networkSynchronized(FilterContents.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
