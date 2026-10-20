package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure geometry checks; never starts a client or reads a world. */
public final class PortalFrameRegressionCheck {
    private PortalFrameRegressionCheck() { }

    public static void run() {
        List<BlockPos> xFrame = PortalFrameGeometry.frame(BlockPos.ZERO, Direction.Axis.X);
        assert Set.copyOf(xFrame).equals(Set.of(
                new BlockPos(-1, -1, 0), new BlockPos(0, -1, 0),
                new BlockPos(1, -1, 0), new BlockPos(2, -1, 0),
                new BlockPos(-1, 0, 0), new BlockPos(2, 0, 0),
                new BlockPos(-1, 1, 0), new BlockPos(2, 1, 0),
                new BlockPos(-1, 2, 0), new BlockPos(2, 2, 0),
                new BlockPos(-1, 3, 0), new BlockPos(0, 3, 0),
                new BlockPos(1, 3, 0), new BlockPos(2, 3, 0)))
                : "X frame must surround the 2x3 interior, including all four corners";
        assert xFrame.size() == 14 && new HashSet<>(xFrame).size() == 14;
        assert !xFrame.contains(BlockPos.ZERO) : "Interior anchor is empty portal space";
        assert !xFrame.contains(new BlockPos(1, 2, 0)) : "Interior must remain open";

        List<BlockPos> zFrame = PortalFrameGeometry.frame(new BlockPos(-7, 80, -9), Direction.Axis.Z);
        assert Set.copyOf(zFrame).equals(Set.of(
                new BlockPos(-7, 79, -10), new BlockPos(-7, 79, -9),
                new BlockPos(-7, 79, -8), new BlockPos(-7, 79, -7),
                new BlockPos(-7, 80, -10), new BlockPos(-7, 80, -7),
                new BlockPos(-7, 81, -10), new BlockPos(-7, 81, -7),
                new BlockPos(-7, 82, -10), new BlockPos(-7, 82, -7),
                new BlockPos(-7, 83, -10), new BlockPos(-7, 83, -9),
                new BlockPos(-7, 83, -8), new BlockPos(-7, 83, -7)))
                : "Z frame must respect negative coordinates and the chosen bottom interior height";
        assert zFrame.size() == 14 && new HashSet<>(zFrame).size() == 14;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(0, 64, 0);
        List<BlockPos> snapshot = PortalFrameGeometry.frame(mutable, Direction.Axis.X);
        mutable.set(100, 100, 100);
        assert snapshot.contains(new BlockPos(-1, 63, 0)) : "Geometry must snapshot a mutable anchor";
        assert snapshot.stream().noneMatch(BlockPos.MutableBlockPos.class::isInstance);
        try {
            snapshot.add(BlockPos.ZERO);
            throw new AssertionError("Frame list must be immutable");
        } catch (UnsupportedOperationException expected) { }
        try {
            PortalFrameGeometry.frame(BlockPos.ZERO, Direction.Axis.Y);
            throw new AssertionError("Vertical width axis must be rejected");
        } catch (IllegalArgumentException expected) { }
    }
}
