package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.logistics.LinkPortBlock;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ActualGenerators.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile corrosionCell = models().orientableWithBottom(
                "corrosion_cell",
                modLoc("block/corrosion_cell_side"),
                modLoc("block/corrosion_cell_front"),
                modLoc("block/corrosion_cell_bottom"),
                modLoc("block/corrosion_cell_top"));

        horizontalBlock(ModBlocks.CORROSION_CELL.get(), corrosionCell);
        simpleBlockItem(ModBlocks.CORROSION_CELL.get(), corrosionCell);

        ModelFile hydrostatic = models().orientableWithBottom(
                "hydrostatic_generator",
                modLoc("block/hydrostatic_generator_side"),
                modLoc("block/hydrostatic_generator_front"),
                modLoc("block/hydrostatic_generator_bottom"),
                modLoc("block/hydrostatic_generator_top"));

        horizontalBlock(ModBlocks.HYDROSTATIC_GENERATOR.get(), hydrostatic);
        simpleBlockItem(ModBlocks.HYDROSTATIC_GENERATOR.get(), hydrostatic);

        ModelFile photovore = models().orientableWithBottom(
                "photovore",
                modLoc("block/photovore_side"),
                modLoc("block/photovore_front"),
                modLoc("block/photovore_bottom"),
                modLoc("block/photovore_top"));

        horizontalBlock(ModBlocks.PHOTOVORE.get(), photovore);
        simpleBlockItem(ModBlocks.PHOTOVORE.get(), photovore);

        machine(ModBlocks.IMPACT_DYNAMO.get(), "impact_dynamo");
        machine(ModBlocks.SPAWNER_SIPHON.get(), "spawner_siphon");
        machine(ModBlocks.ENCHANTMENT_COMBUSTOR.get(), "enchantment_combustor");
        machine(ModBlocks.SURGE_BANK.get(), "surge_bank");
        machine(ModBlocks.ENERGY_INJECTOR.get(), "energy_injector");
        machine(ModBlocks.CRYSTAL_CHARGER.get(), "crystal_charger");
        machine(ModBlocks.RESONANCE_CRUSHER.get(), "resonance_crusher");
        linkPort();
    }

    /**
     * A flat pad two pixels deep, authored pointing north and rotated onto whichever face it was
     * placed against. The x=90 / x=270 pair is the vanilla convention for pointing a north-facing
     * model down and up.
     */
    private void linkPort() {
        ModelFile model = models().withExistingParent("logic_port", mcLoc("block/block"))
                .texture("particle", modLoc("block/logic_port"))
                .texture("pad", modLoc("block/logic_port"))
                .element()
                .from(2, 2, 0)
                .to(14, 14, 2)
                .allFaces((direction, face) -> face.texture("#pad"))
                .end();

        // Multipart rather than variants: six faces are six independent yes/no answers, and
        // writing them as variants would be sixty-four states saying the same six things.
        MultiPartBlockStateBuilder builder = getMultipartBuilder(ModBlocks.LOGIC_PORT.get());
        for (Direction facing : Direction.values()) {
            builder.part()
                    .modelFile(model)
                    .rotationX(facing == Direction.DOWN ? 90 : facing == Direction.UP ? 270 : 0)
                    .rotationY(switch (facing) {
                        case SOUTH -> 180;
                        case WEST -> 270;
                        case EAST -> 90;
                        default -> 0;
                    })
                    .addModel()
                    .condition(LinkPortBlock.FACE_PROPERTIES.get(facing), true)
                    .end();
        }
        // The item is the same pad centred in the block space: at the edge it sat off to one side
        // of its slot in the hotbar and the inventory.
        ModelFile item = models().withExistingParent("logic_port_item", mcLoc("block/block"))
                .texture("particle", modLoc("block/logic_port"))
                .texture("pad", modLoc("block/logic_port"))
                .element()
                .from(2, 2, 7)
                .to(14, 14, 9)
                .allFaces((direction, face) -> face.texture("#pad"))
                .end();
        simpleBlockItem(ModBlocks.LOGIC_PORT.get(), item);
    }

    /** A four-face machine: side, front, top and bottom, facing whichever way it was placed. */
    private void machine(net.minecraft.world.level.block.Block block, String name) {
        ModelFile model = models().orientableWithBottom(
                name,
                modLoc("block/" + name + "_side"),
                modLoc("block/" + name + "_front"),
                modLoc("block/" + name + "_bottom"),
                modLoc("block/" + name + "_top"));
        horizontalBlock(block, model);
        simpleBlockItem(block, model);
    }
}
