package org.foranly.craftify.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.dtap.DTapConfig;
import org.foranly.craftify.client.dtap.DTapEngine;
import org.foranly.craftify.client.dtap.DTapType;

/**
 * "Mace D-taps" menu: one row per d-tap type with its key binding, an on/off switch and a
 * settings button, an Auto D-tap row, and a link to the name tag health bar settings.
 */
public final class MaceDTapsScreen extends DTapScreenBase {

    private final Map<KeyMapping, Button> bindButtons = new LinkedHashMap<>();
    private KeyMapping binding;

    public MaceDTapsScreen(Screen parent) {
        super(Component.literal("Mace D-taps"), parent);
    }

    @Override
    protected void init() {
        bindButtons.clear();
        begin(DTapType.values().length + 4);
        int left = cx() - 155;

        for (DTapType type : DTapType.values()) {
            DTapConfig.TypeSettings cfg = DTapConfig.get().of(type);
            this.addRenderableWidget(label(left, rowY + 6, type.title));
            addBindButton(DTapEngine.key(type), left + 116, "Click, then press the key for \""
                    + type.title + "\" (Esc = unbind). " + type.description);

            Button enabled = Button.builder(Component.literal(cfg.enabled ? "ON" : "OFF"), b -> {
                        cfg.enabled = !cfg.enabled;
                        DTapConfig.get().save();
                        b.setMessage(Component.literal(cfg.enabled ? "ON" : "OFF"));
                    })
                    .bounds(left + 200, rowY, 34, 20)
                    .tooltip(Tooltip.create(Component.literal("Enable or disable this d-tap")))
                    .build();
            this.addRenderableWidget(enabled);

            this.addRenderableWidget(Button.builder(Component.literal("Settings"), b ->
                            Minecraft.getInstance().setScreen(new DTapSettingsScreen(this, type)))
                    .bounds(left + 238, rowY, 72, 20)
                    .tooltip(Tooltip.create(Component.literal("Timing, ping gaps, weapon priority")))
                    .build());
            rowY += 24;
        }

        // Auto D-tap row: its key toggles auto mode.
        this.addRenderableWidget(label(left, rowY + 6, "Auto D-tap"));
        addBindButton(DTapEngine.autoKey(), left + 116,
                "Click, then press the key that toggles Auto D-tap (Esc = unbind)");
        Button armed = Button.builder(armedLabel(), b -> {
                    DTapEngine.setAutoArmed(!DTapEngine.isAutoArmed());
                    b.setMessage(armedLabel());
                })
                .bounds(left + 200, rowY, 34, 20)
                .tooltip(Tooltip.create(Component.literal("Arm or disarm Auto D-tap right now")))
                .build();
        this.addRenderableWidget(armed);
        this.addRenderableWidget(Button.builder(Component.literal("Settings"), b ->
                        Minecraft.getInstance().setScreen(new AutoDTapScreen(this)))
                .bounds(left + 238, rowY, 72, 20)
                .tooltip(Tooltip.create(Component.literal("Target player, aim, minimum fall")))
                .build());
        rowY += 30;

        action("Health bar on name tags", () ->
                        Minecraft.getInstance().setScreen(new HealthBarScreen(this)),
                "Show a heart bar next to player names");
        note("Your ping: " + DTapEngine.currentPing(Minecraft.getInstance()) + " ms");
        backButton();
    }

    private static Component armedLabel() {
        return Component.literal(DTapEngine.isAutoArmed() ? "ON" : "OFF");
    }

    private void addBindButton(KeyMapping key, int x, String tip) {
        Button bind = Button.builder(bindLabel(key), b -> {
                    binding = key;
                    refreshBindLabels();
                })
                .bounds(x, rowY, 80, 20)
                .tooltip(Tooltip.create(Component.literal(tip)))
                .build();
        bindButtons.put(key, bind);
        this.addRenderableWidget(bind);
    }

    private Component bindLabel(KeyMapping key) {
        if (binding == key) {
            return Component.literal("> press key <");
        }
        return key.isUnbound() ? Component.literal("Not bound") : key.getTranslatedKeyMessage();
    }

    private void refreshBindLabels() {
        for (Map.Entry<KeyMapping, Button> entry : bindButtons.entrySet()) {
            entry.getValue().setMessage(bindLabel(entry.getKey()));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (binding != null) {
            Minecraft mc = Minecraft.getInstance();
            InputConstants.Key newKey = event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    ? InputConstants.UNKNOWN
                    : InputConstants.Type.KEYSYM.getOrCreate(event.key());
            binding.setKey(newKey);
            KeyMapping.resetMapping();
            mc.options.save();
            binding = null;
            refreshBindLabels();
            return true;
        }
        return super.keyPressed(event);
    }
}
