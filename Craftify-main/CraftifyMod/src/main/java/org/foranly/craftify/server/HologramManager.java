package org.foranly.craftify.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Floating text above the player (a {@link CraftifyHologram} entity that is repositioned
 * every tick), used for both hologram modes of the plugin: the optional title hologram and
 * the legacy shared-lyrics hologram. Visible to all nearby players; a small delay can be
 * noticed while moving, exactly like on Paper.
 *
 * <p>The caller only sets a text (or null to remove); this class handles spawning,
 * following and cleanup.
 */
public final class HologramManager {

    private final boolean enabled;
    private final double height;
    private final float scale;

    private final Map<UUID, CraftifyHologram> displays = new HashMap<>();

    public HologramManager(boolean enabled, double height, float scale) {
        this.enabled = enabled;
        this.height = height;
        this.scale = scale;
    }

    /** Whether this hologram mode is enabled in the config. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Shows {@code text} above the player, or removes the hologram when the text is
     * {@code null}/empty.
     */
    public void setText(ServerPlayer player, Component text) {
        if (!enabled || text == null || text.getString().isEmpty()) {
            remove(player.getUUID());
            return;
        }
        ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        if (level == null) {
            return;
        }
        CraftifyHologram display = displays.computeIfAbsent(player.getUUID(), uuid ->
                spawn(level, player.position()));
        if (display == null) {
            return;
        }
        if (display.isRemoved()) {
            displays.remove(player.getUUID());
            display = spawn(level, player.position());
            if (display == null) {
                return;
            }
        }
        // Crossing dimensions: recreate in the player's current world.
        if (display.level() != level) {
            display.discard();
            displays.remove(player.getUUID());
            display = spawn(level, player.position());
        }
        display.setText(text);
    }

    /** Removes a player's hologram (e.g. on disconnect). */
    public void remove(UUID player) {
        CraftifyHologram display = displays.remove(player);
        if (display != null && !display.isRemoved()) {
            display.discard();
        }
    }

    /** Removes every hologram (server stop / reload). */
    public void shutdown() {
        new HashMap<>(displays).keySet().forEach(this::remove);
    }

    /** Keeps each hologram above its player; runs once per server tick. */
    void tick(MinecraftServer server) {
        if (displays.isEmpty()) {
            return;
        }
        displays.forEach((uuid, display) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || display.isRemoved()) {
                remove(uuid);
                return;
            }
            Vec3 pos = player.position();
            display.setPos(pos.x, pos.y + height, pos.z);
        });
    }

    private CraftifyHologram spawn(ServerLevel level, Vec3 position) {
        try {
            CraftifyHologram display = new CraftifyHologram(level);
            display.setPos(position.x, position.y + height, position.z);
            level.addFreshEntity(display);
            display.configure(Component.empty(), scale);
            return display;
        } catch (Exception e) {
            CraftifyServer.LOGGER.warn("Could not spawn a Craftify hologram: {}", e.toString());
            return null;
        }
    }
}
