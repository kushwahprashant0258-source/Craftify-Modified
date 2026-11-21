package org.foranly.craftify.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the song title in the player's floating nametag using <b>scoreboard teams</b>
 * (prefix/suffix) — the same approach as the Paper plugin's NametagManager: in vanilla,
 * {@code getDisplayName()} renders {@code teamPrefix + name + teamSuffix} and ignores the
 * custom name for players, so the song is appended as a team suffix. The label is part of
 * the entity, so it follows the player without lag.
 *
 * <p>Server-side this is just the vanilla scoreboard API: every change (team created,
 * prefix/suffix edited, member added) is broadcast automatically to all tracking clients.
 * The tab list is untouched (the team only carries an empty prefix/suffix while idle).
 *
 * <p>As on Paper, the owner of the name only sees it in third person (F5) if their client
 * runs the mod's "see your own name" mixin.
 */
public final class NametagManager {

    private static final String DEFAULT_PREFIX = "";
    private static final String DEFAULT_SUFFIX = " \u00a7a\u266a \u00a7f{track}";
    /** Short prefix for our team names (vanilla limit is 16 chars). */
    private static final String TEAM_NAME_PREFIX = "cft-";

    private final boolean enabled;
    private final String prefixFormat;
    private final String suffixFormat;

    /** Team name per player, for cleanup on quit/reload/shutdown. */
    private final Map<UUID, String> teamNames = new HashMap<>();
    private int teamCounter;

    public NametagManager(CraftifyServerConfig config) {
        this.enabled = config.nametag.enabled;
        this.prefixFormat = config.nametag.prefix != null ? config.nametag.prefix : DEFAULT_PREFIX;
        this.suffixFormat = config.nametag.suffix != null ? config.nametag.suffix : DEFAULT_SUFFIX;
    }

    /** Whether the nametag mode is enabled in the config. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Updates the player's nametag according to their Spotify state: with {@code playing}
     * and a title, sets the team prefix/suffix (name + song); in any other state clears them.
     */
    public void update(ServerPlayer player, PlayerSpotifyState state) {
        if (!enabled) {
            return;
        }
        if (state.isPlaying() && !state.track().isEmpty()) {
            PlayerTeam team = teamFor(player);
            team.setPlayerPrefix(Component.literal(render(prefixFormat, player, state)));
            team.setPlayerSuffix(Component.literal(render(suffixFormat, player, state)));
            CraftifyServer.LOGGER.debug("Nametag applied for {}: {}", player.getScoreboardName(),
                    state.track());
        } else {
            clear(player);
        }
    }

    /** Restores the player's nametag to their normal name. */
    public void reset(ServerPlayer player) {
        clear(player);
    }

    /** Unregisters the player's team (e.g. on disconnect). */
    public void remove(UUID player) {
        String teamName = teamNames.remove(player);
        if (teamName == null) {
            return;
        }
        Scoreboard board = board();
        PlayerTeam team = board.getPlayerTeam(teamName);
        if (team != null) {
            board.removePlayerTeam(team);
        }
    }

    /** Unregisters every team this manager created (server stop / reload). */
    public void shutdown() {
        Scoreboard board = board();
        teamNames.values().forEach(name -> {
            PlayerTeam team = board.getPlayerTeam(name);
            if (team != null) {
                board.removePlayerTeam(team);
            }
        });
        teamNames.clear();
    }

    private void clear(ServerPlayer player) {
        String teamName = teamNames.get(player.getUUID());
        if (teamName != null) {
            PlayerTeam team = board().getPlayerTeam(teamName);
            if (team != null) {
                team.setPlayerPrefix(Component.empty());
                team.setPlayerSuffix(Component.empty());
            }
        }
    }

    /** Gets (or creates) the player's team, with the player's name as an entry. */
    private PlayerTeam teamFor(ServerPlayer player) {
        Scoreboard board = board();
        String teamName = teamNames.computeIfAbsent(player.getUUID(), uuid -> {
            String name;
            do {
                name = TEAM_NAME_PREFIX + (teamCounter++);
            } while (board.getPlayerTeam(name) != null);
            return name;
        });
        PlayerTeam team = board.getPlayerTeam(teamName);
        if (team == null) {
            team = board.addPlayerTeam(teamName);
        }
        // addPlayerToTeam also removes the entry from any previous team.
        board.addPlayerToTeam(player.getScoreboardName(), team);
        return team;
    }

    /**
     * Applies the placeholders ({name}/{track}) to a format string. The interpolated values
     * are sanitized: a '§' inside a track or player name could otherwise inject formatting.
     */
    private static String render(String format, ServerPlayer player, PlayerSpotifyState state) {
        return format
                .replace("{track}", sanitize(state.track()))
                .replace("{name}", sanitize(player.getScoreboardName()));
    }

    private static String sanitize(String value) {
        return value.replace('\u00a7', ' ').replace('\u0000', ' ');
    }

    private Scoreboard board() {
        net.minecraft.server.MinecraftServer server = CraftifyServer.currentServer();
        if (server == null) {
            throw new IllegalStateException("No server running");
        }
        return server.getScoreboard();
    }
}
