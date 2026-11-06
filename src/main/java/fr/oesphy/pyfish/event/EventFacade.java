package fr.oesphy.pyfish.event;

/**
 * Python-facing top-level {@code events} object injected into every script's
 * global namespace.
 *
 * <p>GraalPy with {@code allowAllAccess(true)} + {@link org.graalvm.polyglot.HostAccess}
 * reads {@code public final} fields directly, so Python scripts access event
 * groups as simple attribute accesses:
 * <pre>{@code
 * from pyfish import events
 *
 * @events.player.chat
 * def on_chat(e):
 *     print(e.message)
 *
 * @events.block.right_clicked("minecraft:lever")
 * def on_lever(e):
 *     pass
 * }</pre>
 *
 * <p>Each field delegates to the corresponding {@link EventGroups} singleton,
 * so event registrations are process-global and survive across script reloads
 * (the listeners themselves are cleared on reload, but the group objects are
 * reused).
 *
 * @see EventGroups
 * @see EventGroup
 * @see EventHandler
 */
public final class EventFacade {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    /** The single shared instance injected into all script contexts. */
    public static final EventFacade INSTANCE = new EventFacade();

    // -------------------------------------------------------------------------
    // Public event group fields (Python-accessible via attribute access)
    // -------------------------------------------------------------------------

    /** Player-related events: join, leave, chat, respawn, advancement, death … */
    public final EventGroup player  = EventGroups.PLAYER;

    /** Block-related events: place, break, right_clicked, left_clicked … */
    public final EventGroup block   = EventGroups.BLOCK;

    /** Entity-related events: spawn, death, hurt, tick … */
    public final EventGroup entity  = EventGroups.ENTITY;

    /** Item-related events: use, right_clicked, crafted, tooltip … */
    public final EventGroup item    = EventGroups.ITEM;

    /** World/level events: tick, explosion, load, unload … */
    public final EventGroup level   = EventGroups.LEVEL;

    /** Datapack recipe events fired during server reload. */
    public final EventGroup recipe  = EventGroups.RECIPE;

    /** Datapack tag events fired during server reload. */
    public final EventGroup tag     = EventGroups.TAG;

    /** Server lifecycle events: start, stop, tick … */
    public final EventGroup server  = EventGroups.SERVER;

    /**
     * Startup-only registry events, fired before registries freeze.
     * Scripts using these events cannot be hot-reloaded.
     */
    public final EventGroup startup = EventGroups.STARTUP;

    // -------------------------------------------------------------------------
    // Construction — private; use INSTANCE
    // -------------------------------------------------------------------------

    private EventFacade() {}
}
