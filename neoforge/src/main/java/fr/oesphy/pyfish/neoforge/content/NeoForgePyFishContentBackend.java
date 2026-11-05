package fr.oesphy.pyfish.neoforge.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.OreBiomeSelection;
import fr.oesphy.pyfish.content.PyFishContentBackend;
import fr.oesphy.pyfish.content.PyFishContentSupport;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class NeoForgePyFishContentBackend implements PyFishContentBackend {
    private final IEventBus modEventBus;
    // DeferredRegister is namespace-bound, so Python mods cannot safely share the PyFish
    // register. Lazily creating one per namespace also avoids empty registrations.
    private final Map<String, DeferredRegister.Items> itemRegisters = new LinkedHashMap<>();
    private final Map<String, DeferredRegister.Blocks> blockRegisters = new LinkedHashMap<>();
    private final Map<String, DeferredRegister.Items> blockItemRegisters = new LinkedHashMap<>();
    private final Map<String, DeferredRegister<CreativeModeTab>> tabRegisters = new LinkedHashMap<>();
    private final Map<String, List<Supplier<? extends Item>>> tabMembers = new ConcurrentHashMap<>();
    private final Set<String> registeredIds = ConcurrentHashMap.newKeySet();

    @Override
    public void registerOrePlacement(String featureId, OreBiomeSelection biomes) {
        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", "neoforge:add_features");
        if (biomes.kind() == OreBiomeSelection.Kind.BIOMES) {
            JsonArray ids = new JsonArray();
            biomes.values().forEach(ids::add);
            modifier.add("biomes", ids);
        } else {
            String selector = switch (biomes.kind()) {
                case OVERWORLD -> "#c:is_overworld";
                case NETHER -> "#c:is_nether";
                case END -> "#c:is_end";
                case TAG -> "#" + biomes.values().get(0);
                case BIOMES -> throw new IllegalStateException("Handled above");
            };
            modifier.addProperty("biomes", selector);
        }
        modifier.addProperty("features", featureId);
        modifier.addProperty("step", "underground_ores");
        String namespace = PyFishResourceGenerator.namespaceOf(featureId);
        String path = PyFishResourceGenerator.pathOf(featureId);
        PyFishResourceGenerator.addDataTextResource(namespace + ":neoforge/biome_modifier/" + path + ".json",
                modifier.toString());
    }

    public NeoForgePyFishContentBackend(IEventBus modEventBus) {
        this.modEventBus = modEventBus;
    }

    @Override
    public void registerItem(String namespace, String path, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("item:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate item registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        DeferredHolder<Item, Item> holder = items(namespace).register(path,
                () -> PyFishContentSupport.createItem(namespace, path, propertySnapshot, capturedEvents));
        handleTab(holder, namespace, propertySnapshot);
        PyFishContentSupport.generateItemResources(id, propertySnapshot, false);
        PyFish.LOGGER.info("[PyFish/Content] Registered NeoForge item {}", id);
    }

    @Override
    public void registerFood(String namespace, String path, int nutrition, double saturation, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("item:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate food registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        DeferredHolder<Item, Item> holder = items(namespace).register(path,
                () -> PyFishContentSupport.createFood(namespace, path, nutrition, saturation, propertySnapshot, capturedEvents));
        handleTab(holder, namespace, propertySnapshot);
        PyFishContentSupport.generateItemResources(id, propertySnapshot, false);
        PyFish.LOGGER.info("[PyFish/Content] Registered NeoForge food {}", id);
    }

    @Override
    public void registerTool(String namespace, String path, String type, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("item:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate tool registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        DeferredHolder<Item, Item> holder = items(namespace).register(path,
                () -> PyFishContentSupport.createTool(namespace, path, type, propertySnapshot, capturedEvents));
        handleTab(holder, namespace, propertySnapshot);
        PyFishContentSupport.handleToolTags(id, type);
        PyFishContentSupport.generateItemResources(id, propertySnapshot, true);
        PyFish.LOGGER.info("[PyFish/Content] Registered NeoForge tool {} ({})", id, type);
    }

    @Override
    public void registerBlock(String namespace, String path, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("block:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate block registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        Map<String, Object> capturedEvents = PyFishContentSupport.captureEvents(properties);
        var blockHolder = blocks(namespace).register(path,
                () -> PyFishContentSupport.createBlock(namespace, path, propertySnapshot, capturedEvents));
        DeferredHolder<Item, Item> itemHolder = blockItems(namespace).register(path,
                () -> PyFishContentSupport.createBlockItem(namespace, path, blockHolder.get()));

        handleTab(itemHolder, namespace, propertySnapshot);
        PyFishContentSupport.generateBlockResources(id, propertySnapshot);
        PyFish.LOGGER.info("[PyFish/Content] Registered NeoForge block {}", id);
    }

    @Override
    public void registerTab(String namespace, String path, Map<String, Object> properties) {
        String id = namespace + ":" + path;
        if (!registeredIds.add("tab:" + id)) {
            PyFish.LOGGER.warn("[PyFish/Content] Duplicate creative tab registration skipped for {}", id);
            return;
        }

        Map<String, Object> propertySnapshot = PyFishContentSupport.snapshotProperties(properties);
        String displayName = propertySnapshot.get("displayName") != null
                ? propertySnapshot.get("displayName").toString()
                : path;
        String iconId = propertySnapshot.get("icon") != null
                ? PyFishResourceGenerator.resolveIdentifier(propertySnapshot.get("icon").toString(), namespace)
                : "minecraft:diamond";

        tabs(namespace).register(path, () -> MinecraftCompat.createCreativeModeTab(
                displayName,
                () -> MinecraftCompat.createItemStack(MinecraftCompat.registryGet(BuiltInRegistries.ITEM, iconId))));

        PyFish.LOGGER.info("[PyFish/Content] Registered NeoForge creative tab {}", id);
    }

    public void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        String tabId = MinecraftCompat.resourceKeyIdString(event.getTabKey());
        List<Supplier<? extends Item>> members = tabMembers.get(tabId);
        if (members == null) {
            return;
        }

        members.forEach(supplier -> event.accept(supplier.get()));
    }

    public void onAddPackFinders(AddPackFindersEvent event) {
        if (!PyFishResourceGenerator.hasResources(event.getPackType())) {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                "pyfish_generated_" + event.getPackType().name().toLowerCase(),
                Component.literal("PyFish Generated Resources (" + event.getPackType().name() + ")"),
                PackSource.BUILT_IN,
                Optional.empty());

        PyFishNeoForgeRuntimePackResources resources = new PyFishNeoForgeRuntimePackResources(location,
                event.getPackType());
        Pack pack = Pack.readMetaAndCreate(
                location,
                new Pack.ResourcesSupplier() {
                    @Override
                    public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) {
                        return resources;
                    }

                    @Override
                    public net.minecraft.server.packs.PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                        return resources;
                    }
                },
                event.getPackType(),
                new net.minecraft.server.packs.PackSelectionConfig(true, Pack.Position.TOP, true));

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private DeferredRegister.Items items(String namespace) {
        return itemRegisters.computeIfAbsent(namespace, key -> {
            DeferredRegister.Items register = DeferredRegister.createItems(namespace);
            register.register(modEventBus);
            return register;
        });
    }

    private DeferredRegister.Blocks blocks(String namespace) {
        return blockRegisters.computeIfAbsent(namespace, key -> {
            DeferredRegister.Blocks register = DeferredRegister.createBlocks(namespace);
            register.register(modEventBus);
            return register;
        });
    }

    private DeferredRegister.Items blockItems(String namespace) {
        return blockItemRegisters.computeIfAbsent(namespace, key -> {
            DeferredRegister.Items register = DeferredRegister.createItems(namespace);
            register.register(modEventBus);
            return register;
        });
    }

    private DeferredRegister<CreativeModeTab> tabs(String namespace) {
        return tabRegisters.computeIfAbsent(namespace, key -> {
            DeferredRegister<CreativeModeTab> register = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, namespace);
            register.register(modEventBus);
            return register;
        });
    }

    private void handleTab(DeferredHolder<Item, ? extends Item> holder, String namespace, Map<String, Object> properties) {
        String tabId = PyFishContentSupport.resolveTabId(namespace, properties);
        if (tabId == null) {
            return;
        }

        // Retain the holder as a supplier: resolving it while scripts declare content is too
        // early in NeoForge's registry lifecycle.
        tabMembers.computeIfAbsent(tabId, ignored -> new ArrayList<>()).add(holder);
    }
}
