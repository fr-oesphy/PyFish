package fr.oesphy.pyfish.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonDependencyCacheTest {
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path directory;

    @Test
    void conflictingDeclarationsForOneModCannotOverwriteEachOther() {
        var manager = new PythonDependencyManager(directory);
        manager.installDependencies("test", "sample==1\n");
        assertThrows(IllegalStateException.class, () -> manager.installDependencies("test", "sample==2\n"));
        assertThrows(IllegalStateException.class, () -> manager.prepareDependencies(null));
    }

    @Test
    void addingDependenciesAfterStartupRequiresRestart() {
        var manager = new PythonDependencyManager(directory);
        manager.prepareDependencies(null);
        assertThrows(IllegalStateException.class, () -> manager.installDependencies("new-mod", "sample==1"));
    }

    @Test
    void equivalentLineEndingsDoNotCreateConflicts() {
        var manager = new PythonDependencyManager(directory);
        manager.installDependencies("test", "sample==1\r\n");
        manager.installDependencies("test", "sample==1\n");
    }
    @Test
    void matchingRuntimeAndRequirementsReuseTheCache() throws Exception {
        assertEquals(hash("sample==1", "25.3.4.1", "windows/amd64"),
                hash("sample==1", "25.3.4.1", "windows/amd64"));
    }

    @Test
    void interpreterAndPlatformChangesInvalidateTheCache() throws Exception {
        String current = hash("sample==1", "25.3.4.1", "windows/amd64");
        assertNotEquals(current, hash("sample==1", "25.0.1", "windows/amd64"));
        assertNotEquals(current, hash("sample==1", "25.3.4.1", "linux/amd64"));
        assertNotEquals(current, hash("sample==1", "25.3.4.1", "windows/aarch64"));
    }

    @Test
    void requirementsChangesInvalidateTheCache() throws Exception {
        assertNotEquals(hash("sample==1", "25.3.4.1", "windows/amd64"),
                hash("sample==2", "25.3.4.1", "windows/amd64"));
    }

    private String hash(String content, String version, String platform) throws Exception {
        return PythonDependencyManager.requirementsHash(content, version, platform);
    }
}
