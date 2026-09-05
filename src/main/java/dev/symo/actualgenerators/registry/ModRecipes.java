package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.recipe.CrushingRecipe;
import dev.symo.actualgenerators.recipe.FilterCopyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe types and serializers for the machines that process items. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ActualGenerators.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ActualGenerators.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<CrushingRecipe>> CRUSHING =
            RECIPE_TYPES.register("crushing", RecipeType::simple);

    public static final DeferredHolder<RecipeSerializer<?>, CrushingRecipe.Serializer> CRUSHING_SERIALIZER =
            RECIPE_SERIALIZERS.register("crushing", CrushingRecipe.Serializer::new);

    /** Filters copied onto blanks and blanked again, in an ordinary crafting grid. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<FilterCopyRecipe>> FILTER_COPY =
            RECIPE_SERIALIZERS.register("filter_copy", () -> new SimpleCraftingRecipeSerializer<>(FilterCopyRecipe::new));

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
