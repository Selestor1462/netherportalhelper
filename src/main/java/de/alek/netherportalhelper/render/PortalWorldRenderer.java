package de.alek.netherportalhelper.render;

import de.alek.netherportalhelper.config.ConfigManager;
import de.alek.netherportalhelper.config.ModConfig;
import de.alek.netherportalhelper.screen.PortalMenuScreen;
import de.alek.netherportalhelper.util.PortalWorldAccess;
import de.alek.netherportalhelper.util.PortalTarget;
import de.alek.netherportalhelper.util.PortalTracker;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Client-only planning guides. All geometry uses vanilla depth testing. */
public final class PortalWorldRenderer {
    private static final double MARKER_DISTANCE_SQUARED = 256.0 * 256.0;
    private static final double FRAME_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final RenderStateDataKey<Preview> PREVIEW = RenderStateDataKey.create();
    private static final GizmoStyle MARKER_STYLE = GizmoStyle.strokeAndFill(0xDC61E7F5, 2.0f, 0x1461E7F5);
    private static final GizmoStyle FRAME_STYLE = GizmoStyle.strokeAndFill(0xB8B999F5, 1.2f, 0x20B999F5);

    private PortalWorldRenderer() { }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(PortalWorldRenderer::extract);
        LevelRenderEvents.BEFORE_GIZMOS.register(PortalWorldRenderer::draw);
    }

    private static void extract(LevelExtractionContext context) {
        FabricRenderState state = (FabricRenderState) context.levelState();
        state.setData(PREVIEW, null);
        Minecraft client = Minecraft.getInstance();
        ModConfig config = ConfigManager.get();
        if (!config.hudVisible || client.gui.hud.isHidden() || client.gui.screen() instanceof PortalMenuScreen || !PortalTracker.isActive()
                || (!config.showTargetMarker && !config.showFramePreview)) return;

        PortalTarget target = PortalTracker.getTarget();
        ClientLevel level = context.level();
        if (target == null || level == null || !level.dimension().equals(target.destinationDimension())) return;
        BlockPos destination = target.destination();
        if (!canPreview(level, destination)) return;

        double distanceSquared = context.levelState().cameraRenderState.pos.distanceToSqr(Vec3.atCenterOf(destination));
        if (distanceSquared > MARKER_DISTANCE_SQUARED) return;
        BlockPos marker = config.showTargetMarker ? destination.immutable() : null;
        List<BlockPos> frame = List.of();
        if (config.showFramePreview && distanceSquared <= FRAME_DISTANCE_SQUARED) {
            // A partial frame could suggest an impossible build, so suppress the whole guide.
            frame = PortalWorldAccess.frameIfValid(destination, target.axis(), block -> canPreview(level, block));
        }
        if (marker != null || !frame.isEmpty()) state.setData(PREVIEW, new Preview(marker, frame));
    }

    private static boolean canPreview(ClientLevel level, BlockPos block) {
        return level.isInsideBuildHeight(block)
                && level.getWorldBorder().isWithinBounds(new AABB(block))
                && PortalWorldAccess.isLoaded(level, block);
    }

    private static void draw(LevelRenderContext context) {
        Preview preview = ((FabricRenderState) context.levelState()).getData(PREVIEW);
        if (preview == null) return;
        // BEFORE_GIZMOS fires immediately before vanilla finalizes this frame's collector.
        // No persistence or always-on-top flags: terrain occludes these translucent guides.
        try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            if (preview.marker() != null) {
                Gizmos.cuboid(new AABB(preview.marker()).inflate(0.006), MARKER_STYLE);
            }
            for (BlockPos block : preview.frame()) {
                Gizmos.cuboid(new AABB(block).deflate(0.005), FRAME_STYLE);
            }
        }
    }

    /** Only immutable extracted positions cross into the drawing phase; no client world is retained. */
    private record Preview(BlockPos marker, List<BlockPos> frame) { }
}
