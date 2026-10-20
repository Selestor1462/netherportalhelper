package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** A saved source and its reference destination, with independent building choices. */
public record PortalTarget(UUID id, String name, BlockPos source, ResourceKey<Level> sourceDimension,
                           BlockPos calculatedDestination, Integer buildY, Direction.Axis axis) {
    public PortalTarget {
        if (id == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Portal identity and name are required");
        }
        name = name.trim();
        if (name.isEmpty() || name.length() > 64) throw new IllegalArgumentException("Portal name must contain 1–64 characters");
        if (!PortalTracker.isPortalDimension(sourceDimension)) throw new IllegalArgumentException("Unsupported portal dimension");
        source = checkedPosition(source);
        calculatedDestination = checkedPosition(calculatedDestination);
        if (buildY != null && (buildY < -2032 || buildY > 2031)) throw new IllegalArgumentException("Build height is outside supported bounds");
        if (axis != Direction.Axis.X && axis != Direction.Axis.Z) throw new IllegalArgumentException("Portal axis must be X or Z");
    }

    public static PortalTarget create(String name, BlockPos source, ResourceKey<Level> dimension,
                                      Integer buildY, Direction.Axis axis) {
        BlockPos immutableSource = checkedPosition(source);
        if (!PortalTracker.isPortalDimension(dimension)) throw new IllegalArgumentException("Unsupported portal dimension");
        return new PortalTarget(UUID.randomUUID(), name, immutableSource, dimension,
                PortalTracker.calculateCounterpartPosition(immutableSource, dimension), buildY, axis);
    }

    public ResourceKey<Level> destinationDimension() {
        return PortalTracker.getCounterpartDimension(sourceDimension);
    }

    public BlockPos destination() {
        return buildY == null ? calculatedDestination
                : new BlockPos(calculatedDestination.getX(), buildY, calculatedDestination.getZ());
    }

    public PortalTarget withName(String newName) {
        return new PortalTarget(id, newName, source, sourceDimension, calculatedDestination, buildY, axis);
    }

    public PortalTarget withBuild(Integer newBuildY, Direction.Axis newAxis) {
        return new PortalTarget(id, name, source, sourceDimension, calculatedDestination, newBuildY, newAxis);
    }

    private static BlockPos checkedPosition(BlockPos position) {
        if (position == null || position.getX() < -29_999_984 || position.getX() > 29_999_984
                || position.getZ() < -29_999_984 || position.getZ() > 29_999_984
                || position.getY() < -2032 || position.getY() > 2031) {
            throw new IllegalArgumentException("Portal coordinates are outside supported bounds");
        }
        return position.immutable();
    }
}
