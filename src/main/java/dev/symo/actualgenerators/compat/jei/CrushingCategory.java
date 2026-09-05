package dev.symo.actualgenerators.compat.jei;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.recipe.CrushingRecipe;
import dev.symo.actualgenerators.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Arrays;
import java.util.List;

/**
 * The Resonance Crusher's page in JEI.
 *
 * <p>It shows the one thing a crusher recipe has that an ordinary furnace recipe does not: the
 * frequency the material rings at. That number is derived from the recipe id, so a pack that adds
 * its own crushing recipe gets a page with a frequency on it without doing anything.
 */
public class CrushingCategory implements IRecipeCategory<RecipeHolder<CrushingRecipe>> {
    public static final RecipeType<RecipeHolder<CrushingRecipe>> TYPE = RecipeType.createRecipeHolderType(
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "crushing"));

    private static final int WIDTH = 116;
    private static final int HEIGHT = 42;
    private static final int INPUT_X = 4;
    private static final int OUTPUT_X = 60;
    private static final int SLOT_Y = 4;
    private static final int ARROW_X = 28;
    private static final int ARROW_Y = 5;
    private static final int TEXT_Y = 28;
    private static final int TEXT_COLOUR = 0x404040;

    /** How long the arrow takes to fill, purely cosmetic — the real time is upgrade-dependent. */
    private static final int ARROW_TICKS = 60;

    private final IDrawable icon;
    private final IDrawable arrow;

    public CrushingCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemLike(ModBlocks.RESONANCE_CRUSHER.get());
        this.arrow = helper.createAnimatedRecipeArrow(ARROW_TICKS);
    }

    @Override
    public RecipeType<RecipeHolder<CrushingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.actualgenerators.resonance_crusher");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<CrushingRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CrushingRecipe> holder, IFocusGroup focuses) {
        CrushingRecipe recipe = holder.value();

        // The ingredient itself carries no count, so stamp the recipe's on every stack: a player
        // reading the page needs to see that it takes three of something, not one.
        List<ItemStack> inputs = Arrays.stream(recipe.input().getItems())
                .map(stack -> stack.copyWithCount(recipe.inputCount()))
                .toList();

        builder.addInputSlot(INPUT_X, SLOT_Y).setStandardSlotBackground().addItemStacks(inputs);
        builder.addOutputSlot(OUTPUT_X, SLOT_Y).setOutputSlotBackground().addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<CrushingRecipe> holder,
                     IRecipeSlotsView slots,
                     GuiGraphics graphics,
                     double mouseX,
                     double mouseY) {
        arrow.draw(graphics, ARROW_X, ARROW_Y);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("gui.actualgenerators.frequency", CrushingRecipe.frequencyOf(holder.id())),
                INPUT_X, TEXT_Y, TEXT_COLOUR, false);
    }
}
