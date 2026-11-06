package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/**
 * Wraps a Minecraft {@link Entity} for use in Python scripts.
 *
 * <p>Use {@link #of(Entity)} as the factory — it automatically returns a
 * {@link PlayerWrapper} when the entity is a {@link Player}.
 */
public class EntityWrapper {

    protected final Entity entity;

    protected EntityWrapper(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "Entity must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Wraps an {@link Entity}, automatically upgrading to a {@link PlayerWrapper} if
     * the entity is a {@link Player}.
     *
     * @param entity the entity; must not be {@code null}
     * @return an {@link EntityWrapper} or {@link PlayerWrapper}
     */
    public static EntityWrapper of(Entity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        if (entity instanceof Player player) {
            return PlayerWrapper.of(player);
        }
        return new EntityWrapper(entity);
    }

    // -------------------------------------------------------------------------
    // Accessors (snake_case for Python friendliness)
    // -------------------------------------------------------------------------

    /**
     * Returns the entity type registry key, e.g. {@code "minecraft:zombie"}.
     *
     * @return entity type id string
     */
    public String type_id() {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null ? key.toString() : "minecraft:unknown";
    }

    /** @return the entity's X position */
    public double x() { return entity.getX(); }

    /** @return the entity's Y position */
    public double y() { return entity.getY(); }

    /** @return the entity's Z position */
    public double z() { return entity.getZ(); }

    /**
     * Returns the block position of this entity.
     *
     * @return a {@link PosWrapper} at the entity's block coordinates
     */
    public PosWrapper block_pos() {
        return PosWrapper.of(entity.blockPosition());
    }

    /**
     * Returns the UUID of this entity as a string.
     *
     * @return UUID string
     */
    public String uuid() {
        return entity.getStringUUID();
    }

    /**
     * Returns {@code true} if the entity is alive.
     *
     * @return alive flag
     */
    public boolean is_alive() {
        return entity.isAlive();
    }

    /**
     * Returns the display name of this entity.
     *
     * @return name string
     */
    public String name() {
        return entity.getName().getString();
    }

    /** @return the underlying {@link Entity} */
    public Entity unwrap() {
        return entity;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return type_id() + " @ " + block_pos();
    }
}
