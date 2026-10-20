package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A 4x5 frame around the two-wide, three-high interior, including corners. */
public final class PortalFrameGeometry {
    private PortalFrameGeometry() { }

    /** The anchor is the lower left interior block when looking along the width axis. */
    public static List<BlockPos> frame(BlockPos interior, Direction.Axis axis) {
        Objects.requireNonNull(interior, "interior");
        if (axis != Direction.Axis.X && axis != Direction.Axis.Z) {
            throw new IllegalArgumentException("Portal width must follow the X or Z axis");
        }
        List<BlockPos> blocks = new ArrayList<>(14);
        for (int height = -1; height <= 3; height++) {
            for (int width = -1; width <= 2; width++) {
                if (height != -1 && height != 3 && width != -1 && width != 2) continue;
                blocks.add(new BlockPos(
                        interior.getX() + (axis == Direction.Axis.X ? width : 0),
                        interior.getY() + height,
                        interior.getZ() + (axis == Direction.Axis.Z ? width : 0)));
            }
        }
        return List.copyOf(blocks);
    }
}
