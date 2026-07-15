package fr.oesphy.pyfish.neoforge.content;

import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.PyFishPackMetadataFactory;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

public final class PyFishNeoForgeRuntimePackResources implements PackResources {
    private final PackLocationInfo locationInfo;
    private final PackType packType;

    public PyFishNeoForgeRuntimePackResources(PackLocationInfo locationInfo, PackType packType) {
        this.locationInfo = locationInfo;
        this.packType = packType;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... paths) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType requestedPackType, ResourceLocation location) {
        if (requestedPackType != this.packType) {
            return null;
        }
        byte[] data = PyFishResourceGenerator.getGeneratedResources(requestedPackType).get(location.toString());
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

            ResourceLocation resourceLocation = (ResourceLocation) MinecraftCompat.parseId(location);
            byte[] bytes = entry.getValue();
            resourceOutput.accept(resourceLocation, () -> new ByteArrayInputStream(bytes));
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
}
