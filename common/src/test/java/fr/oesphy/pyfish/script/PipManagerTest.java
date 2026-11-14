package fr.oesphy.pyfish.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PipManager}.
 *
 * <p>Each test creates a fresh {@link PipManager} instance (not the singleton)
 * to avoid cross-test state contamination.
 */
class PipManagerTest {

    @TempDir
    Path tempDir;

    private PipManager pip;

    @BeforeEach
    void freshManager() {
        pip = new PipManager();
        pip.init(tempDir);
    }

    // -------------------------------------------------------------------------
    // install
    // -------------------------------------------------------------------------

    @Test
    void installAddsPackage() {
        // Default CommonProperties has allowNetwork=true, so install should succeed
        String result = pip.install("requests==2.32.0");
        assertNotNull(result);
        assertFalse(result.toLowerCase().contains("disabled"),
                "Should not be blocked by network config in defaults, got: " + result);
        assertEquals(1, pip.list().size());
    }

    @Test
    void installNormalisesNameToLowerCase() {
        pip.install("Requests==2.32.0");
        List<PipManager.PackageEntry> list = pip.list();
        assertTrue(list.stream().anyMatch(e -> e.name().equals("requests")),
                "Package name should be lowercase");
    }

    @Test
    void installDuplicateReturnsAlreadyInstalledMessage() {
        pip.install("requests");
        String result = pip.install("requests");
        assertTrue(result.toLowerCase().contains("already installed"),
                "Expected 'already installed' message, got: " + result);
        assertEquals(1, pip.list().size()); // still only one entry
    }

    @Test
    void installWithoutVersionRecordsLatest() {
        pip.install("flask");
        PipManager.PackageEntry entry = pip.list().stream()
                .filter(e -> e.name().equals("flask"))
                .findFirst().orElse(null);
        assertNotNull(entry);
        assertEquals("latest", entry.version());
    }

    @Test
    void installWithVersionRecordsVersion() {
        pip.install("flask==3.0.0");
        PipManager.PackageEntry entry = pip.list().stream()
                .filter(e -> e.name().equals("flask"))
                .findFirst().orElse(null);
        assertNotNull(entry);
        assertEquals("3.0.0", entry.version());
    }

    @Test
    void installEmptySpecReturnsError() {
        String result = pip.install("   ");
        assertTrue(result.toLowerCase().contains("empty") || result.toLowerCase().contains("not"),
                "Expected error for blank spec, got: " + result);
    }

    // -------------------------------------------------------------------------
    // uninstall
    // -------------------------------------------------------------------------

    @Test
    void uninstallRemovesPackage() {
        pip.install("requests");
        String result = pip.uninstall("requests");
        assertTrue(result.contains("Uninstalled"), "Expected uninstall confirmation, got: " + result);
        assertTrue(pip.list().isEmpty());
    }

    @Test
    void uninstallNonExistentReturnsNotInstalled() {
        String result = pip.uninstall("nonexistent_pkg");
        assertTrue(result.toLowerCase().contains("not installed"),
                "Expected 'not installed' message, got: " + result);
    }

    @Test
    void uninstallIsCaseInsensitive() {
        pip.install("requests");
        String result = pip.uninstall("REQUESTS");
        assertTrue(result.contains("Uninstalled"), "Expected uninstall confirmation, got: " + result);
        assertTrue(pip.list().isEmpty());
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    @Test
    void listEmptyInitially() {
        assertTrue(pip.list().isEmpty());
    }

    @Test
    void listAfterInstall() {
        pip.install("requests");
        pip.install("flask");
        assertEquals(2, pip.list().size());
    }

    @Test
    void listIsUnmodifiable() {
        List<PipManager.PackageEntry> list = pip.list();
        assertThrows(UnsupportedOperationException.class,
                () -> list.add(new PipManager.PackageEntry("evil", "1.0")));
    }

    // -------------------------------------------------------------------------
    // persistence
    // -------------------------------------------------------------------------

    @Test
    void packagesPersistedToDisk() {
        pip.install("requests==2.32.0");

        // Re-init from same dir — should reload from file
        PipManager pip2 = new PipManager();
        pip2.init(tempDir);
        List<PipManager.PackageEntry> list = pip2.list();
        assertFalse(list.isEmpty());
        assertEquals("requests", list.get(0).name());
        assertEquals("2.32.0",   list.get(0).version());
    }

    @Test
    void packagesFileCreatedInPyfishDir() {
        pip.install("requests");
        assertTrue(Files.exists(tempDir.resolve("pyfish").resolve(PipManager.PACKAGES_FILE)));
    }

    @Test
    void uninstallRemovedFromPersistedFile() {
        pip.install("requests");
        pip.install("flask");
        pip.uninstall("requests");

        PipManager pip2 = new PipManager();
        pip2.init(tempDir);
        assertEquals(1, pip2.list().size());
        assertEquals("flask", pip2.list().get(0).name());
    }

    // -------------------------------------------------------------------------
    // PackageEntry record
    // -------------------------------------------------------------------------

    @Test
    void packageEntryFreezeFormat() {
        PipManager.PackageEntry e = new PipManager.PackageEntry("requests", "2.32.0");
        assertEquals("requests==2.32.0", e.freezeFormat());
        assertEquals("requests==2.32.0", e.toString());
    }

    @Test
    void packageEntryNullThrows() {
        assertThrows(NullPointerException.class, () -> new PipManager.PackageEntry(null, "1.0"));
        assertThrows(NullPointerException.class, () -> new PipManager.PackageEntry("pkg", null));
    }

    // -------------------------------------------------------------------------
    // sitePackagesDir
    // -------------------------------------------------------------------------

    @Test
    void sitePackagesDirPath() {
        Path dir = pip.sitePackagesDir();
        assertTrue(dir.toString().endsWith(PipManager.SITE_PACKAGES_DIR));
    }

    @Test
    void sitePackagesDirThrowsIfNotInitialised() {
        PipManager uninitialised = new PipManager();
        assertThrows(IllegalStateException.class, uninitialised::sitePackagesDir);
    }
}
