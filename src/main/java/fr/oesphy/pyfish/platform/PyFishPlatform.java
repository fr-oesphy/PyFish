package fr.oesphy.pyfish.platform;

import java.nio.file.Path;
import java.util.Optional;

public interface PyFishPlatform {
    String getLoaderName();

    PlatformPythonModuleLocator getPythonModuleLocator();

    PlatformEventBridge getEventBridge();

    /**
     * Returns the root of PyFish's own mod archive. Packaged runtimes use it to
     * materialize GraalPy resources without relying on loader classpath tricks.
     */
    Optional<Path> getRuntimeResourceRoot();
}
