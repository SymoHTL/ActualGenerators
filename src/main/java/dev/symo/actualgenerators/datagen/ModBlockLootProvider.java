package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * Machines drop themselves. Their contents are dropped by the block entity instead, so upgrades
 * and half-finished copper are never lost to a pickaxe.
 */
public class ModBlockLootProvider extends BlockLootSubProvider {

    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.CORROSION_CELL.get());
        dropSelf(ModBlocks.HYDROSTATIC_GENERATOR.get());
        dropSelf(ModBlocks.GEOTHERMAL_TAP.get());
        dropSelf(ModBlocks.PHOTOVORE.get());
        dropSelf(ModBlocks.IMPACT_DYNAMO.get());
        dropSelf(ModBlocks.SPAWNER_SIPHON.get());
        dropSelf(ModBlocks.ENCHANTMENT_COMBUSTOR.get());
        dropSelf(ModBlocks.SURGE_BANK.get());
        dropSelf(ModBlocks.ENERGY_INJECTOR.get());
        dropSelf(ModBlocks.CRYSTAL_CHARGER.get());
        dropSelf(ModBlocks.RESONANCE_CRUSHER.get());
        dropSelf(ModBlocks.LOGIC_PORT.get());
        dropSelf(ModBlocks.MACHINE_CASING.get());
        dropSelf(ModBlocks.GEOTHERMAL_CASING.get());
        dropSelf(ModBlocks.ITEM_HATCH.get());
        dropSelf(ModBlocks.ENERGY_HATCH.get());
        dropSelf(ModBlocks.REDSTONE_HATCH.get());
        dropSelf(ModBlocks.FLUID_HATCH.get());
        dropSelf(ModBlocks.ANNIHILATION_FURNACE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }
}
