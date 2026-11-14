from pyfish import blocks, ores, items, recipes, tags
import sys

assert sys.version_info[:2] == (3, 13), sys.version

items.register_item("preferred_ingot", {"texture": "minecraft:item/iron_ingot"})
recipes.register_schema("minecraft:crafting_shaped", [
    {"path": "key.*", "role": "input", "kind": "ingredient"},
    {"path": "result", "role": "output", "kind": "item_stack"},
])
tags.add_item_tag("pyfish_ore_tests:test_ingots", "minecraft:iron_ingot")
recipes.replace_output({"id": "pyfish_ore_tests:output_recipe"}, "#pyfish_ore_tests:test_ingots", "pyfish_ore_tests:preferred_ingot")
recipes.replace_input({"id": "pyfish_ore_tests:shaped_recipe"}, "minecraft:dirt", "minecraft:cobblestone")
recipes.shapeless("output_recipe", "minecraft:iron_ingot", 2, ["minecraft:cobblestone"])
recipes.shaped("shaped_recipe", "minecraft:stick", 1, ["X"], {"X": "minecraft:dirt"})
recipes.shapeless("removed_recipe", "minecraft:stick", 1, ["minecraft:dirt"])
recipes.remove("removed_recipe")
recipes.shapeless("filtered_recipe", "minecraft:stick", 1, ["minecraft:dirt"])
recipes.remove_matching({"all": [{"mod": "pyfish_ore_tests"}, {"id": "pyfish_ore_tests:filtered_recipe"}],
                         "not": {"type": "minecraft:smelting"}})
recipes.shapeless("ordered_recipe", "minecraft:cobblestone", 1, ["minecraft:cobblestone"])
for source, target in [("cobblestone", "dirt"), ("stone", "cobblestone"), ("dirt", "stone")]:
    recipes.replace_output({"id": "pyfish_ore_tests:ordered_recipe"}, "minecraft:" + source, "minecraft:" + target)

for name, kind, tier in [("low_pickaxe", "pickaxe", 0), ("high_pickaxe", "pickaxe", 4), ("low_multitool", "multitool", 0)]:
    items.register_tool(name, kind, {"tier": tier, "durability": 100, "texture": "minecraft:item/iron_pickaxe"})

items.register_item("blocking_wand", {
    "texture": "minecraft:item/stick",
    "events": {"use": lambda *args: "fail", "use_on": lambda *args: "consume"},
})
blocks.register_block("glass_slab", {
    "texture": "minecraft:block/glass", "renderType": "translucent",
    "shape": [0, 0, 0, 16, 8, 16], "lightLevel": 7, "sound": "glass",
    "miningTool": "pickaxe", "requiredTier": 3, "requiresCorrectToolForDrops": True,
    "stacksTo": 16, "fireResistant": True, "events": {"use": lambda *args: "fail"},
})
blocks.register_block("ghost", {"noCollision": True, "drops": "none", "texture": "minecraft:block/glass"})

blocks.register_block("stone_ore", {"texture": "minecraft:block/emerald_block"})
blocks.register_block("deep_ore", {"texture": "minecraft:block/diamond_block"})

ores.register_ore("natural", {
    "block": "stone_ore",
    "deepslateBlock": "deep_ore",
    "size": 16,
    "count": 48,
    "minY": -48,
    "maxY": 48,
    "distribution": "trapezoid",
})

ores.register_ore("controlled", {
    "block": "stone_ore",
    "deepslateBlock": "deep_ore",
    "size": 16,
    "count": 64,
    "minY": 24,
    "maxY": 36,
    "biomes": "#minecraft:is_overworld",
})

ores.register_ore("excluded", {
    "targets": [{"replaceable": "minecraft:stone", "block": "minecraft:gold_block"}],
    "size": 16,
    "count": 64,
    "minY": 24,
    "maxY": 36,
    "biomes": ["minecraft:nether_wastes"],
})
