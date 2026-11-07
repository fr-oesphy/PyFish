package fr.oesphy.pyfish.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Schema-directed edits: unrelated JSON fields are never searched or rewritten. */
public final class RecipeEditor {
    public record Field(String path, String role, String kind) {
        public Field {
            if (!path.matches("[a-zA-Z0-9_-]+(?:\\.(?:[a-zA-Z0-9_-]+|\\*))*"))
                throw new IllegalArgumentException("Invalid recipe field path: " + path);
            if (!Set.of("input", "output").contains(role) || !Set.of("ingredient", "item_stack").contains(kind))
                throw new IllegalArgumentException("Recipe fields require input/output and ingredient/item_stack");
        }
    }
    public record Rule(Object filter, String role, String from, String to) {}
    private final Map<String, List<Field>> schemas = new LinkedHashMap<>();
    private final List<Rule> rules = new ArrayList<>();
    private long revision;
    public long revision() { return revision; }

    public RecipeEditor() {
        schema("minecraft:crafting_shaped", List.of(new Field("key.*", "input", "ingredient"), new Field("result", "output", "item_stack")));
        schema("minecraft:crafting_shapeless", List.of(new Field("ingredients.*", "input", "ingredient"), new Field("result", "output", "item_stack")));
        for (String type : List.of("smelting", "blasting", "smoking", "campfire_cooking", "stonecutting"))
            schema("minecraft:" + type, List.of(new Field("ingredient", "input", "ingredient"), new Field("result", "output", "item_stack")));
        schema("minecraft:smithing_transform", List.of(new Field("template", "input", "ingredient"),
                new Field("base", "input", "ingredient"), new Field("addition", "input", "ingredient"), new Field("result", "output", "item_stack")));
        schema("minecraft:smithing_trim", List.of(new Field("template", "input", "ingredient"),
                new Field("base", "input", "ingredient"), new Field("addition", "input", "ingredient")));
    }

