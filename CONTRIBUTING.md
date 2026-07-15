# Contributing to PyFish

Thanks for taking the time to contribute.

## Before You Start

PyFish currently targets:

- Java 21
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

`quickCheck` is the fastest meaningful verification task for contributors. It
runs the shared tests and the selected loader compile tasks for the active
version profile.

Useful commands:

- `.\gradlew.bat quickCheck`: fast verification for the default profile
- `.\gradlew.bat -PpyfishVersionProfile=mc1211 quickCheck`: fast verification for a specific profile
- `.\gradlew.bat build`: full build for the selected profile
- `.\gradlew.bat -PpyfishVersionProfile=mc12111 build`: full build for another profile
- `.\gradlew.bat buildAllVersionProfiles`: release matrix build across all configured profiles
- `.\gradlew.bat :common:test`: shared runtime and utility tests only

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