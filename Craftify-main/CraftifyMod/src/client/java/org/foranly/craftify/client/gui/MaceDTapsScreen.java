package org.foranly.craftify.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
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
 * settings button, plus a link to the name tag health bar settings.
 */
public final class MaceDTapsScreen extends DTapScreenBase {

    private final Map<DTapType, Button> bindButtons = new EnumMap<>(DTapType.class);
    private DTapType binding;

    public MaceDTapsScreen(Screen parent) {
        super(Component.literal("Mace D-taps"), parent);
    }

    @Override
    protected void init() {
        bindButtons.clear();
        begin(DTapType.values().length + 3);

        for (DTapType type : DTapType.values()) {
            DTapConfig.TypeSettings cfg = DTapConfig.get().of(type);
            int left = cx() - 155;

            this.addRenderableWidget(label(left, rowY + 6, type.title));

            Button bind = Button.builder(bindLabel(type), b -> {
                        binding = type;
                        refreshBindLabels();
                    })
                    .bounds(left + 116, rowY, 80, 20)
                    .tooltip(Tooltip.create(Component.literal("Click, then press the key for \""
                            + type.title + "\" (Esc = unbind). " + type.description)))
                    .build();
            bindButtons.put(type, bind);
            this.addRenderableWidget(bind);

            Button enabled = Button.builder(Component.literal(cfg.enabled ? "ON" : "OFF"), b -> {
                        cfg.enabled = !cfg.enabled;
                        DTapConfig.get().save();
                        b.setMessage(Component.literal(cfg.enabled ? "ON" : "OFF"));
                    })
                    .bounds(left + 200, rowY, 34, 20)
                    .tooltip(Tooltip.create(Component.literal("Enable or disable this d-tap")))
                    .build();
            this.addRenderableWidget(enabled);

            Button settings = Button.builder(Component.literal("Settings"), b ->
                            Minecraft.getInstance().setScreen(new DTapSettingsScreen(this, type)))
                    .bounds(left + 238, rowY, 72, 20)
                    .tooltip(Tooltip.create(Component.literal("Timing, ping gaps, weapon priority")))
                    .build();
            this.addRenderableWidget(settings);
            rowY += 24;
        }

        rowY += 6;
        action("Health bar on name tags", () ->
                        Minecraft.getInstance().setScreen(new HealthBarScreen(this)),
                "Show a heart bar next to player names");
        note("Your ping: " + DTapEngine.currentPing(Minecraft.getInstance()) + " ms");
        backButton();
    }

    private Component bindLabel(DTapType type) {
        if (binding == type) {
            return Component.literal("> press key <");
        }
        KeyMapping key = DTapEngine.key(type);
        return key.isUnbound() ? Component.literal("Not bound") : key.getTranslatedKeyMessage();
    }

    private void refreshBindLabels() {
        for (Map.Entry<DTapType, Button> entry : bindButtons.entrySet()) {
            entry.getValue().setMessage(bindLabel(entry.getKey()));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (binding != null) {
            Minecraft mc = Minecraft.getInstance();
            KeyMapping key = DTapEngine.key(binding);
            InputConstants.Key newKey = event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    ? InputConstants.UNKNOWN
                    : InputConstants.Type.KEYSYM.getOrCreate(event.key());
            key.setKey(newKey);
            KeyMapping.resetMapping();
            mc.options.save();
            binding = null;
            refreshBindLabels();
            return true;
        }
        return super.keyPressed(event);
    }
}
