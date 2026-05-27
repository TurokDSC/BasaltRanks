# BasaltRanks - Source Code (Modrinth Review)

This repository contains the full source code for **BasaltRanks**, shared privately for Modrinth Content Moderation review.

## Build Instructions

### Requirements
- Java 21+
- Gradle 8.11 (included via wrapper)

### Build (unobfuscated)

```bash
./gradlew clean shadowJar
```

Output: `build/libs/BasaltRanks-2.2.0.jar`

### Build (obfuscated - as submitted to Modrinth)

```bash
./gradlew obfuscate
```

Output: `build/obfuscated/BasaltRanks-2.2.0.jar`

The obfuscated JAR is the file uploaded to Modrinth.

## Obfuscation Process

We use **ProGuard 7.6.1** via the `com.guardsquare:proguard-gradle` plugin. The configuration is in `build.gradle.kts` (lines 73-120).

**What ProGuard does:**
- Renames internal classes/methods to short names (`repackageclasses`)
- Keeps all public entry points intact (main class, event handlers, command executors, PlaceholderAPI expansion, bStats)
- Does NOT optimize bytecode (`dontoptimize`) - only renames
- Preserves `plugin.yml` references (`adaptresourcefilecontents`)

**What is kept (not obfuscated):**
- `BasaltRanks` main class and companion
- `Module` interface
- All `@EventHandler` methods
- All `CommandExecutor` and `TabCompleter` implementations
- `BasaltRanksExpansion` (PlaceholderAPI)
- bStats classes

A full ProGuard mapping file is generated at `build/obfuscated/proguard-mapping.txt` after each obfuscated build.

## Dependencies

- **Paper API 1.21.4** (compileOnly)
- **LuckPerms API 5.4** (compileOnly, optional)
- **PlaceholderAPI 2.11.6** (compileOnly, optional)
- **Kotlin stdlib** (shaded + relocated)
- **bStats 3.2.1** (shaded + relocated)

## License

See [LICENSE](LICENSE) for details.
