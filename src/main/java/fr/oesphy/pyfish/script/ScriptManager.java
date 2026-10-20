package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.runtime.GraalPyRuntimeManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages script discovery, loading, and reloading.
 * Handles script types, namespaces, and error collection.
 */
public class ScriptManager {
    private static final Logger LOGGER = PyFish.LOGGER;

    private final Map<String, ScriptPack> packs;
    private final ConsoleApi consoleApi;
    private final Path instanceRoot;
    private GraalPyRuntimeManager runtimeManager;

    public ScriptManager(Path instanceRoot) {
        this.packs = new HashMap<>();
        this.consoleApi = new ConsoleApi();
        this.instanceRoot = instanceRoot;
    }

    /**
     * Sets the runtime manager for script execution.
     * This is called after the ScriptManager is created but before scripts are executed.
     */
    public void setRuntimeManager(GraalPyRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    /**
     * Discovers scripts from the pyfish directory structure.
     * Layout:
     * - pyfish/scripts/*.py -> global pack (SERVER type)
     * - pyfish/<namespace>/scripts/*.py -> namespace pack (SERVER type)
     * - pyfish/<namespace>/startup/*.py -> STARTUP type
     * - pyfish/<namespace>/server/*.py -> SERVER type
     * - pyfish/<namespace>/client/*.py -> CLIENT type
     */
    public void discoverScripts() {
        LOGGER.info("[PyFish] Discovering scripts from instance root: {}", instanceRoot);

        Path pyfishRoot = instanceRoot.resolve("pyfish");
        if (!Files.exists(pyfishRoot)) {
            LOGGER.info("[PyFish] pyfish directory does not exist, skipping discovery");
            return;
        }

        // Discover global scripts (pyfish/scripts/)
        Path globalScriptsDir = pyfishRoot.resolve("scripts");
        if (Files.exists(globalScriptsDir)) {
            discoverPack(null, globalScriptsDir, ScriptType.SERVER);
        }

        // Discover namespace packs (pyfish/<namespace>/)
        try {
            var namespaceDirs = Files.list(pyfishRoot)
                    .filter(Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().equals("scripts"))
                    .filter(dir -> !dir.getFileName().toString().startsWith("."))
                    .toList();

            for (Path namespaceDir : namespaceDirs) {
                String namespace = namespaceDir.getFileName().toString();
                discoverNamespacePack(namespace, namespaceDir);
            }
        } catch (IOException e) {
            LOGGER.error("[PyFish] Failed to list namespace directories", e);
        }

        LOGGER.info("[PyFish] Script discovery complete. Packs: {}", packs.size());
    }

    /**
     * Discovers scripts for a namespace pack.
     */
    private void discoverNamespacePack(String namespace, Path namespaceDir) {
        LOGGER.info("[PyFish] Discovering namespace pack: {}", namespace);

        ScriptPack pack = new ScriptPack(namespace, namespaceDir);

        // Discover startup scripts
        Path startupDir = namespaceDir.resolve("startup");
        if (Files.exists(startupDir)) {
            discoverScriptsInDir(pack, startupDir, ScriptType.STARTUP);
        }

        // Discover server scripts
        Path serverDir = namespaceDir.resolve("server");
        if (Files.exists(serverDir)) {
            discoverScriptsInDir(pack, serverDir, ScriptType.SERVER);
        }

        // Discover client scripts
        Path clientDir = namespaceDir.resolve("client");
        if (Files.exists(clientDir)) {
            discoverScriptsInDir(pack, clientDir, ScriptType.CLIENT);
        }

        // Fall back to scripts/ directory (SERVER type)
        Path scriptsDir = namespaceDir.resolve("scripts");
        if (Files.exists(scriptsDir)) {
            discoverScriptsInDir(pack, scriptsDir, ScriptType.SERVER);
        }

        if (pack.getScriptCount() > 0) {
            packs.put(namespace, pack);
            LOGGER.info("[PyFish] Added pack: {} with {} scripts", namespace, pack.getScriptCount());
        }
    }

    /**
     * Discovers scripts in a specific directory and adds them to a pack.
     */
    private void discoverScriptsInDir(ScriptPack pack, Path dir, ScriptType type) {
        try {
            var scriptFiles = Files.list(dir)
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".py"))
                    .filter(file -> !file.getFileName().toString().startsWith("__"))
                    .toList();

            for (Path scriptPath : scriptFiles) {
                ScriptFile script = new ScriptFile(scriptPath, type, pack.getNamespace());
                loadScriptSource(script);
                parseHeaderDirectives(script);
                pack.addScript(script);
            }

            LOGGER.info("[PyFish] Discovered {} {} scripts in {}", scriptFiles.size(), type, dir);
        } catch (IOException e) {
            LOGGER.error("[PyFish] Failed to discover scripts in {}", dir, e);
        }
    }

    /**
     * Discovers scripts for the global pack (no namespace).
     */
    private void discoverPack(String namespace, Path scriptsDir, ScriptType type) {
        LOGGER.info("[PyFish] Discovering global scripts from: {}", scriptsDir);

        ScriptPack pack = new ScriptPack(namespace, scriptsDir);
        discoverScriptsInDir(pack, scriptsDir, type);

        if (pack.getScriptCount() > 0) {
            packs.put("global", pack);
            LOGGER.info("[PyFish] Added global pack with {} scripts", pack.getScriptCount());
        }
    }

    /**
     * Loads the source code from a script file.
     */
    private void loadScriptSource(ScriptFile script) {
        try {
            String source = Files.readString(script.getPath());
            script.setSource(source);
            script.setLastModified(Files.getLastModifiedTime(script.getPath()).toMillis());
        } catch (IOException e) {
            LOGGER.error("[PyFish] Failed to read script: {}", script.getPath(), e);
            script.setSource("");
            script.setLastError(new ScriptFile.ScriptError(
                    "Failed to read script: " + e.getMessage(),
                    null,
                    0,
                    0
            ));
        }
    }

    /**
     * Parses header directives from the script source.
     * Directives are comments in the format: # @pyfish key=value
     */
    private void parseHeaderDirectives(ScriptFile script) {
        Pattern directivePattern = Pattern.compile("^#\\s*@pyfish\\s+(\\w+)=(.+)$");
        String[] lines = script.getSource().split("\n");

        for (String line : lines) {
            Matcher matcher = directivePattern.matcher(line);
            if (matcher.matches()) {
                String key = matcher.group(1);
                String value = matcher.group(2).trim();
                script.getHeaderDirectives().put(key, value);
            }
        }
    }

    /**
     * Gets all packs.
     */
    public Map<String, ScriptPack> getPacks() {
        return new HashMap<>(packs);
    }

    /**
     * Gets a specific pack by namespace.
     */
    public ScriptPack getPack(String namespace) {
        return packs.get(namespace);
    }

    /**
     * Gets all scripts of a specific type across all packs.
     */
    public List<ScriptFile> getScripts(ScriptType type) {
        List<ScriptFile> scripts = new ArrayList<>();
        for (ScriptPack pack : packs.values()) {
            scripts.addAll(pack.getScripts(type));
        }
        return scripts;
    }

    /**
     * Gets all scripts across all packs.
     */
    public List<ScriptFile> getAllScripts() {
        List<ScriptFile> scripts = new ArrayList<>();
        for (ScriptPack pack : packs.values()) {
            scripts.addAll(pack.getAllScripts());
        }
        return scripts;
    }

    /**
     * Gets all scripts with errors.
     */
    public List<ScriptFile> getScriptsWithErrors() {
        List<ScriptFile> errorScripts = new ArrayList<>();
        for (ScriptFile script : getAllScripts()) {
            if (script.getLastError() != null) {
                errorScripts.add(script);
            }
        }
        return errorScripts;
    }

    /**
     * Gets the console API.
     */
    public ConsoleApi getConsoleApi() {
        return consoleApi;
    }

    /**
     * Reloads all scripts from disk.
     * This clears the current script list, clears event callbacks, and re-discovers scripts.
     * Note: This is a full reload; STARTUP scripts are not re-executed as registries are frozen.
     */
    public void reload() {
        LOGGER.info("[PyFish] Reloading scripts...");

        // Clear event callbacks to prevent duplicate handlers
        if (runtimeManager != null && runtimeManager.getMinecraftAPI() != null) {
            runtimeManager.getMinecraftAPI().clearAllEventCallbacks();
        }

        // Clear script packs and console buffer
        packs.clear();
        consoleApi.clearRingBuffer();

        // Re-discover scripts
        discoverScripts();

        LOGGER.info("[PyFish] Script discovery complete. Use executeScripts() to run reloaded scripts.");
    }

    /**
     * Reloads scripts of a specific type.
     * SERVER and CLIENT scripts can be reloaded; STARTUP scripts cannot.
     */
    public void reload(ScriptType type) {
        if (type == ScriptType.STARTUP) {
            LOGGER.warn("[PyFish] STARTUP scripts cannot be reloaded after registries freeze.");
            return;
        }

        LOGGER.info("[PyFish] Reloading {} scripts...", type);

        // Clear event callbacks for namespaces that have scripts of this type
        for (ScriptPack pack : packs.values()) {
            if (pack.getScriptCount(type) > 0 && runtimeManager != null && runtimeManager.getMinecraftAPI() != null) {
                runtimeManager.getMinecraftAPI().clearEventCallbacksForNamespace(pack.getNamespace());
            }
        }

        // For now, do a full reload to ensure consistent state
        // TODO: Implement selective reload that only re-executes changed scripts
        reload();
    }

    /**
     * Executes all discovered scripts using the runtime manager.
     * Scripts are executed in the correct order: STARTUP -> SERVER -> CLIENT
     */
    public void executeScripts() {
        if (runtimeManager == null) {
            LOGGER.warn("[PyFish] Cannot execute scripts: runtime manager not set");
            return;
        }

        LOGGER.info("[PyFish] Executing scripts...");

        // Execute STARTUP scripts first (if in initialization phase)
        List<ScriptFile> startupScripts = getScripts(ScriptType.STARTUP);
        if (!startupScripts.isEmpty()) {
            LOGGER.info("[PyFish] Executing {} STARTUP scripts", startupScripts.size());
            executeScriptList(startupScripts);
        }

        // Execute SERVER scripts
        List<ScriptFile> serverScripts = getScripts(ScriptType.SERVER);
        if (!serverScripts.isEmpty()) {
            LOGGER.info("[PyFish] Executing {} SERVER scripts", serverScripts.size());
            executeScriptList(serverScripts);
        }

        // Execute CLIENT scripts (only on client side)
        List<ScriptFile> clientScripts = getScripts(ScriptType.CLIENT);
        if (!clientScripts.isEmpty()) {
            LOGGER.info("[PyFish] Executing {} CLIENT scripts", clientScripts.size());
            executeScriptList(clientScripts);
        }

        LOGGER.info("[PyFish] Script execution complete");
    }

    /**
     * Executes a list of scripts in sorted order.
     */
    private void executeScriptList(List<ScriptFile> scripts) {
        List<ScriptFile> sorted = sortScriptsForExecution(scripts);
        for (ScriptFile script : sorted) {
            executeScript(script);
        }
    }

    /**
     * Executes a single script through the runtime manager.
     */
    private void executeScript(ScriptFile script) {
        try {
            String scriptName = script.getFullName();
            String code = script.getSource();

            // Load the script into the runtime manager
            runtimeManager.loadScript(scriptName, code, script.getNamespace());

            // Execute the script
            runtimeManager.executeScript(scriptName, null, null, true);

            script.setLoaded(true);
            script.setLastError(null);
            LOGGER.debug("[PyFish] Executed script: {}", script.getRelativePath());
        } catch (Exception e) {
            script.setLoaded(false);
            script.setLastError(new ScriptFile.ScriptError(
                    e.getMessage(),
                    null,
                    0,
                    0
            ));
            LOGGER.error("[PyFish] Failed to execute script {}: {}", script.getRelativePath(), e.getMessage(), e);
        }
    }

    /**
     * Sorts scripts for execution order.
     * init.py files come first, then alphabetical order.
     */
    public List<ScriptFile> sortScriptsForExecution(List<ScriptFile> scripts) {
        List<ScriptFile> sorted = new ArrayList<>(scripts);
        sorted.sort((a, b) -> {
            boolean aInit = a.getPath().getFileName().toString().equals("init.py");
            boolean bInit = b.getPath().getFileName().toString().equals("init.py");

            if (aInit && !bInit) {
                return -1;
            }
            if (!aInit && bInit) {
                return 1;
            }

            return a.getPath().getFileName().toString().compareToIgnoreCase(
                    b.getPath().getFileName().toString());
        });
        return sorted;
    }

    /**
     * Gets the total number of scripts.
     */
    public int getTotalScriptCount() {
        int count = 0;
        for (ScriptPack pack : packs.values()) {
            count += pack.getScriptCount();
        }
        return count;
    }

    /**
     * Gets the number of scripts of a specific type.
     */
    public int getScriptCount(ScriptType type) {
        int count = 0;
        for (ScriptPack pack : packs.values()) {
            count += pack.getScriptCount(type);
        }
        return count;
    }
}
