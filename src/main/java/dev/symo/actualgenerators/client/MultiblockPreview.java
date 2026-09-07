package dev.symo.actualgenerators.client;

import dev.symo.actualgenerators.machine.multiblock.MultiblockControllerBlockEntity;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Which controllers are showing a preview of the structure they want, and at what size. Client
 * only: the switch is a picture for the player building the thing; the size is a copy of the
 * controller's own, kept in step by the window while it is open. A controller drops out of the
 * map when its structure forms or it is gone ({@link MultiblockPreviewRenderer}).
 */
public final class MultiblockPreview {
    /** A box's three sizes, in the controller's terms. */
    public record Size(int width, int height, int depth) {
        public int get(MultiblockControllerBlockEntity.Extent extent) {
            return switch (extent) {
                case WIDTH -> width;
                case HEIGHT -> height;
                case DEPTH -> depth;
            };
        }

        public Size with(MultiblockControllerBlockEntity.Extent extent, int value) {
            return switch (extent) {
                case WIDTH -> new Size(value, height, depth);
                case HEIGHT -> new Size(width, value, depth);
                case DEPTH -> new Size(width, height, value);
            };
        }

        /** The smallest box the controller allows: the size a preview starts at. */
        public static Size smallest(MultiblockControllerBlockEntity controller) {
            return new Size(controller.minSize(MultiblockControllerBlockEntity.Extent.WIDTH),
                    controller.minSize(MultiblockControllerBlockEntity.Extent.HEIGHT),
                    controller.minSize(MultiblockControllerBlockEntity.Extent.DEPTH));
        }
    }

    private static final Map<BlockPos, Size> SHOWN = new HashMap<>();

    private MultiblockPreview() {
    }

    public static boolean isShown(BlockPos controller) {
        return SHOWN.containsKey(controller);
    }

    public static @Nullable Size shown(BlockPos controller) {
        return SHOWN.get(controller);
    }

    public static void show(BlockPos controller, Size size) {
        SHOWN.put(controller.immutable(), size);
    }

    public static void hide(BlockPos controller) {
        SHOWN.remove(controller);
    }

    static Map<BlockPos, Size> all() {
        return SHOWN;
    }
}
