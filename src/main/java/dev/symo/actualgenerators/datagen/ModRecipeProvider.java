package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.recipe.CrushingRecipe;
import dev.symo.actualgenerators.recipe.FilterCopyRecipe;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.registry.ModTags;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * What the Resonance Crusher knows how to take apart.
 *
 * <p>Ore recipes are written against the common {@code c:ores/*} tags rather than vanilla blocks,
 * so another mod's iron ore crushes exactly like vanilla's without either mod knowing about the
 * other. The numbers here are the plain yield; the extra a tuned crusher shakes loose is a config
 * value, not a recipe one.
 */
public class ModRecipeProvider extends RecipeProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // Metals go through dust, which is what lets raw ore be worth crushing at all: raw iron
        // into more raw iron would be a duplication bug wearing a machine costume, so it crushes
        // into a dust that smelts back into exactly one ingot.
        crushTag(output, "iron_ore", Tags.Items.ORES_IRON, ModItems.IRON_DUST.get(), 2);
        crushTag(output, "copper_ore", Tags.Items.ORES_COPPER, ModItems.COPPER_DUST.get(), 3);
        crushTag(output, "gold_ore", Tags.Items.ORES_GOLD, ModItems.GOLD_DUST.get(), 2);
        crushTag(output, "raw_iron", Tags.Items.RAW_MATERIALS_IRON, ModItems.IRON_DUST.get(), 2);
        crushTag(output, "raw_copper", Tags.Items.RAW_MATERIALS_COPPER, ModItems.COPPER_DUST.get(), 2);
        crushTag(output, "raw_gold", Tags.Items.RAW_MATERIALS_GOLD, ModItems.GOLD_DUST.get(), 2);
        crushTag(output, "coal_ore", Tags.Items.ORES_COAL, Items.COAL, 2);
        crushTag(output, "redstone_ore", Tags.Items.ORES_REDSTONE, Items.REDSTONE, 8);
        crushTag(output, "lapis_ore", Tags.Items.ORES_LAPIS, Items.LAPIS_LAZULI, 12);
        crushTag(output, "diamond_ore", Tags.Items.ORES_DIAMOND, Items.DIAMOND, 2);
        crushTag(output, "emerald_ore", Tags.Items.ORES_EMERALD, Items.EMERALD, 2);
        crushTag(output, "quartz_ore", Tags.Items.ORES_QUARTZ, Items.QUARTZ, 2);

        // And the gravel cycle, which is cheap, fast, and the thing a new crusher gets pointed at
        // first because a player always has cobble to spare.
        crush(output, "gravel", Ingredient.of(Items.COBBLESTONE), 1, new ItemStack(Items.GRAVEL), 40);
        crush(output, "sand", Ingredient.of(Items.GRAVEL), 1, new ItemStack(Items.SAND), 40);

        // And the way back: a dust is worth exactly one ingot, in either kind of furnace.
        smelt(output, ModTags.Items.DUSTS_IRON, Items.IRON_INGOT, "iron");
        smelt(output, ModTags.Items.DUSTS_COPPER, Items.COPPER_INGOT, "copper");
        smelt(output, ModTags.Items.DUSTS_GOLD, Items.GOLD_INGOT, "gold");

        crafting(output);
    }

    /** Dust to ingot, in a furnace and in a blast furnace, since a player will try both. */
    private void smelt(RecipeOutput output, TagKey<Item> dust, ItemLike ingot, String metal) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(dust), RecipeCategory.MISC, ingot, 0.7F, 200)
                .unlockedBy("has_dust", has(dust))
                .save(output, ResourceLocation.fromNamespaceAndPath(
                        ActualGenerators.MODID, "smelting/" + metal + "_ingot_from_dust"));
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(dust), RecipeCategory.MISC, ingot, 0.7F, 100)
                .unlockedBy("has_dust", has(dust))
                .save(output, ResourceLocation.fromNamespaceAndPath(
                        ActualGenerators.MODID, "blasting/" + metal + "_ingot_from_dust"));
    }

    /**
     * How the mod is built in survival.
     *
     * <p>Everything goes through one Machine Frame, so what a machine costs is the frame plus the
     * handful of things that say what it does — and rebalancing a whole tier is one recipe rather
     * than nine. Ingredients are common tags wherever a tag exists, so another mod's copper works.
     */
    private void crafting(RecipeOutput output) {
        // The casing. Eight ingots and a pinch of redstone: the reason a machine is an investment.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MACHINE_FRAME.get())
                .pattern("ICI")
                .pattern("CRC")
                .pattern("ICI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_copper", has(Tags.Items.INGOTS_COPPER))
                .save(output);

        // Generators: the frame in the middle, and around it what each one actually works on.
        machine(output, ModItems.CORROSION_CELL.get(), " C ", "CFC", " G ")
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.HYDROSTATIC_GENERATOR.get(), " G ", "IFI", " G ")
                .define('G', Tags.Items.GLASS_BLOCKS)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.PHOTOVORE.get(), " D ", "GFG", " D ")
                .define('D', Items.GLOWSTONE_DUST)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.IMPACT_DYNAMO.get(), " P ", "IFI", " P ")
                .define('P', Blocks.PISTON)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.SPAWNER_SIPHON.get(), " S ", "GFG", " S ")
                .define('S', Blocks.SOUL_SAND)
                .define('G', Tags.Items.INGOTS_GOLD)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.ENCHANTMENT_COMBUSTOR.get(), " B ", "LFL", " B ")
                .define('B', Items.BOOK)
                .define('L', Tags.Items.GEMS_LAPIS)
                .unlockedBy("has_frame", hasFrame())
                .save(output);

        // Multiblocks. The casing is made by the four because a box needs dozens; a hatch is a
        // casing with the thing it lets through; the controller is the late-game frame recipe.
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModItems.MACHINE_CASING.get(), 4)
                .pattern("III")
                .pattern("I I")
                .pattern("III")
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        // The tap's plate: casing with copper through it round a block of magma, four at a time.
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModItems.GEOTHERMAL_CASING.get(), 4)
                .pattern("PCP")
                .pattern("CMC")
                .pattern("PCP")
                .define('P', Tags.Items.INGOTS_COPPER)
                .define('C', ModItems.MACHINE_CASING.get())
                .define('M', Items.MAGMA_BLOCK)
                .unlockedBy("has_casing", has(ModItems.MACHINE_CASING.get()))
                .save(output);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, ModItems.ITEM_HATCH.get())
                .requires(ModItems.MACHINE_CASING.get())
                .requires(Tags.Items.CHESTS_WOODEN)
                .unlockedBy("has_casing", has(ModItems.MACHINE_CASING.get()))
                .save(output);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, ModItems.ENERGY_HATCH.get())
                .requires(ModItems.MACHINE_CASING.get())
                .requires(Tags.Items.STORAGE_BLOCKS_REDSTONE)
                .unlockedBy("has_casing", has(ModItems.MACHINE_CASING.get()))
                .save(output);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, ModItems.REDSTONE_HATCH.get())
                .requires(ModItems.MACHINE_CASING.get())
                .requires(Items.REDSTONE_TORCH)
                .unlockedBy("has_casing", has(ModItems.MACHINE_CASING.get()))
                .save(output);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, ModItems.FLUID_HATCH.get())
                .requires(ModItems.MACHINE_CASING.get())
                .requires(Items.BUCKET)
                .unlockedBy("has_casing", has(ModItems.MACHINE_CASING.get()))
                .save(output);
        // A rod with a hot bulb: the probe is what a player makes before the first casing.
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.THERMAL_PROBE.get())
                .pattern("R")
                .pattern("C")
                .pattern("I")
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_copper", has(Tags.Items.INGOTS_COPPER))
                .save(output);
        // Magma for the heat, copper for the pipe: the tap is a mid-game frame recipe.
        machine(output, ModItems.GEOTHERMAL_TAP.get(), " M ", "CFC", " M ")
                .define('M', Items.MAGMA_BLOCK)
                .define('C', Tags.Items.INGOTS_COPPER)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.ANNIHILATION_FURNACE.get(), " O ", "NFN", " O ")
                .define('O', Tags.Items.OBSIDIANS)
                .define('N', Tags.Items.INGOTS_NETHERITE)
                .unlockedBy("has_frame", hasFrame())
                .save(output);

        // Storage and processing cost blocks rather than ingots: they are the step up.
        machine(output, ModItems.SURGE_BANK.get(), " R ", "CFC", " R ")
                .define('R', Blocks.REDSTONE_BLOCK)
                .define('C', Items.COPPER_BLOCK)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.CRYSTAL_CHARGER.get(), " A ", "GFG", " A ")
                .define('A', Tags.Items.GEMS_AMETHYST)
                .define('G', Tags.Items.INGOTS_GOLD)
                .unlockedBy("has_frame", hasFrame())
                .save(output);
        machine(output, ModItems.RESONANCE_CRUSHER.get(), " I ", "PFP", " L ")
                .define('I', Items.IRON_BLOCK)
                .define('P', Blocks.PISTON)
                .define('L', Items.FLINT)
                .unlockedBy("has_frame", hasFrame())
                .save(output);

        // Upgrades are made by the handful, so they stay to a single row.
        row(output, ModItems.ENERGY_UPGRADE.get())
                .define('O', Tags.Items.DUSTS_REDSTONE)
                .define('I', Tags.Items.INGOTS_COPPER)
                .unlockedBy("has_copper", has(Tags.Items.INGOTS_COPPER))
                .save(output);
        row(output, ModItems.SPEED_UPGRADE.get())
                .define('O', Items.SUGAR)
                .define('I', Tags.Items.INGOTS_GOLD)
                .unlockedBy("has_gold", has(Tags.Items.INGOTS_GOLD))
                .save(output);
        row(output, ModItems.OVERCLOCK_UPGRADE.get())
                .define('O', Tags.Items.GEMS_AMETHYST)
                .define('I', Blocks.REDSTONE_BLOCK)
                .unlockedBy("has_amethyst", has(Tags.Items.GEMS_AMETHYST))
                .save(output);
        row(output, ModItems.STACK_UPGRADE.get())
                .define('O', Items.CHEST)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_iron", has(Tags.Items.INGOTS_IRON))
                .save(output);
        // Tiers: a plus of the material round the rung below, the frame at the bottom of the
        // ladder, so the metal in an iron tier is never lost when a machine goes up to gold.
        tier(output, ModItems.IRON_TIER_UPGRADE.get(), Tags.Items.INGOTS_IRON, ModItems.MACHINE_FRAME.get());
        tier(output, ModItems.GOLD_TIER_UPGRADE.get(), Tags.Items.INGOTS_GOLD, ModItems.IRON_TIER_UPGRADE.get());
        tier(output, ModItems.DIAMOND_TIER_UPGRADE.get(), Tags.Items.GEMS_DIAMOND, ModItems.GOLD_TIER_UPGRADE.get());
        tier(output, ModItems.NETHERITE_TIER_UPGRADE.get(), Tags.Items.INGOTS_NETHERITE, ModItems.DIAMOND_TIER_UPGRADE.get());
        row(output, ModItems.CONFIG_CARD.get())
                .define('O', Items.PAPER)
                .define('I', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_redstone", has(Tags.Items.DUSTS_REDSTONE))
                .save(output);
        guide(output);

        // The tool, the filter, and the two items that carry energy around.
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.LINKING_TOOL.get())
                .pattern("I I")
                .pattern(" I ")
                .pattern(" A ")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('A', Tags.Items.GEMS_AMETHYST)
                .unlockedBy("has_amethyst", has(Tags.Items.GEMS_AMETHYST))
                .save(output);

        row(output, ModItems.FILTER.get())
                .define('O', Tags.Items.INGOTS_IRON)
                .define('I', Items.PAPER)
                .unlockedBy("has_iron", has(Tags.Items.INGOTS_IRON))
                .save(output);
        // A set filter with a blank one makes two of the set one; a set filter alone comes out blank.
        SpecialRecipeBuilder.special(FilterCopyRecipe::new)
                .save(output, ActualGenerators.MODID + ":filter_copy");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.FLUX_CRYSTAL.get())
                .pattern(" A ")
                .pattern("ARA")
                .pattern(" A ")
                .define('A', Tags.Items.GEMS_AMETHYST)
                .define('R', Blocks.REDSTONE_BLOCK)
                .unlockedBy("has_amethyst", has(Tags.Items.GEMS_AMETHYST))
                .save(output);

        machine(output, ModItems.ENERGY_INJECTOR.get(), "ARA", "RFR", "ARA")
                .define('A', Tags.Items.GEMS_AMETHYST)
                .define('R', Blocks.REDSTONE_BLOCK)
                .unlockedBy("has_frame", hasFrame())
                .save(output);

        // Logistics. Ports are made two at a time because a base needs one per machine face,
        // and a network that costs a machine frame per end would never get built.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LOGIC_PORT.get(), 2)
                .pattern(" A ")
                .pattern("IRI")
                .define('A', Tags.Items.GEMS_AMETHYST)
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_amethyst", has(Tags.Items.GEMS_AMETHYST))
                .save(output);

        row(output, ModItems.LINK_RANGE_UPGRADE.get())
                .define('O', Items.ENDER_PEARL)
                .define('I', Tags.Items.INGOTS_GOLD)
                .unlockedBy("has_pearl", has(Items.ENDER_PEARL))
                .save(output);

        // The trade-off upgrade costs what a trade-off upgrade should: this is not one more tier.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.UNBOUND_LINK_CARD.get())
                .pattern(" E ")
                .pattern("ARA")
                .pattern(" E ")
                .define('E', Items.ENDER_EYE)
                .define('A', Blocks.AMETHYST_BLOCK)
                .define('R', ModItems.LINK_RANGE_UPGRADE.get())
                .unlockedBy("has_range_upgrade", has(ModItems.LINK_RANGE_UPGRADE.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.FLUX_COUPLER.get())
                .pattern(" C ")
                .pattern("IXI")
                .pattern(" I ")
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('I', Tags.Items.INGOTS_IRON)
                .define('X', ModItems.FLUX_CRYSTAL.get())
                .unlockedBy("has_crystal", has(ModItems.FLUX_CRYSTAL.get()))
                .save(output);
    }

    /** A machine: three rows with the frame already keyed to F in the middle. */
    private static ShapedRecipeBuilder machine(RecipeOutput output, ItemLike result,
                                               String top, String middle, String bottom) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, result)
                .pattern(top)
                .pattern(middle)
                .pattern(bottom)
                .define('F', ModItems.MACHINE_FRAME.get());
    }

    /** A one-row recipe: two of the outer thing either side of one of the inner. */
    private static ShapedRecipeBuilder row(RecipeOutput output, ItemLike result) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result).pattern("OIO");
    }

    /**
     * The in-game guide: GuideME's book pointed at our pages, from a book and a copper ingot.
     *
     * <p>GuideME is a runtime neighbour, never a compile-time one, so the book and its component
     * are looked up by id. Datagen without GuideME on the classpath writes no recipe and says so;
     * a game without GuideME never loads the recipe, which is what the condition is for.
     */
    private static void guide(RecipeOutput output) {
        Optional<Item> book = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("guideme:guide"));
        Optional<DataComponentType<?>> guideId =
                BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ResourceLocation.parse("guideme:guide_id"));
        if (book.isEmpty() || guideId.isEmpty()) {
            LOGGER.warn("GuideME is not on the datagen classpath, so no recipe for the guide was written");
            return;
        }
        ItemStack result = new ItemStack(book.get());
        @SuppressWarnings("unchecked")
        DataComponentType<ResourceLocation> type = (DataComponentType<ResourceLocation>) guideId.get();
        result.set(type, ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "guide"));
        new ShapelessRecipeBuilder(RecipeCategory.MISC, result)
                .requires(Items.BOOK)
                .requires(Tags.Items.INGOTS_COPPER)
                .unlockedBy("has_frame", hasFrame())
                .save(output.withConditions(new ModLoadedCondition("guideme")),
                        ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "guide"));
    }

    /** A tier: four of the material round the rung below it. */
    private static void tier(RecipeOutput output, ItemLike result, TagKey<Item> material, ItemLike core) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern(" M ")
                .pattern("MCM")
                .pattern(" M ")
                .define('M', material)
                .define('C', core)
                .unlockedBy("has_core", has(core))
                .save(output);
    }

    private static net.minecraft.advancements.Criterion<?> hasFrame() {
        return has(ModItems.MACHINE_FRAME.get());
    }


    private static void crushTag(RecipeOutput output, String name, TagKey<Item> ore, ItemLike result, int count) {
        crush(output, name, Ingredient.of(ore), 1, new ItemStack(result, count), 0);
    }

    private static void crush(RecipeOutput output,
                              String name,
                              Ingredient input,
                              int inputCount,
                              ItemStack result,
                              int ticks) {
        output.accept(
                ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "crushing/" + name),
                new CrushingRecipe(input, inputCount, result, ticks),
                null);
    }
}
