# BasaltRanks

Rank badge generator for Paper and Folia servers. It renders pixel-art badges and shows them in chat, above nametags and in the tab list, using a generated resource pack with bitmap fonts. Each badge is a Unicode character from the Private Use Area, so players see it as an image inside normal text.

Current version: 2.2.0. Full usage guide in [DOCUMENTATION.md](DOCUMENTATION.md).

## Features

- Five badge styles: Flat, 3D, Glass, Metallic, Neon
- 26 built-in templates (staff, ranks, special, fun)
- In-game GUI editor and a browser-based web editor
- Resource pack generated and served by the plugin itself
- Automatic badge assignment by LuckPerms group
- PlaceholderAPI placeholders (`%basaltranks_badge%`, `%basaltranks_text%` and others)
- Folia support

## Requirements

- Paper or a fork (`api-version` 1.20)
- Java 21
- Optional: LuckPerms, PlaceholderAPI

## Installation

1. Put the JAR in `plugins/` and start the server.
2. In `plugins/BasaltRanks/config.yml`, set `resourcepack.server-ip` to your public IP or domain. The default `localhost` only works on the same machine.
3. Run `/badge templates` to browse templates, or `/badge create <id> <template>` to make a badge.

The web editor starts by default and its default password is `change-me`. Change `web-editor.password` before exposing the port to the internet, or set `web-editor.auto-start: false`.

## Commands

| Command | Description |
|---------|-------------|
| `/badge list`, `/badge info <id>` | List badges and show details |
| `/badge create <id> [template]` | Create a badge |
| `/badge edit <id>` | Open the GUI editor |
| `/badge assign <player> <badge>` | Assign a badge to a player |
| `/badge web [on\|off\|password]` | Toggle the web editor |
| `/basaltranks reload` | Reload the configuration |

All admin commands need `basaltranks.admin`. See [DOCUMENTATION.md](DOCUMENTATION.md) for the full list, permissions and configuration reference.

## Building from source

Requires Java 21 and the included Gradle wrapper.

```bash
./gradlew clean shadowJar
```

Output: `build/libs/BasaltRanks-2.2.0.jar`

## Modrinth review

The JAR published on Modrinth is obfuscated with ProGuard 7.6.1 (renaming only, no bytecode optimization). The configuration is in `build.gradle.kts`. To reproduce it:

```bash
./gradlew obfuscate
```

Output: `build/obfuscated/BasaltRanks-2.2.0.jar`, plus the mapping file `build/obfuscated/proguard-mapping.txt`. Entry points (main class, event handlers, command executors, the PlaceholderAPI expansion and bStats) are kept unrenamed.

## License

All rights reserved. Free to use on any server, personal or commercial. Redistribution, decompilation and derivative works are not allowed. See [LICENSE](LICENSE).
