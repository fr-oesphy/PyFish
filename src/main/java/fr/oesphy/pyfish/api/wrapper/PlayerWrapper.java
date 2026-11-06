package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/**
 * Extends {@link EntityWrapper} to expose player-specific accessors for use in Python scripts.
 */
public final class PlayerWrapper extends EntityWrapper {

    private final Player player;

    private PlayerWrapper(Player player) {
        super(player);
        this.player = player;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Wraps a {@link Player}.
     *
     * @param player the player; must not be {@code null}
     * @return a new {@link PlayerWrapper}
     */
    public static PlayerWrapper of(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        return new PlayerWrapper(player);
    }

    // -------------------------------------------------------------------------
    // Player-specific accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the item held in the player's main hand.
     *
     * @return an {@link ItemStackWrapper} for the main hand item
     */
    public ItemStackWrapper main_hand() {
        return ItemStackWrapper.of(player.getMainHandItem());
    }

    /**
     * Returns the item held in the player's off hand.
     *
     * @return an {@link ItemStackWrapper} for the off-hand item
     */
    public ItemStackWrapper off_hand() {
        return ItemStackWrapper.of(player.getOffhandItem());
    }

    /**
     * Returns the player's food level (0–20).
     *
     * @return food level
     */
    public int food_level() {
        return player.getFoodData().getFoodLevel();
    }

    /**
     * Returns the player's current health.
     *
     * @return health value
     */
    public float health() {
        return player.getHealth();
    }

    /**
     * Returns the player's experience level.
     *
     * @return XP level
     */
    public int xp_level() {
        return player.experienceLevel;
    }

    /**
     * Returns the game mode name for server players; falls back to {@code "survival"} for
     * client-side players.
     *
     * @return game mode string (e.g. {@code "survival"}, {@code "creative"})
     */
    public String game_mode() {
        if (player instanceof ServerPlayer sp) {
            return sp.gameMode.getGameModeForPlayer().getName();
        }
        return "survival";
    }

    /**
     * Sends a system chat message to the player (server-side only).
     *
     * @param message the message text
     */
    public void tell(String message) {
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(message));
        }
    }

    /** @return the underlying {@link Player} */
    public Player unwrap_player() {
        return player;
    }
}
