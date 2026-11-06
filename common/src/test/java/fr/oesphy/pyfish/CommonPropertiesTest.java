package fr.oesphy.pyfish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CommonProperties}.
 *
 * <p>All tests are pure-Java — no GraalPy or Minecraft bootstrap required.
 */
class CommonPropertiesTest {

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    @Test
    void parseDefaults_emptyJson() {
        CommonProperties p = CommonProperties.parse("{}");
        assertTrue(p.allowNetwork());
        assertFalse(p.allowJavaInterop());
        assertEquals(5000, p.scriptTimeoutMs());
        assertTrue(p.autoRequirements());
        assertEquals(50, p.maxPipPackages());
    }

    @Test
    void parseAllFields() {
        String json = """
                {
                  "allow_network": false,
                  "allow_java_interop": true,
                  "script_timeout_ms": 2000,
                  "auto_requirements": false,
                  "max_pip_packages": 10
                }
                """;
        CommonProperties p = CommonProperties.parse(json);
        assertFalse(p.allowNetwork());
        assertTrue(p.allowJavaInterop());
        assertEquals(2000, p.scriptTimeoutMs());
        assertFalse(p.autoRequirements());
        assertEquals(10, p.maxPipPackages());
    }

    @Test
    void parseMissingFields_fallToDefaults() {
        CommonProperties p = CommonProperties.parse("{\"allow_network\": false}");
        assertFalse(p.allowNetwork());   // overridden
        assertFalse(p.allowJavaInterop()); // default
        assertEquals(5000, p.scriptTimeoutMs()); // default
    }

    @Test
    void parseNullJson_returnsDefaults() {
        CommonProperties p = CommonProperties.parse(null);
        assertTrue(p.allowNetwork());
    }

    @Test
    void parseNegativeTimeout_clampsToZero() {
        CommonProperties p = CommonProperties.parse("{\"script_timeout_ms\": -1}");
        assertEquals(0, p.scriptTimeoutMs());
    }

    @Test
    void parseNegativeMaxPip_clampsToZero() {
        CommonProperties p = CommonProperties.parse("{\"max_pip_packages\": -5}");
        assertEquals(0, p.maxPipPackages());
    }

    // -------------------------------------------------------------------------
    // JSON serialisation
    // -------------------------------------------------------------------------

    @Test
    void toJsonContainsAllKeys() {
        CommonProperties p = CommonProperties.parse("{}");
        String json = CommonProperties.toJson(p);
        assertTrue(json.contains("allow_network"));
        assertTrue(json.contains("allow_java_interop"));
        assertTrue(json.contains("script_timeout_ms"));
        assertTrue(json.contains("auto_requirements"));
        assertTrue(json.contains("max_pip_packages"));
    }

    @Test
    void toJsonRoundTrip() {
        String original = """
                {
                  "allow_network": false,
                  "allow_java_interop": true,
                  "script_timeout_ms": 1000,
                  "auto_requirements": false,
                  "max_pip_packages": 25
                }
                """;
        CommonProperties p = CommonProperties.parse(original);
        String json = CommonProperties.toJson(p);
        CommonProperties reparsed = CommonProperties.parse(json);

        assertFalse(reparsed.allowNetwork());
        assertTrue(reparsed.allowJavaInterop());
        assertEquals(1000, reparsed.scriptTimeoutMs());
        assertFalse(reparsed.autoRequirements());
        assertEquals(25, reparsed.maxPipPackages());
    }

    // -------------------------------------------------------------------------
    // load() / file I/O
    // -------------------------------------------------------------------------

    @Test
    void load_createsDefaultFileIfMissing(@TempDir Path tempDir) {
        CommonProperties p = CommonProperties.load(tempDir);
        // File should have been created
        assertTrue(Files.exists(tempDir.resolve("pyfish").resolve("config.json")));
        // Should have default values
        assertTrue(p.allowNetwork());
    }

    @Test
    void load_readsExistingFile(@TempDir Path tempDir) throws IOException {
        Path configDir = tempDir.resolve("pyfish");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.json"),
                "{\"allow_network\": false, \"max_pip_packages\": 7}",
                StandardCharsets.UTF_8);

        CommonProperties p = CommonProperties.load(tempDir);
        assertFalse(p.allowNetwork());
        assertEquals(7, p.maxPipPackages());
    }

    @Test
    void load_updatesStaticInstance(@TempDir Path tempDir) {
        CommonProperties.load(tempDir);
        assertNotNull(CommonProperties.get());
    }

    @Test
    void get_returnsNonNull() {
        // Even before load() the default singleton is set
        assertNotNull(CommonProperties.get());
    }

    @Test
    void toString_containsFields() {
        CommonProperties p = CommonProperties.parse("{}");
        String s = p.toString();
        assertTrue(s.contains("allowNetwork=true"));
        assertTrue(s.contains("maxPipPackages=50"));
    }
}
