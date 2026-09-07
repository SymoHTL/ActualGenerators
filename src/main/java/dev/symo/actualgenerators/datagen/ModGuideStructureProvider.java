package dev.symo.actualgenerators.datagen;

import dev.symo.actualgenerators.ActualGenerators;
import dev.symo.actualgenerators.machine.multiblock.MultiblockCasingBlock;
import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlock;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the structures the guide shows in its scenes: the smallest of every multiblock, formed,
 * with a hatch of each kind it takes, laid out the way the preview draws them and the tests
 * build them. A picture in the guide that disagreed with the hologram would be a bug, so the
 * picture is generated from the same conventions rather than saved from a world.
 */
public class ModGuideStructureProvider implements DataProvider {
    public static final String ANNIHILATION_FURNACE = "annihilation_furnace";
    public static final String GEOTHERMAL_TAP = "geothermal_tap";

    private final PackOutput.PathProvider structures;

    public ModGuideStructureProvider(PackOutput output) {
        this.structures = output.createPathProvider(PackOutput.Target.RESOURCE_PACK,
                "guides/" + ActualGenerators.MODID + "/guide/structures");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return CompletableFuture.runAsync(() -> {
            write(output, ANNIHILATION_FURNACE, furnace());
            write(output, GEOTHERMAL_TAP, tap());
        });
    }

    /** A 3×7×3 furnace on a bucket of corium, an item hatch left, an energy hatch right, a redstone hatch behind. */
    private static Structure furnace() {
        Structure box = new Structure(3, 7, 3);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 7; y++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == 2 || y == 6 || z == 2) {
                        box.put(x, y, z, formed(ModBlocks.MACHINE_CASING.get()));
                    }
                }
            }
        }
        box.put(1, 0, 0, controller(ModBlocks.ANNIHILATION_FURNACE.get()));
        box.put(1, 1, 1, ModBlocks.CORIUM.get().defaultBlockState());
        box.put(0, 3, 1, formed(ModBlocks.ITEM_HATCH.get()));
        box.put(2, 3, 1, formed(ModBlocks.ENERGY_HATCH.get()));
        box.put(1, 3, 2, formed(ModBlocks.REDSTONE_HATCH.get()));
        return box;
    }

    /** A 5×5 plate under the 3×3 cap, the tap in the cap's front, a fluid hatch left of it and an energy hatch right. */
    private static Structure tap() {
        Structure plate = new Structure(5, 2, 5);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                plate.put(x, 0, z, formed(ModBlocks.GEOTHERMAL_CASING.get()));
            }
        }
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                plate.put(x, 1, z, formed(ModBlocks.MACHINE_CASING.get()));
            }
        }
        plate.put(2, 1, 1, controller(ModBlocks.GEOTHERMAL_TAP.get()));
        plate.put(1, 1, 2, formed(ModBlocks.FLUID_HATCH.get()));
        plate.put(3, 1, 2, formed(ModBlocks.ENERGY_HATCH.get()));
        return plate;
    }

    private static BlockState formed(Block shell) {
        return shell.defaultBlockState().setValue(MultiblockCasingBlock.FORMED, true);
    }

    /** The controller in the front wall, facing north out of it, formed. */
    private static BlockState controller(Block block) {
        return block.defaultBlockState()
                .setValue(MultiblockControllerBlock.FACING, Direction.NORTH)
                .setValue(MultiblockControllerBlock.FORMED, true);
    }

    private void write(CachedOutput output, String name, Structure structure) {
        Path target = structures.file(ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, name), "snbt");
        try {
            NbtToSnbt.writeSnbt(output, target, NbtUtils.structureToSnbt(structure.tag()));
        } catch (IOException e) {
            throw new UncheckedIOException("failed writing guide structure " + name, e);
        }
    }

    /** A structure template built block by block; what is not put stays air. */
    private static final class Structure {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final List<BlockState> palette = new ArrayList<>();
        private final ListTag blocks = new ListTag();

        Structure(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        void put(int x, int y, int z, BlockState state) {
            int index = palette.indexOf(state);
            if (index < 0) {
                index = palette.size();
                palette.add(state);
            }
            CompoundTag block = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(x));
            pos.add(IntTag.valueOf(y));
            pos.add(IntTag.valueOf(z));
            block.put("pos", pos);
            block.putInt("state", index);
            blocks.add(block);
        }

        CompoundTag tag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            ListTag size = new ListTag();
            size.add(IntTag.valueOf(sizeX));
            size.add(IntTag.valueOf(sizeY));
            size.add(IntTag.valueOf(sizeZ));
            tag.put("size", size);
            ListTag states = new ListTag();
            for (BlockState state : palette) {
                states.add(NbtUtils.writeBlockState(state));
            }
            tag.put("palette", states);
            tag.put("blocks", blocks);
            tag.put("entities", new ListTag());
            return tag;
        }
    }

    @Override
    public String getName() {
        return "Actual Generators guide structures";
    }
}
