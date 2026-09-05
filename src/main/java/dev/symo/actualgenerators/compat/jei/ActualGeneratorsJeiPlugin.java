package dev.symo.actualgenerators.compat.jei;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.registry.ModBlocks;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Teaches JEI about the recipes only this mod's machines can run.
 *
 * <p>Loaded by JEI itself, so nothing here runs when JEI is absent — which is why the mod has no
 * hard dependency on it.
 */
@JeiPlugin
public class ActualGeneratorsJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrushingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    /** Dragging an ingredient out of JEI onto a filter slot is how a filter gets filled. */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(dev.symo.actualgenerators.client.FilterScreen.class,
                new FilterGhostHandler());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        registration.addRecipes(CrushingCategory.TYPE,
                level.getRecipeManager().getAllRecipesFor(ModRecipes.CRUSHING.get()));

        // The items whose point is not obvious from a recipe.
        registration.addItemStackInfo(ModItems.FLUX_CRYSTAL.get().getDefaultInstance(),
                Component.translatable("jei.actualgenerators.flux_crystal"));
        registration.addItemStackInfo(ModItems.LINKING_TOOL.get().getDefaultInstance(),
                Component.translatable("jei.actualgenerators.linking_tool"));
        registration.addItemStackInfo(ModItems.FILTER.get().getDefaultInstance(),
                Component.translatable("jei.actualgenerators.filter"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.RESONANCE_CRUSHER.get(), CrushingCategory.TYPE);
    }
}
