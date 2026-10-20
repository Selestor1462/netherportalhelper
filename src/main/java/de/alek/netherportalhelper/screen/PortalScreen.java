package de.alek.netherportalhelper.screen;

import de.alek.netherportalhelper.Netherportalhelper;
import de.alek.netherportalhelper.config.ConfigManager;
import de.alek.netherportalhelper.config.ConfigScreen;
import de.alek.netherportalhelper.config.ModConfig;
import de.alek.netherportalhelper.hud.HUDOverlay;
import de.alek.netherportalhelper.util.PortalCoordinates;
import de.alek.netherportalhelper.util.PortalManager;
import de.alek.netherportalhelper.util.PortalTarget;
import de.alek.netherportalhelper.util.PortalTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** World-scoped saved portal pairs and navigation controls. */
public final class PortalScreen extends Screen implements PortalMenuScreen {
    private static final int BODY_TOP = 42;
    private final Screen parent;
    private final List<BodyButton> bodyButtons = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private UUID selected;
    private int page;
    private int pageSize;
    private int left;
    private int contentWidth;
    private int bodyBottom;
    private int footerY;
    private int scrollOffset;
    private int maxScroll;
    private Button scrollUp;
    private Button scrollDown;
    private String messageKey;
    private boolean messageError;
    private List<FormattedCharSequence> messageLines = List.of();
    private List<PortalTarget> targets = List.of();

