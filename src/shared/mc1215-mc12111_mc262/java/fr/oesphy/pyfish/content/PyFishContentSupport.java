package fr.oesphy.pyfish.content;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.block.ModularBlock;
import fr.oesphy.pyfish.content.item.ModularAxeItem;
import fr.oesphy.pyfish.content.item.ModularHoeItem;
import fr.oesphy.pyfish.content.item.ModularItem;
import fr.oesphy.pyfish.content.item.ModularMultitool;
import fr.oesphy.pyfish.content.item.ModularPickaxeItem;
import fr.oesphy.pyfish.content.item.ModularShovelItem;
import fr.oesphy.pyfish.content.item.ModularSwordItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PyFishContentSupport {
    private PyFishContentSupport() {
    }

    public static Item createItem(String namespace, String path, Map<String, Object> properties) {
        return createItem(namespace, path, properties, captureEvents(properties));
    }

    public static Item createItem(String namespace, String path, Map<String, Object> properties,
            Map<String, Object> capturedEvents) {
        return new ModularItem(parseItemProperties(namespace, path, properties), normalizeEvents(capturedEvents));
    }

    public static Item createFood(String namespace, String path, int nutrition, double saturation,
            Map<String, Object> properties) {
        return createFood(namespace, path, nutrition, saturation, properties, captureEvents(properties));
    }

    public static Item createFood(String namespace, String path, int nutrition, double saturation,
            Map<String, Object> properties, Map<String, Object> capturedEvents) {
        Map<String, Object> safeProperties = properties == null ? Collections.emptyMap() : properties;
        Item.Properties itemProperties = parseItemProperties(namespace, path, safeProperties);
        FoodProperties.Builder food = new FoodProperties.Builder()
                .nutrition(nutrition)
                .saturationModifier((float) saturation);
        if (Boolean.TRUE.equals(safeProperties.get("alwaysEdible"))) {
            food.alwaysEdible();
        }
        itemProperties.food(food.build());
        return new ModularItem(itemProperties, normalizeEvents(capturedEvents));
    }

    public static Item createTool(String namespace, String path, String type, Map<String, Object> properties) {
        return createTool(namespace, path, type, properties, captureEvents(properties));
    }

    public static Item createTool(String namespace, String path, String type, Map<String, Object> properties,
            Map<String, Object> capturedEvents) {
        Map<String, Object> safeProperties = properties == null ? Collections.emptyMap() : properties;
        Map<String, Object> events = normalizeEvents(capturedEvents);
        Item.Properties itemProperties = parseItemProperties(namespace, path, safeProperties);
        ToolMaterial toolMaterial = createToolMaterial(namespace, safeProperties);
        float attackDamage = ((Number) safeProperties.getOrDefault("damage", 0.0f)).floatValue();
        float attackSpeed = ((Number) safeProperties.getOrDefault("attackSpeed", -2.4f)).floatValue();

        return switch (type.toLowerCase()) {
            case "sword" -> new ModularSwordItem(toolMaterial, attackDamage, attackSpeed, itemProperties, events);
            case "pickaxe" -> new ModularPickaxeItem(toolMaterial, attackDamage, attackSpeed, itemProperties, events);
            case "axe" -> new ModularAxeItem(toolMaterial, attackDamage, attackSpeed, itemProperties, events);
            case "hoe" -> new ModularHoeItem(toolMaterial, attackDamage, attackSpeed, itemProperties, events);
            case "shovel" -> new ModularShovelItem(toolMaterial, attackDamage, attackSpeed, itemProperties, events);
            case "multitool" -> new ModularMultitool(toolMaterial, itemProperties, attackDamage, attackSpeed, events);
            default -> {
                PyFish.LOGGER.warn("[PyFish/Content] Falling back to generic item implementation for tool {}:{}", namespace, path);
                yield new ModularItem(itemProperties, events);
            }
        };
    }

    public static Block createBlock(String namespace, String path, Map<String, Object> properties) {
        return createBlock(namespace, path, properties, captureEvents(properties));
    }

    public static Block createBlock(String namespace, String path, Map<String, Object> properties,
            Map<String, Object> capturedEvents) {
        return new ModularBlock(parseBlockProperties(namespace, path, properties), normalizeEvents(capturedEvents), properties == null ? Map.of() : properties);
    }

    public static Block createBlock(Map<String, Object> properties) {
        return createBlock(properties, captureEvents(properties));
    }

    public static Block createBlock(Map<String, Object> properties, Map<String, Object> capturedEvents) {
        return new ModularBlock(parseBlockProperties(properties), normalizeEvents(capturedEvents), properties == null ? Map.of() : properties);
    }

    public static BlockItem createBlockItem(String namespace, String path, Block block) {
        return createBlockItem(namespace, path, block, Map.of());
    }

    public static BlockItem createBlockItem(String namespace, String path, Block block, Map<String, Object> properties) {
        return new BlockItem(block, parseItemProperties(namespace, path, properties));
    }

    public static BlockItem createBlockItem(Block block) {
        return new BlockItem(block, new Item.Properties());
    }

    public static Item.Properties parseItemProperties(String namespace, String path, Map<String, Object> properties) {
        Item.Properties itemProperties = new Item.Properties();
        bindPropertyId(itemProperties, Registries.ITEM, namespace, path);
        if (properties == null) {
            return itemProperties;
        }

        if (properties.containsKey("stacksTo")) {
            itemProperties.stacksTo(((Number) properties.get("stacksTo")).intValue());
        }
        if (Boolean.TRUE.equals(properties.get("fireResistant"))) {
            itemProperties.fireResistant();
        }
        if (properties.get("durability") instanceof Number durability && durability.intValue() > 0) {
            itemProperties.durability(((Number) properties.get("durability")).intValue());
        }

        return itemProperties;
    }

    public static Item.Properties parseItemProperties(Map<String, Object> properties) {
        return parseItemProperties(null, null, properties);
    }

    public static BlockBehaviour.Properties parseBlockProperties(String namespace, String path, Map<String, Object> properties) {
        BlockBehaviour.Properties blockProperties = BlockBehaviour.Properties.of();
        bindPropertyId(blockProperties, Registries.BLOCK, namespace, path);
        if (properties == null) {
            return ContentProperties.applyBlock(blockProperties, properties);
        }

        if (properties.containsKey("destroyTime")) {
            blockProperties.destroyTime(((Number) properties.get("destroyTime")).floatValue());
        }
        if (properties.containsKey("explosionResistance")) {
            blockProperties.explosionResistance(((Number) properties.get("explosionResistance")).floatValue());
        }
        if (Boolean.TRUE.equals(properties.get("requiresCorrectToolForDrops"))) {
            blockProperties.requiresCorrectToolForDrops();
        }

        return ContentProperties.applyBlock(blockProperties, properties);
    }

    public static BlockBehaviour.Properties parseBlockProperties(Map<String, Object> properties) {
        return parseBlockProperties(null, null, properties);
    }

    public static void handleToolTags(String itemId, String type) {
        String resolvedId = PyFishResourceGenerator.resolveIdentifier(itemId, PyFish.MODID);
        PyFishResourceGenerator.addItemTag("minecraft:enchantable/vanishing", resolvedId);
        PyFishResourceGenerator.addItemTag("minecraft:enchantable/durability", resolvedId);

        switch (type.toLowerCase()) {
            case "sword" -> {
                PyFishResourceGenerator.addItemTag("minecraft:swords", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/weapon", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/sharp_weapon", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/fire_aspect", resolvedId);
            }
            case "pickaxe" -> {
                PyFishResourceGenerator.addItemTag("minecraft:pickaxes", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/mining", resolvedId);
            }
            case "shovel" -> {
                PyFishResourceGenerator.addItemTag("minecraft:shovels", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/mining", resolvedId);
            }
            case "axe" -> {
                PyFishResourceGenerator.addItemTag("minecraft:axes", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/mining", resolvedId);
            }
            case "hoe" -> {
                PyFishResourceGenerator.addItemTag("minecraft:hoes", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/mining", resolvedId);
            }
            case "multitool" -> {
                PyFishResourceGenerator.addItemTag("minecraft:swords", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:pickaxes", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:axes", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:shovels", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:hoes", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/mining", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/weapon", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/sharp_weapon", resolvedId);
                PyFishResourceGenerator.addItemTag("minecraft:enchantable/fire_aspect", resolvedId);
            }
            default -> {
            }
        }
    }

    public static String resolveTabId(String namespace, Map<String, Object> properties) {
        Object raw = properties != null ? properties.get("tab") : null;
        if (raw == null) {
            return null;
        }
        return PyFishResourceGenerator.resolveNamespacedString(raw.toString(), namespace);
    }

    public static void generateItemResources(String itemId, Map<String, Object> properties, boolean toolLike) {
        String resolvedId = PyFishResourceGenerator.resolveIdentifier(itemId, PyFish.MODID);
        String namespace = PyFishResourceGenerator.namespaceOf(resolvedId);
        String texture = null;
        String parent = null;
        if (properties != null) {
            texture = PyFishResourceGenerator.resolveTextureReference((String) properties.get("texture"), namespace);
            parent = (String) properties.get("model");
        }

        if (toolLike && parent == null) {
            parent = "minecraft:item/handheld";
        }

        PyFishResourceGenerator.generateItemModel(resolvedId, texture, parent);
    }

    public static void generateBlockResources(String blockId, Map<String, Object> properties) {
        String resolvedId = PyFishResourceGenerator.resolveIdentifier(blockId, PyFish.MODID);
        PyFishResourceGenerator.generateBlockResources(resolvedId, properties == null ? Map.of() : properties);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractEvents(Map<String, Object> properties) {
        return captureEvents(properties);
    }

    public static Map<String, Object> captureEvents(Map<String, Object> properties) {
        if (properties == null) {
            return Collections.emptyMap();
        }

        Object rawEvents = properties.get("events");
        Map<String, Object> events = new CapturedEvents();
        if (rawEvents instanceof Map<?, ?> rawMap) {
            rawMap.forEach((key, value) -> captureEventCandidate(events, key, value));
            return events;
        }

        if (rawEvents instanceof Value polyglotEvents && polyglotEvents.hasMembers()) {
            for (String key : polyglotEvents.getMemberKeys()) {
                captureEventCandidate(events, key, polyglotEvents.getMember(key));
            }
            return events;
        }

        return Collections.emptyMap();
    }

    private static void captureEventCandidate(Map<String, Object> events, Object key, Object value) {
        if (key == null || value == null) {
            return;
        }

        if (value instanceof Value polyglotValue && polyglotValue.canExecute()) {
            events.put(key.toString(), polyglotValue);
            return;
        }

        try {
            Value captured = Context.getCurrent().asValue(value);
            if (captured.canExecute()) {
                events.put(key.toString(), captured);
            }
        } catch (Exception e) {
            PyFish.LOGGER.warn("[PyFish/Content] Failed to capture Python handler '{}' : {}", key, e.getMessage());
        }
    }

    public static Map<String, Object> snapshotProperties(Map<String, Object> properties) {
        if (properties == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (key != null) {
                snapshot.put(key.toString(), snapshotValue(value));
            }
        });
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static ToolMaterial createToolMaterial(String namespace, Map<String, Object> properties) {
        int uses = ((Number) properties.getOrDefault("durability", 250)).intValue();
        float speed = ((Number) properties.getOrDefault("speed", 6.0f)).floatValue();
        float damageBonus = ((Number) properties.getOrDefault("damageBonus", 2.0f)).floatValue();
        int enchantmentValue = ((Number) properties.getOrDefault("enchantmentValue", 14)).intValue();

        String repairItem = properties.containsKey("repairItem")
                ? PyFishResourceGenerator.resolveNamespacedString(properties.get("repairItem").toString(), namespace)
                : "minecraft:iron_ingot";
        String repairTagId = namespace + ":repair/" + repairItem.replace(':', '_').replace('/', '_');
        PyFishResourceGenerator.addItemTag(repairTagId, repairItem);

        TagKey<Item> repairTag = (TagKey<Item>) MinecraftCompat.createTagKey(Registries.ITEM, repairTagId);
        return new ToolMaterial(ContentProperties.incorrectBlocks(properties), uses, speed, damageBonus, enchantmentValue, repairTag);
    }

    private static Map<String, Object> normalizeEvents(Map<String, Object> capturedEvents) {
        return capturedEvents == null ? Collections.emptyMap() : capturedEvents;
    }

    private static void bindPropertyId(Object properties, Object registryKey, String namespace, String path) {
        if (namespace == null || namespace.isBlank() || path == null || path.isBlank()) {
            return;
        }
        MinecraftCompat.bindPropertyIdIfPresent(properties, registryKey, namespace + ":" + path);
    }

    private static Object snapshotValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Value polyglotValue) {
            if (polyglotValue.canExecute()) {
                return polyglotValue;
            }
            if (polyglotValue.isNull()) {
                return null;
            }
            if (polyglotValue.isBoolean()) {
                return polyglotValue.asBoolean();
            }
            if (polyglotValue.isString()) {
                return polyglotValue.asString();
            }
            if (polyglotValue.fitsInInt()) {
                return polyglotValue.asInt();
            }
            if (polyglotValue.fitsInLong()) {
                return polyglotValue.asLong();
            }
            if (polyglotValue.fitsInDouble()) {
                return polyglotValue.asDouble();
            }
            if (polyglotValue.hasArrayElements()) {
                List<Object> snapshot = new ArrayList<>();
                long size = polyglotValue.getArraySize();
                for (long index = 0; index < size; index++) {
                    snapshot.add(snapshotValue(polyglotValue.getArrayElement(index)));
                }
                return snapshot;
            }
            if (polyglotValue.hasMembers()) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                for (String member : polyglotValue.getMemberKeys()) {
                    snapshot.put(member, snapshotValue(polyglotValue.getMember(member)));
                }
                return snapshot;
            }
            return polyglotValue;
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> {
                if (key != null) {
                    snapshot.put(key.toString(), snapshotValue(entryValue));
                }
            });
            return snapshot;
        }

        if (value instanceof Iterable<?> iterable) {
            List<Object> snapshot = new ArrayList<>();
            iterable.forEach(entry -> snapshot.add(snapshotValue(entry)));
            return snapshot;
        }

        return value;
    }
}
