package fr.oesphy.pyfish;

import fr.oesphy.pyfish.platform.PyFishPlatform;
import fr.oesphy.pyfish.runtime.GraalPyRuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PyFish {
    public static final String MODID = "pyfish";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private static GraalPyRuntimeManager pythonRuntime;
    private static boolean initialized;

    private PyFish() {
    }

    public static synchronized void initialize(PyFishPlatform platform) {
        if (initialized) {
            LOGGER.debug("[PyFish] Initialization skipped, runtime already active.");
            return;
        }

        initialized = true;
        LOGGER.info("[PyFish] Initializing on {}...", platform.getLoaderName());

        // Script entrypoints declare content, so this must stay in the loader's startup
        // phase while its registries can still accept registrations.
        pythonRuntime = new GraalPyRuntimeManager(platform.getPythonModuleLocator(), platform.getEventBridge());

        // Initialize the new ScriptManager for enhanced script discovery
        Path instanceRoot = Paths.get(".");
        pythonRuntime.initScriptManager(instanceRoot);

        // Legacy script loading (still works alongside ScriptManager)
        pythonRuntime.loadScriptsFromResources("pyfish");

        Path externalScripts = Paths.get("pyfish");
        pythonRuntime.loadScriptsFromPath(externalScripts);
        pythonRuntime.loadPyzModsFromDirectory(Paths.get("mods"));
        pythonRuntime.executeAllLoadedScripts();

        LOGGER.info("""
                
                ______     ______ _     _    
                | ___ \\    |  ___(_)   | |   
                | |_/ /   _| |_   _ ___| |__ 
                |  __/ | | |  _| | / __| '_ \\
                | |  | |_| | |   | \\__ \\ | | |
                \\_|   \\__, \\_|   |_|___/_| |_|
                       __/ |                 
                       |___/                  
                """);
        LOGGER.info("[PyFish] PyFish initialized on {}", platform.getLoaderName());
    }

    public static synchronized boolean isInitialized() {
        return initialized && pythonRuntime != null;
    }

    public static synchronized GraalPyRuntimeManager getPythonRuntime() {
        if (pythonRuntime == null) {
            throw new IllegalStateException("PyFish runtime is not initialized yet.");
        }
        return pythonRuntime;
    }
}