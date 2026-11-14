package fr.oesphy.pyfish.api.convert;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static conversion utilities between Python {@link Value} objects and Java/Minecraft types.
 *
 * <p>All methods are null-safe and return sensible defaults instead of throwing.
 */
public final class TypeCoerce {

    // -------------------------------------------------------------------------
    // ResourceLocation helpers
    // -------------------------------------------------------------------------

    /**
     * Parses a resource location string of the form {@code "namespace:path"}.
     * If no colon is present the namespace defaults to {@code "minecraft"}.
     *
     * @param s the string to parse; must not be {@code null}
     * @return a {@link ResourceLocation}
     */
    public static ResourceLocation toResourceLocation(String s) {
        if (s == null) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "air");
        }
        int colon = s.indexOf(':');
        if (colon < 0) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", s);
        }
        String namespace = s.substring(0, colon);
        String path = s.substring(colon + 1);
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /**
     * Converts a GraalPy {@link Value} (expected to be a string) to a {@link ResourceLocation}.
     *
     * @param v the Python value; must be string-like
     * @return a {@link ResourceLocation}
     */
    public static ResourceLocation toResourceLocation(Value v) {
        return toResourceLocation(coerceString(v));
    }

    // -------------------------------------------------------------------------
    // NBT helpers
    // -------------------------------------------------------------------------

    /**
     * Recursively converts a {@link CompoundTag} into a {@code Map<String, Object>}.
     * Values are converted to their Java primitives or nested maps/lists.
     *
     * @param tag the tag to convert; must not be {@code null}
     * @return a mutable map representation
     */
    public static Map<String, Object> fromNbt(CompoundTag tag) {
        Map<String, Object> map = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            Tag entry = tag.get(key);
            map.put(key, nbtTagToObject(entry));
        }
        return map;
    }

    private static Object nbtTagToObject(Tag tag) {
        if (tag instanceof StringTag st) {
            return st.getAsString();
        } else if (tag instanceof IntTag it) {
            return it.getAsInt();
        } else if (tag instanceof LongTag lt) {
            return lt.getAsLong();
        } else if (tag instanceof DoubleTag dt) {
            return dt.getAsDouble();
        } else if (tag instanceof FloatTag ft) {
            return ft.getAsFloat();
        } else if (tag instanceof ByteTag bt) {
            return bt.getAsByte();
        } else if (tag instanceof ShortTag sht) {
            return sht.getAsShort();
        } else if (tag instanceof CompoundTag ct) {
            return fromNbt(ct);
        } else if (tag instanceof ListTag listTag) {
            List<Object> list = new ArrayList<>();
            for (Tag t : listTag) {
                list.add(nbtTagToObject(t));
            }
            return list;
        } else if (tag != null) {
            return tag.getAsString();
        }
        return null;
    }

    /**
     * Converts a dict-like Python {@link Value} to a {@link CompoundTag} recursively.
     *
     * @param v the Python dict value
     * @return a new {@link CompoundTag}
     */
    public static CompoundTag toCompoundTag(Value v) {
        CompoundTag tag = new CompoundTag();
        if (v == null || !v.hasMembers()) {
            return tag;
        }
        for (String key : v.getMemberKeys()) {
            Value member = v.getMember(key);
            if (member == null) {
                continue;
            }
            putValueIntoTag(tag, key, member);
        }
        return tag;
    }

    private static void putValueIntoTag(CompoundTag tag, String key, Value v) {
        if (v.isString()) {
            tag.putString(key, v.asString());
        } else if (v.isBoolean()) {
            tag.putBoolean(key, v.asBoolean());
        } else if (v.fitsInInt()) {
            tag.putInt(key, v.asInt());
        } else if (v.fitsInLong()) {
            tag.putLong(key, v.asLong());
        } else if (v.fitsInDouble()) {
            tag.putDouble(key, v.asDouble());
        } else if (v.hasMembers()) {
            tag.put(key, toCompoundTag(v));
        }
    }

    // -------------------------------------------------------------------------
    // Primitive coercions
    // -------------------------------------------------------------------------

    /**
     * Returns the string value of {@code v}; never {@code null}.
     *
     * @param v the Python value
     * @return string representation
     */
    public static String coerceString(Value v) {
        if (v == null) return "";
        try {
            if (v.isString()) return v.asString();
            return v.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Returns the int value of {@code v}, or {@code 0} on failure.
     *
     * @param v the Python value
     * @return int value
     */
    public static int coerceInt(Value v) {
        if (v == null) return 0;
        try {
            return v.asInt();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns the double value of {@code v}, or {@code 0.0} on failure.
     *
     * @param v the Python value
     * @return double value
     */
    public static double coerceDouble(Value v) {
        if (v == null) return 0.0;
        try {
            return v.asDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Returns the boolean value of {@code v}, or {@code false} on failure.
     *
     * @param v the Python value
     * @return boolean value
     */
    public static boolean coerceBool(Value v) {
        if (v == null) return false;
        try {
            return v.asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Non-instantiable
    // -------------------------------------------------------------------------

    private TypeCoerce() {}
}
