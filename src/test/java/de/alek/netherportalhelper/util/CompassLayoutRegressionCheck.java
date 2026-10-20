package de.alek.netherportalhelper.util;

import de.alek.netherportalhelper.hud.CompassLayout;

/** Geometry checks for the clipped arrow reported in the in-game screenshots. */
final class CompassLayoutRegressionCheck {
    static void run() {
        for (int screenWidth : new int[]{180, 320, 640, 1920}) {
            for (int screenHeight : new int[]{100, 180, 360, 1080}) {
                for (float arrowScale : new float[]{1, 3, 6}) {
                    var layout = CompassLayout.calculate(screenWidth, screenHeight, 20,
                            9, 9, arrowScale, 9, 250, 2.5f);
                    assert layout.x() >= 0 && layout.x() + layout.width() <= screenWidth;
                    assert layout.y() >= 0 && layout.y() + layout.height() <= screenHeight;
                    for (int degrees = 0; degrees < 360; degrees++) {
                        double angle = Math.toRadians(degrees);
                        // Include shadow and font bearing around the glyph's nominal rectangle.
                        double extent = (Math.abs(Math.cos(angle)) * 6.5
                                + Math.abs(Math.sin(angle)) * 6.5) * layout.arrowScale();
                        assert layout.arrowCenterY() - extent >= layout.y() + 4 : layout;
                        assert layout.arrowCenterY() + extent <= layout.textY() - 2 : layout;
                        assert layout.centerX() - extent >= layout.x() + 4 : layout;
                        assert layout.centerX() + extent <= layout.x() + layout.width() - 4 : layout;
                    }
                    assert layout.textY() + 2 * layout.lineStep() <= layout.y() + layout.height() - 4 : layout;
                }
            }
        }
    }
}
