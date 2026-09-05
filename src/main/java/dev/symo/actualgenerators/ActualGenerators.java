package dev.symo.actualgenerators;

import com.mojang.logging.LogUtils;
import dev.symo.actualgenerators.config.ClientConfig;
import dev.symo.actualgenerators.config.ServerConfig;
import dev.symo.actualgenerators.registry.ModBlockEntities;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModCreativeTabs;
import dev.symo.actualgenerators.registry.ModDataComponents;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.registry.ModMenus;
import dev.symo.actualgenerators.registry.ModRecipes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

@Mod(ActualGenerators.MODID)
public class ActualGenerators {
    public static final String MODID = "actualgenerators";
    public static final Logger LOGGER = LogUtils.getLogger();

    // FML injects the mod event bus and the mod container based on the parameter types.
    public ActualGenerators(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
        modEventBus.addListener(ModItems::registerCapabilities);
        modEventBus.addListener(ActualGenerators::onConfigLoad);
        modEventBus.addListener(ActualGenerators::onConfigReload);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup", MODID);
    }

    private static void onConfigLoad(final ModConfigEvent.Loading event) {
        ServerConfig.invalidate();
    }

    private static void onConfigReload(final ModConfigEvent.Reloading event) {
        ServerConfig.invalidate();
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("{} client setup", MODID);
        }
    }
}
