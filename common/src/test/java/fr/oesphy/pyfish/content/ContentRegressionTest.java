package fr.oesphy.pyfish.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.oesphy.pyfish.content.api.ContentInteraction;
import fr.oesphy.pyfish.content.api.RecipeAPI;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContentRegressionTest {
    @AfterEach
    void clear() {
        PyFishResourceGenerator.clear();
    }

    private JsonObject resource(PackType type, String id) {
        byte[] bytes = PyFishResourceGenerator.getGeneratedResources(type).get(id);
        assertNotNull(bytes, id);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void buildCapabilitiesMatchTheSelectedProfile() {
        String profile = System.getProperty("pyfish.test.profile");
        assertNotNull(profile);
        assertEquals(profile.equals("mc1211"), ContentVersion.legacyIngredients());
        assertEquals(profile.equals("mc262"), ContentVersion.automaticRenderLayers());
    }

    @Test
    void deferredEventsRetainTheirScriptNamespace() {
        CapturedEvents events;
        try (var scope = PyFishScriptContext.push("regression")) {
            events = new CapturedEvents();
        }
        assertEquals("regression", events.namespace());
        assertNotEquals("regression", PyFishScriptContext.currentNamespace());
    }

    @Test
    void tiersAndPropertyBoundsAreExplicit() {
        assertEquals("wood", ContentProperties.tier(Map.of("tier", 0), "tier"));
        assertEquals("netherite", ContentProperties.tier(Map.of("tier", 4), "tier"));
        assertEquals("diamond", ContentProperties.tier(Map.of("tier", "diamond"), "tier"));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.tier(Map.of("tier", 2.5), "tier"));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.tier(Map.of("tier", 5), "tier"));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.renderType(Map.of("renderType", "transparent")));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.integer(Map.of("lightLevel", 16), "lightLevel", 0, 0, 15));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.shape(List.of(0, 0, 0, 17, 16, 16)));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.validate("block", Map.of("noCollision", "true")));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.validate("block", Map.of("sound", "unknown")));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.validate("block", Map.of("speedFactor", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.validate("tool", Map.of("durability", 0)));
        assertDoesNotThrow(() -> ContentProperties.validate("item", Map.of("durability", 0)));
        assertThrows(IllegalArgumentException.class, () -> ContentProperties.validate("block", Map.of(
                "collisionShape", List.of(8, 0, 0, 4, 16, 16))));
    }

    @Test
    void recipeIngredientsFollowTheActiveMinecraftCodec() {
        RecipeAPI recipes = new RecipeAPI(PyFishContentManager.getInstance());
        recipes.shaped("regression:shaped", "minecraft:stick", 2, List.of("X"), Map.of("X", "#minecraft:planks"));
        recipes.shapeless("regression:shapeless", "minecraft:stick", 1, List.of("minecraft:oak_planks"));
        var shaped = resource(PackType.SERVER_DATA, "regression:recipe/shaped.json").getAsJsonObject("key").get("X");
        var shapeless = resource(PackType.SERVER_DATA, "regression:recipe/shapeless.json").getAsJsonArray("ingredients").get(0);
        if (ContentVersion.legacyIngredients()) {
            assertEquals("minecraft:planks", shaped.getAsJsonObject().get("tag").getAsString());
            assertEquals("minecraft:oak_planks", shapeless.getAsJsonObject().get("item").getAsString());
        } else {
            assertEquals("#minecraft:planks", shaped.getAsString());
            assertEquals("minecraft:oak_planks", shapeless.getAsString());
        }
    }

    @Test
    void outputReplacementPreservesCountComponentsAndIngredients() {
        JsonObject json = JsonParser.parseString("""
                {"type":"minecraft:crafting_shapeless","ingredients":["other:tin"],"result":{"id":"other:tin","count":3,"components":{"test:value":1}}}
                """).getAsJsonObject();
        RecipeEditor editor = new RecipeEditor();
        editor.add(new RecipeEditor.Rule(Map.of(), "output", "other:tin", "preferred:tin"));
        assertTrue(editor.apply("regression:test", json, tag -> Set.of()).changed());
        assertEquals("other:tin", json.getAsJsonArray("ingredients").get(0).getAsString());
        assertEquals("preferred:tin", json.getAsJsonObject("result").get("id").getAsString());
        assertEquals(3, json.getAsJsonObject("result").get("count").getAsInt());
        assertTrue(json.getAsJsonObject("result").has("components"));
        assertFalse(editor.apply("regression:test", json, tag -> Set.of()).changed());
    }

    @Test
    void blockDefaultsIncludeLootAndDeclaredMiningTags() {
        PyFishResourceGenerator.generateBlockResources("regression:ore", Map.of(
                "texture", "minecraft:block/stone", "miningTool", "pickaxe", "requiredTier", 4));
        assertEquals("regression:ore", resource(PackType.SERVER_DATA, "regression:loot_table/blocks/ore.json")
                .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                .get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(resource(PackType.SERVER_DATA, "minecraft:tags/block/mineable/pickaxe.json")
                .getAsJsonArray("values").toString().contains("regression:ore"));
        assertTrue(resource(PackType.SERVER_DATA, "minecraft:tags/block/needs_diamond_tool.json")
                .getAsJsonArray("values").toString().contains("regression:ore"));
    }

    @Test
    void authoredModelsAndLootAreNeverOverwritten() {
        String model = "{\"parent\":\"minecraft:block/cross\"}";
        String loot = "{\"type\":\"minecraft:block\",\"pools\":[]}";
        PyFishResourceGenerator.addClientTextResource("regression:models/block/test.json", model);
        PyFishResourceGenerator.addDataTextResource("regression:loot_table/blocks/test.json", loot);
        PyFishResourceGenerator.generateBlockResources("regression:test", Map.of("renderType", "translucent"));
        assertEquals("minecraft:block/cross", resource(PackType.CLIENT_RESOURCES,
                "regression:models/block/test.json").get("parent").getAsString());
        assertTrue(resource(PackType.SERVER_DATA, "regression:loot_table/blocks/test.json").getAsJsonArray("pools").isEmpty());
    }

    @Test
    void transparentModelsUseTheVersionAppropriateMetadata() {
        PyFishResourceGenerator.generateBlockResources("regression:glass", Map.of(
                "renderType", "translucent", "texture", "minecraft:block/glass"));
        JsonObject model = resource(PackType.CLIENT_RESOURCES, "regression:models/block/glass.json");
        if (ContentVersion.automaticRenderLayers()) {
            assertTrue(model.getAsJsonObject("textures").getAsJsonObject("all").get("force_translucent").getAsBoolean());
        } else {
            assertEquals("minecraft:translucent", model.get("render_type").getAsString());
        }
        PyFishResourceGenerator.generateBlockResources("regression:leaves", Map.of(
                "renderType", "cutout_mipped", "texture", "minecraft:block/oak_leaves"));
        JsonObject cutout = resource(PackType.CLIENT_RESOURCES, "regression:models/block/leaves.json");
        if (ContentVersion.automaticRenderLayers()) {
            assertFalse(cutout.has("render_type"));
        } else {
            assertEquals(System.getProperty("pyfish.test.profile").equals("mc12111")
                    ? "minecraft:cutout" : "minecraft:cutout_mipped", cutout.get("render_type").getAsString());
        }
    }

    @Test
    void authoredAndProgrammaticTagMembersAreMergedOnce() {
        PyFishResourceGenerator.addDataTextResource("regression:tags/item/metals.json",
                "{\"replace\":true,\"values\":[\"regression:tin\"]}");
        PyFishResourceGenerator.addItemTag("regression:metals", "regression:copper");
        for (int i = 0; i < 2; i++) {
            JsonObject tag = resource(PackType.SERVER_DATA, "regression:tags/item/metals.json");
            assertTrue(tag.get("replace").getAsBoolean());
            assertEquals(2, tag.getAsJsonArray("values").size());
            assertEquals(Set.of("regression:tin", "regression:copper"),
                    Set.copyOf(PyFishResourceGenerator.getItemsByTag("regression:metals")));
        }
    }

    @Test
    void interactionResultsHaveAnExplicitPassAndFailure() {
        assertEquals(ContentInteraction.PASS, ContentInteraction.from(null));
        assertEquals(ContentInteraction.CONSUME, ContentInteraction.parse("consume"));
        assertEquals(ContentInteraction.FAIL, ContentInteraction.parse("fail"));
        assertThrows(IllegalArgumentException.class, () -> ContentInteraction.parse("invalid"));
    }
}
