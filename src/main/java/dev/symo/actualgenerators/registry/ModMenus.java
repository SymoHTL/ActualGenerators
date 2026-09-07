package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.menu.RedstoneHatchMenu;
import dev.symo.actualgenerators.menu.GeothermalTapMenu;
import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.menu.CorrosionCellMenu;
import dev.symo.actualgenerators.menu.EnchantmentCombustorMenu;
import dev.symo.actualgenerators.menu.CrystalChargerMenu;
import dev.symo.actualgenerators.menu.FilterMenu;
import dev.symo.actualgenerators.menu.FluxCouplerMenu;
import dev.symo.actualgenerators.menu.HydrostaticGeneratorMenu;
import dev.symo.actualgenerators.menu.ImpactDynamoMenu;
import dev.symo.actualgenerators.menu.LinkPortMenu;
import dev.symo.actualgenerators.menu.NetworkOverviewMenu;
import dev.symo.actualgenerators.menu.NetworkPickerMenu;
import dev.symo.actualgenerators.menu.PhotovoreMenu;
import dev.symo.actualgenerators.menu.ResonanceCrusherMenu;
import dev.symo.actualgenerators.menu.SpawnerSiphonMenu;
import dev.symo.actualgenerators.menu.EnergyInjectorMenu;
import dev.symo.actualgenerators.menu.SurgeBankMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.symo.actualgenerators.menu.AnnihilationFurnaceMenu;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ActualGenerators.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CorrosionCellMenu>> CORROSION_CELL =
            MENUS.register("corrosion_cell", () -> IMenuTypeExtension.create(CorrosionCellMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<HydrostaticGeneratorMenu>> HYDROSTATIC_GENERATOR =
            MENUS.register("hydrostatic_generator", () -> IMenuTypeExtension.create(HydrostaticGeneratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<GeothermalTapMenu>> GEOTHERMAL_TAP =
            MENUS.register("geothermal_tap", () -> IMenuTypeExtension.create(GeothermalTapMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<RedstoneHatchMenu>> REDSTONE_HATCH =
            MENUS.register("redstone_hatch", () -> IMenuTypeExtension.create(RedstoneHatchMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PhotovoreMenu>> PHOTOVORE =
            MENUS.register("photovore", () -> IMenuTypeExtension.create(PhotovoreMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ImpactDynamoMenu>> IMPACT_DYNAMO =
            MENUS.register("impact_dynamo", () -> IMenuTypeExtension.create(ImpactDynamoMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SpawnerSiphonMenu>> SPAWNER_SIPHON =
            MENUS.register("spawner_siphon", () -> IMenuTypeExtension.create(SpawnerSiphonMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EnchantmentCombustorMenu>> ENCHANTMENT_COMBUSTOR =
            MENUS.register("enchantment_combustor", () -> IMenuTypeExtension.create(EnchantmentCombustorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AnnihilationFurnaceMenu>> ANNIHILATION_FURNACE =
            MENUS.register("annihilation_furnace", () -> IMenuTypeExtension.create(AnnihilationFurnaceMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EnergyInjectorMenu>> ENERGY_INJECTOR =
            MENUS.register("energy_injector",
                    () -> IMenuTypeExtension.create(EnergyInjectorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SurgeBankMenu>> SURGE_BANK =
            MENUS.register("surge_bank", () -> IMenuTypeExtension.create(SurgeBankMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CrystalChargerMenu>> CRYSTAL_CHARGER =
            MENUS.register("crystal_charger", () -> IMenuTypeExtension.create(CrystalChargerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FluxCouplerMenu>> FLUX_COUPLER =
            MENUS.register("flux_coupler", () -> IMenuTypeExtension.create(FluxCouplerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ResonanceCrusherMenu>> RESONANCE_CRUSHER =
            MENUS.register("resonance_crusher", () -> IMenuTypeExtension.create(ResonanceCrusherMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LinkPortMenu>> LOGIC_PORT =
            MENUS.register("logic_port", () -> IMenuTypeExtension.create(LinkPortMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FilterMenu>> FILTER =
            MENUS.register("filter", () -> IMenuTypeExtension.create(FilterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<NetworkPickerMenu>> NETWORK_PICKER =
            MENUS.register("network_picker", () -> IMenuTypeExtension.create(NetworkPickerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<NetworkOverviewMenu>> NETWORK_OVERVIEW =
            MENUS.register("network_overview", () -> IMenuTypeExtension.create(NetworkOverviewMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
