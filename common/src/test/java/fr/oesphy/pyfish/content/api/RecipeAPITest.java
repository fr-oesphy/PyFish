package fr.oesphy.pyfish.content.api;

import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeAPITest {
    @AfterEach
    void clearGeneratedResources() {
        PyFishResourceGenerator.clear();
    }

    @Test
    void writesRecipesToTheMinecraft121Directory() {
        RecipeAPI recipes = new RecipeAPI(PyFishContentManager.getInstance());
        recipes.shaped(
                "test_recipe",
                "minecraft:stone",
                1,
                List.of("S"),
                Map.of("S", "minecraft:cobblestone"));

        Map<String, byte[]> generated = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertTrue(generated.containsKey("pyfish:recipe/test_recipe.json"));
        assertFalse(generated.containsKey("pyfish:recipes/test_recipe.json"));
    }
}
