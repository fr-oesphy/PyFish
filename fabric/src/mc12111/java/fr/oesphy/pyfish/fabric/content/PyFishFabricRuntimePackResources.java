package fr.oesphy.pyfish.fabric.content;

import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.PyFishPackMetadataFactory;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PyFishFabricRuntimePackResources implements PackResources {
    private final PackLocationInfo locationInfo;
    private final PackType packType;

    public PyFishFabricRuntimePackResources(PackLocationInfo locationInfo, PackType packType) {
        this.locationInfo = locationInfo;
        this.packType = packType;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... paths) {
        if ("pack.mcmeta".equals(String.join("/", paths))) {
            byte[] metadata = PyFishPackMetadataFactory.createPackMcmetaBytes(packType);
            if (metadata != null) {
                return () -> new ByteArrayInputStream(metadata);
            }
        }
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType requestedPackType, Identifier location) {
        if (requestedPackType != this.packType) {
            return null;
        }
        byte[] data = resolveGeneratedResource(requestedPackType, location);
        return data != null ? () -> new ByteArrayInputStream(data) : null;
    }

    @Override
    public void listResources(PackType requestedPackType, String namespace, String path, ResourceOutput resourceOutput) {
        if (requestedPackType != this.packType) {
            return;
        }
        for (Map.Entry<String, byte[]> entry : PyFishResourceGenerator.getGeneratedResources(requestedPackType).entrySet()) {
            String location = entry.getKey();
            if (!PyFishResourceGenerator.namespaceOf(location).equals(namespace)
                    || !PyFishResourceGenerator.pathOf(location).startsWith(path)) {
                continue;
            }

            Identifier identifier = (Identifier) MinecraftCompat.parseId(location);
            byte[] bytes = entry.getValue();
            resourceOutput.accept(identifier, () -> new ByteArrayInputStream(bytes));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType requestedPackType) {
        return requestedPackType == this.packType ? PyFishResourceGenerator.getNamespaces(requestedPackType) : Set.of();
    }

    @Override
    public @Nullable <T> T getMetadataSection(MetadataSectionType<T> metadataType) throws IOException {
        return PyFishPackMetadataFactory.create(metadataType, packType);
    }

    @Override
    public PackLocationInfo location() {
        return locationInfo;
    }

    @Override
    public void close() {
    }

    private byte[] resolveGeneratedResource(PackType requestedPackType, Identifier location) {
        Map<String, byte[]> resources = PyFishResourceGenerator.getGeneratedResources(requestedPackType);
        byte[] direct = resources.get(location.toString());
        if (direct != null) {
            return direct;
        }

        for (String alias : buildAliases(requestedPackType, location)) {
            byte[] aliased = resources.get(alias);
            if (aliased != null) {
                return aliased;
            }
        }

        return null;
    }

    private List<String> buildAliases(PackType requestedPackType, Identifier location) {
        List<String> aliases = new ArrayList<>();
        String namespace = location.getNamespace();
        String path = location.getPath();
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String leafPath = normalizedPath;

        if (requestedPackType == PackType.CLIENT_RESOURCES) {
            if (leafPath.startsWith("item/")) {
                leafPath = leafPath.substring("item/".length());
            } else if (leafPath.startsWith("block/")) {
                leafPath = leafPath.substring("block/".length());
            }

            if (!normalizedPath.contains(".")) {
                aliases.add(namespace + ":models/" + normalizedPath + ".json");
                aliases.add(namespace + ":models/item/" + leafPath + ".json");
                aliases.add(namespace + ":models/block/" + leafPath + ".json");
                aliases.add(namespace + ":blockstates/" + leafPath + ".json");
                aliases.add(namespace + ":textures/" + leafPath + ".png");
                aliases.add(namespace + ":textures/item/" + leafPath + ".png");
                aliases.add(namespace + ":textures/block/" + leafPath + ".png");
            }
        }

        return aliases;
    }
}
