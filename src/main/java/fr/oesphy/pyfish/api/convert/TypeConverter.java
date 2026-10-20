package fr.oesphy.pyfish.api.convert;

import fr.oesphy.pyfish.api.wrapper.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.graalvm.polyglot.Value;

/**
 * Utility class for converting between Java and Python types.
 * Handles automatic coercion for common Minecraft types.
 */
public class TypeConverter {

    /**
     * Converts a Value from Python to a Java object.
     */
    public static Object fromPython(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isString()) {
            return value.asString();
        }

        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            return value.asDouble();
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        // Handle lists and dicts - return as-is for now
        // Full implementation would need more work
        return value;
    }

    /**
     * Converts a string to a ResourceLocation/Identifier.
     * Accepts "minecraft:stone", "#minecraft:logs", or "@mod" formats.
     */
    public static ResourceLocation toIdentifier(String id) {
        if (id == null || id.isEmpty()) {
            return ResourceLocation.tryParse("minecraft:air");
        }

        // Handle tag prefixes
        if (id.startsWith("#")) {
            id = id.substring(1);
        }

        ResourceLocation result = ResourceLocation.tryParse(id);
        return result != null ? result : ResourceLocation.tryParse("minecraft:air");
    }

    /**
     * Wraps a Java object in a Python-friendly wrapper if available.
     */
    public static Object wrap(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof BlockPos) {
            return new Pos((BlockPos) obj);
        }

        if (obj instanceof ResourceLocation) {
            return new Identifier((ResourceLocation) obj);
        }

        if (obj instanceof ServerPlayer) {
            return new PlayerWrapper((ServerPlayer) obj);
        }

        if (obj instanceof ServerLevel) {
            return new LevelWrapper((ServerLevel) obj);
        }

        if (obj instanceof Entity) {
            return new EntityWrapper((Entity) obj);
        }

        if (obj instanceof ItemStack) {
            return new ItemStackWrapper((ItemStack) obj);
        }

        // Default: return as-is
        return obj;
    }

    /**
     * Unwraps a wrapper to get the underlying Java object.
     */
    public static Object unwrap(Object obj) {
        if (obj instanceof BaseWrapper<?>) {
            return ((BaseWrapper<?>) obj).unwrap();
        }
        return obj;
    }
}
