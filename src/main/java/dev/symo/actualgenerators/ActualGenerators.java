package dev.symo.actualgenerators;

import com.mojang.logging.LogUtils;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModCreativeTabs;
import dev.symo.actualgenerators.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(ActualGenerators.MODID)
public class ActualGenerators {
    public static final String MODID = "actualgenerators";
    public static final Logger LOGGER = LogUtils.getLogger();

    // FML injects the mod event bus and the mod container based on the parameter types.
    public ActualGenerators(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup", MODID);
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("{} client setup", MODID);
        }
    }
}
