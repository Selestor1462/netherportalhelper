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
import java.util.UUID;

/** Client-thread portal library commands. Persist first; apply only successful changes. */
public final class PortalManager {
    private static TargetStore store;
    private static String scope;
    private static TargetStore.Library library = TargetStore.Library.empty();

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
        apply(TargetStore.Library.empty());
    }

    public static List<PortalTarget> targets() { return library.targets(); }

    public static PortalTarget activeTarget() {
        return library.targets().stream().filter(target -> target.id().equals(library.activeId())).findFirst().orElse(null);
    }

    public static void add(String name, BlockPos source, ResourceKey<Level> dimension,
                           Integer buildY, Direction.Axis axis) throws IOException {
        PortalTarget target = PortalTarget.create(name, source, dimension, buildY, axis);
        var targets = new ArrayList<>(library.targets());
        targets.add(target);
        persist(new TargetStore.Library(targets, target.id()));
    }

    public static void capture(Minecraft client, String name) throws IOException {
        if (client.player == null || client.level == null || !PortalTracker.isPortalDimension(client.level.dimension())) {
            throw new IllegalArgumentException("A portal dimension is required");
        }
        PortalCapture.Captured captured = null;
        if (client.hitResult instanceof BlockHitResult hit && client.player.distanceToSqr(hit.getLocation())
                <= Math.pow(client.player.blockInteractionRange() + 1, 2)) {
            captured = PortalCapture.capture(client.level, hit.getBlockPos());
        }
        add(name, captured == null ? client.player.blockPosition() : captured.source(), client.level.dimension(),
                null, captured == null ? Direction.Axis.X : captured.axis());
    }

    public static void select(UUID id) throws IOException {
        require(id);
        persist(new TargetStore.Library(library.targets(), id));
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

    public static void delete(UUID id) throws IOException {
        require(id);
        var targets = library.targets().stream().filter(target -> !target.id().equals(id)).toList();
        persist(new TargetStore.Library(targets, id.equals(library.activeId()) ? null : library.activeId()));
    }

    private static PortalTarget require(UUID id) {
        return library.targets().stream().filter(target -> target.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown portal target"));
    }

    private static void replace(PortalTarget updated) throws IOException {
        persist(new TargetStore.Library(library.targets().stream()
                .map(target -> target.id().equals(updated.id()) ? updated : target).toList(), library.activeId()));
    }

    private static void persist(TargetStore.Library updated) throws IOException {
        if (store == null || scope == null) throw new IOException("No world or server identity available");
        store.saveLibrary(scope, updated);
        apply(updated);
    }

    private static void apply(TargetStore.Library updated) {
        library = updated;
        PortalTracker.setTarget(activeTarget());
        HUDOverlay.resetCompassState();
        HUDOverlay.showHints();
    }
}
