package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Proxy;
import net.minecraft.world.level.LevelReader;

public final class PortalInputRegressionCheck {
    public static void run() {
        var coordinates = PortalCoordinates.parse("nether -15, 128, 40");
        assert coordinates.source().equals(new BlockPos(-15, 128, 40));
        assert coordinates.sourceDimension() == Level.NETHER;
        assert PortalCoordinates.parse("minecraft:overworld 120 72 -48").sourceDimension() == Level.OVERWORLD;
        for (String bad : new String[]{"120 72 -48", "end 1 2 3", "nether 1.5 2 3", "nether 999999999999 2 3",
                "overworld 30000000 70 0", "nether 0 2048 0", "nether 1 2 3 4", ""}) {
            try { PortalCoordinates.parse(bad); throw new AssertionError("Accepted invalid coordinates: " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        Set<BlockPos> portal = new HashSet<>();
        for (int x = -10; x <= -9; x++) for (int y = 60; y <= 62; y++) portal.add(new BlockPos(x, y, 4));
        BlockPos interior = PortalCapture.findInterior(new BlockPos(-9, 62, 4), Direction.Axis.X, portal::contains);
        assert interior.equals(new BlockPos(-10, 60, 4)) : interior;
        assert PortalCapture.findInterior(new BlockPos(0, 0, 0), Direction.Axis.X, portal::contains) == null;
        Set<BlockPos> zPortal = Set.of(new BlockPos(5, 70, -3), new BlockPos(5, 70, -2),
                new BlockPos(5, 71, -3), new BlockPos(5, 71, -2));
        assert PortalCapture.findInterior(new BlockPos(5, 71, -2), Direction.Axis.Z, zPortal::contains)
                .equals(new BlockPos(5, 70, -3));
        // Even a pathological supplied predicate cannot cause an unbounded scan.
        assert PortalCapture.findInterior(BlockPos.ZERO, Direction.Axis.X, position -> true) == null;
        LevelReader absentClientChunk = (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                new Class<?>[]{LevelReader.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("hasChunk")) return true; // Actual 26.2 client behavior.
                    if (method.getName().equals("getChunk") && arguments.length == 4) {
                        assert Boolean.FALSE.equals(arguments[3]) : "Inspection must not request a chunk";
                        assert arguments[0].equals(-1) && arguments[1].equals(1) : "Negative block coordinates need floor chunk indexing";
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        assert !PortalWorldAccess.isLoaded(absentClientChunk, new BlockPos(-1, 70, 16));
        assert PortalWorldAccess.frameIfValid(new BlockPos(15, 70, 0), Direction.Axis.X,
                block -> block.getX() < 16).isEmpty() : "An unloaded edge must suppress the complete frame";
        assert PortalWorldAccess.frameIfValid(new BlockPos(15, 70, 0), Direction.Axis.X, block -> true).size() == 14;
    }
}
