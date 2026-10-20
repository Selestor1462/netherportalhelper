package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import de.alek.netherportalhelper.config.ModConfig;
import de.alek.netherportalhelper.config.SettingsScreenLayout;
import de.alek.netherportalhelper.hud.CompassSmoother;
import de.alek.netherportalhelper.hud.OverlayLayout;
import de.alek.netherportalhelper.hud.PreviewLayout;
import de.alek.netherportalhelper.hud.PanelLayout;
import de.alek.netherportalhelper.hud.PreviewScene;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class PortalTrackerRegressionCheck {
    private PortalTrackerRegressionCheck() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        checkNetherToOverworld(-9, -72);
        checkNetherToOverworld(-8, -64);
        checkNetherToOverworld(-1, -8);
        checkNetherToOverworld(0, 0);
        checkNetherToOverworld(9, 72);

        checkOverworldToNether(-9, -2);
        checkOverworldToNether(-8, -1);
        checkOverworldToNether(-1, -1);
        checkOverworldToNether(0, 0);
        checkOverworldToNether(9, 1);

        checkConfigNormalization();
        checkOverlayAnchorsAndFitting();
        checkSettingsLayoutResponsive();
        checkPreviewFitAcrossSizes();
        checkCompassFrameRateIndependence();
        checkCompassWraparound();
        checkCompassSmoothingEndpoints();
        CompassLayoutRegressionCheck.run();
        checkCompactPanelUsesOnlyVisibleContent();
        checkNavigationLifecycle();
        checkTargetPersistenceAndIsolation();
        checkPreviewAspectAndScenarios();
        LocalizationRegressionCheck.run();
        PortalLibraryRegressionCheck.run();
        PortalFrameRegressionCheck.run();
        PortalInputRegressionCheck.run();
        PortalManagerRegressionCheck.run();
    }

    private static void checkNetherToOverworld(int netherCoordinate, int expected) {
        BlockPos actual = PortalTracker.calculateCounterpartPosition(
                new BlockPos(netherCoordinate, 70, netherCoordinate), Level.NETHER
        );
        assert actual.equals(new BlockPos(expected, 70, expected)) : actual;
    }

    private static void checkOverworldToNether(int overworldCoordinate, int expected) {
        BlockPos actual = PortalTracker.calculateCounterpartPosition(
                new BlockPos(overworldCoordinate, 70, overworldCoordinate), Level.OVERWORLD
        );
        assert actual.equals(new BlockPos(expected, 70, expected)) : actual;
    }

    private static void checkConfigNormalization() {
        ModConfig config = new ModConfig();
        config.hudPanelX = -1;
        config.hudScale = Float.POSITIVE_INFINITY;
        config.backgroundOpacity = -1.0f;
        config.compassCenterY = 900;
        config.hudAnchor = null;
        config.hudLayout = null;
        config.normalize();
        assert config.hudPanelX == 0;
        assert config.hudScale == 1.0f;
        assert config.backgroundOpacity == 0.0f;
        assert config.compassCenterY == 400;
        assert config.hudAnchor == ModConfig.HudAnchor.TOP_LEFT;
        assert config.hudLayout == ModConfig.HudLayout.COMPACT;
        assert !config.showCurrentPosition : "New and legacy configurations start with a target-only HUD";
        config.showCurrentPosition = true;
        assert config.copy().showCurrentPosition : "Saving a settings draft must retain the position option";
    }

    private static void checkOverlayAnchorsAndFitting() {
        int[][] sizes = {{180, 100}, {240, 144}, {320, 180}, {640, 360}, {1920, 1080}};
        for (int[] size : sizes) {
            for (ModConfig.HudAnchor anchor : ModConfig.HudAnchor.values()) {
                OverlayLayout.Placement placement = OverlayLayout.place(size[0], size[1], 260, 110,
                        500, 500, 2.0f, anchor);
                assert placement.x() >= 0 && placement.x() + placement.width() <= size[0] : placement;
                assert placement.y() >= 0 && placement.y() + placement.height() <= size[1] : placement;
                assert placement.scale() <= 2.0f : placement;
            }
        }
    }

    private static void checkSettingsLayoutResponsive() {
        int[] widths = {180, 220, 280, 320, 480, 960, 1199, 1200, 1280, 1920};
        int[] heights = {100, 120, 128, 144, 160, 180, 240, 359, 360, 361, 720, 1080};
        for (int width : widths) {
            for (int height : heights) {
                SettingsScreenLayout.Layout layout = SettingsScreenLayout.calculate(width, height);
                assert layout.tabLeft() >= 0 : layout;
                assert layout.tabLeft() + layout.tabWidth() * 2 + layout.tabGap() <= width : layout;
                assert layout.footerY() > layout.controlBottom() : layout;
                assert layout.visibleRows() >= 1 : layout;
                if (layout.stackedFooter()) {
                    assert layout.footerY() + 64 <= height - 4 : layout;
                } else {
                    assert layout.footerY() + 20 <= height - 5 : layout;
                }
                if (layout.previewVisible()) {
                    assert layout.previewCanvasY() >= layout.previewTop() + 13 : layout;
                    if (layout.sideBySide()) {
                        assert layout.previewX() >= layout.controlRight() + 16 : layout;
                        assert layout.previewBottom() <= layout.controlBottom() - 14 : layout;
                    } else {
                        assert layout.previewBottom() <= layout.controlTop() - 14 : layout;
                    }
                    assert layout.previewX() >= 0 : layout;
                    assert layout.previewX() + layout.previewWidth() <= width : layout;
                    assert layout.previewBottom() <= height : layout;
                } else {
                    assert layout.controlTop() >= layout.tabY() + 24 : layout;
                }
                if (layout.visibleRows() > 0) {
                    assert layout.rowHeight() >= 24 : layout;
                    assert layout.controlTop() + layout.visibleRows() * layout.rowHeight()
                            <= layout.controlBottom() + 1 : layout;
                }
                assert layout.visibleRows() <= 4 : layout;
                int contentRight = layout.controlLeft() + 2 * layout.columnWidth() + layout.columnGap();
                assert contentRight <= layout.controlRight() : layout;
            }
        }
    }

    private static void checkPreviewFitAcrossSizes() {
        int[] widths = {180, 220, 280, 320, 480, 960, 1199, 1200, 1280, 1920};
        int[] heights = {128, 144, 160, 180, 240, 359, 360, 361, 720, 1080};
        for (int width : widths) {
            for (int height : heights) {
                SettingsScreenLayout.Layout screen = SettingsScreenLayout.calculate(width, height);
                if (!screen.previewVisible() || screen.previewCanvasHeight() == 0) continue;
                int viewportWidth = Math.max(0, screen.previewWidth() - 2);
                PreviewLayout.Placement preview = PreviewLayout.fit(screen.previewX() + 1, screen.previewCanvasY(),
                        viewportWidth, screen.previewCanvasHeight());
                assert preview.scale() > 0.0f : preview;
                assert preview.x() >= screen.previewX() + 1 : preview;
                assert preview.x() + preview.width() <= screen.previewX() + screen.previewWidth() - 1 + 0.01f : preview;
                assert preview.y() >= screen.previewCanvasY() : preview;
                assert preview.y() + preview.height()
                        <= screen.previewCanvasY() + screen.previewCanvasHeight() + 0.01f : preview;
            }
        }
    }

    private static void checkCompassFrameRateIndependence() {
        float at30 = smoothForOneSecond(30);
        float at60 = smoothForOneSecond(60);
        float at144 = smoothForOneSecond(144);
        assert Math.abs(at30 - at60) < 0.02f : at30 + " vs " + at60;
        assert Math.abs(at60 - at144) < 0.02f : at60 + " vs " + at144;
    }

    private static float smoothForOneSecond(int framesPerSecond) {
        CompassSmoother smoother = new CompassSmoother();
        smoother.update(0.0f, 0.28f, 0.0);
        float value = 0.0f;
        for (int frame = 0; frame < framesPerSecond; frame++) {
            value = smoother.update(120.0f, 0.28f, 1.0 / framesPerSecond);
        }
        return value;
    }

    private static void checkCompassWraparound() {
        CompassSmoother smoother = new CompassSmoother();
        smoother.update(179.0f, 0.28f, 0.0);
        float value = smoother.update(-179.0f, 0.28f, 1.0 / 60.0);
        assert Math.abs(value) > 170.0f : value;
    }

    private static void checkCompassSmoothingEndpoints() {
        CompassSmoother direct = new CompassSmoother();
        direct.update(0, 0, 0);
        assert direct.update(90, 0, 1.0 / 60) == 90 : "Zero smoothing must track the target immediately";
        CompassSmoother smooth = new CompassSmoother();
        smooth.update(0, 1, 0);
        float first = smooth.update(90, 1, 1.0 / 60);
        assert first > 0 && first < 90 : "Maximum smoothing must move without snapping or freezing: " + first;
        float value = first;
        for (int frame = 0; frame < 180; frame++) value = smooth.update(90, 1, 1.0 / 60);
        assert Math.abs(value - 90) < 0.01f : value;
    }

    private static void checkCompactPanelUsesOnlyVisibleContent() {
        PanelLayout.Size compact = PanelLayout.measure(80, 180, 400, false, false);
        PanelLayout.Size detailed = PanelLayout.measure(80, 180, 400, true, true);
        assert compact.width() < detailed.width() : "Hidden hints must not widen the compact panel";
        assert compact.height() < detailed.height() : "Compact mode must reclaim the hint area";
        assert compact.width() >= 180 + 32 : "Coordinate icons and padding must still fit";
    }

    private static void checkNavigationLifecycle() {
        BlockPos source = new BlockPos(120, 72, -48);
        BlockPos target = PortalTracker.calculateCounterpartPosition(source, Level.OVERWORLD);
        PortalTracker.clear();
        assert !PortalTracker.assess(target, Level.NETHER).navigating();
        assert PortalTracker.lockTarget(target, Level.NETHER);
        assert !PortalTracker.assess(source, Level.OVERWORLD).navigating();
        var highAbove = PortalTracker.assess(new BlockPos(15, 120, -6), Level.NETHER);
        assert highAbove.navigating() && highAbove.horizontalArrival();
        assert highAbove.heightDifference() == -48 : "Horizontal arrival must retain height information";
        assert !PortalTracker.assess(new BlockPos(16, 72, -6), Level.NETHER).horizontalArrival();
        PortalTracker.toggleFreeze(null, null);
        assert !PortalTracker.assess(target, Level.NETHER).navigating();
        assert PortalTracker.getTargetPos() == null;
    }

    private static void checkTargetPersistenceAndIsolation() throws Exception {
        Path directory = Files.createTempDirectory("portal-target-regression-");
        Path file = directory.resolve("targets.json");
        try {
            TargetStore store = new TargetStore(file);
            String serverA = TargetStore.serverKey("EXAMPLE.org:25565");
            String serverB = TargetStore.serverKey("other.example.org");
            String worldA = TargetStore.worldKey(directory.resolve("world-a"));
            String worldB = TargetStore.worldKey(directory.resolve("world-b"));
            assert serverA.equals(TargetStore.serverKey("example.org"));
            assert !serverA.equals(serverB) && !worldA.equals(worldB);
            var saved = new TargetStore.SavedTarget(new BlockPos(120, 72, -48), Level.OVERWORLD,
                    new BlockPos(15, 72, -6), Level.NETHER);
            assert store.load(serverA).isEmpty();
            store.save(serverA, saved);
            store.save(worldA, saved);
            TargetStore reopened = new TargetStore(file);
            assert reopened.load(serverA).orElseThrow().equals(saved);
            assert reopened.load(serverB).isEmpty() && reopened.load(worldB).isEmpty();
            reopened.save(serverA, null);
            assert reopened.load(serverA).isEmpty();
            assert reopened.load(worldA).orElseThrow().equals(saved) : "Unlocking one world must preserve another";
            String damaged = "{ broken JSON";
            Files.writeString(file, damaged);
            try {
                reopened.load(worldA);
                throw new AssertionError("Corrupt storage must report a read failure");
            } catch (IOException expected) { }
            try {
                reopened.save(worldA, saved);
                throw new AssertionError("Corrupt storage must not be silently overwritten");
            } catch (IOException expected) { }
            assert Files.readString(file).equals(damaged);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void checkPreviewAspectAndScenarios() {
        var fit = PreviewLayout.fit(10, 20, 800, 200, 960, 540);
        assert Math.abs(fit.width() / fit.height() - 16.0 / 9) < 0.001 : fit;
        assert fit.x() >= 10 && fit.x() + fit.width() <= 810;
        assert fit.y() >= 20 && fit.y() + fit.height() <= 220;
        var first = PreviewScene.sample(PreviewScene.Scenario.TRAVELLING, 0);
        var moved = PreviewScene.sample(PreviewScene.Scenario.TRAVELLING, 0.5);
        assert first.rawDifference() != moved.rawDifference() : "The preview must animate to demonstrate smoothing";
        var behind = PreviewScene.sample(PreviewScene.Scenario.BEHIND, 0);
        assert Math.abs(behind.rawDifference()) >= 165 && !behind.horizontalArrival();
        var arrived = PreviewScene.sample(PreviewScene.Scenario.ARRIVED, 0);
        assert arrived.horizontalArrival() && arrived.heightDifference() != 0;
        var fullHdAtGui1 = PreviewLayout.fit(0, 0, 780, 220, 1920, 1080);
        assert !PreviewLayout.isReadable(fullHdAtGui1.scale(), 1, 1, 9)
                : "A full-HD miniature at GUI scale 1 needs a legibility fallback";
        var fullHdAtGui3 = PreviewLayout.fit(0, 0, 620, 150, 640, 360);
        assert PreviewLayout.isReadable(fullHdAtGui3.scale(), 3, 1, 9);
        assert !PreviewLayout.isReadable(0, 3, 1, 9);
    }
}
