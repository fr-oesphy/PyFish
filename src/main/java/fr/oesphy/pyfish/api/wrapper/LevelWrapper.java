package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Wraps a Minecraft {@link Level} (dimension/world) for use in Python scripts.
 */
public final class LevelWrapper {

    private final Level level;

    private LevelWrapper(Level level) {
        this.level = Objects.requireNonNull(level, "Level must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Wraps an existing {@link Level}.
     *
     * @param level the level; must not be {@code null}
     * @return a new {@link LevelWrapper}
     */
    public static LevelWrapper of(Level level) {
        Objects.requireNonNull(level, "level must not be null");
        return new LevelWrapper(level);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the registry key of this dimension, e.g. {@code "minecraft:overworld"}.
     *
     * @return dimension string
     */
    public String dimension() {
        return level.dimension().location().toString();
    }

    /**
     * Returns the absolute game time in ticks since world creation.
     *
     * @return game time in ticks
     */
    public long game_time() {
        return level.getGameTime();
    }

    /**
     * Returns the current time-of-day in ticks (0–23999).
     *
     * @return day time in ticks
     */
    public long day_time() {
        return level.getDayTime();
    }

    /**
     * Returns {@code true} if it is currently day (sun visible).
     *
     * @return day flag
     */
    public boolean is_day() {
        return level.isDay();
    }

    /**
     * Returns {@code true} if this level is running on the logical client side.
     *
     * @return client-side flag
     */
    public boolean is_client() {
        return level.isClientSide;
    }

    /**
     * Returns the registry id of the block at the given coordinates, e.g.
     * {@code "minecraft:stone"}.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @param z z-coordinate
     * @return block id string
     */
    public String get_block(int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null ? key.toString() : "minecraft:air";
    }

    /** @return the underlying {@link Level} */
    public Level unwrap() {
        return level;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "LevelWrapper[" + dimension() + "]";
    }
}
