package de.alek.netherportalhelper.hud;

/** Fits the shared HUD and compass renderers into the settings preview viewport. */
public final class PreviewLayout {
    private PreviewLayout() {
    }

    public static Placement fit(int x, int y, int viewportWidth, int viewportHeight) {
        return fit(x, y, viewportWidth, viewportHeight, 640, 360);
    }

    public static Placement fit(int x, int y, int viewportWidth, int viewportHeight, int sceneWidth, int sceneHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || sceneWidth <= 0 || sceneHeight <= 0) {
            return new Placement(x, y, 0.0f, 0.0f, 0.0f);
        }
        float scale = Math.min(viewportWidth / (float) sceneWidth, viewportHeight / (float) sceneHeight);
        float width = sceneWidth * scale;
        float height = sceneHeight * scale;
        return new Placement(x + (viewportWidth - width) / 2.0f,
                y + (viewportHeight - height) / 2.0f, scale, width, height);
    }

    public static boolean isReadable(float sceneScale, float guiScale, float textScale, int lineHeight) {
        return sceneScale > 0 && guiScale > 0 && textScale > 0
                && sceneScale * guiScale * textScale * lineHeight >= 6.0f;
    }

    public record Placement(float x, float y, float scale, float width, float height) {
    }
}
