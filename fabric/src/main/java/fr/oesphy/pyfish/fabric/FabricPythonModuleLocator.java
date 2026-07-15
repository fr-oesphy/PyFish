package fr.oesphy.pyfish.fabric;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.platform.PlatformPythonModuleLocator;
import fr.oesphy.pyfish.runtime.PythonModuleScanner;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class FabricPythonModuleLocator implements PlatformPythonModuleLocator {
    @Override
    public Map<String, String> discoverPythonModules() {
        Map<String, String> discoveredModules = new LinkedHashMap<>();

        for (ModContainer modContainer : FabricLoader.getInstance().getAllMods()) {
            for (Path rootPath : modContainer.getRootPaths()) {
                PythonModuleScanner.scanTopLevelPythonFiles(rootPath, discoveredModules, PyFish.LOGGER,
                        modContainer.getMetadata().getId());
            }
        }

        return discoveredModules;
    }
}