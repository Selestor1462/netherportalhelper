package de.alek.netherportalhelper.util;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PortalLibraryRegressionCheck {
    private PortalLibraryRegressionCheck() { }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        run();
    }

    public static void run() throws Exception {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(12, 72, -4);
        PortalTracker.lockTarget(mutable, Level.NETHER);
        mutable.set(99, 99, 99);
        assert PortalTracker.getTargetPos().equals(new BlockPos(12, 72, -4))
                : "Locked destination must not follow a mutable position";
        PortalTracker.clear();
        checkReferenceAndBuildHeight();
        checkValidationAndImmutableLibrary();
        Path directory = Files.createTempDirectory("portal-library-");
        Path file = directory.resolve("targets.json");
        try {
            checkLibraryRoundTrip(file);
            checkMigration(file);
            checkVersionTwoMigration(file);
            checkInvalidFilesPreserved(file);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
            PortalTracker.clear();
        }
    }

    private static void checkReferenceAndBuildHeight() {
        BlockPos.MutableBlockPos source = new BlockPos.MutableBlockPos(-9, 72, 120);
        PortalTarget target = PortalTarget.create("  Farm  ", source, Level.OVERWORLD, 90, Direction.Axis.Z);
        source.set(0, 0, 0);
        assert target.name().equals("Farm");
        assert target.source().equals(new BlockPos(-9, 72, 120));
        assert target.calculatedDestination().equals(new BlockPos(-2, 72, 15));
        assert target.destination().equals(new BlockPos(-2, 90, 15));
        assert target.destinationDimension() == Level.NETHER;
        PortalTarget edited = target.withName("Mine").withBuild(110, Direction.Axis.X);
        assert edited.id().equals(target.id()) && edited.source().equals(target.source());
        assert edited.calculatedDestination().getY() == 72 && edited.destination().getY() == 110;
        assert edited.withBuild(null, Direction.Axis.Z).destination().getY() == 72;
        PortalTracker.setTarget(edited);
        assert PortalTracker.getTarget().equals(edited);
        assert PortalTracker.getTargetPos().equals(new BlockPos(-2, 110, 15));
        assert PortalTracker.assess(new BlockPos(-2, 100, 15), Level.NETHER).heightDifference() == 10;
        PortalTracker.clear();
        assert PortalTracker.getTarget() == null && !PortalTracker.isActive();
        BlockPos.MutableBlockPos destination = new BlockPos.MutableBlockPos(1, 64, 2);
        PortalTarget copied = new PortalTarget(UUID.randomUUID(), "Copied", new BlockPos(8, 64, 16),
                Level.OVERWORLD, destination, null, Direction.Axis.X);
        destination.set(5, 80, 9);
        assert copied.calculatedDestination().equals(new BlockPos(1, 64, 2));
    }

    private static void checkValidationAndImmutableLibrary() {
        PortalTarget valid = PortalTarget.create("A", new BlockPos(8, 64, 16), Level.OVERWORLD,
                null, Direction.Axis.X);
        for (String name : List.of("", "  ", "x".repeat(65))) {
            expectInvalid(() -> valid.withName(name));
        }
        expectInvalid(() -> valid.withBuild(2032, Direction.Axis.X));
        expectInvalid(() -> valid.withBuild(-2033, Direction.Axis.X));
        expectInvalid(() -> valid.withBuild(64, Direction.Axis.Y));
        expectInvalid(() -> PortalTarget.create("A", new BlockPos(Integer.MAX_VALUE, 64, 0),
                Level.NETHER, null, Direction.Axis.X));
        expectInvalid(() -> PortalTarget.create("A", new BlockPos(29_999_984, 64, 0),
                Level.NETHER, null, Direction.Axis.X));
        expectInvalid(() -> PortalTarget.create("A", new BlockPos(0, 2032, 0),
                Level.OVERWORLD, null, Direction.Axis.X));
        expectInvalid(() -> PortalTarget.create("A", BlockPos.ZERO, Level.END, null, Direction.Axis.X));
        expectInvalid(() -> new TargetStore.Library(List.of(valid, valid), valid.id()));
        expectInvalid(() -> new TargetStore.Library(List.of(valid), UUID.randomUUID()));
        ArrayList<PortalTarget> input = new ArrayList<>(List.of(valid));
        TargetStore.Library library = new TargetStore.Library(input, valid.id());
        input.clear();
        assert library.targets().size() == 1;
        try {
            library.targets().clear();
            throw new AssertionError("Library targets must be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    private static void checkLibraryRoundTrip(Path file) throws Exception {
        TargetStore store = new TargetStore(file);
        PortalTarget farm = PortalTarget.create("Farm", new BlockPos(120, 72, -48),
                Level.OVERWORLD, 90, Direction.Axis.Z);
        PortalTarget mine = PortalTarget.create("Mine", new BlockPos(-12, 60, 24),
                Level.NETHER, null, Direction.Axis.X);
        store.saveLibrary("world-a", new TargetStore.Library(List.of(farm, mine), mine.id()));
        store.saveLibrary("world-b", new TargetStore.Library(List.of(farm), farm.id()));
        TargetStore reopened = new TargetStore(file);
        assert reopened.loadLibrary("world-a").equals(new TargetStore.Library(List.of(farm, mine), mine.id()));
        assert reopened.loadLibrary("world-c").equals(TargetStore.Library.empty());
        PortalTarget renamed = farm.withName("Updated").withBuild(100, Direction.Axis.X);
        reopened.saveLibrary("world-a", new TargetStore.Library(List.of(renamed), renamed.id()));
        assert reopened.loadLibrary("world-a").targets().equals(List.of(renamed));
        assert reopened.loadLibrary("world-b").targets().equals(List.of(farm));
        reopened.save("world-a", null);
        assert reopened.load("world-a").isEmpty();
        assert reopened.loadLibrary("world-a").targets().equals(List.of(renamed));
        reopened.saveLibrary("world-a", TargetStore.Library.empty());
        assert reopened.loadLibrary("world-a").targets().isEmpty();
        assert reopened.loadLibrary("world-b").activeId().equals(farm.id());
    }

    private static void checkMigration(Path file) throws Exception {
        String old = """
                {"version":1,"targets":{
                  "world-a":{"source":{"dimension":"overworld","x":120,"y":72,"z":-48},
                    "destination":{"dimension":"nether","x":15,"y":65,"z":-6}},
                  "world-b":{"source":{"dimension":"nether","x":-2,"y":60,"z":3},
                    "destination":{"dimension":"overworld","x":-16,"y":61,"z":24}}
                }}
                """;
        Files.writeString(file, old);
        TargetStore store = new TargetStore(file);
        TargetStore.Library first = store.loadLibrary("world-a");
        PortalTarget target = first.targets().getFirst();
        assert first.activeId().equals(target.id());
        assert target.name().equals("Portal") && target.buildY() == null && target.axis() == Direction.Axis.X;
        assert target.source().getY() == 72 && target.calculatedDestination().getY() == 65;
        assert store.loadLibrary("world-a").equals(first) : "Migration IDs must be stable before saving";
        TargetStore.Library sibling = store.loadLibrary("world-b");
        store.saveLibrary("world-a", new TargetStore.Library(List.of(target.withName("Renamed")), target.id()));
        assert JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("version").getAsInt() == 3;
        assert !target.hasObservedDestination() && !target.linkVerified() && !first.returning();
        assert new TargetStore(file).loadLibrary("world-b").equals(sibling);
        assert store.load("world-b").orElseThrow().destination().equals(new BlockPos(-16, 61, 24));
    }

    private static void checkInvalidFilesPreserved(Path file) throws Exception {
        TargetStore store = new TargetStore(file);
        PortalTarget valid = PortalTarget.create("A", BlockPos.ZERO, Level.OVERWORLD, null, Direction.Axis.X);
        store.saveLibrary("world-a", new TargetStore.Library(List.of(valid), valid.id()));
        String validDocument = Files.readString(file);
        List<String> invalid = List.of("{ broken JSON", "{version:2,targets:{}}", "{\"version\":9,\"targets\":{}}",
                validDocument.replace("\"x\": 0", "\"x\": 4294967296"),
                validDocument.replace("\"x\": 0", "\"x\": 0.25"),
                validDocument.replace("\"axis\": \"X\"", "\"axis\": \"Y\""),
                validDocument.replace("\"activeId\": \"" + valid.id(), "\"activeId\": \"" + UUID.randomUUID()));
        for (String damaged : invalid) {
            Files.writeString(file, damaged);
            expectIo(() -> store.loadLibrary("world-a"));
            expectIo(() -> store.saveLibrary("world-b", TargetStore.Library.empty()));
            assert Files.readString(file).equals(damaged) : "Invalid data must survive failed read and write";
        }
    }

    private static void checkVersionTwoMigration(Path file) throws Exception {
        String fixture = """
                {"version":2,"targets":{"world-a":{"activeId":"00000000-0000-0000-0000-000000000001",
                  "targets":[{"id":"00000000-0000-0000-0000-000000000001","name":"Old plan",
                  "source":{"dimension":"overworld","x":120,"y":72,"z":-48},
                  "calculatedDestination":{"dimension":"nether","x":15,"y":72,"z":-6},
                  "buildY":90,"axis":"Z"}]}}}
                """;
        Files.writeString(file, fixture);
        TargetStore store = new TargetStore(file);
        TargetStore.Library migrated = store.loadLibrary("world-a");
        PortalTarget target = migrated.targets().getFirst();
        assert !migrated.returning() && !target.hasObservedDestination() && !target.linkVerified();
        assert target.destination().equals(new BlockPos(15, 90, -6));
        PortalTarget observed = target.withObservedDestination(new BlockPos(17, 65, -5)).withLinkVerified(true);
        store.saveLibrary("world-b", new TargetStore.Library(List.of(observed), observed.id(), true));
        assert new TargetStore(file).loadLibrary("world-a").equals(migrated) : "Saving another scope must preserve migrated plans";
        assert new TargetStore(file).loadLibrary("world-b").equals(new TargetStore.Library(List.of(observed), observed.id(), true));
        String valid = Files.readString(file);
        var damaged = JsonParser.parseString(valid).getAsJsonObject();
        damaged.getAsJsonObject("targets").getAsJsonObject("world-b").getAsJsonArray("targets")
                .get(0).getAsJsonObject().getAsJsonObject("observedDestination").addProperty("dimension", "overworld");
        Files.writeString(file, damaged.toString());
        expectIo(() -> store.loadLibrary("world-a"));
        expectIo(() -> store.saveLibrary("world-a", migrated));
        assert Files.readString(file).equals(damaged.toString()) : "An invalid sibling endpoint must never be overwritten";
        Files.writeString(file, valid);
    }

    private static void expectInvalid(CheckedAction action) {
        try {
            action.run();
            throw new AssertionError("Invalid portal data must be rejected");
        } catch (IllegalArgumentException expected) {
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void expectIo(CheckedAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Malformed storage must report IOException");
        } catch (IOException expected) { }
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }
}
