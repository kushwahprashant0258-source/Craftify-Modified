package org.foranly.craftify.client.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.foranly.craftify.client.spotify.SpotifyWebApi;
import org.foranly.craftify.client.spotify.SpotifyWebApi.Playback;
import org.foranly.craftify.client.spotify.SpotifyWebApi.Track;

/** In-game Spotify search and playback interface. Audio is rendered by a Spotify Connect device. */
public final class SpotifyScreen extends Screen {
    private final Screen parent;
    private EditBox clientIdBox;
    private EditBox searchBox;
    private String clientId;
    private String query = "";
    private String status = "Connect your Spotify account to begin.";
    private String nowPlaying = "Nothing playing";
    private String nowArtist = "";
    private String album = "";
    private boolean connected;
    private boolean busy;
    private boolean playing;
    private int volume = 50;
    private List<Track> results = List.of();

    public SpotifyScreen(Screen parent) {
        super(Component.literal("Larper🥀 - Spotify"));
        this.parent = parent;
        this.clientId = SpotifyWebApi.clientId();
        this.connected = SpotifyWebApi.isConnected();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = Math.max(18, height / 2 - 118);

        clientIdBox = new EditBox(font, cx - 150, top, 300, 20, Component.literal("Spotify Client ID"));
        clientIdBox.setMaxLength(100);
        clientIdBox.setValue(clientId == null ? "" : clientId);
        clientIdBox.setHint(Component.literal("Paste your Spotify Client ID"));
        addRenderableWidget(clientIdBox);

        addRenderableWidget(Button.builder(Component.literal(busy ? "Connecting..." : connected ? "Reconnect Spotify" : "Connect Spotify"), b -> connect())
                .bounds(cx - 150, top + 24, 146, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh player"), b -> refreshPlayback())
                .bounds(cx + 4, top + 24, 146, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Search tracks"), b -> search())
                .bounds(cx + 88, top + 57, 62, 20).build());
        searchBox = new EditBox(font, cx - 150, top + 57, 230, 20, Component.literal("Search Spotify"));
        searchBox.setMaxLength(120);
        searchBox.setValue(query);
        searchBox.setHint(Component.literal("Song, artist or album"));
        searchBox.setResponder(v -> query = v);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Vol −"), b -> changeVolume(-10))
                .bounds(cx - 150, top + 86, 42, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀◀"), b -> command(SpotifyWebApi.previous(), "Previous track"))
                .bounds(cx - 105, top + 86, 55, 20).build());
        addRenderableWidget(Button.builder(Component.literal(playing ? "Pause" : "Play"), b -> {
                    if (playing) command(SpotifyWebApi.pause(), "Paused");
                    else command(SpotifyWebApi.resume(), "Resumed playback");
                }).bounds(cx - 45, top + 86, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶▶"), b -> command(SpotifyWebApi.next(), "Skipped to next track"))
                .bounds(cx + 50, top + 86, 55, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Vol +"), b -> changeVolume(10))
                .bounds(cx + 108, top + 86, 42, 20).build());

        int resultY = top + 165;
        if (results.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("Search results will appear here"), b -> {})
                    .bounds(cx - 150, resultY, 300, 20).build());
            resultY += 23;
        } else {
            int count = Math.min(6, results.size());
            for (int i = 0; i < count; i++) {
                Track track = results.get(i);
                String label = (i + 1) + ". " + track.label();
                if (label.length() > 48) label = label.substring(0, 45) + "...";
                addRenderableWidget(Button.builder(Component.literal(label), b -> play(track))
                        .bounds(cx - 150, resultY + i * 22, 300, 20).build());
            }
            resultY += count * 22;
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(cx - 150, Math.min(height - 28, resultY + 8), 300, 20).build());

        if (connected) refreshPlayback();
    }

    private void connect() {
        String id = clientIdBox.getValue().trim();
        if (id.isEmpty()) { status = "Enter your Spotify Client ID first."; return; }
        clientId = id; busy = true; status = "Opening Spotify authorization in your browser..."; rebuildWidgets();
        SpotifyWebApi.connect(id).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            if (error != null) status = "Connect failed: " + rootMessage(error);
            else { connected = true; status = "Spotify connected."; refreshPlayback(); }
            rebuildWidgets();
        }));
    }

    private void search() {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) { status = "Type a track or artist to search."; return; }
        if (!SpotifyWebApi.isConnected()) { status = "Connect Spotify first."; return; }
        busy = true; status = "Searching Spotify..."; results = List.of(); rebuildWidgets();
        SpotifyWebApi.search(q).whenComplete((tracks, error) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            if (error != null) status = "Search failed: " + rootMessage(error);
            else { results = tracks; status = tracks.isEmpty() ? "No tracks found." : "Found " + tracks.size() + " tracks. Select one to play."; }
            rebuildWidgets();
        }));
    }

    private void play(Track track) {
        status = "Sending track to Spotify...";
        command(SpotifyWebApi.play(track.uri()), "Playing: " + track.label());
    }

    private void refreshPlayback() {
        if (!SpotifyWebApi.isConnected()) { status = "Connect Spotify first."; return; }
        SpotifyWebApi.currentPlayback().whenComplete((state, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) status = "Player unavailable: " + rootMessage(error);
            else if (state == null || state.track() == null) { nowPlaying = "Nothing playing"; nowArtist = ""; album = ""; playing = false; status = "No active Spotify device or track."; }
            else {
                Playback p = state; nowPlaying = p.track().name(); nowArtist = p.track().artist(); album = p.track().album(); playing = p.playing(); volume = p.volumePercent();
                status = playing ? "Spotify is playing." : "Spotify is paused.";
            }
            rebuildWidgets();
        }));
    }

    private void changeVolume(int delta) {
        int nextVolume = Math.max(0, Math.min(100, volume + delta));
        command(SpotifyWebApi.setVolume(nextVolume), "Volume: " + nextVolume + "%");
    }

    private void command(java.util.concurrent.CompletableFuture<Void> action, String success) {
        if (!SpotifyWebApi.isConnected()) { status = "Connect Spotify first."; return; }
        action.whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            status = error == null ? success : "Spotify command failed: " + rootMessage(error);
            refreshPlayback();
            rebuildWidgets();
        }));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int cx = width / 2;
        int top = Math.max(18, height / 2 - 118);
        graphics.drawCenteredString(font, "LARPER🥀  /  SPOTIFY", cx, top - 14, 0x1ED760);
        graphics.drawString(font, "NOW PLAYING", cx - 150, top + 113, 0x1ED760);
        graphics.drawString(font, trim(nowPlaying, 42), cx - 150, top + 126, 0xFFFFFF);
        graphics.drawString(font, trim(nowArtist + (album.isBlank() ? "" : "  •  " + album), 52), cx - 150, top + 139, 0xAAAAAA);
        graphics.drawString(font, "Volume: " + volume + "%", cx + 72, top + 113, 0xAAAAAA);
        graphics.drawString(font, trim(status, 75), cx - 150, height - 13, 0xBBBBBB);
    }

    private static String trim(String s, int max) { return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 3) + "..."; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && searchBox != null && searchBox.isFocused()) { search(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
