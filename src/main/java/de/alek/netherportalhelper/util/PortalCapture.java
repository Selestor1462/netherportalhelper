package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Captures a connected, loaded portal's lower interior corner and orientation. */
public final class PortalCapture {
    private PortalCapture() { }

    public static Captured capture(Level level, BlockPos hit) {
        if (!PortalTracker.isPortalDimension(level.dimension()) || !PortalWorldAccess.isLoaded(level, hit)) return null;
        var state = level.getBlockState(hit);
        if (state.getBlock() != Blocks.NETHER_PORTAL) return null;
        Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
        BlockPos interior = findInterior(hit, axis, position -> PortalWorldAccess.isLoaded(level, position)
                && level.getBlockState(position).getBlock() == Blocks.NETHER_PORTAL
                && level.getBlockState(position).getValue(NetherPortalBlock.AXIS) == axis);
        return interior == null ? null : new Captured(interior, axis);
    }

    public static BlockPos findInterior(BlockPos start, Direction.Axis axis, Predicate<BlockPos> isPortal) {
        if (axis != Direction.Axis.X && axis != Direction.Axis.Z) throw new IllegalArgumentException("Horizontal portal axis required");
        if (!isPortal.test(start)) return null;
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        pending.add(start.immutable());
        BlockPos lowest = start.immutable();
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            if (!seen.add(current) || !isPortal.test(current)) continue;
            // Vanilla portal interiors are at most 21 by 21. Bound malformed/modded scans too.
            if (seen.size() > 600 || Math.abs(current.getY() - start.getY()) > 20
                    || Math.abs(axis == Direction.Axis.X ? current.getX() - start.getX() : current.getZ() - start.getZ()) > 20) {
                return null;
            }
            if (current.getY() < lowest.getY() || (current.getY() == lowest.getY()
                    && (axis == Direction.Axis.X ? current.getX() < lowest.getX() : current.getZ() < lowest.getZ()))) {
                lowest = current;
            }
            pending.add(current.above());
            pending.add(current.below());
            pending.add(axis == Direction.Axis.X ? current.east() : current.south());
            pending.add(axis == Direction.Axis.X ? current.west() : current.north());
        }
        return lowest.immutable();
    }

    public record Captured(BlockPos source, Direction.Axis axis) { }
}
