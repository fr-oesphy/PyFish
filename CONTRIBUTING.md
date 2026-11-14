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

## Native Dependency Tests

The packaged-runtime smoke tests also exercise dependency installation using small
local wheels: resolver conflicts, import-file collisions, ABI rejection, cache
reuse and repair. These do not require a system Python or a C compiler.

To test a real native package during Minecraft startup, add
`-PpyfishNativeGameTests` to the normal GameTest command. The fixture installs
NumPy and verifies matrix multiplication, LAPACK and FFT through its native
extensions before registering a probe item. Run it on all three supported test
profiles. It requires a compatible NumPy wheel for the host platform. For example:

```text
.\gradlew.bat -PpyfishVersionProfile=mc262 -PpyfishGameTests -PpyfishNativeGameTests :fabric:runGameTest :neoforge:runGameTestServer --max-workers=1
```

For a local wheel repository, add `-PpyfishNativeWheelhouse=<directory>`; this
generates an offline requirements file for the fixture. Ordinary GameTests remove
the optional fixture again, so a previous native run does not contaminate them.

`NativeDependencySmokeTest` also accepts `original-libraries` as its third argument.
This opt-in diagnostic installs the original requests, websocket-client, Pillow
and pyperclip packages through the managed installer, and exercises all eleven
counterparts of the former Java helpers. It needs network access, local TCP ports
and an interactive clipboard for the clipboard test. Clipboard contents are
restored without being logged. It writes `original-libraries/results.json` below
the supplied cache directory, distinguishing installation and functional failures.
If Windows itself refuses `OpenClipboard` with error 5, the result is `blocked`
at the environment stage, not a claimed pyperclip incompatibility.

For example, after building Fabric 26.2 and compiling the common tests:

```powershell
$jar = 'build/profiles/mc262/fabric/libs/pyfish-fabric-26.2-1.3.0.jar'
$cp = "build/profiles/mc262/common/classes/java/test;common/src/test/resources;$jar"
java --enable-native-access=ALL-UNNAMED -cp $cp fr.oesphy.pyfish.runtime.NativeDependencySmokeTest $jar .tmp/original-libraries original-libraries
```

Repeat with each loader/profile JAR. The eight removed helpers and the adapted
original websocket-client must pass their original-library checks. The six standard-library replacements also
run offline as part of every packaged-runtime smoke test.

For Minecraft startup coverage, combine `-PpyfishGameTests`,
`-PpyfishNativeGameTests` and `-PpyfishWebSocketGameTests`. The native fixture
installs websocket-client 1.9.2 alongside NumPy and exercises ordinary
`create_connection()` and `WebSocketApp` calls without `socket=`. It also verifies
that explicitly requested unsupported TCP options still raise their original
errors. Use an offline wheelhouse containing both wheels if needed.

Add `-PpyfishWebSocketTlsDirectory=<directory>` for WSS coverage. That directory
must contain a throwaway localhost certificate named `websocket-test.crt` and its
private key `websocket-test.key`, with a SAN for IP `127.0.0.1`. The client trusts
only that fixture CA and retains hostname checking. Never use production keys;
keep fixtures in `.tmp/`. No certificates are installed into the OS trust store.
The packaged-runtime smoke tests also cover selective filtering, repeated
activation, unchanged supported defaults and propagation of unexpected errors.

After populating that cache, use `interop-diagnostics` instead of
`original-libraries` to inspect the default WebSocket socket options and the raw
Windows clipboard calls. This also asserts text, binary and callback exchanges
with websocket-client's optional `socket=` argument. Normal calls are covered by
the original-library and GameTest suites. The JSON report is written
to `original-libraries/interop-results.json`. Clipboard text is never logged.
The test-only `-Dpyfish.test.posixBackend=native` option compares GraalPy's native
backend, but is not a supported production setting: it may bypass the embedding's
redirected standard streams. No clipboard permission or desktop ACL is modified.

For a read-only comparison inside a graphical Minecraft client, run
`./gradlew -PpyfishVersionProfile=mc262 -PpyfishClipboardClientTest :fabric:runClipboardClient`.
The isolated client writes `fabric/runs/clipboard-test-mc262/clipboard-client-report.json`
after startup, comparing AWT, GLFW, Minecraft's handler and real pyperclip on the
render thread. Neither clipboard contents nor hashes are logged, and no world
is opened. Check native error codes as well as returned values: Minecraft's
handler can return an empty string after a failed system read. This test mod is
not included in release JARs.

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
