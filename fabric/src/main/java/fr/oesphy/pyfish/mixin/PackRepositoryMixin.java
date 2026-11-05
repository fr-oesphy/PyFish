package fr.oesphy.pyfish.mixin;

import fr.oesphy.pyfish.fabric.content.PyFishFabricGeneratedPackSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.LinkedHashSet;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void pyfish$addGeneratedResourceSources(RepositorySource[] repositorySources, CallbackInfo ci) {
        // Register both sources before the repository's first reload. Adding them when a
        // script emits its first asset is already too late for the active pack scan.
        // Vanilla may retain an ImmutableSet here; do not mutate the constructor's set.
        sources = new LinkedHashSet<>(sources);
        sources.add(new PyFishFabricGeneratedPackSource(PackType.CLIENT_RESOURCES));
        sources.add(new PyFishFabricGeneratedPackSource(PackType.SERVER_DATA));
    }
}
