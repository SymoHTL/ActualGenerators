package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.registry.ModFluids;
import dev.symo.actualgenerators.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class ModFluidTagsProvider extends FluidTagsProvider {

    public ModFluidTagsProvider(PackOutput output,
                                CompletableFuture<HolderLookup.Provider> registries,
                                ExistingFileHelper existingFileHelper) {
        super(output, registries, ActualGenerators.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Corium burns and glows like lava, so it is lava to everything that asks.
        tag(FluidTags.LAVA).add(ModFluids.CORIUM.get(), ModFluids.CORIUM_FLOWING.get());
        // What heats an Annihilation Furnace: corium fully, lava poorly. Packs add their own.
        tag(ModTags.Fluids.ANNIHILATION_HEAT_STRONG).add(ModFluids.CORIUM.get(), ModFluids.CORIUM_FLOWING.get());
        tag(ModTags.Fluids.ANNIHILATION_HEAT_WEAK).addTag(FluidTags.LAVA);
    }
}
