package fr.oesphy.pyfish;

import fr.oesphy.pyfish.script.ScriptType;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Canonical path constants and namespace-discovery utilities for PyFish's
 * on-disk script hierarchy.
 *
 * <h3>Directory layout</h3>
 * <pre>
 * &lt;instance&gt;/
 * └── pyfish/                          ← {@link #SCRIPT_ROOT_NAME}
 *     ├── config.json                  ← (Phase 6)
 *     ├── scripts/                     ← pyfish namespace (built-in)
 *     │   ├── startup/  → STARTUP
 *     │   ├── server/   → SERVER
 *     │   ├── client/   → CLIENT
 *     │   └── *.py      → SERVER (default for loose files)
 *     ├── &lt;namespace&gt;/                 ← third-party namespace
 *     │   ├── scripts/
 *     │   │   ├── startup/
 *     │   │   ├── server/
 *     │   │   ├── client/
 *     │   │   └── *.py  → SERVER
 *     │   └── requirements.txt
 *     └── …
 * </pre>
 *
 * <h3>Namespace rules</h3>
 * <ul>
 *   <li>Namespace names are lowercased and sanitised to {@code [a-z0-9_.-]}.</li>
 *   <li>The reserved name {@value #PYFISH_NAMESPACE} always refers to the
 *       built-in {@code pyfish/scripts/} directory.</li>
 *   <li>Any subdirectory of the script root that itself contains a
 *       {@code scripts/} subdirectory is treated as a third-party namespace.</li>
 * </ul>
 */
public final class PyFishPaths {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Root folder name under the game instance directory. */
    public static final String SCRIPT_ROOT_NAME = "pyfish";

    /** The built-in PyFish namespace. */
    public static final String PYFISH_NAMESPACE = "pyfish";

    /** Name of the scripts subdirectory inside each namespace folder. */
    public static final String SCRIPTS_DIR_NAME = "scripts";

    /** Subfolder name that maps to {@link ScriptType#STARTUP}. */
    public static final String STARTUP_SUBFOLDER = "startup";

    /** Subfolder name that maps to {@link ScriptType#SERVER}. */
    public static final String SERVER_SUBFOLDER = "server";

    /** Subfolder name that maps to {@link ScriptType#CLIENT}. */
    public static final String CLIENT_SUBFOLDER = "client";

    /** Config JSON file within the script root (created by Phase 6). */
    public static final String CONFIG_FILE_NAME = "config.json";

    private PyFishPaths() {}

    // -------------------------------------------------------------------------
    // Path helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the PyFish script root under {@code gameDir}.
     * This directory is created by {@link ScriptLoader} on first use if absent.
     *
     * @param gameDir the Minecraft instance/game directory (the one that contains
     *                {@code mods/}, {@code config/}, etc.)
     */
    public static Path scriptRoot(Path gameDir) {
        return gameDir.resolve(SCRIPT_ROOT_NAME);
    }

    /**
     * Returns the built-in {@code pyfish/scripts/} directory.
     * Scripts here are registered under the {@value #PYFISH_NAMESPACE} namespace.
     */
    public static Path pyfishScriptsDir(Path scriptRoot) {
        return scriptRoot.resolve(SCRIPTS_DIR_NAME);
    }

    /**
     * Returns the {@code scripts/} directory for a third-party namespace.
     *
     * @param scriptRoot the pyfish script root
     * @param namespace  the sanitised namespace / mod-id
     */
    public static Path namespaceScriptsDir(Path scriptRoot, String namespace) {
        return scriptRoot.resolve(namespace).resolve(SCRIPTS_DIR_NAME);
    }

    /**
     * Returns the {@code requirements.txt} file for a third-party namespace,
     * or an empty optional if not present.
     */
    public static Path requirementsFile(Path scriptRoot, String namespace) {
        return scriptRoot.resolve(namespace).resolve("requirements.txt");
    }

    /**
     * Returns the subfolder path for the given {@link ScriptType} within a
     * scripts directory, e.g. {@code scripts/startup}, {@code scripts/server}.
     */
    public static Path typedSubdir(Path scriptsDir, ScriptType type) {
        return scriptsDir.resolve(subfolderFor(type));
    }

    // -------------------------------------------------------------------------
    // Type routing
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ScriptType} for a given subfolder name, or
     * {@code null} when the name does not map to any known type (i.e. it is a
     * loose file or an unknown directory that should be skipped).
     *
     * <ul>
     *   <li>{@code "startup"} → {@link ScriptType#STARTUP}</li>
     *   <li>{@code "server"}  → {@link ScriptType#SERVER}</li>
     *   <li>{@code "client"}  → {@link ScriptType#CLIENT}</li>
     * </ul>
     *
     * @param subfolder directory name under the {@code scripts/} folder
     * @return the corresponding {@link ScriptType}, or {@code null}
     */
    public static ScriptType typeForSubfolder(String subfolder) {
        if (subfolder == null) return null;
        return switch (subfolder.toLowerCase(Locale.ROOT)) {
            case STARTUP_SUBFOLDER -> ScriptType.STARTUP;
            case SERVER_SUBFOLDER  -> ScriptType.SERVER;
            case CLIENT_SUBFOLDER  -> ScriptType.CLIENT;
            default                -> null;
        };
    }

    /**
     * Returns the canonical subfolder name for a given {@link ScriptType}.
     */
    public static String subfolderFor(ScriptType type) {
        return switch (type) {
            case STARTUP -> STARTUP_SUBFOLDER;
            case SERVER  -> SERVER_SUBFOLDER;
            case CLIENT  -> CLIENT_SUBFOLDER;
        };
    }

    // -------------------------------------------------------------------------
    // Namespace discovery
    // -------------------------------------------------------------------------

    /**
     * Scans {@code scriptRoot} and returns a list of all discoverable namespaces.
     *
     * <p>A directory entry is treated as a namespace if:
     * <ol>
     *   <li>It is a directory (not a file or symlink to a file).</li>
     *   <li>Its name is not {@code "scripts"} (the built-in pyfish scripts dir).</li>
     *   <li>It contains a subdirectory named {@code "scripts"}.</li>
     * </ol>
     *
     * <p>The built-in {@value #PYFISH_NAMESPACE} namespace is always returned
     * first (even if no {@code scripts/} directory exists yet for it), followed
     * by third-party namespaces in lexicographic order.
     *
     * @param scriptRoot the pyfish script root
     * @param logger     used for diagnostics; may be {@code null} to suppress
     * @return unmodifiable list of namespace descriptors; never {@code null}
     */
    public static List<NamespaceRoot> discoverNamespaces(Path scriptRoot, Logger logger) {
        Objects.requireNonNull(scriptRoot, "scriptRoot");

        List<NamespaceRoot> result = new ArrayList<>();

        // Always include the built-in pyfish namespace (scripts/ directly under root)
        Path builtinScripts = pyfishScriptsDir(scriptRoot);
        result.add(new NamespaceRoot(PYFISH_NAMESPACE, builtinScripts));

        if (!Files.isDirectory(scriptRoot)) {
            return Collections.unmodifiableList(result);
        }

        // Scan for third-party namespaces: subdirs that contain a scripts/ subdir
        try (var stream = Files.list(scriptRoot)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !p.getFileName().toString().equals(SCRIPTS_DIR_NAME))
                  .sorted()
                  .forEach(nsDir -> {
                      Path scriptsDir = nsDir.resolve(SCRIPTS_DIR_NAME);
                      if (Files.isDirectory(scriptsDir)) {
                          String raw = nsDir.getFileName().toString();
                          String sanitised = sanitiseNamespace(raw);
                          if (!sanitised.equals(PYFISH_NAMESPACE)) {
                              if (!sanitised.equals(raw) && logger != null) {
                                  logger.warn(
                                      "[PyFish/Paths] Namespace folder '{}' sanitised to '{}'",
                                      raw, sanitised);
                              }
                              result.add(new NamespaceRoot(sanitised, scriptsDir));
                          }
                      }
                  });
        } catch (IOException e) {
            if (logger != null) {
                logger.error("[PyFish/Paths] Failed to scan script root '{}': {}",
                        scriptRoot, e.getMessage());
            }
        }

        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sanitises a raw directory name into a valid Minecraft namespace/mod-id:
     * lowercased, non-{@code [a-z0-9_.-]} characters replaced with {@code _}.
     */
    public static String sanitiseNamespace(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    // -------------------------------------------------------------------------
    // Value type
    // -------------------------------------------------------------------------

    /**
     * Pairs a sanitised namespace string with the resolved {@code scripts/}
     * directory for that namespace.
     *
     * @param namespace  sanitised namespace / mod-id (e.g. {@code "mymod"})
     * @param scriptsDir the {@code scripts/} directory for this namespace
     */
    public record NamespaceRoot(String namespace, Path scriptsDir) {

        public NamespaceRoot {
            Objects.requireNonNull(namespace,  "namespace");
            Objects.requireNonNull(scriptsDir, "scriptsDir");
        }

        /** Returns {@code true} if the scripts directory actually exists on disk. */
        public boolean exists() {
            return Files.isDirectory(scriptsDir);
        }

        @Override
        public String toString() {
            return "NamespaceRoot{ns=" + namespace + ", dir=" + scriptsDir + '}';
        }
    }
}
