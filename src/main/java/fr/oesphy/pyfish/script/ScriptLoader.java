package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.PyFishPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads {@link ScriptPack}s from the standard on-disk directory layout and
 * registers them with the appropriate {@link ScriptManager}.
 *
 * <h3>Directory layout consumed</h3>
 * <pre>
 * &lt;instance&gt;/pyfish/
 * ├── scripts/              ← pyfish namespace
 * │   ├── startup/*.py      → STARTUP
 * │   ├── server/*.py       → SERVER
 * │   ├── client/*.py       → CLIENT
 * │   └── *.py              → SERVER (loose-file default)
 * └── &lt;namespace&gt;/
 *     └── scripts/
 *         ├── startup/*.py
 *         ├── server/*.py
 *         ├── client/*.py
 *         └── *.py          → SERVER
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ScriptLoader.load(gameDir, startupMgr, serverMgr, clientMgr);
 * }</pre>
 *
 * <p>Missing directories are silently skipped — the game directory is created
 * on first access.  {@link IOException}s reading individual files are logged
 * and the offending file is skipped; they never abort the whole load.
 *
 * @see PyFishPaths
 * @see ScriptManager
 * @see ScriptPack
 */
public final class ScriptLoader {

    private static final Logger LOGGER = PyFish.LOGGER;

    private ScriptLoader() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scans the standard {@code <gameDir>/pyfish/} hierarchy and populates the
     * three {@link ScriptManager}s with {@link ScriptPack}s.
     *
     * <p>The {@code pyfish/} root is created as an empty directory if it does
     * not yet exist so players know where to place their scripts.
     *
     * @param gameDir   the Minecraft instance/game directory
     * @param managers  map of {@link ScriptType} → {@link ScriptManager};
     *                  types absent from the map are silently skipped
     */
    public static void load(Path gameDir, Map<ScriptType, ScriptManager> managers) {
        Objects.requireNonNull(gameDir,  "gameDir");
        Objects.requireNonNull(managers, "managers");

        Path scriptRoot = PyFishPaths.scriptRoot(gameDir);
        ensureDirectory(scriptRoot);

        List<PyFishPaths.NamespaceRoot> namespaces =
                PyFishPaths.discoverNamespaces(scriptRoot, LOGGER);

        LOGGER.info("[PyFish/ScriptLoader] Discovered {} namespace(s) in {}",
                namespaces.size(), scriptRoot);

        for (PyFishPaths.NamespaceRoot ns : namespaces) {
            loadNamespace(ns, managers);
        }
    }

    /**
     * Convenience overload accepting three separate managers.
     *
     * @param gameDir    the Minecraft instance directory
     * @param startupMgr manager for {@link ScriptType#STARTUP} scripts
     * @param serverMgr  manager for {@link ScriptType#SERVER} scripts
     * @param clientMgr  manager for {@link ScriptType#CLIENT} scripts
     */
    public static void load(
            Path gameDir,
            ScriptManager startupMgr,
            ScriptManager serverMgr,
            ScriptManager clientMgr) {

        Map<ScriptType, ScriptManager> managers = new EnumMap<>(ScriptType.class);
        if (startupMgr != null) managers.put(ScriptType.STARTUP, startupMgr);
        if (serverMgr  != null) managers.put(ScriptType.SERVER,  serverMgr);
        if (clientMgr  != null) managers.put(ScriptType.CLIENT,  clientMgr);
        load(gameDir, managers);
    }

