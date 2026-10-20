package de.alek.netherportalhelper.util;

import de.alek.netherportalhelper.hud.HUDOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Client-thread portal library commands. Persist first; apply only successful changes. */
public final class PortalManager {
    private static TargetStore store;
    private static String scope;
    private static TargetStore.Library library = TargetStore.Library.empty();
    private static DeletedTarget undoDelete;

    private PortalManager() { }

    public static void initialize(TargetStore targetStore) {
        store = targetStore;
        endSession();
    }

    public static void startSession(String worldScope) throws IOException {
        endSession();
        scope = worldScope;
        if (scope != null) apply(store.loadLibrary(scope));
    }

    public static void endSession() {
        scope = null;
        undoDelete = null;
        apply(TargetStore.Library.empty());
    }

    public static List<PortalTarget> targets() { return library.targets(); }

    public static PortalTarget activeTarget() {
        return library.targets().stream().filter(target -> target.id().equals(library.activeId())).findFirst().orElse(null);
    }

    public static void add(String name, BlockPos source, ResourceKey<Level> dimension,
                           Integer buildY, Direction.Axis axis) throws IOException {
        PortalTarget existing = findEndpoint(source, dimension);
        if (existing != null) {
            select(existing.id(), dimension != existing.sourceDimension());
            return;
        }
        PortalTarget target = PortalTarget.create(name, source, dimension, buildY, axis);
        var targets = new ArrayList<>(library.targets());
        targets.add(target);
        persist(new TargetStore.Library(targets, target.id()));
    }

    public static CaptureResult capture(Minecraft client, String name) throws IOException {
        if (client.player == null || client.level == null || !PortalTracker.isPortalDimension(client.level.dimension())) {
            throw new IllegalArgumentException("A portal dimension is required");
        }
        PortalCapture.Captured captured = aimedPortal(client);
        BlockPos position = captured == null ? client.player.blockPosition() : captured.source();
        boolean duplicate = findEndpoint(position, client.level.dimension()) != null;
        add(name, position, client.level.dimension(),
                null, captured == null ? Direction.Axis.X : captured.axis());
        return new CaptureResult(activeTarget(), duplicate, captured == null);
    }

    public record CaptureResult(PortalTarget target, boolean duplicate, boolean positionFallback) { }

    private static PortalCapture.Captured aimedPortal(Minecraft client) {
        if (client.player != null && client.level != null && client.hitResult instanceof BlockHitResult hit
                && client.player.distanceToSqr(hit.getLocation()) <= Math.pow(client.player.blockInteractionRange() + 1, 2)) {
            return PortalCapture.capture(client.level, hit.getBlockPos());
        }
        return null;
    }

    private static PortalTarget findEndpoint(BlockPos position, ResourceKey<Level> dimension) {
        return library.targets().stream().filter(target -> target.sourceDimension() == dimension && target.source().equals(position))
                .findFirst().orElseGet(() -> library.targets().stream()
                        .filter(target -> target.destinationDimension() == dimension && position != null && position.equals(target.observedDestination()))
                        .findFirst().orElse(null));
    }

    public static void select(UUID id) throws IOException {
        select(id, false);
    }

    public static void select(UUID id, boolean returning) throws IOException {
        require(id);
        persist(new TargetStore.Library(library.targets(), id, returning));
    }

    public static void unlock() throws IOException { persist(new TargetStore.Library(library.targets(), null)); }

    public static void rename(UUID id, String name) throws IOException {
        PortalTarget previous = require(id);
        replace(previous.withName(name));
    }

    public static void updateBuild(UUID id, Integer buildY, Direction.Axis axis) throws IOException {
        replace(require(id).withBuild(buildY, axis));
    }

    public static void update(UUID id, String name, Integer buildY, Direction.Axis axis) throws IOException {
        replace(require(id).withName(name).withBuild(buildY, axis));
    }

    public static void recordDestination(UUID id, BlockPos position, ResourceKey<Level> dimension) throws IOException {
        PortalTarget target = require(id);
        if (dimension != target.destinationDimension()) throw new IllegalArgumentException("Record the counterpart in the opposite dimension");
        if (position == null) throw new IllegalArgumentException("Aim at a loaded Nether portal to record the counterpart");
        replace(target.withObservedDestination(position));
    }

