package org.foranly.craftify.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.dtap.DTapConfig;
import org.foranly.craftify.client.dtap.DTapEngine;
import org.foranly.craftify.client.dtap.DTapType;

/** Gap (in ticks) used for each ping range when "Use ping-based gap" is on. */
public final class PingGapScreen extends DTapScreenBase {

    private final DTapType type;

    public PingGapScreen(Screen parent, DTapType type) {
        super(Component.literal(type.title + " - gap per ping"), parent);
        this.type = type;
    }

    @Override
    protected void init() {
        DTapConfig.TypeSettings cfg = DTapConfig.get().of(type);
        begin(7);
        String[] labels = {"Under 50 ms", "50 - 99 ms", "100 - 149 ms", "150 ms and up"};
        for (int i = 0; i < labels.length; i++) {
            final int bucket = i;
            stepper(labels[i], "ticks", () -> cfg.pingGaps[bucket], v -> cfg.pingGaps[bucket] = v, 0, 20,
                    "Ticks between the hits when your ping is in this range (20 ticks = 1 second)");
        }
        int ping = DTapEngine.currentPing(Minecraft.getInstance());
        note("Your ping: " + ping + " ms (range " + (DTapConfig.bucketOf(ping) + 1) + ")");
        note("Turn on \"Use ping-based gap\" in the settings to apply these.");
        backButton();
    }
}
