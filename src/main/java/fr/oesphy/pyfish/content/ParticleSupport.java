package fr.oesphy.pyfish.content;

import com.google.gson.Gson;
import com.mojang.serialization.JsonOps;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;

public final class ParticleSupport {
    private static final Gson GSON = new Gson();

    private ParticleSupport() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ParticleOptions create(ServerLevel level, String id, Map<String, Object> options) {
        ParticleType type = MinecraftCompat.registryGet(BuiltInRegistries.PARTICLE_TYPE, id);
        if (type == null) throw new IllegalArgumentException("Unknown particle type: " + id);
        if (type instanceof ParticleOptions simple) return simple;
        var json = GSON.toJsonTree(PyFishContentSupport.snapshotProperties(options));
        return (ParticleOptions) type.codec().codec().parse(
                level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }
}
