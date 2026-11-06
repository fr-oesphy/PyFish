package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Console API exposed to Python scripts as the {@code console} binding.
 *
 * <p>All log methods include the script's qualified name (set via
 * {@link #pushScript}/{@link #popScript}) and delegate to the shared SLF4J
 * {@link PyFish#LOGGER}.  A fixed-size ring buffer (capacity
 * {@value #RING_BUFFER_CAPACITY}) of {@link Entry} records is maintained so
 * that {@code /pyfish errors} can replay recent console output.
 *
 * <h3>Python usage</h3>
 * <pre>
 * console.log("Hello from Python!")
 * console.warn("Something suspicious")
 * console.error("Something went wrong")
 * console.print("Raw output")  # alias of log
 * </pre>
 *
 * <h3>Script context tracking</h3>
 * <p>{@link ScriptManager} calls {@link #pushScript}/{@link #popScript} around
 * each script execution so the prefix in log messages identifies which script
 * produced the line.
 */
public final class ConsoleApi {

    private static final Logger LOGGER = PyFish.LOGGER;
    static final int RING_BUFFER_CAPACITY = 200;

    /** Log severity level. */
    public enum Level { INFO, WARN, ERROR }

    /**
     * Immutable record of one console message.
     *
     * @param scriptName qualified script name at the time of the message,
     *                   or {@code ""} if called outside any script context
     * @param level      severity
     * @param message    the formatted message text
     * @param timestamp  {@link System#currentTimeMillis()} when logged
     */
    public record Entry(String scriptName, Level level, String message, long timestamp) {}

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /**
     * Stack of currently-executing script names (innermost at top).
     * Access must be from the thread that owns the {@link PyRuntime}.
     */
    private final Deque<String> scriptStack = new ArrayDeque<>();

    /** Fixed-size ring buffer; guarded by {@code this}. */
    private final Deque<Entry> ringBuffer = new ArrayDeque<>(RING_BUFFER_CAPACITY);

    // -------------------------------------------------------------------------
    // Public Python-facing methods
    // -------------------------------------------------------------------------

    /**
     * Logs a message at INFO level.
     *
     * @param message the message; must not be {@code null}
     */
    public void log(Object message) {
        emit(Level.INFO, stringify(message));
    }

    /**
     * Alias for {@link #log(Object)} — matches Python's built-in {@code print}
     * signature enough for simple usage.
     */
    public void print(Object message) {
        emit(Level.INFO, stringify(message));
    }

    /**
     * Logs a message at WARN level.
     *
     * @param message the message; must not be {@code null}
     */
    public void warn(Object message) {
        emit(Level.WARN, stringify(message));
    }

    /**
     * Logs a message at ERROR level.
     *
     * @param message the message; must not be {@code null}
     */
    public void error(Object message) {
        emit(Level.ERROR, stringify(message));
    }

    // -------------------------------------------------------------------------
    // Script context tracking (called by ScriptManager)
    // -------------------------------------------------------------------------

    /**
     * Marks the start of {@code scriptName}'s execution so subsequent log
     * messages include the script name in their prefix.
     *
     * <p>Must be paired with {@link #popScript()}.
     *
     * @param scriptName the {@link ScriptFile#qualifiedName()} being executed
     */
    public void pushScript(String scriptName) {
        scriptStack.push(Objects.requireNonNullElse(scriptName, "<unknown>"));
    }

    /**
     * Marks the end of the innermost script's execution.
     * Safe to call even when the stack is empty.
     */
    public void popScript() {
        if (!scriptStack.isEmpty()) {
            scriptStack.pop();
        }
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable snapshot of the ring buffer in insertion order
     * (oldest first).
     *
     * @return list of up to {@value #RING_BUFFER_CAPACITY} recent entries
     */
    public synchronized List<Entry> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(ringBuffer));
    }

    /** Clears the ring buffer. */
    public synchronized void clearHistory() {
        ringBuffer.clear();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void emit(Level level, String message) {
        String prefix = buildPrefix(level);
        switch (level) {
            case INFO  -> LOGGER.info ("{}: {}", prefix, message);
            case WARN  -> LOGGER.warn ("{}: {}", prefix, message);
            case ERROR -> LOGGER.error("{}: {}", prefix, message);
        }
        addToRingBuffer(new Entry(currentScript(), level, message, System.currentTimeMillis()));
    }

    private String buildPrefix(Level level) {
        String script = currentScript();
        if (script.isEmpty()) {
            return "[PyFish/console]";
        }
        return "[PyFish/console/" + script + "]";
    }

    private String currentScript() {
        String top = scriptStack.peek();
        return top != null ? top : "";
    }

    private synchronized void addToRingBuffer(Entry entry) {
        if (ringBuffer.size() >= RING_BUFFER_CAPACITY) {
            ringBuffer.pollFirst(); // drop oldest
        }
        ringBuffer.addLast(entry);
    }

    private static String stringify(Object obj) {
        if (obj == null) return "null";
        // GraalPy Value objects have a useful toString already
        if (obj instanceof Value v) {
            try { return v.asString(); } catch (Exception ignored) {}
        }
        return obj.toString();
    }
}
