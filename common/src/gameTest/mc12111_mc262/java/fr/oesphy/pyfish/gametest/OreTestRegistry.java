package fr.oesphy.pyfish.gametest;

import fr.oesphy.pyfish.compat.MinecraftCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

final class OreTestRegistry {
    static PlacedFeature feature(ServerLevel level, String path) {
        return MinecraftCompat.registryGet(level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE),
                "pyfish_ore_tests:" + path);
    }
}
