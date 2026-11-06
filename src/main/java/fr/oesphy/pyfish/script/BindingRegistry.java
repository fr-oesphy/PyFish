package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.event.EventFacade;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;

/**
 * Injects the standard Python bindings into a GraalPy {@link Context}.
 *
 * <p>All names injected here become globals in every script evaluated by the
 * context.  Scripts can access them directly:
 * <pre>{@code
 * # Available in every PyFish script without any import:
 * console.log("Hello from Python!")
 *
 * @events.player.chat
 * def on_chat(e):
 *     console.warn("Got chat: " + e.message)
 * }</pre>
 *
 * <p>The canonical Python module {@code pyfish} (loaded from
 * {@code src/main/resources/pyfish.py}) may re-export these bindings under
 * nicer names, but having them as globals keeps scripts short and readable.
 *
 * <h3>Bindings injected</h3>
 * <dl>
 *   <dt>{@code console}</dt>
 *   <dd>A {@link ConsoleApi} that routes {@code print}/{@code log}/{@code warn}/
 *       {@code error} to the mod logger and an in-memory ring buffer.</dd>
 *   <dt>{@code events}</dt>
 *   <dd>The {@link EventFacade#INSTANCE} singleton, giving access to all
 *       standard event groups ({@code events.player}, {@code events.block}, …).</dd>
 * </dl>
 *
 * <p>Future phases will add {@code mc}, {@code registry}, and other bindings
 * through additional calls to {@link #injectAll}.
 *
 * @see ConsoleApi
 * @see EventFacade
 */
public final class BindingRegistry {

    private static final Logger LOGGER = PyFish.LOGGER;

    // -------------------------------------------------------------------------
    // Injection entry point
    // -------------------------------------------------------------------------

    /**
     * Injects all standard bindings into the Python global namespace of the
     * given context.
     *
     * <p>This method must be called from the thread that owns {@code context}
     * (i.e. inside a {@link PyRuntime#runSafe} block) because GraalPy contexts
     * are not thread-safe.
     *
     * @param context the GraalPy context to inject into; must not be {@code null}
     * @param console the {@link ConsoleApi} instance for this manager; must not be {@code null}
     */
    public static void injectAll(Context context, ConsoleApi console) {
        Value bindings = context.getBindings("python");

        // --- console ---------------------------------------------------------
        try {
            bindings.putMember("console", console);
            LOGGER.debug("[PyFish/BindingRegistry] Injected binding: console");
        } catch (Exception e) {
            LOGGER.warn("[PyFish/BindingRegistry] Failed to inject 'console': {}", e.getMessage());
        }

        // --- events ----------------------------------------------------------
        try {
            bindings.putMember("events", EventFacade.INSTANCE);
            LOGGER.debug("[PyFish/BindingRegistry] Injected binding: events");
        } catch (Exception e) {
            LOGGER.warn("[PyFish/BindingRegistry] Failed to inject 'events': {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Non-instantiable
    // -------------------------------------------------------------------------

    private BindingRegistry() {}
}
