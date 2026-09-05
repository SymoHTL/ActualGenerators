package dev.symo.actualgenerators.logistics;

import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;

/**
 * Where a pad is: a block position in a dimension, and which of that block's six faces.
 *
 * <p>A network addresses pads, not blocks — one block position can hold six of them, each on a
 * different network.
 */
public record PortRef(GlobalPos at, Direction face) {
}
