# Mirage

Mirage is a Minecraft plugin that protects your server against xray mods and packs.

![alt text](https://files.smoofyuniverse.net/images/mirage_screenshots.png)

## Server owners

Compiled artifacts and additional information can be found on Ore: https://ore.spongepowered.org/Yeregorix/Mirage.

## Developers

Mirage requires Java 21 to build.

Commands:

- `./gradlew shadowJar` constructs a jar that includes all its dependencies.
- `./gradlew setupVanillaServer` setups a vanilla server in directory `run/vanilla`.
- `./gradlew setupForgeServer` setups a Forge server in directory `run/forge`.

## Known issues

### Incompatibility with elytradev's mod

The Mirage plugin distributed on Ore and the elytradev's mod share the same identifier.
This causes a crash on server startup with the following error
message: `java.lang.NoClassDefFoundError: com/elytradev/mirage/event/GatherLightsEvent`.

To fix this, please use the Mirage plugin jar hosted at https://files.smoofyuniverse.net/smoofymirage/.

Mirage version 1.5.0 and above should not be affected by this issue, because elytradev's mod has not been updated past
Minecraft 1.12.2.
