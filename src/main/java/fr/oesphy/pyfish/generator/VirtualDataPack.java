package fr.oesphy.pyfish.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;

/**
 * Python-friendly facade for adding <em>server-side</em> data resources
 * (recipes, loot tables, tags, advancements) to the virtual data pack
 * managed by {@link PyFishResourceGenerator}.
 *
 * <h3>Example usage in a Python script</h3>
 * <pre>{@code
 * # Shapeless crafting recipe
 * data_pack.add_recipe("pyfish:ruby_nugget", {
 *     "type": "minecraft:crafting_shapeless",
 *     "ingredients": [{"item": "pyfish:ruby"}],
 *     "result": {"id": "pyfish:ruby_nugget", "count": 9}
 * })
 *
 * # Item tag
 * data_pack.add_tag("item", "c:gems", ["pyfish:ruby"])
 * }</pre>
 *
 * @see VirtualAssetPack
 * @see PyFishResourceGenerator
 */
public final class VirtualDataPack {

    /** Shared instance exposed as the {@code data_pack} binding. */
    public static final VirtualDataPack INSTANCE = new VirtualDataPack();

    private static final Logger LOGGER = PyFish.LOGGER;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    // -------------------------------------------------------------------------
    // Recipe helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a recipe JSON.
     *
     * @param id   recipe resource location, e.g. {@code "pyfish:ruby_nugget"}
     * @param json JSON string or Python dict
     */
    public void add_recipe(String id, Object json) {
        String resolved = PyFishResourceGenerator.resolveNamespacedString(id, PyFish.MODID);
        int colon = resolved.indexOf(':');
        String ns = colon >= 0 ? resolved.substring(0, colon) : PyFish.MODID;
        String path = colon >= 0 ? resolved.substring(colon + 1) : resolved;
        String location = ns + ":recipe/" + path + ".json";
        addDataJson(location, json);
    }

    // -------------------------------------------------------------------------
    // Loot table helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a loot table JSON.
     *
     * @param id   loot table resource location, e.g. {@code "pyfish:blocks/ruby_block"}
     * @param json JSON string or Python dict
     */
    public void add_loot_table(String id, Object json) {
        String resolved = PyFishResourceGenerator.resolveNamespacedString(id, PyFish.MODID);
        int colon = resolved.indexOf(':');
        String ns = colon >= 0 ? resolved.substring(0, colon) : PyFish.MODID;
        String path = colon >= 0 ? resolved.substring(colon + 1) : resolved;
        String location = ns + ":loot_table/" + path + ".json";
        addDataJson(location, json);
    }

    // -------------------------------------------------------------------------
    // Tag helpers
    // -------------------------------------------------------------------------

    /**
     * Adds entries to an item or block tag.
     *
     * @param registryType {@code "item"} or {@code "block"} (or any other registry folder)
     * @param tagId        tag resource location, e.g. {@code "c:gems"}
     * @param entries      collection of item/block resource location strings
     */
    public void add_tag(String registryType, String tagId, Collection<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (String entry : entries) {
            if ("item".equals(registryType)) {
                PyFishResourceGenerator.addItemTag(tagId, entry);
            } else if ("block".equals(registryType)) {
                PyFishResourceGenerator.addBlockTag(tagId, entry);
            } else {
                // For other registry types (entity_type, etc.), write raw JSON
                String resolved = PyFishResourceGenerator.resolveNamespacedString(tagId, PyFish.MODID);
                int colon = resolved.indexOf(':');
                String ns = colon >= 0 ? resolved.substring(0, colon) : PyFish.MODID;
                String path = colon >= 0 ? resolved.substring(colon + 1) : resolved;
                // We only support item and block through the generator directly;
                // log a warning for other types
                LOGGER.warn("[PyFish/VirtualDataPack] add_tag: unsupported registry type '{}'. " +
                        "Use add_raw_data_resource for other tag types.", registryType);
                return;
            }
        }
        LOGGER.debug("[PyFish/VirtualDataPack] Added {} entries to {} tag: {}", entries.size(), registryType, tagId);
    }

    /**
     * Adds an entry to an item tag.
     *
     * @param tagId tag resource location
     * @param entry item resource location to add
     */
    public void add_item_tag(String tagId, String entry) {
        PyFishResourceGenerator.addItemTag(tagId, entry);
        LOGGER.debug("[PyFish/VirtualDataPack] Added {} to item tag {}", entry, tagId);
    }

    /**
     * Adds an entry to a block tag.
     *
     * @param tagId tag resource location
     * @param entry block resource location to add
     */
    public void add_block_tag(String tagId, String entry) {
        PyFishResourceGenerator.addBlockTag(tagId, entry);
        LOGGER.debug("[PyFish/VirtualDataPack] Added {} to block tag {}", entry, tagId);
    }

    // -------------------------------------------------------------------------
    // Advancement helpers
    // -------------------------------------------------------------------------

    /**
     * Adds an advancement JSON.
     *
     * @param id   advancement resource location, e.g. {@code "pyfish:story/my_advancement"}
     * @param json JSON string or Python dict
     */
    public void add_advancement(String id, Object json) {
        String resolved = PyFishResourceGenerator.resolveNamespacedString(id, PyFish.MODID);
        int colon = resolved.indexOf(':');
        String ns = colon >= 0 ? resolved.substring(0, colon) : PyFish.MODID;
        String path = colon >= 0 ? resolved.substring(colon + 1) : resolved;
        String location = ns + ":advancement/" + path + ".json";
        addDataJson(location, json);
    }

    // -------------------------------------------------------------------------
    // Raw resource helper
    // -------------------------------------------------------------------------

    /**
     * Adds a raw server-data resource at an exact resource location.
     *
     * @param location resource location, e.g. {@code "pyfish:recipe/ruby_nugget.json"}
     * @param content  raw JSON text content
     */
    public void add_raw_data_resource(String location, String content) {
        PyFishResourceGenerator.addDataTextResource(location, content);
        LOGGER.debug("[PyFish/VirtualDataPack] Added data resource: {}", location);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void addDataJson(String location, Object json) {
        String jsonStr = toJsonString(json);
        PyFishResourceGenerator.addDataTextResource(location, jsonStr);
        LOGGER.debug("[PyFish/VirtualDataPack] Added data JSON at: {}", location);
    }

    private static String toJsonString(Object obj) {
        if (obj instanceof String s) {
            return s;
        }
        return GSON.toJson(obj);
    }

    private VirtualDataPack() {}
}
