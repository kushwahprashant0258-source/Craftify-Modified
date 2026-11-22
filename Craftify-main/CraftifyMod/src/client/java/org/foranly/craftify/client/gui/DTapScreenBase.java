package org.foranly.craftify.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.dtap.DTapConfig;

/** Shared row-building helpers for the D-tap settings screens. */
abstract class DTapScreenBase extends Screen {

    protected final Screen parent;
    protected int rowY;

    protected DTapScreenBase(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Starts a new column of rows, centered vertically for {@code rows} rows (plus title and back). */
    protected void begin(int rows) {
        rowY = Math.max(34, this.height / 2 - (rows * 24) / 2);
        StringWidget heading = new StringWidget(0, Math.max(8, rowY - 24), this.width, 12, this.title, this.font);
        this.addRenderableWidget(heading);
    }

    protected int cx() {
        return this.width / 2;
    }

    protected Button toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
        Button button = Button.builder(onOff(label, get.getAsBoolean()), b -> {
                    set.accept(!get.getAsBoolean());
                    DTapConfig.get().save();
                    b.setMessage(onOff(label, get.getAsBoolean()));
                })
                .bounds(cx() - 120, rowY, 240, 20)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
        this.addRenderableWidget(button);
        rowY += 24;
        return button;
    }

    protected <E extends Enum<E>> Button cycle(String label, E[] values, Supplier<E> get, Consumer<E> set, String tip) {
        Button button = Button.builder(Component.literal(label + ": " + pretty(get.get())), b -> {
                    E current = get.get();
                    set.accept(values[(current.ordinal() + 1) % values.length]);
                    DTapConfig.get().save();
                    b.setMessage(Component.literal(label + ": " + pretty(get.get())));
                })
                .bounds(cx() - 120, rowY, 240, 20)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
        this.addRenderableWidget(button);
        rowY += 24;
        return button;
    }

    /** A "label  [-] value [+]" row. */
    protected void stepper(String label, String unit, IntSupplier get, IntConsumer set, int min, int max, String tip) {
        stepper(label, unit, get, set, min, max, 1, tip);
    }

    protected void stepper(String label, String unit, IntSupplier get, IntConsumer set, int min, int max,
                           int step, String tip) {
        StringWidget name = label(cx() - 120, rowY + 6, label);
        StringWidget value = new StringWidget(cx() + 36, rowY + 6, 54, 10,
                Component.literal(get.getAsInt() + " " + unit), this.font);
        Button minus = Button.builder(Component.literal("-"), b -> {
                    set.accept(Math.max(min, get.getAsInt() - step));
                    DTapConfig.get().save();
                    value.setMessage(Component.literal(get.getAsInt() + " " + unit));
                })
                .bounds(cx() + 10, rowY, 20, 20).tooltip(Tooltip.create(Component.literal(tip))).build();
        Button plus = Button.builder(Component.literal("+"), b -> {
                    set.accept(Math.min(max, get.getAsInt() + step));
                    DTapConfig.get().save();
                    value.setMessage(Component.literal(get.getAsInt() + " " + unit));
                })
                .bounds(cx() + 96, rowY, 24, 20).tooltip(Tooltip.create(Component.literal(tip))).build();
        this.addRenderableWidget(name);
        this.addRenderableWidget(minus);
        this.addRenderableWidget(value);
        this.addRenderableWidget(plus);
        rowY += 24;
    }

    /** Text label whose box is exactly as wide as its text, so alignment does not matter. */
    protected StringWidget label(int x, int y, String text) {
        return new StringWidget(x, y, this.font.width(text), 10, Component.literal(text), this.font);
    }

    /** A "label  [text box]" row. */
    protected EditBox textField(String label, java.util.function.Supplier<String> get,
                                Consumer<String> set, int maxLength, String tip) {
        this.addRenderableWidget(label(cx() - 120, rowY + 6, label));
        EditBox box = new EditBox(this.font, cx() + 6, rowY, 114, 20, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(get.get());
        box.setResponder(text -> {
            set.accept(text);
            DTapConfig.get().save();
        });
        box.setTooltip(Tooltip.create(Component.literal(tip)));
        this.addRenderableWidget(box);
        rowY += 24;
        return box;
    }

    protected Button action(String label, Runnable run, String tip) {
        Button button = Button.builder(Component.literal(label), b -> run.run())
                .bounds(cx() - 120, rowY, 240, 20)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
        this.addRenderableWidget(button);
        rowY += 24;
        return button;
    }

    protected StringWidget note(String text) {
        StringWidget widget = new StringWidget(cx() - 120, rowY + 4, 240, 10, Component.literal(text), this.font);
        this.addRenderableWidget(widget);
        rowY += 16;
        return widget;
    }

    protected void backButton() {
        rowY += 6;
        action("Back", this::onClose, "Return to the previous menu");
    }

    protected static Component onOff(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    protected static String pretty(Enum<?> value) {
        String text = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
