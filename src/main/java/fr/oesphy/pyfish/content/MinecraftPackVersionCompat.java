package fr.oesphy.pyfish.content;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

final class MinecraftPackVersionCompat {
    private MinecraftPackVersionCompat() {
    }

    static Object current(PackType packType) {
        return SharedConstants.getCurrentVersion().getPackVersion(packType);
    }

    static int currentFormatNumber(PackType packType) {
        return SharedConstants.getCurrentVersion().getPackVersion(packType);
    }
}
