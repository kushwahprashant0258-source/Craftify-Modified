package org.foranly.craftify.client.youtube;

import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Everything that touches MCEF lives here so the mod still loads when MCEF is not installed. */
final class YtBackend {

    private static MCEFBrowser browser;

    private YtBackend() {
    }

    /** @return null when ready, otherwise a short progress text. */
    static String ready() {
        MCEFApi.Initialization init = MCEFApi.initialize();
        if (init.isDone()) {
            return null;
        }
        return "Chromium is setting up: " + init.getStage() + " " + Math.round(init.getPercentage() * 100.0F) + "%";
    }

    static void load(String url) {
        if (browser == null) {
            browser = MCEFApi.getInstance().createBrowser(url, false);
            browser.resize(640, 360);
        } else {
            browser.getCefBrowser().loadURL(url);
        }
    }

    static void js(String code) {
        if (browser != null) {
            browser.getCefBrowser().executeJavaScript(code, browser.getCefBrowser().getURL(), 0);
        }
    }

    static boolean active() {
        return browser != null;
    }

    static void click() {
        if (browser != null) {
            MouseButtonEvent event = new MouseButtonEvent(320.0D, 180.0D, new MouseButtonInfo(0, 0));
            browser.onMouseMoved(320, 180);
            browser.onMouseClicked(event, false);
            browser.onMouseReleased(event);
        }
    }

    static void stop() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception ignored) {
                // closing is best effort
            }
            browser = null;
        }
    }
}
