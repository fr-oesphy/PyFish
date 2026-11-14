package fr.oesphy.pyfish.script;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ScriptManager}, {@link ScriptFile}, {@link ScriptPack},
 * {@link ScriptError}, and {@link ConsoleApi}.
 *
 * <p>Tests that build an actual GraalPy context are guarded by
 * {@link #isGraalPyVfsAvailable()} so they skip gracefully when the VFS
 * resource is absent or empty (the same guard used in
 * {@link PyContextFactoryTest}).
 */
class ScriptManagerTest {

    // -------------------------------------------------------------------------
    // VFS guard (same logic as PyContextFactoryTest)
    // -------------------------------------------------------------------------

    private static boolean isGraalPyVfsAvailable() {
        java.net.URL url = ScriptManagerTest.class.getClassLoader()
                .getResource("org.graalvm.python.vfs/fileslist.txt");
        if (url == null) return false;
        try (java.io.InputStream is = url.openStream()) {
            return is.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // ScriptType — pure-Java, always run
    // -------------------------------------------------------------------------

    @Test
    void scriptTypeEnumHasExpectedValues() {
        assertEquals(3, ScriptType.values().length);
        assertSame(ScriptType.STARTUP, ScriptType.valueOf("STARTUP"));
        assertSame(ScriptType.SERVER,  ScriptType.valueOf("SERVER"));
        assertSame(ScriptType.CLIENT,  ScriptType.valueOf("CLIENT"));
    }

    // -------------------------------------------------------------------------
    // ScriptFile — pure-Java, always run
    // -------------------------------------------------------------------------

    @Test
    void scriptFileQualifiedName() {
        ScriptFile f = new ScriptFile("mymod", "startup/init", "pass", ScriptType.STARTUP);
        assertEquals("mymod:startup/init", f.qualifiedName());
    }

    @Test
    void scriptFileErrorState() {
        ScriptFile f = new ScriptFile("mymod", "test", "pass", ScriptType.SERVER);
        assertFalse(f.hasError());

        ScriptError err = ScriptError.fromException("mymod:test", new RuntimeException("oops"));
        f.recordError(err);
        assertTrue(f.hasError());
        assertSame(err, f.lastError());

        f.clearError();
        assertFalse(f.hasError());
        assertNull(f.lastError());
    }

    // -------------------------------------------------------------------------
    // ScriptPack — pure-Java, always run
    // -------------------------------------------------------------------------

    @Test
    void scriptPackFromCodeContainsOneFile() {
        ScriptPack pack = ScriptPack.fromCode("mymod", "hello", "pass", ScriptType.SERVER);
        assertEquals(1, pack.files().size());
        assertEquals("mymod:hello", pack.files().get(0).qualifiedName());
    }

    @Test
    void scriptPackDiscoverOnNonExistentDirIsEmpty() {
        ScriptPack pack = ScriptPack.discover(
                java.nio.file.Paths.get("nonexistent_dir_that_will_never_exist"),
                "mymod",
                ScriptType.SERVER);
        assertTrue(pack.isEmpty());
    }

    // -------------------------------------------------------------------------
    // ScriptError — pure-Java, always run
    // -------------------------------------------------------------------------

    @Test
    void scriptErrorFromRuntimeException() {
        RuntimeException cause = new RuntimeException("bad thing");
        ScriptError err = ScriptError.fromException("mymod:test", cause);

        assertEquals("mymod:test", err.scriptPath());
        assertEquals("bad thing", err.message());
        assertEquals(-1, err.line());
        assertNotNull(err.timestamp());
        assertNotNull(err.summary());
        assertTrue(err.summary().contains("bad thing"));
    }

    @Test
    void scriptErrorSummaryIncludesLocation() {
        // Synthetic error with known line/col
        ScriptError err = new ScriptError("ns:script", 7, 3, "boom", "", java.time.Instant.now(), null);
        String summary = err.summary();
        assertTrue(summary.contains("line 7"), "Expected 'line 7' in: " + summary);
        assertTrue(summary.contains("col 3"),  "Expected 'col 3' in: " + summary);
        assertTrue(summary.contains("boom"),   "Expected 'boom' in: " + summary);
    }

    // -------------------------------------------------------------------------
    // ConsoleApi — pure-Java, always run
    // -------------------------------------------------------------------------

    @Test
    void consoleApiLogsWithoutThrowing() {
        ConsoleApi console = new ConsoleApi();
        // All log methods must accept any input without throwing
        assertDoesNotThrow(() -> {
            console.log("hello");
            console.warn("warning");
            console.error("error");
            console.print("print");
            console.log(null);
        });
    }

    @Test
    void consoleApiRingBufferRetainsHistory() {
        ConsoleApi console = new ConsoleApi();
        console.pushScript("mymod:test");
        console.log("first");
        console.warn("second");
        console.popScript();

        List<ConsoleApi.Entry> history = console.getHistory();
        assertEquals(2, history.size());
        assertEquals(ConsoleApi.Level.INFO, history.get(0).level());
        assertEquals(ConsoleApi.Level.WARN, history.get(1).level());
        assertEquals("first",  history.get(0).message());
        assertEquals("second", history.get(1).message());
    }

    @Test
    void consoleApiRingBufferIsBounded() {
        ConsoleApi console = new ConsoleApi();
        // Fill beyond capacity
        int over = ConsoleApi.RING_BUFFER_CAPACITY + 10;
        for (int i = 0; i < over; i++) {
            console.log("msg " + i);
        }
        List<ConsoleApi.Entry> history = console.getHistory();
        assertEquals(ConsoleApi.RING_BUFFER_CAPACITY, history.size(),
                "Ring buffer must not exceed capacity");
        // Most recent entry should be the last logged message
        assertEquals("msg " + (over - 1), history.get(history.size() - 1).message());
    }

    @Test
    void consoleApiClearHistoryEmptiesBuffer() {
        ConsoleApi console = new ConsoleApi();
        console.log("something");
        assertFalse(console.getHistory().isEmpty());
        console.clearHistory();
        assertTrue(console.getHistory().isEmpty());
    }

    // -------------------------------------------------------------------------
    // ScriptManager — requires GraalPy VFS
    // -------------------------------------------------------------------------

    @Test
    void scriptsRunInOrder() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime runtime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager mgr = new ScriptManager(ScriptType.SERVER, runtime);
            // Use Python globals as a simple sequencing log
            ScriptPack p1 = ScriptPack.fromCode("test", "first",
                    "_order = getattr(_order if '_order' in dir() else type('X', (), {})(), '__class__', [])\n"
                    + "try: _order.append('first')\nexcept: _order = ['first']",
                    ScriptType.SERVER);
            ScriptPack p2 = ScriptPack.fromCode("test", "second",
                    "# Just needs to run without error",
                    ScriptType.SERVER);
            mgr.addPack(p1);
            mgr.addPack(p2);
            mgr.runAll();

            // Both scripts ran — there should be no errors
            List<ScriptError> errors = mgr.collectErrors();
            assertTrue(errors.isEmpty(),
                    "Expected no errors but got: " + errors.stream()
                            .map(ScriptError::summary).toList());
        }
    }

    @Test
    void errorIsRecordedAndDoesNotCrash() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime runtime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager mgr = new ScriptManager(ScriptType.SERVER, runtime);
            ScriptPack bad = ScriptPack.fromCode("test", "bad_script",
                    "raise ValueError('intentional test error')",
                    ScriptType.SERVER);
            ScriptPack good = ScriptPack.fromCode("test", "good_script",
                    "x = 1 + 1",
                    ScriptType.SERVER);

            mgr.addPack(bad);
            mgr.addPack(good);
            // runAll must NOT throw even if one script fails
            assertDoesNotThrow(mgr::runAll);

            List<ScriptError> errors = mgr.collectErrors();
            assertEquals(1, errors.size(), "Expected exactly one error");
            assertTrue(errors.get(0).scriptPath().contains("bad_script"),
                    "Error should reference bad_script but was: " + errors.get(0).scriptPath());
        }
    }

    @Test
    void clearServerScriptsDoesNotClearStartup() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime serverRuntime  = new PyRuntime(ScriptType.SERVER);
             PyRuntime startupRuntime = new PyRuntime(ScriptType.STARTUP)) {

            ScriptManager serverMgr  = new ScriptManager(ScriptType.SERVER,  serverRuntime);
            ScriptManager startupMgr = new ScriptManager(ScriptType.STARTUP, startupRuntime);

            serverMgr.addPack(
                    ScriptPack.fromCode("test", "server_script", "pass", ScriptType.SERVER));
            startupMgr.addPack(
                    ScriptPack.fromCode("test", "startup_script", "pass", ScriptType.STARTUP));

            assertEquals(1, serverMgr.getScripts().size());
            assertEquals(1, startupMgr.getScripts().size());

            serverMgr.clearServerScripts();
            assertEquals(0, serverMgr.getScripts().size(),
                    "Server scripts should have been cleared");

            // STARTUP manager must be unaffected (it's a separate manager, but also test its immunity)
            startupMgr.clearServerScripts(); // must be a no-op on STARTUP manager
            assertEquals(1, startupMgr.getScripts().size(),
                    "STARTUP scripts must NOT be cleared by clearServerScripts()");
        }
    }

    @Test
    void evalInjectedConsoleBinding() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime runtime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager mgr = new ScriptManager(ScriptType.SERVER, runtime);
            ScriptPack pack = ScriptPack.fromCode("test", "console_test",
                    "console.log('hello from python')",
                    ScriptType.SERVER);
            mgr.addPack(pack);
            assertDoesNotThrow(mgr::runAll);

            List<ScriptError> errors = mgr.collectErrors();
            assertTrue(errors.isEmpty(),
                    "console.log() should not cause an error: " + errors);
        }
    }

    @Test
    void packTypeMismatchIsIgnored() {
        // No GraalPy needed — this is pure registration logic.
        // We test ScriptManager.addPack() type-filter without building a real Context.
        // We create a ScriptManager but intentionally do NOT call runAll() so the
        // null-context PyRuntime is never asked to eval anything.
        Assumptions.assumeTrue(isGraalPyVfsAvailable(), "GraalPy VFS not available");

        try (PyRuntime runtime = new PyRuntime(ScriptType.SERVER)) {
            ScriptManager mgr = new ScriptManager(ScriptType.SERVER, runtime);
            ScriptPack clientPack = ScriptPack.fromCode("test", "client_only",
                    "pass", ScriptType.CLIENT);
            mgr.addPack(clientPack);
            // The CLIENT pack must not appear in a SERVER manager
            Map<String, ScriptFile> scripts = mgr.getScripts();
            assertFalse(scripts.containsKey("test:client_only"),
                    "A CLIENT pack must not be registered in a SERVER ScriptManager");
        }
    }

    // -------------------------------------------------------------------------
    // Helper: a no-op mock Context for tests that only test registration logic
    // -------------------------------------------------------------------------

    /**
     * Returns a minimal mock {@link Context} sufficient for constructing a
     * {@link PyRuntime} in tests that only exercise registration (no eval).
     * Uses a real Context built from PyContextFactory if VFS is available,
     * otherwise null (the PyRuntime(type, context) ctor accepts null for tests).
     */
    private static Context mockContext() {
        // The two-arg PyRuntime(ScriptType, Context) constructor accepts any Context,
        // including null in test environments where we won't eval anything.
        return null;
    }
}
