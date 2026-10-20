package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.List;
import java.util.function.Predicate;

/** Non-loading world inspection shared by capture and building guides. */
public final class PortalWorldAccess {
    private PortalWorldAccess() { }

    public static boolean isLoaded(LevelReader level, BlockPos block) {
        // ClientLevel.hasChunk always returns true in 26.2. Ask for a real cached chunk.
        return level.getChunk(block.getX() >> 4, block.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }

    public static List<BlockPos> frameIfValid(BlockPos interior, Direction.Axis axis, Predicate<BlockPos> valid) {
        List<BlockPos> frame = PortalFrameGeometry.frame(interior, axis);
        return frame.stream().allMatch(valid) ? frame : List.of();
    }
}
