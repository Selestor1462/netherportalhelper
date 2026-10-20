package de.alek.netherportalhelper.config;

/** Responsive geometry for the options screen, independent of Minecraft rendering. */
public final class SettingsScreenLayout {
    private static final int ROW_HEIGHT = 24;
    private static final int MAX_ROW_SPACING = 56;
    private static final int MAX_STACKED_WIDTH = 960;
    private static final int MAX_SPLIT_WIDTH = 1400;
    private static final int MAX_PREVIEW_WIDTH = 780;

    private SettingsScreenLayout() {
    }

    public static Layout calculate(int screenWidth, int screenHeight) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);
        boolean compact = height < 180;
        boolean stackedFooter = width < 280 && height >= 160;
        boolean sideBySide = width >= 1200 && height >= 360;
        int titleY = compact ? 3 : 8;
        int tabY = compact ? 15 : 25;
        int tabHeight = compact ? 18 : 20;
        int tabGap = Math.min(6, Math.max(0, (width - 2) / 4));
        int tabWidth = Math.max(1, Math.min(120, (width - 2 - tabGap * 2) / 3));
        int tabLeft = Math.max(0, (width - tabWidth * 3 - tabGap * 2) / 2);
        int previewTop = tabY + tabHeight + (compact ? 4 : 2);
        int previewNoticeY = tabY + tabHeight + 2;
        int pageWidth = Math.min(Math.max(1, width - 24), sideBySide ? MAX_SPLIT_WIDTH : MAX_STACKED_WIDTH);
        int pageLeft = (width - pageWidth) / 2;
        int splitGap = sideBySide ? 16 : 0;
        int controlPanelWidth = sideBySide
                ? Math.min(600, Math.max(480, (pageWidth - splitGap) * 45 / 100))
                : pageWidth;
        int previewWidth = sideBySide
                ? Math.min(MAX_PREVIEW_WIDTH, Math.max(1, pageWidth - controlPanelWidth - splitGap))
                : Math.min(Math.max(1, width - 18), MAX_PREVIEW_WIDTH);
        int previewX = sideBySide
                ? pageLeft + controlPanelWidth + splitGap
                : (width - previewWidth) / 2;
        int controlLeft = pageLeft;
        boolean previewVisible = height >= 128;
        int previewCanvasY = previewVisible ? previewTop + 24 : 0;
        int footerY = Math.max(0, height - (stackedFooter ? 68 : 25));
        int controlBottom = Math.max(0, footerY - 5);
        int controlGap = previewVisible ? 14 : 4;
        int targetCanvasHeight = Math.max(0, Math.min(sideBySide ? 220 : compact ? 48 : 180, height / 2 - 14));
        int maxCanvasHeight = !previewVisible ? 0 : sideBySide
                ? controlBottom - previewCanvasY - controlGap
                : controlBottom - previewCanvasY - controlGap - ROW_HEIGHT;
        int previewCanvasHeight = previewVisible
                ? Math.max(0, Math.min(targetCanvasHeight, maxCanvasHeight)) : 0;
        if (previewVisible && previewCanvasHeight < 18) {
            previewVisible = false;
            previewCanvasY = 0;
            previewCanvasHeight = 0;
            controlGap = 4;
        }
        int previewBottom = previewVisible ? previewCanvasY + previewCanvasHeight : previewTop;
        int controlTop = previewVisible
                ? (sideBySide ? previewCanvasY : previewBottom + controlGap)
                : tabY + tabHeight + 13;
        int controlsHeight = Math.max(0, controlBottom - controlTop + 1);
        int visibleRows = Math.min(4, controlsHeight / ROW_HEIGHT);
        int rowHeight = visibleRows == 0 ? 0 : Math.min(MAX_ROW_SPACING, controlsHeight / visibleRows);

        int columnGap = width < 320 ? 6 : 10;
        int scrollbarReserve = visibleRows > 0 && visibleRows < 4 ? 10 : 0;
        int contentRight = controlLeft + controlPanelWidth;
        int scrollbarX = scrollbarReserve > 0 ? contentRight - 8 : -1;
        int controlRight = Math.max(controlLeft + 2, contentRight - scrollbarReserve);
        int cellRight = scrollbarReserve > 0 ? scrollbarX - 5 : controlRight;
        int columnWidth = Math.max(1, (cellRight - controlLeft - columnGap) / 2);

        return new Layout(width, height, titleY, tabY, tabHeight, tabGap, tabWidth, tabLeft, previewNoticeY,
                previewX, previewWidth, previewTop, previewCanvasY, previewCanvasHeight, previewBottom, previewVisible,
                sideBySide, controlTop, controlBottom, visibleRows, rowHeight, controlLeft, controlRight,
                columnGap, columnWidth, scrollbarReserve, scrollbarX, footerY, stackedFooter);
    }

    public record Layout(int width, int height, int titleY, int tabY, int tabHeight, int tabGap, int tabWidth,
                         int tabLeft, int previewNoticeY, int previewX, int previewWidth,
                         int previewTop, int previewCanvasY,
                         int previewCanvasHeight, int previewBottom,
                         boolean previewVisible, boolean sideBySide, int controlTop, int controlBottom, int visibleRows,
                         int rowHeight, int controlLeft, int controlRight, int columnGap, int columnWidth,
                         int scrollbarReserve, int scrollbarX, int footerY, boolean stackedFooter) {
    }
}
