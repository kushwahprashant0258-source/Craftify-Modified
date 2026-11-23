package org.foranly.craftify.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.dtap.DTapConfig;
import org.foranly.craftify.client.dtap.DTapEngine;
import org.foranly.craftify.client.dtap.DTapType;

/** Gap (in seconds) used for each ping range when "Use ping-based gap" is on. */
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
            textField(labels[i] + " (s)", () -> DTapConfig.formatSeconds(cfg.pingGapSeconds[bucket]), v -> {
                double sec = DTapConfig.parseSeconds(v);
                if (sec >= 0) {
                    cfg.pingGapSeconds[bucket] = sec;
                }
            }, 10, "Seconds between the hits when your ping is in this range, e.g. 0.02 or 20ms");
        }
        int ping = DTapEngine.currentPing(Minecraft.getInstance());
        note("Your ping: " + ping + " ms (range " + (DTapConfig.bucketOf(ping) + 1) + ")");
        note("Turn on \"Use ping-based gap\" in the settings to apply these.");
        backButton();
    }
}
