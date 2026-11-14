package fr.oesphy.pyfish.content;

import fr.oesphy.pyfish.PyFish;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ContentProperties {
    private static final Set<String> COMMON = Set.of("texture", "model", "tab", "events");
    private static final Set<String> ITEM = Set.of("stacksTo", "durability", "fireResistant");
    private static final Set<String> FOOD = Set.of("alwaysEdible");
    private static final Set<String> TOOL = Set.of("speed", "tier", "damage", "damageBonus", "attackSpeed",
            "enchantmentValue", "repairItem");
    private static final Set<String> BLOCK = Set.of("destroyTime", "explosionResistance", "requiresCorrectToolForDrops",
            "renderType", "noOcclusion", "noCollision", "shape", "collisionShape", "lightLevel", "sound",
            "friction", "speedFactor", "jumpFactor", "drops", "miningTool", "requiredTier");
    private static final Map<Block, String> RENDER_LAYERS = new ConcurrentHashMap<>();

    private ContentProperties() {
    }

    public static void validate(String kind, Map<String, Object> properties) {
        if (properties == null) return;
        properties.keySet().forEach(key -> {
            boolean known = COMMON.contains(key) || ITEM.contains(key)
                    || (kind.equals("food") && FOOD.contains(key))
                    || (kind.equals("tool") && TOOL.contains(key))
                    || (kind.equals("block") && BLOCK.contains(key));
            if (!known) PyFish.LOGGER.warn("[PyFish/Content] Unknown {} property '{}'; it will be ignored", kind, key);
        });
        if (properties.containsKey("stacksTo")) integer(properties, "stacksTo", 64, 1, 99);
        if (properties.containsKey("durability")) integer(properties, "durability", 0, 0, Integer.MAX_VALUE);
        for (String key : List.of("fireResistant", "alwaysEdible", "requiresCorrectToolForDrops", "noCollision", "noOcclusion")) {
            if (properties.containsKey(key) && !(properties.get(key) instanceof Boolean)) {
                throw new IllegalArgumentException(key + " must be a boolean");
            }
        }
        for (String key : List.of("destroyTime", "explosionResistance", "friction", "speedFactor", "jumpFactor",
                "speed", "damage", "damageBonus", "attackSpeed")) {
            if (properties.containsKey(key) && (!(properties.get(key) instanceof Number number)
                    || !Float.isFinite(number.floatValue()))) {
                throw new IllegalArgumentException(key + " must be a finite number");
            }
        }
        if (kind.equals("tool")) {
            integer(properties, "durability", 250, 1, Integer.MAX_VALUE);
            incorrectBlocks(properties);
        }
        if (kind.equals("block")) {
            renderType(properties);
            shape(properties.get("shape"));
            shape(properties.get("collisionShape"));
            integer(properties, "lightLevel", 0, 0, 15);
            if (properties.containsKey("miningTool") && !Set.of("pickaxe", "axe", "shovel", "hoe")
                    .contains(properties.get("miningTool"))) {
                throw new IllegalArgumentException("miningTool must be pickaxe, axe, shovel or hoe");
            }
            if (properties.containsKey("requiredTier")) tier(properties, "requiredTier");
            if (properties.containsKey("sound") && !Set.of("stone", "wood", "metal", "glass", "gravel", "sand", "grass", "wool")
                    .contains(properties.get("sound"))) {
                throw new IllegalArgumentException("Unknown block sound: " + properties.get("sound"));
            }
            if (properties.containsKey("drops") && (!(properties.get("drops") instanceof String drop)
                    || !drop.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+"))) {
                throw new IllegalArgumentException("drops must be self, none or an item identifier");
            }
        }
    }

    public static int integer(Map<String, Object> properties, String key, int fallback, int min, int max) {
        Object value = properties.getOrDefault(key, fallback);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() < min || number.intValue() > max) {
            throw new IllegalArgumentException(key + " must be an integer between " + min + " and " + max);
        }
        return number.intValue();
    }

    public static String tier(Map<String, Object> properties, String key) {
        Object value = properties.getOrDefault(key, "iron");
        if (value instanceof Number) {
            return switch (integer(properties, key, 2, 0, 4)) {
                case 0 -> "wood";
                case 1 -> "stone";
                case 2 -> "iron";
                case 3 -> "diamond";
                case 4 -> "netherite";
                default -> throw new IllegalArgumentException("Invalid tier");
            };
        }
        String name = value.toString().toLowerCase(Locale.ROOT);
        if (name.equals("wooden")) name = "wood";
        if (name.equals("golden")) name = "gold";
        if (!Set.of("wood", "gold", "stone", "iron", "diamond", "netherite").contains(name)) {
            throw new IllegalArgumentException(key + " must be a tier from 0 to 4 or a vanilla material name");
        }
        return name;
    }

    public static TagKey<Block> incorrectBlocks(Map<String, Object> properties) {
        return switch (tier(properties, "tier")) {
            case "wood" -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case "gold" -> BlockTags.INCORRECT_FOR_GOLD_TOOL;
            case "stone" -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case "diamond" -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            case "netherite" -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
            default -> BlockTags.INCORRECT_FOR_IRON_TOOL;
        };
    }

    public static String renderType(Map<String, Object> properties) {
        String value = properties.getOrDefault("renderType", "solid").toString().toLowerCase(Locale.ROOT);
        if (!Set.of("solid", "cutout", "cutout_mipped", "translucent").contains(value)) {
            throw new IllegalArgumentException("renderType must be solid, cutout, cutout_mipped or translucent");
        }
        return value;
    }

    public static BlockBehaviour.Properties applyBlock(BlockBehaviour.Properties target, Map<String, Object> values) {
        if (values == null) return target;
        if (Boolean.TRUE.equals(values.get("noCollision"))) fr.oesphy.pyfish.compat.MinecraftBlockCompat.noCollision(target);
        if (Boolean.TRUE.equals(values.get("noOcclusion")) || !renderType(values).equals("solid")) target.noOcclusion();
        int light = integer(values, "lightLevel", 0, 0, 15);
        target.lightLevel(state -> light);
        if (values.containsKey("friction")) target.friction(((Number) values.get("friction")).floatValue());
        if (values.containsKey("speedFactor")) target.speedFactor(((Number) values.get("speedFactor")).floatValue());
        if (values.containsKey("jumpFactor")) target.jumpFactor(((Number) values.get("jumpFactor")).floatValue());
        if (values.containsKey("sound")) target.sound(switch (values.get("sound").toString().toLowerCase(Locale.ROOT)) {
            case "wood" -> SoundType.WOOD;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "gravel" -> SoundType.GRAVEL;
            case "sand" -> SoundType.SAND;
            case "grass" -> SoundType.GRASS;
            case "wool" -> SoundType.WOOL;
            case "stone" -> SoundType.STONE;
            default -> throw new IllegalArgumentException("Unknown block sound: " + values.get("sound"));
        });
        return target;
    }

    public static VoxelShape shape(Object value) {
        if (value == null) return null;
        if (!(value instanceof List<?> boxes)) throw new IllegalArgumentException("A shape must be a box or a list of boxes");
        if (boxes.isEmpty()) return Shapes.empty();
        if (boxes.getFirst() instanceof Number) {
            if (boxes.size() != 6) throw new IllegalArgumentException("A box requires six coordinates in block pixels (0..16)");
            double[] coordinates = new double[6];
            for (int i = 0; i < 6; i++) {
                if (!(boxes.get(i) instanceof Number n) || !Double.isFinite(n.doubleValue())
                        || n.doubleValue() < 0 || n.doubleValue() > 16) {
                    throw new IllegalArgumentException("Shape coordinates must be finite numbers between 0 and 16");
                }
                coordinates[i] = n.doubleValue();
            }
            for (int i = 0; i < 3; i++) if (coordinates[i] > coordinates[i + 3]) {
                throw new IllegalArgumentException("A box's minimum coordinates cannot exceed its maximum coordinates");
            }
            return Block.box(coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4], coordinates[5]);
        }
        VoxelShape result = Shapes.empty();
        for (Object box : boxes) result = Shapes.or(result, shape(box));
        return result;
    }

    public static void trackRenderLayer(Block block, Map<String, Object> properties) {
        RENDER_LAYERS.put(block, renderType(properties));
    }

    public static Map<Block, String> renderLayers() {
        return Map.copyOf(RENDER_LAYERS);
    }
}
