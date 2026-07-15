package fr.oesphy.pyfish.content.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipeAPI {
    private final PyFishContentManager manager;
    private final Set<String> removedRecipes = new LinkedHashSet<>();

    public RecipeAPI(PyFishContentManager manager) {
        this.manager = manager;
    }

    public Set<String> getRemovedRecipes() {
        return removedRecipes;
    }

    public void clearRemovals() {
        removedRecipes.clear();
    }

    public void shaped(String id, String result, int count, List<String> pattern, Map<String, String> keys) {
        String recipeId = manager.resolveId(id);

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shaped");
        root.addProperty("category", "misc");

        JsonArray patternArray = new JsonArray();
        pattern.forEach(patternArray::add);
        root.add("pattern", patternArray);

        JsonObject keyObject = new JsonObject();
        keys.forEach((symbol, value) -> keyObject.addProperty(symbol, normalizeIngredient(value)));
        root.add("key", keyObject);

        JsonObject resultObject = new JsonObject();
        resultObject.addProperty("id", manager.resolveNamespacedString(result));
        resultObject.addProperty("count", count);
        root.add("result", resultObject);

        PyFishResourceGenerator.addDataTextResource(
                PyFishResourceGenerator.namespaceOf(recipeId) + ":recipes/" + PyFishResourceGenerator.pathOf(recipeId) + ".json",
                root.toString());
        PyFish.LOGGER.info("[PyFish/Content] Generated shaped recipe {}", recipeId);
    }

    public void shapeless(String id, String result, int count, List<String> ingredients) {
        String recipeId = manager.resolveId(id);

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shapeless");
        root.addProperty("category", "misc");

        JsonArray ingredientArray = new JsonArray();
        ingredients.stream()
                .map(this::normalizeIngredient)
                .forEach(ingredientArray::add);
        root.add("ingredients", ingredientArray);

        JsonObject resultObject = new JsonObject();
        resultObject.addProperty("id", manager.resolveNamespacedString(result));
        resultObject.addProperty("count", count);
        root.add("result", resultObject);

        PyFishResourceGenerator.addDataTextResource(
                PyFishResourceGenerator.namespaceOf(recipeId) + ":recipes/" + PyFishResourceGenerator.pathOf(recipeId) + ".json",
                root.toString());
        PyFish.LOGGER.info("[PyFish/Content] Generated shapeless recipe {}", recipeId);
    }

    public void remove(String recipeId) {
        String resolved = manager.resolveId(recipeId);
        removedRecipes.add(resolved);
        PyFish.LOGGER.info("[PyFish/Content] Queued recipe removal for {}", resolved);
    }

    private String normalizeIngredient(String value) {
        if (value == null || value.isBlank()) {
            return "minecraft:air";
        }

        if (value.startsWith("#")) {
            return "#" + manager.resolveNamespacedString(value.substring(1));
        }

        return manager.resolveNamespacedString(value);
    }
}
