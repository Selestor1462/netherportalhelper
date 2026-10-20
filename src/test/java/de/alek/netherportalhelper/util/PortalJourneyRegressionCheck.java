package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.io.IOException;
import java.nio.file.Files;

/** Exercises the saved outward/return loop and transactional delete recovery. */
public final class PortalJourneyRegressionCheck {
    private PortalJourneyRegressionCheck() { }

    public static void run() throws Exception {
        var directory = Files.createTempDirectory("portal-journey-");
        var file = directory.resolve("targets.json");
        try {
            PortalManager.initialize(new TargetStore(file));
            PortalManager.startSession("journey");
            PortalManager.add("Home", new BlockPos(120, 70, -48), Level.OVERWORLD, 90, Direction.Axis.X);
            var home = PortalManager.activeTarget();
            expectInvalid(() -> PortalManager.markVerified(home.id(), true));
            expectInvalid(() -> PortalManager.recordDestination(home.id(), new BlockPos(15, 65, -6), Level.OVERWORLD));
            PortalManager.recordDestination(home.id(), new BlockPos(17, 65, -5), Level.NETHER);
            assert !PortalManager.activeTarget().linkVerified() : "Recording a portal must not claim a tested game link";
            assert PortalTracker.getTargetPos().equals(new BlockPos(17, 65, -5));
            assert PortalTracker.getTargetDimension() == Level.NETHER && !PortalTracker.isReturning();
            PortalManager.markVerified(home.id(), true);
            var linked = PortalManager.activeTarget();
            assert linked.source().equals(new BlockPos(120, 70, -48));
            assert linked.plannedDestination().equals(new BlockPos(15, 90, -6));
            assert linked.destination().equals(new BlockPos(17, 65, -5));
            assert linked.hasObservedDestination() && linked.linkVerified();
            assert linked.withName("Renamed").linkVerified();
            assert linked.withBuild(90, Direction.Axis.X).linkVerified() : "No-op build edits must retain the recorded pair";
            assert !linked.withBuild(91, Direction.Axis.X).hasObservedDestination();
            assert !linked.withBuild(91, Direction.Axis.X).linkVerified();
            assert !linked.withObservedDestination(new BlockPos(18, 65, -5)).linkVerified();
            expectInvalid(() -> linked.withObservedDestination(new BlockPos(0, 2032, 0)));
            BlockPos.MutableBlockPos observed = new BlockPos.MutableBlockPos(17, 65, -5);
            var immutable = home.withObservedDestination(observed);
            observed.set(0, 0, 0);
            assert immutable.observedDestination().equals(new BlockPos(17, 65, -5));
            PortalManager.startSession("journey");
            assert PortalManager.activeTarget().equals(linked) && !PortalTracker.isReturning();
            PortalManager.select(home.id(), true);
            assert PortalTracker.isReturning();
            assert PortalTracker.getTargetPos().equals(new BlockPos(120, 70, -48));
            assert PortalTracker.getTargetDimension() == Level.OVERWORLD;
            PortalManager.startSession("journey");
            assert PortalTracker.isReturning() && PortalManager.activeTarget().equals(linked);
            PortalManager.add("Recaptured counterpart", new BlockPos(17, 65, -5), Level.NETHER, null, Direction.Axis.Z);
            assert PortalManager.targets().size() == 1 && PortalTracker.isReturning();
            assert PortalManager.activeTarget().equals(linked);
            PortalManager.delete(home.id());
            assert PortalManager.canUndoDelete() && PortalManager.activeTarget() == null;
            PortalManager.add("Farm", new BlockPos(80, 64, 16), Level.OVERWORLD, null, Direction.Axis.Z);
            var farm = PortalManager.activeTarget();
            PortalManager.undoDelete();
            assert PortalManager.targets().getFirst().equals(linked);
            assert PortalManager.activeTarget().equals(farm) && !PortalTracker.isReturning();
            assert !PortalManager.canUndoDelete();
            PortalManager.select(home.id(), true);
            PortalManager.delete(home.id());
            String saved = Files.readString(file);
            Files.writeString(file, "{ broken");
            try {
                PortalManager.undoDelete();
                throw new AssertionError("Failed undo must report save failure");
            } catch (IOException expected) { }
            assert PortalManager.canUndoDelete() && PortalManager.targets().size() == 1;
            assert Files.readString(file).equals("{ broken");
            Files.writeString(file, saved);
            PortalManager.rename(farm.id(), "Farm edited after delete");
            PortalManager.undoDelete();
            assert PortalManager.activeTarget().equals(linked) && PortalTracker.isReturning();
            assert PortalManager.targets().get(1).name().equals("Farm edited after delete");
            PortalManager.delete(home.id());
            PortalManager.startSession("other");
            assert !PortalManager.canUndoDelete() : "Undo must never cross a connection boundary";
            assert !new TargetStore.Library(PortalManager.targets(), null, true).returning();
            checkUndoEndpointCollisions(file);
            checkUndoKeepsDistinctSourcesWithSharedCounterpart();
            checkUndoKeepsPreexistingReverseRoute();
        } finally {
            PortalManager.endSession();
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void checkUndoEndpointCollisions(java.nio.file.Path file) throws Exception {
        for (boolean opposite : new boolean[]{false, true}) {
            String scope = opposite ? "undo-opposite-collision" : "undo-source-collision";
            PortalManager.startSession(scope);
            PortalManager.add("Deleted home", new BlockPos(120, 70, -48), Level.OVERWORLD, 90, Direction.Axis.X);
            var home = PortalManager.activeTarget();
            PortalManager.recordDestination(home.id(), new BlockPos(17, 65, -5), Level.NETHER);
            PortalManager.markVerified(home.id(), true);
            PortalManager.select(home.id(), true);
            PortalManager.delete(home.id());
            PortalManager.add("Recaptured route", opposite ? new BlockPos(17, 65, -5) : new BlockPos(120, 70, -48),
                    opposite ? Level.NETHER : Level.OVERWORLD, 100, Direction.Axis.Z);
            var replacement = PortalManager.activeTarget();
            PortalManager.rename(replacement.id(), "Keep these later edits");
            replacement = PortalManager.activeTarget();
            if (opposite) PortalManager.unlock();
            String saved = Files.readString(file);
            Files.writeString(file, "{ broken");
            try {
                PortalManager.undoDelete();
                throw new AssertionError("A failed collision merge must retain the undo snapshot");
            } catch (IOException expected) { }
            assert PortalManager.canUndoDelete() && PortalManager.targets().size() == 1;
            assert PortalManager.targets().getFirst().equals(replacement);
            Files.writeString(file, saved);
            PortalTarget resolved = PortalManager.undoDelete();
            assert resolved.equals(replacement) : "Undo must report the existing route kept after recapture";
            assert PortalManager.targets().size() == 1 : "Undo must not duplicate a recaptured endpoint";
            assert PortalManager.activeTarget().equals(replacement) : "Undo must retain newer route metadata";
            assert !PortalTracker.isReturning() : "The opposite-source route must map return navigation to its counterpart";
            assert !PortalManager.canUndoDelete();
            PortalManager.startSession(scope);
            assert PortalManager.targets().equals(java.util.List.of(replacement));
            assert PortalManager.activeTarget().equals(replacement) && !PortalTracker.isReturning();
        }
    }

    private static void checkUndoKeepsDistinctSourcesWithSharedCounterpart() throws Exception {
        PortalManager.startSession("undo-shared-counterpart");
        PortalManager.add("Home", new BlockPos(120, 70, -48), Level.OVERWORLD, null, Direction.Axis.X);
        var home = PortalManager.activeTarget();
        PortalManager.recordDestination(home.id(), new BlockPos(17, 65, -5), Level.NETHER);
        home = PortalManager.activeTarget();
        PortalManager.add("Farm", new BlockPos(80, 64, 16), Level.OVERWORLD, null, Direction.Axis.Z);
        var farm = PortalManager.activeTarget();
        PortalManager.recordDestination(farm.id(), new BlockPos(17, 65, -5), Level.NETHER);
        farm = PortalManager.activeTarget();
        PortalManager.delete(home.id());
        PortalTarget restored = PortalManager.undoDelete();
        assert restored.equals(home) : "Sharing a recorded counterpart must not erase a distinct source route";
        assert PortalManager.targets().equals(java.util.List.of(home, farm));
        assert PortalManager.activeTarget().equals(farm);
        PortalManager.startSession("undo-shared-counterpart");
        assert PortalManager.targets().equals(java.util.List.of(home, farm));
    }

    private static void checkUndoKeepsPreexistingReverseRoute() throws Exception {
        PortalManager.startSession("undo-preexisting-reverse");
        PortalManager.add("Home", new BlockPos(120, 70, -48), Level.OVERWORLD, null, Direction.Axis.X);
        var home = PortalManager.activeTarget();
        // Establish both routes first: recorded endpoint dedup only applies when adding a source.
        PortalManager.add("Existing reverse", new BlockPos(17, 65, -5), Level.NETHER, null, Direction.Axis.Z);
        var reverse = PortalManager.activeTarget();
        PortalManager.recordDestination(reverse.id(), new BlockPos(300, 70, 80), Level.OVERWORLD);
        reverse = PortalManager.activeTarget();
        PortalManager.recordDestination(home.id(), new BlockPos(17, 65, -5), Level.NETHER);
        home = PortalManager.targets().getFirst();
        PortalManager.delete(home.id());
        assert PortalManager.undoDelete().equals(home) : "A preexisting reverse route must not suppress the deleted source";
        assert PortalManager.targets().equals(java.util.List.of(home, reverse));
        assert PortalManager.activeTarget().equals(reverse);
        PortalManager.startSession("undo-preexisting-reverse");
        assert PortalManager.targets().equals(java.util.List.of(home, reverse));
    }

    private static void expectInvalid(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Invalid route transition must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    private interface Action { void run() throws Exception; }
}
