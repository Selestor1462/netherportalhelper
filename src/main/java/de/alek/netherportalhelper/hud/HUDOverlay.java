package de.alek.netherportalhelper.hud;

import de.alek.netherportalhelper.Netherportalhelper;
import de.alek.netherportalhelper.config.ConfigManager;
import de.alek.netherportalhelper.config.ModConfig;
import de.alek.netherportalhelper.screen.PortalMenuScreen;
import de.alek.netherportalhelper.util.PortalTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class HUDOverlay {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB5B5B5;
    private static final CompassSmoother COMPASS_SMOOTHER = new CompassSmoother();
    private static final CompassSmoother PREVIEW_SMOOTHER = new CompassSmoother();
    private static long lastCompassUpdateNanos;
    private static long lastPreviewUpdateNanos;
    private static long hintsUntilMillis;

    private HUDOverlay() {
    }

    public static void resetCompassState() {
        COMPASS_SMOOTHER.reset();
        lastCompassUpdateNanos = 0L;
    }

    public static void showHints() {
        hintsUntilMillis = System.currentTimeMillis() + 5_000L;
    }

    public static void resetPreviewState() {
        PREVIEW_SMOOTHER.reset();
        lastPreviewUpdateNanos = 0;
    }

    public static void render(GuiGraphicsExtractor context) {
        ModConfig config = ConfigManager.get();
        if (!config.hudVisible) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.hud.isHidden()
                || client.gui.screen() instanceof PortalMenuScreen) return;
        BlockPos playerPos = client.player.blockPosition();
        ResourceKey<Level> currentDimension = client.level.dimension();
        if (!PortalTracker.isPortalDimension(currentDimension)) return;

        BlockPos target = PortalTracker.isActive() ? PortalTracker.getTargetPos()
                : PortalTracker.calculateCounterpartPosition(playerPos, currentDimension);
        ResourceKey<Level> targetDimension = PortalTracker.isActive() ? PortalTracker.getTargetDimension()
                : PortalTracker.getCounterpartDimension(currentDimension);
        if (target == null || targetDimension == null) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        renderPanel(context, client.font, config, playerPos, currentDimension, target, targetDimension,
                PortalTracker.isActive(), screenWidth, screenHeight, false);

        if (PortalTracker.isActive() && currentDimension == targetDimension) {
            renderCompass(context, client.font, config, client.player, target, screenWidth, screenHeight, null);
        }
    }

    public static void renderPreview(GuiGraphicsExtractor context, Font font, ModConfig config,
                                     int x, int y, int viewportWidth, int viewportHeight, PreviewScene.Scenario scenario) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return;
        if (!config.hudVisible) {
            Component hidden = Component.translatable("config.netherportalhelper.preview_hidden");
            drawPreviewMessage(context, font, hidden, x, y, viewportWidth, viewportHeight);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int sceneWidth = client.getWindow().getGuiScaledWidth();
        int sceneHeight = client.getWindow().getGuiScaledHeight();
        PreviewLayout.Placement placement = PreviewLayout.fit(x, y, viewportWidth, viewportHeight, sceneWidth, sceneHeight);
        // The small overview shows placement. Text is inspected in the separate detail view.
        BlockPos current = scenario == PreviewScene.Scenario.ARRIVED ? new BlockPos(15, 64, -6) : new BlockPos(120, 64, -48);
        BlockPos target = new BlockPos(15, 72, -6);
        if (placement.scale() <= 0.0f) return;
        context.enableScissor(x, y, x + viewportWidth, y + viewportHeight);
        context.pose().pushMatrix();
        context.pose().translate(placement.x(), placement.y());
        context.pose().scale(placement.scale(), placement.scale());
        context.fill(0, 0, sceneWidth, sceneHeight, 0xFF242B30);
        renderPanel(context, font, config, current, Level.NETHER, target, Level.NETHER,
                true, sceneWidth, sceneHeight, true);
        renderCompass(context, font, config, null, target, sceneWidth, sceneHeight, scenario);
        context.pose().popMatrix();
        context.disableScissor();
    }

    public static void renderDetailPreview(GuiGraphicsExtractor context, Font font, ModConfig config,
                                           int x, int y, int width, int height,
                                           PreviewScene.Scenario scenario, boolean compass) {
        if (width <= 0 || height <= 0) return;
        if (!config.hudVisible) {
            drawPreviewMessage(context, font, Component.translatable("config.netherportalhelper.preview_hidden"),
                    x, y, width, height);
            return;
        }
        // A fixed close-up camera keeps changes in the configured scale visible.
        int sceneWidth = compass ? 360 : 420;
        int sceneHeight = compass ? 210 : 170;
        float zoom = Math.min(2.0f, Math.min(width / (float) sceneWidth, height / (float) sceneHeight));
        float textScale = compass ? config.compassTextScale : config.hudScale;
        if (!PreviewLayout.isReadable(zoom, Minecraft.getInstance().getWindow().getGuiScale(), textScale, font.lineHeight)) {
            drawPreviewMessage(context, font, Component.translatable("config.netherportalhelper.preview_resize"),
                    x, y, width, height);
            return;
        }
        ModConfig detail = config.copy();
        detail.hudAnchor = ModConfig.HudAnchor.TOP_LEFT;
        detail.hudPanelX = 12;
        detail.hudPanelY = 12;
        detail.compassCenterY = sceneHeight / 2;
        context.enableScissor(x, y, x + width, y + height);
        context.fill(x, y, x + width, y + height, 0xFF242B30);
        context.pose().pushMatrix();
        context.pose().translate(x + (width - sceneWidth * zoom) / 2, y + (height - sceneHeight * zoom) / 2);
        context.pose().scale(zoom, zoom);
        BlockPos target = new BlockPos(15, 72, -6);
        if (compass) renderCompass(context, font, detail, null, target, sceneWidth, sceneHeight, scenario);
        else renderPanel(context, font, detail, new BlockPos(120, 64, -48), Level.NETHER,
                target, Level.NETHER, true, sceneWidth, sceneHeight, true);
        context.pose().popMatrix();
        context.disableScissor();
    }

    private static void drawPreviewMessage(GuiGraphicsExtractor context, Font font, Component message,
                                           int x, int y, int width, int height) {
        float scale = Math.min(1.0f, Math.min(Math.max(1, width - 8) / (float) Math.max(1, font.width(message)),
                height / (float) Math.max(1, font.lineHeight)));
        context.pose().pushMatrix();
        context.pose().translate(x + width / 2.0f, y + height / 2.0f);
        context.pose().scale(scale, scale);
        context.text(font, message, -font.width(message) / 2, -font.lineHeight / 2, MUTED, false);
        context.pose().popMatrix();
    }

    private static void renderPanel(GuiGraphicsExtractor context, Font font, ModConfig config,
                                    BlockPos current, ResourceKey<Level> currentDimension,
                                    BlockPos target, ResourceKey<Level> targetDimension, boolean locked,
                                    int screenWidth, int screenHeight, boolean preview) {
        var savedTarget = preview ? null : PortalTracker.getTarget();
        Component title = locked && savedTarget != null ? Component.literal(savedTarget.name())
                : Component.translatable("hud.netherportalhelper.title");
        boolean showPosition = config.showCurrentPosition;
        Component positionLabel = rowLabel("position_label", currentDimension);
        String targetKind = savedTarget == null ? "calculated_label" : PortalTracker.isReturning() ? "source_label"
                : savedTarget.hasObservedDestination() ? "recorded_label" : "planned_label";
        Component targetLabel = rowLabel(preview ? "planned_label" : targetKind, targetDimension);
        Component currentLine = coordinateLine(current);
        Component targetLine = coordinateLine(target);
        boolean changeDimension = locked && currentDimension != targetDimension;
        boolean atBuildSite = locked && savedTarget != null && !PortalTracker.isReturning()
                && !savedTarget.hasObservedDestination() && currentDimension == targetDimension
                && current.getX() == target.getX() && current.getZ() == target.getZ();
        Component lockHint = changeDimension
                ? Component.translatable("hud.netherportalhelper.change_dimension", dimensionName(targetDimension))
                : hint(!locked ? "hud.netherportalhelper.lock_hint" : atBuildSite
                        ? "hud.netherportalhelper.build_step" : "key.netherportalhelper.planner",
                        locked ? Netherportalhelper.getPlannerKeyName() : Netherportalhelper.getFreezeKeyName());
        boolean showHint = !locked || config.hudLayout == ModConfig.HudLayout.DETAILED
                || changeDimension || atBuildSite || (!preview && System.currentTimeMillis() < hintsUntilMillis);
        int rowWidth = Math.max(font.width(targetLabel), font.width(targetLine));
        if (showPosition) rowWidth = Math.max(rowWidth, Math.max(font.width(positionLabel), font.width(currentLine)));
        PanelLayout.Size size = PanelLayout.measure(font.width(title), rowWidth,
                font.width(lockHint), showPosition, showHint);
        int width = size.width();
        int height = size.height();
        OverlayLayout.Placement placement = OverlayLayout.place(screenWidth, screenHeight, width, height,
                config.hudPanelX, config.hudPanelY, config.hudScale, config.hudAnchor);
        context.pose().pushMatrix();
        context.pose().translate(placement.x(), placement.y());
        context.pose().scale(placement.scale(), placement.scale());
        int background = withAlpha(0x151515, config.backgroundOpacity);
        int border = withAlpha(0x555555, Math.min(1.0f, config.backgroundOpacity + 0.3f));
        context.fill(0, 0, width, height, background);
        context.fill(0, 0, width, 1, border);
        context.fill(0, height - 1, width, height, border);
        context.fill(0, 0, 1, height, border);
        context.fill(width - 1, 0, width, height, border);
        context.fill(1, 1, 3, height - 1, locked ? 0xFF82C878 : dimensionColor(targetDimension));
        context.text(font, title, 8, 6, WHITE, true);
        int rowY = 21;
        if (showPosition) {
            drawCoordinateRow(context, font, currentDimension, positionLabel, currentLine, rowY, MUTED);
            rowY += 26;
        }
        drawCoordinateRow(context, font, targetDimension, targetLabel, targetLine, rowY,
                locked ? 0xFF82C878 : WHITE);
        if (showHint) context.text(font, lockHint, 8, rowY + 27, MUTED, true);
        context.pose().popMatrix();
    }

    private static Component rowLabel(String key, ResourceKey<Level> dimension) {
        return Component.translatable("hud.netherportalhelper." + key, dimensionName(dimension));
    }

    private static Component dimensionName(ResourceKey<Level> dimension) {
        return Component.translatable(dimension == Level.NETHER
                ? "hud.netherportalhelper.dim_nether" : "hud.netherportalhelper.dim_overworld");
    }

    private static void drawCoordinateRow(GuiGraphicsExtractor context, Font font, ResourceKey<Level> dimension,
                                           Component label, Component coordinates, int rowY, int color) {
        context.pose().pushMatrix();
        context.pose().translate(7, rowY + 3);
        context.pose().scale(0.75f, 0.75f);
        context.item(dimensionIcon(dimension), 0, 0);
        context.pose().popMatrix();
        context.text(font, label, 24, rowY, color, true);
        context.text(font, coordinates, 24, rowY + 11, color, true);
    }

    private static void renderCompass(GuiGraphicsExtractor context, Font font, ModConfig config,
                                      net.minecraft.world.entity.player.Player player, BlockPos target,
                                      int screenWidth, int screenHeight, PreviewScene.Scenario scenario) {
        boolean preview = scenario != null;
        double distance;
        float rawDifference;
        int dy;
        boolean onTarget;
        if (preview) {
            PreviewScene.Sample sample = PreviewScene.sample(scenario, System.nanoTime() / 1_000_000_000.0);
            distance = sample.distance();
            rawDifference = sample.rawDifference();
            dy = sample.heightDifference();
            onTarget = sample.horizontalArrival();
        } else {
            double targetX = target.getX() + 0.5;
            double targetZ = target.getZ() + 0.5;
            distance = Math.sqrt(player.distanceToSqr(targetX, player.getY(), targetZ));
            PortalTracker.NavigationState navigation = PortalTracker.assess(player.blockPosition(), player.level().dimension());
            onTarget = navigation.horizontalArrival();
            dy = navigation.heightDifference();
            double angle = Math.toDegrees(Math.atan2(targetZ - player.getZ(), targetX - player.getX())) - 90.0;
            rawDifference = (float) Mth.wrapDegrees(angle - player.getYRot());
        }

        long now = System.nanoTime();
        long previous = preview ? lastPreviewUpdateNanos : lastCompassUpdateNanos;
        double elapsed = previous == 0 ? 0 : (now - previous) / 1_000_000_000.0;
        if (preview) lastPreviewUpdateNanos = now;
        else lastCompassUpdateNanos = now;
        float difference = (preview ? PREVIEW_SMOOTHER : COMPASS_SMOOTHER)
                .update(rawDifference, config.compassSmoothing, elapsed);

        Component distanceLine = onTarget ? Component.translatable("hud.netherportalhelper.arrived")
                : Component.translatable(distance >= 1000 ? "hud.netherportalhelper.distance_km" : "hud.netherportalhelper.distance",
                        String.format(Locale.ROOT, distance < 100 || distance >= 1000 ? "%.1f" : "%.0f",
                                distance >= 1000 ? distance / 1000 : distance));
        if (config.showYLevelIndicator && dy != 0) {
            Component heightHint = Component.translatable(dy > 0
                    ? "hud.netherportalhelper.height_above_short" : "hud.netherportalhelper.height_below_short", Math.abs(dy));
            distanceLine = Component.translatable("hud.netherportalhelper.distance_height", distanceLine, heightHint);
        }
        Component turnHint = Component.translatable("hud.netherportalhelper.turn_around");
        boolean turnAround = !onTarget && config.showTurnAroundHint
                && Math.abs(difference) >= config.compassBackzoneDegrees;
        // Reserve one compact localized width for the journey; changing height or distance never resizes it.
        int textWidth = compassReservedWidth(font);
        CompassLayout.Placement layout = CompassLayout.calculate(screenWidth, screenHeight, config.compassCenterY,
                Math.max(1, font.width(Component.literal("⬆"))), font.lineHeight, config.compassArrowScale,
                font.lineHeight, textWidth, config.compassTextScale);
        int x1 = layout.x(), y1 = layout.y();
        int x2 = x1 + layout.width(), y2 = y1 + layout.height();
        int background = withAlpha(0x151515, config.backgroundOpacity);
        int border = withAlpha(0x555555, Math.min(1.0f, config.backgroundOpacity + 0.3f));
        context.fill(x1, y1, x2, y2, background);
        context.fill(x1, y1, x2, y1 + 1, border);
        context.fill(x1, y2 - 1, x2, y2, border);
        context.fill(x1, y1, x1 + 1, y2, border);
        context.fill(x2 - 1, y1, x2, y2, border);
        if (onTarget) {
            float checkScale = layout.arrowScale() / 2.0f;
            drawCheck(context, layout.centerX(), layout.arrowCenterY() - Math.round(9 * checkScale), checkScale);
        } else {
            drawCenteredRotatedText(context, font, Component.literal("⬆"), layout.centerX(), layout.arrowCenterY(),
                    blendArrowColor(Math.abs(difference) / 180.0f), layout.arrowScale(), difference);
        }
        float distanceScale = Math.min(layout.textScale(),
                (layout.width() - 12.0f) / Math.max(1, font.width(distanceLine)));
        drawCenteredText(context, font, distanceLine, layout.centerX(), layout.textY(), WHITE, distanceScale);
        if (turnAround) drawCenteredText(context, font, turnHint, layout.centerX(),
                layout.textY() + layout.lineStep(), 0xFFE89A78, layout.textScale());
    }

    private static int compassReservedWidth(Font font) {
        Component sample = Component.translatable("hud.netherportalhelper.distance_height",
                Component.translatable("hud.netherportalhelper.distance", "999"),
                Component.translatable("hud.netherportalhelper.height_above_short", "99"));
        return Math.max(100, Math.max(font.width(sample), font.width(Component.translatable("hud.netherportalhelper.turn_around"))));
    }

    private static Component coordinateLine(BlockPos position) {
        return Component.translatable("hud.netherportalhelper.compact_coordinates",
                position.getX(), position.getY(), position.getZ());
    }

    private static Component hint(String translationKey, String keyName) {
        return Component.translatable("hud.netherportalhelper.hint",
                Component.literal(keyName), Component.translatable(translationKey));
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int dimensionColor(ResourceKey<Level> dimension) {
        return dimension == Level.NETHER ? 0xFFE89A78 : 0xFF82C878;
    }

    private static ItemStack dimensionIcon(ResourceKey<Level> dimension) {
        return new ItemStack(dimension == Level.NETHER ? Items.NETHERRACK : Items.GRASS_BLOCK);
    }

    private static int blendArrowColor(float normalized) {
        float t = Math.max(0.0f, Math.min(1.0f, normalized));
        int startR = 0x82, startG = 0xC8, startB = 0x78;
        int endR = 0xE8, endG = 0x9A, endB = 0x78;
        int r = (int) (startR + (endR - startR) * t);
        int g = (int) (startG + (endG - startG) * t);
        int b = (int) (startB + (endB - startB) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawCheck(GuiGraphicsExtractor context, int centerX, int top, float scale) {
        context.pose().pushMatrix();
        context.pose().translate(centerX, top);
        context.pose().scale(scale, scale);
        context.fill(-9, 10, -4, 14, 0xFF82C878);
        context.fill(-5, 13, -1, 18, 0xFF82C878);
        context.fill(-2, 7, 2, 14, 0xFF82C878);
        context.fill(1, 3, 5, 10, 0xFF82C878);
        context.fill(4, 0, 8, 5, 0xFF82C878);
        context.pose().popMatrix();
    }

    private static void drawCenteredText(GuiGraphicsExtractor context, Font font, Component text,
                                         int x, int y, int color, float scale) {
        int textWidth = font.width(text);
        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        context.pose().scale(scale, scale);
        context.text(font, text, -textWidth / 2, 0, color, false);
        context.pose().popMatrix();
    }

    private static void drawCenteredRotatedText(GuiGraphicsExtractor context, Font font, Component text,
                                                int x, int y, int color, float scale, float rotation) {
        int textWidth = font.width(text);
        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        context.pose().rotate((float) Math.toRadians(rotation));
        context.pose().scale(scale, scale);
        context.text(font, text, -textWidth / 2, -font.lineHeight / 2, color, true);
        context.pose().popMatrix();
    }
}
