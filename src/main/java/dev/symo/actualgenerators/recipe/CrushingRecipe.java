package dev.symo.actualgenerators.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.symo.actualgenerators.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * One thing the Resonance Crusher knows how to shake apart.
 *
 * <p>A recipe says what goes in, how many of it, and what comes out. It deliberately does not say
 * what the bonus for a tuned crusher is worth, or how much power the machine draws: those are
 * balance, and balance lives in the server config where a pack can move it.
 *
 * <p>{@code ticks} is the one timing a recipe may set for itself, because a block of cobble is not
 * an iron ore; zero means "whatever the config says".
 */
public record CrushingRecipe(Ingredient input, int inputCount, ItemStack result, int ticks)
        implements Recipe<SingleRecipeInput> {

    /**
     * The frequency this recipe resonates at, in the arbitrary units printed on the machine.
     *
     * <p>Derived from the recipe id rather than stored, so every recipe — including one a pack adds
     * tomorrow — has one, and it is the same number on every world and every client.
     */
    public static int frequencyOf(ResourceLocation id) {
        return 100 + Math.floorMod(id.hashCode(), 900);
    }

    /** Whether a stack could feed this recipe at all, ignoring how many of it there are. */
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    /**
     * Keeps these out of the vanilla recipe book, which has no category for them and says so in
     * the log once per recipe on every world load. JEI is where a crushing recipe is read.
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CRUSHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CRUSHING.get();
    }

    public static class Serializer implements RecipeSerializer<CrushingRecipe> {
        private static final MapCodec<CrushingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CrushingRecipe::input),
                Codec.INT.optionalFieldOf("count", 1).forGetter(CrushingRecipe::inputCount),
                ItemStack.CODEC.fieldOf("result").forGetter(CrushingRecipe::result),
                Codec.INT.optionalFieldOf("ticks", 0).forGetter(CrushingRecipe::ticks)
        ).apply(instance, CrushingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CrushingRecipe::input,
                        ByteBufCodecs.VAR_INT, CrushingRecipe::inputCount,
                        ItemStack.STREAM_CODEC, CrushingRecipe::result,
                        ByteBufCodecs.VAR_INT, CrushingRecipe::ticks,
                        CrushingRecipe::new);

        @Override
        public MapCodec<CrushingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
