package fr.oesphy.pyfish.content;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;

final class MinecraftPackVersionCompat {
    private MinecraftPackVersionCompat() {
    }

    static Object current(PackType packType) {
        return currentFormat(packType);
    }

    static int currentFormatNumber(PackType packType) {
        return currentFormat(packType).major();
    }

    private static PackFormat currentFormat(PackType packType) {
        return SharedConstants.getCurrentVersion().packVersion(packType);
    }
}
