package org.foranly.craftify.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.dtap.DTapConfig;
import org.foranly.craftify.client.dtap.DTapEngine;
import org.foranly.craftify.client.dtap.DTapType;

/** Auto D-tap settings: who to target, which d-tap, minimum fall and how the aim moves. */
public final class AutoDTapScreen extends DTapScreenBase {

    public AutoDTapScreen(Screen parent) {
        super(Component.literal("Auto D-tap"), parent);
    }

    @Override
    protected void init() {
        DTapConfig.Auto cfg = DTapConfig.get().auto;
        begin(8);

        toggle("Auto D-tap armed", DTapEngine::isAutoArmed, DTapEngine::setAutoArmed,
                "Same as the toggle key: aims and fires by itself once the requirements are met");
        textField("Target player", () -> cfg.targetName, v -> cfg.targetName = v, 16,
                "Exact name of the player to hit. Only this player is ever targeted.");
        cycle("D-tap to perform", DTapType.values(), () -> cfg.type, v -> cfg.type = v,
                "Elytra D-tap fires while gliding; the others fire while falling");
        stepper("Min fall distance", "blocks", () -> cfg.minFallBlocks, v -> cfg.minFallBlocks = v, 2, 40,
                "Blocks fallen before the slam counts (vanilla needs more than 1.5). More = more damage.");
        toggle("Auto aim", () -> cfg.aimEnabled, v -> cfg.aimEnabled = v,
                "Turn your view toward the target while the requirements are met");
        stepper("Aim speed", "%", () -> cfg.aimSpeedPercent, v -> cfg.aimSpeedPercent = v, 5, 100, 5,
                "Share of the remaining angle turned per tick. 100 = instant, lower = smoother on camera.");
        note("Needs the d-tap's own settings (gap, weapons, elytra off) to be set up too.");
        backButton();
    }
}
