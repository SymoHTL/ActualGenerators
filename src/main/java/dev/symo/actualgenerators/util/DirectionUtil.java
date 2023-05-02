package dev.symo.actualgenerators.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class DirectionUtil {

    public static Direction getFacingDirection(BlockPos source, BlockPos target) {
        var diff = target.subtract(source);
        return Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
    }
}
