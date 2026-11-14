package fr.oesphy.pyfish.compat;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;

public final class MinecraftWorldCompat {
    private MinecraftWorldCompat() {
    }

    public static long getDayTime(ServerLevel level) {
        return level.clockManager().getTotalTicks(clock(level));
    }

    public static void setDayTime(ServerLevel level, long time) {
        level.clockManager().setTotalTicks(clock(level), time);
    }

    public static EntityType<LightningBolt> lightningType() {
        return EntityTypes.LIGHTNING_BOLT;
    }

    private static Holder<WorldClock> clock(ServerLevel level) {
        // Dimensions without a clock keep the previous shared Overworld-time behavior.
        return level.dimensionType().defaultClock().orElseGet(() ->
                level.registryAccess().get(WorldClocks.OVERWORLD).orElseThrow());
    }
}
