package org.foranly.craftify.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.foranly.craftify.network.LyricsLinePayload;
import org.foranly.craftify.network.SpotifyTitlePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * The server side of the mod: when CraftifyMod is installed on a Fabric server (or runs on
 * an integrated/LAN world), it receives {@code craftify:title} and
 * {@code craftify:lyricsline} from clients and renders them like the Paper plugin does:
 *
 * <ul>
 *   <li><b>Nametag</b> (default): song next to the floating name via scoreboard teams.</li>
 *   <li><b>Lyrics below-name</b> (default): shared lyric line in the vanilla BELOW_NAME slot.</li>
 *   <li><b>Holograms</b> (optional/legacy): TextDisplay entities above the player.</li>
 * </ul>
 *
 * <p>The rendering lives on the server, so every vanilla client sees it; only reading
 * Spotify needs the client mod. Configured in {@code config/craftify-server.json}.
 */
public final class CraftifyServer {

    public static final Logger LOGGER = LoggerFactory.getLogger("craftify");

    private static volatile MinecraftServer currentServer;
    private static boolean initialized;

    private SpotifyStateStore store;
    private NametagManager nametags;
    private BelowNameLyricsManager belowNameLyrics;
    private HologramManager titleHolograms;
    private HologramManager lyricsHolograms;
    private CraftifyServerConfig config;

    private CraftifyServer() {
    }

    /**
     * Sets up everything that must exist once per JVM (the common entrypoint calls this on
     * both physical sides). Receivers and events are inert until a server actually runs.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        new CraftifyServer().register();
    }

    /** Current running server, for the managers' scoreboard access. */
    static MinecraftServer currentServer() {
        return currentServer;
    }

    private void register() {
        // Client → server payloads (PROTOCOL.md §2/§6). Handlers run on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(SpotifyTitlePayload.TYPE,
                (payload, context) -> handleTitle(context.player(), payload.json()));
        ServerPlayNetworking.registerGlobalReceiver(LyricsLinePayload.TYPE,
                (payload, context) -> handleLyricsLine(context.player(), payload.json()));

        // Lifecycle: build displays when a server starts, clean up before the world save.
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(server ->
                tickHolograms(server));

        // Clear state + display when a player disconnects (PROTOCOL.md §4.1).
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                cleanup(handler.getPlayer()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommands(dispatcher));

