package de.alek.netherportalhelper.screen;

import de.alek.netherportalhelper.Netherportalhelper;
import de.alek.netherportalhelper.util.PortalCoordinates;
import de.alek.netherportalhelper.util.PortalManager;
import de.alek.netherportalhelper.util.PortalTarget;
import de.alek.netherportalhelper.util.PortalTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.alek.netherportalhelper.screen.PortalScreen.text;

/** A retained draft: resizing and failed writes preserve all entered values. */
final class PortalEditorScreen extends Screen implements PortalMenuScreen {
    private static final int TOP = 40;
    private final PortalScreen parent;
    private final PortalTarget existing;
    private final List<BodyWidget> bodyWidgets = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private String nameDraft;
    private String sourceDraft;
    private String heightDraft;
    private Direction.Axis axis;
    private EditBox nameInput;
    private EditBox sourceInput;
    private EditBox heightInput;
    private String messageKey;
    private int left;
    private int contentWidth;
    private int bodyBottom;
    private int previewY;
    private int scrollOffset;
    private int maxScroll;
    private Button scrollUp;
    private Button scrollDown;

    PortalEditorScreen(PortalScreen parent, PortalTarget existing) {
        super(text(existing == null ? "add_title" : "edit_title"));
        this.parent = parent;
        this.existing = existing;
        nameDraft = existing == null ? "" : existing.name();
        sourceDraft = existing == null ? "" : PortalCoordinates.format(existing);
        heightDraft = existing == null || existing.buildY() == null ? "" : existing.buildY().toString();
        axis = existing == null ? Direction.Axis.X : existing.axis();
    }

