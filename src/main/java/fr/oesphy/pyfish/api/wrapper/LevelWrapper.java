package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.server.level.ServerLevel;

/**
 * Wrapper for ServerLevel with Python-friendly methods.
 */
public class LevelWrapper extends BaseWrapper<ServerLevel> {
    public LevelWrapper(ServerLevel level) {
        super(level);
    }

    public long getTime() {
        return wrapped.getDayTime();
    }

    public void setTime(long time) {
        wrapped.setDayTime(time);
    }

    public Pos getSpawnPos() {
        return new Pos(wrapped.getSharedSpawnPos());
    }

    public String getDimension() {
        return wrapped.dimension().location().toString();
    }

    @Override
    public String toString() {
        return "Level[dimension=" + getDimension() + "]";
    }
}
