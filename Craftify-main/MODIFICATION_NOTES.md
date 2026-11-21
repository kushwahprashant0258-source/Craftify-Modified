# Craftify 1.21.11 modification notes

This source package is based on the supplied Craftify project and keeps its existing GPL-3.0 license.

## Included changes

- Targets Minecraft 1.21.11, Fabric, Java 21, official Mojang mappings, Fabric API 0.141.6+1.21.11.
- Keeps the original Spotify-specific detector.
- Adds a Windows fallback detector for PotPlayer (`PotPlayerMini64.exe` / `PotPlayerMini.exe`), VLC, AIMP, MusicBee and foobar2000. It reads app window captions, so the player must expose the current track in its title. This is not a universal Windows SMTC reader.
- Adds opt-in normal-chat lyric sending based on the already-fetched LRCLib timestamps. It is disabled by default. Commands: `/craftify lyrics chat on`, `/craftify lyrics chat off`, `/craftify lyrics chat status`.
- Adds a GitHub Actions workflow at `.github/workflows/build-mod.yml` to build the Fabric mod online and upload the resulting JAR artifact.

## Build

The intended build target is the `CraftifyMod` project. Use JDK 21 and run `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS.

For the website-based route: create a GitHub repository, upload/push the contents of this ZIP, open the **Actions** tab, select **Build Craftify Fabric Mod**, and click **Run workflow**. When it finishes, open the run and download the `Craftify-1.21.11-Fabric` artifact.

## Verification status

The source edits and workflow are included, but this package has not been compiled or launched against Minecraft in this environment: Gradle could not download its distribution because network access to `services.gradle.org` was unavailable. The GitHub Actions build is therefore the first full compile check. If it reports an error, the log will identify any remaining 1.21.11 API migration work.
