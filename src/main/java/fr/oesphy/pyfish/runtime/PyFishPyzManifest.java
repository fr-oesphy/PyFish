package fr.oesphy.pyfish.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record PyFishPyzManifest(
        int schemaVersion,
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        String license,
        String homepage) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String MANIFEST_NAME = "pyfish.mod.json";
    public static final String LEGACY_MANIFEST_NAME = "pyfish.json";
    private static final Pattern MOD_ID_PATTERN = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Gson GSON = new Gson();

    public PyFishPyzManifest {
        authors = List.copyOf(authors);
    }

    public static PyFishPyzManifest parse(String json) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(stripBom(json));
        } catch (Exception e) {
            throw new IllegalArgumentException(MANIFEST_NAME + " is not valid JSON: " + e.getMessage(), e);
        }

        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(MANIFEST_NAME + " must contain a JSON object");
        }

        JsonObject root = parsed.getAsJsonObject();
        if (root.has("dependencies")) {
            throw new IllegalArgumentException(MANIFEST_NAME
                    + " field 'dependencies' is not supported yet; remove it and document required mods separately");
        }
        int schemaVersion = requiredInteger(root, "schema_version");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported " + MANIFEST_NAME + " schema_version " + schemaVersion
                    + " (expected " + CURRENT_SCHEMA_VERSION + ")");
        }

        String id = requiredString(root, "id");
        if (!MOD_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid mod id '" + id
                    + "': use 1-64 lowercase letters, numbers, underscores, dots, or dashes");
        }
        if (id.equals("minecraft") || id.equals("pyfish")) {
            throw new IllegalArgumentException("Mod id '" + id + "' is reserved");
        }

        return new PyFishPyzManifest(
                schemaVersion,
                id,
                requiredString(root, "name"),
                requiredString(root, "version"),
                optionalString(root, "description"),
                optionalAuthors(root),
                optionalString(root, "license"),
                optionalString(root, "homepage"));
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", schemaVersion);
        root.addProperty("id", id);
        root.addProperty("name", name);
        root.addProperty("version", version);
        root.addProperty("description", description);

        JsonArray authorArray = new JsonArray();
        authors.forEach(authorArray::add);
        root.add("authors", authorArray);

        root.addProperty("license", license);
        root.addProperty("homepage", homepage);
        return GSON.toJson(root);
    }

    private static int requiredInteger(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(MANIFEST_NAME + " field '" + key + "' must be an integer");
        }
        try {
            return value.getAsInt();
        } catch (Exception e) {
            throw new IllegalArgumentException(MANIFEST_NAME + " field '" + key + "' must be an integer", e);
        }
    }

    private static String requiredString(JsonObject root, String key) {
        String value = optionalString(root, key);
        if (value.isBlank()) {
            throw new IllegalArgumentException(MANIFEST_NAME + " field '" + key + "' is required");
        }
        return value;
    }

    private static String optionalString(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(MANIFEST_NAME + " field '" + key + "' must be a string");
        }
        return value.getAsString().trim();
    }

    private static List<String> optionalAuthors(JsonObject root) {
        JsonElement value = root.get("authors");
        if (value == null || value.isJsonNull()) {
            return List.of();
        }

        List<String> authors = new ArrayList<>();
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            addAuthor(authors, value.getAsString());
            return authors;
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(MANIFEST_NAME + " field 'authors' must be a string or an array of strings");
        }

        for (JsonElement author : value.getAsJsonArray()) {
            if (!author.isJsonPrimitive() || !author.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(MANIFEST_NAME + " field 'authors' must contain only strings");
            }
            addAuthor(authors, author.getAsString());
        }
        return authors;
    }

    private static void addAuthor(List<String> authors, String author) {
        String normalized = author.trim();
        if (!normalized.isEmpty()) {
            authors.add(normalized);
        }
    }

    private static String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
