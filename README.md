# PyFish

![PyFish Logo](https://cdn.modrinth.com/data/bdP4b2gP/a01bf99421098f66b65ad2d4006a5337a18a58f0.png)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20%7C%2026.2-brightgreen.svg)](https://www.minecraft.net/)
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
- Ore generation declared from Python for Fabric and NeoForge
- Support for bundled `assets/` and `data/` resources in both external folders and `.pyz` mods
- Pure-Python dependency installation through `requirements.txt`
- Java-backed helper modules such as `requests`, `sqlite3`, `asyncio`, `logging`, `pathlib`, `websocket`, and more

## Supported Environment

- Minecraft `1.21.1` through `1.21.11`, and `26.2`
- Java `21` for 1.21.x; Java `25` for 26.2
- Fabric Loader or NeoForge matching the selected build profile

## Installation

Downloads:

- Modrinth: <https://modrinth.com/project/pyfish-python>
- CurseForge: <https://www.curseforge.com/minecraft/mc-mods/pyfish-python-scripting-and-modding>

Install the PyFish JAR like any other mod for the matching loader and Minecraft
version.

## Script Locations

PyFish loads Python code from:

- `pyfish/scripts/*.py`
- `pyfish/<namespace>/scripts/*.py`
- packaged `.pyz` mods placed in the Minecraft `mods/` folder
- `src/main/resources/pyfish/...` inside loader JAR mods

Example instance layout:

```text
.minecraft/
|-- mods/
|   |-- pyfish-neoforge-1.21.11-1.2.6.jar
|   `-- example_mod-1.0.0.pyz
|-- config/
`-- pyfish/
    |-- scripts/
    |   `-- global_script.py
    `-- my_modpack/
        |-- requirements.txt
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

## Ore Generation

Register the ore blocks first, then declare where they generate:

```python
from pyfish import blocks, ores

blocks.register_block("tin_ore", {"texture": "block/tin_ore"})
blocks.register_block("deepslate_tin_ore", {"texture": "block/deepslate_tin_ore"})

ores.register_ore("tin_vein", {
    "block": "tin_ore",
    "deepslateBlock": "deepslate_tin_ore",
    "size": 8,
    "count": 12,
    "minY": -32,
    "maxY": 48,
    "distribution": "trapezoid",
    "biomes": "overworld",
})
```

`size` controls vein size; `count` is the number of placement attempts per chunk.
`distribution` accepts `uniform` or `trapezoid`. `biomes` accepts `overworld`,
`nether`, `end`, a biome ID, a `#namespace:tag`, or a list of biome IDs. For
custom host blocks, use `targets` instead of `block`/`deepslateBlock`, for
example `[{"replaceable": "#minecraft:stone_ore_replaceables", "block": "tin_ore"}]`.
Generation affects newly generated chunks; existing chunks are not rewritten.
The height range selects vein origins; individual blocks can extend a few blocks
beyond it.

Ore integration tests run in actual Minecraft servers, with test code excluded
from release JARs:

```text
.\gradlew.bat -PpyfishGameTests :fabric:runGameTest :neoforge:runGameTestServer
.\gradlew.bat -PpyfishGameTests -PpyfishVersionProfile=mc12111 :fabric:runGameTest :neoforge:runGameTestServer
.\gradlew.bat -PpyfishGameTests -PpyfishVersionProfile=mc262 :fabric:runGameTest :neoforge:runGameTestServer
```

The GameTest suite targets `mc1211`, `mc12111` and `mc262` (JDK 25). It checks
fresh noise-generated chunks, stone/deepslate replacement,
height constraints, untouched non-target blocks, and excluded biomes.

## Minimal Example

```python
from pyfish import mc, log_print


def on_player_join(event):
    player = event.getPlayer() or event.getEntity()
    if player is not None:
        mc.send_message(player, "PyFish runtime OK")


log_print("Script loaded")
mc.on("on_player_join", on_player_join)
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
- `.\gradlew.bat -PpyfishVersionProfile=mc262 build`
- `.\gradlew.bat buildAllVersionProfiles`

`quickCheck` runs shared tests and smoke-tests the packaged Python runtime for
each selected loader.
`build` builds only the selected version profile. Use `buildAllVersionProfiles`
when you intentionally want the full release matrix.

For `mc262`, run Gradle with JDK 25 (set the IDE's Gradle JVM or `JAVA_HOME`).
The profile selects the Java 25 compiler, loader versions and unobfuscated
Fabric packaging automatically. Older profiles retain Java 21 and Fabric
remapping. Outputs are kept separately in `build/profiles/<profile>/`.

Minecraft 26.2 uses world clocks. The Python time helpers read and update the
dimension's default clock, falling back to the Overworld clock when there is
no dimension-specific clock.

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
