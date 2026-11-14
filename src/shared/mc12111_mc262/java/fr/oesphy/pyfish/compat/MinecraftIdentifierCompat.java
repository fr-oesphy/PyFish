package fr.oesphy.pyfish.compat;

import net.minecraft.resources.Identifier;

final class MinecraftIdentifierCompat {
    private MinecraftIdentifierCompat() {
    }

    static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    static Identifier parseId(String value) {
        return Identifier.parse(value);
    }

    static String namespace(Object id) {
        return ((Identifier) id).getNamespace();
    }

    static String path(Object id) {
        return ((Identifier) id).getPath();
    }
}
