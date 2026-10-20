package de.alek.netherportalhelper.config;

import de.alek.netherportalhelper.hud.HUDOverlay;
import de.alek.netherportalhelper.hud.PreviewScene;
import de.alek.netherportalhelper.screen.PortalScreen;
import de.alek.netherportalhelper.screen.PortalMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.DoubleConsumer;

public final class ConfigScreen extends Screen implements PortalMenuScreen {
    private final Screen parent;
    private ModConfig draft;
    private Page page = Page.HUD;
    private String messageKey;
    private int scrollRow;
    private int titleY;
    private int tabHeight;
    private int previewNoticeY;
    private int previewX;
    private int previewWidth;
    private int controlTop;
    private int controlBottom;
    private int visibleRows;
    private int previewTop;
    private int previewBottom;
    private int previewCanvasY;
    private int previewCanvasHeight;
    private boolean previewVisible;
    private boolean sideBySide;
    private boolean stackedFooter;
    private int columnGap;
    private int controlLeft;
    private int controlRight;
    private int columnWidth;
    private int scrollbarX;
    private PreviewScene.Scenario previewScenario = PreviewScene.Scenario.TRAVELLING;

    public ConfigScreen(Screen parent) {
        this(parent, ConfigManager.get().copy());
    }

    private ConfigScreen(Screen parent, ModConfig draft) {
        super(Component.translatable("config.netherportalhelper.title"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        labels.clear();
        // These options belong to the planner and may change while this retained draft is open.
        draft.showTargetMarker = ConfigManager.get().showTargetMarker;
        draft.showFramePreview = ConfigManager.get().showFramePreview;
        SettingsScreenLayout.Layout layout = SettingsScreenLayout.calculate(width, height);
        titleY = layout.titleY();
        tabHeight = layout.tabHeight();
        int tabWidth = layout.tabWidth();
        addRenderableWidget(Button.builder(Component.translatable("config.netherportalhelper.hud"),
                        button -> openPage(Page.HUD))
                .bounds(layout.tabLeft(), layout.tabY(), tabWidth, tabHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("config.netherportalhelper.compass"),
                        button -> openPage(Page.COMPASS))
                .bounds(layout.tabLeft() + tabWidth + layout.tabGap(), layout.tabY(), tabWidth, tabHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("planner.netherportalhelper.title"),
                        button -> Minecraft.getInstance().gui.setScreen(new PortalScreen(this)))
                .bounds(layout.tabLeft() + (tabWidth + layout.tabGap()) * 2, layout.tabY(), tabWidth, tabHeight).build());

        previewNoticeY = layout.previewNoticeY();
        previewX = layout.previewX();
        previewWidth = layout.previewWidth();
        previewTop = layout.previewTop();
        previewBottom = layout.previewBottom();
        previewCanvasY = layout.previewCanvasY();
        previewCanvasHeight = layout.previewCanvasHeight();
        previewVisible = layout.previewVisible();
        if (previewVisible) {
            int scenarioWidth = Math.min(170, previewWidth / 2);
            addRenderableWidget(Button.builder(Component.translatable(previewScenario.translationKey()), button -> {
                previewScenario = previewScenario.next();
                HUDOverlay.resetPreviewState();
                button.setMessage(Component.translatable(previewScenario.translationKey()));
            }).bounds(previewX + previewWidth - scenarioWidth - 4, previewTop + 2, scenarioWidth, 18)
                    .tooltip(Tooltip.create(Component.translatable("config.netherportalhelper.preview_scenario"))).build());
        }
        sideBySide = layout.sideBySide();
        controlTop = layout.controlTop();
        controlBottom = layout.controlBottom();
        visibleRows = layout.visibleRows();
        stackedFooter = layout.stackedFooter();
        columnGap = layout.columnGap();
        controlLeft = layout.controlLeft();
        controlRight = layout.controlRight();
        columnWidth = layout.columnWidth();
        scrollbarX = layout.scrollbarX();
        scrollRow = Math.max(0, Math.min(Math.max(0, 4 - visibleRows), scrollRow));
        int rowHeight = layout.rowHeight();

        for (int row = scrollRow; row < Math.min(4, scrollRow + visibleRows); row++) {
            int rowY = controlTop + (row - scrollRow) * rowHeight + Math.max(0, (rowHeight - 32) / 2);
            if (page == Page.HUD) addHudRow(row, controlLeft, rowY, columnWidth, rowHeight);
            else addCompassRow(row, controlLeft, rowY, columnWidth, rowHeight);
        }

        int footerY = layout.footerY();
        if (stackedFooter) {
            int buttonLeft = Math.min(12, width / 4);
            int buttonWidth = Math.max(1, width - buttonLeft * 2);
            addRenderableWidget(Button.builder(Component.translatable("config.netherportalhelper.cancel"),
                            button -> closeToParent())
                    .bounds(buttonLeft, footerY, buttonWidth, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("config.netherportalhelper.reset"),
                            button -> resetDraft())
                    .bounds(buttonLeft, footerY + 22, buttonWidth, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("config.netherportalhelper.save"),
                            button -> saveDraft())
                    .bounds(buttonLeft, footerY + 44, buttonWidth, 20).build());
            return;
        }
        int buttonGap = 6;
        int footerWidth = Math.min(Math.max(1, width - 24), 760);
        int buttonWidth = Math.max(1, (footerWidth - buttonGap * 2) / 3);
        int buttonLeft = (width - footerWidth) / 2;
        boolean compactLabels = width < 280;
        addRenderableWidget(Button.builder(Component.translatable(compactLabels
                                ? "config.netherportalhelper.cancel_short" : "config.netherportalhelper.cancel"),
                        button -> closeToParent())
                .bounds(buttonLeft, footerY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(compactLabels
                                ? "config.netherportalhelper.reset_short" : "config.netherportalhelper.reset"),
                        button -> resetDraft())
                .bounds(buttonLeft + buttonWidth + buttonGap, footerY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(compactLabels
                                ? "config.netherportalhelper.save_short" : "config.netherportalhelper.save"),
                        button -> saveDraft())
                .bounds(buttonLeft + (buttonWidth + buttonGap) * 2, footerY, buttonWidth, 20).build());
    }

    private void addHudRow(int row, int left, int y, int cellWidth, int rowHeight) {
        switch (row) {
            case 0 -> {
                addToggle(left, y, cellWidth, rowHeight, "config.netherportalhelper.visible",
                        () -> draft.hudVisible, value -> draft.hudVisible = value);
                addAnchor(left + cellWidth + columnGap, y, cellWidth, rowHeight);
            }
            case 1 -> {
                addSlider(left, y, cellWidth, rowHeight, "config.netherportalhelper.offset_x",
                        draft.hudPanelX, 0, 100, 0, " px", value -> draft.hudPanelX = (int) Math.round(value));
                addSlider(left + cellWidth + columnGap, y, cellWidth, rowHeight, "config.netherportalhelper.offset_y",
                        draft.hudPanelY, 0, 100, 0, " px", value -> draft.hudPanelY = (int) Math.round(value));
            }
            case 2 -> {
                addSlider(left, y, cellWidth, rowHeight, "config.netherportalhelper.scale",
                        draft.hudScale, 0.75, 2.0, 2, "×", value -> draft.hudScale = (float) value);
                addSlider(left + cellWidth + columnGap, y, cellWidth, rowHeight,
                        "config.netherportalhelper.opacity", draft.backgroundOpacity, 0.0, 1.0, 2, "",
                        value -> draft.backgroundOpacity = (float) value);
            }
            case 3 -> {
                addLayout(left, y, cellWidth, rowHeight);
                addToggle(left + cellWidth + columnGap, y, cellWidth, rowHeight,
                        "config.netherportalhelper.current_position", () -> draft.showCurrentPosition,
                        value -> draft.showCurrentPosition = value);
            }
            default -> throw new IllegalArgumentException("Unknown HUD options row: " + row);
        }
    }

    private void addCompassRow(int row, int left, int y, int cellWidth, int rowHeight) {
        switch (row) {
            case 0 -> {
                addSlider(left, y, cellWidth, rowHeight, "config.netherportalhelper.compass_y",
                        draft.compassCenterY, 20, 400, 0, " px", value -> draft.compassCenterY = (int) Math.round(value));
                addSlider(left + cellWidth + columnGap, y, cellWidth, rowHeight,
                        "config.netherportalhelper.arrow_scale", draft.compassArrowScale, 1.0, 6.0, 1, "×",
                        value -> draft.compassArrowScale = (float) value);
            }
            case 1 -> {
                addSlider(left, y, cellWidth, rowHeight, "config.netherportalhelper.text_scale",
                        draft.compassTextScale, 0.6, 2.5, 1, "×", value -> draft.compassTextScale = (float) value);
                addSlider(left + cellWidth + columnGap, y, cellWidth, rowHeight,
                        "config.netherportalhelper.smoothing", draft.compassSmoothing, 0.0, 1.0, 2, "",
                        value -> draft.compassSmoothing = (float) value);
            }
            case 2 -> {
                addSlider(left, y, cellWidth, rowHeight, "config.netherportalhelper.backzone",
                        draft.compassBackzoneDegrees, 90.0, 179.0, 0, "°",
                        value -> draft.compassBackzoneDegrees = (float) value);
                addToggle(left + cellWidth + columnGap, y, cellWidth, rowHeight,
                        "config.netherportalhelper.turn_hint",
                        () -> draft.showTurnAroundHint, value -> draft.showTurnAroundHint = value);
            }
            case 3 -> addToggle(left, y, cellWidth, rowHeight, "config.netherportalhelper.y_hint",
                    () -> draft.showYLevelIndicator, value -> draft.showYLevelIndicator = value);
            default -> throw new IllegalArgumentException("Unknown compass options row: " + row);
        }
    }

    private void addAnchor(int x, int y, int cellWidth, int rowHeight) {
        addLabel(x, y, cellWidth, "config.netherportalhelper.anchor");
        addRenderableWidget(Button.builder(anchorLabel(), button -> {
                    ModConfig.HudAnchor[] anchors = ModConfig.HudAnchor.values();
                    draft.hudAnchor = anchors[(draft.hudAnchor.ordinal() + 1) % anchors.length];
                    button.setMessage(anchorLabel());
                })
                .bounds(x, y + sliderOffset(rowHeight), cellWidth, widgetHeight(rowHeight)).build());
    }

    private void addLayout(int x, int y, int cellWidth, int rowHeight) {
        addLabel(x, y, cellWidth, "config.netherportalhelper.layout");
        addRenderableWidget(Button.builder(layoutLabel(), button -> {
                    draft.hudLayout = draft.hudLayout == ModConfig.HudLayout.COMPACT
                            ? ModConfig.HudLayout.DETAILED : ModConfig.HudLayout.COMPACT;
                    button.setMessage(layoutLabel());
                })
                .bounds(x, y + sliderOffset(rowHeight), cellWidth, widgetHeight(rowHeight)).build());
    }

    private void addToggle(int x, int y, int cellWidth, int rowHeight, String labelKey,
                           BooleanSupplier getter, BooleanConsumer setter) {
        addLabel(x, y, cellWidth, labelKey);
        addRenderableWidget(Button.builder(toggleLabel(getter.get()), button -> {
                    boolean next = !getter.get();
                    setter.accept(next);
                    button.setMessage(toggleLabel(next));
                })
                .bounds(x, y + sliderOffset(rowHeight), cellWidth, widgetHeight(rowHeight)).build());
    }

    private void addSlider(int x, int y, int cellWidth, int rowHeight, String labelKey,
                           double value, double min, double max, int decimals, String suffix,
                           DoubleConsumer setter) {
        addLabel(x, y, cellWidth, labelKey);
        int sliderY = y + sliderOffset(rowHeight);
        int sliderHeight = widgetHeight(rowHeight);
        addRenderableWidget(new ValueSlider(x, sliderY, cellWidth, sliderHeight, value, min, max,
                decimals, suffix, setter));
    }

    private void addLabel(int x, int y, int cellWidth, String labelKey) {
        labels.add(new Label(x + 2, y, cellWidth - 4, Component.translatable(labelKey)));
    }

    private final java.util.List<Label> labels = new java.util.ArrayList<>();

    private int sliderOffset(int rowHeight) {
        return Math.min(12, Math.max(10, rowHeight - 14));
    }

    private int widgetHeight(int rowHeight) {
        return Math.max(14, Math.min(20, rowHeight - sliderOffset(rowHeight)));
    }

    private Component toggleLabel(boolean value) {
        return Component.translatable(value ? "config.netherportalhelper.on" : "config.netherportalhelper.off");
    }

    private Component anchorLabel() {
        return Component.translatable("config.netherportalhelper.anchor_value",
                Component.translatable("config.netherportalhelper.anchor." + draft.hudAnchor.name().toLowerCase(Locale.ROOT)));
    }

    private Component layoutLabel() {
        return Component.translatable("config.netherportalhelper.layout_value",
                Component.translatable(draft.hudLayout == ModConfig.HudLayout.COMPACT
                        ? "config.netherportalhelper.compact" : "config.netherportalhelper.detailed"));
    }

    private void openPage(Page newPage) {
        Minecraft.getInstance().gui.setScreen(new ConfigScreen(parent, draft.copy(), newPage, 0, previewScenario));
    }

    private ConfigScreen(Screen parent, ModConfig draft, Page page) {
        this(parent, draft, page, 0);
    }

    private ConfigScreen(Screen parent, ModConfig draft, Page page, int scrollRow) {
        this(parent, draft, page, scrollRow, PreviewScene.Scenario.TRAVELLING);
    }

    private ConfigScreen(Screen parent, ModConfig draft, Page page, int scrollRow, PreviewScene.Scenario scenario) {
        super(Component.translatable("config.netherportalhelper.title"));
        this.parent = parent;
        this.draft = draft;
        this.page = page;
        this.scrollRow = scrollRow;
        this.previewScenario = scenario;
    }

    private void resetDraft() {
        draft = new ModConfig();
        Minecraft.getInstance().gui.setScreen(new ConfigScreen(parent, draft, page, 0, previewScenario));
    }

    private void saveDraft() {
        if (ConfigManager.save(draft, de.alek.netherportalhelper.Netherportalhelper.getLogger())) {
            HUDOverlay.showHints();
            HUDOverlay.resetCompassState();
            closeToParent();
        } else {
            messageKey = "config.netherportalhelper.save_error";
        }
    }

    private void closeToParent() {
        HUDOverlay.resetCompassState();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int scrollAreaRight = scrollbarX >= 0 ? scrollbarX + 3 : controlRight;
        if (visibleRows > 0 && visibleRows < 4 && x >= controlLeft && x <= scrollAreaRight
                && y >= controlTop && y <= controlBottom && scrollY != 0.0) {
            int nextRow = Math.max(0, Math.min(4 - visibleRows, scrollRow + (scrollY < 0.0 ? 1 : -1)));
            if (nextRow != scrollRow) {
                Minecraft.getInstance().gui.setScreen(new ConfigScreen(parent, draft.copy(), page, nextRow, previewScenario));
            }
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (previewVisible) {
            graphics.fill(previewX, previewTop, previewX + previewWidth, previewBottom, 0xC9151515);
            graphics.fill(previewX, previewTop, previewX + previewWidth, previewTop + 1, 0xFF555555);
            graphics.fill(previewX, previewTop, previewX + 1, previewBottom, 0xFF555555);
            graphics.fill(previewX + previewWidth - 1, previewTop,
                    previewX + previewWidth, previewBottom, 0xFF555555);
            graphics.fill(previewX, previewBottom - 1,
                    previewX + previewWidth, previewBottom, 0xFF555555);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        drawCenteredFitted(graphics, font, getTitle(), width / 2, titleY,
                Math.max(1, width - 16), 0xFFFFFFFF);
        labels.forEach(label -> drawFittedLabel(graphics, font, label));
        if (previewVisible) {
            graphics.text(font, Component.translatable("config.netherportalhelper.preview"),
                    previewX + 4, previewTop + 2, 0xFFB5B5B5, false);
            HUDOverlay.renderPreview(graphics, font, draft, previewX + 1, previewCanvasY,
                    Math.max(0, previewWidth - 2), previewCanvasHeight, previewScenario);
        } else if (messageKey == null) {
            drawCenteredFitted(graphics, font,
                    Component.translatable("config.netherportalhelper.preview_resize"),
                    width / 2, previewNoticeY, Math.max(1, width - 16), 0xFFB5B5B5);
        }
        if (messageKey != null) {
            Component message = Component.translatable(messageKey);
            int availableWidth = Math.max(1, width - 24);
            float scale = Math.min(1.0f, availableWidth / (float) Math.max(1, font.width(message)));
            int messageY = previewVisible ? previewBottom + 2 : previewNoticeY;
            graphics.pose().pushMatrix();
            int messageCenterX = sideBySide ? previewX + previewWidth / 2 : width / 2;
            graphics.pose().translate((float) messageCenterX, (float) messageY);
            graphics.pose().scale(scale, scale);
            graphics.text(font, message, -font.width(message) / 2, 0, 0xFFFF7777, false);
            graphics.pose().popMatrix();
        }
        if (visibleRows > 0 && visibleRows < 4) {
            int trackHeight = controlBottom - controlTop;
            int thumbHeight = Math.max(12, trackHeight * visibleRows / 4);
            int thumbTop = controlTop + (trackHeight - thumbHeight) * scrollRow / (4 - visibleRows);
            graphics.fill(scrollbarX, controlTop, scrollbarX + 2, controlBottom, 0xFF555555);
            graphics.fill(scrollbarX - 1, thumbTop, scrollbarX + 3, thumbTop + thumbHeight, 0xFFB5B5B5);
        }
    }

    private void drawFittedLabel(GuiGraphicsExtractor graphics, Font font, Label label) {
        int textWidth = font.width(label.text);
        float scale = Math.min(1.0f, label.maxWidth / (float) Math.max(1, textWidth));
        graphics.pose().pushMatrix();
        graphics.pose().translate(label.x, label.y + 2);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label.text, 0, 0, 0xFFB5B5B5, false);
        graphics.pose().popMatrix();
    }

    private void drawCenteredFitted(GuiGraphicsExtractor graphics, Font font, Component text,
                                    int centerX, int y, int maxWidth, int color) {
        float scale = Math.min(1.0f, maxWidth / (float) Math.max(1, font.width(text)));
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) centerX, (float) y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popMatrix();
    }

    private enum Page { HUD, COMPASS }

    private record Label(int x, int y, int maxWidth, Component text) { }

    @FunctionalInterface
    private interface BooleanSupplier { boolean get(); }

    @FunctionalInterface
    private interface BooleanConsumer { void accept(boolean value); }

    private static final class ValueSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final int decimals;
        private final String suffix;
        private final DoubleConsumer setter;

        private ValueSlider(int x, int y, int width, int height, double initial, double min, double max,
                            int decimals, String suffix, DoubleConsumer setter) {
            super(x, y, width, height, Component.empty(), Math.max(0.0, Math.min(1.0,
                    (initial - min) / (max - min))));
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.suffix = suffix;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double current = min + (max - min) * value;
            setMessage(Component.literal(String.format(Locale.ROOT, "%1$." + decimals + "f%2$s", current, suffix)));
        }

        @Override
        protected void applyValue() {
            setter.accept(min + (max - min) * value);
        }
    }
}
