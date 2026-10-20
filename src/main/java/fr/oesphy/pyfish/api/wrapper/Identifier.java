package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.resources.ResourceLocation;

/**
 * Wrapper for Minecraft identifiers (ResourceLocation).
 */
public class Identifier extends BaseWrapper<ResourceLocation> {
    public Identifier(ResourceLocation location) {
        super(location);
    }

    public Identifier(String namespace, String path) {
        super(ResourceLocation.tryParse(namespace + ":" + path));
    }

    public Identifier(String id) {
        super(ResourceLocation.tryParse(id));
    }

    public String getNamespace() {
        return wrapped.getNamespace();
    }

    public String getPath() {
        return wrapped.getPath();
    }

    public String toString() {
        return wrapped.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Identifier)) return false;
        return wrapped.equals(((Identifier) obj).wrapped);
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }
}
