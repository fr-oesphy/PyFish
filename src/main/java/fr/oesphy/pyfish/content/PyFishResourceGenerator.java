package fr.oesphy.pyfish.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.oesphy.pyfish.PyFish;
import net.minecraft.server.packs.PackType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PyFishResourceGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<PackType, Map<String, byte[]>> GENERATED_RESOURCES = new EnumMap<>(PackType.class);
    private static final Map<String, Map<String, String>> LOCALIZATIONS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> GENERATED_ITEM_TAGS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> GENERATED_BLOCK_TAGS = new LinkedHashMap<>();
    private static final Set<String> CLIENT_RESOURCE_FOLDERS_REQUIRING_NAMESPACE = Set.of(
            "atlases", "blockstates", "equipment", "font", "items", "lang", "models",
            "particles", "post_effect", "shaders", "sounds", "textures", "waypoint_style");
    private static final Set<String> SERVER_RESOURCE_FOLDERS_REQUIRING_NAMESPACE = Set.of(
            "advancement", "banner_pattern", "chat_type", "damage_type", "dimension", "dimension_type",
            "enchantment", "enchantment_provider", "instrument", "item_modifier", "jukebox_song", "loot_table",
            "painting_variant", "predicate", "recipe", "tags", "trial_spawner", "trim_material", "trim_pattern",
            "wolf_variant", "worldgen");

    static {
        GENERATED_RESOURCES.put(PackType.CLIENT_RESOURCES, new LinkedHashMap<>());
        GENERATED_RESOURCES.put(PackType.SERVER_DATA, new LinkedHashMap<>());
    }

    private PyFishResourceGenerator() {
    }

    public static String resolveIdentifier(String rawId, String defaultNamespace) {
        String value = Objects.requireNonNullElse(rawId, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be blank");
        }

        return value.contains(":") ? value : defaultNamespace + ":" + value;
    }

    public static String resolveNamespacedString(String rawId, String defaultNamespace) {
        return resolveIdentifier(rawId, defaultNamespace);
    }

    public static String resolveTextureReference(String rawId, String defaultNamespace) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }

        String value = rawId.trim().replace('\\', '/');
        int separator = value.indexOf(':');
        String namespace = separator >= 0 ? value.substring(0, separator) : defaultNamespace;
        String path = separator >= 0 ? value.substring(separator + 1) : value;
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return resolveIdentifier(namespace + ":" + path, defaultNamespace);
    }

    public static String preferTextureFolder(String textureId, String folder) {
        if (textureId == null || textureId.isBlank()) {
            return textureId;
        }

        String resolved = resolveIdentifier(textureId, PyFish.MODID);
        String path = pathOf(resolved);
        if (path.contains("/") || textureExists(resolved)) {
            return resolved;
        }

        String conventional = namespaceOf(resolved) + ":" + folder + "/" + path;
        return textureExists(conventional) ? conventional : resolved;
    }

    public static String namespaceOf(String id) {
        String resolved = resolveIdentifier(id, PyFish.MODID);
        int separator = resolved.indexOf(':');
        return separator >= 0 ? resolved.substring(0, separator) : PyFish.MODID;
    }

    public static String pathOf(String id) {
        String resolved = resolveIdentifier(id, PyFish.MODID);
        int separator = resolved.indexOf(':');
        return separator >= 0 ? resolved.substring(separator + 1) : resolved;
    }

    public static synchronized void clear() {
        GENERATED_RESOURCES.values().forEach(Map::clear);
        LOCALIZATIONS.clear();
        GENERATED_ITEM_TAGS.clear();
        GENERATED_BLOCK_TAGS.clear();
    }

    public static synchronized boolean hasResources(PackType packType) {
        materializeDeferredResources();
        return !GENERATED_RESOURCES.getOrDefault(packType, Collections.emptyMap()).isEmpty();
    }

    public static synchronized Map<String, byte[]> getGeneratedResources(PackType packType) {
        materializeDeferredResources();
        return Collections.unmodifiableMap(new LinkedHashMap<>(
                GENERATED_RESOURCES.getOrDefault(packType, Collections.emptyMap())));
    }

    public static synchronized Set<String> getNamespaces(PackType packType) {
        materializeDeferredResources();
        Set<String> namespaces = new LinkedHashSet<>();
        GENERATED_RESOURCES.getOrDefault(packType, Collections.emptyMap()).keySet()
                .forEach(location -> namespaces.add(namespaceOf(location)));
        return namespaces;
    }

    public static void addClientTextResource(String location, String content) {
        addResource(PackType.CLIENT_RESOURCES, location, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void addClientTextResourceIfAbsent(String location, String content) {
        addResourceIfAbsent(PackType.CLIENT_RESOURCES, location, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void addDataTextResource(String location, String content) {
        addResource(PackType.SERVER_DATA, location, content.getBytes(StandardCharsets.UTF_8));
    }

    public static synchronized void addResource(PackType packType, String location, byte[] data) {
        GENERATED_RESOURCES.computeIfAbsent(packType, ignored -> new LinkedHashMap<>())
                .put(resolveIdentifier(location, PyFish.MODID), data);
    }

    public static synchronized void addResourceIfAbsent(PackType packType, String location, byte[] data) {
        GENERATED_RESOURCES.computeIfAbsent(packType, ignored -> new LinkedHashMap<>())
                .putIfAbsent(resolveIdentifier(location, PyFish.MODID), data);
    }

    public static synchronized void addArchiveResources(Path archivePath) throws IOException {
        try (ZipFile archive = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                ResourceLocationParts resource = resourceLocationForArchiveEntry(entry.getName());
                if (resource == null) {
                    continue;
                }

                try (InputStream input = archive.getInputStream(entry)) {
                    // Authored assets are authoritative. API-generated defaults use the same
                    // put-if-absent rule and therefore cannot replace files shipped in a PYZ.
                    addResourceIfAbsent(resource.packType(), resource.location(), input.readAllBytes());
                }
            }
        }
    }

    public static synchronized void addDirectoryResources(Path modDirectory) throws IOException {
        Path fileName = modDirectory.getFileName();
        String namespace = fileName != null ? fileName.toString().toLowerCase(Locale.ROOT) : PyFish.MODID;
        addDirectoryResources(modDirectory, namespace);
    }

    public static synchronized void addDirectoryResources(Path modDirectory, String defaultNamespace) throws IOException {
        String namespace = Objects.requireNonNullElse(defaultNamespace, "").trim().toLowerCase(Locale.ROOT);
        if (namespace.isEmpty()) {
            namespace = PyFish.MODID;
        }
        Path assets = modDirectory.resolve("assets");
        if (Files.isDirectory(assets.resolve("texture"), LinkOption.NOFOLLOW_LINKS)) {
            PyFish.LOGGER.warn("[PyFish/Content] Ignoring assets/texture/ in '{}': expected "
                    + "assets/{}/textures/ (namespace required, 'textures' plural).", modDirectory, namespace);
        }

        addDirectoryResources(assets, PackType.CLIENT_RESOURCES, namespace);
        addDirectoryResources(modDirectory.resolve("data"), PackType.SERVER_DATA, namespace);
    }

    private static void addDirectoryResources(Path resourceRoot, PackType packType, String defaultNamespace)
            throws IOException {
        if (!Files.isDirectory(resourceRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try (var resources = Files.walk(resourceRoot)) {
            resources
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .forEach(path -> {
                        String relativePath = resourceRoot.relativize(path).toString().replace('\\', '/');
                        ResourceLocationParts resource = resourceLocationForExternalDirectory(
                                packType, relativePath, defaultNamespace);
                        if (resource == null) {
                            PyFish.LOGGER.warn("[PyFish/Content] Ignoring resource outside a namespace: {}", path);
                            return;
                        }

                        try {
                            // Load authored files before scripts generate fallbacks. This mirrors PYZ
                            // precedence and keeps the same layout usable during local development.
                            addResourceIfAbsent(resource.packType(), resource.location(), Files.readAllBytes(path));
                        } catch (IOException exception) {
                            throw new DirectoryResourceException(path, exception);
                        }
                    });
        } catch (DirectoryResourceException exception) {
            throw new IOException("Unable to read external resource " + exception.path, exception.getCause());
        }
    }

    private static ResourceLocationParts resourceLocationForExternalDirectory(
            PackType packType, String relativePath, String defaultNamespace) {
        int separator = relativePath.indexOf('/');
        if (separator <= 0 || separator == relativePath.length() - 1) {
            return null;
        }

        String firstFolder = relativePath.substring(0, separator);
        if (packType == PackType.CLIENT_RESOURCES && "texture".equals(firstFolder)) {
            return null;
        }

        Set<String> foldersRequiringNamespace = packType == PackType.CLIENT_RESOURCES
                ? CLIENT_RESOURCE_FOLDERS_REQUIRING_NAMESPACE
                : SERVER_RESOURCE_FOLDERS_REQUIRING_NAMESPACE;
        if (foldersRequiringNamespace.contains(firstFolder)) {
            String root = packType == PackType.CLIENT_RESOURCES ? "assets" : "data";
            PyFish.LOGGER.warn("[PyFish/Content] Ignoring '{}': resource namespace is missing. Expected {}/{}/{}...",
                    relativePath, root, defaultNamespace, firstFolder + "/");
            return null;
        }

        return resourceLocationForArchiveEntry(packType, relativePath);
    }

    public static void addLocalization(String id, String category, String displayName) {
        String resolvedId = resolveIdentifier(id, PyFish.MODID);
        String namespace = namespaceOf(resolvedId);
        LOCALIZATIONS.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>())
                .put(category + "." + namespace + "." + pathOf(resolvedId), displayName);
    }

    public static void addItemTag(String tagId, String value) {
        GENERATED_ITEM_TAGS.computeIfAbsent(resolveIdentifier(tagId, PyFish.MODID), ignored -> new LinkedHashSet<>())
                .add(resolveIdentifier(value, PyFish.MODID));
    }

    public static void addBlockTag(String tagId, String value) {
        GENERATED_BLOCK_TAGS.computeIfAbsent(resolveIdentifier(tagId, PyFish.MODID), ignored -> new LinkedHashSet<>())
                .add(resolveIdentifier(value, PyFish.MODID));
    }

    public static void generateItemModel(String itemId, String texture, String parent) {
        String resolvedId = resolveIdentifier(itemId, PyFish.MODID);
        String namespace = namespaceOf(resolvedId);
        String path = pathOf(resolvedId);
        String resolvedParent = parent != null && !parent.isBlank() ? parent : "minecraft:item/generated";
        String resolvedTexture = texture != null && !texture.isBlank() ? texture : namespace + ":" + path;
        resolvedTexture = preferTextureFolder(resolvedTexture, "item");

        JsonObject root = new JsonObject();
        root.addProperty("parent", resolvedParent);

        if (texture != null || "minecraft:item/generated".equals(resolvedParent)
                || "minecraft:item/handheld".equals(resolvedParent)) {
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", resolvedTexture);
            root.add("textures", textures);
        }

        addClientTextResourceIfAbsent(namespace + ":models/item/" + path + ".json", GSON.toJson(root));
        generateItemDefinition(namespace, path, namespace + ":item/" + path);
        addLocalization(resolvedId, "item", formatName(path));
    }

    public static void generateBlockState(String blockId) {
        String resolvedId = resolveIdentifier(blockId, PyFish.MODID);
        String namespace = namespaceOf(resolvedId);
        String path = pathOf(resolvedId);

        JsonObject root = new JsonObject();
        JsonObject variants = new JsonObject();
        JsonObject normal = new JsonObject();
        normal.addProperty("model", namespace + ":block/" + path);
        variants.add("", normal);
        root.add("variants", variants);

        addClientTextResourceIfAbsent(namespace + ":blockstates/" + path + ".json", GSON.toJson(root));
    }

    public static void generateBlockModel(String blockId, Object textureDefinition) {
        String resolvedId = resolveIdentifier(blockId, PyFish.MODID);
        String namespace = namespaceOf(resolvedId);
        String path = pathOf(resolvedId);

        JsonObject root = new JsonObject();
        JsonObject textures = new JsonObject();

        if (textureDefinition instanceof String texture) {
            root.addProperty("parent", "minecraft:block/cube_all");
            String resolvedTexture = preferTextureFolder(resolveTextureReference(texture, namespace), "block");
            textures.addProperty("all", resolvedTexture);
            textures.addProperty("particle", resolvedTexture);
        } else if (textureDefinition instanceof Map<?, ?> rawMap && !rawMap.isEmpty()) {
            Map<String, String> textureMap = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null && value != null) {
                    textureMap.put(key.toString(), preferTextureFolder(
                            resolveTextureReference(value.toString(), namespace), "block"));
                }
            });

            if (textureMap.containsKey("top") && textureMap.containsKey("bottom") && textureMap.containsKey("side")) {
                root.addProperty("parent", "minecraft:block/cube_bottom_top");
                textures.addProperty("top", textureMap.get("top"));
                textures.addProperty("bottom", textureMap.get("bottom"));
                textures.addProperty("side", textureMap.get("side"));
            } else if (textureMap.containsKey("end") && textureMap.containsKey("side")) {
                root.addProperty("parent", "minecraft:block/cube_column");
                textures.addProperty("end", textureMap.get("end"));
                textures.addProperty("side", textureMap.get("side"));
            } else {
                root.addProperty("parent", "minecraft:block/cube");
                String fallbackTexture = textureMap.getOrDefault("all", textureMap.values().iterator().next());
                textures.addProperty("particle", fallbackTexture);
                textures.addProperty("north", textureMap.getOrDefault("north", fallbackTexture));
                textures.addProperty("south", textureMap.getOrDefault("south", fallbackTexture));
                textures.addProperty("east", textureMap.getOrDefault("east", fallbackTexture));
                textures.addProperty("west", textureMap.getOrDefault("west", fallbackTexture));
                textures.addProperty("up", textureMap.getOrDefault("up", fallbackTexture));
                textures.addProperty("down", textureMap.getOrDefault("down", fallbackTexture));
            }
        } else {
            String defaultTexture = preferTextureFolder(namespace + ":" + path, "block");
            root.addProperty("parent", "minecraft:block/cube_all");
            textures.addProperty("all", defaultTexture);
            textures.addProperty("particle", defaultTexture);
        }

        root.add("textures", textures);
        addClientTextResourceIfAbsent(namespace + ":models/block/" + path + ".json", GSON.toJson(root));
        addLocalization(resolvedId, "block", formatName(path));
    }

    public static void generateBlockItemModel(String blockId) {
        String resolvedId = resolveIdentifier(blockId, PyFish.MODID);
        String namespace = namespaceOf(resolvedId);
        String path = pathOf(resolvedId);

        JsonObject root = new JsonObject();
        root.addProperty("parent", namespace + ":block/" + path);
        addClientTextResourceIfAbsent(namespace + ":models/item/" + path + ".json", GSON.toJson(root));
        generateItemDefinition(namespace, path, namespace + ":item/" + path);
    }

    public static BufferedImage loadTexture(String textureId) {
        byte[] bytes = getTextureBytes(textureId);
        if (bytes == null) {
            return null;
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(input);
        } catch (IOException e) {
            PyFish.LOGGER.error("[PyFish/Content] Failed to decode texture {}: {}", textureId, e.getMessage());
            return null;
        }
    }

    public static byte[] getTextureBytes(String textureId) {
        String resolvedId = resolveIdentifier(textureId, PyFish.MODID);
        String namespace = namespaceOf(resolvedId);
        String path = pathOf(resolvedId);
        String generatedLocation = namespace + ":textures/" + path + ".png";
        byte[] generatedBytes = GENERATED_RESOURCES.get(PackType.CLIENT_RESOURCES).get(generatedLocation);
        if (generatedBytes != null) {
            return generatedBytes;
        }

        String resourcePath = "assets/" + namespace + "/textures/" + path + ".png";
        try (InputStream input = PyFishResourceGenerator.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input != null) {
                return input.readAllBytes();
            }
        } catch (IOException e) {
            PyFish.LOGGER.debug("[PyFish/Content] Failed to read texture {} from classpath: {}", resolvedId,
                    e.getMessage());
        }

        return null;
    }

    public static void registerTextureBytes(String textureId, byte[] data) {
        String resolvedId = resolveIdentifier(textureId, PyFish.MODID);
        addResource(
                PackType.CLIENT_RESOURCES,
                namespaceOf(resolvedId) + ":textures/" + pathOf(resolvedId) + ".png",
                data);
    }

    private static boolean textureExists(String textureId) {
        String resolved = resolveIdentifier(textureId, PyFish.MODID);
        String location = namespaceOf(resolved) + ":textures/" + pathOf(resolved) + ".png";
        if (GENERATED_RESOURCES.get(PackType.CLIENT_RESOURCES).containsKey(location)) {
            return true;
        }

        String classpathLocation = "assets/" + namespaceOf(resolved) + "/textures/" + pathOf(resolved) + ".png";
        return PyFishResourceGenerator.class.getClassLoader().getResource(classpathLocation) != null;
    }

    public static List<String> getItemsByTag(String tagId) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        resolveTagValues(resolveIdentifier(tagId, PyFish.MODID), "item", values, new HashSet<>());
        return new ArrayList<>(values);
    }

    public static void resolveTagValues(String tagId, String registryFolder, Set<String> values, Set<String> visited) {
        String resolvedTagId = resolveIdentifier(tagId, PyFish.MODID);
        String visitKey = registryFolder + ":" + resolvedTagId;
        if (!visited.add(visitKey)) {
            return;
        }

        collectGeneratedTagValues(resolvedTagId, registryFolder, values, visited);
        collectClasspathTagValues(resolvedTagId, registryFolder, values, visited);
    }

    private static void collectGeneratedTagValues(String tagId, String registryFolder, Set<String> values,
            Set<String> visited) {
        Map<String, Set<String>> source = "block".equals(registryFolder) ? GENERATED_BLOCK_TAGS : GENERATED_ITEM_TAGS;
        Set<String> generatedValues = source.getOrDefault(tagId, Collections.emptySet());
        for (String entry : generatedValues) {
            collectTagValue(entry, registryFolder, values, visited);
        }
    }

    private static void collectClasspathTagValues(String tagId, String registryFolder, Set<String> values,
            Set<String> visited) {
        String namespace = namespaceOf(tagId);
        String path = pathOf(tagId);
        String[] candidatePaths = {
                "data/" + namespace + "/tags/" + registryFolder + "/" + path + ".json",
                "data/" + namespace + "/tags/" + registryFolder + "s/" + path + ".json"
        };

        for (String resourcePath : candidatePaths) {
            try {
                Enumeration<URL> urls = PyFishResourceGenerator.class.getClassLoader().getResources(resourcePath);
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    try (InputStream input = url.openStream()) {
                        JsonObject root = JsonParser.parseReader(
                                new java.io.InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                        JsonArray rawValues = root.getAsJsonArray("values");
                        if (rawValues == null) {
                            continue;
                        }

                        for (JsonElement element : rawValues) {
                            if (element.isJsonPrimitive()) {
                                collectTagValue(element.getAsString(), registryFolder, values, visited);
                            } else if (element.isJsonObject()) {
                                JsonObject object = element.getAsJsonObject();
                                if (object.has("id")) {
                                    collectTagValue(object.get("id").getAsString(), registryFolder, values, visited);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                PyFish.LOGGER.debug("[PyFish/Content] Failed to inspect tag {} at {}: {}", tagId, resourcePath,
                        e.getMessage());
            }
        }
    }

    private static void collectTagValue(String rawValue, String registryFolder, Set<String> values, Set<String> visited) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }

        if (rawValue.startsWith("#")) {
            resolveTagValues(resolveIdentifier(rawValue.substring(1), PyFish.MODID), registryFolder, values, visited);
            return;
        }

        values.add(resolveIdentifier(rawValue, PyFish.MODID));
    }

    private static void materializeDeferredResources() {
        // Scripts may add translations and tags throughout startup. Materialize at pack-read
        // time so the loader sees one complete snapshot rather than an early partial file.
        LOCALIZATIONS.forEach(PyFishResourceGenerator::addDataDrivenLocalization);
        GENERATED_ITEM_TAGS.forEach((tagId, values) -> addTagResource("item", tagId, values));
        GENERATED_BLOCK_TAGS.forEach((tagId, values) -> addTagResource("block", tagId, values));
    }

    private static void addDataDrivenLocalization(String namespace, Map<String, String> entries) {
        if (entries.isEmpty()) {
            return;
        }

        String location = namespace + ":lang/en_us.json";
        JsonObject root = new JsonObject();
        byte[] existing = GENERATED_RESOURCES.get(PackType.CLIENT_RESOURCES).get(location);
        if (existing != null) {
            try {
                JsonObject existingRoot = JsonParser.parseString(new String(existing, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                existingRoot.entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
            } catch (Exception e) {
                PyFish.LOGGER.debug("[PyFish/Content] Ignoring invalid existing lang file {}: {}", location,
                        e.getMessage());
            }
        }

        entries.forEach((key, value) -> {
            if (!root.has(key)) {
                root.addProperty(key, value);
            }
        });

        byte[] bytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        GENERATED_RESOURCES.get(PackType.CLIENT_RESOURCES).put(location, bytes);
    }

    private static void generateItemDefinition(String namespace, String path, String model) {
        JsonObject root = new JsonObject();
        JsonObject modelObject = new JsonObject();
        modelObject.addProperty("type", "minecraft:model");
        modelObject.addProperty("model", model);
        root.add("model", modelObject);
        addClientTextResourceIfAbsent(namespace + ":items/" + path + ".json", GSON.toJson(root));
    }

    private static ResourceLocationParts resourceLocationForArchiveEntry(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("assets/")) {
            return resourceLocationForArchiveEntry(PackType.CLIENT_RESOURCES, normalized.substring("assets/".length()));
        }
        if (normalized.startsWith("data/")) {
            return resourceLocationForArchiveEntry(PackType.SERVER_DATA, normalized.substring("data/".length()));
        }
        return null;
    }

    private static ResourceLocationParts resourceLocationForArchiveEntry(PackType packType, String relativePath) {
        int separator = relativePath.indexOf('/');
        if (separator <= 0 || separator == relativePath.length() - 1) {
            return null;
        }

        String namespace = relativePath.substring(0, separator);
        String path = relativePath.substring(separator + 1);
        return new ResourceLocationParts(packType, namespace + ":" + path);
    }

    private static void addTagResource(String registryFolder, String tagId, Set<String> values) {
        JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        root.add("values", array);

        String location = namespaceOf(tagId) + ":tags/" + registryFolder + "/" + pathOf(tagId) + ".json";
        GENERATED_RESOURCES.get(PackType.SERVER_DATA)
                .put(location, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
    }

    private static String formatName(String rawId) {
        String[] parts = rawId.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return builder.toString().trim();
    }

    private record ResourceLocationParts(PackType packType, String location) {
    }

    private static final class DirectoryResourceException extends RuntimeException {
        private final Path path;

        private DirectoryResourceException(Path path, IOException cause) {
            super(cause);
            this.path = path;
        }
    }
}
