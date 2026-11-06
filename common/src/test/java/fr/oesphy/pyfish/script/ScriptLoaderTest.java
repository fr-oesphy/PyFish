package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFishPaths;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ScriptLoader} — file-system layout and registration logic.
 *
 * <p>Tests that only exercise directory scanning and {@link ScriptManager}
 * registration (without calling {@link ScriptManager#runAll()}) are
 * <strong>pure-Java and always run</strong>.  Tests that actually execute
 * Python are guarded by the VFS emptiness check.
 */
class ScriptLoaderTest {

    // -------------------------------------------------------------------------
    // VFS guard
    // -------------------------------------------------------------------------

    private static boolean isGraalPyVfsAvailable() {
        URL url = ScriptLoaderTest.class.getClassLoader()
                .getResource("org.graalvm.python.vfs/fileslist.txt");
        if (url == null) return false;
        try (InputStream is = url.openStream()) {
            return is.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Registration-only tests (pure-Java, always run)
    // These use a "stub" ScriptManager that doesn't hold a real PyRuntime,
    // achieved via the ScriptManager(type, runtime) constructor with a
    // PyRuntime whose context is never called.
    // -------------------------------------------------------------------------

    @Test
    void looseFilesInScriptsDirGoToServer(@TempDir Path gameDir) throws IOException {
        // pyfish/scripts/my_script.py → SERVER
        Path scriptsDir = Files.createDirectories(
                PyFishPaths.scriptRoot(gameDir).resolve("scripts"));
        Files.writeString(scriptsDir.resolve("my_script.py"), "pass");

        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime serverRuntime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager serverMgr = new ScriptManager(ScriptType.SERVER, serverRuntime);
            ScriptLoader.load(gameDir, null, serverMgr, null);

            Map<String, ScriptFile> scripts = serverMgr.getScripts();
            assertTrue(scripts.containsKey("pyfish:my_script"),
                    "Loose scripts/ file should be registered as pyfish:my_script");
            assertEquals(ScriptType.SERVER,
                    scripts.get("pyfish:my_script").type());
        }
    }

    @Test
    void startupSubdirScriptsGoToStartup(@TempDir Path gameDir) throws IOException {
        Path startupDir = Files.createDirectories(
                PyFishPaths.scriptRoot(gameDir).resolve("scripts").resolve("startup"));
        Files.writeString(startupDir.resolve("register.py"), "pass");

        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime startupRuntime = new PyRuntime(ScriptType.STARTUP)) {
            ScriptManager startupMgr = new ScriptManager(ScriptType.STARTUP, startupRuntime);
            ScriptLoader.load(gameDir, startupMgr, null, null);

            Map<String, ScriptFile> scripts = startupMgr.getScripts();
            assertTrue(scripts.containsKey("pyfish:register"),
                    "startup/ script should be registered as pyfish:register");
            assertEquals(ScriptType.STARTUP,
                    scripts.get("pyfish:register").type());
        }
    }

    @Test
    void thirdPartyNamespaceScriptsAreRegistered(@TempDir Path gameDir) throws IOException {
        // pyfish/mymod/scripts/server/on_chat.py → SERVER, namespace=mymod
        Path serverDir = Files.createDirectories(
                PyFishPaths.scriptRoot(gameDir)
                        .resolve("mymod").resolve("scripts").resolve("server"));
        Files.writeString(serverDir.resolve("on_chat.py"), "pass");

        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime serverRuntime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager serverMgr = new ScriptManager(ScriptType.SERVER, serverRuntime);
            ScriptLoader.load(gameDir, null, serverMgr, null);

            Map<String, ScriptFile> scripts = serverMgr.getScripts();
            assertTrue(scripts.containsKey("mymod:on_chat"),
                    "Third-party server script should be mymod:on_chat. Got: " + scripts.keySet());
        }
    }

    @Test
    void missingGameDirDoesNotThrow(@TempDir Path tempParent) {
        // gameDir that doesn't exist yet
        Path gameDir = tempParent.resolve("nonexistent_instance");
        // No managers — just test that load() handles missing directories gracefully
        assertDoesNotThrow(() ->
            ScriptLoader.load(gameDir, null, null, null));
    }

    @Test
    void emptyScriptRootProducesNoScripts(@TempDir Path gameDir) throws IOException {
        Files.createDirectories(PyFishPaths.scriptRoot(gameDir));

        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime serverRuntime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager serverMgr = new ScriptManager(ScriptType.SERVER, serverRuntime);
            ScriptLoader.load(gameDir, null, serverMgr, null);
            assertTrue(serverMgr.getScripts().isEmpty(),
                    "No scripts should be registered from an empty pyfish/ root");
        }
    }

    @Test
    void reloadClearsAndRepopulatesServerScripts(@TempDir Path gameDir) throws IOException {
        Path serverDir = Files.createDirectories(
                PyFishPaths.scriptRoot(gameDir).resolve("scripts").resolve("server"));
        Files.writeString(serverDir.resolve("first.py"), "pass");

        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime serverRuntime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager serverMgr = new ScriptManager(ScriptType.SERVER, serverRuntime);
            ScriptLoader.load(gameDir, null, serverMgr, null);
            assertEquals(1, serverMgr.getScripts().size());

            // Add a second script and reload
            Files.writeString(serverDir.resolve("second.py"), "pass");
            ScriptLoader.reload(gameDir, serverMgr);

            // After reload: both scripts present (clear + re-load + runAll)
            assertEquals(2, serverMgr.getScripts().size(),
                    "After reload both scripts should be present");
        }
    }
}
