package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.server.level.ServerPlayer;

/**
 * Wrapper for ServerPlayer with Python-friendly methods.
 */
public class PlayerWrapper extends BaseWrapper<ServerPlayer> {
    public PlayerWrapper(ServerPlayer player) {
        super(player);
    }

    public String getName() {
        return wrapped.getName().getString();
    }

    public String getUuid() {
        return wrapped.getUUID().toString();
    }

    public float getHealth() {
        return wrapped.getHealth();
    }

    public void setHealth(float health) {
        wrapped.setHealth(health);
    }

    public int getFoodLevel() {
        return wrapped.getFoodData().getFoodLevel();
    }

    public void setFoodLevel(int foodLevel) {
        wrapped.getFoodData().setFoodLevel(foodLevel);
    }

    public Pos getPos() {
        return new Pos(wrapped.blockPosition());
    }

    @Override
    public String toString() {
        return "Player[name=" + getName() + ", pos=" + getPos() + "]";
    }
}