    public void schema(String type, List<Field> fields) {
        if (fields.stream().map(Field::path).distinct().count() != fields.size())
            throw new IllegalArgumentException("Duplicate recipe field paths");
        schemas.put(type, List.copyOf(fields));
        revision++;
    }
    public boolean supports(String type) { return schemas.containsKey(type); }
    public boolean hasRules() { return !rules.isEmpty(); }
    public void add(Rule rule) {
        validateFilter(rule.filter());
        if (!Set.of("input", "output", "remove").contains(rule.role())) throw new IllegalArgumentException("Invalid recipe operation");
        if (!rule.role().equals("remove")) {
            if (rule.from() == null || rule.from().isBlank() || rule.to() == null || rule.to().isBlank())
                throw new IllegalArgumentException("Replacement identifiers cannot be blank");
            if (rule.role().equals("output") && rule.to().startsWith("#"))
                throw new IllegalArgumentException("A recipe output requires an explicit item, not a tag");
        }
        rules.add(rule);
        revision++;
    }
    public static void validateFilter(Object value) {
        if (value instanceof List<?> list) { list.forEach(RecipeEditor::validateFilter); return; }
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("A recipe filter must be a dictionary or an OR list");
        map.forEach((key, child) -> {
            switch (key.toString()) {
                case "id", "type", "mod", "input", "output" -> {
                    if (!(child instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Filter " + key + " requires a non-empty string");
                }
                case "not" -> validateFilter(child);
                case "all", "any" -> {
                    if (!(child instanceof List<?> list)) throw new IllegalArgumentException(key + " requires a list");
                    list.forEach(RecipeEditor::validateFilter);
                }
                default -> throw new IllegalArgumentException("Unknown recipe filter: " + key);
            }
        });
    }

    public boolean matches(String id, JsonObject recipe, Object filter, Function<String, Set<String>> tags) {
        if (filter instanceof List<?> list) return list.stream().anyMatch(f -> matches(id, recipe, f, tags));
        Map<?, ?> map = (Map<?, ?>) filter;
        for (var entry : map.entrySet()) {
            Object value = entry.getValue();
            boolean match = switch (entry.getKey().toString()) {
                case "id" -> id.equals(value);
                case "type" -> recipe.get("type").getAsString().equals(value);
                case "mod" -> PyFishResourceGenerator.namespaceOf(id).equals(value);
                case "input", "output" -> contains(recipe, entry.getKey().toString(), value.toString(), tags);
                case "not" -> !matches(id, recipe, value, tags);
                case "all" -> ((List<?>) value).stream().allMatch(f -> matches(id, recipe, f, tags));
                case "any" -> ((List<?>) value).stream().anyMatch(f -> matches(id, recipe, f, tags));
                default -> throw new IllegalArgumentException("Invalid recipe filter");
            };
            if (!match) return false;
        }
        return true;
    }

    public record Result(boolean changed, boolean removed) {}
    public Result apply(String id, JsonObject recipe, Function<String, Set<String>> tags) {
        boolean changed = false;
        for (Rule rule : rules) {
            if (!matches(id, recipe, rule.filter(), tags)) continue;
            if (rule.role().equals("remove")) return new Result(changed, true);
            for (Field field : fields(recipe, rule.role())) {
                for (Slot slot : slots(recipe, field.path())) {
                    JsonElement before = slot.get();
                    JsonElement after = replace(before, field.kind(), rule.from(), rule.to(), tags);
                    if (!before.equals(after)) { slot.set(after); changed = true; }
                }
            }
        }
        return new Result(changed, false);
    }
    private List<Field> fields(JsonObject recipe, String role) {
        return schemas.getOrDefault(recipe.get("type").getAsString(), List.of()).stream().filter(f -> f.role().equals(role)).toList();
    }
    private boolean contains(JsonObject recipe, String role, String match, Function<String, Set<String>> tags) {
        for (Field field : fields(recipe, role)) for (Slot slot : slots(recipe, field.path()))
            if (containsValue(slot.get(), field.kind(), match, tags)) return true;
        return false;
    }
    private boolean containsValue(JsonElement value, String kind, String match, Function<String, Set<String>> tags) {
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (containsValue(child, kind, match, tags)) return true;
            return false;
        }
        String identifier = identifier(value, kind);
        return identifier != null && matchesIdentifier(identifier, match, tags);
    }
    private static String identifier(JsonElement value, String kind) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString();
        if (!value.isJsonObject()) return null;
        var object = value.getAsJsonObject();
        if (object.has("type")) return null; // Custom ingredient codecs need a dedicated component, not recursive guessing.
        String key = kind.equals("ingredient") ? "item" : object.has("id") ? "id" : "item";
        if (object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
        if (kind.equals("ingredient") && object.has("tag")) return "#" + object.get("tag").getAsString();
        return null;
    }
    private static boolean matchesIdentifier(String value, String match, Function<String, Set<String>> tags) {
        if (value.equals(match)) return true;
        // An item match must not narrow an entire ingredient tag containing that item.
        return match.startsWith("#") && !value.startsWith("#") && tags.apply(match.substring(1)).contains(value);
    }
    private JsonElement replace(JsonElement value, String kind, String from, String to, Function<String, Set<String>> tags) {
        if (value.isJsonArray()) {
            var array = value.getAsJsonArray().deepCopy();
            for (int i = 0; i < array.size(); i++) array.set(i, replace(array.get(i), kind, from, to, tags));
            return array;
        }
        String identifier = identifier(value, kind);
        if (identifier == null || !matchesIdentifier(identifier, from, tags)) return value;
        if (value.isJsonPrimitive()) return new JsonPrimitive(to);
        var object = value.getAsJsonObject().deepCopy();
        if (kind.equals("ingredient")) {
            if (object.size() != 1) throw new IllegalArgumentException("Non-standard ingredient requires a dedicated schema component");
            object.remove("item"); object.remove("tag");
            object.addProperty(to.startsWith("#") ? "tag" : "item", to.startsWith("#") ? to.substring(1) : to);
        } else {
            if (to.startsWith("#")) throw new IllegalArgumentException("Item stacks cannot be replaced by tags");
            object.addProperty(object.has("id") ? "id" : "item", to);
        }
        return object;
    }
    private record Slot(JsonElement parent, String key) {
        JsonElement get() { return parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key); }
        void set(JsonElement value) { if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), value); else parent.getAsJsonObject().add(key, value); }
    }
    private static List<Slot> slots(JsonObject recipe, String path) {
        List<Slot> result = new ArrayList<>();
        walk(recipe, path.split("\\."), 0, result);
        return result;
    }
    private static void walk(JsonElement node, String[] path, int index, List<Slot> result) {
        if (node == null || node.isJsonNull()) return;
        String key = path[index];
        List<Slot> children = new ArrayList<>();
        if (key.equals("*")) {
            if (node.isJsonObject()) node.getAsJsonObject().keySet().forEach(k -> children.add(new Slot(node, k)));
            else if (node.isJsonArray()) for (int i = 0; i < node.getAsJsonArray().size(); i++) children.add(new Slot(node, Integer.toString(i)));
        } else if (node.isJsonObject() && node.getAsJsonObject().has(key)) children.add(new Slot(node, key));
        else if (node.isJsonArray() && key.matches("[0-9]+")) {
            int position = Integer.parseInt(key);
            if (position < node.getAsJsonArray().size()) children.add(new Slot(node, key));
        }
        for (Slot slot : children) {
            if (index == path.length - 1) result.add(slot);
            else walk(slot.get(), path, index + 1, result);
        }
    }
}
