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
    private int scrollOffset;
    private int maxScroll;
    private Button scrollUp;
    private Button scrollDown;
    private String messageKey;
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
        bodyBottom = Math.max(BODY_TOP + 20, height - 52);
        boolean wide = contentWidth >= 600;
        int listWidth = wide ? (contentWidth - 20) * 2 / 5 : contentWidth;
        int detailLeft = wide ? left + listWidth + 20 : left;
        int detailWidth = wide ? contentWidth - listWidth - 20 : contentWidth;
        int previousPageSize = pageSize;
        // A short stacked list keeps the selected pair directly beside its details in the flow.
        pageSize = wide ? Math.max(1, (bodyBottom - BODY_TOP - 68) / 34)
                : Math.max(1, Math.min(4, (height - 280) / 34));
        if (previousPageSize > 0 && previousPageSize != pageSize) page = page * previousPageSize / pageSize;
        int pages = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        boolean inWorld = minecraft.player != null && minecraft.level != null;
        label(text("saved_pairs"), left, BODY_TOP, listWidth, 0xFFFFFFFF);
        int half = (listWidth - 4) / 2;
        Button capture = bodyButton("capture", left, BODY_TOP + 14, half, () -> perform(() -> {
            PortalManager.capture(minecraft, text("default_name", targets.size() + 1).getString());
            List<PortalTarget> updated = PortalManager.targets();
            if (!updated.isEmpty()) reveal(updated.getLast().id());
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
            entry.setTooltip(Tooltip.create(summary));
        }
        int pageY = listTop + Math.max(1, shown) * 34;
        if (targets.isEmpty()) label(text("empty"), left, listTop + 10, listWidth, 0xFFAAAAAA);
        bodyButton("previous", left, pageY, 26, () -> {
            page--;
            rebuildWidgets();
        }).active = page > 0;
        bodyButton("next", left + listWidth - 26, pageY, 26, () -> {
            page++;
            rebuildWidgets();
        }).active = page < pages - 1;
        label(text("page", page + 1, pages), left + 30, pageY + 6, listWidth - 60, 0xFFAAAAAA);
        int detailTop = wide ? BODY_TOP : pageY + 32;
        int detailBottom = addDetails(detailLeft, detailTop, detailWidth, inWorld, active);
        int contentBottom = Math.max(pageY + 20, detailBottom);
        maxScroll = Math.max(0, contentBottom - bodyBottom);
        scrollUp = addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> scroll(-34))
                .bounds(left + contentWidth + 4, BODY_TOP, 16, 20).build());
        scrollDown = addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> scroll(34))
                .bounds(left + contentWidth + 4, bodyBottom - 20, 16, 20).build());
        scrollUp.setTooltip(Tooltip.create(text("scroll_hint")));
        scrollDown.setTooltip(Tooltip.create(text("scroll_hint")));
        updateScroll();
        int footerHalf = (contentWidth - 4) / 2;
        button("settings", left, height - 40, footerHalf,
                () -> minecraft.gui.setScreen(new ConfigScreen(this)));
        button("done", left + footerHalf + 4, height - 40, contentWidth - footerHalf - 4, this::onClose);
    }

    private int addDetails(int x, int y, int detailWidth, boolean inWorld, PortalTarget active) {
        label(text("selected_target"), x, y, detailWidth, 0xFFFFFFFF);
        PortalTarget target = selectedTarget();
        boolean hasSelection = target != null;
        if (target != null) {
            label(Component.literal(target.name()), x, y + 15, detailWidth, 0xFFFFFFFF);
            label(text("source", coordinates(target.sourceDimension(), target.source())), x, y + 30, detailWidth, 0xFFCCCCCC);
            label(text("destination", coordinates(target.destinationDimension(), target.destination())), x, y + 43, detailWidth, 0xFF88EEAA);
            label(text("reference", target.calculatedDestination().getY(), target.axis().name()), x, y + 56, detailWidth, 0xFFAAAAAA);
        } else label(text("no_selection"), x, y + 30, detailWidth, 0xFFAAAAAA);
        int secondaryWidth = Math.max(1, (detailWidth - 4) / 3);
        int primaryWidth = detailWidth - secondaryWidth - 4;
        bodyButton("navigate", x, y + 74, primaryWidth, () -> perform(() -> {
            PortalManager.select(selected);
            HUDOverlay.resetCompassState();
            HUDOverlay.showHints();
            minecraft.gui.setScreen(null);
        })).active = hasSelection && inWorld;
        bodyButton("edit", x + primaryWidth + 4, y + 74, secondaryWidth,
                () -> minecraft.gui.setScreen(new PortalEditorScreen(this, selectedTarget()))).active = hasSelection;
        int half = (detailWidth - 4) / 2;
        bodyButton("copy", x, y + 98, half, () -> {
            minecraft.keyboardHandler.setClipboard(PortalCoordinates.format(selectedTarget()));
            messageKey = "copied";
        }).active = hasSelection;
        bodyButton("delete", x + half + 4, y + 98, detailWidth - half - 4, () -> perform(() -> {
            int previousIndex = targets.indexOf(selectedTarget());
            PortalManager.delete(selected);
            List<PortalTarget> updated = PortalManager.targets();
            selected = updated.isEmpty() ? null : updated.get(Math.min(previousIndex, updated.size() - 1)).id();
        })).active = hasSelection;
        label(text("build_preview"), x, y + 134, detailWidth, 0xFFFFFFFF);
        addBodyButton(Button.builder(toggle("marker", ConfigManager.get().showTargetMarker), ignored -> toggleVisual(true))
                .bounds(x, y + 149, half, 20).build()).active = hasSelection;
        addBodyButton(Button.builder(toggle("frame", ConfigManager.get().showFramePreview), ignored -> toggleVisual(false))
                .bounds(x + half + 4, y + 149, detailWidth - half - 4, 20).build()).active = hasSelection;
        bodyButton("deactivate", x, y + 181, detailWidth, () -> perform(() -> {
            PortalManager.unlock();
            HUDOverlay.resetCompassState();
            HUDOverlay.showHints();
        })).active = active != null || PortalTracker.isActive();
        return y + 201;
    }

    private void toggleVisual(boolean marker) {
        ModConfig draft = ConfigManager.get().copy();
        if (marker) draft.showTargetMarker = !draft.showTargetMarker;
        else draft.showFramePreview = !draft.showFramePreview;
        messageKey = ConfigManager.save(draft, Netherportalhelper.getLogger()) ? null : "storage_error";
        rebuildWidgets();
    }

    private void perform(StorageAction action) {
        messageKey = null;
        try {
            action.run();
            // Navigate closes this screen; rebuilding it would reopen and reset focus unnecessarily.
            if (minecraft.gui.screen() == this) rebuildWidgets();
        } catch (IOException exception) {
            Netherportalhelper.getLogger().error("Could not save portal planner change", exception);
            messageKey = "storage_error";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            messageKey = "invalid_input";
        }
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
        labels.add(new Label(text, x, y, Math.max(1, labelWidth), color));
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
        graphics.horizontalLine(left, left + contentWidth, height - 46, 0xFF666666);
        if (messageKey != null) graphics.centeredText(font, text(messageKey), width / 2, height - 14,
                messageKey.equals("copied") ? 0xFF88EEAA : 0xFFFF7777);
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
    private record Label(Component text, int x, int y, int width, int color) { }

    @FunctionalInterface
    private interface StorageAction {
        void run() throws IOException;
    }
}
