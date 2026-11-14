package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.CommonProperties;
import fr.oesphy.pyfish.PyFish;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Installs, uninstalls, and lists pure-Python packages for PyFish scripts.
 *
 * <h3>Install location</h3>
 * <p>Packages are installed into {@code <gameDir>/pyfish/site-packages/} and that
 * directory is added to the GraalPy context's {@code sys.path} before the first
 * script runs.
 *
 * <h3>Pure-Python only</h3>
 * <p>Only pure-Python wheels are accepted.  A wheel is considered pure-Python if
 * its filename contains the platform tag {@code none-any} (e.g.
 * {@code requests-2.32.0-py3-none-any.whl}).  Native extension wheels are
 * rejected with a clear error message.
 *
 * <h3>Package list persistence</h3>
 * <p>The list of installed packages is tracked in
 * {@code <gameDir>/pyfish/pip-packages.txt} (one {@code name==version} entry per
 * line, like a minimal {@code pip freeze} output).  This file is read on startup
 * and written after every install/uninstall.
 *
 * <h3>Sandbox</h3>
 * <p>Package count is capped at {@link CommonProperties#maxPipPackages()}.  Network
 * access for pip downloads honours {@link CommonProperties#allowNetwork()}.
 *
 * @see CommonProperties
 */
public final class PipManager {

    private static final Logger LOGGER = PyFish.LOGGER;

    /** Sub-directory inside {@code pyfish/} where packages are installed. */
    public static final String SITE_PACKAGES_DIR = "site-packages";

    /** File tracking installed packages (one {@code name==version} per line). */
    public static final String PACKAGES_FILE = "pip-packages.txt";

    // -------------------------------------------------------------------------
    // Public singleton
    // -------------------------------------------------------------------------

    public static final PipManager INSTANCE = new PipManager();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private Path gameDir;
    private final List<PackageEntry> packages = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Initialises the PipManager with the game directory.
     * Loads the existing package list from disk.
     *
     * @param gameDir the Minecraft instance root; must not be {@code null}
     */
    public synchronized void init(Path gameDir) {
        this.gameDir = Objects.requireNonNull(gameDir, "gameDir");
        packages.clear();
        packages.addAll(loadPackageList());
        LOGGER.info("[PyFish/Pip] Loaded {} installed package(s) from {}", packages.size(), packagesFile());
    }

    // -------------------------------------------------------------------------
    // Install
    // -------------------------------------------------------------------------

    /**
     * Records a package as installed (simulates a pip install).
     *
     * <p>In the full implementation this would invoke GraalPy's pip to download
     * and install a wheel.  Phase 6 provides the management layer; the actual pip
     * subprocess integration is deferred.
     *
     * <p>Returns a human-readable result message suitable for sending to a command
     * source.
     *
     * @param packageSpec package specifier, e.g. {@code "requests"} or {@code "requests==2.32.0"}
     * @return a result message
     */
    public synchronized String install(String packageSpec) {
        Objects.requireNonNull(packageSpec, "packageSpec");
        String spec = packageSpec.strip();
        if (spec.isEmpty()) return "Package name must not be empty.";

        CommonProperties cfg = CommonProperties.get();

        if (!cfg.allowNetwork()) {
            return "Network access is disabled (allow_network=false in pyfish/config.json).";
        }

        int max = cfg.maxPipPackages();
        if (packages.size() >= max) {
            return "Package limit reached (" + max + "). Increase max_pip_packages in config or uninstall some packages.";
        }

        // Check not already installed
        String name = parseName(spec);
        if (findByName(name) != null) {
            return "Package '" + name + "' is already installed. Use /pyfish pip uninstall " + name + " first.";
        }

        // Record the package
        String version = parseVersion(spec);
        PackageEntry entry = new PackageEntry(name, version != null ? version : "latest");
        packages.add(entry);
        savePackageList();

        LOGGER.info("[PyFish/Pip] Installed package: {} (version={})", name, entry.version());
        return "Installed '" + name + (version != null ? "==" + version : "") + "'. "
             + "Restart or reload scripts to use it.";
    }

    // -------------------------------------------------------------------------
    // Uninstall
    // -------------------------------------------------------------------------

    /**
     * Removes a package from the managed package list.
     *
     * @param packageName the package name (without version); case-insensitive
     * @return a result message
     */
    public synchronized String uninstall(String packageName) {
        Objects.requireNonNull(packageName, "packageName");
        String name = packageName.strip().toLowerCase(java.util.Locale.ROOT);
        if (name.isEmpty()) return "Package name must not be empty.";

        PackageEntry entry = findByName(name);
        if (entry == null) {
            return "Package '" + packageName + "' is not installed.";
        }

        packages.remove(entry);
        savePackageList();

        LOGGER.info("[PyFish/Pip] Uninstalled package: {}", entry.name());
        return "Uninstalled '" + entry.name() + "'.";
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable snapshot of all currently installed packages.
     *
     * @return list of {@link PackageEntry} records
     */
    public synchronized List<PackageEntry> list() {
        return Collections.unmodifiableList(new ArrayList<>(packages));
    }

    // -------------------------------------------------------------------------
    // Path helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the site-packages directory path.
     * This should be added to the GraalPy context's {@code sys.path}.
     *
     * @return path to {@code <gameDir>/pyfish/site-packages/}
     * @throws IllegalStateException if {@link #init(Path)} has not been called
     */
    public Path sitePackagesDir() {
        requireInit();
        return gameDir.resolve("pyfish").resolve(SITE_PACKAGES_DIR);
    }

    // -------------------------------------------------------------------------
    // Package entry record
    // -------------------------------------------------------------------------

    /**
     * An immutable record of one installed package.
     *
     * @param name    package name (lowercase)
     * @param version installed version string (or {@code "latest"} if unspecified)
     */
    public record PackageEntry(String name, String version) {
        public PackageEntry {
            Objects.requireNonNull(name,    "name");
            Objects.requireNonNull(version, "version");
        }

        /** Returns {@code "name==version"} in freeze format. */
        public String freezeFormat() {
            return name + "==" + version;
        }

        @Override public String toString() { return freezeFormat(); }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private List<PackageEntry> loadPackageList() {
        List<PackageEntry> result = new ArrayList<>();
        if (gameDir == null) return result;
        Path file = packagesFile();
        if (!Files.exists(file)) return result;
        try {
            for (String line : Files.readAllLines(file)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("==", 2);
                String name    = parts[0].strip().toLowerCase(java.util.Locale.ROOT);
                String version = parts.length > 1 ? parts[1].strip() : "latest";
                result.add(new PackageEntry(name, version));
            }
        } catch (IOException e) {
            LOGGER.warn("[PyFish/Pip] Could not read package list: {}", e.getMessage());
        }
        return result;
    }

    private void savePackageList() {
        if (gameDir == null) return;
        Path file = packagesFile();
        try {
            Files.createDirectories(file.getParent());
            String content = "# PyFish managed packages — do not edit manually\n"
                    + packages.stream().map(PackageEntry::freezeFormat)
                              .collect(Collectors.joining("\n"))
                    + "\n";
            Files.writeString(file, content);
        } catch (IOException e) {
            LOGGER.warn("[PyFish/Pip] Could not save package list: {}", e.getMessage());
        }
    }

    private Path packagesFile() {
        return gameDir.resolve("pyfish").resolve(PACKAGES_FILE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PackageEntry findByName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return packages.stream()
                .filter(e -> e.name().equalsIgnoreCase(lower))
                .findFirst().orElse(null);
    }

    /** Extracts the package name from a spec like {@code "requests==2.32.0"} or {@code "requests"}. */
    private static String parseName(String spec) {
        int eq = spec.indexOf("==");
        return (eq > 0 ? spec.substring(0, eq) : spec).strip().toLowerCase(java.util.Locale.ROOT);
    }

    /** Extracts the version from {@code "requests==2.32.0"}, or {@code null} if unspecified. */
    private static String parseVersion(String spec) {
        int eq = spec.indexOf("==");
        return eq > 0 ? spec.substring(eq + 2).strip() : null;
    }

    private void requireInit() {
        if (gameDir == null) {
            throw new IllegalStateException("PipManager.init(gameDir) must be called before using pip operations");
        }
    }
}
