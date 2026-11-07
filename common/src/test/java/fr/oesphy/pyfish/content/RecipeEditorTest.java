package fr.oesphy.pyfish.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeEditorTest {
    private JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private Set<String> tags(String tag) { return tag.equals("c:tin") ? Set.of("a:tin", "b:tin") : Set.of(); }

    @Test void customSchemaEditsOnlyDeclaredStacks() {
        var editor = new RecipeEditor();
        editor.schema("machine:crusher", List.of(new RecipeEditor.Field("results.*.stack", "output", "item_stack")));
        editor.add(new RecipeEditor.Rule(Map.of("mod", "pack"), "output", "#c:tin", "b:tin"));
        var recipe = json("""
            {"type":"machine:crusher","results":[{"stack":{"id":"a:tin","count":3,"components":{"test:x":1}},"chance":0.25}],"decoy":{"item":"a:tin"}}
            """);
        assertTrue(editor.apply("pack:ore", recipe, this::tags).changed());
        var result = recipe.getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals("b:tin", result.getAsJsonObject("stack").get("id").getAsString());
        assertEquals(3, result.getAsJsonObject("stack").get("count").getAsInt());
        assertTrue(result.getAsJsonObject("stack").has("components"));
        assertEquals(0.25, result.get("chance").getAsDouble());
        assertEquals("a:tin", recipe.getAsJsonObject("decoy").get("item").getAsString());
    }

    @Test void filtersCombineWithoutNarrowingIngredientTags() {
        var editor = new RecipeEditor();
        var recipe = json("""
            {"type":"minecraft:crafting_shapeless","ingredients":[{"tag":"c:tin"},{"item":"a:tin"}],"result":{"id":"a:tin"}}
            """);
        Object filter = Map.of("all", List.of(Map.of("mod", "pack"), Map.of("not", Map.of("id", "pack:excluded"))),
                "any", List.of(Map.of("input", "#c:tin"), Map.of("output", "minecraft:air")));
        assertTrue(editor.matches("pack:test", recipe, filter, this::tags));
        assertFalse(editor.matches("pack:excluded", recipe, filter, this::tags));
        editor.add(new RecipeEditor.Rule(filter, "input", "a:tin", "b:tin"));
        assertTrue(editor.apply("pack:test", recipe, this::tags).changed());
        assertEquals("c:tin", recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("b:tin", recipe.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());
        assertEquals("a:tin", recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test void inputFormatsAndRuleOrderArePreserved() {
        var editor = new RecipeEditor();
        editor.add(new RecipeEditor.Rule(Map.of(), "input", "a:tin", "#c:tin"));
        editor.add(new RecipeEditor.Rule(Map.of(), "input", "#c:tin", "b:tin"));
        var recipe = json("""
            {"type":"minecraft:crafting_shaped","key":{"A":["a:tin",{"item":"a:tin"}]},"result":"a:tin"}
            """);
        assertTrue(editor.apply("pack:test", recipe, this::tags).changed());
        var alternatives = recipe.getAsJsonObject("key").getAsJsonArray("A");
        assertEquals("b:tin", alternatives.get(0).getAsString());
        assertEquals("b:tin", alternatives.get(1).getAsJsonObject().get("item").getAsString());
    }

    @Test void unknownRecipesAreNotGuessedButCanBeRemoved() {
        var editor = new RecipeEditor();
        editor.add(new RecipeEditor.Rule(Map.of(), "output", "a:tin", "b:tin"));
        var recipe = json("{\"type\":\"unknown:type\",\"result\":\"a:tin\"}");
        assertFalse(editor.apply("pack:test", recipe, this::tags).changed());
        editor.add(new RecipeEditor.Rule(Map.of("type", "unknown:type"), "remove", null, null));
        assertTrue(editor.apply("pack:test", recipe, this::tags).removed());
    }

    @Test void invalidSelectorsAndSchemasAreRejected() {
        var editor = new RecipeEditor();
        assertThrows(IllegalArgumentException.class, () -> editor.add(new RecipeEditor.Rule(Map.of("typo", "x"), "remove", null, null)));
        assertThrows(IllegalArgumentException.class, () -> editor.add(new RecipeEditor.Rule(Map.of(), "output", "a:tin", "#c:tin")));
        assertThrows(IllegalArgumentException.class, () -> new RecipeEditor.Field("results..item", "output", "item_stack"));
        assertThrows(IllegalArgumentException.class, () -> new RecipeEditor.Field("result", "output", "fluid"));
    }
}
