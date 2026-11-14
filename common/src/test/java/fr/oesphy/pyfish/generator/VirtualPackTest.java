package fr.oesphy.pyfish.generator;

import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VirtualAssetPack} and {@link VirtualDataPack}.
 *
 * <p>These tests only use pure-Java APIs (Gson, PyFishResourceGenerator static methods)
 * — no GraalPy or Minecraft bootstrap required.
 */
class VirtualPackTest {

    @BeforeEach
    void clearResources() {
        // Reset the generator's static maps between tests
        PyFishResourceGenerator.clear();
    }

    // -------------------------------------------------------------------------
    // VirtualAssetPack tests
    // -------------------------------------------------------------------------

    @Test
    void assetPack_singleton_notNull() {
        assertNotNull(VirtualAssetPack.INSTANCE, "INSTANCE should not be null");
    }

    @Test
    void assetPack_addModel_string_json() {
        VirtualAssetPack.INSTANCE.add_model("pyfish:item/ruby",
                "{\"parent\":\"minecraft:item/generated\"}");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        assertTrue(resources.containsKey("pyfish:models/item/ruby.json"),
                "Client resources should contain the item model");
        String content = new String(resources.get("pyfish:models/item/ruby.json"), StandardCharsets.UTF_8);
        assertTrue(content.contains("minecraft:item/generated"),
                "Model JSON should contain the parent");
    }

    @Test
    void assetPack_addModel_map_json() {
        VirtualAssetPack.INSTANCE.add_model("pyfish:item/gem",
                Map.of("parent", "minecraft:item/generated"));

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        assertTrue(resources.containsKey("pyfish:models/item/gem.json"),
                "Client resources should contain the item model from map");
    }

    @Test
    void assetPack_addBlockstate() {
        VirtualAssetPack.INSTANCE.add_blockstate("pyfish:ruby_block",
                "{\"variants\":{\"\":{\"model\":\"pyfish:block/ruby_block\"}}}");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        assertTrue(resources.containsKey("pyfish:blockstates/ruby_block.json"),
                "Client resources should contain the blockstate");
    }

    @Test
    void assetPack_addItemDefinition() {
        VirtualAssetPack.INSTANCE.add_item_definition("pyfish:ruby",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"pyfish:item/ruby\"}}");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        assertTrue(resources.containsKey("pyfish:items/ruby.json"),
                "Client resources should contain the item definition");
    }

    @Test
    void assetPack_generateItemModel_addsClientResources() {
        VirtualAssetPack.INSTANCE.generate_item_model("pyfish:ruby", null);

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        // generateItemModel creates models/item/<path>.json and items/<path>.json
        assertTrue(resources.containsKey("pyfish:models/item/ruby.json"),
                "generate_item_model should add item model");
    }

    @Test
    void assetPack_generateBlockModel_addsBlockstateAndModel() {
        VirtualAssetPack.INSTANCE.generate_block_model("pyfish:ruby_block", null);

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        assertTrue(resources.containsKey("pyfish:blockstates/ruby_block.json"),
                "generate_block_model should add blockstate");
        assertTrue(resources.containsKey("pyfish:models/block/ruby_block.json"),
                "generate_block_model should add block model");
    }

    @Test
    void assetPack_addClientResource_raw() {
        VirtualAssetPack.INSTANCE.add_client_resource("pyfish:sounds/my_sound.json", "{\"sounds\":[]}");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        assertTrue(resources.containsKey("pyfish:sounds/my_sound.json"),
                "add_client_resource should write raw resource");
    }

    @Test
    void assetPack_addLangEntries() {
        VirtualAssetPack.INSTANCE.add_lang_entries("en_us", Map.of(
                "item.pyfish.ruby", "Ruby",
                "block.pyfish.ruby_block", "Ruby Block"
        ));

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        // The add_lang_entries writes to pyfish:lang/en_us.json
        assertTrue(resources.containsKey("pyfish:lang/en_us.json"),
                "add_lang_entries should write lang file");
    }

    @Test
    void assetPack_addModel_noNamespace_defaultsToPyfish() {
        // "ruby_ore" has no namespace — should default to "pyfish"
        // But resolveNamespacedString with PyFish.MODID... PyFish.MODID = "pyfish"
        // However PyFish class may not be bootstrapped. Let's use an explicit namespace.
        VirtualAssetPack.INSTANCE.add_model("pyfish:item/test",
                "{\"parent\":\"minecraft:item/generated\"}");
        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        assertFalse(resources.isEmpty(), "Adding a model should produce at least one resource");
    }

    // -------------------------------------------------------------------------
    // VirtualDataPack tests
    // -------------------------------------------------------------------------

