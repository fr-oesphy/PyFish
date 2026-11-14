package fr.oesphy.pyfish.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PyContextFactory}, {@link PyRuntime}, and {@link ScriptType}.
 *
 * <p>Tests that build an actual GraalPy context are guarded by
 * {@link Assumptions#assumeTrue} so they are silently skipped when the GraalPy VFS
 * resource files are absent from the test classpath (e.g. if the
 * {@code graalPyVFSFilesList} Gradle task has not been run, or in pure unit-test
 * environments).  The full context integration is exercised by the
 * {@code GraalPyRuntimeJarSmokeTest} main-class test.
 */
class PyContextFactoryTest {

    // -------------------------------------------------------------------------
    // ScriptType — pure-Java tests, always run
    // -------------------------------------------------------------------------

    /**
     * {@link ScriptType} must expose exactly the three values required by the spec.
     */
    @Test
    void scriptTypeHasThreeValues() {
        ScriptType[] values = ScriptType.values();
        assertEquals(3, values.length,
                "ScriptType must have exactly STARTUP, SERVER, CLIENT");
        assertSame(ScriptType.STARTUP, ScriptType.valueOf("STARTUP"));
        assertSame(ScriptType.SERVER,  ScriptType.valueOf("SERVER"));
        assertSame(ScriptType.CLIENT,  ScriptType.valueOf("CLIENT"));
    }

    @Test
    void scriptTypeOrdinals() {
        assertEquals(0, ScriptType.STARTUP.ordinal());
        assertEquals(1, ScriptType.SERVER.ordinal());
        assertEquals(2, ScriptType.CLIENT.ordinal());
    }

    // -------------------------------------------------------------------------
    // makeLoggingStream — pure-Java tests, always run
    // -------------------------------------------------------------------------

    @Test
    void loggingStreamFlushesOnNewline() throws Exception {
        // We can't observe SLF4J output in a plain JUnit test, but we can verify
        // that the stream accepts bytes and doesn't throw.
        OutputStream stream = PyContextFactory.makeLoggingStream(false);
        // Should not throw
        stream.write('H');
        stream.write('i');
        stream.write('\n');
        stream.write('\n'); // empty line — should be silently ignored
        stream.flush();
    }

    @Test
    void loggingStreamErrorPathDoesNotThrow() throws Exception {
        OutputStream stream = PyContextFactory.makeLoggingStream(true);
        stream.write("error line\n".getBytes(StandardCharsets.UTF_8));
        stream.flush();
    }

    // -------------------------------------------------------------------------
    // GraalPy context tests — skipped when VFS is unavailable
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the GraalPy VFS resource is on the classpath
     * <em>and</em> has been populated with entries.
     *
     * <p>The Gradle {@code graalPyVFSFilesList} task creates the file but only
     * writes entries when at least one Python resource is processed.  An empty
     * (0-byte) {@code fileslist.txt} still satisfies a plain resource-existence
     * check but causes an {@link AssertionError} inside GraalPy's
     * {@code VirtualFileSystemImpl.pathIsInVfs} when a context is built.  We
     * therefore also verify that the file is non-empty.
     */
    private static boolean isGraalPyVfsAvailable() {
        java.net.URL url = PyContextFactoryTest.class.getClassLoader()
                .getResource("org.graalvm.python.vfs/fileslist.txt");
        if (url == null) {
            return false;
        }
        try (java.io.InputStream is = url.openStream()) {
            return is.read() != -1; // non-empty → VFS has entries
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @Test
    void serverContextEvaluatesArithmetic() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not on test classpath — skipping context test");

        try (Context ctx = PyContextFactory.build(
                ScriptType.SERVER,
                PyContextFactory.makeLoggingStream(false),
                PyContextFactory.makeLoggingStream(true))) {
            Value result = ctx.eval("python", "1 + 1");
            assertEquals(2, result.asInt(), "1 + 1 should equal 2 in Python");
        }
    }

    @Test
    void startupContextEvaluatesString() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not on test classpath — skipping context test");

        try (Context ctx = PyContextFactory.build(
                ScriptType.STARTUP,
                PyContextFactory.makeLoggingStream(false),
                PyContextFactory.makeLoggingStream(true))) {
            Value result = ctx.eval("python", "'hello' + ' ' + 'pyfish'");
            assertEquals("hello pyfish", result.asString());
        }
    }

    @Test
    void convenienceOverloadProducesUsableContext() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not on test classpath — skipping context test");

        try (Context ctx = PyContextFactory.build(ScriptType.SERVER)) {
            Value result = ctx.eval("python", "2 ** 10");
            assertEquals(1024, result.asInt());
        }
    }

    @Test
    void pyRuntimeRunSafeExecutesWork() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not on test classpath — skipping context test");

        try (PyRuntime runtime = new PyRuntime(ScriptType.SERVER)) {
            int[] holder = {0};
            runtime.runSafe(() -> {
                Value v = runtime.getContext().eval("python", "7 * 6");
                holder[0] = v.asInt();
            });
            assertEquals(42, holder[0]);
        }
    }

    @Test
    void pyRuntimeRunSafeReturnsValue() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not on test classpath — skipping context test");

        try (PyRuntime runtime = new PyRuntime(ScriptType.SERVER)) {
            String result = runtime.runSafe(() ->
                    runtime.getContext().eval("python", "'PyFish'").asString());
            assertEquals("PyFish", result);
        }
    }

    @Test
    void pyRuntimeIsHeldByCurrentThread() {
        Assumptions.assumeTrue(isGraalPyVfsAvailable(),
                "GraalPy VFS not on test classpath — skipping context test");

        try (PyRuntime runtime = new PyRuntime(ScriptType.SERVER)) {
            assertFalse(runtime.isHeldByCurrentThread(),
                    "Lock should not be held before runSafe");
            runtime.runSafe(() -> assertTrue(runtime.isHeldByCurrentThread(),
                    "Lock should be held inside runSafe"));
            assertFalse(runtime.isHeldByCurrentThread(),
                    "Lock should be released after runSafe");
        }
    }
}
