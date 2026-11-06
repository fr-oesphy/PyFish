package fr.oesphy.pyfish.event;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.script.ConsoleApi;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Manages a named list of Python (or Java) listeners for a single event within
 * an {@link EventGroup} (e.g. {@code player.chat}).
 *
 * <h3>Decorator protocol</h3>
 * <p>{@code EventHandler} implements {@link Function}{@code <Object,Object>} so
 * that GraalPy can call it as a Python decorator.  The {@link #apply(Object)}
 * method inspects its argument:
 * <ul>
 *   <li>If it is an executable {@link Value} (a Python callable), the callable
 *       is registered as a listener with no filter, and the callable itself is
 *       returned so the original function name stays usable in the script.</li>
 *   <li>If it is a {@link Value} string, a <em>filter-bound</em>
 *       {@code Function<Object,Object>} is returned.  Calling that returned
 *       function with a Python callable registers the callable with the given
 *       filter string (e.g. {@code "minecraft:lever"}).</li>
 *   <li>Otherwise the argument is returned unchanged.</li>
 * </ul>
 *
 * <h3>Firing</h3>
 * <p>{@link #fire(PyEvent, String)} iterates over all registered listeners.
 * For each listener whose filter is {@code null} or matches {@code subjectId},
 * the callable is invoked with the event.  Exceptions are caught per listener
 * and logged; they do not propagate.
 *
 * <h3>Thread safety</h3>
 * <p>The listener list is a {@link CopyOnWriteArrayList}; concurrent reads
 * during fire are safe.  Registration should happen before the first fire.
 *
 * @see EventGroup
 * @see PyEvent
 * @see EventResult
 */
public final class EventHandler implements Function<Object, Object> {

    private static final Logger LOGGER = PyFish.LOGGER;

    // -------------------------------------------------------------------------
    // Internal record — package-private for tests
    // -------------------------------------------------------------------------

    record ListenerEntry(Value callable, String filter, String scriptName) {}

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String groupName;
    private final String eventName;

    /**
     * CopyOnWriteArrayList so that {@link #fire} can iterate concurrently with
     * registrations happening from other scripts (unusual but safe).
     */
    private final CopyOnWriteArrayList<ListenerEntry> listeners = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * @param groupName the event group that owns this handler (e.g. {@code "player"})
     * @param eventName the name of this specific event (e.g. {@code "chat"})
     */
    public EventHandler(String groupName, String eventName) {
        this.groupName = groupName;
        this.eventName = eventName;
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a listener with no subject filter.
     *
     * @param callable a GraalPy {@link Value} representing a Python callable
     */
    public void register(Value callable, String filter) {
        String scriptName = resolveScriptName();
        listeners.add(new ListenerEntry(callable, filter, scriptName));
        LOGGER.debug("[PyFish/EventHandler] Registered listener for {}.{} (filter={}, script={})",
                groupName, eventName, filter, scriptName);
    }

    /**
     * Registers a listener with no subject filter.
     *
     * @param callable a GraalPy {@link Value} representing a Python callable
     */
    public void register(Value callable) {
        register(callable, null);
    }

    // -------------------------------------------------------------------------
    // Firing
    // -------------------------------------------------------------------------

    /**
     * Fires this event at all registered listeners whose filter matches
     * {@code subjectId} (or who have no filter).
     *
     * <p>Exceptions thrown by individual listeners are caught and logged; they
     * do not propagate to the caller.  The event's {@link PyEvent#result()} at
     * the end of iteration is returned.
     *
     * @param event     the event to pass to each listener
     * @param subjectId optional subject identifier for targeted listeners
     *                  (e.g. {@code "minecraft:lever"}); {@code null} means
     *                  only unfiltered listeners are called
     * @return the final {@link EventResult} of the event
     */
    public EventResult fire(PyEvent event, String subjectId) {
        for (ListenerEntry entry : listeners) {
            // Filter check: skip if listener has a filter that doesn't match
            if (entry.filter() != null && !entry.filter().equals(subjectId)) {
                continue;
            }

            try {
                Value returnValue = entry.callable().execute(event);

                // Coerce string return values into EventResult
                if (returnValue != null && returnValue.isString()) {
                    EventResult parsed = EventResult.fromString(returnValue.asString());
                    if (parsed != null) {
                        event.setResult(parsed);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[PyFish/EventHandler] Error in listener for {}.{} (script={}): {}",
                        groupName, eventName, entry.scriptName(), e.getMessage());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[PyFish/EventHandler] Traceback:", e);
                }
                // Do NOT rethrow — failures are isolated per listener
            }
        }
        return event.result();
    }

    /**
     * Fires with no subject filter (all unfiltered listeners are called).
     */
    public EventResult fire(PyEvent event) {
        return fire(event, null);
    }

    // -------------------------------------------------------------------------
    // Reload support
    // -------------------------------------------------------------------------

    /** Removes all registered listeners (called on server/client reload). */
    public void clearListeners() {
        int removed = listeners.size();
        listeners.clear();
        LOGGER.debug("[PyFish/EventHandler] Cleared {} listener(s) from {}.{}",
                removed, groupName, eventName);
    }

    // -------------------------------------------------------------------------
    // Function<Object,Object> — Python decorator protocol
    // -------------------------------------------------------------------------

    /**
     * Implements the Python decorator protocol.
     *
     * <ul>
     *   <li>Callable arg → register and return the callable.</li>
     *   <li>String arg   → return a filter-bound decorator factory.</li>
     *   <li>Other        → return arg unchanged.</li>
     * </ul>
     */
    @Override
    public Object apply(Object arg) {
        if (!(arg instanceof Value v)) {
            return arg;
        }

        if (v.canExecute()) {
            // Direct decorator: @events.player.chat
            register(v);
            return v; // return the original callable so the name still works
        }

        if (v.isString()) {
            // Filter factory: @events.block.right_clicked("minecraft:lever")
            String filter = v.asString();
            return (Function<Object, Object>) innerArg -> {
                if (innerArg instanceof Value innerV && innerV.canExecute()) {
                    register(innerV, filter);
                    return innerV;
                }
                return innerArg;
            };
        }

        return arg;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the name of the event group that owns this handler. */
    public String groupName() { return groupName; }

    /** Returns the name of this specific event. */
    public String eventName() { return eventName; }

    /** Returns the number of currently registered listeners. */
    public int listenerCount() { return listeners.size(); }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Best-effort: returns the current script name from {@link ConsoleApi}'s
     * thread-local.  Returns {@code "<unknown>"} if no script is running.
     */
    private static String resolveScriptName() {
        String name = ConsoleApi.currentScriptStatic();
        return (name == null || name.isEmpty()) ? "<unknown>" : name;
    }
}
