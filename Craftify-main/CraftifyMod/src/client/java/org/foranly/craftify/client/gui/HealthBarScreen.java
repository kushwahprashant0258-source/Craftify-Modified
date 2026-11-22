package org.foranly.craftify.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.dtap.DTapConfig;

/** Settings of the heart bar shown on name tags. */
public final class HealthBarScreen extends DTapScreenBase {

    public HealthBarScreen(Screen parent) {
        super(Component.literal("Name tag health bar"), parent);
    }

    @Override
    protected void init() {
        DTapConfig.HealthBar cfg = DTapConfig.get().healthBar;
        begin(5);
        toggle("Health bar", () -> cfg.enabled, v -> cfg.enabled = v, "Show hearts next to names above players' heads");
        cycle("Style", DTapConfig.HealthStyle.values(), () -> cfg.style, v -> cfg.style = v,
                "Hearts, a number, or both");
        toggle("Include absorption", () -> cfg.includeAbsorption, v -> cfg.includeAbsorption = v,
                "Show golden hearts for absorption (golden apples, totems)");
        toggle("Show on yourself", () -> cfg.showSelf, v -> cfg.showSelf = v,
                "Also show it on your own name tag (third person)");
        toggle("Players only", () -> cfg.playersOnly, v -> cfg.playersOnly = v,
                "Off = also show on mobs that have a visible name tag");
        backButton();
    }
}
