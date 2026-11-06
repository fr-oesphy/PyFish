package fr.oesphy.pyfish.api.wrapper;

import fr.oesphy.pyfish.api.convert.TypeCoerce;
import net.minecraft.nbt.*;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Wraps a {@link CompoundTag} and exposes it to Python scripts as a dict-like object
 * via the {@link ProxyObject} interface.
 *
 * <p>Reading a member converts it from its NBT representation to a plain Java type
 * (or nested {@link NbtWrapper}).  Writing a member converts the Python value back
 * to the appropriate NBT type.
 */
public final class NbtWrapper implements ProxyObject {

    private final CompoundTag tag;

    private NbtWrapper(CompoundTag tag) {
        this.tag = Objects.requireNonNull(tag, "CompoundTag must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Wraps an existing {@link CompoundTag}.
     *
     * @param tag the tag; must not be {@code null}
     * @return a new {@link NbtWrapper}
     */
    public static NbtWrapper of(CompoundTag tag) {
        return new NbtWrapper(tag);
    }

    /**
     * Returns a wrapper around an empty {@link CompoundTag}.
     *
     * @return an empty wrapper
     */
    public static NbtWrapper empty() {
        return new NbtWrapper(new CompoundTag());
    }

    // -------------------------------------------------------------------------
    // ProxyObject implementation
    // -------------------------------------------------------------------------

    @Override
    public Object getMember(String key) {
        if (!tag.contains(key)) {
            return null;
        }
        Tag entry = tag.get(key);
        return nbtTagToJava(entry);
    }

    @Override
    public boolean hasMember(String key) {
        return tag.contains(key);
    }

    @Override
    public Object getMemberKeys() {
        return new org.graalvm.polyglot.proxy.ProxyArray() {
            private final List<String> keys = new ArrayList<>(tag.getAllKeys());

            @Override
            public Object get(long index) {
                return keys.get((int) index);
            }

            @Override
            public void set(long index, Value value) {
                throw new UnsupportedOperationException("NbtWrapper key list is immutable");
            }

            @Override
            public long getSize() {
                return keys.size();
            }
        };
    }

    @Override
    public void putMember(String key, Value value) {
        if (value == null || value.isNull()) {
            tag.remove(key);
            return;
        }
        if (value.isString()) {
            tag.putString(key, value.asString());
        } else if (value.isBoolean()) {
            tag.putBoolean(key, value.asBoolean());
        } else if (value.fitsInInt()) {
            tag.putInt(key, value.asInt());
        } else if (value.fitsInLong()) {
            tag.putLong(key, value.asLong());
        } else if (value.fitsInFloat()) {
            tag.putFloat(key, value.asFloat());
        } else if (value.fitsInDouble()) {
            tag.putDouble(key, value.asDouble());
        } else if (value.hasMembers()) {
            tag.put(key, TypeCoerce.toCompoundTag(value));
        } else {
            tag.putString(key, value.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Helper: NBT → Java
    // -------------------------------------------------------------------------

    private static Object nbtTagToJava(Tag tag) {
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
            return NbtWrapper.of(ct);
        } else if (tag instanceof ListTag listTag) {
            List<Object> list = new ArrayList<>();
            for (Tag t : listTag) {
                list.add(nbtTagToJava(t));
            }
            return list;
        } else if (tag != null) {
            return tag.getAsString();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Public convenience methods
    // -------------------------------------------------------------------------

    /** @return the underlying {@link CompoundTag} */
    public CompoundTag unwrap() {
        return tag;
    }

    /** @return {@code true} if the underlying tag has no keys */
    public boolean isEmpty() {
        return tag.isEmpty();
    }

    /** @return an immutable snapshot of the tag's key set */
    public Set<String> keys() {
        return Set.copyOf(tag.getAllKeys());
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return tag.toString();
    }
}
