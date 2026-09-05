package dev.symo.actualgenerators.registry;

import dev.symo.actualgenerators.ActualGenerators;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tags the mod defines. Anything a machine treats as food or fuel goes through one of these. */
public final class ModTags {

    private ModTags() {
    }

    /** Common tags the mod's own materials live under, so other mods' equivalents work. */
    public static final class Items {
        public static final TagKey<Item> DUSTS_IRON = common("dusts/iron");
        public static final TagKey<Item> DUSTS_COPPER = common("dusts/copper");
        public static final TagKey<Item> DUSTS_GOLD = common("dusts/gold");

        private Items() {
        }

        private static TagKey<Item> common(String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", name));
        }
    }

    public static final class Blocks {
        /**
         * What a Photovore will graze on. A whitelist rather than "anything that glows", so it
         * never eats a beacon, a portal, or a lava lake — and so packs can decide what counts.
         */
        public static final TagKey<Block> PHOTOVORE_FOOD = tag("photovore_food");

        private Blocks() {
        }

        private static TagKey<Block> tag(String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, name));
        }
    }
}
