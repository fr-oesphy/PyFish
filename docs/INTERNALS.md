# PyFish Internals

This document explains the internal architecture of PyFish, including threading, classloading, sandboxing, and how GraalPy is embedded.

## Overview

PyFish embeds GraalPy (a Python implementation for the JVM) to run Python scripts inside Minecraft. The architecture is designed to be:
- **Multiloader**: Works on both Fabric and NeoForge
- **Jar-in-jar**: GraalPy libraries are bundled inside the mod JAR
- **Sandboxed**: Python code runs with controlled access to Java APIs
- **Thread-safe**: GraalPy Context is confined to specific threads

## Architecture

### Module Structure

```
PyFish/
├── common/                          # Shared code (loader-neutral)
│   └── src/main/java/fr/oesphy/pyfish/
│       ├── PyFish.java              # Mod bootstrap
│       ├── api/MinecraftAPI.java    # Java API exposed to Python
│       ├── platform/                # Platform abstraction layer
│       │   ├── PyFishPlatform.java  # Platform interface
│       │   ├── PlatformEventBridge.java
│       │   └── PlatformPythonModuleLocator.java
│       ├── runtime/
│       │   ├── GraalPyRuntimeManager.java  # Manages Python Context
│       │   ├── PyFishPyzExecutor.java     # PYZ mod execution
│       │   ├── PyFishPyzMod.java          # PYZ mod metadata
│       │   ├── PythonModuleScanner.java  # Discovers Python modules
│       │   └── PythonDependencyManager.java
│       ├── script/                  # Script management system (Phase 2)
│       │   ├── ScriptType.java      # STARTUP, SERVER, CLIENT enum
│       │   ├── ScriptFile.java      # Script metadata and error tracking
│       │   ├── ScriptPack.java      # Namespace folder representation
│       │   ├── ScriptManager.java   # Discovery, load order, reload
│       │   ├── ConsoleApi.java      # Logging with file/line tracking
│       │   ├── ClassFilter.java     # Java class access control
│       │   └── PyContextFactory.java # Context creation with security
│       ├── event/                   # Event system (Phase 3)
│       │   ├── PyEvent.java         # Base event class with cancel/result support
│       │   └── EventResult.java    # PASS, CANCEL, SUCCESS, CONSUME enum
│       ├── content/                 # Content registration system
│       ├── config/                  # Configuration management
│       ├── compat/                  # Version compatibility layers
│       └── http/                    # HTTP client for requests shim
├── fabric/                          # Fabric-specific code
│   └── src/main/java/fr/oesphy/pyfish/fabric/
│       ├── PyFishFabric.java        # Fabric entrypoint
│       ├── FabricPlatform.java      # Platform implementation
│       ├── FabricEventBridge.java   # Event bridging
│       └── FabricPythonModuleLocator.java
└── neoforge/                       # NeoForge-specific code
    └── src/main/java/fr/oesphy/pyfish/neoforge/
        ├── PyFishNeoForge.java      # NeoForge entrypoint
        ├── NeoForgePlatform.java    # Platform implementation
        ├── NeoForgeEventBridge.java # Event bridging
        └── NeoForgePythonModuleLocator.java
```

## Threading Model

### GraalPy Context Confinement

GraalPy's `Context` object is **not thread-safe**. PyFish handles this through:

1. **Single Shared Context**: All scripts share one `Context` to allow module imports and callback objects to persist between events.

2. **Thread Confinement**: The Context is created on and confined to the server thread (for server scripts) or client thread (for client scripts).

3. **Safe Call-In**: All Python execution goes through the owning thread. The `GraalPyRuntimeManager` ensures this by executing scripts on the correct thread.

### Current Implementation

The current implementation uses a **single shared Context** mode:
- The Context is created once during initialization
- All scripts execute in this same Context
- Event callbacks are invoked from the Context's owning thread
- Module imports persist across script executions

