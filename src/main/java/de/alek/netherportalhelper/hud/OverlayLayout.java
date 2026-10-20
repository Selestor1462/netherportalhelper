package de.alek.netherportalhelper.hud;

import de.alek.netherportalhelper.config.ModConfig.HudAnchor;

public final class OverlayLayout {
    private OverlayLayout() {
    }

    public static Placement place(int screenWidth, int screenHeight, int panelWidth, int panelHeight,
                                  int offsetX, int offsetY, float requestedScale, HudAnchor anchor) {
        int safeWidth = Math.max(0, screenWidth - 8);
        int safeHeight = Math.max(0, screenHeight - 8);
        float scale = Math.min(requestedScale,
                Math.min(panelWidth == 0 ? requestedScale : safeWidth / (float) panelWidth,
                        panelHeight == 0 ? requestedScale : safeHeight / (float) panelHeight));
        scale = Math.max(0.01f, scale);

        int drawnWidth = Math.round(panelWidth * scale);
        int drawnHeight = Math.round(panelHeight * scale);
        int maxX = Math.max(0, screenWidth - drawnWidth);
        int maxY = Math.max(0, screenHeight - drawnHeight);
        int x = switch (anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> Math.min(maxX, Math.max(0, screenWidth - 4 - drawnWidth - offsetX));
            default -> Math.min(maxX, Math.max(4, offsetX));
        };
        int y = switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.min(maxY, Math.max(0, screenHeight - 4 - drawnHeight - offsetY));
            default -> Math.min(maxY, Math.max(4, offsetY));
        };
        return new Placement(x, y, scale, drawnWidth, drawnHeight);
    }

    public record Placement(int x, int y, float scale, int width, int height) {
    }
}
