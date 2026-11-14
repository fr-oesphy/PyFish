package fr.oesphy.pyfish.compat;

import net.minecraft.resources.ResourceLocation;

final class MinecraftIdentifierCompat {
    private MinecraftIdentifierCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    static ResourceLocation parseId(String value) {
        return ResourceLocation.parse(value);
    }

    static String namespace(Object id) {
        return ((ResourceLocation) id).getNamespace();
    }

    static String path(Object id) {
        return ((ResourceLocation) id).getPath();
    }
}
