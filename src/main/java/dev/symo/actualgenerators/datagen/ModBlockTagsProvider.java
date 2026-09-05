package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output,
                                CompletableFuture<HolderLookup.Provider> registries,
                                ExistingFileHelper existingFileHelper) {
        super(output, registries, ActualGenerators.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Placed light sources a Photovore may eat. Deliberately conservative: no fire (it would
        // regrow off netherrack for free), no lava, no beacons or portals, nothing a player would
        // be upset to come back to. Packs can add or remove entries.
        tag(ModTags.Blocks.PHOTOVORE_FOOD).add(
                Blocks.TORCH, Blocks.WALL_TORCH,
                Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
                Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
                Blocks.LANTERN, Blocks.SOUL_LANTERN,
                Blocks.GLOWSTONE, Blocks.SEA_LANTERN,
                Blocks.SHROOMLIGHT, Blocks.JACK_O_LANTERN,
                Blocks.END_ROD,
                Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT);
    }
}
