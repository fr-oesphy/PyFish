# PyFish

![PyFish Logo](https://cdn.modrinth.com/data/bdP4b2gP/a01bf99421098f66b65ad2d4006a5337a18a58f0.png)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1--1.21.11-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-Multi--profile-yellow.svg)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-Multi--profile-orange.svg)](https://neoforged.net/)
[![Python](https://img.shields.io/badge/Python-GraalPy-blue.svg)](https://graalpy.org/jvm-developers/)
[![License](https://img.shields.io/badge/License-LGPL_3.0-blue.svg)](LICENSE)

PyFish is a multiloader Minecraft mod that embeds GraalPy so Python scripts can
run directly inside the game on Fabric and NeoForge.

It supports three main workflows:

- quick scripts loaded from `pyfish/scripts/`
- organized modpack logic in `pyfish/<namespace>/`
- packaged Python mods distributed as `.pyz` archives or normal loader JARs

## Highlights

- Shared Python API across Fabric and NeoForge
- Event callbacks for player, world, interaction, lifecycle, and loader hooks
- Dynamic content registration from Python for items, blocks, recipes, tabs, tags, and textures
- Support for bundled `assets/` and `data/` resources in both external folders and `.pyz` mods
- Pure-Python dependency installation through `requirements.txt`
- Java-backed helper modules such as `requests`, `sqlite3`, `asyncio`, `logging`, `pathlib`, `websocket`, and more
- **New**: Task scheduling for delayed and repeating operations
- **New**: Decorator-based event registration syntax
- **New**: Pythonic wrappers for Player and World objects
- **New**: Administrative commands for script management and validation
- **New**: Enhanced script reload with proper event cleanup
- **New**: Resource validation tools

## Supported Environment

- Minecraft `1.21.1` through `1.21.11`
- Java `21`
- Fabric Loader or NeoForge matching the selected build profile

## Installation

Downloads:

- Modrinth: <https://modrinth.com/project/pyfish-python>
- CurseForge: <https://www.curseforge.com/minecraft/mc-mods/pyfish-python-scripting-and-modding>

Install the PyFish JAR like any other mod for the matching loader and Minecraft
version.

## Script Locations

PyFish loads Python code from:

- `pyfish/scripts/*.py` (global scripts)
- `pyfish/<namespace>/startup/*.py` (startup scripts - run before registries freeze)
- `pyfish/<namespace>/server/*.py` (server scripts - run on server start and reload)
- `pyfish/<namespace>/client/*.py` (client scripts - run on client init and resource reload)
- `pyfish/<namespace>/scripts/*.py` (fallback server scripts)
- packaged `.pyz` mods placed in the Minecraft `mods/` folder
- `src/main/resources/pyfish/...` inside loader JAR mods

Example instance layout:

```text
.minecraft/
|-- mods/
|   |-- pyfish-neoforge-1.21.11-1.2.5.jar
|   `-- example_mod-1.0.0.pyz
|-- config/
`-- pyfish/
    |-- scripts/
    |   `-- global_script.py
    `-- my_modpack/
        |-- requirements.txt
        |-- startup/
        |   `-- register_content.py
        |-- server/
        |   `-- events.py
        |-- client/
        |   `-- hud.py
        |-- assets/
        |   `-- my_modpack/
        |       `-- textures/
        |           `-- item/
        |               `-- token.png
        |-- data/
        |   `-- my_modpack/
        |       `-- recipe/
        |           `-- token.json
        `-- scripts/
            `-- level_up.py
```

Static resources follow the normal Minecraft layout:

- external namespace folders: `pyfish/<namespace>/assets/<namespace>/...` and `pyfish/<namespace>/data/<namespace>/...`
- `.pyz` mods: `src/assets/<namespace>/...` and `src/data/<namespace>/...`
- loader JAR mods: `src/main/resources/assets/<namespace>/...` and `src/main/resources/data/<namespace>/...`

## Minimal Example

```python
from pyfish import mc, log_print


# Using the new decorator syntax
@mc.event("on_player_join")
def on_player_join(event):
    player = event.player if hasattr(event, 'player') else (event.getPlayer() or event.getEntity())
    if player is not None:
        wrapped_player = mc.wrap_player(player)
        wrapped_player.send_message("PyFish runtime OK")


log_print("Script loaded")
```

## Administrative Commands

PyFish provides in-game commands for server operators (OP level 2):

- `/pyfish reload [server]` - Reload all scripts or only SERVER scripts
- `/pyfish scripts` - List all discovered scripts and their status
- `/pyfish status` - Show PyFish runtime status and statistics
- `/pyfish validate` - Validate resources in pyfish directories
- `/pyfish help` - Show help message

## New API Features

### Decorator-based Event Registration

```python
@mc.event("on_player_join")
def on_player_join(event):
    player = mc.wrap_player(event.player)
    player.send_message("Welcome!")
```

### Task Scheduling

```python
@mc.event("on_server_starting")
def on_server_starting(event):
    # Schedule a delayed task (40 ticks = 2 seconds)
    mc.schedule_delayed_task(event.server, 40, lambda: mc.broadcast_message(event.server, "Ready!"))

    # Schedule a repeating task (every 100 ticks = 5 seconds)
    mc.schedule_repeating_task(event.server, 100, announce_status)
```

### Pythonic Wrappers

```python
# Player wrapper with convenience methods
player = mc.wrap_player(java_player)
player.send_message("Hello!")
player.heal()
player.give_item("minecraft:diamond", 64)

# World wrapper
world = mc.wrap_world(java_world)
world.set_block(0, 64, 0, "minecraft:stone")
world.broadcast_message("Server message")
```

## Documentation

The user documentation lives in a separate repository:

- <https://github.com/fr-oesphy/pyfish-doc> (on the web here : <https://fr-oesphy.github.io/pyfish-doc>)

## Development

Project structure:

- `common/`: shared Gradle module and tests
- `fabric/`: Fabric metadata, entrypoints, and packaging
- `neoforge/`: NeoForge metadata, entrypoints, and packaging
- `src/main/java/fr/oesphy/pyfish/`: shared runtime and API code
- `src/main/resources/`: bundled Python bridge modules and shared assets

Useful commands:

- `.\gradlew.bat listVersionProfiles`
- `.\gradlew.bat quickCheck`
- `.\gradlew.bat -PpyfishVersionProfile=mc1211 quickCheck`
- `.\gradlew.bat build`
- `.\gradlew.bat -PpyfishVersionProfile=mc12111 build`
- `.\gradlew.bat buildAllVersionProfiles`

`quickCheck` is intended as the fast contributor workflow. It runs the shared
tests plus the selected loader compile tasks for the active version profile.
`build` builds only the selected version profile. Use `buildAllVersionProfiles`
when you intentionally want the full release matrix.

## Contributing

Contributions are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md) for
local setup, validation commands, repository structure, and documentation
expectations.

## Maintainer

- OesphyGG

## License

PyFish is licensed under the **GNU Lesser General Public License v3.0 only**.
See [LICENSE](LICENSE) for the project license text and
[TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) for the preserved upstream template notice.

## Community

Discord: <https://discord.gg/5JMGwWeDKQ>

### Want to join me for PyFish's dev ?

Ask me on the Discord.
