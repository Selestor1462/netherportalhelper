package de.alek.netherportalhelper.hud;

import de.alek.netherportalhelper.Netherportalhelper;
import de.alek.netherportalhelper.config.ConfigManager;
import de.alek.netherportalhelper.config.ModConfig;
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

import java.util.ArrayList;
import java.util.List;
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
        if (client.player == null || client.level == null) return;
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
        if (!PreviewLayout.isReadable(placement.scale(), client.getWindow().getGuiScale(),
                Math.min(config.hudScale, config.compassTextScale), font.lineHeight)) {
            drawPreviewMessage(context, font, Component.translatable("config.netherportalhelper.preview_resize"),
                    x, y, viewportWidth, viewportHeight);
            return;
        }
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
        Component status = Component.translatable(locked ? "hud.netherportalhelper.locked" : "hud.netherportalhelper.ready");
        Component currentLine = coordinateLine(currentDimension, current);
        Component targetLine = coordinateLine(targetDimension, target);
        if (savedTarget != null && savedTarget.buildY() != null) {
            targetLine = targetLine.copy().append(Component.translatable("hud.netherportalhelper.reference_y",
                    Integer.toString(savedTarget.calculatedDestination().getY())));
        }
        Component lockHint = hint(locked ? "hud.netherportalhelper.unlock_hint" : "hud.netherportalhelper.lock_hint",
                Netherportalhelper.getFreezeKeyName());
        Component visibilityHint = hint("hud.netherportalhelper.hide_hint", Netherportalhelper.getHideKeyName());
        Component plannerHint = hint("key.netherportalhelper.planner", Netherportalhelper.getPlannerKeyName());
        Component reference = Component.translatable("hud.netherportalhelper.reference");
        Component linkNotice = Component.translatable("hud.netherportalhelper.link_notice");

        int padding = 7;
        int iconAndGap = 26;
        int rowY = 27;
        int separatorY = 66;
        int hintsY = 72;
        boolean showHints = config.hudLayout == ModConfig.HudLayout.DETAILED
                || (!preview && System.currentTimeMillis() < hintsUntilMillis);
        PanelLayout.Size size = PanelLayout.measure(font.width(title), font.width(status),
                Math.max(font.width(currentLine), font.width(targetLine)),
                Math.max(font.width(plannerHint), Math.max(font.width(lockHint), font.width(visibilityHint))),
                Math.max(font.width(reference), font.width(linkNotice)), showHints);
        int width = size.width();
        int height = size.height();

        OverlayLayout.Placement placement = OverlayLayout.place(screenWidth, screenHeight, width, height,
                config.hudPanelX, config.hudPanelY, config.hudScale, config.hudAnchor);
        context.pose().pushMatrix();
        context.pose().translate(placement.x(), placement.y());
        context.pose().scale(placement.scale(), placement.scale());

        int background = withAlpha(0x151515, config.backgroundOpacity);
        int border = withAlpha(0x555555, Math.min(1.0f, config.backgroundOpacity + 0.3f));
        int accent = dimensionColor(currentDimension);
        context.fill(0, 0, width, height, background);
        context.fill(0, 0, width, 1, border);
        context.fill(0, height - 1, width, height, border);
        context.fill(0, 0, 1, height, border);
        context.fill(width - 1, 0, width, height, border);
        context.fill(1, 1, 3, height - 1, accent);

        context.text(font, title, padding + 2, 6, WHITE, false);
        int statusWidth = font.width(status);
        context.fill(width - padding - statusWidth - 8, 5,
                width - padding, 19, withAlpha(locked ? 0x1B3820 : 0x282828, 1.0f));
        context.text(font, status, width - padding - statusWidth - 4, 8, locked ? 0xFF82C878 : MUTED, false);

        context.item(dimensionIcon(currentDimension), padding, rowY);
        context.item(dimensionIcon(targetDimension), padding, rowY + 19);
        context.text(font, currentLine, padding + iconAndGap, rowY + 4, WHITE, false);
        context.text(font, targetLine, padding + iconAndGap, rowY + 23, locked ? 0xFF82C878 : MUTED, false);
        if (showHints) {
            context.fill(padding, separatorY, width - padding, separatorY + 1, border);
            context.text(font, lockHint, padding + 2, hintsY, MUTED, false);
            context.text(font, visibilityHint, padding + 2, hintsY + 13, MUTED, false);
            context.text(font, plannerHint, padding + 2, hintsY + 26, MUTED, false);
            context.text(font, reference, padding + 2, hintsY + 41, MUTED, false);
            context.text(font, linkNotice, padding + 2, hintsY + 54, MUTED, false);
        }
        context.pose().popMatrix();
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

        List<Component> lines = new ArrayList<>(3);
        if (onTarget) {
            lines.add(Component.translatable("hud.netherportalhelper.arrived"));
        } else {
            lines.add(Component.translatable("hud.netherportalhelper.distance",
                    Component.literal(String.format(Locale.ROOT, "%.1f", distance))));
            if (config.showTurnAroundHint && Math.abs(difference) >= config.compassBackzoneDegrees) {
                lines.add(Component.translatable("hud.netherportalhelper.turn_around"));
            }
        }
        if (config.showYLevelIndicator && Math.abs(dy) > 3) {
            lines.add(Component.translatable(dy > 0 ? "hud.netherportalhelper.y_above" : "hud.netherportalhelper.y_below",
                    Component.literal(Integer.toString(Math.abs(dy)))));
        }

        float arrowScale = config.compassArrowScale;
        float maxLineScale = config.compassTextScale;
        float checkScale = 1.0f;
        int maxLineWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        float availableTextWidth = Math.max(0.0f, screenWidth - 32.0f);
        if (maxLineWidth > 0) maxLineScale = Math.min(maxLineScale, availableTextWidth / maxLineWidth);
        int arrowGlyphWidth = Math.max(1, font.width(Component.literal("⬆")));
        arrowScale = Math.min(arrowScale, availableTextWidth / arrowGlyphWidth);

        int preferredContentHeight = (onTarget ? 20 : Math.round(font.lineHeight * arrowScale))
                + (lines.isEmpty() ? 0 : 5 + Math.round(lines.size() * (font.lineHeight + 2) * maxLineScale));
        int availableContentHeight = Math.max(0, screenHeight - 20);
        if (preferredContentHeight > availableContentHeight && preferredContentHeight > 0) {
            float fit = availableContentHeight / (float) preferredContentHeight;
            arrowScale *= fit;
            maxLineScale *= fit;
            checkScale *= fit;
        }

        int arrowHeight = onTarget ? Math.round(20 * checkScale) : Math.round(font.lineHeight * arrowScale);
        int contentHeight = arrowHeight + (lines.isEmpty() ? 0 : 5 + Math.round(lines.size() * (font.lineHeight + 2) * maxLineScale));
        int arrowWidth = onTarget ? Math.round(18 * checkScale)
                : Math.round(font.width(Component.literal("⬆")) * arrowScale);
        int panelWidth = Math.max(1, Math.min(Math.max(1, screenWidth - 8),
                Math.max(arrowWidth, Math.round(maxLineWidth * maxLineScale)) + 24));
        int panelHeight = contentHeight + 12;
        int centerX = screenWidth / 2;
        int leftMargin = Math.min(4, Math.max(0, (screenWidth - panelWidth) / 2));
        int x1 = Math.max(leftMargin, Math.min(screenWidth - panelWidth - leftMargin, centerX - panelWidth / 2));
        int x2 = x1 + panelWidth;
        int topMargin = Math.min(4, Math.max(0, (screenHeight - panelHeight) / 2));
        int y1 = Math.max(topMargin, Math.min(screenHeight - panelHeight - topMargin,
                config.compassCenterY - panelHeight / 2));
        int y2 = y1 + panelHeight;
        int background = withAlpha(0x151515, config.backgroundOpacity);
        int border = withAlpha(0x555555, Math.min(1.0f, config.backgroundOpacity + 0.3f));
        context.fill(x1, y1, x2, y2, background);
        context.fill(x1, y1, x2, y1 + 1, border);
        context.fill(x1, y2 - 1, x2, y2, border);
        context.fill(x1, y1, x1 + 1, y2, border);
        context.fill(x2 - 1, y1, x2, y2, border);

        if (onTarget) {
            drawCheck(context, centerX, y1 + 4, checkScale);
        } else {
            drawCenteredRotatedText(context, font, Component.literal("⬆"), centerX, y1 + 4,
                    blendArrowColor(Math.abs(difference) / 180.0f), arrowScale, difference);
        }
        int lineY = y1 + 6 + arrowHeight;
        for (int i = 0; i < lines.size(); i++) {
            int color = lines.get(i).getString().equals(Component.translatable("hud.netherportalhelper.turn_around").getString())
                    ? 0xFFE89A78 : WHITE;
            drawCenteredText(context, font, lines.get(i), centerX, lineY, color, maxLineScale);
            lineY += Math.round((font.lineHeight + 2) * maxLineScale);
        }
    }

    private static Component coordinateLine(ResourceKey<Level> dimension, BlockPos position) {
        Component name = Component.translatable(dimension == Level.NETHER
                ? "hud.netherportalhelper.dim_nether" : "hud.netherportalhelper.dim_overworld");
        return Component.translatable("hud.netherportalhelper.coordinates", name,
                Component.literal(Integer.toString(position.getX())),
                Component.literal(Integer.toString(position.getY())),
                Component.literal(Integer.toString(position.getZ())));
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
