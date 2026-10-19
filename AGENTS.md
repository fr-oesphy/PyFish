# Repository Guidelines

## Project Structure & Module Organization

PyFish is a Java 21, Gradle-based Minecraft mod that embeds GraalPy. Shared
runtime and API code lives in `src/main/java/fr/oesphy/pyfish/`; bundled Python
bridge modules and assets live in `src/main/resources/`. Keep loader-neutral
behavior there whenever possible. The `common/` module assembles shared sources
and contains tests in `common/src/test/java/`. Put Fabric-only entrypoints,
mixins, and metadata in `fabric/`; put NeoForge equivalents in `neoforge/`.

Minecraft-specific compatibility sources use profile directories such as
`src/mc1212/java/` and `fabric/src/mc1212/java/`. Version selection is defined
in `gradle/version-profiles.json`; do not change a compatibility class without
checking the profiles that select it.

## Build, Test, and Development Commands

Run commands from the repository root (use `./gradlew` on Linux/macOS):

```text
.\gradlew.bat listVersionProfiles
.\gradlew.bat quickCheck
.\gradlew.bat -PpyfishVersionProfile=mc12111 quickCheck
.\gradlew.bat build
.\gradlew.bat buildAllVersionProfiles
.\gradlew.bat :common:test
```

`quickCheck` runs shared tests and validates runtime JAR contents for the
selected profile; use it for routine changes. `build` packages both loaders for
that profile. Reserve `buildAllVersionProfiles` for release-wide compatibility
checks. Build artifacts are written below `build/profiles/<profile>/`.

## Coding Style & Naming Conventions

Use four spaces for Java and Groovy indentation, braces on the declaration
line, and one public top-level class per file. Follow existing package layout
(`fr.oesphy.pyfish`) and Java names: `PascalCase` classes, `camelCase` methods
and fields, `UPPER_SNAKE_CASE` constants. Tests use descriptive behavior names,
for example `scanJarTopLevelPythonFilesLoadsRootModules`. No formatter or linter
is configured; match nearby code and keep loader differences behind small,
explicit compatibility layers.

## Testing Guidelines

Tests use JUnit Jupiter 5 and belong under `common/src/test/java/`, mirroring the
production package. Add or update focused tests for changes to module scanning,
manifests, dependency resolution, or runtime loading. Add tests for script
management components (ScriptManager, ClassFilter, etc.) in the `script/` test
package. Run `:common:test` while iterating and `quickCheck` before submitting.
There is no stated coverage target.

## Commits & Pull Requests

Write short, imperative commit subjects that identify the changed area, such as
`Fix Fabric runtime pack resources`. Keep each commit and PR focused. PRs should
explain the problem and solution, name affected loaders/profiles, list commands
run, link relevant issues, and include screenshots or logs when player-visible
behavior changes. Update the README and external documentation when public APIs
or packaging behavior change.

## Internal Documentation

See `docs/INTERNALS.md` for detailed information about:
- Threading model and GraalPy Context confinement
- Classloading and jar-in-jar bundling
- Sandbox model and security considerations
- Script lifecycle and event system
- Script management architecture (ScriptManager, ScriptType, etc.)
- Content registration and virtual packs
- Native-lib shims and PYZ mods
- Performance considerations and troubleshooting
