package fr.oesphy.pyfish.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PyFishPyzModTest {
    @TempDir
    Path tempDir;

    @Test
    void inspectsValidArchiveAndReadsRequirements() throws IOException {
        Path archive = tempDir.resolve("example.pyz");
        writeArchive(archive, Map.of(
                "pyfish.mod.json", manifest("example_mod"),
                "__main__.py", "print('hello from pyz')\n",
                "requirements.txt", "requests==2.32.3\n"));

        PyFishPyzMod mod = PyFishPyzMod.inspect(archive);

        assertEquals("example_mod", mod.manifest().id());
        assertEquals("Example Mod", mod.manifest().name());
        assertEquals("1.0.0", mod.manifest().version());
        assertEquals(java.util.List.of("Oesphy", "Contributor"), mod.manifest().authors());
        assertEquals("requests==2.32.3\n", mod.requirements());
        assertEquals(archive.toAbsolutePath().normalize(), mod.archivePath());
    }

    @Test
    void rejectsArchiveWithoutMainEntrypoint() throws IOException {
        Path archive = tempDir.resolve("missing-main.pyz");
        writeArchive(archive, Map.of("pyfish.mod.json", manifest("missing_main")));

        IOException error = assertThrows(IOException.class, () -> PyFishPyzMod.inspect(archive));

        assertTrue(error.getMessage().contains("__main__.py"));
    }

    @Test
    void rejectsInvalidModId() throws IOException {
        Path archive = tempDir.resolve("invalid-id.pyz");
        writeArchive(archive, Map.of(
                "pyfish.mod.json", manifest("Invalid ID"),
                "__main__.py", "pass\n"));

        IOException error = assertThrows(IOException.class, () -> PyFishPyzMod.inspect(archive));

        assertTrue(error.getMessage().contains("Invalid mod id"));
    }

    @Test
    void rejectsUnsafeArchivePaths() throws IOException {
        Path archive = tempDir.resolve("unsafe.pyz");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("pyfish.mod.json", manifest("unsafe_mod"));
        entries.put("__main__.py", "pass\n");
        entries.put("../outside.py", "pass\n");
        writeArchive(archive, entries);

        IOException error = assertThrows(IOException.class, () -> PyFishPyzMod.inspect(archive));

        assertTrue(error.getMessage().contains("unsafe entry path"));
    }

    @Test
    void acceptsLegacyManifestName() throws IOException {
        Path archive = tempDir.resolve("legacy.pyz");
        writeArchive(archive, Map.of(
                "pyfish.json", manifest("legacy_mod"),
                "__main__.py", "pass\n"));

        PyFishPyzMod mod = PyFishPyzMod.inspect(archive);

        assertEquals("legacy_mod", mod.manifest().id());
    }

    private static String manifest(String modId) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "name": "Example Mod",
                  "version": "1.0.0",
                  "description": "A test Python mod",
                  "authors": ["Oesphy", "Contributor"],
                  "license": "MIT",
                  "homepage": "https://example.invalid"
                }
                """.formatted(modId);
    }

    private static void writeArchive(Path archive, Map<String, String> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(java.nio.file.Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
