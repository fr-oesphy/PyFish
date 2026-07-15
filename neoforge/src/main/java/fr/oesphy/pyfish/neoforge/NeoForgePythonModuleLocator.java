package fr.oesphy.pyfish.neoforge;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.platform.PlatformPythonModuleLocator;
import fr.oesphy.pyfish.runtime.PythonModuleScanner;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public class NeoForgePythonModuleLocator implements PlatformPythonModuleLocator {
    @Override
    public Map<String, String> discoverPythonModules() {
        Map<String, String> discoveredModules = new LinkedHashMap<>();

        ModList.get().getMods().forEach(mod -> {
            try {
                IModFile modFile = mod.getOwningFile().getFile();
                scanModCandidates(modFile, discoveredModules, mod.getModId());
            } catch (Exception e) {
                PyFish.LOGGER.debug("[PyFish] Failed to scan NeoForge mod '{}' for root Python modules: {}",
                        mod.getModId(), e.getMessage());
            }
        });

        return discoveredModules;
    }

    private void scanModCandidates(IModFile modFile, Map<String, String> discoveredModules, String modId)
            throws ReflectiveOperationException {
        Set<Path> candidates = new LinkedHashSet<>();

        tryAddPath(candidates, invokePathMethod(modFile, "findResource", "."));
        tryAddPath(candidates, invokePathMethod(modFile, "getFilePath"));

        Object secureJar = invokeObjectMethod(modFile, "getSecureJar");
        if (secureJar != null) {
            tryAddPath(candidates, invokePathMethod(secureJar, "getPrimaryPath"));
            tryAddPath(candidates, invokePathMethod(secureJar, "getRootPath"));
            tryAddPath(candidates, invokePathMethod(secureJar, "getPath", "."));
            tryAddPath(candidates, invokePathMethod(secureJar, "getPath", ""));
        }

        for (Path candidate : candidates) {
            scanCandidate(candidate, discoveredModules, modId);
        }
    }

    private void scanCandidate(Path candidate, Map<String, String> discoveredModules, String modId) {
        if (candidate == null || !Files.exists(candidate)) {
            return;
        }

        if (Files.isDirectory(candidate)) {
            PythonModuleScanner.scanTopLevelPythonFiles(candidate, discoveredModules, PyFish.LOGGER, modId);
            return;
        }

        if (Files.isRegularFile(candidate) && looksLikeArchive(candidate)) {
            try (JarFile jarFile = new JarFile(candidate.toFile())) {
                PythonModuleScanner.scanJarTopLevelPythonFiles(jarFile, discoveredModules, PyFish.LOGGER,
                        candidate.toString());
            } catch (Exception e) {
                PyFish.LOGGER.debug("[PyFish] Failed to scan NeoForge archive '{}' for root Python modules: {}",
                        candidate, e.getMessage());
            }
        }
    }

    private boolean looksLikeArchive(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString().toLowerCase() : "";
        return fileName.endsWith(".jar") || fileName.endsWith(".zip");
    }

    private Path resolveModRoot(IModFile modFile) throws ReflectiveOperationException {
        try {
            Method findResource = modFile.getClass().getMethod("findResource", String.class);
            return (Path) findResource.invoke(modFile, ".");
        } catch (NoSuchMethodException ignored) {
            // Fall through to newer loader APIs.
        }

        try {
            Method getFilePath = modFile.getClass().getMethod("getFilePath");
            return (Path) getFilePath.invoke(modFile);
        } catch (NoSuchMethodException ignored) {
            // Fall through to secure-jar based APIs.
        }

        Object secureJar = modFile.getClass().getMethod("getSecureJar").invoke(modFile);
        try {
            return (Path) secureJar.getClass().getMethod("getRootPath").invoke(secureJar);
        } catch (NoSuchMethodException ignored) {
            return (Path) secureJar.getClass().getMethod("getPath", String.class).invoke(secureJar, ".");
        }
    }

    private void tryAddPath(Set<Path> candidates, Path path) {
        if (path != null) {
            candidates.add(path);
        }
    }

    private Path invokePathMethod(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Object result = invokeObjectMethod(target, methodName, args);
        return result instanceof Path path ? path : null;
    }

    private Object invokeObjectMethod(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }

        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
