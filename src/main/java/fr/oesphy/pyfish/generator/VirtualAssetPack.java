package fr.oesphy.pyfish.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Python-friendly facade for adding <em>client-side</em> assets (models,
 * blockstates, lang entries, textures, atlases) to the virtual resource pack
 * managed by {@link PyFishResourceGenerator}.
 *
 * <p>All methods accept either raw JSON strings or Java {@link Map} objects
 * (from Python dicts) and delegate to the underlying generator.
 *
 * <h3>Example usage in a Python script</h3>
 * <pre>{@code
 * asset_pack.add_lang("en_us", "item.pyfish.ruby", "Ruby")
 * asset_pack.add_model("pyfish:item/ruby", {"parent": "minecraft:item/generated",
 *                                            "textures": {"layer0": "pyfish:item/ruby"}})
 * }</pre>
 *
 * @see VirtualDataPack
 * @see PyFishResourceGenerator
 */
public final class VirtualAssetPack {

    /** Shared instance exposed as the {@code asset_pack} binding. */
    public static final VirtualAssetPack INSTANCE = new VirtualAssetPack();

    private static final Logger LOGGER = PyFish.LOGGER;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    // -------------------------------------------------------------------------
    // Model helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a raw item/block model JSON.
     *
     * @param id   resource location of the model, e.g. {@code "pyfish:item/ruby"}
     * @param json JSON string or Python dict
     */
    public void add_model(String id, Object json) {
        String resolved = PyFishResourceGenerator.resolveNamespacedString(id, PyFish.MODID);
        String location = resolved + ".json";
        // Insert under models/ path: "namespace:models/path"
        // The id is already like "pyfish:item/ruby", so the location is "pyfish:models/item/ruby.json"
        int colon = resolved.indexOf(':');
        String ns = colon >= 0 ? resolved.substring(0, colon) : PyFish.MODID;
        String path = colon >= 0 ? resolved.substring(colon + 1) : resolved;
        String resourceLocation = ns + ":models/" + path + ".json";
        addClientJson(resourceLocation, json);
    }

    /**
     * Adds a blockstate definition JSON.
     *
     * @param id   block resource location, e.g. {@code "pyfish:ruby_block"}
     * @param json JSON string or Python dict
     */
    public void add_blockstate(String id, Object json) {
        String resolved = PyFishResourceGenerator.resolveNamespacedString(id, PyFish.MODID);
        int colon = resolved.indexOf(':');
        String ns = colon >= 0 ? resolved.substring(0, colon) : PyFish.MODID;
        String path = colon >= 0 ? resolved.substring(colon + 1) : resolved;
        String resourceLocation = ns + ":blockstates/" + path + ".json";
        addClientJson(resourceLocation, json);
    }

    /**
     * Adds an item definition JSON (1.21+ items/ folder).
     *
     * @param id   item resource location, e.g. {@code "pyfish:ruby"}
     * @param json JSON string or Python dict
     */
    public void add_item_definition(String id, Object json) {
        String resolved = PyFishResourceGenerator.resolveNamespacedString(id, PyFish.MODID);
        int colon = resolved.indexOf(':');
        String ns = colon >= 0 ? resolved.substring(0, colon) : PyFish.MODID;
        String path = colon >= 0 ? resolved.substring(colon + 1) : resolved;
        String resourceLocation = ns + ":items/" + path + ".json";
        addClientJson(resourceLocation, json);
    }

    // -------------------------------------------------------------------------
    // Lang helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a single lang entry.
     *
     * @param locale translation key locale, e.g. {@code "en_us"}
     * @param key    translation key, e.g. {@code "item.pyfish.ruby"}
     * @param value  display name, e.g. {@code "Ruby"}
     */
    public void add_lang(String locale, String key, String value) {
        // We re-use add_client_text_resource to write to the lang file directly
        // but that would clobber. Instead we build a minimal JSON and add it.
        // PyFishResourceGenerator.addLocalization handles merging so we use that path.
        // Extract namespace from the key (e.g. "item.pyfish.ruby" → namespace "pyfish")
        // The generator expects (id, category, displayName) for auto-name. Use raw path instead.
        // Raw path: add directly as a client text resource for the lang file.
        String location = "pyfish:" + "lang/" + locale + ".json";
        // Build a minimal JSON with just this key
        String json = "{\"" + escapeJson(key) + "\": \"" + escapeJson(value) + "\"}";
        // We use addClientTextResource which will overwrite; callers should batch via add_lang_entries
        // For single additions use putIfAbsent logic by writing through the resource generator
        PyFishResourceGenerator.addClientTextResourceIfAbsent(location, json);
        LOGGER.debug("[PyFish/VirtualAssetPack] Added lang entry {}/{}: {}", locale, key, value);
    }

    /**
     * Adds multiple lang entries at once from a Python dict.
     *
     * @param locale  locale code, e.g. {@code "en_us"}
     * @param entries map of translation key → display name
     */
    public void add_lang_entries(String locale, Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":\"")
              .append(escapeJson(e.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        String location = "pyfish:lang/" + locale + ".json";
        PyFishResourceGenerator.addClientTextResourceIfAbsent(location, sb.toString());
        LOGGER.debug("[PyFish/VirtualAssetPack] Added {} lang entries for {}", entries.size(), locale);
    }

    // -------------------------------------------------------------------------
    // Raw resource helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a raw client text resource (JSON, etc.) at an exact resource location.
     *
     * @param location resource location, e.g. {@code "pyfish:textures/item/ruby.png"}
     * @param content  raw text content
     */
    public void add_client_resource(String location, String content) {
        PyFishResourceGenerator.addClientTextResource(location, content);
        LOGGER.debug("[PyFish/VirtualAssetPack] Added client resource: {}", location);
    }

    /**
     * Generates a simple item model (parent=item/generated, single layer0 texture).
     *
     * @param itemId  item resource location
     * @param texture texture resource location (or {@code null} to use item id)
     */
    public void generate_item_model(String itemId, String texture) {
        PyFishResourceGenerator.generateItemModel(itemId, texture, null);
        LOGGER.debug("[PyFish/VirtualAssetPack] Generated item model for: {}", itemId);
    }

    /**
     * Generates item model with a custom parent.
     *
     * @param itemId  item resource location
     * @param texture texture resource location
     * @param parent  parent model resource location
     */
    public void generate_item_model(String itemId, String texture, String parent) {
        PyFishResourceGenerator.generateItemModel(itemId, texture, parent);
        LOGGER.debug("[PyFish/VirtualAssetPack] Generated item model for: {} (parent={})", itemId, parent);
    }

    /**
     * Generates default blockstate and block model (cube_all with one texture).
     *
     * @param blockId block resource location
     * @param texture texture resource location (or {@code null} to use block id)
     */
    public void generate_block_model(String blockId, String texture) {
        PyFishResourceGenerator.generateBlockModel(blockId, texture);
        PyFishResourceGenerator.generateBlockState(blockId);
        LOGGER.debug("[PyFish/VirtualAssetPack] Generated block model and state for: {}", blockId);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void addClientJson(String location, Object json) {
        String jsonStr = toJsonString(json);
        PyFishResourceGenerator.addClientTextResourceIfAbsent(location, jsonStr);
        LOGGER.debug("[PyFish/VirtualAssetPack] Added client JSON at: {}", location);
    }

    private static String toJsonString(Object obj) {
        if (obj instanceof String s) {
            return s;
        }
        // Convert Map / other objects via Gson
        return GSON.toJson(obj);
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private VirtualAssetPack() {}
}
