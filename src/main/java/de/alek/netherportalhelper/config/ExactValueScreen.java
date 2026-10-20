package de.alek.netherportalhelper.config;

import de.alek.netherportalhelper.screen.PortalMenuScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/** Edits one value in the settings draft; Apply never writes the config file. */
final class ExactValueScreen extends Screen implements PortalMenuScreen {
    private final Screen parent;
    private final double min;
    private final double max;
    private final boolean integer;
    private final String suffix;
    private final DoubleConsumer setter;
    private String valueDraft;
    private EditBox input;
    private Button apply;
    private int left;
    private int contentWidth;
    private int top;
    private Component feedback;
    private int feedbackColor;

    ExactValueScreen(Screen parent, Component label, double initial, double min, double max,
                     boolean integer, String suffix, DoubleConsumer setter) {
        super(label);
        this.parent = parent;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.suffix = suffix;
        this.setter = setter;
        valueDraft = format(initial);
    }

    @Override
    protected void init() {
        contentWidth = Math.max(1, Math.min(340, width - 32));
        left = (width - contentWidth) / 2;
        top = Math.max(4, (height - 132) / 2);
        input = addRenderableWidget(new EditBox(font, left, top + 30, contentWidth, 20, title));
        input.setMaxLength(64);
        input.setValue(valueDraft);
        int buttonWidth = Math.max(1, (contentWidth - 6) / 2);
        int buttonY = Math.min(top + 105, Math.max(top + 58, height - 24));
        addRenderableWidget(Button.builder(Component.translatable("config.netherportalhelper.cancel"),
                        button -> onClose())
                .bounds(left, buttonY, buttonWidth, 20).build());
        apply = addRenderableWidget(Button.builder(Component.translatable("config.netherportalhelper.exact_apply"),
                        button -> applyValue())
                .bounds(left + buttonWidth + 6, buttonY, buttonWidth, 20).build());
        input.setResponder(value -> {
            valueDraft = value;
            validateInput();
        });
        validateInput();
        setInitialFocus(input);
    }

    private Double parsedValue() {
        try {
            double value = Double.parseDouble(valueDraft.strip());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double normalizedValue(double value) {
        double clamped = Math.max(min, Math.min(max, value));
        return integer ? Math.round(clamped) : clamped;
    }

    private void validateInput() {
        Double value = parsedValue();
        apply.active = value != null;
        input.setTextColor(value == null ? 0xFFFF7777 : 0xFFFFFFFF);
        if (value == null) {
            feedback = Component.translatable("config.netherportalhelper.exact_invalid");
            feedbackColor = 0xFFFF7777;
        } else if (value.doubleValue() != normalizedValue(value)) {
            feedback = Component.translatable("config.netherportalhelper.exact_clamped",
                    format(normalizedValue(value)) + suffix);
            feedbackColor = 0xFFFFCC77;
        } else {
            feedback = Component.empty();
            feedbackColor = 0xFFAAAAAA;
        }
    }

    private void applyValue() {
        Double value = parsedValue();
        if (value == null) {
            validateInput();
            return;
        }
        setter.accept(normalizedValue(value));
        minecraft.gui.setScreen(parent);
    }

    private String format(double value) {
        return integer ? Long.toString(Math.round(value)) : Float.toString((float) value);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        fittedText(graphics, title, top, 0xFFFFFFFF);
        fittedText(graphics, Component.translatable("config.netherportalhelper.exact_range",
                format(min) + suffix, format(max) + suffix), top + 18, 0xFFAAAAAA);
        fittedText(graphics, feedback, top + 56, feedbackColor);
    }

    private void fittedText(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        float scale = Math.min(1.0f, contentWidth / (float) Math.max(1, font.width(text)));
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) width / 2, (float) y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popMatrix();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
