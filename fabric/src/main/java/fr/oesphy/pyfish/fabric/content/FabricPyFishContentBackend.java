package fr.oesphy.pyfish.fabric.content;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.OreBiomeSelection;
import fr.oesphy.pyfish.content.PyFishContentBackend;
import fr.oesphy.pyfish.content.PyFishContentSupport;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import fr.oesphy.pyfish.fabric.compat.FabricVersionCompat;
import fr.oesphy.pyfish.mixin.MappedRegistryAccessor;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class FabricPyFishContentBackend implements PyFishContentBackend {
    private final Set<String> registeredIds = ConcurrentHashMap.newKeySet();
    private final Map<String, ResourceKey<CreativeModeTab>> tabTargets = new ConcurrentHashMap<>();

    @Override
    public void registerOrePlacement(String featureId, OreBiomeSelection biomes) {
        Predicate<BiomeSelectionContext> selector = switch (biomes.kind()) {
            case OVERWORLD -> BiomeSelectors.foundInOverworld();
            case NETHER -> BiomeSelectors.foundInTheNether();
            case END -> BiomeSelectors.foundInTheEnd();
            case TAG -> {
                TagKey<Biome> tag = MinecraftCompat.createTagKey(Registries.BIOME, biomes.values().get(0));
                yield BiomeSelectors.tag(tag);
            }
            case BIOMES -> {
                List<ResourceKey<Biome>> keys = biomes.values().stream()
                        .map(id -> MinecraftCompat.<ResourceKey<Biome>>createResourceKey(Registries.BIOME, id))
                        .toList();
                yield BiomeSelectors.includeByKey(keys);
            }
        };
        ResourceKey<PlacedFeature> feature = MinecraftCompat.createResourceKey(Registries.PLACED_FEATURE, featureId);
        BiomeModifications.addFeature(selector, GenerationStep.Decoration.UNDERGROUND_ORES, feature);
    }

    @Override
    public void registerItem(String namespace, String path, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("item:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate Fabric item registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        Item item = PyFishContentSupport.createItem(namespace, path, propertySnapshot, capturedEvents);
        registerBuiltIn(BuiltInRegistries.ITEM, id, item);
        attachToTab(item, namespace, propertySnapshot);
        PyFishContentSupport.generateItemResources(id, propertySnapshot, false);
        PyFish.LOGGER.info("[PyFish/Content] Registered Fabric item {}", id);
    }

    @Override
    public void registerFood(String namespace, String path, int nutrition, double saturation, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("item:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate Fabric food registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        Item item = PyFishContentSupport.createFood(namespace, path, nutrition, saturation, propertySnapshot, capturedEvents);
        registerBuiltIn(BuiltInRegistries.ITEM, id, item);
        attachToTab(item, namespace, propertySnapshot);
        PyFishContentSupport.generateItemResources(id, propertySnapshot, false);
        PyFish.LOGGER.info("[PyFish/Content] Registered Fabric food {}", id);
    }

    @Override
    public void registerTool(String namespace, String path, String type, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("item:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate Fabric tool registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        Item item = PyFishContentSupport.createTool(namespace, path, type, propertySnapshot, capturedEvents);
        registerBuiltIn(BuiltInRegistries.ITEM, id, item);
        attachToTab(item, namespace, propertySnapshot);
        PyFishContentSupport.handleToolTags(id, type);
        PyFishContentSupport.generateItemResources(id, propertySnapshot, true);
        PyFish.LOGGER.info("[PyFish/Content] Registered Fabric tool {} ({})", id, type);
    }

    @Override
    public void registerBlock(String namespace, String path, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("block:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate Fabric block registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        var block = PyFishContentSupport.createBlock(namespace, path, propertySnapshot, capturedEvents);
        registerBuiltIn(BuiltInRegistries.BLOCK, id, block);

        var blockItem = PyFishContentSupport.createBlockItem(namespace, path, block, propertySnapshot);
        registerBuiltIn(BuiltInRegistries.ITEM, id, blockItem);
        attachToTab(blockItem, namespace, propertySnapshot);

        PyFishContentSupport.generateBlockResources(id, propertySnapshot);
        PyFish.LOGGER.info("[PyFish/Content] Registered Fabric block {}", id);
    }

    @Override
    public void registerTab(String namespace, String path, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("tab:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate Fabric creative tab registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        String displayName = propertySnapshot.get("displayName") != null
                ? propertySnapshot.get("displayName").toString()
                : path;
        String iconId = propertySnapshot.get("icon") != null
                ? PyFishResourceGenerator.resolveIdentifier(propertySnapshot.get("icon").toString(), namespace)
                : "minecraft:diamond";

        CreativeModeTab tab = FabricVersionCompat.tabBuilder()
                .title(Component.literal(displayName))
                .icon(() -> MinecraftCompat.createItemStack(MinecraftCompat.registryGet(BuiltInRegistries.ITEM, iconId)))
                // Fabric assigns custom tab placement through its own item group builder internals.
                .displayItems((parameters, output) -> {
                })
                .build();

        try {
            ResourceKey<CreativeModeTab> tabKey = createTabKey(id);
            registerBuiltIn(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
            tabTargets.put(id, tabKey);
            PyFish.LOGGER.info("[PyFish/Content] Registered Fabric creative tab {}", id);
        } catch (RuntimeException exception) {
            ResourceKey<CreativeModeTab> fallbackKey = resolveFallbackTabKey(propertySnapshot);
            tabTargets.put(id, fallbackKey);
            PyFish.LOGGER.warn(
                    "[PyFish/Content] Fabric refused runtime creative tab {}. Falling back to {} so content remains accessible.",
                    id, MinecraftCompat.resourceKeyIdString(fallbackKey), exception);
        }
    }

    private void attachToTab(Item item, String namespace, Map<String, Object> properties) {
        String tabId = PyFishContentSupport.resolveTabId(namespace, properties);
        if (tabId == null) {
            return;
        }

        ResourceKey<CreativeModeTab> tabKey = tabTargets.get(tabId);
        if (tabKey == null) {
            try {
                tabKey = createTabKey(tabId);
            } catch (RuntimeException exception) {
                tabKey = CreativeModeTabs.INGREDIENTS;
                PyFish.LOGGER.warn(
                        "[PyFish/Content] Fabric could not resolve tab {} at runtime. Falling back to {}.",
                        tabId, MinecraftCompat.resourceKeyIdString(tabKey), exception);
            }
        }
        FabricVersionCompat.addToTab(tabKey, item);
    }

    private ResourceKey<CreativeModeTab> createTabKey(String id) {
        @SuppressWarnings("unchecked")
        ResourceKey<CreativeModeTab> tabKey = MinecraftCompat.createResourceKey(Registries.CREATIVE_MODE_TAB, id);
        return tabKey;
    }

    private ResourceKey<CreativeModeTab> resolveFallbackTabKey(Map<String, Object> properties) {
        Object rawFallback = properties.get("fabricFallbackTab");
        if (rawFallback instanceof String fallback && !fallback.isBlank()) {
            return createTabKey(PyFishResourceGenerator.resolveNamespacedString(fallback, "minecraft"));
        }

        return CreativeModeTabs.INGREDIENTS;
    }

    private <T> T registerBuiltIn(Object registry, String id, T value) {
        MappedRegistryAccessor accessor = (MappedRegistryAccessor) registry;
        boolean previousFrozen = accessor.pyfish$isFrozen();
        // PyFish entrypoints run after Fabric has frozen built-ins. Keep the writable window
        // to one registration and restore the exact prior state, including unusual dev runs.
        accessor.pyfish$setFrozen(false);

        try {
            return MinecraftCompat.registryRegister(registry, id, value);
        } finally {
            accessor.pyfish$setFrozen(previousFrozen);
        }
    }
}
