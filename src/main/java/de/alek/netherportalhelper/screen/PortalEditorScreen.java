package de.alek.netherportalhelper.screen;

import de.alek.netherportalhelper.Netherportalhelper;
import de.alek.netherportalhelper.util.PortalCoordinates;
import de.alek.netherportalhelper.util.PortalManager;
import de.alek.netherportalhelper.util.PortalTarget;
import de.alek.netherportalhelper.util.PortalTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.alek.netherportalhelper.screen.PortalScreen.text;

/** A retained draft: resizing and failed writes preserve all entered values. */
final class PortalEditorScreen extends Screen {
    private final PortalScreen parent;
    private final PortalTarget existing;
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
    private int top;

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
        contentWidth = Math.min(560, Math.max(1, width - 24));
        left = (width - contentWidth) / 2;
        top = Math.max(29, (height - 208) / 2);
        nameInput = input("name", left, top + 11, contentWidth, 64, nameDraft);
        nameInput.setResponder(value -> nameDraft = value);
        sourceInput = input("source_input", left, top + 48, contentWidth, 160, sourceDraft);
        sourceInput.setHint(text("source_example"));
        sourceInput.setResponder(value -> {
            sourceDraft = value;
            messageKey = null;
        });
        sourceInput.setEditable(existing == null);
        int half = (contentWidth - 4) / 2;
        Button paste = button("paste", left, top + 72, half, () -> {
            sourceInput.setValue(minecraft.keyboardHandler.getClipboard().strip());
            setFocused(sourceInput);
        });
        paste.active = existing == null;
        paste.setTooltip(Tooltip.create(text("paste_hint")));
        Button current = button("use_position", left + half + 4, top + 72, half, () -> {
            BlockPos position = minecraft.player.blockPosition();
            String dimension = minecraft.level.dimension().equals(net.minecraft.world.level.Level.NETHER) ? "nether" : "overworld";
            sourceInput.setValue(dimension + " " + position.getX() + " " + position.getY() + " " + position.getZ());
        });
        current.active = existing == null && minecraft.player != null && minecraft.level != null
                && PortalTracker.isPortalDimension(minecraft.level.dimension());
        heightInput = input("build_y", left, top + 109, half, 8, heightDraft);
        heightInput.setHint(text("reference_hint"));
        heightInput.setResponder(value -> {
            heightDraft = value;
            messageKey = null;
        });
        addRenderableWidget(Button.builder(text("axis", axis.name()), button -> {
            axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            button.setMessage(text("axis", axis.name()));
        }).bounds(left + half + 4, top + 109, half, 20)
                .tooltip(Tooltip.create(text("axis_hint"))).build());
        button("cancel", left, height - 42, half, this::onClose);
        button("save", left + half + 4, height - 42, half, this::save);
        setInitialFocus(nameDraft.isBlank() ? nameInput : existing == null ? sourceInput : heightInput);
    }

    private EditBox input(String key, int x, int y, int inputWidth, int maxLength, String value) {
        EditBox input = new EditBox(font, x, y, inputWidth, 20, text(key));
        input.setMaxLength(maxLength);
        input.setValue(value);
        return addRenderableWidget(input);
    }

    private Button button(String key, int x, int y, int buttonWidth, Runnable action) {
        return addRenderableWidget(Button.builder(text(key), ignored -> action.run())
                .bounds(x, y, Math.max(1, buttonWidth), 20).build());
    }

    private Integer buildY() {
        return heightDraft.isBlank() ? null : Integer.valueOf(heightDraft.strip());
    }

    private void save() {
        messageKey = null;
        try {
            if (nameDraft.isBlank()) {
                messageKey = "name_required";
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.text(font, text("name"), left, top, 0xFFCCCCCC);
        graphics.text(font, text("source_input"), left, top + 37, 0xFFCCCCCC);
        graphics.text(font, text("build_y"), left, top + 98, 0xFFCCCCCC);
        clippedLine(graphics, text("height_help"), top + 133, 0xFFAAAAAA);
        try {
            PortalCoordinates.Input input = PortalCoordinates.parse(sourceDraft);
            BlockPos reference = PortalTracker.calculateCounterpartPosition(input.source(), input.sourceDimension());
            Integer buildY = buildY();
            BlockPos destination = buildY == null ? reference : new BlockPos(reference.getX(), buildY, reference.getZ());
            clippedLine(graphics, text("destination", PortalScreen.coordinates(
                    PortalTracker.getCounterpartDimension(input.sourceDimension()), destination)), top + 147, 0xFF88EEAA);
            clippedLine(graphics, text("reference", reference.getY(), axis.name()), top + 160, 0xFFAAAAAA);
        } catch (IllegalArgumentException ignored) {
            clippedLine(graphics, text("source_example"), top + 147, 0xFFAAAAAA);
        }
        if (messageKey != null) graphics.centeredText(font, text(messageKey), width / 2, height - 16, 0xFFFF7777);
    }

    private void clippedLine(GuiGraphicsExtractor graphics, Component line, int y, int color) {
        graphics.enableScissor(left, y, left + contentWidth, y + 10);
        graphics.text(font, line, left, y, color);
        graphics.disableScissor();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
