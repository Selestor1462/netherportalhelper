package de.alek.netherportalhelper.hud;

/** Measures only content that the panel will actually draw. */
public final class PanelLayout {
    private PanelLayout() { }

    public static Size measure(int titleWidth, int statusWidth, int coordinateWidth,
                               int hintWidth, int referenceWidth, boolean showHints) {
        int contentWidth = Math.max(titleWidth + statusWidth + 24, coordinateWidth + 26);
        if (showHints) contentWidth = Math.max(contentWidth, Math.max(hintWidth, referenceWidth));
        return new Size(contentWidth + 14, showHints ? 145 : 67);
    }

    public record Size(int width, int height) { }
}