    @Override
    protected void init() {
        bodyWidgets.clear();
        labels.clear();
        contentWidth = Math.min(600, Math.max(1, width - 48));
        left = (width - contentWidth) / 2;
        bodyBottom = Math.max(TOP + 20, height - 52);
        label("name", TOP, 0xFFCCCCCC);
        nameInput = input("name", TOP + 12, contentWidth, 64, nameDraft);
        nameInput.setResponder(value -> nameDraft = value);
        label("source_input", TOP + 44, 0xFFCCCCCC);
        sourceInput = input("source_input", TOP + 56, contentWidth, 160, sourceDraft);
        sourceInput.setHint(text("source_example"));
        sourceInput.setTooltip(Tooltip.create(text("portal_reference_help")));
        sourceInput.setResponder(value -> {
            sourceDraft = value;
            messageKey = null;
        });
        sourceInput.setEditable(existing == null);
        int half = (contentWidth - 4) / 2;
        Button paste = bodyButton("paste", left, TOP + 80, half, () -> {
            sourceInput.setValue(minecraft.keyboardHandler.getClipboard().strip());
            setFocused(sourceInput);
        });
        paste.active = existing == null;
        paste.setTooltip(Tooltip.create(text("paste_hint")));
        Button current = bodyButton("use_position", left + half + 4, TOP + 80, contentWidth - half - 4, () -> {
            BlockPos position = minecraft.player.blockPosition();
            String dimension = minecraft.level.dimension().equals(net.minecraft.world.level.Level.NETHER) ? "nether" : "overworld";
            sourceInput.setValue(dimension + " " + position.getX() + " " + position.getY() + " " + position.getZ());
        });
        current.active = existing == null && minecraft.player != null && minecraft.level != null
                && PortalTracker.isPortalDimension(minecraft.level.dimension());
        int buildLabelY = wrappedLabel("portal_reference_help", TOP + 105) + 10;
        label("build_y", buildLabelY, 0xFFCCCCCC);
        heightInput = input("build_y", buildLabelY + 12, half, 8, heightDraft);
        heightInput.setHint(text("reference_hint"));
        heightInput.setTooltip(Tooltip.create(text("height_help")));
        heightInput.setResponder(value -> {
            heightDraft = value;
            messageKey = null;
        });
        addBodyWidget(Button.builder(text("axis", axis.name()), button -> {
            axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            button.setMessage(text("axis", axis.name()));
        }).bounds(left + half + 4, buildLabelY + 12, contentWidth - half - 4, 20)
                .tooltip(Tooltip.create(text("axis_hint"))).build());
        previewY = wrappedLabel("height_help", buildLabelY + 38) + 12;
        int contentBottom = wrappedLabel("linking_help", previewY + 32) + 4;
        maxScroll = Math.max(0, contentBottom - bodyBottom);
        scrollUp = addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> scroll(-30))
                .bounds(left + contentWidth + 4, TOP, 16, 20).build());
        scrollDown = addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> scroll(30))
                .bounds(left + contentWidth + 4, bodyBottom - 20, 16, 20).build());
        scrollUp.setTooltip(Tooltip.create(text("scroll_hint")));
        scrollDown.setTooltip(Tooltip.create(text("scroll_hint")));
        updateScroll();
        button("cancel", left, height - 40, half, this::onClose);
        button("save", left + half + 4, height - 40, contentWidth - half - 4, this::save);
        EditBox initial = nameDraft.isBlank() ? nameInput : existing == null ? sourceInput : heightInput;
        if (!initial.visible) {
            scrollOffset += initial.getY() < TOP ? initial.getY() - TOP
                    : Math.max(0, initial.getBottom() - bodyBottom);
            updateScroll();
        }
        setInitialFocus(initial);
    }

    private void label(String key, int y, int color) {
        labels.add(new Label(text(key).getVisualOrderText(), y, color));
    }

    private int wrappedLabel(String key, int y) {
        for (FormattedCharSequence line : font.split(text(key), contentWidth)) {
            labels.add(new Label(line, y, 0xFFAAAAAA));
            y += font.lineHeight + 2;
        }
        return y;
    }

    private EditBox input(String key, int y, int inputWidth, int maxLength, String value) {
        EditBox input = new EditBox(font, left, y, Math.max(1, inputWidth), 20, text(key));
        input.setMaxLength(maxLength);
        input.setValue(value);
        return addBodyWidget(input);
    }

    private Button button(String key, int x, int y, int buttonWidth, Runnable action) {
        return addRenderableWidget(Button.builder(text(key), ignored -> action.run())
                .bounds(x, y, Math.max(1, buttonWidth), 20).build());
    }

    private Button bodyButton(String key, int x, int y, int buttonWidth, Runnable action) {
        return addBodyWidget(Button.builder(text(key), ignored -> action.run())
                .bounds(x, y, Math.max(1, buttonWidth), 20).build());
    }

    private <T extends AbstractWidget> T addBodyWidget(T widget) {
        bodyWidgets.add(new BodyWidget(widget, widget.getY()));
        return addRenderableWidget(widget);
    }

    private void scroll(int amount) {
        scrollOffset += amount;
        updateScroll();
    }

    private void updateScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        for (BodyWidget entry : bodyWidgets) {
            AbstractWidget widget = entry.widget();
            widget.setY(entry.y() - scrollOffset);
            widget.visible = widget.getY() >= TOP && widget.getBottom() <= bodyBottom;
        }
        scrollUp.visible = scrollDown.visible = maxScroll > 0;
        scrollUp.active = scrollOffset > 0;
        scrollDown.active = scrollOffset < maxScroll;
    }

    private Integer buildY() {
        return heightDraft.isBlank() ? null : Integer.valueOf(heightDraft.strip());
    }

    private void save() {
        messageKey = null;
        try {
            if (nameDraft.isBlank()) {
                messageKey = "name_required";
                scrollOffset = 0;
                updateScroll();
                setFocused(nameInput);
                return;
            }
            Integer buildY = buildY();
            if (existing == null) {
                PortalCoordinates.Input input = PortalCoordinates.parse(sourceDraft);
                Set<UUID> previousIds = PortalManager.targets().stream().map(PortalTarget::id).collect(Collectors.toSet());
                PortalManager.add(nameDraft.strip(), input.source(), input.sourceDimension(), buildY, axis);
                PortalManager.targets().stream().filter(target -> !previousIds.contains(target.id())).findFirst()
                        .ifPresent(target -> parent.reveal(target.id()));
            } else {
                PortalManager.update(existing.id(), nameDraft.strip(), buildY, axis);
            }
            minecraft.gui.setScreen(parent);
        } catch (IOException exception) {
            Netherportalhelper.getLogger().error("Could not save portal planner draft", exception);
            messageKey = "storage_error";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            messageKey = "invalid_input";
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (maxScroll > 0 && y >= TOP && y <= bodyBottom && scrollY != 0) {
            scroll(scrollY < 0 ? 30 : -30);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.enableScissor(left, TOP, left + contentWidth, bodyBottom);
        for (Label label : labels) graphics.text(font, label.text(), left, label.y() - scrollOffset, label.color());
        try {
            PortalCoordinates.Input input = PortalCoordinates.parse(sourceDraft);
            BlockPos reference = PortalTracker.calculateCounterpartPosition(input.source(), input.sourceDimension());
            Integer buildY = buildY();
            BlockPos destination = buildY == null ? reference : new BlockPos(reference.getX(), buildY, reference.getZ());
            graphics.text(font, text("destination", PortalScreen.coordinates(
                    PortalTracker.getCounterpartDimension(input.sourceDimension()), destination)), left, previewY - scrollOffset, 0xFF88EEAA);
            graphics.text(font, text("reference", reference.getY(), axis.name()), left, previewY + 13 - scrollOffset, 0xFFAAAAAA);
        } catch (IllegalArgumentException ignored) {
            graphics.text(font, text("source_example"), left, previewY - scrollOffset, 0xFFAAAAAA);
        }
        graphics.disableScissor();
        graphics.horizontalLine(left, left + contentWidth, height - 46, 0xFF666666);
        if (messageKey != null) graphics.centeredText(font, text(messageKey), width / 2, height - 14, 0xFFFF7777);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private record BodyWidget(AbstractWidget widget, int y) { }
    private record Label(FormattedCharSequence text, int y, int color) { }
}
