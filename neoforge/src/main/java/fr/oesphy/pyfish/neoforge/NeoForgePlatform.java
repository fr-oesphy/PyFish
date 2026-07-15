package fr.oesphy.pyfish.neoforge;

import fr.oesphy.pyfish.platform.PlatformEventBridge;
import fr.oesphy.pyfish.platform.PlatformPythonModuleLocator;
import fr.oesphy.pyfish.platform.PyFishPlatform;

public class NeoForgePlatform implements PyFishPlatform {
    private final PlatformPythonModuleLocator pythonModuleLocator = new NeoForgePythonModuleLocator();
    private final PlatformEventBridge eventBridge = new NeoForgeEventBridge();

    @Override
    public String getLoaderName() {
        return "NeoForge";
    }

    @Override
    public PlatformPythonModuleLocator getPythonModuleLocator() {
        return pythonModuleLocator;
    }

    @Override
    public PlatformEventBridge getEventBridge() {
        return eventBridge;
    }
}
