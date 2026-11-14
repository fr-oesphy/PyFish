package fr.oesphy.pyfish.content.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import fr.oesphy.pyfish.content.ContentVersion;
import fr.oesphy.pyfish.content.RecipeEditor;
import fr.oesphy.pyfish.content.PyFishContentSupport;
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
    private final RecipeEditor editor = new RecipeEditor();
    private Map<String, Object> lastReport = Map.of();

    public RecipeEditor getEditor() { return editor; }
    public Map<String, Object> getLastReport() { return lastReport; }
    public void setLastReport(Map<String, Object> report) { lastReport = Map.copyOf(report); }

    public void registerSchema(String type, List<Map<String, Object>> fields) {
        var parsed = fields.stream().map(field -> {
            var data = PyFishContentSupport.snapshotProperties(field);
            if (!data.keySet().equals(Set.of("path", "role", "kind")))
                throw new IllegalArgumentException("Schema fields require exactly path, role and kind");
            return new RecipeEditor.Field(data.get("path").toString(), data.get("role").toString(), data.get("kind").toString());
        }).toList();
        editor.schema(manager.resolveNamespacedString(type), parsed);
    }

    private Object filter(Object filter) {
        return PyFishContentSupport.snapshotProperties(Map.of("filter", filter)).get("filter");
    }

    private String matchId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Item selectors cannot be blank");
        return id.startsWith("#") ? "#" + manager.resolveNamespacedString(id.substring(1)) : manager.resolveNamespacedString(id);
    }

    public void replaceInput(Object filter, String from, String to) {
        editor.add(new RecipeEditor.Rule(filter(filter), "input", matchId(from), matchId(to)));
    }

    public void replaceOutput(Object filter, String from, String to) {
        editor.add(new RecipeEditor.Rule(filter(filter), "output", matchId(from), matchId(to)));
    }

    public void removeMatching(Object filter) {
        editor.add(new RecipeEditor.Rule(filter(filter), "remove", null, null));
    }

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
        keys.forEach((symbol, value) -> keyObject.add(symbol, normalizeIngredient(value)));
        root.add("key", keyObject);

        JsonObject resultObject = new JsonObject();
        resultObject.addProperty("id", manager.resolveNamespacedString(result));
        resultObject.addProperty("count", count);
        root.add("result", resultObject);

        PyFishResourceGenerator.addDataTextResource(
                PyFishResourceGenerator.namespaceOf(recipeId) + ":recipe/" + PyFishResourceGenerator.pathOf(recipeId) + ".json",
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
                PyFishResourceGenerator.namespaceOf(recipeId) + ":recipe/" + PyFishResourceGenerator.pathOf(recipeId) + ".json",
                root.toString());
        PyFish.LOGGER.info("[PyFish/Content] Generated shapeless recipe {}", recipeId);
    }

    public void remove(String recipeId) {
        String resolved = manager.resolveId(recipeId);
        removedRecipes.add(resolved);
        PyFish.LOGGER.info("[PyFish/Content] Queued recipe removal for {}", resolved);
    }

    private JsonElement normalizeIngredient(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Recipe ingredients cannot be blank");
        }
        boolean tag = value.startsWith("#");
        String id = manager.resolveNamespacedString(tag ? value.substring(1) : value);
        if (ContentVersion.legacyIngredients()) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty(tag ? "tag" : "item", id);
            return ingredient;
        }
        return new JsonPrimitive(tag ? "#" + id : id);
    }
}
