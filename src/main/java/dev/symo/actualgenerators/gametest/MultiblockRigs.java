package dev.symo.actualgenerators.gametest;

import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlock;
import dev.symo.actualgenerators.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Predicate;

/**
 * Structures built the way the preview shows them and the guide describes them, so a player who
 * builds what the hologram shows builds what these build.
 *
 * <p>A box: the controller in the bottom row of the front wall (north, facing north out of it),
 * the wall centred on it. A tap: a plate of Geothermal Casing with the 3×3 cap centred on top,
 * the controller in the middle of the cap's north side, facing north.
 */
final class MultiblockRigs {

    private MultiblockRigs() {
    }

    /** The controller's place in a box whose near corner is {@code corner}. */
    static BlockPos controllerPos(BlockPos corner, int width) {
        return new BlockPos(corner.getX() + width / 2, corner.getY(), corner.getZ());
    }

    /**
     * A hollow box of casing from {@code corner}, the controller in its front wall, every shell
     * position the predicate allows filled and the inside cleared to air.
     */
    static void buildBox(GameTestHelper helper,
                         BlockPos corner,
                         Block controllerBlock,
                         int width,
                         int height,
                         int depth,
                         Predicate<BlockPos> place) {
        BlockPos controller = controllerPos(corner, width);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0 || x == width - 1 || y == height - 1 || z == depth - 1;
                    BlockPos pos = corner.offset(x, y, z);
                    if (!shell) {
                        helper.setBlock(pos, Blocks.AIR);
                    } else if (!place.test(pos)) {
                        continue;
                    } else if (pos.equals(controller)) {
                        helper.setBlock(pos, controllerBlock.defaultBlockState()
                                .setValue(MultiblockControllerBlock.FACING, Direction.NORTH));
                    } else {
                        helper.setBlock(pos, ModBlocks.MACHINE_CASING.get());
                    }
                }
            }
        }
    }

    /** The middle of the cap: one up from the plate's centre. */
    static BlockPos capCentre(BlockPos corner, int width, int depth) {
        return corner.offset(width / 2, 1, depth / 2);
    }

    /** The tap's place: the middle of the cap's north side. */
    static BlockPos tapPos(BlockPos corner, int width, int depth) {
        return capCentre(corner, width, depth).north();
    }

    /**
     * A plate of Geothermal Casing from {@code corner}, {@code width} by {@code depth} and both
     * odd, with the 3×3 cap of Machine Casing centred on top and the tap in the cap's north
     * side; every position the predicate allows is filled.
     */
    static void buildTap(GameTestHelper helper, BlockPos corner, int width, int depth, Predicate<BlockPos> place) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                BlockPos pos = corner.offset(x, 0, z);
                if (place.test(pos)) {
                    helper.setBlock(pos, ModBlocks.GEOTHERMAL_CASING.get());
                }
            }
        }
        BlockPos centre = capCentre(corner, width, depth);
        BlockPos tap = tapPos(corner, width, depth);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = centre.offset(dx, 0, dz);
                if (!place.test(pos)) {
                    continue;
                }
                if (pos.equals(tap)) {
                    helper.setBlock(pos, ModBlocks.GEOTHERMAL_TAP.get().defaultBlockState()
                            .setValue(MultiblockControllerBlock.FACING, Direction.NORTH));
                } else {
                    helper.setBlock(pos, ModBlocks.MACHINE_CASING.get());
                }
            }
        }
    }
}
