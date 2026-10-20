package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class PortalTracker {
    private static boolean active = false;
    private static BlockPos targetPos = null;
    private static ResourceKey<Level> targetDimension = null;
    private static PortalTarget target = null;

    public static PortalTarget getTarget() {
        return target;
    }

    public static void setTarget(PortalTarget selected) {
        if (selected == null) {
            clear();
            return;
        }
        target = selected;
        targetPos = selected.destination();
        targetDimension = selected.destinationDimension();
        active = true;
    }

    public static boolean isActive() {
        return active;
    }

    public static BlockPos getTargetPos() {
        return targetPos;
    }

    public static ResourceKey<Level> getTargetDimension() {
        return targetDimension;
    }

    public static NavigationState assess(BlockPos player, ResourceKey<Level> dimension) {
        if (!active || player == null || targetPos == null || dimension != targetDimension) {
            return new NavigationState(false, false, 0);
        }
        return new NavigationState(true,
                player.getX() == targetPos.getX() && player.getZ() == targetPos.getZ(),
                targetPos.getY() - player.getY());
    }

    public record NavigationState(boolean navigating, boolean horizontalArrival, int heightDifference) { }

    public static boolean isPortalDimension(ResourceKey<Level> dimension) {
        return dimension == Level.NETHER || dimension == Level.OVERWORLD;
    }

    public static ResourceKey<Level> getCounterpartDimension(ResourceKey<Level> currentDimension) {
        if (currentDimension == Level.NETHER) {
            return Level.OVERWORLD;
        }
        if (currentDimension == Level.OVERWORLD) {
            return Level.NETHER;
        }
        return null;
    }

    public static BlockPos calculateCounterpartPosition(BlockPos currentPos, ResourceKey<Level> currentDimension) {
        if (currentDimension == Level.NETHER) {
            return new BlockPos(currentPos.getX() * 8, currentPos.getY(), currentPos.getZ() * 8);
        }
        if (currentDimension == Level.OVERWORLD) {
            return new BlockPos(Math.floorDiv(currentPos.getX(), 8), currentPos.getY(), Math.floorDiv(currentPos.getZ(), 8));
        }
        return null;
    }

    public static boolean lockTarget(BlockPos calculatedTarget, ResourceKey<Level> calculatedDimension) {
        if (calculatedTarget == null || !isPortalDimension(calculatedDimension)) {
            return false;
        }
        active = true;
        target = null;
        targetPos = calculatedTarget.immutable();
        targetDimension = calculatedDimension;
        return true;
    }

    public static void clear() {
        active = false;
        targetPos = null;
        targetDimension = null;
        target = null;
    }

    public static void toggleFreeze(BlockPos calculatedTarget, ResourceKey<Level> calculatedDimension) {
        if (active) {
            clear();
        } else {
            lockTarget(calculatedTarget, calculatedDimension);
        }
    }
}
