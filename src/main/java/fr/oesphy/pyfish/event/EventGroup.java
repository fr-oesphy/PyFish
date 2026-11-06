package fr.oesphy.pyfish.event;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A named group of related events (e.g. {@code player}, {@code block}).
 *
 * <p>{@code EventGroup} implements {@link ProxyObject} so that GraalPy can
 * access event handlers via Python attribute syntax:
 * <pre>{@code
 * @events.player.chat        # Python
 * // calls EventGroup.getMember("chat") → EventHandler, then EventHandler.apply(callable)
 * }</pre>
 *
 * <p>Any attribute name is accepted — handlers are created lazily on first
 * access.  This lets scripts subscribe to future events without requiring
 * Java-side registration.
 *
 * <h3>Naming</h3>
 * <p>Handler names are stored as-is (lowercase recommended by convention).
 * The group name (e.g. {@code "player"}) appears in log messages.
 *
 * @see EventHandler
 * @see EventGroups
 */
public final class EventGroup implements ProxyObject {

    private final String name;

    /**
     * LinkedHashMap preserves insertion order, which is useful for deterministic
     * iteration in {@link #getMemberKeys()} and during {@link #clearAllListeners()}.
     */
    private final Map<String, EventHandler> handlers = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * @param name the group name, used in log messages (e.g. {@code "player"})
     */
    public EventGroup(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    // -------------------------------------------------------------------------
    // ProxyObject — GraalPy attribute access
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link EventHandler} for the given event name, creating it
     * lazily if it does not yet exist.
     *
     * <p>GraalPy calls this when a Python script accesses
     * {@code events.&lt;group&gt;.&lt;eventName&gt;}.
     */
    @Override
    public Object getMember(String key) {
        return getHandler(key);
    }

    /**
     * Always returns {@code true}; any attribute name is a valid event name.
     * This avoids {@link org.graalvm.polyglot.PolyglotException} for new event
     * names that haven't been accessed before.
     */
    @Override
    public boolean hasMember(String key) {
        return true;
    }

    /**
     * Returns the names of all handlers that have been accessed so far.
     * Used by GraalPy for {@code dir(events.player)}.
     */
    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromArray((Object[]) handlers.keySet().toArray(String[]::new));
    }

    /**
     * No-op — event groups are read-only from Python.
     * Handlers are created automatically via {@link #getMember(String)}.
     */
    @Override
    public void putMember(String key, org.graalvm.polyglot.Value value) {
        // intentionally ignored; use register() / getHandler() from Java
    }

    // -------------------------------------------------------------------------
    // Handler management
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link EventHandler} for {@code eventName}, creating it
     * lazily if it does not yet exist.
     *
     * @param eventName the event name (e.g. {@code "chat"})
     */
    public EventHandler getHandler(String eventName) {
        Objects.requireNonNull(eventName, "eventName");
        return handlers.computeIfAbsent(eventName,
                name -> new EventHandler(this.name, name));
    }

    /**
     * Alias for {@link #getHandler(String)} for callers that prefer a verb.
     */
    public EventHandler on(String eventName) {
        return getHandler(eventName);
    }

    // -------------------------------------------------------------------------
    // Firing
    // -------------------------------------------------------------------------

    /**
     * Fires the named event at all matching listeners.
     *
     * <p>If no handler exists for {@code eventName}, this is a no-op and
     * returns {@link EventResult#PASS}.
     *
     * @param eventName the event to fire
     * @param event     the event object to pass to listeners
     * @param subjectId optional subject filter (may be {@code null})
     * @return the final {@link EventResult}
     */
    public EventResult fire(String eventName, PyEvent event, String subjectId) {
        EventHandler handler = handlers.get(eventName);
        if (handler == null) {
            return EventResult.PASS;
        }
        return handler.fire(event, subjectId);
    }

    /** Fires with no subject filter. */
    public EventResult fire(String eventName, PyEvent event) {
        return fire(eventName, event, null);
    }

    // -------------------------------------------------------------------------
    // Reload support
    // -------------------------------------------------------------------------

    /**
     * Clears all listeners from every handler in this group.
     * Called on server/client reload.
     */
    public void clearAllListeners() {
        handlers.values().forEach(EventHandler::clearListeners);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The name of this event group (e.g. {@code "player"}). */
    public String name() { return name; }

    /** Returns the number of event handlers that have been registered. */
    public int handlerCount() { return handlers.size(); }
}
