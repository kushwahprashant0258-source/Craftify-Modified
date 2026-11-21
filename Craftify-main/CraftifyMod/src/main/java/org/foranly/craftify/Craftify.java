package org.foranly.craftify;

import net.fabricmc.api.ModInitializer;
import org.foranly.craftify.network.LyricsLinePayload;
import org.foranly.craftify.network.SpotifyTitlePayload;
import org.foranly.craftify.server.CraftifyServer;

/**
 * Common entrypoint. Runs on both physical sides:
 *
 * <ul>
 *   <li><b>Client:</b> registers the serverbound payloads the mod sends
 *       ({@code craftify:title}, {@code craftify:lyricsline}); everything else happens in
 *       {@link org.foranly.craftify.client.CraftifyClient}.</li>
 *   <li><b>Server</b> (dedicated Fabric server or an integrated/LAN world): also receives
 *     those payloads and renders them (nametag teams, below-name lyrics, holograms) — see
 *     {@link CraftifyServer}. The Spotify detection itself is client-only code.</li>
 * </ul>
 */
public class Craftify implements ModInitializer {

    @Override
    public void onInitialize() {
        // Registered once per JVM: the client needs them to send, a server running this
        // mod needs them to receive.
        SpotifyTitlePayload.register();
        LyricsLinePayload.register();
        // Receivers + rendering; inert until a server actually runs in this JVM.
        CraftifyServer.init();
    }
}
