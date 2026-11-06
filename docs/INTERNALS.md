# PyFish Internals

This document is the authoritative reference for PyFish's threading model,
GraalPy classloading strategy, and security sandbox.  It is kept in sync with
the source code; every design decision recorded here has a corresponding comment
in the relevant Java class.

---

## 1. Project Layout

```
src/main/java/fr/oesphy/pyfish/
├── PyFish.java                 mod bootstrap, logger, singleton runtime holder
├── PyFishCommon.java           shared init hook called by both loaders
├── script/
│   ├── ScriptType.java         STARTUP | SERVER | CLIENT lifecycle enum
│   ├── PyContextFactory.java   single source of truth for Context construction
│   └── PyRuntime.java          owns a Context, ReentrantLock, runSafe()
├── runtime/
│   ├── GraalPyRuntimeManager.java   script loading, discovery, PYZ, module scan
│   ├── GraalPyRuntimeResources.java GraalPy home extraction for packaged JARs
│   └── ...
├── platform/
│   ├── PyFishPlatform.java          loader interface
│   ├── PlatformEventBridge.java     event registration interface
│   └── PlatformPythonModuleLocator.java  .py file discovery interface
└── ...

fabric/src/main/java/fr/oesphy/pyfish/fabric/
neoforge/src/main/java/fr/oesphy/pyfish/neoforge/
```

---

## 2. GraalPy Embedding

### 2.1 Why GraalPy

GraalPy is the reference implementation of Python 3.12 on the JVM.  It runs
inside the mod JVM without any native Python installation.  Scripts run in a
Truffle polyglot context that shares the heap with Minecraft.

### 2.2 Context construction — `PyContextFactory`

**All** GraalPy contexts in PyFish are created through
[`PyContextFactory.build(ScriptType, ...)`](../src/main/java/fr/oesphy/pyfish/script/PyContextFactory.java).
This is a single-entry-point policy: no other class may call
`Context.newBuilder()` or `GraalPyResources.contextBuilder()` directly.

Builder options chosen and why:

| Option | Value | Reason |
|--------|-------|--------|
| `allowAllAccess` | `true` (Phase 1) | Simplified until Phase 6 sandbox |
| `allowHostAccess` | `HostAccess.ALL` | Scripts must call Java API classes |
| `allowNativeAccess` | `true` | GraalPy needs it for its own native libs |
| `python.EmulateJython` | `true` | Enables `import java.*` idiom |
| `python.IsolateNativeModules` | `true` | Clean reload semantics |
| `engine.WarnInterpreterOnly` | `false` | Expected; Minecraft JVM rarely has GraalVM JIT |
| `currentWorkingDirectory` | `Paths.get(".")` | Windows launcher sets no CWD by default |

Phase 6 will introduce a restricted `HostAccess` policy (allowlist-based), an IO
sandbox limiting scripts to `pyfish/` and the world's `pyfish_data/`, and a
network toggle backed by the config file.

### 2.3 VFS and classloading — `GraalPyRuntimeResources`

GraalPy requires its standard library and runtime resources to be accessible from
the filesystem.  In development, Gradle puts the GraalPy JARs on the classpath
and `GraalPyResources.contextBuilder()` auto-discovers them.

In production (packaged JAR), NeoForge's transformer and Fabric Loom's remapper
wrap the classloader in ways that break GraalPy's internal resource lookup.
`GraalPyRuntimeResources` solves this by:

1. Detecting the manifest files (`libpython.files`, `libgraalpy.files`) inside
   the mod JAR's `META-INF/resources/` directory.
2. Extracting them to a versioned, fingerprinted cache under `.pyfish/runtime-cache/`.
3. Passing the extracted paths to `Context.Builder` via `python.CoreHome`,
   `python.StdLibHome`, etc.

The extraction is guarded by a `FileLock` so concurrent Minecraft instances share
the same cache safely.

**Development path** (when manifest files are absent):
```
GraalPyResources.contextBuilder(VirtualFileSystem) → classpath resources
```

**Production path** (when manifest files are present):
```
GraalPyRuntimeResources.prepare(resourceRoot) →
  .pyfish/runtime-cache/graalpy-25.0.1/<os-arch>-<fingerprint16>/
  → Context.Builder options point here
```

