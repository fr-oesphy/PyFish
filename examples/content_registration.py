"""
Content Registration Example for PyFish

This example demonstrates how to register custom items, blocks, and recipes.
Place this file in pyfish/<namespace>/startup/content_registration.py

Note: STARTUP scripts run before registries freeze and can register content.
They cannot be hot-reloaded during gameplay.
"""

from pyfish import items, blocks, recipes, tags, tabs, log_print


def register_custom_items():
    """Register custom items."""
    # Register a simple item
    items.register_item("ruby", {
        "display_name": "Ruby",
        "max_stack_size": 64
    })

    # Register a food item
    items.register_food("magic_apple", 4, 2.4, {
        "display_name": "Magic Apple",
        "max_stack_size": 16
    })

    # Register a tool
    items.register_tool("ruby_pickaxe", "pickaxe", {
        "display_name": "Ruby Pickaxe",
        "attack_damage": 2.0,
        "attack_speed": -2.8,
        "durability": 1500
    })

    log_print("Registered custom items")


def register_custom_blocks():
    """Register custom blocks."""
    blocks.register_block("ruby_ore", {
        "display_name": "Ruby Ore",
        "hardness": 3.0,
        "resistance": 5.0,
        "requires_tool": True
    })

    blocks.register_block("ruby_block", {
        "display_name": "Ruby Block",
        "hardness": 5.0,
        "resistance": 10.0,
        "requires_tool": True
    })

    log_print("Registered custom blocks")


def register_recipes():
    """Register custom recipes."""
    # Shaped recipe for ruby block
    recipes.shaped("ruby_block", "pyfish:ruby_block", 1, [
        "RRR",
        "RRR",
        "RRR"
    ], {
        "R": "pyfish:ruby"
    })

    # Shapeless recipe for magic apple
    recipes.shapeless("magic_apple", "pyfish:magic_apple", 1, [
        "minecraft:apple",
        "minecraft:gold_ingot"
    ])

    # Shaped recipe for ruby pickaxe
    recipes.shaped("ruby_pickaxe", "pyfish:ruby_pickaxe", 1, [
        "RRR",
        " S ",
        " S "
    ], {
        "R": "pyfish:ruby",
        "S": "minecraft:stick"
    })

    log_print("Registered custom recipes")


def register_tags():
    """Register custom tags."""
    # Add ruby to common item tags
    tags.add_item_tag("forge:gems/ruby", "pyfish:ruby")
    tags.add_item_tag("c:gems/ruby", "pyfish:ruby")

    # Add ruby ore to common ore tags
    tags.add_block_tag("forge:ores/ruby", "pyfish:ruby_ore")
    tags.add_block_tag("c:ores/ruby", "pyfish:ruby_ore")

    log_print("Registered custom tags")


def register_creative_tab():
    """Register a custom creative tab."""
    tabs.create("pyfish", {
        "display_name": "PyFish Items",
        "icon_item": "pyfish:ruby"
    })

    log_print("Registered custom creative tab")


# Execute all registrations
register_custom_items()
register_custom_blocks()
register_recipes()
register_tags()
register_creative_tab()

log_print("Content registration complete!")
