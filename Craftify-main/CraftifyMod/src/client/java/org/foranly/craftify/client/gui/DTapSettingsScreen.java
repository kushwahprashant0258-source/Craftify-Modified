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
        begin(7);

        toggle("Enabled", () -> cfg.enabled, v -> cfg.enabled = v, "Turn this d-tap on or off");

        if (type != DTapType.FOLLOW_UP) {
            stepper(type.isGrapple() ? "Gap between throws" : "Gap between hits", "ticks", () -> cfg.gapTicks, v -> cfg.gapTicks = v, 0, 20,
                    "20 ticks = 1 second. 0 = same tick. If the second hit does not pop, raise it by 1.");
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

        toggle("Return to original slot", () -> cfg.returnToSlot, v -> cfg.returnToSlot = v,
                "Switch back to the slot you started on after the d-tap");

        int ping = DTapEngine.currentPing(Minecraft.getInstance());
        note("Your ping: " + ping + " ms -> gap now "
                + DTapEngine.effectiveGap(Minecraft.getInstance(), cfg) + " ticks");
        backButton();
    }
}
