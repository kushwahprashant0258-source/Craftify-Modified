package org.foranly.craftify.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.youtube.YouTubePlayer;

/** Play the audio of a YouTube video (link or search text) inside the game. */
public final class YouTubeScreen extends DTapScreenBase {

    private static String query = "";

    public YouTubeScreen(Screen parent) {
        super(Component.literal("YouTube audio"), parent);
    }

    @Override
    protected void init() {
        begin(10);
        if (!YouTubePlayer.installed()) {
            note("Needs the MCEF Modern mod: modrinth.com/mod/mcef-modern");
        }
        textField("Link or search", () -> query, v -> query = v, 200,
                "Paste a YouTube link, or type a song name to play the first search result");
        action("Play", () -> YouTubePlayer.play(query), "Start playing the audio");
        action("Pause / Resume", YouTubePlayer::togglePause, "Also available as a key in Controls");
        action("Start audio (click)", YouTubePlayer::clickToStart,
                "Use this if nothing plays: sends a real click to the hidden player");
        action("Back 10 s", () -> YouTubePlayer.seek(-10), "Jump back 10 seconds");
        action("Forward 10 s", () -> YouTubePlayer.seek(10), "Jump forward 10 seconds");
        stepper("Volume", "%", YouTubePlayer::volume, YouTubePlayer::setVolume, 0, 100, 10,
                "Volume of the YouTube player");
        action("Stop", YouTubePlayer::stop, "Stop and close the hidden browser");
        note(YouTubePlayer.status());
        backButton();
    }
}
