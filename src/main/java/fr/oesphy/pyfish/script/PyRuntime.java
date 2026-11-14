package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Owns a single GraalPy {@link Context} and enforces thread-safe access.
 *
 * <h3>Thread safety</h3>
 * <p>GraalPy contexts are <em>not</em> thread-safe. {@code PyRuntime} guards the
 * context with a {@link ReentrantLock} so that any thread can submit work via
 * {@link #runSafe(ThrowingRunnable)} without racing with another caller.
 *
 * <p>In practice, server scripts are always called from the server thread and client
 * scripts from the render thread, so contention is rare. The lock exists to handle
 * edge cases (async callbacks returning to the game thread while a reload is in
 * progress, etc.).
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Created by the platform entrypoint during mod initialisation.</li>
 *   <li>Lives for the duration of the game session (or until the server stops for
 *       the SERVER type).</li>
 *   <li>{@link #close()} must be called when the runtime is no longer needed so
 *       GraalPy can release its native resources.</li>
 * </ol>
 *
 * @see PyContextFactory
 * @see ScriptType
 */
public final class PyRuntime implements AutoCloseable {

    private static final Logger LOGGER = PyFish.LOGGER;

    private final ScriptType type;
    private final Context context;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a {@code PyRuntime} with a freshly built context.
     *
     * @param type the script lifecycle type this runtime serves
     */
    public PyRuntime(ScriptType type) {
        this.type = type;
        this.context = PyContextFactory.build(type);
        LOGGER.info("[PyFish/Runtime] PyRuntime ready (type={})", type);
    }

    /**
     * Creates a {@code PyRuntime} wrapping an already-built context.
     * Useful for testing or when the context needs special configuration.
     *
     * @param type    the script lifecycle type this runtime serves
     * @param context a pre-built GraalPy context
     */
    public PyRuntime(ScriptType type, Context context) {
        this.type = type;
        this.context = context;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs {@code work} inside the GraalPy context under the runtime lock.
     *
     * <p>Use this for all interactions with the context — script execution, binding
     * injection, eval calls, and Python-to-Java callbacks.
     *
     * @param work the action to run; may throw any checked exception
     * @throws RuntimeException wrapping any exception thrown by {@code work}
     */
    public void runSafe(ThrowingRunnable work) {
        lock.lock();
        try {
            work.run();
        } catch (Exception e) {
            LOGGER.error("[PyFish/Runtime] Error inside runSafe (type={}): {}", type, e.getMessage(), e);
            throw new RuntimeException("Error executing Python work (type=" + type + ")", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Runs {@code work} inside the GraalPy context under the runtime lock and
     * returns its result.
     *
     * @param work the action to run; may throw any checked exception
     * @param <T>  result type
     * @return the value returned by {@code work}
     * @throws RuntimeException wrapping any exception thrown by {@code work}
     */
    public <T> T runSafe(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } catch (Exception e) {
            LOGGER.error("[PyFish/Runtime] Error inside runSafe (type={}): {}", type, e.getMessage(), e);
            throw new RuntimeException("Error executing Python work (type=" + type + ")", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns {@code true} if the current thread holds the runtime lock.
     * Useful for assertions in code that must only run inside {@link #runSafe}.
     */
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * Returns the underlying GraalPy {@link Context}.
     *
     * <p><strong>Warning</strong>: callers must hold the runtime lock before
     * touching the context. Prefer {@link #runSafe} in production code; this
     * method exists for GraalPyRuntimeManager, which manages the full context
     * lifecycle and therefore needs direct access.
     */
    public Context getContext() {
        return context;
    }

    /**
     * Returns the {@link ScriptType} this runtime is configured for.
     */
    public ScriptType getType() {
        return type;
    }

    /**
     * Closes the underlying GraalPy context and releases native resources.
     * After this call the runtime must not be used.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            LOGGER.info("[PyFish/Runtime] Closing PyRuntime (type={})", type);
            context.close();
        } catch (Exception e) {
            LOGGER.error("[PyFish/Runtime] Error closing GraalPy context (type={}): {}", type, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Functional interface
    // -------------------------------------------------------------------------

    /**
     * Like {@link Runnable} but allowed to throw checked exceptions.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
