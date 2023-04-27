package dev.symo.actualgenerators;

import dev.symo.actualgenerators.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ActualGenerators.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCreativeModeTabs {
    public static CreativeModeTab ACTUAL_GENERATORS_TAB;

    @SubscribeEvent
    public static void registerCreativeModeTabs(CreativeModeTabEvent.Register event) {
        ACTUAL_GENERATORS_TAB = event.registerCreativeModeTab(new ResourceLocation(ActualGenerators.MOD_ID, "actualgenerators_tab"),
                builder -> builder.icon(() -> new ItemStack(ModItems.ITEM_PIPE.get()))
                        .title(Component.translatable("creativemodetab.actualgenerators_tab")));
    }
}
