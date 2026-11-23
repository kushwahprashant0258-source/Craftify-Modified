package org.foranly.craftify.client.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Desktop;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.foranly.craftify.client.config.CraftifyConfig;

/** Spotify Web API client. Spotify itself remains the playback device; Craftify supplies the in-game UI. */
public final class SpotifyWebApi {
    public static final int CALLBACK_PORT = 8888;
    public static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    private static final String API = "https://api.spotify.com/v1";
    private static final String TOKEN = "https://accounts.spotify.com/api/token";
    private static final String AUTHORIZE = "https://accounts.spotify.com/authorize";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String accessToken;
    private static volatile String refreshToken;
    private static volatile long expiresAt;
    private static volatile ServerSocket callbackServer;

    private SpotifyWebApi() {}

    public record Track(String name, String artist, String album, String uri, String imageUrl, int durationMs) {
        public String label() { return name + "  —  " + artist; }
    }
    public record Playback(Track track, boolean playing, int progressMs, int volumePercent) {}

    public static boolean isConnected() {
        loadSavedTokens();
        return accessToken != null && !accessToken.isBlank();
    }

    public static String clientId() { return CraftifyConfig.instance().getSpotifyClientId(); }

    public static CompletableFuture<Void> connect(String clientId) {
        if (clientId == null || clientId.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("Enter your Spotify Client ID first."));
        CraftifyConfig.instance().setSpotifyClientId(clientId.trim());
        return CompletableFuture.runAsync(() -> {
            try {
                disconnectCallback();
                String verifier = randomUrl(64);
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                String state = randomUrl(24);
                CompletableFuture<String> codeFuture = new CompletableFuture<>();
                ServerSocket server = new ServerSocket(CALLBACK_PORT, 1, java.net.InetAddress.getByName("127.0.0.1"));
                callbackServer = server;
                Thread callbackThread = new Thread(() -> {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(180_000);
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String requestLine = reader.readLine();
                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                        String target = requestLine != null && requestLine.split(" ").length > 1 ? requestLine.split(" ")[1] : "/";
                        String query = target.contains("?") ? target.substring(target.indexOf('?') + 1) : null;
                        String code = queryValue(query, "code");
                        String returnedState = queryValue(query, "state");
                        String error = queryValue(query, "error");
                        String page = error != null ? "Spotify authorization was cancelled. You can close this tab." : "Larper connected to Spotify. You can close this tab and return to Minecraft.";
                        byte[] bytes = ("<html><body style='font-family:sans-serif;background:#111;color:#fff;padding:3rem'><h2>" + page + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
                        java.io.OutputStream out = socket.getOutputStream();
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(bytes); out.flush();
                        if (error != null) codeFuture.completeExceptionally(new IOException("Spotify authorization denied: " + error));
                        else if (code == null || !state.equals(returnedState)) codeFuture.completeExceptionally(new IOException("Invalid Spotify callback state."));
                        else codeFuture.complete(code);
                    } catch (Exception e) { codeFuture.completeExceptionally(e); }
                }, "craftify-spotify-oauth");
                callbackThread.setDaemon(true);
                callbackThread.start();
                String scope = "user-read-playback-state user-read-currently-playing user-modify-playback-state";
                String url = AUTHORIZE + "?response_type=code&client_id=" + enc(clientId.trim())
                        + "&redirect_uri=" + enc(REDIRECT_URI) + "&scope=" + enc(scope)
                        + "&code_challenge_method=S256&code_challenge=" + enc(challenge) + "&state=" + enc(state);
                if (!Desktop.isDesktopSupported()) throw new IOException("This computer cannot open a browser automatically. Open this URL manually: " + url);
                Desktop.getDesktop().browse(URI.create(url));
                String code = codeFuture.orTimeout(180, java.util.concurrent.TimeUnit.SECONDS).join();
                exchangeCode(clientId.trim(), code, verifier);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(root(e));
            } finally {
                disconnectCallback();
            }
        });
    }

    public static synchronized void disconnect() {
        accessToken = null; refreshToken = null; expiresAt = 0;
        CraftifyConfig.instance().setSpotifyTokens("", "", 0);
    }

    private static synchronized void loadSavedTokens() {
        if (refreshToken != null || accessToken != null) return;
        CraftifyConfig c = CraftifyConfig.instance();
        accessToken = emptyToNull(c.getSpotifyAccessToken());
        refreshToken = emptyToNull(c.getSpotifyRefreshToken());
        expiresAt = c.getSpotifyTokenExpiresAt();
    }