    /**
     * Reloads SERVER scripts only: clears the server manager, re-scans the
     * disk, and re-populates it.  STARTUP and CLIENT scripts are untouched.
     *
     * <p>Call this from {@code /pyfish reload} instead of calling
     * {@link ScriptManager#clearServerScripts()} + {@link #load} directly, so
     * that the reload respects the same directory conventions as the initial load.
     *
     * @param gameDir   the Minecraft instance directory
     * @param serverMgr the SERVER {@link ScriptManager} to reload
     */
    public static void reload(Path gameDir, ScriptManager serverMgr) {
        Objects.requireNonNull(gameDir,   "gameDir");
        Objects.requireNonNull(serverMgr, "serverMgr");

        serverMgr.clearServerScripts();

        Map<ScriptType, ScriptManager> mgrs = new EnumMap<>(ScriptType.class);
        mgrs.put(ScriptType.SERVER, serverMgr);
        load(gameDir, mgrs);

        serverMgr.runAll();
    }

    // -------------------------------------------------------------------------
    // Per-namespace loading
    // -------------------------------------------------------------------------

    /**
     * Loads all scripts for one namespace and registers packs with the matching
     * managers.
     */
    private static void loadNamespace(
            PyFishPaths.NamespaceRoot ns,
            Map<ScriptType, ScriptManager> managers) {

        Path scriptsDir = ns.scriptsDir();
        String namespace = ns.namespace();

        if (!Files.isDirectory(scriptsDir)) {
            LOGGER.debug("[PyFish/ScriptLoader] No scripts dir for namespace '{}' at {}",
                    namespace, scriptsDir);
            return;
        }

        LOGGER.info("[PyFish/ScriptLoader] Loading namespace '{}' from {}", namespace, scriptsDir);

        // 1. Typed subfolders: startup/, server/, client/
        for (ScriptType type : ScriptType.values()) {
            ScriptManager mgr = managers.get(type);
            if (mgr == null) continue;

            Path subdir = PyFishPaths.typedSubdir(scriptsDir, type);
            if (Files.isDirectory(subdir)) {
                ScriptPack pack = ScriptPack.discover(subdir, namespace, type);
                if (!pack.isEmpty()) {
                    mgr.addPack(pack);
                    LOGGER.info("[PyFish/ScriptLoader]   {} scripts/{}: {} file(s)",
                            namespace, PyFishPaths.subfolderFor(type), pack.files().size());
                }
            }
        }

        // 2. Loose .py files at the scripts/ root → SERVER (default)
        ScriptManager serverMgr = managers.get(ScriptType.SERVER);
        if (serverMgr != null) {
            List<ScriptFile> looseFiles = collectLooseFiles(scriptsDir, namespace);
            if (!looseFiles.isEmpty()) {
                LOGGER.info("[PyFish/ScriptLoader]   {} scripts/ (loose): {} file(s)",
                        namespace, looseFiles.size());
                for (ScriptFile f : looseFiles) {
                    serverMgr.addScript(f);
                }
            }
        }
    }

    /**
     * Collects {@code .py} files that sit directly in {@code scriptsDir}
     * (not in any subfolder), defaulting them to {@link ScriptType#SERVER}.
     *
     * <p>Files inside any named subfolder ({@code startup/}, {@code server/},
     * {@code client/}) are excluded because they are handled by the typed pass.
     */
    private static List<ScriptFile> collectLooseFiles(Path scriptsDir, String namespace) {
        try (var stream = Files.list(scriptsDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".py"))
                .sorted()
                .map(p -> {
                    String name = p.getFileName().toString().replaceAll("\\.py$", "");
                    try {
                        String source = Files.readString(p);
                        return new ScriptFile(namespace, name, source, ScriptType.SERVER);
                    } catch (IOException e) {
                        LOGGER.error("[PyFish/ScriptLoader] Cannot read loose script {}: {}",
                                p, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            LOGGER.error("[PyFish/ScriptLoader] Cannot list scripts dir {}: {}",
                    scriptsDir, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static void ensureDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
                LOGGER.info("[PyFish/ScriptLoader] Created script root: {}", dir);
            } catch (IOException e) {
                LOGGER.warn("[PyFish/ScriptLoader] Could not create script root {}: {}",
                        dir, e.getMessage());
            }
        }
    }
}