    public PortalScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        bodyButtons.clear();
        labels.clear();
        targets = PortalManager.targets();
        if (selected == null || targets.stream().noneMatch(target -> target.id().equals(selected))) {
            PortalTarget active = PortalManager.activeTarget();
            selected = active != null ? active.id() : targets.isEmpty() ? null : targets.getFirst().id();
        }
        contentWidth = Math.min(840, Math.max(1, width - 48));
        left = (width - contentWidth) / 2;
        messageLines = messageKey == null ? List.of() : font.split(text(messageKey), contentWidth);
        footerY = height - 40 - Math.max(0, messageLines.size() * (font.lineHeight + 2) - 10);
        bodyBottom = Math.max(BODY_TOP + 20, footerY - 12);
        boolean wide = contentWidth >= 600;
        int listWidth = wide ? (contentWidth - 20) * 2 / 5 : contentWidth;
        int detailLeft = wide ? left + listWidth + 20 : left;
        int detailWidth = wide ? contentWidth - listWidth - 20 : contentWidth;
        int previousPageSize = pageSize;
        // A short stacked list keeps the selected pair directly beside its details in the flow.
        pageSize = wide ? Math.max(1, (bodyBottom - BODY_TOP - 68) / 34)
                : Math.max(1, Math.min(4, (height - 280) / 34));
        if (previousPageSize > 0 && previousPageSize != pageSize) {
            int selectedIndex = -1;
            for (int index = 0; index < targets.size(); index++) {
                if (targets.get(index).id().equals(selected)) selectedIndex = index;
            }
            page = selectedIndex < 0 ? page * previousPageSize / pageSize : selectedIndex / pageSize;
        }
        int pages = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        boolean inWorld = minecraft.player != null && minecraft.level != null;
        label(text("saved_pairs"), left, BODY_TOP, listWidth, 0xFFFFFFFF);
        int half = (listWidth - 4) / 2;
        Button capture = bodyButton("capture", left, BODY_TOP + 14, half, () -> perform(() -> {
            PortalManager.CaptureResult result = PortalManager.capture(minecraft, text("default_name", targets.size() + 1).getString());
            reveal(result.target().id());
            messageKey = result.duplicate()
                    ? result.positionFallback() ? "capture_duplicate_position" : "capture_duplicate"
                    : result.positionFallback() ? "capture_position" : "capture_portal";
        }));
        capture.active = inWorld && PortalTracker.isPortalDimension(minecraft.level.dimension());
        capture.setTooltip(Tooltip.create(text("capture_hint")));
        bodyButton("add", left + half + 4, BODY_TOP + 14, listWidth - half - 4,
                () -> minecraft.gui.setScreen(new PortalEditorScreen(this, null))).active = inWorld;
        PortalTarget active = PortalManager.activeTarget();
        int shown = Math.min(pageSize, targets.size() - page * pageSize);
        int listTop = BODY_TOP + 38;
        for (int row = 0; row < shown; row++) {
            PortalTarget target = targets.get(page * pageSize + row);
            Component name = text(target.id().equals(selected) ? "selected_entry" : "entry", target.name());
            if (active != null && target.id().equals(active.id())) name = text("active_entry", name);
            Component summary = text("pair_hint", coordinates(target.sourceDimension(), target.source()),
                    coordinates(target.destinationDimension(), target.destination()));
            Button entry = addBodyButton(new PortalEntry(left, listTop + row * 34, listWidth,
                    name, summary, target.id().equals(selected), () -> {
                selected = target.id();
                messageKey = null;
                rebuildWidgets();
            }));
            entry.setTooltip(Tooltip.create(Component.literal(target.name()).append("\n").append(summary)));
        }
        int pageY = listTop + Math.max(1, shown) * 34;
        if (targets.isEmpty()) label(text("empty"), left, listTop + 10, listWidth, 0xFFAAAAAA);
        int listBottom = pageY;
        if (pages > 1) {
            bodyButton("previous", left, pageY, 26, () -> {
                page--;
                rebuildWidgets();
            }).active = page > 0;
            bodyButton("next", left + listWidth - 26, pageY, 26, () -> {
                page++;
                rebuildWidgets();
            }).active = page < pages - 1;
            label(text("page", page + 1, pages), left + 30, pageY + 6, listWidth - 60, 0xFFAAAAAA);
            listBottom += 24;
        }
        int detailTop = wide ? BODY_TOP : listBottom + 12;
        int detailBottom = addDetails(detailLeft, detailTop, detailWidth, inWorld, active);
        int contentBottom = Math.max(listBottom, detailBottom);
        maxScroll = Math.max(0, contentBottom - bodyBottom);
        scrollUp = addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> scroll(-34))
                .bounds(left + contentWidth + 4, BODY_TOP, 16, 20).build());
        scrollDown = addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> scroll(34))
                .bounds(left + contentWidth + 4, bodyBottom - 20, 16, 20).build());
        scrollUp.setTooltip(Tooltip.create(text("scroll_hint")));
        scrollDown.setTooltip(Tooltip.create(text("scroll_hint")));
        updateScroll();
        boolean canUndo = PortalManager.canUndoDelete();
        int footerParts = canUndo ? 3 : 2;
        int footerWidth = (contentWidth - (footerParts - 1) * 4) / footerParts;
        button("settings", left, footerY, footerWidth,
                () -> minecraft.gui.setScreen(new ConfigScreen(this)));
        if (canUndo) button("undo_delete", left + footerWidth + 4, footerY, footerWidth, () -> perform(() -> {
            List<UUID> previousIds = PortalManager.targets().stream().map(PortalTarget::id).toList();
            PortalTarget restored = PortalManager.undoDelete();
            reveal(restored.id());
            messageKey = previousIds.contains(restored.id()) ? "restore_existing" : "restored";
        }));
        int doneX = left + (footerParts - 1) * (footerWidth + 4);
        button("done", doneX, footerY, left + contentWidth - doneX, this::onClose);
    }

    private int addDetails(int x, int y, int detailWidth, boolean inWorld, PortalTarget active) {
        PortalTarget target = selectedTarget();
        boolean hasSelection = target != null;
        if (target != null) {
            y = wrappedLabel(text("selected_name", target.name()), x, y, detailWidth, 0xFFFFFFFF);
            y = wrappedLabel(text(target.linkVerified() ? "status_verified"
                    : target.hasObservedDestination() ? "status_recorded" : "status_planned"), x, y, detailWidth,
                    target.linkVerified() ? 0xFF88EEAA : target.hasObservedDestination() ? 0xFF88DDEE : 0xFFAAAAAA);
            y = wrappedLabel(text("source", coordinates(target.sourceDimension(), target.source())), x, y + 4, detailWidth, 0xFFCCCCCC);
            y = wrappedLabel(text(target.hasObservedDestination() ? "recorded_counterpart" : "planned_counterpart",
                    coordinates(target.destinationDimension(), target.destination())), x, y, detailWidth,
                    target.hasObservedDestination() ? 0xFF88DDEE : 0xFFCCCCCC);
            y = wrappedLabel(text("reference", target.calculatedDestination().getY(), target.axis().name()), x, y, detailWidth, 0xFFAAAAAA);
        } else {
            y = wrappedLabel(text("selected_target"), x, y, detailWidth, 0xFFFFFFFF);
            y = wrappedLabel(text("no_selection"), x, y + 4, detailWidth, 0xFFAAAAAA);
        }
        y += 8;
        boolean selectedIsActive = active != null && active.id().equals(selected);
        int returnWidth = Math.max(1, (detailWidth - 4) / 3);
        int forwardWidth = detailWidth - returnWidth - 4;
        String forwardKey = selectedIsActive && !PortalTracker.isReturning() ? "continue_navigation" : "navigate_counterpart";
        Button forward = bodyButton(forwardKey, x, y, forwardWidth, () -> navigate(false));
        forward.active = hasSelection && inWorld;
        forward.setTooltip(Tooltip.create(text(forwardKey)));
        Button returning = bodyButton("navigate_source", x + forwardWidth + 4, y, returnWidth, () -> navigate(true));
        returning.active = hasSelection && inWorld;
        returning.setTooltip(Tooltip.create(text("navigate_source")));
        y += 24;
        int half = (detailWidth - 4) / 2;
        boolean recorded = hasSelection && target.hasObservedDestination();
        Button record = bodyButton(recorded ? "update_counterpart" : "record_counterpart", x, y,
                recorded ? half : detailWidth, () -> perform(() -> {
            try {
                PortalManager.recordDestination(selected, minecraft);
                messageKey = "counterpart_recorded";
            } catch (IllegalArgumentException | IllegalStateException exception) {
                messageKey = "record_counterpart_error";
                messageError = true;
            }
        }));
        record.active = hasSelection && inWorld && minecraft.level.dimension().equals(target.destinationDimension());
        record.setTooltip(Tooltip.create(text("record_counterpart_hint")));
        if (recorded) {
            String testedKey = target.linkVerified() ? "mark_untested" : "mark_tested";
            Button tested = bodyButton(testedKey, x + half + 4, y, detailWidth - half - 4, () -> perform(() -> {
                boolean verified = !selectedTarget().linkVerified();
                PortalManager.markVerified(selected, verified);
                messageKey = verified ? "tested_saved" : "tested_cleared";
            }));
            tested.setTooltip(Tooltip.create(text(testedKey).copy().append("\n").append(text("tested_hint"))));
        }
        y += 24;
        bodyButton("copy_source", x, y, half, () -> {
            minecraft.keyboardHandler.setClipboard(PortalCoordinates.format(selectedTarget()));
            showMessage("copied_source");
        }).active = hasSelection;
        bodyButton("copy_destination", x + half + 4, y, detailWidth - half - 4, () -> {
            PortalTarget copied = selectedTarget();
            BlockPos position = copied.destination();
            minecraft.keyboardHandler.setClipboard((copied.destinationDimension().equals(Level.NETHER) ? "nether" : "overworld")
                    + " " + position.getX() + " " + position.getY() + " " + position.getZ());
            showMessage("copied_destination");
        }).active = hasSelection;
        y += 24;
        bodyButton("edit", x, y, half,
                () -> minecraft.gui.setScreen(new PortalEditorScreen(this, selectedTarget()))).active = hasSelection;
        bodyButton("delete", x + half + 4, y, detailWidth - half - 4, () -> perform(() -> {
            int previousIndex = targets.indexOf(selectedTarget());
            PortalManager.delete(selected);
            List<PortalTarget> updated = PortalManager.targets();
            selected = updated.isEmpty() ? null : updated.get(Math.min(previousIndex, updated.size() - 1)).id();
            messageKey = "deleted";
        })).active = hasSelection;
        y += 36;
        y = wrappedLabel(active == null ? text("no_active_guides") : text("active_guides", active.name()),
                x, y, detailWidth, 0xFFFFFFFF);
        if (active != null) y = wrappedLabel(text(PortalTracker.isReturning() ? "active_return" : "active_forward", active.name()),
                x, y, detailWidth, 0xFF88EEAA);
        y += 4;
        addBodyButton(Button.builder(toggle("marker", ConfigManager.get().showTargetMarker), ignored -> toggleVisual(true))
                .bounds(x, y, half, 20).build()).active = active != null;
        addBodyButton(Button.builder(toggle("frame", ConfigManager.get().showFramePreview), ignored -> toggleVisual(false))
                .bounds(x + half + 4, y, detailWidth - half - 4, 20).build()).active = active != null;
        y += 24;
        Button stop = addBodyButton(Button.builder(active == null ? text("deactivate") : text("stop_named", active.name()), ignored -> perform(() -> {
            PortalManager.unlock();
            HUDOverlay.resetCompassState();
            HUDOverlay.showHints();
        })).bounds(x, y, detailWidth, 20).build());
        stop.active = active != null;
        if (active != null) stop.setTooltip(Tooltip.create(text("stop_named", active.name())));
        return y + 20;
    }

    private void navigate(boolean returning) {
        perform(() -> {
            PortalManager.select(selected, returning);
            HUDOverlay.resetCompassState();
            HUDOverlay.showHints();
            minecraft.gui.setScreen(null);
        });
    }

    void showMessage(String key) {
        messageKey = key;
        messageError = false;
        if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
    }

    private void toggleVisual(boolean marker) {
        ModConfig draft = ConfigManager.get().copy();
        if (marker) draft.showTargetMarker = !draft.showTargetMarker;
        else draft.showFramePreview = !draft.showFramePreview;
        messageKey = ConfigManager.save(draft, Netherportalhelper.getLogger()) ? null : "storage_error";
        messageError = messageKey != null;
        rebuildWidgets();
    }

    private void perform(StorageAction action) {
        messageKey = null;
        messageError = false;
        try {
            action.run();
        } catch (IOException exception) {
            Netherportalhelper.getLogger().error("Could not save portal planner change", exception);
            messageKey = "storage_error";
            messageError = true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            messageKey = "invalid_input";
            messageError = true;
        }
        // Navigation closes this screen; rebuild only while it is still open.
        if (minecraft.gui.screen() == this) rebuildWidgets();
    }

    private PortalTarget selectedTarget() {
        return targets.stream().filter(target -> target.id().equals(selected)).findFirst().orElse(null);
    }

    void reveal(UUID targetId) {
        List<PortalTarget> updated = PortalManager.targets();
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).id().equals(targetId)) {
                selected = targetId;
                page = index / Math.max(1, pageSize);
                break;
            }
        }
    }

    private Button button(String key, int x, int y, int buttonWidth, Runnable action) {
        return addRenderableWidget(Button.builder(text(key), ignored -> action.run())
                .bounds(x, y, Math.max(1, buttonWidth), 20).build());
    }

    private Button bodyButton(String key, int x, int y, int buttonWidth, Runnable action) {
        return addBodyButton(Button.builder(text(key), ignored -> action.run())
                .bounds(x, y, Math.max(1, buttonWidth), 20).build());
    }

    private Button addBodyButton(Button button) {
        bodyButtons.add(new BodyButton(button, button.getY()));
        return addRenderableWidget(button);
    }

    private void label(Component text, int x, int y, int labelWidth, int color) {
        labels.add(new Label(text.getVisualOrderText(), x, y, Math.max(1, labelWidth), color));
    }

    private int wrappedLabel(Component text, int x, int y, int labelWidth, int color) {
        for (FormattedCharSequence line : font.split(text, Math.max(1, labelWidth))) {
            labels.add(new Label(line, x, y, Math.max(1, labelWidth), color));
            y += font.lineHeight + 3;
        }
        return y;
    }

    private void scroll(int amount) {
        scrollOffset += amount;
        updateScroll();
    }

    private void updateScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        for (BodyButton entry : bodyButtons) {
            Button button = entry.button();
            button.setY(entry.y() - scrollOffset);
            button.visible = button.getY() >= BODY_TOP && button.getBottom() <= bodyBottom;
        }
        scrollUp.visible = scrollDown.visible = maxScroll > 0;
        scrollUp.active = scrollOffset > 0;
        scrollDown.active = scrollOffset < maxScroll;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (maxScroll > 0 && y >= BODY_TOP && y <= bodyBottom && scrollY != 0) {
            scroll(scrollY < 0 ? 34 : -34);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, text(minecraft.level == null ? "no_world" : "scope"), width / 2, 25, 0xFFAAAAAA);
        graphics.enableScissor(left, BODY_TOP, left + contentWidth, bodyBottom);
        for (Label label : labels) {
            int y = label.y() - scrollOffset;
            graphics.enableScissor(label.x(), BODY_TOP, label.x() + label.width(), bodyBottom);
            graphics.text(font, label.text(), label.x(), y, label.color());
            graphics.disableScissor();
        }
        graphics.disableScissor();
        graphics.horizontalLine(left, left + contentWidth, footerY - 6, 0xFF666666);
        int messageY = footerY + 26;
        for (FormattedCharSequence line : messageLines) {
            graphics.text(font, line, left + (contentWidth - font.width(line)) / 2, messageY,
                    messageError ? 0xFFFF7777 : 0xFF88EEAA);
            messageY += font.lineHeight + 2;
        }
    }

    static Component coordinates(ResourceKey<Level> dimension, BlockPos position) {
        return text("coordinates", text(dimension.equals(Level.NETHER) ? "nether" : "overworld"),
                position.getX(), position.getY(), position.getZ());
    }

    static Component text(String key, Object... arguments) {
        return Component.translatable("planner.netherportalhelper." + key, arguments);
    }

    private Component toggle(String key, boolean value) {
        return text("toggle", text(key), Component.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** Native button skin, focus, narration and input, with a second coordinate line. */
    private final class PortalEntry extends Button {
        private final Component summary;
        private final boolean selectedEntry;

        private PortalEntry(int x, int y, int width, Component name, Component summary, boolean selectedEntry, Runnable action) {
            super(x, y, width, 32, name, ignored -> action.run(), DEFAULT_NARRATION);
            this.summary = summary;
            this.selectedEntry = selectedEntry;
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return super.createNarrationMessage().append(" ").append(summary);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);
            if (selectedEntry) graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF88EEAA);
            graphics.enableScissor(getX() + 6, getY(), getRight() - 6, getBottom());
            graphics.text(font, getMessage(), getX() + 6, getY() + 5, 0xFFFFFFFF);
            graphics.text(font, summary, getX() + 6, getY() + 18, 0xFFBBBBBB);
            graphics.disableScissor();
        }
    }

    private record BodyButton(Button button, int y) { }
    private record Label(FormattedCharSequence text, int x, int y, int width, int color) { }

    @FunctionalInterface
    private interface StorageAction {
        void run() throws IOException;
    }
}
