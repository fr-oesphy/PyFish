package fr.oesphy.pyfish;

import fr.oesphy.pyfish.platform.PyFishPlatform;
import fr.oesphy.pyfish.script.ConsoleApi;
import fr.oesphy.pyfish.script.ScriptLoader;
import fr.oesphy.pyfish.script.ScriptManager;
import fr.oesphy.pyfish.script.ScriptType;
import fr.oesphy.pyfish.script.PyRuntime;

import java.nio.file.Path;
import java.nio.file.Paths;
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
 *
 * <p>Phase 3 adds {@link #onServerStart(Path)}/{@link #onServerStop()} lifecycle
 * hooks and the CLIENT {@link ScriptManager} singleton.  Loader entrypoints call
 * these from the appropriate server-start / server-stop events.
 */
public final class PyFishCommon {

    private PyFishCommon() {
    }

    // -------------------------------------------------------------------------
    // Lazy ScriptManager singletons (Phase 2 / 3)
    // -------------------------------------------------------------------------

    private static volatile ScriptManager serverScriptManager;
    private static final Object SERVER_MANAGER_LOCK = new Object();

    private static volatile ScriptManager startupScriptManager;
    private static final Object STARTUP_MANAGER_LOCK = new Object();

    private static volatile ScriptManager clientScriptManager;
    private static final Object CLIENT_MANAGER_LOCK = new Object();

    // -------------------------------------------------------------------------
    // Public API — initialisation
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

    // -------------------------------------------------------------------------
    // Public API — lifecycle hooks (Phase 3)
    // -------------------------------------------------------------------------

    /**
     * Called by loader entrypoints when a server starts (or in single-player,
     * when the integrated server is ready).
     *
     * <p>Discovers all script namespaces under {@code <gameDir>/pyfish/},
     * populates the SERVER {@link ScriptManager}, and runs all SERVER scripts.
     * STARTUP scripts are managed separately and are not affected.
     *
     * <p>Idempotent if the server manager already exists: re-discovery and
     * re-execution are skipped so hot-reload remains under explicit control via
     * {@code /pyfish reload}.
     *
     * @param gameDir the Minecraft instance/game directory
     */
    public static void onServerStart(Path gameDir) {
        PyFish.LOGGER.info("[PyFish/Common] onServerStart({})", gameDir);
        ScriptManager mgr = getServerScriptManager();
        ScriptLoader.load(gameDir, null, mgr, null);
        mgr.runAll();
    }

    /**
     * Convenience overload: resolves {@code gameDir} from the JVM's current
     * working directory (suitable for integrated-server and single-player).
     */
    public static void onServerStart() {
        onServerStart(Paths.get(".").toAbsolutePath().normalize());
    }

    /**
     * Called when the server stops to give scripts an orderly shutdown.
     *
     * <p>Closes the SERVER {@link PyRuntime}, releasing GraalPy native
     * resources.  After this call the server script manager is unusable until
     * the next {@link #onServerStart(Path)}.
     */
    public static void onServerStop() {
        ScriptManager mgr = serverScriptManager;
        if (mgr == null) return;
        PyFish.LOGGER.info("[PyFish/Common] onServerStop — closing SERVER runtime.");
        try {
            mgr.getRuntime().close();
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/Common] Error closing SERVER runtime", e);
        }
        // Reset so that the next server start creates a fresh runtime.
        synchronized (SERVER_MANAGER_LOCK) {
            serverScriptManager = null;
        }
    }

    /**
     * Called by loader entrypoints when client resources are (re-)loaded.
     *
     * <p>Clears and re-populates the CLIENT {@link ScriptManager} from disk,
     * then runs all CLIENT scripts. Called on both initial resource load and
     * on F3+T reload.
     *
     * @param gameDir the Minecraft instance/game directory
     */
    public static void onClientResourceLoad(Path gameDir) {
        PyFish.LOGGER.info("[PyFish/Common] onClientResourceLoad({})", gameDir);
        ScriptManager mgr = getClientScriptManager();
        mgr.clearScripts(); // clear previous CLIENT scripts before reload
        ScriptLoader.load(gameDir, null, null, mgr);
        mgr.runAll();
    }

    // -------------------------------------------------------------------------
    // Public API — ScriptManager accessors (Phase 2 / 3)
    // -------------------------------------------------------------------------

    /**
     * Returns the shared SERVER {@link ScriptManager}, creating it on the first
     * call.  The manager is backed by a {@link PyRuntime} built with
     * {@link ScriptType#SERVER}.
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
     * Returns the shared STARTUP {@link ScriptManager}, creating it on the
     * first call.  STARTUP scripts run before registries freeze and are never
     * reloaded.
     */
    public static ScriptManager getStartupScriptManager() {
        ScriptManager mgr = startupScriptManager;
        if (mgr == null) {
            synchronized (STARTUP_MANAGER_LOCK) {
                mgr = startupScriptManager;
                if (mgr == null) {
                    PyRuntime runtime = new PyRuntime(ScriptType.STARTUP);
                    mgr = new ScriptManager(ScriptType.STARTUP, runtime);
                    startupScriptManager = mgr;
                    PyFish.LOGGER.info("[PyFish/Common] STARTUP ScriptManager created.");
                }
            }
        }
        return mgr;
    }

    /**
     * Returns the shared CLIENT {@link ScriptManager}, creating it on the
     * first call.  CLIENT scripts run on the logical client thread.
     */
    public static ScriptManager getClientScriptManager() {
        ScriptManager mgr = clientScriptManager;
        if (mgr == null) {
            synchronized (CLIENT_MANAGER_LOCK) {
                mgr = clientScriptManager;
                if (mgr == null) {
                    PyRuntime runtime = new PyRuntime(ScriptType.CLIENT);
                    mgr = new ScriptManager(ScriptType.CLIENT, runtime);
                    clientScriptManager = mgr;
                    PyFish.LOGGER.info("[PyFish/Common] CLIENT ScriptManager created.");
                }
            }
        }
        return mgr;
    }

    /** {@code true} if the SERVER manager has been created. */
    public static boolean isServerScriptManagerInitialized() {
        return serverScriptManager != null;
    }

    /** {@code true} if the CLIENT manager has been created. */
    public static boolean isClientScriptManagerInitialized() {
        return clientScriptManager != null;
    }

    /**
     * Returns the {@link ConsoleApi} from the SERVER manager, or empty if the
     * manager has not been created yet.
     */
    public static Optional<ConsoleApi> getServerConsole() {
        ScriptManager mgr = serverScriptManager;
        return mgr == null ? Optional.empty() : Optional.of(mgr.getConsole());
    }

    /**
     * Returns the {@link ConsoleApi} from the CLIENT manager, or empty if the
     * manager has not been created yet.
     */
    public static Optional<ConsoleApi> getClientConsole() {
        ScriptManager mgr = clientScriptManager;
        return mgr == null ? Optional.empty() : Optional.of(mgr.getConsole());
    }
}
