package dev.symo.actualgenerators.machine.multiblock;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;

/**
 * What a block of the tap's plate is once the plate stands: part of a ring of fins, or the plain
 * plate between rings. The rings run round the plate's centre every second block from the
 * outside in, so the plate reads as one coil from above whatever angle it is seen from. A corner
 * is named for where it lies from the centre; a ring piece for the axis its fins run along.
 */
public enum CoilPiece implements StringRepresentable {
    NONE,
    PLAIN,
    RING_X,
    RING_Z,
    CORNER_NE,
    CORNER_SE,
    CORNER_SW,
    CORNER_NW;

    public static final EnumProperty<CoilPiece> COIL = EnumProperty.create("coil", CoilPiece.class);

    /**
     * The piece a plate block at ({@code dx}, {@code dz}) from the centre of a plate {@code half}
     * blocks from centre to edge is: the outermost ring wears fins, every second ring inward does
     * too, the centre never.
     */
    public static CoilPiece at(int dx, int dz, int half) {
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring == 0 || (half - ring) % 2 == 1) {
            return PLAIN;
        }
        if (Math.abs(dx) == ring && Math.abs(dz) == ring) {
            return dx > 0 ? (dz > 0 ? CORNER_SE : CORNER_NE) : (dz > 0 ? CORNER_SW : CORNER_NW);
        }
        return Math.abs(dz) == ring ? RING_X : RING_Z;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
