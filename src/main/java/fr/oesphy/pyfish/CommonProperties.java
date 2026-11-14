package fr.oesphy.pyfish;

import fr.oesphy.pyfish.PyFish;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads and saves PyFish's main configuration file at {@code <gameDir>/pyfish/config.json}.
 *
 * <p>The file is a minimal hand-rolled JSON object (no external parser needed) so
 * that PyFish has zero additional runtime dependencies.  Supported keys:
 *
 * <table border="1">
 *   <caption>Configuration keys</caption>
 *   <tr><th>Key</th><th>Type</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>{@code allow_network}</td><td>boolean</td><td>{@code true}</td>
 *       <td>Allow scripts to make outbound HTTP/WebSocket connections via the
 *           {@code requests} and {@code websocket} shims.</td></tr>
 *   <tr><td>{@code allow_java_interop}</td><td>boolean</td><td>{@code false}</td>
 *       <td>Allow scripts to call {@code java.type(...)} to access arbitrary Java
 *           classes.  When {@code false}, only classes permitted by
 *           {@link fr.oesphy.pyfish.script.ClassFilter} are accessible.</td></tr>
 *   <tr><td>{@code script_timeout_ms}</td><td>int</td><td>{@code 5000}</td>
 *       <td>Maximum wall-clock milliseconds a single script evaluation may run
 *           before being interrupted (0 = unlimited).</td></tr>
 *   <tr><td>{@code auto_requirements}</td><td>boolean</td><td>{@code true}</td>
 *       <td>Automatically install {@code requirements.txt} found next to scripts
 *           on startup.</td></tr>
 *   <tr><td>{@code max_pip_packages}</td><td>int</td><td>{@code 50}</td>
 *       <td>Hard cap on the number of pip-installed packages PyFish will manage
 *           (prevents runaway disk usage).</td></tr>
 * </table>
 *
 * <p>The singleton instance is updated each time {@link #load(Path)} is called.
 * Hot-reload (on {@code /pyfish reload}) re-reads the file.
 *
 * @see fr.oesphy.pyfish.script.ClassFilter
 */
public final class CommonProperties {

    private static final Logger LOGGER = PyFish.LOGGER;
    private static final String CONFIG_FILE_NAME = "config.json";

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static final AtomicReference<CommonProperties> INSTANCE =
            new AtomicReference<>(new CommonProperties());

    /** Returns the currently loaded configuration. */
    public static CommonProperties get() {
        return INSTANCE.get();
    }

    /**
     * Loads (or reloads) the configuration from {@code <gameDir>/pyfish/config.json}.
     * Creates the file with defaults if it does not exist.
     *
     * @param gameDir the Minecraft instance directory
     * @return the newly loaded {@link CommonProperties}
     */
    public static CommonProperties load(Path gameDir) {
        Path configFile = gameDir.resolve("pyfish").resolve(CONFIG_FILE_NAME);
        CommonProperties props = readOrCreate(configFile);
        INSTANCE.set(props);
        LOGGER.info("[PyFish/Config] Loaded configuration from {}", configFile);
        return props;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final boolean allowNetwork;
    private final boolean allowJavaInterop;
    private final int     scriptTimeoutMs;
    private final boolean autoRequirements;
    private final int     maxPipPackages;

    // -------------------------------------------------------------------------
    // Construction — private; use load() / get()
    // -------------------------------------------------------------------------

    /** Default-value constructor. */
    private CommonProperties() {
        this(true, false, 5000, true, 50);
    }

    private CommonProperties(
            boolean allowNetwork,
            boolean allowJavaInterop,
            int     scriptTimeoutMs,
            boolean autoRequirements,
            int     maxPipPackages) {
        this.allowNetwork      = allowNetwork;
        this.allowJavaInterop  = allowJavaInterop;
        this.scriptTimeoutMs   = Math.max(0, scriptTimeoutMs);
        this.autoRequirements  = autoRequirements;
        this.maxPipPackages    = Math.max(0, maxPipPackages);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Whether scripts may make outbound network connections.
     * Applies to the {@code requests} and {@code websocket} shims.
     */
    public boolean allowNetwork()      { return allowNetwork; }

    /**
     * Whether {@code java.type(...)} is available to scripts for arbitrary
     * Java class access.  When {@code false}, only classes in
     * {@link fr.oesphy.pyfish.script.ClassFilter#DEFAULT} are reachable.
     */
    public boolean allowJavaInterop()  { return allowJavaInterop; }

    /**
     * Maximum milliseconds a single script evaluation may run (0 = unlimited).
     */
    public int     scriptTimeoutMs()   { return scriptTimeoutMs; }

    /**
     * Whether {@code requirements.txt} files are auto-installed on startup.
     */
    public boolean autoRequirements()  { return autoRequirements; }

    /**
     * Hard cap on the number of pip packages PyFish manages.
     */
    public int     maxPipPackages()    { return maxPipPackages; }

    // -------------------------------------------------------------------------
    // JSON I/O — hand-rolled to avoid external dependencies
    // -------------------------------------------------------------------------

    private static CommonProperties readOrCreate(Path configFile) {
        if (!Files.exists(configFile)) {
            CommonProperties defaults = new CommonProperties();
            writeDefaults(configFile, defaults);
            return defaults;
        }
        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            return parse(json);
        } catch (IOException e) {
            LOGGER.warn("[PyFish/Config] Could not read {}: {} — using defaults", configFile, e.getMessage());
            return new CommonProperties();
        }
    }

    /** Writes a pretty-printed JSON config file with the given values. */
    private static void writeDefaults(Path configFile, CommonProperties p) {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, toJson(p), StandardCharsets.UTF_8);
            LOGGER.info("[PyFish/Config] Created default config at {}", configFile);
        } catch (IOException e) {
            LOGGER.warn("[PyFish/Config] Could not write default config: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Minimal hand-rolled JSON parser (key : value pairs, no nesting)
    // -------------------------------------------------------------------------

    /**
     * Parses a flat JSON object string into a {@link CommonProperties}.
     * Tolerates missing keys (uses defaults) and ignores unknown keys.
     */
    static CommonProperties parse(String json) {
        Map<String, String> map = parseJsonObject(json);

        boolean allowNetwork     = getBool(map, "allow_network",     true);
        boolean allowJavaInterop = getBool(map, "allow_java_interop", false);
        int     scriptTimeoutMs  = getInt (map, "script_timeout_ms",  5000);
        boolean autoRequirements = getBool(map, "auto_requirements",  true);
        int     maxPipPackages   = getInt (map, "max_pip_packages",   50);

        return new CommonProperties(allowNetwork, allowJavaInterop,
                scriptTimeoutMs, autoRequirements, maxPipPackages);
    }

    /** Serialises a {@link CommonProperties} to a human-readable JSON string. */
    static String toJson(CommonProperties p) {
        return "{\n" +
               "  \"allow_network\": "      + p.allowNetwork      + ",\n" +
               "  \"allow_java_interop\": " + p.allowJavaInterop  + ",\n" +
               "  \"script_timeout_ms\": "  + p.scriptTimeoutMs   + ",\n" +
               "  \"auto_requirements\": "  + p.autoRequirements  + ",\n" +
               "  \"max_pip_packages\": "   + p.maxPipPackages    + "\n" +
               "}\n";
    }

    // -------------------------------------------------------------------------
    // JSON parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts key/value pairs from a flat JSON object.
     * Handles string values (with quotes) and bare values (numbers/booleans).
     */
    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) return map;
        // Strip outer braces
        String content = json.strip();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}"))  content = content.substring(0, content.length() - 1);

        // Split on commas that are not inside strings (simple heuristic — no nested objects)
        for (String pair : content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].strip().replace("\"", "");
            String val = kv[1].strip().replace("\"", "");
            if (!key.isEmpty()) {
                map.put(key, val);
            }
        }
        return map;
    }

    private static boolean getBool(Map<String, String> map, String key, boolean def) {
        String v = map.get(key);
        if (v == null) return def;
        return "true".equalsIgnoreCase(v.strip());
    }

    private static int getInt(Map<String, String> map, String key, int def) {
        String v = map.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.strip()); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public String toString() {
        return "CommonProperties{allowNetwork=" + allowNetwork +
               ", allowJavaInterop=" + allowJavaInterop +
               ", scriptTimeoutMs=" + scriptTimeoutMs +
               ", autoRequirements=" + autoRequirements +
               ", maxPipPackages=" + maxPipPackages + '}';
    }
}
