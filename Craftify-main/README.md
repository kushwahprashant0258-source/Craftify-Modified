# Craftify

A two-component system that shows **what song is playing** for each player on a Minecraft
server. The client retains Spotify detection and adds Windows media-player title detection
(including PotPlayer), plus opt-in timestamp-synchronized lyrics in normal chat:

| Component | Folder | What it does |
|------------|---------|----------|
| **CraftifyMod** | [`CraftifyMod/`](CraftifyMod/) | Minecraft mod (Fabric, dual-sided): **on the client** it detects Spotify on the player's OS, reads the song title and pause state, sends them to the server and shows synchronized lyrics on screen (LRCLib); **on the server** (Fabric servers with mods, or an integrated/LAN world) it receives those packets and renders them — nametag, lyrics below-name, holograms. |
| **CraftifyPlugin** | [`CraftifyPlugin/`](CraftifyPlugin/) | Paper server plugin: receives the title, stores it per player and displays it (nametag, commands). Use this on Paper servers; use the mod itself on Fabric servers. |

The **communication contract** between both — the exact packet format and how the plugin
must receive it — is defined in [`PROTOCOL.md`](PROTOCOL.md).

## How it works

```
┌──────────────────────────────┐   craftify:title (custom_payload)   ┌──────────────────────────────┐
│  Client: CraftifyMod         │ ─────────────────────────────────►  │  Server: CraftifyPlugin      │
│                              │                                     │                              │
│  Detects Spotify on the OS   │   {"state":"playing",               │  Stores the state per        │
│  Reads song + pause state    │    "track":"Song - Artist",         │  player (UUID)               │
│  Sends ONLY on changes       │    "timestamp": ...}                │  Nametag + /nowplaying       │
│  Lyrics overlay (LRCLib)     │   {"line":"..."} (opt-in share)    │  Lyrics below-name (opt-in)  │
│  F10 menu (9 positions)      │                                     │  (title hologram optional)   │
└──────────────────────────────┘                                     └──────────────────────────────┘
```

1. **The mod** runs on the client and is the only component with access to the player's
   operating system: it detects the Spotify process (Windows/macOS/Linux) and reads the
   current song and its **pause state** (playing / paused / closed). On Windows it reads
   the window title (it changes with each song, typically `Song - Artist`, and reverts to
   the account tier when paused); on macOS from MediaRemote via the bundled
   `nowplaying-cli` (no permission, Apple Silicon) with AppleScript as fallback; on Linux
   from MPRIS via `playerctl`.
2. **Only when the state changes** (new song, Spotify closed, etc.) it sends a
   `minecraft:custom_payload` packet on the `craftify:title` channel with a three-field
   JSON: `state` (`playing` / `paused` / `no_track` / `closed`), `track` and `timestamp`.
3. **The receiver** (the plugin on Paper, or the mod itself on a Fabric server) decodes the
   payload and keeps the latest state per player. Today it displays it in the player's
   **floating nametag** — via scoreboard teams (prefix + name + suffix, on a single line;
   vanilla ignores the custom name for players) — and via the `/nowplaying` command. A
   **hologram** (`TextDisplay`) remains available as an optional mode.
4. **The mod** also lets you see your own name in **third person (F5)** — vanilla hides
   it — so the player sees their nametag with the song.
5. **The mod** optionally shows **synchronized lyrics** (from LRCLib) on screen that
   advance with the song and freeze on pause — client-side only.
6. The player can **share the current line** with the server (F10 menu, opt-in): the mod
   sends it on `craftify:lyricsline` and the plugin renders it as a **hologram** above the
   player, so others can follow along.

The mod does **not** interpret the music and the receiver does **not** touch the operating
system: each component does one thing, and they communicate only through the protocol
defined in [`PROTOCOL.md`](PROTOCOL.md).

### Which jar goes on which server?

| Server type | What to install | Receiver |
|-------------|-----------------|----------|
| **Paper** (plugins) | `CraftifyPlugin` in `plugins/` | CraftifyPlugin |
| **Fabric** (mods) | `CraftifyMod` in `mods/` (same jar as the client) | The mod itself |
| Singleplayer / LAN | Nothing extra — the client mod handles it locally | Integrated server |

On Fabric servers the same dual-sided jar that reads Spotify on clients also implements
the receiving side: nametag via scoreboard teams, shared lyrics in the `BELOW_NAME` slot,
optional holograms, `/nowplaying` and `/craftifyserver reload|status`, configured in
`config/craftify-server.json`.

## Component documentation

- **CraftifyMod** ([`CraftifyMod/README.md`](CraftifyMod/README.md)) — the mod: detection
  per OS, requirements, commands (`/craftify spotify`, `/craftify send`, `/craftify
  lyrics`), lyrics overlay + sharing (LRCLib, F10 menu with position picker and search),
  polling and detection cost, build — plus its **server side** for Fabric servers
  (nametag teams, below-name lyrics, holograms, `/nowplaying`,
  `config/craftify-server.json`).
- **CraftifyPlugin** ([`CraftifyPlugin/README.md`](CraftifyPlugin/README.md)) — the plugin:
  what it does today (channel reception, per-player state, nametag via scoreboard teams,
  `/nowplaying`, `/craftifyplugin reload`), configuration and build.
- **PROTOCOL.md** ([`PROTOCOL.md`](PROTOCOL.md)) — the contract between both: exact on-wire
  byte format, the 4 possible states and how the plugin must receive/decode them (with
  Paper and Fabric examples).

## Current status

- [x] The mod detects Spotify (song + pause state), sends `craftify:title` on every state
  change, shows the player's own name in third person (F5), synced lyrics (LRCLib) and a
  general F10 menu (Spotify status, packet sending, lyrics options with 9 positions,
  lyrics search).
- [x] The Paper plugin receives the channels, stores the state per player and shows it in
  the player's nametag (hologram optional) plus the shared lyric line as a hologram.
- [x] The mod also works **on the server**: on a Fabric server (mods instead of plugins)
  the same jar receives the channels and renders the nametag (scoreboard teams), the
  shared lyric line (`BELOW_NAME` slot or hologram) and an optional title hologram —
  `/nowplaying`, `/craftifyserver reload|status` and `config/craftify-server.json`.
- [x] Opt-in client-side automatic chat lyrics (`/craftify lyrics chat on|off|status`).
- [x] Windows player-caption fallback including PotPlayer (best-effort; depends on the title bar exposing the track).

## Quick build

```bash
# Mod (client and Fabric server — the same jar)
cd CraftifyMod
./gradlew build        # -> CraftifyMod/build/libs/CraftifyMod-1.0.1.jar

# Plugin (server)
cd CraftifyPlugin
./gradlew build        # -> CraftifyPlugin/build/libs/CraftifyPlugin-1.0.1.jar
```

The mod jar goes into the client's `mods` folder (and/or the Fabric server's `mods`
folder); the plugin jar into the server's `plugins` folder (Paper). The 1.21.11 mod uses Java 21. A GitHub Actions workflow is included to build the mod online and upload the JAR artifact.

## Roadmap

1. [x] Detect the Spotify process, read the title and the pause state (`/craftify spotify`).
2. [x] Send `craftify:title` packets to the server when the title changes.
3. [x] Paper plugin that receives the channel and stores the state per player +
   nametag/hologram.
4. [x] Fabric server support: the mod itself receives the channels and renders the
   nametag, the shared lyrics and (optionally) holograms on servers with mods.
5. [ ] Conversion logic in the plugin (chat, scoreboard, Discord, Twitch, etc.).
