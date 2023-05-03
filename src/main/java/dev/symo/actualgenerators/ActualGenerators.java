package dev.symo.actualgenerators;

import com.mojang.logging.LogUtils;
import dev.symo.actualgenerators.block.ModBlocks;
import dev.symo.actualgenerators.block.entity.ModBlockEntities;
import dev.symo.actualgenerators.block.entity.pipe.PipeRenderer;
import dev.symo.actualgenerators.item.ModItems;
import dev.symo.actualgenerators.net.ModMessages;
import dev.symo.actualgenerators.screen.ItemPipeBlockScreen;
import dev.symo.actualgenerators.screen.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ActualGenerators.MOD_ID)
public class ActualGenerators {
    public static final String MOD_ID = "actualgenerators";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ActualGenerators() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        ModMenuTypes.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
                    ModMessages.register();
                }
        );
    }

    private void addCreative(CreativeModeTabEvent.BuildContents event) {
        if (event.getTab() == CreativeModeTabs.INGREDIENTS) {
            //event.accept(ModItems.ITEM_PIPE);
            event.accept(ModBlocks.ITEM_PIPE_BLOCK.get().asItem());
        }

        if (event.getTab() == ModCreativeModeTabs.ACTUAL_GENERATORS_TAB) {
            //event.accept(ModItems.ITEM_PIPE);
            event.accept(ModBlocks.ITEM_PIPE_BLOCK.get().asItem());
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // register custom renderer
            BlockEntityRenderers.register(ModBlockEntities.ITEM_PIPE_BLOCK_ENTITY.get(), PipeRenderer::new);


            MenuScreens.register(ModMenuTypes.ITEM_PIPE_BLOCK_MENU.get(), ItemPipeBlockScreen::new);
        }
    }
}
