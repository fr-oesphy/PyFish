package fr.oesphy.pyfish.platform;

import java.util.Map;

public interface PlatformPythonModuleLocator {
    Map<String, String> discoverPythonModules();
}
