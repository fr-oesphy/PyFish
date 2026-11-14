package fr.oesphy.pyfish.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.PackType;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class BlockResources {
    private BlockResources() {
    }

    static void generate(String id, Map<String, Object> properties) {
        String namespace = PyFishResourceGenerator.namespaceOf(id);
        String path = PyFishResourceGenerator.pathOf(id);
        String location = namespace + ":models/block/" + path + ".json";
        boolean authored = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES).containsKey(location);
        PyFishResourceGenerator.generateBlockState(id);
        PyFishResourceGenerator.generateBlockModel(id, properties.get("texture"));
        PyFishResourceGenerator.generateBlockItemModel(id);
        if (!authored || properties.containsKey("renderType")) {
            byte[] bytes = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES).get(location);
            JsonObject model = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!authored && properties.containsKey("model")) model.addProperty("parent",
                    PyFishResourceGenerator.resolveIdentifier(properties.get("model").toString(), namespace));
            String layer = ContentProperties.renderType(properties);
            if (!ContentVersion.automaticRenderLayers()) {
                if (!model.has("render_type")) model.addProperty("render_type", "minecraft:" + ContentVersion.namedRenderLayer(layer));
            } else if (layer.equals("translucent")) {
                JsonObject textures = model.getAsJsonObject("textures");
                for (String key : textures == null ? new String[0] : textures.keySet().toArray(String[]::new)) {
                    if (key.equals("particle")) continue;
                    if (textures.get(key).isJsonPrimitive() && textures.get(key).getAsString().startsWith("#")) continue;
                    JsonObject material = textures.get(key).isJsonObject()
                            ? textures.getAsJsonObject(key).deepCopy() : new JsonObject();
                    if (!material.has("sprite")) material.addProperty("sprite", textures.get(key).getAsString());
                    if (!material.has("force_translucent")) material.addProperty("force_translucent", true);
                    textures.add(key, material);
                }
            }
            PyFishResourceGenerator.addClientTextResource(location, model.toString());
        }
        String drop = properties.getOrDefault("drops", "self").toString();
        JsonObject loot = new JsonObject();
        loot.addProperty("type", "minecraft:block");
        JsonArray pools = new JsonArray();
        if (!drop.equals("none")) {
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", drop.equals("self") ? id : PyFishResourceGenerator.resolveIdentifier(drop, namespace));
            JsonArray entries = new JsonArray();
            entries.add(entry);
            pool.add("entries", entries);
            JsonObject survives = new JsonObject();
            survives.addProperty("condition", "minecraft:survives_explosion");
            JsonArray conditions = new JsonArray();
            conditions.add(survives);
            pool.add("conditions", conditions);
            pools.add(pool);
        }
        loot.add("pools", pools);
        PyFishResourceGenerator.addDataTextResourceIfAbsent(namespace + ":loot_table/blocks/" + path + ".json", loot.toString());
        if (properties.containsKey("miningTool")) {
            String tool = properties.get("miningTool").toString();
            if (!java.util.Set.of("pickaxe", "axe", "shovel", "hoe").contains(tool)) {
                throw new IllegalArgumentException("miningTool must be pickaxe, axe, shovel or hoe");
            }
            PyFishResourceGenerator.addBlockTag("minecraft:mineable/" + tool, id);
        }
        if (properties.containsKey("requiredTier")) {
            String tier = ContentProperties.tier(properties, "requiredTier");
            // Vanilla's highest block requirement is diamond, including netherite hosts.
            String requirement = switch (tier) {
                case "stone" -> "stone";
                case "iron" -> "iron";
                case "diamond", "netherite" -> "diamond";
                default -> null;
            };
            if (requirement != null) PyFishResourceGenerator.addBlockTag("minecraft:needs_" + requirement + "_tool", id);
        }
    }
}
