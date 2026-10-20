package fr.oesphy.pyfish.content;

import fr.oesphy.pyfish.PyFish;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates Minecraft resource files (recipes, loot tables, advancements, etc.)
 * Ensures identifiers are valid, JSON is parseable, and paths are safe.
 */
public class ResourceValidator {
    private static final Logger LOGGER = PyFish.LOGGER;

    private final List<ValidationError> errors = new ArrayList<>();
    private final List<ValidationError> warnings = new ArrayList<>();

    /**
     * Validates a resource identifier.
     * @param identifier The identifier to validate (e.g., "mod:item")
     * @return true if valid
     */
    public boolean validateIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            errors.add(new ValidationError("Identifier cannot be null or blank", "identifier", identifier));
            return false;
        }

        // Check for valid namespace:path format
        int colonIndex = identifier.indexOf(':');
        if (colonIndex == -1) {
            errors.add(new ValidationError("Identifier must contain a namespace (format: namespace:path)", "identifier", identifier));
            return false;
        }

        String namespace = identifier.substring(0, colonIndex);
        String path = identifier.substring(colonIndex + 1);

        // Validate namespace
        if (namespace.isBlank()) {
            errors.add(new ValidationError("Namespace cannot be blank", "identifier", identifier));
            return false;
        }

        // Namespace should be lowercase alphanumeric with underscores
        if (!namespace.matches("^[a-z0-9_]+$")) {
            warnings.add(new ValidationError("Namespace should be lowercase alphanumeric with underscores", "identifier", identifier));
        }

        // Validate path
        if (path.isBlank()) {
            errors.add(new ValidationError("Path cannot be blank", "identifier", identifier));
            return false;
        }

        // Path should be lowercase alphanumeric with underscores, slashes, and dots
        if (!path.matches("^[a-z0-9_/\\.]+$")) {
            warnings.add(new ValidationError("Path should be lowercase alphanumeric with underscores, slashes, and dots", "identifier", identifier));
        }

        return true;
    }

    /**
     * Validates a JSON string.
     * @param json The JSON string to validate
     * @param context Context for error messages (e.g., "recipe:mod:item")
     * @return true if valid JSON
     */
    public boolean validateJson(String json, String context) {
        if (json == null || json.isBlank()) {
            errors.add(new ValidationError("JSON cannot be null or blank", context, json));
            return false;
        }

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            gson.fromJson(json, Object.class);
            return true;
        } catch (Exception e) {
            errors.add(new ValidationError("Invalid JSON: " + e.getMessage(), context, json));
            return false;
        }
    }

    /**
     * Validates a file path for safety.
     * Ensures the path doesn't escape its intended directory.
     * @param path The path to validate
     * @param baseDir The base directory the path should be relative to
     * @return true if safe
     */
    public boolean validatePathSafety(Path path, Path baseDir) {
        if (path == null) {
            errors.add(new ValidationError("Path cannot be null", "path", null));
            return false;
        }

        try {
            Path normalized = path.normalize();
            Path normalizedBase = baseDir.normalize();

            if (!normalized.startsWith(normalizedBase)) {
                errors.add(new ValidationError("Path escapes base directory", "path", path.toString()));
                return false;
            }

            return true;
        } catch (Exception e) {
            errors.add(new ValidationError("Path validation error: " + e.getMessage(), "path", path.toString()));
            return false;
        }
    }

    /**
     * Validates that a file exists.
     * @param path The path to check
     * @return true if the file exists
     */
    public boolean validateFileExists(Path path) {
        if (path == null) {
            errors.add(new ValidationError("Path cannot be null", "path", null));
            return false;
        }

        if (!Files.exists(path)) {
            errors.add(new ValidationError("File does not exist", "path", path.toString()));
            return false;
        }

        return true;
    }

    /**
     * Gets all validation errors.
     */
    public List<ValidationError> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Gets all validation warnings.
     */
    public List<ValidationError> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * Returns true if there are any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns true if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Clears all errors and warnings.
     */
    public void clear() {
        errors.clear();
        warnings.clear();
    }

    /**
     * Represents a validation error or warning.
     */
    public static class ValidationError {
        private final String message;
        private final String context;
        private final String value;

        public ValidationError(String message, String context, String value) {
            this.message = message;
            this.context = context;
            this.value = value;
        }

        public String getMessage() {
            return message;
        }

        public String getContext() {
            return context;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", context, message, value != null ? value : "null");
        }
    }
}
