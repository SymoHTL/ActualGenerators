package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.registry.ModItems;
import dev.symo.actualgenerators.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * The common tags the mod's own materials belong to.
 *
 * <p>Dusts go under {@code c:dusts/<metal>} rather than being referenced by item, so a pack that
 * already has another mod's iron dust smelts either one, and another mod's furnace recipe takes
 * ours.
 */
public class ModItemTagsProvider extends ItemTagsProvider {

    public ModItemTagsProvider(PackOutput output,
                               CompletableFuture<HolderLookup.Provider> registries,
                               CompletableFuture<TagsProvider.TagLookup<Block>> blockTags,
                               ExistingFileHelper existingFileHelper) {
        super(output, registries, blockTags, ActualGenerators.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        dust(ModTags.Items.DUSTS_IRON, ModItems.IRON_DUST.get());
        dust(ModTags.Items.DUSTS_COPPER, ModItems.COPPER_DUST.get());
        dust(ModTags.Items.DUSTS_GOLD, ModItems.GOLD_DUST.get());
    }

    /** A dust belongs to its own metal's tag and to the general one. */
    private void dust(TagKey<Item> tag, Item item) {
        tag(tag).add(item);
        tag(Tags.Items.DUSTS).addTag(tag);
    }
}
