package org.foranly.craftify.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.foranly.craftify.client.command.SpotifyCommand;
import org.foranly.craftify.client.gui.CraftifyMenuScreen;
import org.foranly.craftify.client.lyrics.LyricsManager;
import org.foranly.craftify.client.spotify.SpotifyTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftifyClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftify");

    /** Controls category shown in Options > Controls (translation: key.category.craftify.main). */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("craftify", "main"));

    /** F10 opens the general Craftify menu. */
    private static final KeyMapping LYRICS_KEY = new KeyMapping(
            "key.craftify.lyrics", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_F10, CATEGORY);

    @Override
    public void onInitializeClient() {
        // The payloads are registered in the common entrypoint (Craftify#onInitialize),
        // so the same registration also serves a dedicated server running this mod.
        // Registers the lyrics HUD element (LRCLib overlay, toggle: F10 / /larper lyrics).
        LyricsManager.instance();
        // F10 opens the general Craftify menu (in-game only).
        KeyBindingHelper.registerKeyBinding(LYRICS_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (LYRICS_KEY.consumeClick() && client.player != null && client.screen == null) {
                client.setScreen(new CraftifyMenuScreen());
            }
        });
        // Helps verifying in the game log which jar version was loaded.
        LOGGER.info("Craftify client initialized (mixins: LivingEntityRendererMixin, lyrics overlay, F10 menu)");

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> SpotifyCommand.register(dispatcher));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SpotifyTracker.start());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpotifyTracker.stop());
    }
}
