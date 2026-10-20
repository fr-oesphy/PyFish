package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Wrapper for Minecraft entities.
 */
public class EntityWrapper extends BaseWrapper<Entity> {
    public EntityWrapper(Entity entity) {
        super(entity);
    }

    public String getType() {
        return wrapped.getType().toString();
    }

    public Pos getPos() {
        return new Pos(wrapped.blockPosition());
    }

    public double getX() {
        return wrapped.getX();
    }

    public double getY() {
        return wrapped.getY();
    }

    public double getZ() {
        return wrapped.getZ();
    }

    public Level getLevel() {
        return wrapped.level();
    }

    public boolean isAlive() {
        return wrapped.isAlive();
    }

    public void kill() {
        wrapped.discard();
    }

    @Override
    public String toString() {
        return "Entity[type=" + getType() + ", pos=" + getPos() + "]";
    }
}
