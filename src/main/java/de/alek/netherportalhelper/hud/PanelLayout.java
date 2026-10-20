package de.alek.netherportalhelper.hud;

/** Measures only content that the panel will actually draw. */
public final class PanelLayout {
    private PanelLayout() { }

    public static Size measure(int titleWidth, int coordinateWidth, int hintWidth,
                               boolean showPosition, boolean showHint) {
        int width = Math.max(titleWidth + 16, coordinateWidth + 32);
        if (showHint) width = Math.max(width, hintWidth + 16);
        return new Size(width, 47 + (showPosition ? 26 : 0) + (showHint ? 13 : 0));
    }

    public record Size(int width, int height) { }
}
