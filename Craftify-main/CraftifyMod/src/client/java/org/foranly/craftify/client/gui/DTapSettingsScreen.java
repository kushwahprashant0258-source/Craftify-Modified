package org.foranly.craftify.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.dtap.DTapConfig;
import org.foranly.craftify.client.dtap.DTapEngine;
import org.foranly.craftify.client.dtap.DTapType;

/** Settings of one d-tap type: gap, ping-based gap, weapon priority, elytra removal, slot restore. */
public final class DTapSettingsScreen extends DTapScreenBase {

    private final DTapType type;

    public DTapSettingsScreen(Screen parent, DTapType type) {
        super(Component.literal(type.title + " settings"), parent);
        this.type = type;
    }

    @Override
    protected void init() {
        DTapConfig.TypeSettings cfg = DTapConfig.get().of(type);
        begin(8);

        toggle("Enabled", () -> cfg.enabled, v -> cfg.enabled = v, "Turn this d-tap on or off");

        if (type != DTapType.FOLLOW_UP) {
            textField(type.isGrapple() ? "Gap between throws (s)" : "Gap between hits (s)",
                    () -> DTapConfig.formatSeconds(cfg.gapSeconds), v -> {
                        double sec = DTapConfig.parseSeconds(v);
                        if (sec >= 0) {
                            cfg.gapSeconds = sec;
                        }
                    }, 10,
                    "Seconds, decimals allowed: 0, 0.001 (1 ms), 0.05 (1 tick), 0.1. You can also type 50ms.");
            toggle("Use ping-based gap", () -> cfg.pingGapEnabled, v -> {
                cfg.pingGapEnabled = v;
            }, "Use the gap saved for your current ping range instead of the fixed gap");
            action("Edit gaps per ping range", () ->
                            Minecraft.getInstance().setScreen(new PingGapScreen(this, type)),
                    "Set a different gap for low, medium and high ping");
        }

        if (type == DTapType.ELYTRA) {
            cycle("First hit with", DTapConfig.FirstWeapon.values(), () -> cfg.firstWeapon,
                    v -> cfg.firstWeapon = v,
                    "Auto = Breach mace, then axe, then sword. The second hit is always the Density mace.");
            cycle("Take elytra off", DTapConfig.ElytraOff.values(), () -> cfg.elytraOff,
                    v -> cfg.elytraOff = v,
                    "Right-clicks a chestplate (offhand or hotbar) between the hits so the slam counts");
        }

        toggle("Auto aim at target", () -> cfg.autoAim, v -> cfg.autoAim = v,
                "Turns your view onto the target before each hit or throw of this d-tap");
        toggle("Return to original slot", () -> cfg.returnToSlot, v -> cfg.returnToSlot = v,
                "Switch back to the slot you started on after the d-tap");

        int ping = DTapEngine.currentPing(Minecraft.getInstance());
        note("Your ping: " + ping + " ms -> gap now "
                + DTapConfig.formatSeconds(DTapEngine.effectiveGapSeconds(Minecraft.getInstance(), cfg)) + " s");
        backButton();
    }
}
