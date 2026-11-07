# Contributing to PyFish

Thanks for taking the time to contribute.

## Before You Start

PyFish currently targets:

- Java 25 for all Minecraft profiles
- Fabric and NeoForge
- Minecraft version profiles managed through `gradle/version-profiles.json`

If you want to work on gameplay or loader behavior, test against the profile you
actually changed.

## Repository Layout

- `common/`: shared Gradle module, tests, and common packaging
- `fabric/`: Fabric entrypoints, metadata, packaging, and loader-only sources
- `neoforge/`: NeoForge entrypoints, metadata, packaging, and loader-only sources
- `src/main/java/fr/oesphy/pyfish/`: shared runtime and API code
- `src/main/resources/`: bundled Python bridge modules and shared assets
- `examples/`: self-contained Python examples and asset packs

## Local Setup

Recommended first commands on Windows:

```text
.\gradlew.bat listVersionProfiles
.\gradlew.bat quickCheck
```

On Linux or macOS:

```text
./gradlew listVersionProfiles
./gradlew quickCheck
```

`quickCheck` runs shared tests and launches each selected loader's packaged
Python runtime in a clean JVM, checking both compilation and packaging.

Useful commands:

- `.\gradlew.bat quickCheck`: fast verification for the default profile
- `.\gradlew.bat -PpyfishVersionProfile=mc1211 quickCheck`: fast verification for a specific profile
- `.\gradlew.bat build`: full build for the selected profile
- `.\gradlew.bat -PpyfishVersionProfile=mc12111 build`: full build for another profile
- `.\gradlew.bat -PpyfishVersionProfile=mc262 quickCheck`: verify both loaders on Minecraft 26.2
- `.\gradlew.bat buildAllVersionProfiles`: release matrix build across all configured profiles
- `.\gradlew.bat :common:test`: shared runtime and utility tests only

Select JDK 25 as the Gradle JVM for every profile. This also preserves GraalPy's
native-extension support (`sqlite3`, `ctypes`) at runtime. Gradle and
loader plugin versions are pinned in the repository, so no manual tool update
is needed when switching profiles.

## Source Layout

`src/main/` contains the default implementation. Version-specific adapters live
in `src/mc1211/` or `src/mc262/`; reusable adapters live in `src/shared/`.
The same convention applies inside `fabric/` and `neoforge/`.

Shared directory names describe their consumers: `mc1212-mc1214` means 1.21.2
through 1.21.4, while `mc12111_mc262` means 1.21.11 and 26.2, not the versions
between them. `mc1215-mc12111_mc262` combines a range with that additional target.
The Gradle source sets select these groups explicitly. A version-specific
overlay takes precedence over a shared implementation of the same class.

GameTests follow the same naming convention under `src/gameTest/`. Keep shared
code in one group rather than copying it into each consuming profile.

For content changes, run the GameTests on `mc1211`, `mc12111` and `mc262` as
well as `quickCheck`. The suite covers crafting, loot, tool materials, callbacks,
inventory overflow and particles alongside ore generation. Run configs isolate
their worlds by profile and recreate them for each run. CI runs both loaders
sequentially within each profile to keep memory use predictable.
The remaining 1.21.x profiles have a separate CI matrix that compiles both
loaders and runs the shared unit tests. Server GameTests check resource data
and gameplay behavior, not the final on-screen rendering; verify transparent
blocks in a client as well when changing render-layer handling.

## Coding Guidelines

- Keep loader-independent behavior in `common/` or `src/main/java` whenever possible.
- Prefer small, explicit compatibility layers when Fabric and NeoForge differ.
- Add or update tests when changing manifest parsing, dependency resolution, or runtime loading behavior.
- Keep public docs aligned with the implementation.

## Documentation

The user-facing documentation is maintained in the separate `pyfish-doc` repo.
If your change affects the public API or packaging flow, keep the README and any
in-repo examples accurate, then update the external docs repository separately.

## Pull Requests

Please keep pull requests focused. A good PR usually includes:

1. A short explanation of the problem being solved.
2. The smallest practical code change that solves it cleanly.
3. Notes about how you tested the change.
4. Documentation updates when the user-facing behavior changed.

If a change is intentionally loader-specific, call that out in the PR
description so reviewers do not assume parity by default.
