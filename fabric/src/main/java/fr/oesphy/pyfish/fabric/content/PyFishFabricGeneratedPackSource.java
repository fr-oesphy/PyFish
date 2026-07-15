package fr.oesphy.pyfish.fabric.content;

import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.util.Optional;
import java.util.function.Consumer;

public final class PyFishFabricGeneratedPackSource implements RepositorySource {
    private final PackType packType;

    public PyFishFabricGeneratedPackSource(PackType packType) {
        this.packType = packType;
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        if (!PyFishResourceGenerator.hasResources(packType)) {
            return;
        }

        PackLocationInfo location = new PackLocationInfo(
                "pyfish_generated_" + packType.name().toLowerCase(),
                Component.literal("PyFish Generated Resources (" + packType.name() + ")"),
                PackSource.BUILT_IN,
                Optional.empty());

        PyFishFabricRuntimePackResources resources = new PyFishFabricRuntimePackResources(location, packType);
        Pack pack = Pack.readMetaAndCreate(
                location,
                new Pack.ResourcesSupplier() {
                    @Override
                    public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) {
                        return resources;
                    }

                    @Override
                    public net.minecraft.server.packs.PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                        return resources;
                    }
                },
                packType,
                new net.minecraft.server.packs.PackSelectionConfig(true, Pack.Position.TOP, true));

        if (pack != null) {
            consumer.accept(pack);
        }
    }
}