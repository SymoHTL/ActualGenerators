package dev.symo.actualgenerators.datagen;

import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.EnumMap;
import dev.symo.actualgenerators.machine.multiblock.CoilPiece;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.CustomLoaderBuilder;
import net.minecraft.world.level.block.Block;
import dev.symo.actualgenerators.machine.multiblock.MultiblockCasingBlock;
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
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlock;

public class ModBlockStateProvider extends BlockStateProvider {
    /** The connected casing loader, {@code ConnectedCasingModel.ID}; a client class datagen does not load. */
    private static final ResourceLocation CONNECTED_CASING =
            ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, "connected_casing");
    /** How many detail tiles a formed wall's middles are picked from: bolts, a vent, a recessed panel, a pilot light. */
    private static final int CASING_DETAIL_TILES = 4;


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
        multiblock();
        // The fluid block is drawn by the fluid renderer; the model only names its particle.
        getVariantBuilder(ModBlocks.CORIUM.get()).partialState().setModels(new ConfiguredModel(
                models().getBuilder("corium").texture("particle", modLoc("block/corium_still"))));
    }

    /**
     * The shell blocks: connected models once the structure stands, plain cubes while it does not.
     * The controllers keep their fronts and join the wall with their other five faces; the tap's
     * plate becomes rings of fins.
     */
    private void multiblock() {
        casing(ModBlocks.MACHINE_CASING.get(), "machine_casing", "machine_casing", CASING_DETAIL_TILES);
        coil(ModBlocks.GEOTHERMAL_CASING.get(), "geothermal_casing");
        casing(ModBlocks.ITEM_HATCH.get(), "item_hatch", "machine_casing", 0);
        casing(ModBlocks.ENERGY_HATCH.get(), "energy_hatch", "machine_casing", 0);
        casing(ModBlocks.REDSTONE_HATCH.get(), "redstone_hatch", "machine_casing", 0);
        casing(ModBlocks.FLUID_HATCH.get(), "fluid_hatch", "machine_casing", 0);

        ModelFile tapIdle = models().orientableWithBottom(
                "geothermal_tap",
                modLoc("block/geothermal_tap_side"),
                modLoc("block/geothermal_tap_front"),
                modLoc("block/geothermal_tap_bottom"),
                modLoc("block/geothermal_tap_top"));
        controller(ModBlocks.GEOTHERMAL_TAP.get(), "geothermal_tap", tapIdle, "geothermal_tap_front_formed");

        ModelFile furnaceIdle = models().orientableWithBottom(
                "annihilation_furnace",
                modLoc("block/machine_casing"),
                modLoc("block/annihilation_furnace_front"),
                modLoc("block/machine_casing"),
                modLoc("block/machine_casing"));
        controller(ModBlocks.ANNIHILATION_FURNACE.get(), "annihilation_furnace", furnaceIdle, "annihilation_furnace_front_formed");
    }

    /**
     * A controller: its own oriented model while loose; formed, a connected casing with its front
     * on the face it faces, one model per facing since the connected model is laid in world
     * directions and takes no rotation.
     */
    private void controller(Block block, String name, ModelFile idle, String frontFormed) {
        Map<Direction, ModelFile> formed = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            formed.put(facing, connected(name + "_formed_" + facing.getSerializedName(), "machine_casing_formed",
                    "machine_casing_panel", null, null, facing, frontFormed, 0));
        }
        getVariantBuilder(block).forAllStates(state -> {
            Direction facing = state.getValue(MultiblockControllerBlock.FACING);
            if (state.getValue(MultiblockControllerBlock.FORMED)) {
                return ConfiguredModel.builder().modelFile(formed.get(facing)).build();
            }
            return ConfiguredModel.builder().modelFile(idle).rotationY(((int) facing.toYRot() + 180) % 360).build();
        });
        simpleBlockItem(block, idle);
    }

    /**
     * A shell block: loose, the plain block; formed, the connected model that shows its own rim
     * only where the structure ends and {@code panel}'s plate where it goes on
     * ({@code actualgenerators:connected_casing}). A hatch joins with the machine casing's plate
     * and keeps its door in the middle; a casing's middle is one of {@code detailTiles} tiles
     * ({@code <panel>_panel_1} and up) picked by position, so a wall is not a grid of one motif.
     */
    private void casing(Block block, String name, String panel, int detailTiles) {
        ModelFile loose = models().cubeAll(name, modLoc("block/" + name));
        ModelFile formed = connected(name + "_formed", name + "_formed", panel + "_panel", null, null, null, null, detailTiles);
        getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(state.getValue(MultiblockCasingBlock.FORMED) ? formed : loose)
                .build());
        simpleBlockItem(block, loose);
    }

    /**
     * The tap's plate: loose, the plain block; formed, plain plate or a piece of a ring of fins,
     * the fins laid from the corner that faces the plate's centre ({@code CoilPiece}).
     */
    private void coil(Block block, String name) {
        ModelFile loose = models().cubeAll(name, modLoc("block/" + name));
        String plate = name + "_plate";
        String ring = name + "_ring";
        String corner = name + "_ring_corner";
        ModelFile plain = connected(plate, plate, plate, null, null, null, null, 0);
        Map<CoilPiece, ModelFile> pieces = new EnumMap<>(CoilPiece.class);
        pieces.put(CoilPiece.RING_X, connected(name + "_ring_x", ring, ring, ring, "nw", null, null, 0));
        pieces.put(CoilPiece.RING_Z, connected(name + "_ring_z", ring, ring, ring, "swap", null, null, 0));
        // The texture's top-left corner is the corner that faces the centre.
        pieces.put(CoilPiece.CORNER_SE, connected(name + "_ring_se", ring, ring, corner, "nw", null, null, 0));
        pieces.put(CoilPiece.CORNER_NE, connected(name + "_ring_ne", ring, ring, corner, "sw", null, null, 0));
        pieces.put(CoilPiece.CORNER_NW, connected(name + "_ring_nw", ring, ring, corner, "se", null, null, 0));
        pieces.put(CoilPiece.CORNER_SW, connected(name + "_ring_sw", ring, ring, corner, "ne", null, null, 0));
        getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(!state.getValue(MultiblockCasingBlock.FORMED) ? loose
                        : pieces.getOrDefault(state.getValue(CoilPiece.COIL), plain))
                .build());
        simpleBlockItem(block, loose);
    }

    /**
     * One connected-casing model: the block's own texture, the plate it joins with, and for a
     * piece of the plate its top and how it is laid, for a controller its front and where it faces.
     */
    private BlockModelBuilder connected(String model, String frame, String panel,
                                        @Nullable String top, @Nullable String topMap,
                                        @Nullable Direction front, @Nullable String frontTexture,
                                        int variants) {
        BlockModelBuilder builder = models().getBuilder(model)
                .customLoader((parent, helper) -> new CustomLoaderBuilder<BlockModelBuilder>(CONNECTED_CASING, parent, helper, false) {
                    @Override
                    public JsonObject toJson(JsonObject json) {
                        json = super.toJson(json);
                        if (topMap != null) {
                            json.addProperty("top_map", topMap);
                        }
                        if (front != null) {
                            json.addProperty("front", front.getSerializedName());
                        }
                        if (variants > 0) {
                            json.addProperty("variants", variants);
                        }
                        return json;
                    }
                })
                .end()
                .texture("panel", modLoc("block/" + panel))
                .texture("frame", modLoc("block/" + frame))
                .texture("particle", modLoc("block/" + frame));
        if (top != null) {
            builder.texture("top", modLoc("block/" + top));
        }
        if (frontTexture != null) {
            builder.texture("front", modLoc("block/" + frontTexture));
        }
        for (int tile = 1; tile <= variants; tile++) {
            builder.texture("variant_" + tile, modLoc("block/" + panel + "_" + tile));
        }
        return builder;
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
