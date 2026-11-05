from pyfish import blocks, ores

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
