# PyFish

![PyFish Logo](https://cdn.modrinth.com/data/bdP4b2gP/a01bf99421098f66b65ad2d4006a5337a18a58f0.png)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20%7C%2026.2-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-Multi--profile-yellow.svg)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-Multi--profile-orange.svg)](https://neoforged.net/)
[![Python](https://img.shields.io/badge/Python-GraalPy-blue.svg)](https://graalpy.org/jvm-developers/)
[![License](https://img.shields.io/badge/License-LGPL_3.0-blue.svg)](LICENSE)

PyFish is a multiloader Minecraft mod that embeds GraalPy so Python scripts can
run directly inside the game on Fabric and NeoForge.

The embedded runtime is GraalPy 25.3.4.1 (Python 3.13). No system Python
installation is required. Python packages must be compatible with GraalPy;
native wheels built for CPython are not interchangeable.
Cached requirements are reinstalled automatically when the GraalPy version or
platform changes, so Python 3.12 native packages are not reused with Python 3.13.

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
- Java `25` for all Minecraft profiles
- Fabric Loader `0.20.1` or newer when using Fabric
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
|   |-- pyfish-neoforge-1.21.11-1.2.7.jar
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
It also exercises block properties and loot, tool tiers, real crafting and
recipe removal/replacement, Python interaction results, inventory overflow,
and parameterized particles. These are server tests; they do not validate
on-screen rendering. Unit tests additionally inspect generated render metadata.

## Content Properties

Block properties can configure the physical block as well as its item:

```python
from pyfish import blocks

blocks.register_block("glass_slab", {
    "texture": "block/glass_slab",
    "renderType": "translucent",
    "shape": [0, 0, 0, 16, 8, 16],
    "lightLevel": 7,
    "sound": "glass",
    "miningTool": "pickaxe",
    "requiredTier": 2,
    "requiresCorrectToolForDrops": True,
    "stacksTo": 16,
})
```

| Property | Meaning |
| --- | --- |
| `renderType` | `solid` (default), `cutout`, `cutout_mipped`, or `translucent`. Non-solid modes also disable occlusion. |
| `noOcclusion` | Disable occlusion explicitly, independently of the render mode. |
| `noCollision` | Make the block non-collidable. |
| `shape` | Outline and default collision shape, as six coordinates in `0..16`, or a list of such boxes. `[]` is empty. |
| `collisionShape` | Override the collision shape without changing the outline. |
| `lightLevel` | Emitted light, from 0 through 15. |
| `sound` | `stone`, `wood`, `metal`, `glass`, `gravel`, `sand`, `grass`, or `wool`. |
| `friction`, `speedFactor`, `jumpFactor` | Physical movement multipliers. |
| `drops` | `self` (default), `none`, or an item ID. Authored loot tables take priority. |
| `miningTool` | Add the block to the vanilla `pickaxe`, `axe`, `shovel`, or `hoe` mining tag. |
| `requiredTier` | Declare a stone, iron, or diamond mining requirement; combine with `requiresCorrectToolForDrops`. Netherite uses vanilla's diamond requirement. |
| `stacksTo`, `fireResistant` | Configure the block's inventory item. |

In 1.21.x, PyFish sets the Fabric render layer and generates the corresponding
NeoForge model metadata. Minecraft 26.2 selects layers automatically from each
face's pixels; `translucent` additionally forces translucency on generated
texture materials. Explicit metadata in an authored model is preserved.
Minecraft 1.21.11 merges `cutout_mipped` into `cutout`; PyFish generates the
supported layer name for that version.
Custom JSON geometry does not infer a collision shape: declare `shape` as well.
Unknown property names produce warnings rather than silently disappearing.

Tools accept `tier` as `0` (wood), `1` (stone), `2` (iron, default), `3`
(diamond), or `4` (netherite), or a material name including `gold`.
Both normal tools and multitools respect their material's harvesting limits.
The tier controls harvesting eligibility, not the defaults for `speed`,
`durability` or damage; configure those separately.

Content `use` and `use_on` callbacks may return `None` or `"pass"` to continue
vanilla behavior, `True`/`"success"` or `"consume"` to handle the action, or
`False`/`"fail"` to reject it. Callbacks run on the server. The `eat` callback
is a notification, not a cancellable interaction.

### Recipe Editing

There is no automatic unification or preferred-mod selection. Register explicit
operations during script startup; they run in declaration order on server start
and datapack reload, against a fresh recipe snapshot rather than previous edits.
The previous `tags.unify` entry point has been removed; use explicit replacements instead.

```python
from pyfish import recipes

recipes.replace_input({"type": "minecraft:crafting_shaped"},
                      "minecraft:iron_ingot", "#c:ingots/iron")
recipes.replace_output({"mod": "example"},
                       "#c:ingots/tin", "example:tin_ingot")
recipes.remove_matching({"all": [{"type": "minecraft:smelting"},
                                 {"not": {"mod": "minecraft"}}]})
```

Filters accept exact `id`, `type`, `mod` (recipe namespace), `input`, and `output`.
Dictionary entries are ANDed; a list is OR. `all` and `any` take filter lists;
`not` takes a filter. `{}` selects everything. Use explicit namespaced IDs in
filters. Item selectors accept item IDs or `#tag` selectors. An item match does
not expand or narrow ingredient tags; use the exact tag selector to replace one.
Output replacements require a concrete item ID. Existing stack counts and
explicit components are retained. Ingredients and outputs are separate operations.
No inventories, loot tables or placed blocks are changed.

Built-in schemas cover shaped/shapeless crafting, cooking, stonecutting and
smithing. For a modded recipe type, declare the fields explicitly:

```python
recipes.register_schema("example:crusher", [
    {"path": "ingredients.*", "role": "input", "kind": "ingredient"},
    {"path": "results.*.stack", "role": "output", "kind": "item_stack"},
])
```

Paths use dot-separated keys, numeric array indices, and `*` for array elements or object values.
`ingredient` supports item/tag strings, simple item/tag objects and alternatives.
`item_stack` supports item strings and objects with `id` or `item`, retaining
other fields such as count and components. These are recipe field kinds, not
Minecraft data components. Custom ingredient codecs and fluids are not inferred.
Declare leaf stack paths to leave probabilities, durations and other metadata untouched.
Registering a schema for an existing type replaces its schema.

`recipes.get_last_report()` returns `modified`, `removed`, `unsupported` and
`failed` after application. Unknown recipe types are listed rather than guessed;
ID/type/mod filters can still remove them. Edits failing Minecraft's codec
validation leave that entire recipe unchanged. `recipes.remove(id)` still works.

During startup, `tags.get_items_by_tag()` inspects the available static and
generated resources. Once a server has loaded its datapacks, it uses the actual
resolved registry tags, including datapack overrides.

`mc.spawn_particle(..., options={...})` accepts the particle-specific JSON fields
for the selected Minecraft version, including block-state and dust parameters.
Simple particles still work without `options`. `mc.give_item` drops any inventory
overflow in ordinary-sized stacks instead of discarding it.

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

Run Gradle with JDK 25 (set the IDE's Gradle JVM or `JAVA_HOME`). GraalPy 25.3
requires it for full native-extension support, including `sqlite3` and `ctypes`.
Minecraft 26.2 uses unobfuscated Fabric packaging; older profiles retain Fabric
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