**Note**: This is simpler than per-script contexts but means:
- Scripts share global state (Python's `sys.modules`, imports, etc.)
- One script's imports are visible to others
- State pollution is possible between scripts

### Future Improvements

For better isolation, consider:
- Per-namespace Contexts (one per modpack namespace)
- Context pooling for concurrent script execution
- Explicit state clearing on reload

## Classloading

### Jar-in-Jar Bundling

GraalPy and its dependencies are bundled inside the mod JAR using the Shadow plugin:

```gradle
configurations {
    embed
    implementation.extendsFrom(embed)
}

dependencies {
    rootProject.ext.pyfishEmbeddedLibraries.each {
        embed it
    }
}

shadowJar {
    configurations = [project.configurations.embed]
    // Excludes native binaries, manifests, etc.
}
```

The embedded libraries include:
- `org.graalvm.sdk:graal-sdk`
- `org.graalvm.polyglot:polyglot`
- `org.graalvm.polyglot:python-community`
- `org.graalvm.polyglot:js-community`
- `org.graalvm.truffle:truffle-api`
- `org.graalvm.regex:regex`
- `org.graalvm.python:python-embedding`
- `org.bouncycastle:bcprov-jdk18on` (for SSL)
- `org.bouncycastle:bcpkix-jdk18on` (for SSL)
- `org.xerial:sqlite-jdbc` (for sqlite3 shim)

### Module Layer Issues

GraalPy uses its own module system and VFS (Virtual File System) for Python's stdlib. This is configured via:

```java
Context.Builder contextBuilder = GraalPyResources.contextBuilder()
    .allowAllAccess(true)
    .allowHostAccess(HostAccess.ALL)
    .allowHostClassLookup(className -> true)
    .allowNativeAccess(true)
    .currentWorkingDirectory(Paths.get(".").toAbsolutePath())
    .out(stdout)
    .err(stderr)
    .allowExperimentalOptions(true)
    .option("python.EmulateJython", "true")
    .option("python.IsolateNativeModules", "true")
    .option("engine.WarnInterpreterOnly", "false");
```

The `GraalPyResources.contextBuilder()` automatically:
- Sets up the Python language home
- Configures the stdlib VFS
- Wires in bundled Python resources

### Module Discovery

Python modules are discovered from multiple sources:

1. **Resources in the mod JAR**: Scanned via `PythonModuleScanner.scanJarTopLevelPythonFiles()`
2. **Platform-specific locators**: `PlatformPythonModuleLocator` implementations for Fabric/NeoForge
3. **Classloader resources**: Fallback scanning of classloader roots
4. **External files**: Scripts loaded from `pyfish/` directory
5. **PYZ mods**: Packaged Python mods in the `mods/` folder

### Context Creation with PyContextFactory

The new `PyContextFactory` (Phase 2) provides a structured way to create GraalPy Contexts with security policies:

```java
PyContextFactory factory = new PyContextFactory(
    classFilter,           // ClassFilter for access control
    allowNetwork,          // boolean for network access
    allowNativeAccess,     // boolean for native library loading
    stdout,                // OutputStream for stdout
    stderr                 // OutputStream for stderr
);

Context context = factory.createContext(ScriptType.SERVER);
```

The factory applies security settings based on the `ClassFilter` mode:
- **ALLOW_ALL**: No restrictions (insecure, but compatible)
- **DENY_ALL**: Whititelist only (secure, requires explicit allow list)
- **WHITELIST**: Only allowed prefixes accessible
- **BLACKLIST**: All except denied prefixes accessible

This replaces the direct Context builder usage in `GraalPyRuntimeManager` for better security control.

Discovered modules are registered into Python's `sys.modules` with retry logic to handle import dependencies.

## Sandbox Model

### Current Security Settings

**Note**: The current implementation uses `allowAllAccess(true)` for compatibility with existing scripts. This is **not a sandbox** in the security sense.

```java
.allowAllAccess(true)
.allowHostAccess(HostAccess.ALL)
.allowHostClassLookup(className -> true)
.allowNativeAccess(true)
```

This means Python scripts can:
- Access any Java class
- Call any Java method
- Load native libraries
- Perform I/O operations

### Security Considerations

The current setup is **trusted-by-default**:
- PYZ mods are treated like normal JAR mods (trusted local code)
- External scripts from `pyfish/` are also trusted
- No class filtering or access restrictions

### Recommended Hardening

For production use, consider:

1. **Disable allowAllAccess**:
   ```java
   .allowAllAccess(false)
   .allowHostAccess(HostAccess.EXPLICIT)
   .allowHostClassLookup(new ClassFilter())  // Custom allow/deny list
   ```

2. **Implement ClassFilter**:
   ```java
   public class ClassFilter {
       public boolean check(String className) {
           // Allow only specific packages
           return className.startsWith("net.minecraft.")
               || className.startsWith("fr.oesphy.pyfish.");
       }
   }
   ```

3. **Restrict I/O**:
   - Use `allowIO(false)` and implement a custom `IOAccess` policy
   - Limit file access to `pyfish/` and `pyfish_data/` directories

4. **Disable Native Access**:
   ```java
   .allowNativeAccess(false)  // Prevent native library loading
   ```

5. **Network Control**:
   - Add a config flag `allow_network`
   - Gate `requests` and `websocket` shims on this flag
   - Implement a whitelist of allowed domains

### Configurable Security

The planned `CommonProperties` will include:
- `allow_network`: Enable/disable network access
- `allow_eval`: Enable/disable `/pyfish eval` command
- `class_filter_mode`: "allow_all", "deny_all", or "whitelist"
- `class_filter_whitelist`: List of allowed Java packages

## Script Management Architecture (Phase 2)

### New Components

Phase 2 introduced a structured script management system:

- **ScriptType**: Enum defining STARTUP, SERVER, CLIENT script types
- **ScriptFile**: Represents a single script with metadata, source, header directives, and error tracking
- **ScriptPack**: Represents a namespace folder containing multiple scripts
- **ScriptManager**: Orchestrates discovery, loading, and reloading of scripts
- **ConsoleApi**: Enhanced logging with file/line tracking and ring buffer
- **ClassFilter**: Configurable Java class access control (allow/deny lists)
- **PyContextFactory**: Factory for creating Contexts with security policies

### Script Directory Layout

```
instance/
└── pyfish/
    ├── scripts/                          # Global scripts (SERVER type)
    │   └── global_script.py
    └── my_modpack/                      # Namespace pack
        ├── startup/                      # STARTUP scripts
        │   └── register_content.py
        ├── server/                       # SERVER scripts
        │   └── events.py
        ├── client/                       # CLIENT scripts
        │   └── hud.py
        ├── scripts/                      # Fallback SERVER scripts
        │   └── handlers.py
        ├── assets/                       # Static resources
        │   └── my_modpack/
        │       └── textures/
        └── data/                         # Data resources
            └── my_modpack/
                └── recipes/
```

### Integration with Legacy System

The new script management system is integrated alongside the legacy system:

- `GraalPyRuntimeManager` now has an optional `ScriptManager`
- Initialized via `initScriptManager(instanceRoot)` in `PyFish.initialize()`
- Legacy script loading (`loadScriptsFromPath`, `loadScriptsFromResources`) still works
- Both systems can coexist during migration

### Script Loading Order

Scripts are sorted for execution in the following order:

1. `init.py` files (if present) execute first
2. Remaining scripts in alphabetical order by filename
3. Within each pack, scripts execute in type order: STARTUP → SERVER → CLIENT

This ensures initialization scripts run before event handlers.

## Script Lifecycle

### Initialization

1. **Mod Entry Point**: `PyFishFabric.initialize()` or `PyFishNeoForge.initialize()`
2. **Platform Setup**: Create `PyFishPlatform` implementation
3. **Runtime Manager**: Instantiate `GraalPyRuntimeManager`
4. **Context Creation**: First script execution triggers Context creation
5. **Module Registration**: Scan and register Python modules
6. **Script Loading**: Load scripts from resources, external paths, and PYZ mods
7. **Script Execution**: Execute all loaded scripts in load order

### Script Types

The new script management system (Phase 2) introduces three script types:

- **STARTUP scripts**: Run before registries freeze, can register content. Located in `pyfish/<namespace>/startup/`. Cannot be hot-reloaded.
- **SERVER scripts**: Run on server start and reload, handle events. Located in `pyfish/<namespace>/server/` or `pyfish/<namespace>/scripts/`. Can be hot-reloaded.
- **CLIENT scripts**: Run on client init and resource reload, handle client-side events. Located in `pyfish/<namespace>/client/`. Can be hot-reloaded.

Global scripts (no namespace) in `pyfish/scripts/` default to SERVER type.

### Script Discovery

The new `ScriptManager` handles script discovery with improved structure:

1. **Global scripts**: `<instance>/pyfish/scripts/*.py` -> SERVER type, no namespace
2. **Namespace packs**: `<instance>/pyfish/<namespace>/` -> One pack per namespace
   - `<namespace>/startup/*.py` -> STARTUP type
   - `<namespace>/server/*.py` -> SERVER type
   - `<namespace>/client/*.py` -> CLIENT type
   - `<namespace>/scripts/*.py` -> SERVER type (fallback)
3. **Legacy discovery**: Still works via `GraalPyRuntimeManager.loadScriptsFromPath()`
4. **PYZ mods**: Packaged Python mods in `mods/`

Namespace is tracked so `register_item("ruby")` becomes `<namespace>:ruby`.

Header directives can be added to scripts using `# @pyfish key=value` syntax.

### Reload

The `ScriptManager.reload()` method:
1. Clears all script packs
2. Clears the console ring buffer
3. Re-discovers scripts from disk
4. Reloads script sources
5. Parses header directives

The planned `/pyfish reload` command will additionally:
1. Clear event callbacks from `MinecraftAPI`
2. Re-execute scripts
3. Re-register event handlers
4. Preserve Context and module imports

Reload is currently supported at the discovery level but not yet wired to commands.

### Console API and Error Tracking

The new `ConsoleApi` (Phase 2) provides enhanced logging with file/line tracking:

```java
ConsoleApi console = scriptManager.getConsoleApi();
console.log("Message", "script.py", 10);  // With location
console.warn("Warning", "script.py", 20);
console.error("Error", "script.py", 30);
```

Features:
- **File/line tracking**: Logs include source file and line number
- **Ring buffer**: Last 1000 log entries stored in memory
- **Log levels**: INFO, WARN, ERROR
- **Query API**: Get all entries or last N entries

Error tracking is handled by `ScriptFile.ScriptError`:
- Stores error message, traceback, line, column, and timestamp
- Associated with each `ScriptFile` instance
- Queryable via `ScriptManager.getScriptsWithErrors()`

### Event System Enhancements (Phase 3)

Phase 3 added event result support and cancellable events:

**EventResult Enum**:
- `PASS`: Pass to next handler (default)
- `CANCEL`: Cancel the event, prevent default action
- `SUCCESS`: Event handled successfully
- `CONSUME`: Event handled, no further processing needed

**Event Object Enhancements**:
- Event objects can have `cancel()` and `setResult()` methods
- `PlayerChatEvent` now supports cancellation and result
- Other events can be upgraded as needed
- Maintains backward compatibility with existing scripts

**Python API**:
```python
from pyfish import mc, EventResult

@events.player.chat
def on_chat(e):
    if "creeper" in e.message:
        e.cancel()  # Cancel the chat message
        return EventResult.CANCEL

    # Alternative: return EventResult directly
    return EventResult.PASS
```

**Java Integration**:
- `MinecraftAPI.triggerWithResult()` handles EventResult returns
- Python callbacks can return strings ("PASS", "CANCEL", etc.) converted to EventResult
- Platform bridges can check event cancellation (planned)

## Event System

### Event Registration

Python scripts register event callbacks via:

```python
from pyfish import mc

def on_player_join(event):
    player = event.getPlayer() or event.getEntity()
    if player is not None:
        mc.send_message(player, "PyFish runtime OK")

mc.on("on_player_join", on_player_join)
```

The `mc.on()` method:
1. Stores the callback in a map keyed by event name
2. Calls `PlatformEventBridge.registerEvent()` to wire the event
3. Each event is wired only once (tracked in `wiredEvents` set)

### Event Dispatch

When a Minecraft event fires:
1. Platform-specific bridge (`FabricEventBridge` or `NeoForgeEventBridge`) intercepts it
2. Bridge converts the event to Python-compatible arguments
3. Bridge calls `MinecraftAPI.trigger()` with the event name and arguments
4. `MinecraftAPI.trigger()` iterates through registered callbacks
5. Each callback is executed with proper namespace context

### Platform Abstraction

The `PlatformEventBridge` interface allows:
- Common code to register events without knowing the loader
- Each loader to implement event wiring in its own way
- Fabric and NeoForge to support different event sets

## Content Registration

### Current System

The existing content system uses:
- `PyFishContentBridge`: Central registry for items, blocks, recipes, tags, textures, tabs
- `PyFishResourceGenerator`: Generates in-memory resource packs
- Dynamic registries: `DynamicItemRegistry`, `DynamicBlockRegistry`

Scripts register content via:

```python
from pyfish import items, blocks, recipes

items.register_item("ruby", display_name="Ruby")
blocks.register_block("ruby_block", hardness=3)
recipes.shaped("ruby_block", ["RRR", "RRR", "RRR"], {"R": "ruby"})
```

### Virtual Packs

Content resources (models, blockstates, lang, loot tables) are generated in memory via:
- `VirtualAssetPack` (Fabric) / `VirtualDataPack` (NeoForge)
- Loaded at highest priority so they override other packs
- Textures read from `pyfish/<namespace>/assets/` when present
- Missing textures fall back to a placeholder

## Native-Lib Shims

Java-backed Python modules provide API parity for common libraries:

### Available Shims

- `java_requests.py`: HTTP client (`requests.get`, `requests.post`, etc.)
- `java_sqlite3.py`: SQLite database (`sqlite3.connect`, cursor, execute, etc.)
- `java_asyncio.py`: Async primitives (`asyncio.sleep`, `asyncio.gather`, etc.)
- `java_logging.py`: Logging (maps to SLF4J logger)
- `java_pathlib.py`: Path manipulation (sandboxed Path)
- `java_websocket.py`: WebSocket client
- `java_multiprocessing.py`: Process management (limited)
- `java_pillow.py`: Image processing (PIL compatibility)
- `java_pyperclip.py`: Clipboard access
- `java_uuid.py`: UUID generation
- `java_subprocess.py`: Subprocess execution (limited)

### Implementation

Each shim:
- Wraps Java APIs in Python-friendly interfaces
- Uses the `java.type()` escape hatch for Java interop
- Provides clear errors for unsupported operations
- Honors the `allow_network` config flag where applicable

## PYZ Mods

### PYZ Format

PYZ mods are ZIP archives containing:
- Python scripts in `scripts/`
- Assets in `assets/<namespace>/`
- Data in `data/<namespace>/`
- Optional `requirements.txt` for dependencies
- Optional `manifest.json` for metadata

### Loading

PYZ mods are loaded from the `mods/` folder:
1. Scanned on mod initialization
2. Metadata extracted from `manifest.json` or filename
3. Scripts loaded and executed with proper namespace
4. Assets/data registered as virtual packs
5. Dependencies installed from `requirements.txt`

### Execution

PYZ entrypoints execute in a dedicated globals scope via `PyFishPyzExecutor`:
- Each PYZ mod gets its own module context
- Imports persist within the mod's context
- Shared Context allows cross-mod communication

## Networking

### Client-Server Communication

PyFish includes networking for:
- Error synchronization: Send script errors to clients
- Reload coordination: Notify clients of script reloads
- Presence detection: Track which clients have PyFish

### Payloads

- `PyFishClientHelloPayload`: Client announces PyFish presence
- Future: Error payloads, reload notifications

## Testing

### Unit Tests

Located in `common/src/test/java/`:
- `PythonModuleScannerTest`: Module discovery
- `PyFishPyzModTest`: PYZ mod loading
- `MinecraftCompatTest`: Version compatibility

### Running Tests

```bash
.\gradlew.bat :common:test
```

### GameTests

Planned integration tests will:
- Boot a world
- Run a sample script
- Assert that an item, recipe, and event handler work

## Performance Considerations

### Context Creation

Creating a GraalPy Context is expensive. The shared Context approach:
- Creates the Context once on first use
- Reuses it for all script executions
- Avoids the overhead of per-script Contexts

### Memory Usage

Python objects and imports persist in the shared Context:
- Large imports (e.g., `numpy`) increase memory footprint
- State accumulation over time can cause memory bloat
- Reload should clear unnecessary state

### Event Callback Overhead

Each event callback:
- Invokes Python through GraalPy's polyglot API
- Converts Java objects to Python values
- Has some overhead compared to pure Java

For high-frequency events (e.g., tick events), consider:
- Batching events
- Using pure Java where performance is critical
- Throttling callback execution

## Troubleshooting

### Common Issues

**ImportError: No module named 'X'**
- Check that the module is in the JAR or on the Python path
- Verify module discovery is working (check logs for "Registered Python module")
- For PYZ mods, ensure dependencies are in `requirements.txt`

**ClassNotFoundException**
- Usually a classloading issue with jar-in-jar
- Verify the shadowJar task is bundling the dependency
- Check that the class is not excluded in shadowJar configuration

**Context not thread-safe**
- Ensure script execution happens on the correct thread
- Don't share Context objects across threads
- Use the thread confinement guarantees in `GraalPyRuntimeManager`

**Script errors not showing**
- Check that event callbacks are properly registered
- Verify the event name matches the platform's event name
- Look for errors in the Minecraft logs

### Debugging

Enable debug logging by setting the log level in the config:
```json
{
  "log_level": "debug"
}
```

Enable GraalPy warnings:
```java
.option("engine.WarnInterpreterOnly", "false")  // Set to true for warnings
```

## Future Work

### Planned Features

1. **Per-namespace Contexts**: Better isolation between modpack namespaces
2. **Configurable sandbox**: Security hardening with class filtering
3. **Async/await support**: Full asyncio integration with game ticks
4. **Hot reload**: Reload scripts without restarting the game
5. **IDE support**: Generate `.pyi` stub files for autocomplete
6. **More shims**: Additional Python stdlib compatibility
7. **Performance monitoring**: Track script execution time

### Contributing

When adding new features:
1. Update this document with architectural changes
2. Add tests for new functionality
3. Consider multiloader compatibility
4. Document security implications
5. Update the AGENTS.md file with build commands
