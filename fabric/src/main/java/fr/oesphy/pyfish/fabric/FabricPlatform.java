package fr.oesphy.pyfish.fabric;

import fr.oesphy.pyfish.platform.PlatformEventBridge;
import fr.oesphy.pyfish.platform.PlatformPythonModuleLocator;
import fr.oesphy.pyfish.platform.PyFishPlatform;

public class FabricPlatform implements PyFishPlatform {
    private final PlatformPythonModuleLocator pythonModuleLocator = new FabricPythonModuleLocator();
    private final PlatformEventBridge eventBridge = new FabricEventBridge();

    @Override
    public String getLoaderName() {
        return "Fabric";
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