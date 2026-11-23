package org.foranly.craftify.client.youtube;

import com.mojang.blaze3d.platform.InputConstants;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.CraftifyClient;

/**
 * Plays the audio of a YouTube video inside the game using YouTube's own embedded player
 * (hosted in a hidden Chromium from the MCEF Modern mod). Nothing is downloaded or extracted.
 */
public final class YouTubePlayer {

    private static final Pattern ID = Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})");
    private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private static KeyMapping pauseKey;
    private static int volume = 60;
    private static int countdown = -1;
    private static String status = "";

    private YouTubePlayer() {
    }

    public static void register() {
        pauseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.craftify.youtube.pause",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CraftifyClient.CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(YouTubePlayer::tick);
    }

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("mcef-modern");
    }

    public static String status() {
        return status;
    }

    public static int volume() {
        return volume;
    }

    public static void setVolume(int percent) {
        volume = Math.max(0, Math.min(100, percent));
        script("var v=document.querySelector('video');if(v){v.volume=" + (volume / 100.0D) + ";}");
    }

    /** Plays a YouTube link / video id, or searches YouTube for the text and plays the first result. */
    public static void play(String input) {
        if (!installed()) {
            status = "Install the MCEF Modern mod (modrinth.com/mod/mcef-modern) first.";
            return;
        }
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            status = "Type a YouTube link or a song name first.";
            return;
        }
        String progress = YtBackend.ready();
        if (progress != null) {
            status = progress + " - press Play again when done.";
            return;
        }
        String id = extractId(text);
        if (id != null) {
            YtBackend.load("https://www.youtube.com/embed/" + id + "?autoplay=1&controls=0&rel=0");
            status = "Loading video " + id + "...";
        } else {
            String query = URLEncoder.encode(text, StandardCharsets.UTF_8);
            YtBackend.load("https://www.youtube.com/results?search_query=" + query);
            // Wait for the results, then jump to the first video's embed page.
            script("var t=setInterval(function(){var a=document.querySelector('ytd-video-renderer a#video-title');"
                    + "if(a){clearInterval(t);var m=/[?&]v=([A-Za-z0-9_-]{11})/.exec(a.href);"
                    + "if(m){location.href='https://www.youtube.com/embed/'+m[1]+'?autoplay=1&controls=0&rel=0';}}},300);");
            status = "Searching for \"" + text + "\"...";
        }
        countdown = 120; // 6 s later: make sure it is playing and apply the volume
    }

    private static String extractId(String text) {
        Matcher m = ID.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return BARE_ID.matcher(text).matches() ? text : null;
    }

    public static void togglePause() {
        script("var v=document.querySelector('video');if(v){if(v.paused){v.play();}else{v.pause();}}");
    }

    public static void seek(int seconds) {
        script("var v=document.querySelector('video');if(v){v.currentTime=Math.max(0,v.currentTime+(" + seconds + "));}");
    }

    /** Fallback when the browser blocks autoplay: a real click on the player starts it. */
    public static void clickToStart() {
        if (installed() && YtBackend.active()) {
            YtBackend.click();
        }
    }

    public static void stop() {
        if (installed()) {
            YtBackend.stop();
        }
        status = "Stopped.";
    }

    private static void script(String code) {
        if (installed() && YtBackend.active()) {
            YtBackend.js(code);
        }
    }

    private static void tick(Minecraft mc) {
        while (pauseKey.consumeClick()) {
            togglePause();
        }
        if (countdown >= 0 && countdown-- == 0) {
            script("var v=document.querySelector('video');if(v){v.volume=" + (volume / 100.0D)
                    + ";if(v.paused){v.play();}}");
            status = "Playing (if silent, press \"Start audio (click)\").";
        }
    }

    public static Component title() {
        return Component.literal("YouTube audio");
    }
}
