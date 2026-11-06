package fr.oesphy.pyfish;

import fr.oesphy.pyfish.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PyFishPaths} — pure-Java, always run (no GraalPy required).
 *
 * <p>All tests use JUnit's {@code @TempDir} to create a real temporary
 * directory structure, so they verify actual filesystem behaviour without
 * touching the production game directory.
 */
class PyFishPathsTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    @Test
    void scriptRootNameIsCorrect() {
        assertEquals("pyfish", PyFishPaths.SCRIPT_ROOT_NAME);
    }

    @Test
    void pyfishNamespaceIsCorrect() {
        assertEquals("pyfish", PyFishPaths.PYFISH_NAMESPACE);
    }

    @Test
    void subfolderMappingsAreConsistent() {
        assertEquals("startup", PyFishPaths.subfolderFor(ScriptType.STARTUP));
        assertEquals("server",  PyFishPaths.subfolderFor(ScriptType.SERVER));
        assertEquals("client",  PyFishPaths.subfolderFor(ScriptType.CLIENT));
    }

    // -------------------------------------------------------------------------
    // typeForSubfolder
    // -------------------------------------------------------------------------

    @Test
    void typeForSubfolderMapsKnownNames() {
        assertSame(ScriptType.STARTUP, PyFishPaths.typeForSubfolder("startup"));
        assertSame(ScriptType.SERVER,  PyFishPaths.typeForSubfolder("server"));
        assertSame(ScriptType.CLIENT,  PyFishPaths.typeForSubfolder("client"));
    }

    @Test
    void typeForSubfolderIsCaseInsensitive() {
        assertSame(ScriptType.STARTUP, PyFishPaths.typeForSubfolder("STARTUP"));
        assertSame(ScriptType.SERVER,  PyFishPaths.typeForSubfolder("Server"));
    }

    @Test
    void typeForSubfolderReturnsNullForUnknown() {
        assertNull(PyFishPaths.typeForSubfolder("assets"));
        assertNull(PyFishPaths.typeForSubfolder(""));
        assertNull(PyFishPaths.typeForSubfolder(null));
    }

    @Test
    void subfolderRoundTrip() {
        // typeForSubfolder(subfolderFor(T)) == T for all ScriptType values
        for (ScriptType type : ScriptType.values()) {
            String folder = PyFishPaths.subfolderFor(type);
            assertSame(type, PyFishPaths.typeForSubfolder(folder),
                    "Round-trip failed for " + type);
        }
    }

    // -------------------------------------------------------------------------
    // sanitiseNamespace
    // -------------------------------------------------------------------------

    @Test
    void sanitiseNamespaceConvertsToLowercase() {
        assertEquals("mymod", PyFishPaths.sanitiseNamespace("MyMod"));
    }

    @Test
    void sanitiseNamespaceReplacesIllegalChars() {
        assertEquals("my_mod", PyFishPaths.sanitiseNamespace("my mod"));
        assertEquals("my_mod", PyFishPaths.sanitiseNamespace("my@mod"));
        assertEquals("my.mod", PyFishPaths.sanitiseNamespace("my.mod")); // dots OK
        assertEquals("my-mod", PyFishPaths.sanitiseNamespace("my-mod")); // hyphens OK
    }

    // -------------------------------------------------------------------------
    // discoverNamespaces — filesystem tests via @TempDir
    // -------------------------------------------------------------------------

    @Test
    void discoverNamespacesAlwaysIncludesBuiltIn(@TempDir Path gameDir) {
        // Even with an empty pyfish/ root, the built-in namespace is always present
        Path scriptRoot = PyFishPaths.scriptRoot(gameDir);
        // Don't create scriptRoot — verify it handles missing root gracefully
        List<PyFishPaths.NamespaceRoot> ns = PyFishPaths.discoverNamespaces(scriptRoot, null);

        assertEquals(1, ns.size(), "Should always have the pyfish namespace");
        assertEquals(PyFishPaths.PYFISH_NAMESPACE, ns.get(0).namespace());
    }

    @Test
    void discoverNamespacesFindsThirdPartyWithScriptsSubdir(@TempDir Path gameDir)
            throws IOException {
        Path scriptRoot = Files.createDirectories(PyFishPaths.scriptRoot(gameDir));

        // Create mymod/scripts/
        Files.createDirectories(scriptRoot.resolve("mymod").resolve("scripts"));

        List<PyFishPaths.NamespaceRoot> ns = PyFishPaths.discoverNamespaces(scriptRoot, null);

        assertEquals(2, ns.size());
        assertEquals(PyFishPaths.PYFISH_NAMESPACE, ns.get(0).namespace()); // built-in first
        assertEquals("mymod", ns.get(1).namespace());
    }

    @Test
    void discoverNamespacesSkipsDirectoriesWithoutScriptsSubdir(@TempDir Path gameDir)
            throws IOException {
        Path scriptRoot = Files.createDirectories(PyFishPaths.scriptRoot(gameDir));

        // Create a dir without scripts/ — should NOT be treated as a namespace
        Files.createDirectories(scriptRoot.resolve("assets"));

        List<PyFishPaths.NamespaceRoot> ns = PyFishPaths.discoverNamespaces(scriptRoot, null);

        // Only the built-in pyfish namespace
        assertEquals(1, ns.size());
        assertEquals(PyFishPaths.PYFISH_NAMESPACE, ns.get(0).namespace());
    }

    @Test
    void discoverNamespacesSkipsDuplicatePyfishNamespace(@TempDir Path gameDir)
            throws IOException {
        Path scriptRoot = Files.createDirectories(PyFishPaths.scriptRoot(gameDir));

        // Create pyfish/scripts/ inside the script root — this is the built-in
        // path itself, NOT a third-party namespace named "pyfish"
        Files.createDirectories(scriptRoot.resolve("scripts"));

        List<PyFishPaths.NamespaceRoot> ns = PyFishPaths.discoverNamespaces(scriptRoot, null);

        // Should still be exactly 1 (built-in) — the scripts/ dir is the built-in dir,
        // not a third-party namespace
        long pyfishCount = ns.stream()
                .filter(n -> n.namespace().equals(PyFishPaths.PYFISH_NAMESPACE))
                .count();
        assertEquals(1, pyfishCount, "pyfish namespace must appear exactly once");
    }

    @Test
    void discoverNamespacesReturnsAlphabeticOrderForThirdParty(@TempDir Path gameDir)
            throws IOException {
        Path scriptRoot = Files.createDirectories(PyFishPaths.scriptRoot(gameDir));
        Files.createDirectories(scriptRoot.resolve("zebra").resolve("scripts"));
        Files.createDirectories(scriptRoot.resolve("alpha").resolve("scripts"));
        Files.createDirectories(scriptRoot.resolve("mango").resolve("scripts"));

        List<PyFishPaths.NamespaceRoot> ns = PyFishPaths.discoverNamespaces(scriptRoot, null);

        // Built-in first, then alphabetical
        assertEquals(4, ns.size());
        assertEquals("pyfish", ns.get(0).namespace());
        assertEquals("alpha",  ns.get(1).namespace());
        assertEquals("mango",  ns.get(2).namespace());
        assertEquals("zebra",  ns.get(3).namespace());
    }

    @Test
    void namespaceRootExistsReturnsFalseForMissingDir(@TempDir Path gameDir) {
        Path scriptRoot = PyFishPaths.scriptRoot(gameDir); // not created
        PyFishPaths.NamespaceRoot ns =
                new PyFishPaths.NamespaceRoot("mymod", scriptRoot.resolve("mymod").resolve("scripts"));
        assertFalse(ns.exists());
    }

    @Test
    void namespaceRootExistsReturnsTrueForPresentDir(@TempDir Path gameDir) throws IOException {
        Path scriptsDir = Files.createDirectories(
                PyFishPaths.scriptRoot(gameDir).resolve("mymod").resolve("scripts"));
        PyFishPaths.NamespaceRoot ns = new PyFishPaths.NamespaceRoot("mymod", scriptsDir);
        assertTrue(ns.exists());
    }

    // -------------------------------------------------------------------------
    // Path helpers
    // -------------------------------------------------------------------------

    @Test
    void scriptRootResolvesUnderGameDir(@TempDir Path gameDir) {
        Path root = PyFishPaths.scriptRoot(gameDir);
        assertEquals(gameDir.resolve("pyfish"), root);
    }

    @Test
    void typedSubdirResolvesCorrectly(@TempDir Path tempDir) {
        Path scriptsDir = tempDir.resolve("scripts");
        Path startupDir = PyFishPaths.typedSubdir(scriptsDir, ScriptType.STARTUP);
        assertEquals(scriptsDir.resolve("startup"), startupDir);
    }
}