    private static synchronized void exchangeCode(String clientId, String code, String verifier) throws Exception {
        String body = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier);
        JsonObject token = tokenRequest(body);
        saveToken(token);
    }

    private static synchronized void refreshIfNeeded() throws Exception {
        loadSavedTokens();
        if (accessToken != null && System.currentTimeMillis() < expiresAt - 60_000) return;
        if (refreshToken == null) throw new IOException("Connect your Spotify account first.");
        String body = "grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId());
        JsonObject token = tokenRequest(body);
        saveToken(token);
    }

    private static JsonObject tokenRequest(String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject json = parseObject(response.body());
        if (response.statusCode() / 100 != 2) throw new IOException("Spotify sign-in failed (HTTP " + response.statusCode() + "): " + errorMessage(json));
        return json;
    }

    private static synchronized void saveToken(JsonObject token) throws IOException {
        accessToken = string(token, "access_token");
        String newRefresh = string(token, "refresh_token");
        if (newRefresh != null) refreshToken = newRefresh;
        int seconds = token.has("expires_in") ? token.get("expires_in").getAsInt() : 3600;
        expiresAt = System.currentTimeMillis() + seconds * 1000L;
        CraftifyConfig.instance().setSpotifyTokens(accessToken, refreshToken == null ? "" : refreshToken, expiresAt);
    }

    public static CompletableFuture<List<Track>> search(String query) {
        return api("GET", "/search?q=" + enc(query) + "&type=track&limit=8", null).thenApply(json -> {
            List<Track> tracks = new ArrayList<>();
            JsonObject root = json.getAsJsonObject("tracks");
            if (root == null || !root.has("items")) return tracks;
            for (JsonElement item : root.getAsJsonArray("items")) {
                Track track = parseTrack(item.getAsJsonObject());
                if (track != null) tracks.add(track);
            }
            return tracks;
        });
    }

    public static CompletableFuture<Playback> currentPlayback() {
        return api("GET", "/me/player", null).thenApply(json -> {
            if (json == null || !json.has("item") || json.get("item").isJsonNull()) return null;
            Track track = parseTrack(json.getAsJsonObject("item"));
            int volume = 50;
            if (json.has("device") && json.get("device").isJsonObject()) {
                JsonObject device = json.getAsJsonObject("device");
                if (device.has("volume_percent") && !device.get("volume_percent").isJsonNull()) volume = device.get("volume_percent").getAsInt();
            }
            return new Playback(track, json.has("is_playing") && json.get("is_playing").getAsBoolean(),
                    json.has("progress_ms") && !json.get("progress_ms").isJsonNull() ? json.get("progress_ms").getAsInt() : 0, volume);
        });
    }

    public static CompletableFuture<Void> play(String uri) { return api("PUT", "/me/player/play", "{\"uris\":[\"" + jsonEscape(uri) + "\"]}").thenApply(x -> null); }
    public static CompletableFuture<Void> pause() { return api("PUT", "/me/player/pause", "{}").thenApply(x -> null); }
    public static CompletableFuture<Void> resume() { return api("PUT", "/me/player/play", "{}").thenApply(x -> null); }
    public static CompletableFuture<Void> next() { return api("POST", "/me/player/next", null).thenApply(x -> null); }
    public static CompletableFuture<Void> previous() { return api("POST", "/me/player/previous", null).thenApply(x -> null); }
    public static CompletableFuture<Void> setVolume(int percent) { return api("PUT", "/me/player/volume?volume_percent=" + Math.max(0, Math.min(100, percent)), null).thenApply(x -> null); }

    private static CompletableFuture<JsonObject> api(String method, String path, String body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                refreshIfNeeded();
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + accessToken).header("Content-Type", "application/json");
                if ("GET".equals(method)) builder.GET();
                else if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
                else builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 204 || response.body().isBlank()) return new JsonObject();
                JsonObject json = parseObject(response.body());
                if (response.statusCode() / 100 != 2) throw new IOException("Spotify API HTTP " + response.statusCode() + ": " + errorMessage(json));
                return json;
            } catch (Exception e) { throw new java.util.concurrent.CompletionException(root(e)); }
        });
    }

    private static Track parseTrack(JsonObject item) {
        if (item == null) return null;
        String name = string(item, "name");
        String uri = string(item, "uri");
        String albumName = ""; String image = "";
        if (item.has("album") && item.get("album").isJsonObject()) {
            JsonObject album = item.getAsJsonObject("album");
            albumName = value(string(album, "name"));
            JsonArray images = album.has("images") && album.get("images").isJsonArray() ? album.getAsJsonArray("images") : new JsonArray();
            if (!images.isEmpty()) image = value(string(images.get(0).getAsJsonObject(), "url"));
        }
        StringBuilder artists = new StringBuilder();
        if (item.has("artists") && item.get("artists").isJsonArray()) for (JsonElement a : item.getAsJsonArray("artists")) {
            if (artists.length() > 0) artists.append(", ");
            artists.append(value(string(a.getAsJsonObject(), "name")));
        }
        int duration = item.has("duration_ms") ? item.get("duration_ms").getAsInt() : 0;
        return name == null || uri == null ? null : new Track(name, artists.toString(), albumName, uri, image, duration);
    }

    private static String queryValue(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) { String[] pair = part.split("=", 2); if (pair.length == 2 && pair[0].equals(key)) return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8); }
        return null;
    }
    private static String randomUrl(int bytes) { byte[] b = new byte[bytes]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String jsonEscape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static JsonObject parseObject(String body) { try { JsonElement e = JsonParser.parseString(body); return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject(); } catch (RuntimeException e) { return new JsonObject(); } }
    private static String string(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null; }
    private static String value(String s) { return s == null ? "" : s; }
    private static String emptyToNull(String s) { return s == null || s.isBlank() ? null : s; }
    private static String errorMessage(JsonObject json) { if (json.has("error_description")) return json.get("error_description").getAsString(); if (json.has("error")) return json.get("error").toString(); return "request was rejected"; }
    private static Throwable root(Throwable t) { while (t.getCause() != null && (t instanceof java.util.concurrent.CompletionException || t instanceof java.util.concurrent.ExecutionException)) t = t.getCause(); return t; }
    private static synchronized void disconnectCallback() { if (callbackServer != null) { try { callbackServer.close(); } catch (IOException ignored) { } callbackServer = null; } }
}
