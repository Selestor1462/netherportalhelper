package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.OpenOption;
import java.util.Arrays;

public final class PortalManagerRegressionCheck {
    public static void run() throws Exception {
        PortalJourneyRegressionCheck.run();
        var directory = Files.createTempDirectory("portal-manager-");
        var file = directory.resolve("targets.json");
        String scope = TargetStore.serverKey("planner.test");
        try {
            PortalManager.initialize(new TargetStore(file));
            PortalManager.startSession(scope);
            PortalManager.add("Base", new BlockPos(120, 70, -48), Level.OVERWORLD, 128, Direction.Axis.X);
            PortalManager.add("Farm", new BlockPos(80, 64, 16), Level.OVERWORLD, null, Direction.Axis.Z);
            assert PortalManager.targets().size() == 2;
            var farm = PortalManager.activeTarget();
            assert farm.name().equals("Farm");
            var base = PortalManager.targets().getFirst();
            PortalManager.add("Duplicate must preserve metadata", new BlockPos(120, 70, -48),
                    Level.OVERWORLD, 20, Direction.Axis.Z);
            assert PortalManager.targets().size() == 2 : "Capturing the same source must select its saved route";
            assert PortalManager.activeTarget().equals(base) : "Recapture must preserve name and build choices";
            PortalManager.update(base.id(), "Home", 130, Direction.Axis.Z);
            PortalManager.select(base.id());
            assert PortalTracker.getTargetPos().equals(new BlockPos(15, 130, -6));
            assert PortalManager.activeTarget().calculatedDestination().getY() == 70;
            assert PortalCoordinates.parse(PortalCoordinates.format(base)).source().equals(base.source());
            PortalManager.unlock();
            assert PortalManager.targets().size() == 2 && !PortalTracker.isActive();
            PortalManager.endSession();
            PortalManager.startSession(scope);
            assert PortalManager.targets().size() == 2 && PortalManager.activeTarget() == null;
            PortalManager.select(farm.id());
            PortalManager.startSession(TargetStore.serverKey("other-planner.test"));
            assert PortalManager.targets().isEmpty() && !PortalTracker.isActive();
            PortalManager.startSession(scope);
            assert PortalManager.activeTarget().id().equals(farm.id());
            PortalManager.delete(farm.id());
            assert PortalManager.activeTarget() == null && PortalManager.targets().size() == 1;
            PortalManager.select(base.id());
            var previous = PortalManager.activeTarget();
            if (System.getProperty("os.name").startsWith("Windows")) {
                String original = Files.readString(file);
                // Windows denies replacement while this handle disallows delete sharing.
                OpenOption noDeleteSharing = (OpenOption) Arrays.stream(Class.forName("com.sun.nio.file.ExtendedOpenOption")
                        .getEnumConstants()).filter(option -> ((Enum<?>) option).name().equals("NOSHARE_DELETE")).findFirst().orElseThrow();
                try (var locked = FileChannel.open(file, StandardOpenOption.READ, noDeleteSharing)) {
                    try {
                        PortalManager.rename(base.id(), "Must not replace locked file");
                        throw new AssertionError("Locked target replacement must report a write failure");
                    } catch (IOException expected) { }
                    assert Files.readString(file).equals(original);
                    assert PortalManager.activeTarget().equals(previous);
                    try {
                        PortalManager.delete(base.id());
                        throw new AssertionError("Failed deletion must not replace the undo snapshot");
                    } catch (IOException expected) { }
                    assert PortalManager.activeTarget().equals(previous) && PortalManager.canUndoDelete();
                    try {
                        PortalManager.undoDelete();
                        throw new AssertionError("Locked file must reject undo atomically");
                    } catch (IOException expected) { }
                    assert PortalManager.canUndoDelete() && PortalManager.targets().size() == 1;
                    assert PortalManager.activeTarget().equals(previous);
                    assert Files.readString(file).equals(original);
                }
                try (var remaining = Files.list(directory)) {
                    assert remaining.count() == 1 : "Failed replacement must remove its temporary file";
                }
            }
            Files.writeString(file, "{ broken");
            try {
                PortalManager.update(base.id(), "Unsaved", 140, Direction.Axis.X);
                throw new AssertionError("A failed save must report failure");
            } catch (IOException expected) { }
            assert PortalManager.activeTarget().equals(previous) : "Failed writes must not change navigation";
            assert PortalManager.targets().getFirst().name().equals("Home");
            assert Files.readString(file).equals("{ broken");
        } finally {
            PortalManager.endSession();
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
