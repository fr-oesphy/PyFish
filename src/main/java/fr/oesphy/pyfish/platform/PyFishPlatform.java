package fr.oesphy.pyfish.platform;

public interface PyFishPlatform {
    String getLoaderName();

    PlatformPythonModuleLocator getPythonModuleLocator();

    PlatformEventBridge getEventBridge();
}
