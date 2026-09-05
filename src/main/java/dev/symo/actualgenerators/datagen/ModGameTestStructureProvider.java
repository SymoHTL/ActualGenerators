package dev.symo.actualgenerators.datagen;

import com.google.common.hash.Hashing;
import dev.symo.actualgenerators.ActualGenerators;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the structure templates the gametests run inside.
 *
 * <p>Structure files are binary NBT, so they cannot be authored as JSON — generating them here
 * keeps {@code src/generated/resources} the single source of truth rather than committing an
 * opaque blob by hand.
 */
public class ModGameTestStructureProvider implements DataProvider {
    /** A 3x3x3 volume of nothing, for tests that exercise logic rather than the world. */
    public static final String EMPTY = "empty";

    /** A 5x4x5 box with a stone floor, for tests that place and tick real machines. */
    public static final String PLATFORM = "platform";

    /** A tall floored shaft, for tests that need room to stack a water column. */
    public static final String TOWER = "tower";

    /** A 10x10x10 floored box, for the test that fills it with three hundred pads. */
    public static final String ARENA = "arena";

    private static final String FLOOR_BLOCK = "minecraft:polished_andesite";

    private final PackOutput.PathProvider structures;

    public ModGameTestStructureProvider(PackOutput output) {
        this.structures = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return CompletableFuture.runAsync(() -> {
            write(output, EMPTY, emptyStructure(3, 3, 3));
            write(output, PLATFORM, platformStructure(5, 4, 5));
            write(output, TOWER, platformStructure(5, 24, 5));
            write(output, ARENA, platformStructure(10, 10, 10));
        });
    }

    private void write(CachedOutput output, String name, CompoundTag tag) {
        Path target = structures.file(ResourceLocation.fromNamespaceAndPath(ActualGenerators.MODID, name), "nbt");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, buffer);
            byte[] bytes = buffer.toByteArray();
            output.writeIfNeeded(target, bytes, Hashing.sha1().hashBytes(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("failed writing gametest structure " + name, e);
        }
    }

    private static CompoundTag emptyStructure(int sizeX, int sizeY, int sizeZ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());

        ListTag size = new ListTag();
        size.add(IntTag.valueOf(sizeX));
        size.add(IntTag.valueOf(sizeY));
        size.add(IntTag.valueOf(sizeZ));
        tag.put("size", size);

        tag.put("palette", new ListTag());
        tag.put("blocks", new ListTag());
        tag.put("entities", new ListTag());
        return tag;
    }

    /** Same as {@link #emptyStructure} but with the bottom layer filled, so machines can stand. */
    private static CompoundTag platformStructure(int sizeX, int sizeY, int sizeZ) {
        CompoundTag tag = emptyStructure(sizeX, sizeY, sizeZ);

        ListTag palette = new ListTag();
        CompoundTag floor = new CompoundTag();
        floor.putString("Name", FLOOR_BLOCK);
        palette.add(floor);
        tag.put("palette", palette);

        ListTag blocks = new ListTag();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                CompoundTag block = new CompoundTag();
                ListTag pos = new ListTag();
                pos.add(IntTag.valueOf(x));
                pos.add(IntTag.valueOf(0));
                pos.add(IntTag.valueOf(z));
                block.put("pos", pos);
                block.putInt("state", 0);
                blocks.add(block);
            }
        }
        tag.put("blocks", blocks);
        return tag;
    }

    @Override
    public String getName() {
        return "Actual Generators gametest structures";
    }
}
