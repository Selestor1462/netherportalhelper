package de.alek.netherportalhelper.config;

public class ModConfig {

    public boolean hudVisible = true;
    public int hudPanelX = 6;
    public int hudPanelY = 6;
    public HudAnchor hudAnchor = HudAnchor.TOP_LEFT;
    public float hudScale = 1.0f;
    public float backgroundOpacity = 0.82f;
    public HudLayout hudLayout = HudLayout.COMPACT;
    public boolean showCurrentPosition = false;

    public int compassCenterY = 56;
    public float compassArrowScale = 3.0f;
    public float compassTextScale = 1.0f;
    public float compassSmoothing = 0.28f;
    public float compassBackzoneDegrees = 165.0f;
    public boolean showTurnAroundHint = true;
    public boolean showYLevelIndicator = true;
    public boolean showTargetMarker = false;
    public boolean showFramePreview = false;

    public void normalize() {
        hudPanelX = Math.max(0, hudPanelX);
        hudPanelY = Math.max(0, hudPanelY);
        if (hudAnchor == null) hudAnchor = HudAnchor.TOP_LEFT;
        if (hudLayout == null) hudLayout = HudLayout.COMPACT;
        hudScale = clampFloat(hudScale, 1.0f, 0.75f, 2.0f);
        backgroundOpacity = clampFloat(backgroundOpacity, 0.82f, 0.0f, 1.0f);
        compassCenterY = clampInt(compassCenterY, 20, 400);
        compassArrowScale = clampFloat(compassArrowScale, 3.0f, 1.0f, 6.0f);
        compassTextScale = clampFloat(compassTextScale, 1.0f, 0.6f, 2.5f);
        compassSmoothing = clampFloat(compassSmoothing, 0.28f, 0.0f, 1.0f);
        compassBackzoneDegrees = clampFloat(compassBackzoneDegrees, 165.0f, 90.0f, 179.0f);
    }

    public ModConfig copy() {
        ModConfig copy = new ModConfig();
        copy.hudVisible = hudVisible;
        copy.hudPanelX = hudPanelX;
        copy.hudPanelY = hudPanelY;
        copy.hudAnchor = hudAnchor;
        copy.hudScale = hudScale;
        copy.backgroundOpacity = backgroundOpacity;
        copy.hudLayout = hudLayout;
        copy.showCurrentPosition = showCurrentPosition;
        copy.compassCenterY = compassCenterY;
        copy.compassArrowScale = compassArrowScale;
        copy.compassTextScale = compassTextScale;
        copy.compassSmoothing = compassSmoothing;
        copy.compassBackzoneDegrees = compassBackzoneDegrees;
        copy.showTurnAroundHint = showTurnAroundHint;
        copy.showYLevelIndicator = showYLevelIndicator;
        copy.showTargetMarker = showTargetMarker;
        copy.showFramePreview = showFramePreview;
        return copy;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public enum HudAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public enum HudLayout {
        COMPACT,
        DETAILED
    }
}