    public static void recordDestination(UUID id, Minecraft client) throws IOException {
        PortalTarget target = require(id);
        if (client.level == null || client.level.dimension() != target.destinationDimension()) {
            throw new IllegalArgumentException("Record the counterpart in the opposite dimension");
        }
        PortalCapture.Captured captured = aimedPortal(client);
        if (captured == null) throw new IllegalArgumentException("Aim at a loaded Nether portal to record the counterpart");
        recordDestination(id, captured.source(), client.level.dimension());
    }

    public static void markVerified(UUID id, boolean verified) throws IOException {
        replace(require(id).withLinkVerified(verified));
    }

    public static void delete(UUID id) throws IOException {
        PortalTarget deleted = require(id);
        DeletedTarget snapshot = new DeletedTarget(deleted, library.targets().indexOf(deleted),
                id.equals(library.activeId()), library.returning(),
                library.targets().stream().map(PortalTarget::id).collect(Collectors.toUnmodifiableSet()));
        var targets = library.targets().stream().filter(target -> !target.id().equals(id)).toList();
        persist(new TargetStore.Library(targets, id.equals(library.activeId()) ? null : library.activeId(), library.returning()));
        undoDelete = snapshot;
    }

    public static boolean canUndoDelete() { return undoDelete != null; }

    /** Restores the deleted route, or keeps a newer route already using one of its endpoints. */
    public static PortalTarget undoDelete() throws IOException {
        if (undoDelete == null) throw new IllegalArgumentException("No deleted portal to restore");
        DeletedTarget snapshot = undoDelete;
        var targets = new ArrayList<>(library.targets());
        // Older routes can legitimately share endpoints; only a subsequent recapture replaces this deletion.
        PortalTarget restored = targets.stream().filter(target -> target.id().equals(snapshot.target().id())
                || !snapshot.existingIds().contains(target.id()) && sharesEndpoint(target, snapshot.target()))
                .findFirst().orElse(null);
        if (restored == null) {
            restored = snapshot.target();
            targets.add(Math.min(snapshot.index(), targets.size()), restored);
        }
        boolean restoreActive = snapshot.active() && library.activeId() == null;
        ResourceKey<Level> previousNavigationDimension = snapshot.returning()
                ? snapshot.target().sourceDimension() : snapshot.target().destinationDimension();
        persist(new TargetStore.Library(targets, restoreActive ? restored.id() : library.activeId(),
                restoreActive ? restored.sourceDimension() == previousNavigationDimension : library.returning()));
        undoDelete = null;
        return restored;
    }

    private static boolean sharesEndpoint(PortalTarget first, PortalTarget second) {
        return hasEndpoint(first, second.source(), second.sourceDimension())
                || second.hasObservedDestination() && first.sourceDimension() == second.destinationDimension()
                && first.source().equals(second.observedDestination());
    }

    private static boolean hasEndpoint(PortalTarget target, BlockPos position, ResourceKey<Level> dimension) {
        return target.sourceDimension() == dimension && target.source().equals(position)
                || target.destinationDimension() == dimension && position.equals(target.observedDestination());
    }

    private record DeletedTarget(PortalTarget target, int index, boolean active, boolean returning, Set<UUID> existingIds) { }

    private static PortalTarget require(UUID id) {
        return library.targets().stream().filter(target -> target.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown portal target"));
    }

    private static void replace(PortalTarget updated) throws IOException {
        persist(new TargetStore.Library(library.targets().stream()
                .map(target -> target.id().equals(updated.id()) ? updated : target).toList(), library.activeId(), library.returning()));
    }

    private static void persist(TargetStore.Library updated) throws IOException {
        if (store == null || scope == null) throw new IOException("No world or server identity available");
        store.saveLibrary(scope, updated);
        apply(updated);
    }

    private static void apply(TargetStore.Library updated) {
        library = updated;
        PortalTracker.setTarget(activeTarget(), updated.returning());
        HUDOverlay.resetCompassState();
        HUDOverlay.showHints();
    }
}
