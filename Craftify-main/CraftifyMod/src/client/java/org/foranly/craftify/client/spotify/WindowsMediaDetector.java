package org.foranly.craftify.client.spotify;

import java.util.Set;

/**
 * Best-effort Windows player detection based on the active application's window title.
 * PotPlayer is explicitly supported, alongside VLC, AIMP, MusicBee and foobar2000.
 * Spotify is intentionally excluded here because it keeps its dedicated detector.
 *
 * <p>Window-title detection is not a universal media-session API: some apps/browsers hide
 * the track title, and pause state is only reliable when the app marks it in its title.
 */
public final class WindowsMediaDetector {
    private static final Set<String> SUPPORTED = Set.of(
            "PotPlayerMini64.exe", "PotPlayerMini.exe", "vlc.exe", "AIMP.exe",
            "MusicBee.exe", "foobar2000.exe"
    );

    private WindowsMediaDetector() {}

    public static SpotifyProcess.Snapshot read() {
        return WindowsSpotify.readForExecutables(SUPPORTED, false);
    }
}
