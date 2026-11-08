package fr.oesphy.pyfish.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonModuleScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scanTopLevelPythonFilesOnlyLoadsTopLevelModules() throws IOException {
        Files.writeString(tempDir.resolve("java_logging.py"), "print('ok')", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("pyfish.py"), "print('skip')", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested").resolve("java_requests.py"), "print('nested')",
                StandardCharsets.UTF_8);

        Map<String, String> discoveredModules = new LinkedHashMap<>();
        PythonModuleScanner.scanTopLevelPythonFiles(tempDir, discoveredModules, LoggerFactory.getLogger("test"),
                "tempDir");

        assertEquals(1, discoveredModules.size());
        assertTrue(discoveredModules.containsKey("java_logging"));
        assertFalse(discoveredModules.containsKey("pyfish"));
        assertFalse(discoveredModules.containsKey("java_requests"));
    }

    @Test
    void scanJarTopLevelPythonFilesLoadsRootModules() throws IOException {
        Path jarPath = tempDir.resolve("modules.jar");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            writeEntry(zipOutputStream, "java_requests.py", "print('requests')");
            writeEntry(zipOutputStream, "pyfish.py", "print('skip')");
            writeEntry(zipOutputStream, "nested/java_sqlite3.py", "print('nested')");
        }

        Map<String, String> discoveredModules = new LinkedHashMap<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            PythonModuleScanner.scanJarTopLevelPythonFiles(jarFile, discoveredModules, LoggerFactory.getLogger("test"),
                    jarPath.toString());
        }

        assertEquals(1, discoveredModules.size());
        assertTrue(discoveredModules.containsKey("java_requests"));
        assertFalse(discoveredModules.containsKey("pyfish"));
        assertFalse(discoveredModules.containsKey("java_sqlite3"));
    }

    @Test
    void corePythonHelpersStayAvailableAsResources() {
        ClassLoader classLoader = PythonModuleScannerTest.class.getClassLoader();
        assertNotNull(classLoader.getResource("pyfish.py"));
        assertNotNull(classLoader.getResource("java_multiprocessing.py"));
        assertNotNull(classLoader.getResource("java_pillow.py"));
        for (String removed : java.util.List.of("asyncio", "logging", "pathlib", "uuid", "sqlite3", "subprocess", "requests", "pyperclip")) {
            org.junit.jupiter.api.Assertions.assertNull(classLoader.getResource("java_" + removed + ".py"));
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }
}
