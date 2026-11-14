package fr.oesphy.pyfish.compat;

import net.minecraft.world.level.block.state.BlockBehaviour;

public final class MinecraftBlockCompat {
    private MinecraftBlockCompat() {
    }

    public static void noCollision(BlockBehaviour.Properties properties) {
        properties.noCollision();
    }
}