---

## 3. Threading Model

### 3.1 Context thread-confinement

A GraalPy `Context` is **not thread-safe**.  Entering the context from two
threads simultaneously causes a `PolyglotException` and corrupts internal state.

PyFish enforces thread-confinement through `PyRuntime`:

```java
public final class PyRuntime {
    private final ReentrantLock lock = new ReentrantLock();

    public void runSafe(ThrowingRunnable work) {
        lock.lock();
        try { work.run(); }
        finally { lock.unlock(); }
    }
}
```

**Server scripts** (`ScriptType.SERVER`): all execution happens on the **server
thread**.  Fabric's `ServerTickEvents`, NeoForge's `ServerTickEvent`, and
all event callbacks fire on the server thread, so contention is rare.

**Client scripts** (`ScriptType.CLIENT`): all execution will happen on the
**render thread** (implemented in Phase 7).

**Async callbacks** (from `requests`, `websocket`, etc.): the native shims
(Phase 6) always marshal results back onto the owning game thread via
`server.execute(runnable)` or Minecraft's render-thread scheduler before
touching the Python context.

### 3.2 `GraalPyRuntimeManager` and the shared context

`GraalPyRuntimeManager` currently holds a single `Context` (the "shared context")
used for all server-side scripts.  Phase 2 will introduce `ScriptManager` which
wraps this in `PyRuntime` explicitly.  For now, `GraalPyRuntimeManager.getContext()`
is `synchronized` as a transitional guard.

### 3.3 PYZ module isolation

`.pyz` Python mod archives each receive their own globals dict inside the shared
context, implemented in `PyFishPyzExecutor`.  They share the interpreter state
(imported modules, Python heap) but cannot accidentally write to each other's
globals.

---

## 4. Script Lifecycle

```
           Loader startup
                │
       ┌────────▼─────────┐
       │  STARTUP scripts  │  Register items, blocks, tabs, tags
       │  (before freeze)  │  Cannot be reloaded
       └────────┬──────────┘
                │ registries frozen
       ┌────────▼─────────┐
       │  SERVER scripts   │  Event listeners, recipes, loot tables
       │  (server start)   │  Reloaded by /pyfish reload
       └────────┬──────────┘
                │ client connects
       ┌────────▼─────────┐
       │  CLIENT scripts   │  Tooltips, keybinds, HUD, lang
       │  (client init)    │  Reloaded with resource packs
       └───────────────────┘
```

Script discovery order within each type:
1. `init.py` / `init` scripts execute first (legacy pattern).
2. All other scripts in case-insensitive alphabetical order.

Failures are caught per-script, logged at ERROR level, and recorded for
`/pyfish errors`.  A single broken script never prevents others from loading.

---

## 5. Namespace and Script Context

`PyFishScriptContext` holds a `ThreadLocal<String>` with the **current
namespace** (mod ID).  Any content API call made inside a script automatically
resolves bare IDs like `"ruby"` to `"<namespace>:ruby"`.

The namespace is set by wrapping execution in `PyFishScriptContext.push(modId)`,
which returns an `AutoCloseable` scope that restores the previous namespace on
close.  This makes it safe to nest calls.

---

## 6. Security Sandbox (Phase 6)

> **Status**: Phase 1 runs with `allowAllAccess(true)`.  The following
> describes the **target** state after Phase 6.

### 6.1 HostAccess policy

```java
HostAccess policy = HostAccess.newBuilder()
    .allowPublicAccess(true)              // only public members
    .allowArrayAccess(true)
    .allowListAccess(true)
    .denyAccess(Runtime.class)            // no exec/halt
    .denyAccess(ProcessBuilder.class)
    .denyAccess(Class.class, "forName")   // no arbitrary reflection
    .build();
```

Scripts that need direct Java class access must explicitly call
`java.type("…")` and are subject to `ClassFilter` allow/deny lists configured
in `pyfish/config.json` → `class_filter_mode`.

### 6.2 IO policy

