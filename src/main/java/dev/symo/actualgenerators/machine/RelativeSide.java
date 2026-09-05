package dev.symo.actualgenerators.machine;

import net.minecraft.core.Direction;

/**
 * A machine face expressed relative to the way the machine points.
 *
 * <p>Side configuration is stored this way rather than in absolute directions so that
 * copying a configuration onto a machine facing elsewhere still means the same thing —
 * "output from the back" stays the back.
 */
public enum RelativeSide {
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    private static final RelativeSide[] VALUES = values();

    public static RelativeSide[] all() {
        return VALUES;
    }

    /** The world direction this face points at, for a machine with the given horizontal facing. */
    public Direction toDirection(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getCounterClockWise();
            case RIGHT -> facing.getClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    /** The face of a machine with the given horizontal facing that points in {@code side}. */
    public static RelativeSide fromDirection(Direction facing, Direction side) {
        if (side == Direction.UP) {
            return TOP;
        }
        if (side == Direction.DOWN) {
            return BOTTOM;
        }
        if (side == facing) {
            return FRONT;
        }
        if (side == facing.getOpposite()) {
            return BACK;
        }
        return side == facing.getCounterClockWise() ? LEFT : RIGHT;
    }

    public static RelativeSide byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FRONT;
    }
}
