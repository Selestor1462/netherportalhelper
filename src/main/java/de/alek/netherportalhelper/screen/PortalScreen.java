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
import java.util.List;
import java.util.UUID;

/** World-scoped saved portal pairs and navigation controls. */
public final class PortalScreen extends Screen {
    private final Screen parent;
    private UUID selected;
    private int page;
    private int pageSize;
    private int left;
    private int contentWidth;
    private int listBottom;
    private String messageKey;
    private List<PortalTarget> targets = List.of();

    public PortalScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        targets = PortalManager.targets();
        if (selected == null || targets.stream().noneMatch(target -> target.id().equals(selected))) {
            PortalTarget active = PortalManager.activeTarget();
            selected = active != null ? active.id() : targets.isEmpty() ? null : targets.getFirst().id();
        }
        contentWidth = Math.min(640, Math.max(1, width - 24));
        left = (width - contentWidth) / 2;
        listBottom = height - 156;
        pageSize = Math.max(1, (listBottom - 64) / 22);
        int pages = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        boolean inWorld = minecraft.player != null && minecraft.level != null;
        int half = (contentWidth - 4) / 2;
        Button capture = button("capture", left, 40, half, () -> perform(() -> {
            PortalManager.capture(minecraft, text("default_name", targets.size() + 1).getString());
            List<PortalTarget> updated = PortalManager.targets();
            if (!updated.isEmpty()) {
                selected = updated.getLast().id();
                page = (updated.size() - 1) / pageSize;
            }
        }));
        capture.active = inWorld && PortalTracker.isPortalDimension(minecraft.level.dimension());
        capture.setTooltip(Tooltip.create(text("capture_hint")));
        button("add", left + half + 4, 40, half,
                () -> minecraft.gui.setScreen(new PortalEditorScreen(this, null))).active = inWorld;
        PortalTarget active = PortalManager.activeTarget();
        for (int index = page * pageSize; index < Math.min(targets.size(), (page + 1) * pageSize); index++) {
            PortalTarget target = targets.get(index);
            Component label = text(target.id().equals(selected) ? "selected_entry" : "entry", target.name());
            if (active != null && target.id().equals(active.id())) label = text("active_entry", label);
            Button entry = addRenderableWidget(Button.builder(label, ignored -> {
                selected = target.id();
                messageKey = null;
                rebuildWidgets();
            }).bounds(left, 64 + (index - page * pageSize) * 22, contentWidth, 20).build());
            entry.setTooltip(Tooltip.create(text("pair_hint", coordinates(target.sourceDimension(), target.source()),
                    coordinates(target.destinationDimension(), target.destination()))));
        }
        button("previous", left, listBottom + 2, 42, () -> {
            page--;
            rebuildWidgets();
        }).active = page > 0;
        button("next", left + contentWidth - 42, listBottom + 2, 42, () -> {
            page++;
            rebuildWidgets();
        }).active = page < pages - 1;
        int quarter = (contentWidth - 12) / 4;
        boolean hasSelection = selectedTarget() != null;
        button("navigate", left, height - 92, quarter, () -> perform(() -> {
            PortalManager.select(selected);
            HUDOverlay.resetCompassState();
            HUDOverlay.showHints();
            minecraft.gui.setScreen(null);
        })).active = hasSelection && inWorld;
        button("edit", left + quarter + 4, height - 92, quarter,
                () -> minecraft.gui.setScreen(new PortalEditorScreen(this, selectedTarget()))).active = hasSelection;
        button("copy", left + 2 * (quarter + 4), height - 92, quarter, () -> {
            minecraft.keyboardHandler.setClipboard(PortalCoordinates.format(selectedTarget()));
            messageKey = "copied";
        }).active = hasSelection;
        button("delete", left + 3 * (quarter + 4), height - 92, quarter,
                () -> perform(() -> PortalManager.delete(selected))).active = hasSelection;
        addRenderableWidget(Button.builder(toggle("marker", ConfigManager.get().showTargetMarker), ignored -> toggleVisual(true))
                .bounds(left, height - 68, half, 20).build());
        addRenderableWidget(Button.builder(toggle("frame", ConfigManager.get().showFramePreview), ignored -> toggleVisual(false))
                .bounds(left + half + 4, height - 68, half, 20).build());
        int third = (contentWidth - 8) / 3;
        button("deactivate", left, height - 44, third, () -> perform(() -> {
            PortalManager.unlock();
            HUDOverlay.resetCompassState();
            HUDOverlay.showHints();
        })).active = active != null || PortalTracker.isActive();
        button("settings", left + third + 4, height - 44, third,
                () -> minecraft.gui.setScreen(new ConfigScreen(this)));
        button("done", left + 2 * (third + 4), height - 44, third, this::onClose);
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
            rebuildWidgets();
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, text(minecraft.level == null ? "no_world" : "scope"), width / 2, 25, 0xFFAAAAAA);
        if (targets.isEmpty()) graphics.centeredText(font, text("empty"), width / 2, 67, 0xFFAAAAAA);
        int pages = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
        graphics.centeredText(font, text("page", page + 1, pages), width / 2, listBottom + 8, 0xFFFFFFFF);
        PortalTarget target = selectedTarget();
        if (target != null) {
            clippedLine(graphics, text("source", coordinates(target.sourceDimension(), target.source())), listBottom + 26, 0xFFCCCCCC);
            clippedLine(graphics, text("destination", coordinates(target.destinationDimension(), target.destination())), listBottom + 37, 0xFF88EEAA);
            clippedLine(graphics, text("reference", target.calculatedDestination().getY(), target.axis().name()), listBottom + 48, 0xFFAAAAAA);
        }
        if (messageKey != null) graphics.centeredText(font, text(messageKey), width / 2, height - 16,
                messageKey.equals("copied") ? 0xFF88EEAA : 0xFFFF7777);
    }

    private void clippedLine(GuiGraphicsExtractor graphics, Component line, int y, int color) {
        graphics.enableScissor(left, y, left + contentWidth, y + 10);
        graphics.text(font, line, left, y, color);
        graphics.disableScissor();
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

    @FunctionalInterface
    private interface StorageAction {
        void run() throws IOException;
    }
}
