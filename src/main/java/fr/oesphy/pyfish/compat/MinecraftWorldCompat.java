package fr.oesphy.pyfish.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

public final class MinecraftWorldCompat {
    private MinecraftWorldCompat() {
    }

    public static long getDayTime(ServerLevel level) {
        return level.getDayTime();
    }

    public static void setDayTime(ServerLevel level, long time) {
        level.setDayTime(time);
    }

    public static EntityType<LightningBolt> lightningType() {
        return EntityType.LIGHTNING_BOLT;
    }
}
