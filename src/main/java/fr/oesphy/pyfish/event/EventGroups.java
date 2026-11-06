package fr.oesphy.pyfish.event;

import java.util.Map;

/**
 * Central static registry of all standard {@link EventGroup} singletons.
 *
 * <p>This class exists so that loader event bridges can fire events into the
 * Python layer without holding a reference to the full scripting stack.
 * Example usage from a Fabric event bridge:
 * <pre>{@code
 * ServerTickEvents.START_SERVER_TICK.register(server -> {
 *     PyEvent event = new PyEvent();
 *     EventGroups.SERVER.fire("tick", event);
 * });
 * }</pre>
 *
 * <h3>Standard groups</h3>
 * <ul>
 *   <li>{@link #PLAYER}  — join, leave, chat, respawn, advancement …</li>
 *   <li>{@link #BLOCK}   — place, break, right_clicked …</li>
 *   <li>{@link #ENTITY}  — spawn, death, hurt …</li>
 *   <li>{@link #ITEM}    — use, right_clicked, tooltip …</li>
 *   <li>{@link #LEVEL}   — tick, explosion, load, unload …</li>
 *   <li>{@link #RECIPE}  — the datapack recipe event (server reload)</li>
 *   <li>{@link #TAG}     — the datapack tag event (server reload)</li>
 *   <li>{@link #SERVER}  — server lifecycle (start, stop, tick …)</li>
 *   <li>{@link #STARTUP} — startup-only registry events (before freeze)</li>
 * </ul>
 *
 * <p>All groups are eagerly created on class load; they are lightweight objects.
 *
 * @see EventGroup
 * @see EventFacade
 */
public final class EventGroups {

    // -------------------------------------------------------------------------
    // Standard groups
    // -------------------------------------------------------------------------

    public static final EventGroup PLAYER  = new EventGroup("player");
    public static final EventGroup BLOCK   = new EventGroup("block");
    public static final EventGroup ENTITY  = new EventGroup("entity");
    public static final EventGroup ITEM    = new EventGroup("item");
    public static final EventGroup LEVEL   = new EventGroup("level");
    public static final EventGroup RECIPE  = new EventGroup("recipe");
    public static final EventGroup TAG     = new EventGroup("tag");
    public static final EventGroup SERVER  = new EventGroup("server");
    public static final EventGroup STARTUP = new EventGroup("startup");

    // -------------------------------------------------------------------------
    // Name → group map
    // -------------------------------------------------------------------------

    /**
     * Immutable map from canonical group name to singleton instance.
     * Used for name-based lookup (e.g. from commands or config).
     */
    public static final Map<String, EventGroup> ALL = Map.of(
            "player",  PLAYER,
            "block",   BLOCK,
            "entity",  ENTITY,
            "item",    ITEM,
            "level",   LEVEL,
            "recipe",  RECIPE,
            "tag",     TAG,
            "server",  SERVER,
            "startup", STARTUP
    );

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link EventGroup} with the given name, or {@code null} if
     * no standard group has that name.
     *
     * @param name the group name (case-sensitive, e.g. {@code "player"})
     */
    public static EventGroup get(String name) {
        return ALL.get(name);
    }

    // -------------------------------------------------------------------------
    // Reload helpers
    // -------------------------------------------------------------------------

    /**
     * Clears all listeners from every standard event group.
     * Call this during server/client reload before re-running scripts.
     */
    public static void clearAll() {
        ALL.values().forEach(EventGroup::clearAllListeners);
    }

    /**
     * Clears listeners from all event groups except {@link #STARTUP}.
     *
     * <p>STARTUP events run once before registries freeze and are not reloaded.
     * Call this during a hot-reload of SERVER / CLIENT scripts.
     */
    public static void clearServerEvents() {
        ALL.forEach((name, group) -> {
            if (!"startup".equals(name)) {
                group.clearAllListeners();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Non-instantiable
    // -------------------------------------------------------------------------

    private EventGroups() {}
}
