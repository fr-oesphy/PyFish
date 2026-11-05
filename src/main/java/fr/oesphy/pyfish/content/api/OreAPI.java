package fr.oesphy.pyfish.content.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.OreBiomeSelection;
import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishContentSupport;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class OreAPI {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private final PyFishContentManager manager;
    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    public OreAPI(PyFishContentManager manager) {
        this.manager = manager;
    }

    public void registerOre(String id, Map<String, Object> properties) {
        String featureId = checkedId(manager.resolveId(id), "ore id");
        Definition definition = Definition.parse(featureId, PyFishContentSupport.snapshotProperties(properties));
        if (!registered.add(featureId)) {
            throw new IllegalArgumentException("Ore feature already registered: " + featureId);
        }

        try {
            manager.registerOrePlacement(featureId, definition.biomes());
            String namespace = PyFishResourceGenerator.namespaceOf(featureId);
            String path = PyFishResourceGenerator.pathOf(featureId);
            PyFishResourceGenerator.addDataTextResource(namespace + ":worldgen/configured_feature/" + path + ".json",
                    definition.configuredFeature().toString());
            PyFishResourceGenerator.addDataTextResource(namespace + ":worldgen/placed_feature/" + path + ".json",
                    definition.placedFeature().toString());
            PyFish.LOGGER.info("[PyFish/Content] Registered ore feature {}", featureId);
        } catch (RuntimeException e) {
            registered.remove(featureId);
            throw e;
        }
    }

    private static String checkedId(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException(field + " must be a valid namespaced identifier: " + value);
        }
        return value;
    }

    record Definition(String id, List<Target> targets, int size, int count, int minY, int maxY,
            String distribution, double discardChance, OreBiomeSelection biomes) {
        static Definition parse(String id, Map<String, Object> properties) {
            String namespace = PyFishResourceGenerator.namespaceOf(id);
            int size = integer(properties, "size", 9, 1, 64);
            int count = integer(properties, "count", 10, 1, 256);
            int minY = integer(properties, "minY", -64, -2048, 2048);
            int maxY = integer(properties, "maxY", 64, -2048, 2048);
            if (minY >= maxY) {
                throw new IllegalArgumentException("minY must be lower than maxY");
            }

            String distribution = string(properties.getOrDefault("distribution", "uniform"), "distribution");
            if (!distribution.equals("uniform") && !distribution.equals("trapezoid")) {
                throw new IllegalArgumentException("distribution must be 'uniform' or 'trapezoid'");
            }
            double discardChance = decimal(properties, "discardChanceOnAirExposure", 0.0);
            if (discardChance < 0 || discardChance > 1) {
                throw new IllegalArgumentException("discardChanceOnAirExposure must be between 0 and 1");
            }

            List<Target> targets = targets(properties, namespace);
            OreBiomeSelection biomes = biomes(properties.getOrDefault("biomes", "overworld"), namespace);
            return new Definition(id, targets, size, count, minY, maxY, distribution, discardChance, biomes);
        }

        private static List<Target> targets(Map<String, Object> properties, String namespace) {
            Object rawTargets = properties.get("targets");
            if (rawTargets != null) {
                if (properties.containsKey("block") || properties.containsKey("deepslateBlock")) {
                    throw new IllegalArgumentException("Use either targets or block/deepslateBlock, not both");
                }
                if (!(rawTargets instanceof List<?> entries) || entries.isEmpty()) {
                    throw new IllegalArgumentException("targets must be a nonempty list");
                }
                List<Target> result = new ArrayList<>();
                for (Object entry : entries) {
                    if (!(entry instanceof Map<?, ?> target)) {
                        throw new IllegalArgumentException("Each ore target must be a map");
                    }
                    String replaceable = string(target.get("replaceable"), "target.replaceable");
                    String block = resolve(string(target.get("block"), "target.block"), namespace, "target.block");
                    result.add(new Target(replaceable.startsWith("#"), resolve(
                            replaceable.startsWith("#") ? replaceable.substring(1) : replaceable,
                            namespace, "target.replaceable"), block));
                }
                return List.copyOf(result);
            }

            String block = resolve(string(properties.get("block"), "block"), namespace, "block");
            List<Target> result = new ArrayList<>();
            result.add(new Target(true, "minecraft:stone_ore_replaceables", block));
            if (properties.containsKey("deepslateBlock")) {
                String deepslate = resolve(string(properties.get("deepslateBlock"), "deepslateBlock"),
                        namespace, "deepslateBlock");
                result.add(new Target(true, "minecraft:deepslate_ore_replaceables", deepslate));
            }
            return List.copyOf(result);
        }

        private static OreBiomeSelection biomes(Object raw, String namespace) {
            if (raw instanceof List<?> entries) {
                if (entries.isEmpty()) {
                    throw new IllegalArgumentException("biomes cannot be empty");
                }
                List<String> ids = new ArrayList<>();
                for (Object entry : entries) {
                    ids.add(resolve(string(entry, "biomes entry"), namespace, "biomes entry"));
                }
                return new OreBiomeSelection(OreBiomeSelection.Kind.BIOMES, ids);
            }

            String value = string(raw, "biomes");
            return switch (value) {
                case "overworld" -> OreBiomeSelection.dimension(OreBiomeSelection.Kind.OVERWORLD);
                case "nether" -> OreBiomeSelection.dimension(OreBiomeSelection.Kind.NETHER);
                case "end" -> OreBiomeSelection.dimension(OreBiomeSelection.Kind.END);
                default -> value.startsWith("#")
                        ? new OreBiomeSelection(OreBiomeSelection.Kind.TAG,
                                List.of(resolve(value.substring(1), namespace, "biome tag")))
                        : new OreBiomeSelection(OreBiomeSelection.Kind.BIOMES,
                                List.of(resolve(value, namespace, "biome")));
            };
        }

        JsonObject configuredFeature() {
            JsonObject root = new JsonObject();
            root.addProperty("type", "minecraft:ore");
            JsonObject config = new JsonObject();
            config.addProperty("size", size);
            config.addProperty("discard_chance_on_air_exposure", discardChance);
            JsonArray targetArray = new JsonArray();
            for (Target target : targets) {
                JsonObject entry = new JsonObject();
                JsonObject predicate = new JsonObject();
                predicate.addProperty("predicate_type", target.tag() ? "minecraft:tag_match" : "minecraft:block_match");
                predicate.addProperty(target.tag() ? "tag" : "block", target.replaceable());
                entry.add("target", predicate);
                JsonObject state = new JsonObject();
                state.addProperty("Name", target.block());
                entry.add("state", state);
                targetArray.add(entry);
            }
            config.add("targets", targetArray);
            root.add("config", config);
            return root;
        }

        JsonObject placedFeature() {
            JsonObject root = new JsonObject();
            root.addProperty("feature", id);
            JsonArray placement = new JsonArray();
            JsonObject countModifier = new JsonObject();
            countModifier.addProperty("type", "minecraft:count");
            countModifier.addProperty("count", count);
            placement.add(countModifier);
            JsonObject spread = new JsonObject();
            spread.addProperty("type", "minecraft:in_square");
            placement.add(spread);
            JsonObject heightModifier = new JsonObject();
            heightModifier.addProperty("type", "minecraft:height_range");
            JsonObject height = new JsonObject();
            height.addProperty("type", "minecraft:" + distribution);
            JsonObject min = new JsonObject();
            min.addProperty("absolute", minY);
            JsonObject max = new JsonObject();
            max.addProperty("absolute", maxY);
            height.add("min_inclusive", min);
            height.add("max_inclusive", max);
            heightModifier.add("height", height);
            placement.add(heightModifier);
            JsonObject biomeModifier = new JsonObject();
            biomeModifier.addProperty("type", "minecraft:biome");
            placement.add(biomeModifier);
            root.add("placement", placement);
            return root;
        }

        private static String resolve(String raw, String namespace, String field) {
            return checkedId(PyFishResourceGenerator.resolveIdentifier(raw, namespace), field);
        }

        private static String string(Object raw, String field) {
            if (!(raw instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(field + " must be a nonempty string");
            }
            return text.trim();
        }

        private static int integer(Map<String, Object> properties, String field, int fallback, int min, int max) {
            Object raw = properties.getOrDefault(field, fallback);
            if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() != number.intValue() || number.intValue() < min || number.intValue() > max) {
                throw new IllegalArgumentException(field + " must be an integer between " + min + " and " + max);
            }
            return number.intValue();
        }

        private static double decimal(Map<String, Object> properties, String field, double fallback) {
            Object raw = properties.getOrDefault(field, fallback);
            if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                throw new IllegalArgumentException(field + " must be a finite number");
            }
            return number.doubleValue();
        }
    }

    record Target(boolean tag, String replaceable, String block) {
    }
}
