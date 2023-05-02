package dev.symo.actualgenerators.screen;

import dev.symo.actualgenerators.ActualGenerators;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ActualGenerators.MOD_ID);

    private static <T extends AbstractContainerMenu> RegistryObject<MenuType<T>> registerMenuType(IContainerFactory<T> factory, String name) {
        return MENUS.register(name, () -> IForgeMenuType.create(factory));
    }    public static final RegistryObject<MenuType<ItemPipeBlockMenu>> ITEM_PIPE_BLOCK_MENU =
            registerMenuType(ItemPipeBlockMenu::new, "item_pipe_block_menu");

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }


}
