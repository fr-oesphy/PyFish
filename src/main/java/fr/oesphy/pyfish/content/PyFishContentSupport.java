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
import net.minecraft.tags.BlockTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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

        Item modernItem = tryCreateModernTool(namespace, type, safeProperties, itemProperties, events);
        if (modernItem != null) {
            return modernItem;
        }

        Item legacyItem = tryCreateLegacyTool(namespace, type, safeProperties, itemProperties, events);
        if (legacyItem != null) {
            return legacyItem;
        }

        PyFish.LOGGER.warn("[PyFish/Content] Falling back to generic item implementation for tool {}:{}", namespace, path);
        return new ModularItem(itemProperties, events);
    }

    public static Block createBlock(String namespace, String path, Map<String, Object> properties) {
        return createBlock(namespace, path, properties, captureEvents(properties));
    }

    public static Block createBlock(String namespace, String path, Map<String, Object> properties,
            Map<String, Object> capturedEvents) {
        ModularBlock block = new ModularBlock(parseBlockProperties(namespace, path, properties),
                normalizeEvents(capturedEvents), properties == null ? Map.of() : properties);
        if (namespace != null && path != null) block.bindLootTable(namespace + ":" + path);
        return block;
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

        Map<String, Object> snapshot = new HashMap<>();
        properties.forEach((key, value) -> {
            if (key != null) {
                snapshot.put(key.toString(), snapshotValue(value));
            }
        });
        return snapshot;
    }

    private static Item tryCreateModernTool(String namespace, String type, Map<String, Object> properties,
            Item.Properties itemProperties, Map<String, Object> events) {
        try {
            Class<?> toolMaterialClass = Class.forName("net.minecraft.world.item.ToolMaterial");
            Object toolMaterial = createModernToolMaterial(namespace, properties);
            float attackDamage = ((Number) properties.getOrDefault("damage", 0.0f)).floatValue();
            float attackSpeed = ((Number) properties.getOrDefault("attackSpeed", -2.4f)).floatValue();

            Method builderMethod = findModernToolBuilder(itemProperties.getClass(), type);
            if (builderMethod == null) {
                return null;
            }

            Object[] arguments = switch (builderMethod.getName()) {
                case "tool" -> new Object[] { toolMaterial, mineableTagFor(type), attackDamage, attackSpeed, 1.0f };
                default -> new Object[] { toolMaterial, attackDamage, attackSpeed };
            };
            builderMethod.invoke(itemProperties, arguments);
            return new ModularItem(itemProperties, events);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception e) {
            PyFish.LOGGER.warn("[PyFish/Content] Modern tool creation failed for {}: {}", type, e.getMessage());
            return null;
        }
    }

    private static Item tryCreateLegacyTool(String namespace, String type, Map<String, Object> properties,
            Item.Properties itemProperties, Map<String, Object> events) {
        try {
            Tier tier = createLegacyTier(namespace, properties);
            float attackDamage = ((Number) properties.getOrDefault("damage", 0.0f)).floatValue();
            float attackSpeed = ((Number) properties.getOrDefault("attackSpeed", -2.4f)).floatValue();

            return switch (type.toLowerCase()) {
                case "sword" -> {
                    applyLegacyToolAttributes(SwordItem.class, itemProperties, tier, attackDamage, attackSpeed);
                    yield new ModularSwordItem(tier, itemProperties, events);
                }
                case "pickaxe" -> {
                    applyLegacyToolAttributes(PickaxeItem.class, itemProperties, tier, attackDamage, attackSpeed);
                    yield new ModularPickaxeItem(tier, itemProperties, events);
                }
                case "shovel" -> {
                    applyLegacyToolAttributes(ShovelItem.class, itemProperties, tier, attackDamage, attackSpeed);
                    yield new ModularShovelItem(tier, itemProperties, events);
                }
                case "axe" -> {
                    applyLegacyToolAttributes(AxeItem.class, itemProperties, tier, attackDamage, attackSpeed);
                    yield new ModularAxeItem(tier, itemProperties, events);
                }
                case "hoe" -> {
                    applyLegacyToolAttributes(HoeItem.class, itemProperties, tier, attackDamage, attackSpeed);
                    yield new ModularHoeItem(tier, itemProperties, events);
                }
                case "multitool" -> new ModularMultitool(tier, itemProperties, attackDamage, attackSpeed, events);
                default -> null;
            };
        } catch (Exception e) {
            PyFish.LOGGER.warn("[PyFish/Content] Legacy tool creation failed for {}: {}", type, e.getMessage());
        }
        return null;
    }

    private static Tier createLegacyTier(String namespace, Map<String, Object> properties) {
        int uses = ((Number) properties.getOrDefault("durability", 250)).intValue();
        float speed = ((Number) properties.getOrDefault("speed", 6.0f)).floatValue();
        float damageBonus = ((Number) properties.getOrDefault("damageBonus", 2.0f)).floatValue();
        int enchantmentValue = ((Number) properties.getOrDefault("enchantmentValue", 14)).intValue();

        String repairId = properties.containsKey("repairItem")
                ? PyFishResourceGenerator.resolveNamespacedString(properties.get("repairItem").toString(), namespace)
                : null;
        Supplier<Ingredient> repairIngredient = () -> {
            if (repairId == null) {
                return emptyIngredient();
            }
            Item repairItem = MinecraftCompat.registryGet(BuiltInRegistries.ITEM, repairId);
            return repairItem != null ? Ingredient.of(repairItem) : emptyIngredient();
        };

        TagKey<Block> incorrectBlocks = ContentProperties.incorrectBlocks(properties);

        try {
            Class<?> factoryClass = Class.forName("fr.oesphy.pyfish.content.PyFishLegacyTierFactory");
            Method createMethod = factoryClass.getMethod("create", int.class, float.class, float.class, int.class,
                    TagKey.class, Supplier.class);
            return (Tier) createMethod.invoke(null, uses, speed, damageBonus, enchantmentValue, incorrectBlocks,
                    repairIngredient);
        } catch (ReflectiveOperationException ignored) {
            // Fall back to the generic proxy implementation below when the
            // version-specific helper is not present in the active profile.
        }

        return (Tier) Proxy.newProxyInstance(
                PyFishContentSupport.class.getClassLoader(),
                new Class<?>[] { Tier.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUses" -> uses;
                    case "getSpeed" -> speed;
                    case "getAttackDamageBonus" -> damageBonus;
                    case "getIncorrectBlocksForDrops" -> incorrectBlocks;
                    case "getEnchantmentValue" -> enchantmentValue;
                    case "getRepairIngredient" -> repairIngredient.get();
                    case "toString" -> "PyFishLegacyTier[" + namespace + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static Object createModernToolMaterial(String namespace, Map<String, Object> properties) {
        int uses = ((Number) properties.getOrDefault("durability", 250)).intValue();
        float speed = ((Number) properties.getOrDefault("speed", 6.0f)).floatValue();
        float damageBonus = ((Number) properties.getOrDefault("damageBonus", 2.0f)).floatValue();
        int enchantmentValue = ((Number) properties.getOrDefault("enchantmentValue", 14)).intValue();

        String repairItem = properties.containsKey("repairItem")
                ? PyFishResourceGenerator.resolveNamespacedString(properties.get("repairItem").toString(), namespace)
                : "minecraft:iron_ingot";
        String repairTagId = namespace + ":repair/" + repairItem.replace(':', '_').replace('/', '_');
        PyFishResourceGenerator.addItemTag(repairTagId, repairItem);

        try {
            Class<?> toolMaterialClass = Class.forName("net.minecraft.world.item.ToolMaterial");
            Object repairTag = MinecraftCompat.createTagKey(Registries.ITEM, repairTagId);
            Constructor<?> constructor = toolMaterialClass.getConstructors()[0];
            return constructor.newInstance(ContentProperties.incorrectBlocks(properties), uses, speed, damageBonus,
                    enchantmentValue, repairTag);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to construct ToolMaterial", e);
        }
    }

    private static Method findModernToolBuilder(Class<?> propertiesClass, String type) {
        String methodName = switch (type.toLowerCase()) {
            case "pickaxe" -> "pickaxe";
            case "hoe" -> "hoe";
            case "shovel" -> "shovel";
            case "sword" -> "sword";
            case "axe", "multitool" -> "tool";
            default -> null;
        };
        if (methodName == null) {
            return null;
        }

        for (Method method : propertiesClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private static void applyLegacyToolAttributes(Class<?> toolClass, Item.Properties itemProperties, Object tier,
            float attackDamage, float attackSpeed) {
        try {
            for (Method method : toolClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 3) {
                    continue;
                }
                if (!method.getReturnType().getName().contains("ItemAttributeModifiers")) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!parameterTypes[0].isInstance(tier)
                        && !parameterTypes[0].isAssignableFrom(tier.getClass())) {
                    continue;
                }
                if (!isFloatLike(parameterTypes[1]) || !isFloatLike(parameterTypes[2])) {
                    continue;
                }

                Object attributes = method.invoke(null, tier, attackDamage, attackSpeed);
                Method propertiesMethod = findItemPropertiesMutationMethod(itemProperties.getClass(), attributes.getClass());
                propertiesMethod.invoke(itemProperties, attributes);
                return;
            }
        } catch (Exception e) {
            PyFish.LOGGER.debug("[PyFish/Content] Skipped legacy tool attributes: {}", e.getMessage());
        }
    }

    private static Method findItemPropertiesMutationMethod(Class<?> propertiesClass, Class<?> argumentType)
            throws NoSuchMethodException {
        Method compatibleMatch = null;

        for (Method method : propertiesClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(argumentType)) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            if (returnType != Void.TYPE && !propertiesClass.isAssignableFrom(returnType)) {
                continue;
            }

            if (parameterType.equals(argumentType)) {
                return method;
            }

            if (compatibleMatch == null) {
                compatibleMatch = method;
            }
        }

        if (compatibleMatch != null) {
            return compatibleMatch;
        }

        throw new NoSuchMethodException("No compatible item properties mutation method found for "
                + argumentType.getName());
    }

    private static boolean isFloatLike(Class<?> type) {
        return type == float.class || type == Float.class;
    }

    private static Ingredient emptyIngredient() {
        try {
            Field field = Ingredient.class.getField("EMPTY");
            Object value = field.get(null);
            if (value instanceof Ingredient ingredient) {
                return ingredient;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = Ingredient.class.getMethod("of");
            Object value = method.invoke(null);
            if (value instanceof Ingredient ingredient) {
                return ingredient;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return Ingredient.of(new Item[0]);
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Block> mineableTagFor(String type) {
        return switch (type.toLowerCase()) {
            case "shovel" -> BlockTags.MINEABLE_WITH_SHOVEL;
            case "axe" -> BlockTags.MINEABLE_WITH_AXE;
            case "hoe" -> BlockTags.MINEABLE_WITH_HOE;
            default -> BlockTags.MINEABLE_WITH_PICKAXE;
        };
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
                Map<String, Object> snapshot = new HashMap<>();
                for (String member : polyglotValue.getMemberKeys()) {
                    snapshot.put(member, snapshotValue(polyglotValue.getMember(member)));
                }
                return snapshot;
            }
            return polyglotValue;
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> snapshot = new HashMap<>();
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
