package fr.oesphy.pyfish;

import fr.oesphy.pyfish.platform.PyFishPlatform;
import fr.oesphy.pyfish.script.ConsoleApi;
import fr.oesphy.pyfish.script.ScriptManager;
import fr.oesphy.pyfish.script.ScriptType;
import fr.oesphy.pyfish.script.PyRuntime;

import java.util.Optional;

/**
 * Shared initialisation hook called by both loader entrypoints.
 *
 * <p>This class exists so that the common initialisation path has the name called
 * for in the architecture specification. Both
 * {@code PyFishFabric.onInitialize()} and {@code PyFishNeoForge}'s constructor
 * delegate here via {@link PyFish#initialize(PyFishPlatform)}.
 *
 * <p>Phase 2 adds {@link ScriptManager} accessors so loader command-registration
 * events can wire up {@code /pyfish} without coupling to internal classes.
 */
public final class PyFishCommon {

    private PyFishCommon() {
    }

    // -------------------------------------------------------------------------
    // Lazy ScriptManager singletons (Phase 2)
    // -------------------------------------------------------------------------

    /**
     * Server-side {@link ScriptManager}; created lazily on first access.
     *
     * <p>The underlying {@link PyRuntime} is built with {@link ScriptType#SERVER}.
     * Loader entrypoints that expose the {@code /pyfish reload} command retrieve
     * this manager via {@link #getServerScriptManager()}.
     *
     * <p>Implementation note: construction is intentionally deferred so that
     * {@link fr.oesphy.pyfish.script.PyContextFactory} is not called during
     * class loading (which happens before GraalPy is on the classpath in some
     * test environments).
     */
    private static volatile ScriptManager serverScriptManager;
    private static final Object SERVER_MANAGER_LOCK = new Object();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Entry point called once by both loaders during their startup phase.
     *
     * <p>Must be called <em>after</em> the platform's content backend has been
     * bootstrapped into {@link fr.oesphy.pyfish.content.PyFishContentManager} and
     * <em>before</em> registries are frozen.
     *
     * @param platform the loader-specific platform implementation
     */
    public static void init(PyFishPlatform platform) {
        PyFish.initialize(platform);
    }

    /**
     * Returns the shared SERVER {@link ScriptManager}, creating it on the first
     * call.
     *
     * <p>The manager is backed by a {@link PyRuntime} built with
     * {@link ScriptType#SERVER}. This is the manager that
     * {@code /pyfish reload} and {@code /pyfish errors} operate on.
     *
     * @return the singleton SERVER script manager
     */
    public static ScriptManager getServerScriptManager() {
        ScriptManager mgr = serverScriptManager;
        if (mgr == null) {
            synchronized (SERVER_MANAGER_LOCK) {
                mgr = serverScriptManager;
                if (mgr == null) {
                    PyRuntime runtime = new PyRuntime(ScriptType.SERVER);
                    mgr = new ScriptManager(ScriptType.SERVER, runtime);
                    serverScriptManager = mgr;
                    PyFish.LOGGER.info("[PyFish/Common] SERVER ScriptManager created.");
                }
            }
        }
        return mgr;
    }

    /**
     * Returns {@code true} if the SERVER {@link ScriptManager} has been created.
     * Useful for guard checks in shutdown hooks.
     */
    public static boolean isServerScriptManagerInitialized() {
        return serverScriptManager != null;
    }

    /**
     * Returns the {@link ConsoleApi} from the SERVER script manager if it has
     * been initialised, otherwise an empty optional.
     */
    public static Optional<ConsoleApi> getServerConsole() {
        ScriptManager mgr = serverScriptManager;
        return mgr == null ? Optional.empty() : Optional.of(mgr.getConsole());
    }
}
