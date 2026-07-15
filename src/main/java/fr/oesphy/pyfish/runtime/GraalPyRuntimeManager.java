package fr.oesphy.pyfish.runtime;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.api.MinecraftAPI;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import fr.oesphy.pyfish.content.PyFishScriptContext;
import fr.oesphy.pyfish.platform.PlatformEventBridge;
import fr.oesphy.pyfish.platform.PlatformPythonModuleLocator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public class GraalPyRuntimeManager {
    private static final Logger LOGGER = PyFish.LOGGER;
    private final Map<String, LoadedScript> loadedScripts = new LinkedHashMap<>();

    private final MinecraftAPI minecraftAPI;
    private final PythonDependencyManager dependencyManager;
    private final PlatformPythonModuleLocator pythonModuleLocator;

    // A single context keeps imported modules and Python callback objects alive between
    // Minecraft events. PYZ entrypoints still get their own globals in PyFishPyzExecutor.
    private Context sharedContext;
    private final LoggingOutputStream stdout;
    private final LoggingOutputStream stderr;

    public GraalPyRuntimeManager(PlatformPythonModuleLocator pythonModuleLocator, PlatformEventBridge eventBridge) {
        this.pythonModuleLocator = pythonModuleLocator;
        minecraftAPI = new MinecraftAPI(eventBridge);
        dependencyManager = new PythonDependencyManager();
        stdout = new LoggingOutputStream(false);
        stderr = new LoggingOutputStream(true);
        LOGGER.info("[PyFish] GraalPyRuntimeManager initialized (Shared Context Mode).");
    }

    public MinecraftAPI getMinecraftAPI() {
        return minecraftAPI;
    }

    public void loadScript(String name, String code, String modId) {
        String fullName = (modId != null) ? modId + ":" + name : name;
        loadedScripts.put(fullName, LoadedScript.source(code, modId));
        LOGGER.info("[PyFish] Script loaded: {} (Mod: {})", fullName, modId != null ? modId : "global");
    }

    private synchronized Context getContext() {
        if (sharedContext == null) {
            LOGGER.info("[PyFish] Creating GraalPy context...");

            try {
                // GraalPyResources wires the language home and stdlib VFS. A plain
                // Context builder works in development, then loses imports in packaged jars.
                Context.Builder contextBuilder = GraalPyResources.contextBuilder()
                        .allowAllAccess(true)
                        .allowHostAccess(HostAccess.ALL)
                        .allowHostClassLookup(className -> true)
                        // PYZ mods are trusted local code, like jar mods. Host access is an API
                        // boundary here, not a security sandbox.
                        .allowNativeAccess(true)
                        // GraalPy rejects an unset CWD on some Windows launcher setups.
                        .currentWorkingDirectory(Paths.get(".").toAbsolutePath())
                        .out(stdout)
                        .err(stderr)
                        .allowExperimentalOptions(true)
                        .option("python.EmulateJython", "true")
                        .option("python.IsolateNativeModules", "true")
                        .option("engine.WarnInterpreterOnly", "false");

                Context ctx = contextBuilder.build();
                initContext(ctx);
                // Do not publish a half-initialized context: subsequent calls cannot repair
                // a missing pyfish module or partially populated sys.modules.
                sharedContext = ctx;

                LOGGER.info("[PyFish] GraalPy context created and initialized successfully.");
            } catch (Exception e) {
                LOGGER.error("[PyFish] Failed to create or initialize GraalPy context", e);
                throw new RuntimeException("Failed to initialize Python runtime", e);
            }
        }
        return sharedContext;
    }

    private void initContext(Context context) throws Exception {
        LOGGER.info("[PyFish] Initializing Shared Python Context...");

        // Force language initialization before injecting host objects. This keeps failures
        // attributable to GraalPy setup rather than to the first user script.
        context.eval("python", "print('[PyFish/Python] GraalPy Initializing...')");

        Value bindings = context.getBindings("python");
        bindings.putMember("log", LOGGER);
        bindings.putMember("_mc_internal", minecraftAPI);

        String pyfishCode = new String(
                getClass().getClassLoader().getResourceAsStream("pyfish.py").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        // Executing into a real module object matters for pickling, relative imports and
        // libraries which inspect __module__; evaluating pyfish.py as loose code does not.
        bindings.putMember("_pyfish_code", pyfishCode);
        context.eval("python",
                "import sys, types\n" +
                        "pyfish_mod = types.ModuleType('pyfish')\n" +
                        "pyfish_mod._mc_internal = _mc_internal\n" +
                        "try:\n" +
                        "    import java as _pyfish_java\n" +
                        "    pyfish_mod.__dict__['java'] = _pyfish_java\n" +
                        "except Exception:\n" +
                        "    pass\n" +
                        "exec(_pyfish_code, pyfish_mod.__dict__)\n" +
                        "sys.modules['pyfish'] = pyfish_mod\n" +
                        "print('[PyFish/Python] pyfish module registered.')");

        registerAllModPythonModules(context);
    }

    /**
     * Scans the current platform for top-level Python modules and registers them as
     * importable modules. ClassLoader scanning is used as a generic fallback so the
     * common runtime can work on multiple loaders.
     */
    private void registerAllModPythonModules(Context context) {
        LOGGER.info("[PyFish] Scanning the platform for Python modules at root resources...");

        Map<String, String> discoveredModules = new LinkedHashMap<>();

        try {
            discoveredModules.putAll(pythonModuleLocator.discoverPythonModules());
        } catch (Exception e) {
            LOGGER.warn("[PyFish] Platform module scan failed, falling back to classloader lookup: {}",
                    e.getMessage());
        }

        discoverModulesViaCodeSource(discoveredModules);
        discoverModulesViaClassLoader(discoveredModules);

        if (discoveredModules.isEmpty()) {
            LOGGER.info("[PyFish] No Python modules found at root resources.");
            return;
        }

        registerModulesWithRetry(context, discoveredModules);
    }

    private void discoverModulesViaClassLoader(Map<String, String> discoveredModules) {
        ClassLoader cl = getClass().getClassLoader();

        try {
            var urls = cl.getResources("");
            while (urls.hasMoreElements()) {
                scanClassLoaderRoot(urls.nextElement(), discoveredModules);
            }
        } catch (IOException e) {
            LOGGER.debug("[PyFish] Failed to enumerate classloader roots: {}", e.getMessage());
        }
    }

    private void discoverModulesViaCodeSource(Map<String, String> discoveredModules) {
        try {
            CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return;
            }

            Path codeSourcePath = Paths.get(codeSource.getLocation().toURI());
            if (Files.isDirectory(codeSourcePath)) {
                PythonModuleScanner.scanTopLevelPythonFiles(codeSourcePath, discoveredModules, LOGGER,
                        codeSourcePath.toString());
                return;
            }

            if (Files.isRegularFile(codeSourcePath)) {
                try (JarFile jarFile = new JarFile(codeSourcePath.toFile())) {
                    PythonModuleScanner.scanJarTopLevelPythonFiles(jarFile, discoveredModules, LOGGER,
                            codeSourcePath.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[PyFish] Failed to scan runtime code source for Python modules: {}", e.getMessage());
        }
    }

    private void scanClassLoaderRoot(URL url, Map<String, String> discoveredModules) {
        try {
            String protocol = url.getProtocol();
            if ("file".equals(protocol) || "union".equals(protocol)) {
                PythonModuleScanner.scanTopLevelPythonFiles(Paths.get(url.toURI()), discoveredModules, LOGGER,
                        url.toString());
                return;
            }

            if ("jar".equals(protocol)) {
                var connection = url.openConnection();
                if (connection instanceof JarURLConnection jarURLConnection) {
                    try (JarFile jarFile = jarURLConnection.getJarFile()) {
                        PythonModuleScanner.scanJarTopLevelPythonFiles(jarFile, discoveredModules, LOGGER,
                                jarFile.getName());
                    }
                }
                return;
            }

            LOGGER.debug("[PyFish] Unsupported classloader root protocol '{}': {}", protocol, url);
        } catch (Exception e) {
            LOGGER.debug("[PyFish] Failed to scan classloader root '{}': {}", url, e.getMessage());
        }
    }

    /**
     * Retries modules whose imports are not available yet. This is deliberately not a
     * dependency resolver: a pass with no progress is the reliable stopping condition for
     * missing modules and import cycles that execute code too early.
     */
    private void registerModulesWithRetry(Context context, Map<String, String> discoveredModules) {
        Map<String, String> pending = new LinkedHashMap<>(discoveredModules);
        Set<String> registered = new HashSet<>();
        Map<String, String> lastErrors = new LinkedHashMap<>();
        int maxRetries = pending.size();

        for (int attempt = 0; attempt < maxRetries && !pending.isEmpty(); attempt++) {
            Map<String, String> failed = new LinkedHashMap<>();

            for (Map.Entry<String, String> entry : pending.entrySet()) {
                try {
                    registerModule(context, entry.getKey(), entry.getValue());
                    registered.add(entry.getKey());
                    lastErrors.remove(entry.getKey());
                    LOGGER.info("[PyFish] Registered Python module: {}", entry.getKey());
                } catch (Exception e) {
                    failed.put(entry.getKey(), entry.getValue());
                    lastErrors.put(entry.getKey(), e.getMessage());
                    if (attempt == 0) {
                        LOGGER.debug("[PyFish] Module '{}' deferred (attempt {}): {}",
                                entry.getKey(), attempt + 1, e.getMessage());
                    }
                }
            }

            if (failed.size() == pending.size()) {
                for (String name : failed.keySet()) {
                    LOGGER.error("[PyFish] Failed to register Python module '{}' after {} attempts: {}", name,
                            attempt + 1, lastErrors.getOrDefault(name, "unknown error"));
                }
                break;
            }

            pending = failed;
        }

        LOGGER.info("[PyFish] Module registration complete: {} registered, {} failed",
                registered.size(), pending.size());
    }

    private void registerModule(Context context, String moduleName, String code) {
        Value bindings = context.getBindings("python");
        bindings.putMember("_tmp_code", code);
        bindings.putMember("_tmp_name", moduleName);
        // Publish before exec, as Python's import machinery does, so modules can resolve
        // recursive imports. A failed exec removes the placeholder to keep retries clean.
        context.eval("python",
                "import sys, types\n" +
                        "name = _tmp_name\n" +
                        "if name not in sys.modules:\n" +
                        "    mod = types.ModuleType(name)\n" +
                        "    mod.__file__ = f'<pyfish:{name}>'\n" +
                        "    sys.modules[name] = mod\n" +
                        "    try:\n" +
                        "        if 'log' in globals(): mod.log = log\n" +
                        "        if '_mc_internal' in globals(): mod._mc_internal = _mc_internal\n" +
                        "        try:\n" +
                        "            import java as _pyfish_java\n" +
                        "            mod.__dict__['java'] = _pyfish_java\n" +
                        "        except Exception:\n" +
                        "            try:\n" +
                        "                mod.__dict__['java'] = __import__('java')\n" +
                        "            except Exception:\n" +
                        "                pass\n" +
                        "        exec(_tmp_code, mod.__dict__)\n" +
                        "        print(f'[PyFish/Python] Module registered from resources: {name}')\n" +
                        "    except Exception:\n" +
                        "        sys.modules.pop(name, None)\n" +
                        "        raise");
    }

    public void executeScript(String name, ServerLevel world, ServerPlayer player, boolean exposeMinecraftAPI) {
        try {
            LoadedScript script = loadedScripts.get(name);
            if (script == null) {
                LOGGER.warn("[PyFish] Script not found: {}", name);
                return;
            }

            String modId = script.modId();
            Context context = getContext();

            Value bindings = context.getBindings("python");
            if (world != null)
                bindings.putMember("world", world);
            if (player != null)
                bindings.putMember("player", player);

            if (modId != null) {
                Path libPath = Paths.get("pyfish", "libs", modId).toAbsolutePath();
                if (Files.exists(libPath)) {
                    String pathStr = libPath.toString().replace("\\", "\\\\");
                    // sys.path belongs to the shared interpreter and survives event callbacks;
                    // keep it stable when the same script is executed more than once.
                    context.eval("python",
                            "import sys; p='" + pathStr + "'; sys.path.append(p) if p not in sys.path else None");
                    LOGGER.debug("[PyFish] Added lib path: {}", libPath);
                }
            }

            LOGGER.info("[PyFish] Executing script: {} (Mod: {})", name, modId);
            try (PyFishScriptContext.Scope ignored = PyFishScriptContext.push(modId)) {
                if (script.pyzMod() != null) {
                    PyFishPyzExecutor.execute(context, script.pyzMod());
                } else {
                    context.eval("python", script.code());
                }
            }

        } catch (Exception e) {
            LOGGER.error("[PyFish] Error executing script {} ({}): {}", name,
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    public void executeAllLoadedScripts() {
        LOGGER.info("[PyFish] Executing all loaded scripts...");
        List<String> scripts = new ArrayList<>(loadedScripts.keySet());

        // Legacy directory mods use init.py to populate helpers imported by sibling scripts.
        // The name tie-breaker also keeps startup reproducible across filesystems.
        scripts.sort((a, b) -> {
            boolean aInit = a.equals("init") || a.endsWith("/init");
            boolean bInit = b.equals("init") || b.endsWith("/init");
            if (aInit && !bInit)
                return -1;
            if (!aInit && bInit)
                return 1;
            return a.compareToIgnoreCase(b);
        });

        for (String name : scripts) {
            executeScript(name, null, null, true);
        }
    }

    public void loadScriptsFromResources(String folder) {
        try {
            var cl = getClass().getClassLoader();
            var urls = cl.getResources(folder);
            while (urls.hasMoreElements()) {
                var url = urls.nextElement();
                LOGGER.info("[PyFish] Scanning resources at: {}", url);

                String protocol = url.getProtocol();

                if (protocol.equals("union") || protocol.equals("file")) {
                    java.nio.file.Path rootDir = java.nio.file.Paths.get(url.toURI());
                    loadScriptsFromPath(rootDir);
                } else if (protocol.equals("jar")) {
                    String path = url.getPath();
                    String jarPath = path.substring(5, path.indexOf("!"));
                    LOGGER.info("[PyFish] Scanning JAR: {}", jarPath);

                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(
                            java.net.URLDecoder.decode(jarPath, java.nio.charset.StandardCharsets.UTF_8))) {
                        java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();

                        // Scan first, then install every requirements file before exposing any
                        // script from the jar. Jar entry order is not a loading contract.
                        Map<String, String> requirements = new HashMap<>();
                        List<JarScriptEntry> scripts = new ArrayList<>();

                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (!name.startsWith(folder + "/") || entry.isDirectory())
                                continue;

                            String relative = name.substring(folder.length() + 1);
                            String[] parts = relative.split("/");

                            if (parts[0].equals("libs"))
                                continue;

                            if (parts[0].equals("scripts")) {
                                if (name.endsWith(".py") && !name.endsWith("pyfish.py")) {
                                    String scriptPath = relative.substring(8).replace(".py", "");
                                    scripts.add(new JarScriptEntry(scriptPath, entry, null));
                                }
                            } else if (parts.length >= 2) {
                                String modId = parts[0];
                                if (parts[1].equals("requirements.txt")) {
                                    try (var is = jar.getInputStream(entry)) {
                                        requirements.put(modId, new String(is.readAllBytes(),
                                                java.nio.charset.StandardCharsets.UTF_8));
                                    }
                                } else if (parts[1].equals("scripts") && name.endsWith(".py")) {
                                    int scriptsIndex = relative.indexOf("/scripts/");
                                    if (scriptsIndex != -1) {
                                        String scriptRelativePath = relative.substring(scriptsIndex + 9).replace(".py",
                                                "");
                                        scripts.add(new JarScriptEntry(scriptRelativePath, entry, modId));
                                    }
                                }
                            }
                        }

                        requirements.forEach(dependencyManager::installDependencies);
                        for (JarScriptEntry script : scripts) {
                            try (var is = jar.getInputStream(script.entry)) {
                                String code = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                loadScript(script.name, code, script.modId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[PyFish] Failed to load scripts from {}", folder, e);
        }
    }

    public void loadScriptsFromPath(Path rootDir) {
        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            return;
        }

        LOGGER.info("[PyFish] Scanning for scripts in path: {}", rootDir);

        try (var mainStream = Files.list(rootDir)) {
            // Preserve the historical directory layout before loading standalone PYZ files;
            // mods/ is last so a root-level archive wins duplicate namespace detection.
            mainStream
                    .sorted(Comparator.comparingInt(this::scriptLoadPriority)
                            .thenComparing(path -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER))
                    .forEach(this::loadPathEntry);
        } catch (IOException e) {
            LOGGER.error("[PyFish] Error listing directory: {}", rootDir, e);
        }
    }

    private void loadPathEntry(Path subPath) {
        String subName = subPath.getFileName().toString();
        if (subName.equals("libs")) {
            return;
        }

        if (subName.equals("mods") && Files.isDirectory(subPath)) {
            loadPyzModsFromDirectory(subPath);
        } else if (Files.isRegularFile(subPath) && isPyzArchive(subPath)) {
            loadPyzMod(subPath);
        } else if (subName.equals("scripts") && Files.isDirectory(subPath)) {
            loadScriptsFromSubfolder(subPath, null);
        } else if (Files.isDirectory(subPath)) {
            loadLogicalModDirectory(subPath, subName);
        }
    }

    private void loadLogicalModDirectory(Path modDirectory, String modId) {
        Path requirementsFile = modDirectory.resolve("requirements.txt");
        if (Files.exists(requirementsFile)) {
            try {
                dependencyManager.installDependencies(modId, Files.readString(requirementsFile));
            } catch (IOException e) {
                LOGGER.error("[PyFish] Failed to read requirements for mod: {}", modId, e);
            }
        }

        Path scriptsDirectory = modDirectory.resolve("scripts");
        if (Files.isDirectory(scriptsDirectory)) {
            loadScriptsFromSubfolder(scriptsDirectory, modId);
        }
    }

    private int scriptLoadPriority(Path path) {
        String name = path.getFileName().toString();
        if (Files.isDirectory(path) && !name.equals("mods")) {
            return 0;
        }
        if (Files.isRegularFile(path) && isPyzArchive(path)) {
            return 1;
        }
        if (Files.isDirectory(path) && name.equals("mods")) {
            return 2;
        }
        return 3;
    }

    public void loadPyzModsFromDirectory(Path modsDirectory) {
        if (!Files.isDirectory(modsDirectory)) {
            return;
        }

        try (var archives = Files.list(modsDirectory)) {
            archives
                    .filter(Files::isRegularFile)
                    .filter(this::isPyzArchive)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(this::loadPyzMod);
        } catch (IOException e) {
            LOGGER.error("[PyFish] Error listing Python mod archives in {}", modsDirectory, e);
        }
    }

    private boolean isPyzArchive(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pyz");
    }

    private void loadPyzMod(Path archivePath) {
        try {
            PyFishPyzMod pyzMod = PyFishPyzMod.inspect(archivePath);
            String modId = pyzMod.manifest().id();
            if (isNamespaceAlreadyLoaded(modId)) {
                LOGGER.error("[PyFish] Rejected Python mod archive '{}': namespace '{}' is already loaded",
                        archivePath, modId);
                return;
            }

            // Assets must be visible when loader pack discovery runs, not only when the
            // entrypoint eventually executes.
            PyFishResourceGenerator.addArchiveResources(pyzMod.archivePath());
            dependencyManager.installDependencies(modId, pyzMod.requirements());

            String fullName = modId + ":__main__";
            loadedScripts.put(fullName, LoadedScript.pyz(pyzMod));
            LOGGER.info("[PyFish] Python mod archive loaded: {} {} ({}) from {}",
                    pyzMod.manifest().name(), pyzMod.manifest().version(), modId, archivePath);
        } catch (Exception e) {
            LOGGER.error("[PyFish] Rejected Python mod archive '{}': {}", archivePath, e.getMessage());
        }
    }

    private boolean isNamespaceAlreadyLoaded(String modId) {
        return loadedScripts.values().stream().anyMatch(script -> modId.equals(script.modId()));
    }

    private void loadScriptsFromSubfolder(Path subDir, String modId) {
        try (var stream = Files.walk(subDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".py"))
                    .forEach(p -> {
                        try {
                            if (p.getFileName().toString().equals("pyfish.py"))
                                return;

                            String relativePath = subDir.relativize(p).toString().replace("\\", "/");
                            String scriptName = relativePath.replace(".py", "");
                            String code = Files.readString(p);
                            loadScript(scriptName, code, modId);
                        } catch (IOException e) {
                            LOGGER.error("[PyFish] Cannot read script {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("[PyFish] Error walking scripts folder: {}", subDir, e);
        }
    }

    public void shutdown() {
        if (sharedContext != null) {
            try {
                sharedContext.close();
            } catch (Exception e) {
                LOGGER.error("[PyFish] Error closing GraalPy context", e);
            }
        }
    }

    private record LoadedScript(
            String code,
            String modId,
            PyFishPyzMod pyzMod) {
        static LoadedScript source(String code, String modId) {
            return new LoadedScript(code, modId, null);
        }

        static LoadedScript pyz(PyFishPyzMod mod) {
            return new LoadedScript(
                    null,
                    mod.manifest().id(),
                    mod);
        }
    }

    private static class LoggingOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final boolean isError;

        public LoggingOutputStream(boolean isError) {
            this.isError = isError;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                String message = buffer.toString();
                if (!message.isEmpty()) {
                    if (isError)
                        LOGGER.error("[PyFish/Python] {}", message);
                    else
                        LOGGER.info("[PyFish/Python] {}", message);
                }
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }
    }

    private static class JarScriptEntry {
        final String name;
        final java.util.jar.JarEntry entry;
        final String modId;

        JarScriptEntry(String name, java.util.jar.JarEntry entry, String modId) {
            this.name = name;
            this.entry = entry;
            this.modId = modId;
        }
    }
}