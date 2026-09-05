package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.ActualGenerators;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ActualGenerators.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ActualGenerators.MODID + ".main"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.OVERCLOCK_UPGRADE.toStack())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CORROSION_CELL.get());
                        output.accept(ModItems.HYDROSTATIC_GENERATOR.get());
                        output.accept(ModItems.PHOTOVORE.get());
                        output.accept(ModItems.IMPACT_DYNAMO.get());
                        output.accept(ModItems.SPAWNER_SIPHON.get());
                        output.accept(ModItems.ENCHANTMENT_COMBUSTOR.get());
                        output.accept(ModItems.RESONANCE_CRUSHER.get());
                        output.accept(ModItems.ENERGY_INJECTOR.get());
                        output.accept(ModItems.SURGE_BANK.get());
                        output.accept(ModItems.CRYSTAL_CHARGER.get());
                        output.accept(ModItems.FLUX_CRYSTAL.get());
                        output.accept(ModItems.FLUX_COUPLER.get());
                        output.accept(ModItems.MACHINE_FRAME.get());
                        output.accept(ModItems.IRON_DUST.get());
                        output.accept(ModItems.COPPER_DUST.get());
                        output.accept(ModItems.GOLD_DUST.get());
                        output.accept(ModItems.LOGIC_PORT.get());
                        output.accept(ModItems.LINK_RANGE_UPGRADE.get());
                        output.accept(ModItems.UNBOUND_LINK_CARD.get());
                        output.accept(ModItems.FILTER.get());
                        output.accept(ModItems.LINKING_TOOL.get());
                        output.accept(ModItems.CONFIG_CARD.get());
                        output.accept(ModItems.ENERGY_UPGRADE.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.OVERCLOCK_UPGRADE.get());
                        output.accept(ModItems.STACK_UPGRADE.get());
                        output.accept(ModItems.IRON_TIER_UPGRADE.get());
                        output.accept(ModItems.GOLD_TIER_UPGRADE.get());
                        output.accept(ModItems.DIAMOND_TIER_UPGRADE.get());
                        output.accept(ModItems.NETHERITE_TIER_UPGRADE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
