package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Lightweight wrapper around {@link ResourceLocation} for use in Python scripts.
 *
 * <p>Instances are immutable and comparable by value.
 */
public final class IdentifierWrapper {

    private final ResourceLocation id;

    private IdentifierWrapper(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "ResourceLocation must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Parses a string of the form {@code "namespace:path"} and wraps it.
     *
     * @param s the identifier string; must not be {@code null}
     * @return a new {@link IdentifierWrapper}
     */
    public static IdentifierWrapper of(String s) {
        Objects.requireNonNull(s, "identifier string must not be null");
        int colon = s.indexOf(':');
        if (colon < 0) {
            return new IdentifierWrapper(ResourceLocation.fromNamespaceAndPath("minecraft", s));
        }
        return new IdentifierWrapper(
                ResourceLocation.fromNamespaceAndPath(s.substring(0, colon), s.substring(colon + 1)));
    }

    /**
     * Wraps an existing {@link ResourceLocation}.
     *
     * @param rl the resource location; must not be {@code null}
     * @return a new {@link IdentifierWrapper}
     */
    public static IdentifierWrapper of(ResourceLocation rl) {
        return new IdentifierWrapper(rl);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** @return the namespace component (e.g. {@code "minecraft"}) */
    public String namespace() {
        return id.getNamespace();
    }

    /** @return the path component (e.g. {@code "stone"}) */
    public String path() {
        return id.getPath();
    }

    /** @return the underlying {@link ResourceLocation} */
    public ResourceLocation unwrap() {
        return id;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentifierWrapper other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
