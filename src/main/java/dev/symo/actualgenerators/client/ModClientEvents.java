package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ActualGenerators.MODID, value = Dist.CLIENT)
public final class ModClientEvents {

    private ModClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CORROSION_CELL.get(), CorrosionCellScreen::new);
        event.register(ModMenus.HYDROSTATIC_GENERATOR.get(), HydrostaticGeneratorScreen::new);
        event.register(ModMenus.PHOTOVORE.get(), PhotovoreScreen::new);
        event.register(ModMenus.IMPACT_DYNAMO.get(), ImpactDynamoScreen::new);
        event.register(ModMenus.SPAWNER_SIPHON.get(), SpawnerSiphonScreen::new);
        event.register(ModMenus.ENCHANTMENT_COMBUSTOR.get(), EnchantmentCombustorScreen::new);
        event.register(ModMenus.RESONANCE_CRUSHER.get(), ResonanceCrusherScreen::new);
        event.register(ModMenus.SURGE_BANK.get(), SurgeBankScreen::new);
        event.register(ModMenus.CRYSTAL_CHARGER.get(), CrystalChargerScreen::new);
        event.register(ModMenus.FLUX_COUPLER.get(), FluxCouplerScreen::new);
        event.register(ModMenus.LOGIC_PORT.get(), LinkPortScreen::new);
        event.register(ModMenus.ENERGY_INJECTOR.get(), EnergyInjectorScreen::new);
        event.register(ModMenus.FILTER.get(), FilterScreen::new);
        event.register(ModMenus.NETWORK_PICKER.get(), NetworkPickerScreen::new);
        event.register(ModMenus.NETWORK_OVERVIEW.get(), NetworkOverviewScreen::new);
    }
}
