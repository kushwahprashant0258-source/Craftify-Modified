package org.foranly.craftify.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shows each sharing player's lyric line in the vanilla {@code BELOW_NAME} scoreboard
 * slot: the line rides inside that player's score entry as a custom number format
 * ({@link net.minecraft.network.chat.numbers.FixedFormat}) and the objective's display
 * name stays empty. Every viewer therefore sees <em>that</em> player's current line under
 * their nametag, so several players can share simultaneously without their lines fighting
 * over the single global label.
 *
 * <p>The hidden numeric value behind the format still follows the configured {@code
 * number} mode (the mod's line number, a random one or a fixed value) — vanilla only
 * renders an entry while a score exists. The line is part of the player's name tag (no
 * separate entity), so it follows the player perfectly with zero lag.
 */
public final class BelowNameLyricsManager {

    /** Vanilla limits objective names to 16 chars. */
    private static final String OBJECTIVE_NAME = "craftify_lyr";

    /** Number modes: -2 = line number from the mod, -1 = random, otherwise fixed. */
    private static final int MODE_LINE = -2;
    private static final int MODE_RANDOM = -1;

    /**
     * Score mode: {@code -2} = use the line number the mod sends, {@code -1} = random per
     * player, otherwise the fixed configured value.
     */
    private final int scoreMode;

    private final Map<UUID, String> entries = new HashMap<>();
    private Objective objective;

    public BelowNameLyricsManager(CraftifyServerConfig config) {
        String numberConfig = config.lyricsDisplay.number != null ? config.lyricsDisplay.number : "line";
        if ("random".equalsIgnoreCase(numberConfig)) {
            this.scoreMode = MODE_RANDOM;
        } else if ("line".equalsIgnoreCase(numberConfig)) {
            this.scoreMode = MODE_LINE;
        } else {
            int parsed;
            try {
                parsed = Integer.parseInt(numberConfig.trim());
            } catch (NumberFormatException e) {
                parsed = 0;
            }
            this.scoreMode = parsed;
        }
    }

    /**
     * Shows the lyric line under the player's name; an empty line clears it. The text
     * travels in the player's own score entry (custom number format), never in the shared
     * objective label.
     */
    public void update(ServerPlayer player, String line, int number) {
        Objective objective = getObjective();
        if (objective == null) {
            return;
        }
        UUID uuid = player.getUUID();
        if (line == null || line.isEmpty()) {
            clearScore(player);
            entries.remove(uuid);
            return;
        }
        // "line" mode: the hidden value changes with the lyric line (1, 2, 3...) sent.
        int value;
        if (scoreMode == MODE_LINE) {
            value = number > 0 ? number : 1;
        } else if (scoreMode == MODE_RANDOM) {
            value = ThreadLocalRandom.current().nextInt(0, 100);
        } else {
            value = scoreMode;
        }
        ScoreHolder holder = ScoreHolder.forNameOnly(player.getScoreboardName());
        ScoreAccess score = board().getOrCreatePlayerScore(holder, objective);
        score.set(value);
        score.numberFormatOverride(new FixedFormat(
                Component.literal(line).withStyle(ChatFormatting.WHITE)));
        entries.put(uuid, line);
    }

    /** Removes a player's entry/score (e.g. on disconnect). */
    public void remove(UUID player) {
        entries.remove(player);
        MinecraftServer server = CraftifyServer.currentServer();
        if (server == null || objective == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            clearScore(online);
        }
    }

    /** Unregisters the objective entirely (server stop / reload / mode switch). */
    public void shutdown() {
        entries.clear();
        if (objective != null) {
            Scoreboard board = objective.getScoreboard();
            if (board != null) {
                board.removeObjective(objective);
            }
            objective = null;
        }
    }

    private void clearScore(ServerPlayer player) {
        if (objective == null) {
            return;
        }
        Scoreboard board = objective.getScoreboard();
        if (board == null) {
            return;
        }
        ScoreHolder holder = ScoreHolder.forNameOnly(player.getScoreboardName());
        board.resetSinglePlayerScore(holder, objective);
    }

    /** Creates the objective lazily on the server scoreboard. */
    private Objective getObjective() {
        if (objective != null) {
            return objective;
        }
        Scoreboard board = board();
        Objective existing = board.getObjective(OBJECTIVE_NAME);
        if (existing != null) {
            objective = existing;
        } else {
            // Empty label: the visible text comes from each player's score format.
            objective = board.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
                    Component.empty(), ObjectiveCriteria.RenderType.INTEGER,
                    false, null);
        }
        board.setDisplayObjective(DisplaySlot.BELOW_NAME, objective);
        return objective;
    }

    private Scoreboard board() {
        MinecraftServer server = CraftifyServer.currentServer();
        if (server == null) {
            throw new IllegalStateException("No server running");
        }
        return server.getScoreboard();
    }
}
