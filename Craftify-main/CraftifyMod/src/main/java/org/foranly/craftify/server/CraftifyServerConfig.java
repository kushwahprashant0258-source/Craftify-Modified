package org.foranly.craftify.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side configuration ({@code config/craftify-server.json}), created with defaults
 * on first start. Mirrors the options of the Paper plugin's {@code config.yml}.
 *
 * <p>The nametag texts use <b>legacy formatting codes</b> ({@code §a} green, {@code §f}
 * white, ...) instead of MiniMessage: vanilla renders them fine above heads and there is
 * no MiniMessage on Fabric. Placeholders {@code {name}} and {@code {track}} work the same.
 */
public class CraftifyServerConfig {

    private static final String FILE_NAME = "craftify-server.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public NametagOptions nametag = new NametagOptions();
    public HologramOptions hologram = new HologramOptions();
    public LyricsDisplayOptions lyricsDisplay = new LyricsDisplayOptions();
    public LyricsHologramOptions lyricsHologram = new LyricsHologramOptions();

    /** Nametag mode (default): song next to the floating name via scoreboard teams. */
    public static class NametagOptions {
        public boolean enabled = true;
        public String prefix = "";
        public String suffix = " \u00a7a\u266a \u00a7f{track}";
    }

    /** Optional TextDisplay hologram above the player with icon + title. */
    public static class HologramOptions {
        public boolean enabled = false;
        public String icon = "\u266a ";
        public double height = 2.15;
        public double scale = 0.6;
    }

    /** How the shared lyric line (craftify:lyricsline) is rendered. */
    public static class LyricsDisplayOptions {
        /** "below-name" (default) or "hologram" (legacy TextDisplay). */
        public String mode = "below-name";
        /** BELOW_NAME number: "line" (the mod's line number), "random" or a fixed value. */
        public String number = "line";
    }

    /** Legacy lyrics hologram mode (used when lyricsDisplay.mode is "hologram"). */
    public static class LyricsHologramOptions {
        public boolean enabled = true;
        public double height = 1.85;
        public double scale = 0.6;
    }

    /** Whether the shared lyric line uses the legacy hologram mode. */
    public boolean lyricsUseHologram() {
        return "hologram".equalsIgnoreCase(lyricsDisplay.mode);
    }

    /** Loads the config from disk; missing fields keep their defaults. */
    public static CraftifyServerConfig load() {
        Path path = path();
        if (!Files.exists(path)) {
            return write(new CraftifyServerConfig());
        }
        try {
            String json = Files.readString(path);
            CraftifyServerConfig config = GSON.fromJson(json, CraftifyServerConfig.class);
            return config != null ? config : write(new CraftifyServerConfig());
        } catch (Exception e) {
            CraftifyServer.LOGGER.warn("Could not read {}: using defaults ({})",
                    FILE_NAME, e.toString());
            return new CraftifyServerConfig();
        }
    }

    /** Writes the config back to disk (creates it with defaults on first start). */
    private static CraftifyServerConfig write(CraftifyServerConfig config) {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config));
        } catch (IOException e) {
            CraftifyServer.LOGGER.warn("Could not save {}: {}", FILE_NAME, e.toString());
        }
        return config;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