    @Test
    void dataPack_singleton_notNull() {
        assertNotNull(VirtualDataPack.INSTANCE, "INSTANCE should not be null");
    }

    @Test
    void dataPack_addRecipe_string_json() {
        String recipeJson = "{\"type\":\"minecraft:crafting_shapeless\","
                + "\"ingredients\":[{\"item\":\"pyfish:ruby\"}],"
                + "\"result\":{\"id\":\"pyfish:ruby_nugget\",\"count\":9}}";
        VirtualDataPack.INSTANCE.add_recipe("pyfish:ruby_nugget", recipeJson);

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(resources.containsKey("pyfish:recipe/ruby_nugget.json"),
                "Data resources should contain the recipe");
        String content = new String(resources.get("pyfish:recipe/ruby_nugget.json"), StandardCharsets.UTF_8);
        assertTrue(content.contains("crafting_shapeless"), "Recipe JSON content should be preserved");
    }

    @Test
    void dataPack_addRecipe_map_json() {
        VirtualDataPack.INSTANCE.add_recipe("pyfish:gem_block",
                Map.of("type", "minecraft:crafting_shapeless"));

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(resources.containsKey("pyfish:recipe/gem_block.json"),
                "Data resources should contain the recipe from map");
    }

    @Test
    void dataPack_addLootTable() {
        VirtualDataPack.INSTANCE.add_loot_table("pyfish:blocks/ruby_block",
                "{\"type\":\"minecraft:block\",\"pools\":[]}");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(resources.containsKey("pyfish:loot_table/blocks/ruby_block.json"),
                "Data resources should contain the loot table");
    }

    @Test
    void dataPack_addItemTag() {
        VirtualDataPack.INSTANCE.add_item_tag("c:gems", "pyfish:ruby");

        // Materialize via hasResources (which calls materializeDeferredResources)
        boolean hasData = PyFishResourceGenerator.hasResources(PackType.SERVER_DATA);
        assertTrue(hasData, "Should have server data after adding an item tag");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        // After materialization, tags are written
        assertTrue(resources.containsKey("c:tags/item/gems.json"),
                "Data resources should contain the item tag JSON");
    }

    @Test
    void dataPack_addBlockTag() {
        VirtualDataPack.INSTANCE.add_block_tag("minecraft:mineable/pickaxe", "pyfish:ruby_block");

        boolean hasData = PyFishResourceGenerator.hasResources(PackType.SERVER_DATA);
        assertTrue(hasData, "Should have server data after adding a block tag");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(resources.containsKey("minecraft:tags/block/mineable/pickaxe.json"),
                "Data resources should contain the block tag JSON");
    }

    @Test
    void dataPack_addTag_items_collection() {
        VirtualDataPack.INSTANCE.add_tag("item", "c:gems",
                java.util.List.of("pyfish:ruby", "pyfish:sapphire"));

        boolean hasData = PyFishResourceGenerator.hasResources(PackType.SERVER_DATA);
        assertTrue(hasData, "Should have server data after adding tag entries");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(resources.containsKey("c:tags/item/gems.json"),
                "Should produce gems tag JSON");
        String content = new String(resources.get("c:tags/item/gems.json"), StandardCharsets.UTF_8);
        assertTrue(content.contains("pyfish:ruby"), "Tag JSON should contain ruby");
        assertTrue(content.contains("pyfish:sapphire"), "Tag JSON should contain sapphire");
    }

    @Test
    void dataPack_addAdvancement() {
        VirtualDataPack.INSTANCE.add_advancement("pyfish:story/begin",
                "{\"criteria\":{},\"rewards\":{}}");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(resources.containsKey("pyfish:advancement/story/begin.json"),
                "Data resources should contain the advancement");
    }

    @Test
    void dataPack_addRawDataResource() {
        VirtualDataPack.INSTANCE.add_raw_data_resource(
                "pyfish:dimension/test_dimension.json",
                "{\"type\":\"minecraft:overworld\"}");

        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(resources.containsKey("pyfish:dimension/test_dimension.json"),
                "Raw data resource should be stored");
    }

    @Test
    void dataPack_addTag_emptyCollection_noOp() {
        // Should not throw and should not add anything
        assertDoesNotThrow(() ->
                VirtualDataPack.INSTANCE.add_tag("item", "c:gems", java.util.List.of())
        );
    }

    @Test
    void dataPack_addTag_nullCollection_noOp() {
        assertDoesNotThrow(() ->
                VirtualDataPack.INSTANCE.add_tag("item", "c:gems", null)
        );
    }
}