Scripts are limited to:
- Read/write: `pyfish/` subtree (scripts, site-packages, assets, data)
- Read/write: world `pyfish_data/<namespace>/` (persistent script data)
- Read-only: Minecraft's data/resource pack directories

The GraalPy IO delegate checks every path before it reaches the OS.

### 6.3 Network policy

Network access (`requests`, `websocket`) is disabled by default.  Set
`"allow_network": true` in `pyfish/config.json` to enable.  The shims consult
`CommonProperties.isNetworkAllowed()` before making any outbound connection.

### 6.4 pip isolation

pip installs into `pyfish/site-packages/` or `pyfish/.venv/`.  Pure-Python
wheels only — C extensions are blocked because they would require native code
execution outside GraalPy's sandbox.  The install directory is on the Context's
`sys.path` at context creation time.

---

## 7. PYZ Archive Security

`.pyz` archives are validated before any code runs:

- Entry count ≤ 10 000
- No absolute paths, backslashes, or `..` traversal
- Total uncompressed size ≤ 128 MiB
- Manifest size ≤ 64 KiB, requirements ≤ 1 MiB, main code ≤ 8 MiB
- Namespace (`id`) must match `[a-z0-9_.-]{1,64}` and not be `minecraft` or `pyfish`
- Namespace must not collide with any already-loaded script pack

---

## 8. Virtual Asset / Data Packs

Generated models, blockstates, item definitions, lang files, loot tables, tag
files, and recipes live in memory in `PyFishResourceGenerator`'s static maps.
No files are written to disk.

Both loaders inject these maps as a highest-priority virtual pack:
- **Fabric**: `PyFishFabricRuntimePackResources` registered via
  `AddBuiltinResourcePackCallback` / `LifecycleEvent.SERVER_STARTING`
- **NeoForge**: `PyFishNeoForgeRuntimePackResources` registered via
  `AddPackFindersEvent`

Script-authored textures (PNG bytes) take precedence over Minecraft's default
assets.  Missing textures fall back to Minecraft's `missing` texture, not an
error.

---

## 9. Build System

The repo uses a **multi-project Gradle build** with a custom `version-profiles`
system to compile against multiple Minecraft versions from one source tree:

```
build.gradle               root — plugin declarations, shared ext properties
common/build.gradle        multiloader-common plugin + neoForge (for mappings)
fabric/build.gradle        multiloader-loader + fabric-loom-remap + shadow
neoforge/build.gradle      multiloader-loader + neoforged.moddev + shadow
```

Source root layout:
- `src/main/java/` — shared code (all profiles)
- `src/mc1211/java/` — 1.21.1-specific overrides
- `src/mc1212/java/` — 1.21.2–1.21.4 overrides
- `src/mc1215/java/` — 1.21.5+ overrides
- `src/mc12111/java/` — 1.21.11-specific overrides

Profile selection:
```powershell
# Default profile (mc1211 = 1.21.1)
.\gradlew.bat build

# Specific profile
.\gradlew.bat build -PpyfishVersionProfile=mc1215

# Skip a loader
.\gradlew.bat :common:test -PpyfishSkipFabric=true -PpyfishSkipNeoForge=true
```

GraalPy version: **25.0.1** (defined in `build.gradle` as `graalPyVersion`).
GraalPy resources are bundled via the `org.graalvm.python` Gradle plugin into
`org.graalvm.python.vfs/` inside the JAR.

---

## 10. Adding a New Script Event (Developer Guide)

1. Add the Java event object class to
   `fr.oesphy.pyfish.api.event.PyFishEventObjects` (or a dedicated package in
   Phase 3+).
2. Add a `case "on_my_event"` branch in `FabricEventBridge.registerEvent()` and
   `NeoForgeEventBridge.registerEvent()`.
3. Trigger the event from the appropriate loader lifecycle callback using
   `PyFish.getPythonRuntime().getMinecraftAPI().trigger("on_my_event", eventObj)`.
4. Document the event in `docs/API.md` (generated from annotations in Phase 8).
5. Add a unit test to `common/src/test/java/`.

---

*Last updated: Phase 1 — multiloader build, GraalPy embedding, `PyContextFactory`, `PyRuntime`, `ScriptType`, hello-world.*
