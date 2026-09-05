package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ActualGenerators.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.ENERGY_UPGRADE.get());
        basicItem(ModItems.SPEED_UPGRADE.get());
        basicItem(ModItems.OVERCLOCK_UPGRADE.get());
        basicItem(ModItems.STACK_UPGRADE.get());
        basicItem(ModItems.IRON_TIER_UPGRADE.get());
        basicItem(ModItems.GOLD_TIER_UPGRADE.get());
        basicItem(ModItems.DIAMOND_TIER_UPGRADE.get());
        basicItem(ModItems.NETHERITE_TIER_UPGRADE.get());
        basicItem(ModItems.MACHINE_FRAME.get());
        basicItem(ModItems.IRON_DUST.get());
        basicItem(ModItems.COPPER_DUST.get());
        basicItem(ModItems.GOLD_DUST.get());
        basicItem(ModItems.LINKING_TOOL.get());
        basicItem(ModItems.FILTER.get());
        basicItem(ModItems.CONFIG_CARD.get());
        basicItem(ModItems.FLUX_CRYSTAL.get());
        basicItem(ModItems.FLUX_COUPLER.get());
        basicItem(ModItems.LINK_RANGE_UPGRADE.get());
        basicItem(ModItems.UNBOUND_LINK_CARD.get());
    }
}
