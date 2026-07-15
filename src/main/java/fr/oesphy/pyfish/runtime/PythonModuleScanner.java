package fr.oesphy.pyfish.runtime;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class PythonModuleScanner {
    private PythonModuleScanner() {
    }

    public static void scanTopLevelPythonFiles(Path root, Map<String, String> discoveredModules, Logger logger,
            String sourceLabel) {
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return;
        }

        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".py"))
                    .forEach(path -> registerModule(path.getFileName().toString(), discoveredModules, logger,
                            sourceLabel, () -> Files.readString(path, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            logger.debug("[PyFish] Failed to scan Python modules in {}: {}", sourceLabel, e.getMessage());
        }
    }

    public static void scanJarTopLevelPythonFiles(JarFile jarFile, Map<String, String> discoveredModules, Logger logger,
            String sourceLabel) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.endsWith(".py") || name.contains("/")) {
                continue;
            }

            registerModule(name, discoveredModules, logger, sourceLabel,
                    () -> new String(jarFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void registerModule(String fileName, Map<String, String> discoveredModules, Logger logger,
            String sourceLabel, ModuleCodeSupplier supplier) {
        if ("pyfish.py".equals(fileName)) {
            return;
        }

        String moduleName = fileName.substring(0, fileName.length() - 3);
        if (discoveredModules.containsKey(moduleName)) {
            // Discovery order defines precedence: loader-provided modules win over generic
            // code-source and classloader fallbacks. Replacing one here makes builds depend
            // on URL enumeration order.
            return;
        }

        try {
            discoveredModules.put(moduleName, supplier.get());
            logger.debug("[PyFish] Discovered Python module '{}' from {}", moduleName, sourceLabel);
        } catch (IOException e) {
            logger.error("[PyFish] Failed to read Python module '{}' from {}: {}", moduleName, sourceLabel,
                    e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ModuleCodeSupplier {
        String get() throws IOException;
    }
}