package de.alek.netherportalhelper.hud;

/** Reserves the full rotated glyph, its shadow, and two stable text rows. */
public final class CompassLayout {
    private CompassLayout() { }

    public static Placement calculate(int screenWidth, int screenHeight, int preferredCenterY,
                                      int glyphWidth, int glyphHeight, float arrowScale,
                                      int lineHeight, int reservedTextWidth, float textScale) {
        float arrowBox = (float) Math.ceil(Math.hypot(glyphWidth + 4, glyphHeight + 4) * arrowScale);
        float lineStep = (lineHeight + 2) * textScale;
        float naturalWidth = Math.max(arrowBox, reservedTextWidth * textScale) + 12;
        float naturalHeight = arrowBox + 4 + 2 * lineStep + 12;
        // Scale the entire content together, retaining room around every rotating corner.
        float fit = Math.min(1, Math.min(Math.max(1, screenWidth - 8) / naturalWidth,
                Math.max(1, screenHeight - 8) / naturalHeight));
        int box = (int) Math.ceil(arrowBox * fit);
        int step = (int) Math.ceil(lineStep * fit);
        int width = Math.min(screenWidth, Math.max(box, (int) Math.ceil(reservedTextWidth * textScale * fit)) + 12);
        int height = Math.min(screenHeight, box + 4 + 2 * step + 12);
        int x = Math.max(0, (screenWidth - width) / 2);
        int y = Math.max(0, Math.min(screenHeight - height, preferredCenterY - height / 2));
        return new Placement(x, y, width, height, x + width / 2,
                y + 6 + box / 2, y + 6 + box + 4, step, arrowScale * fit, textScale * fit);
    }

    public record Placement(int x, int y, int width, int height, int centerX,
                            int arrowCenterY, int textY, int lineStep, float arrowScale, float textScale) { }
}
