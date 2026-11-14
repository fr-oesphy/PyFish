package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages discovery, ordering, execution, reload, and error collection for one
 * {@link ScriptType}.
 *
 * <p>Each loader creates one {@code ScriptManager} per script type it wants to
 * support (typically SERVER and CLIENT; STARTUP scripts are handled during
 * early init before registries freeze). The manager owns the {@link PyRuntime}
 * it was constructed with.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Register packs/scripts via {@link #addPack(ScriptPack)} or
 *       {@link #addScript(ScriptFile)}.</li>
 *   <li>Call {@link #runAll()} to execute everything in registration order.</li>
 *   <li>On reload (SERVER only): call {@link #clearServerScripts()}, re-add
 *       packs, then call {@link #runAll()} again.</li>
 * </ol>
 *
 * <h3>Error handling</h3>
 * <p>Failures are caught per script. Each {@link ScriptFile} records its last
 * {@link ScriptError}. The full list of current errors is available via
 * {@link #collectErrors()}.  Scripts are never allowed to crash the game —
 * errors are logged and stored, then execution continues with the next script.
 *
 * <h3>Thread safety</h3>
 * <p>All GraalPy interactions (eval, binding injection) go through
 * {@link PyRuntime#runSafe}, which holds the runtime lock. Script registration
 * ({@link #addPack}, {@link #addScript}) is NOT thread-safe and must be called
 * from the owning thread before {@link #runAll()} is first invoked.
 *
 * @see ScriptFile
 * @see ScriptPack
 * @see PyRuntime
 */
public final class ScriptManager {

    private static final Logger LOGGER = PyFish.LOGGER;

    private final ScriptType   type;
    private final PyRuntime    runtime;
    private final ConsoleApi   console;

    /**
     * Registration-ordered map: qualifiedName → ScriptFile.
     * LinkedHashMap preserves insertion order for deterministic execution.
     */
    private final Map<String, ScriptFile> scripts = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code ScriptManager} that owns the given {@link PyRuntime}.
     *
     * @param type    the script lifecycle phase this manager handles
     * @param runtime the runtime to use for all GraalPy interactions; must not be {@code null}
     */
    public ScriptManager(ScriptType type, PyRuntime runtime) {
        this.type    = Objects.requireNonNull(type,    "type");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.console = new ConsoleApi();
    }

    // -------------------------------------------------------------------------
    // Script registration
    // -------------------------------------------------------------------------

    /**
     * Registers all scripts in the given {@link ScriptPack}.
     *
     * <p>If the pack's type does not match this manager's type, the pack is
     * silently ignored (a debug message is logged).
     *
     * @param pack the pack to register; must not be {@code null}
     */
    public void addPack(ScriptPack pack) {
        Objects.requireNonNull(pack, "pack");
        if (pack.type() != type) {
            LOGGER.debug("[PyFish/ScriptManager] Skipping pack {} (type mismatch: {} vs {})",
                    pack.namespace(), pack.type(), type);
            return;
        }
        for (ScriptFile file : pack.files()) {
            addScript(file);
        }
    }

    /**
     * Registers a single {@link ScriptFile}.
     *
     * <p>Duplicate qualified names are silently replaced (later registration
     * wins). The type of {@code file} is NOT checked against this manager's
     * type — that responsibility belongs to the caller.
     *
     * @param file the script file; must not be {@code null}
     */
    public void addScript(ScriptFile file) {
        Objects.requireNonNull(file, "file");
        scripts.put(file.qualifiedName(), file);
        LOGGER.debug("[PyFish/ScriptManager] Registered script: {} (type={})",
                file.qualifiedName(), file.type());
    }

    // -------------------------------------------------------------------------
    // Reload support
    // -------------------------------------------------------------------------

    /**
     * Clears all registered scripts, resetting the manager to an empty state
     * ready for re-registration.
     *
     * <p>STARTUP scripts are immutable: this method does nothing if
     * {@link #getType()} is {@link ScriptType#STARTUP}, since STARTUP scripts
     * run once before registries freeze and cannot be reloaded.
     *
     * <p>SERVER and CLIENT managers both clear their scripts. The name
     * {@code clearServerScripts} is kept for compatibility; use
     * {@link #clearScripts()} as a clearer alias for CLIENT managers.
     */
    public void clearServerScripts() {
        if (type == ScriptType.STARTUP) {
            LOGGER.debug("[PyFish/ScriptManager] clearServerScripts() called on STARTUP manager — ignored.");
            return;
        }
        scripts.clear();
        LOGGER.info("[PyFish/ScriptManager] Cleared {} scripts (type={})", type, type);
    }

    /**
     * Alias for {@link #clearServerScripts()} with a name that makes sense for
     * CLIENT managers.  STARTUP managers are still immune.
     */
    public void clearScripts() {
        clearServerScripts();
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    /**
     * Executes all registered scripts in registration order.
     *
     * <p>Each script's error state is cleared before execution.  Failures are
     * caught, recorded in the {@link ScriptFile}, and logged — execution
     * continues with the next script regardless.
     *
     * <p>The {@link ConsoleApi} {@code console} binding is injected into the
     * Python globals once before the first script runs.
     */
    public void runAll() {
        if (scripts.isEmpty()) {
            LOGGER.debug("[PyFish/ScriptManager] No scripts to run (type={}).", type);
            return;
        }

        LOGGER.info("[PyFish/ScriptManager] Running {} script(s) (type={})...",
                scripts.size(), type);

        // Inject console binding once; it is stateful and must survive across scripts.
        injectConsoleBinding();

        int ok  = 0;
        int err = 0;
        for (ScriptFile file : scripts.values()) {
            if (runFile(file)) ok++; else err++;
        }

        LOGGER.info("[PyFish/ScriptManager] Done (type={}): {} ok, {} error(s).", type, ok, err);
    }

    /**
     * Executes a single {@link ScriptFile} inside the runtime lock.
     *
     * <p>The file's previous error state is cleared before execution.  On
     * success, {@link ScriptFile#clearError()} is left in effect.  On failure,
     * a {@link ScriptError} is recorded via {@link ScriptFile#recordError}.
     *
     * @param file the script to run; must not be {@code null}
     * @return {@code true} if the script completed without error
     */
    public boolean runFile(ScriptFile file) {
        Objects.requireNonNull(file, "file");
        file.clearError();

        try {
            runtime.runSafe(() -> {
                console.pushScript(file.qualifiedName());
                try {
                    Context ctx = runtime.getContext();
                    ctx.eval("python", file.source());
                } finally {
                    console.popScript();
                }
            });
            LOGGER.debug("[PyFish/ScriptManager] Script executed: {}", file.qualifiedName());
            return true;
        } catch (Exception e) {
            ScriptError error = ScriptError.fromException(file.qualifiedName(), e);
            file.recordError(error);
            LOGGER.error("[PyFish/ScriptManager] Script error: {}", error.summary());
            if (!error.pythonTraceback().isBlank()) {
                LOGGER.error("[PyFish/ScriptManager] Python traceback:\n{}", error.pythonTraceback());
            }
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Error collection
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable snapshot of all current errors across all
     * registered scripts.
     *
     * @return list of errors; empty if all scripts ran successfully
     */
    public List<ScriptError> collectErrors() {
        List<ScriptError> errors = new ArrayList<>();
        for (ScriptFile file : scripts.values()) {
            if (file.hasError()) {
                errors.add(file.lastError());
            }
        }
        return Collections.unmodifiableList(errors);
    }

    /**
     * Clears all recorded errors from every registered script.
     * Does not re-execute any scripts.
     */
    public void clearErrors() {
        scripts.values().forEach(ScriptFile::clearError);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The lifecycle phase this manager handles. */
    public ScriptType getType()     { return type; }

    /** The underlying {@link PyRuntime}. */
    public PyRuntime  getRuntime()  { return runtime; }

    /** The {@link ConsoleApi} bound into the Python global namespace. */
    public ConsoleApi getConsole()  { return console; }

    /**
     * Returns an unmodifiable view of the currently registered scripts,
     * in registration order.
     */
    public Map<String, ScriptFile> getScripts() {
        return Collections.unmodifiableMap(scripts);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void injectConsoleBinding() {
        try {
            runtime.runSafe(() -> {
                BindingRegistry.injectAll(runtime.getContext(), console);
            });
        } catch (Exception e) {
            LOGGER.warn("[PyFish/ScriptManager] Failed to inject bindings: {}", e.getMessage());
        }
    }
}