        LOGGER.info("Craftify server support registered (receives craftify:title / craftify:lyricsline)");
    }

    private void onServerStarting(MinecraftServer server) {
        currentServer = server;
        config = CraftifyServerConfig.load();
        store = new SpotifyStateStore();
        nametags = new NametagManager(config);
        belowNameLyrics = config.lyricsUseHologram() ? null : new BelowNameLyricsManager(config);
        titleHolograms = new HologramManager(config.hologram.enabled, config.hologram.height,
                (float) config.hologram.scale);
        lyricsHolograms = new HologramManager(config.lyricsUseHologram() && config.lyricsHologram.enabled,
                config.lyricsHologram.height, (float) config.lyricsHologram.scale);

        LOGGER.info("==================================================");
        LOGGER.info("Craftify server ready");
        LOGGER.info("  Channels: {} / {} (client → server)", SpotifyTitlePayload.CHANNEL,
                LyricsLinePayload.CHANNEL);
        LOGGER.info("  Nametag: {}", nametags.isEnabled() ? "ENABLED" : "disabled");
        LOGGER.info("  Title hologram: {}", titleHolograms.isEnabled() ? "ENABLED" : "disabled");
        LOGGER.info("  Lyrics display: {}, number: {}", config.lyricsDisplay.mode,
                config.lyricsDisplay.number);
        LOGGER.info("  Commands: /nowplaying, /craftifyserver reload|status");
        LOGGER.info("  Config: config/craftify-server.json");
        LOGGER.info("==================================================");
    }

    private void onServerStopping(MinecraftServer server) {
        // Runs before the world save, so our teams/objective/holograms are not persisted.
        shutdownDisplays();
        currentServer = null;
    }

    private void shutdownDisplays() {
        if (nametags != null) {
            nametags.shutdown();
        }
        if (belowNameLyrics != null) {
            belowNameLyrics.shutdown();
        }
        if (titleHolograms != null) {
            titleHolograms.shutdown();
        }
        if (lyricsHolograms != null) {
            lyricsHolograms.shutdown();
        }
    }

    private void tickHolograms(MinecraftServer server) {
        if (titleHolograms != null) {
            titleHolograms.tick(server);
        }
        if (lyricsHolograms != null) {
            lyricsHolograms.tick(server);
        }
    }

    // ------------------------------------------------------------------ packets

    /**
     * Stores the new state and refreshes the player's displays. Stale packets (older
     * timestamp than the stored one) are ignored (PROTOCOL.md §4.2).
     */
    private void handleTitle(ServerPlayer player, String json) {
        PlayerSpotifyState state = PlayerSpotifyState.fromJson(json);
        if (state == null) {
            LOGGER.warn("Invalid craftify:title JSON from {}", player.getScoreboardName());
            return;
        }
        UUID uuid = player.getUUID();
        PlayerSpotifyState previous = store.get(uuid).orElse(null);
        if (previous != null && state.timestamp() > 0 && state.timestamp() < previous.timestamp()) {
            return;
        }
        store.update(uuid, state);
        LOGGER.debug("{} → {}", player.getScoreboardName(), state);

        nametags.update(player, state);
        if (titleHolograms.isEnabled()) {
            Component text = null;
            if (state.isPlaying() && !state.track().isEmpty()) {
                String icon = config.hologram.icon == null ? "\u266a " : config.hologram.icon;
                text = Component.literal(icon)
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(state.track()).withStyle(ChatFormatting.WHITE));
            }
            titleHolograms.setText(player, text);
        }
    }

    /** Renders the shared lyric line in the configured display mode. */
    private void handleLyricsLine(ServerPlayer player, String json) {
        String line = "";
        int number = -1;
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            line = object.has("line") ? object.get("line").getAsString() : "";
            number = object.has("number") && object.get("number").isJsonPrimitive()
                    ? object.get("number").getAsInt() : -1;
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid craftify:lyricsline JSON from {}: {}",
                    player.getScoreboardName(), e.getMessage());
            return;
        }
        if (config.lyricsUseHologram()) {
            Component text = line.isEmpty() ? null
                    : Component.literal(line).withStyle(ChatFormatting.WHITE);
            lyricsHolograms.setText(player, text);
        } else if (belowNameLyrics != null) {
            belowNameLyrics.update(player, line, number);
        }
    }

    /** Clears everything about a disconnecting player (PROTOCOL.md §4.1). */
    private void cleanup(ServerPlayer player) {
        UUID uuid = player.getUUID();
        store.remove(uuid);
        nametags.remove(uuid);
        if (belowNameLyrics != null) {
            belowNameLyrics.remove(uuid);
        }
        titleHolograms.remove(uuid);
        lyricsHolograms.remove(uuid);
    }

    // ----------------------------------------------------------------- commands

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nowplaying").executes(ctx -> {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ctx.getSource().sendSuccess(() -> describe(store.get(sender.getUUID()).orElse(null)),
                    false);
            return Command.SINGLE_SUCCESS;
        }));

        dispatcher.register(Commands.literal("craftifyserver")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Larper🥀 server: nametag=" + (nametags.isEnabled() ? "on" : "off")
                                    + ", hologram=" + (titleHolograms.isEnabled() ? "on" : "off")
                                    + ", lyrics-display=" + config.lyricsDisplay.mode
                                    + ", players with known state: " + store.size()), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    reload();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Larper🥀 server reloaded (craftify-server.json)."), true);
                    return Command.SINGLE_SUCCESS;
                })));
    }

    /** One line describing a state, mirroring the Paper plugin's /nowplaying colors. */
    private static Component describe(PlayerSpotifyState state) {
        if (state == null) {
            return Component.literal("No Spotify data yet. Do you have the Larper🥀 mod installed?")
                    .withStyle(ChatFormatting.GRAY);
        }
        return switch (state.state()) {
            case PlayerSpotifyState.STATE_PLAYING -> Component.literal("Listening to: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(state.track()).withStyle(ChatFormatting.WHITE));
            case PlayerSpotifyState.STATE_PAUSED -> Component.literal("Spotify paused.")
                    .withStyle(ChatFormatting.YELLOW);
            case PlayerSpotifyState.STATE_NO_TRACK -> Component.literal("Spotify open, no active song.")
                    .withStyle(ChatFormatting.YELLOW);
            case PlayerSpotifyState.STATE_CLOSED -> Component.literal("Spotify closed.")
                    .withStyle(ChatFormatting.RED);
            default -> Component.literal("Unknown state: " + state.state())
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    /** Reloads the config and re-applies the stored states to all online players. */
    private void reload() {
        MinecraftServer server = currentServer;
        if (server == null || store == null) {
            return;
        }
        shutdownDisplays();

        config = CraftifyServerConfig.load();
        nametags = new NametagManager(config);
        belowNameLyrics = config.lyricsUseHologram() ? null : new BelowNameLyricsManager(config);
        titleHolograms = new HologramManager(config.hologram.enabled, config.hologram.height,
                (float) config.hologram.scale);
        lyricsHolograms = new HologramManager(config.lyricsUseHologram() && config.lyricsHologram.enabled,
                config.lyricsHologram.height, (float) config.lyricsHologram.scale);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            store.get(player.getUUID()).ifPresent(state -> nametags.update(player, state));
        }
        LOGGER.info("Craftify server reloaded (craftify-server.json re-read, states re-applied).");
    }
}
