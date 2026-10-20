package fr.oesphy.pyfish.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScriptFileTest {

    @Test
    void testScriptFileCreation(@TempDir Path tempDir) {
        Path scriptPath = tempDir.resolve("test_script.py");
        ScriptFile script = new ScriptFile(scriptPath, ScriptType.SERVER, "test_namespace");

        assertEquals(scriptPath, script.getPath());
        assertEquals(ScriptType.SERVER, script.getType());
        assertEquals("test_namespace", script.getNamespace());
        assertFalse(script.isLoaded());
        assertNull(script.getLastError());
    }

    @Test
    void testGetFullName() {
        Path scriptPath = Path.of("test.py");
        ScriptFile script = new ScriptFile(scriptPath, ScriptType.SERVER, "mymod");
        assertEquals("mymod:test.py", script.getFullName());

        ScriptFile globalScript = new ScriptFile(scriptPath, ScriptType.SERVER, null);
        assertEquals("test.py", globalScript.getFullName());
    }

    @Test
    void testScriptError() {
        ScriptFile.ScriptError error = new ScriptFile.ScriptError(
                "Test error message",
                "Traceback:\n  at line 10",
                10,
                5
        );

        assertEquals("Test error message", error.getMessage());
        assertEquals("Traceback:\n  at line 10", error.getTraceback());
        assertEquals(10, error.getLine());
        assertEquals(5, error.getColumn());
        assertTrue(error.getTimestamp() > 0);

        String errorString = error.toString();
        assertTrue(errorString.contains("ScriptError"));
        assertTrue(errorString.contains("Test error message"));
        assertTrue(errorString.contains("line 10"));
    }

    @Test
    void testScriptErrorWithoutLocation() {
        ScriptFile.ScriptError error = new ScriptFile.ScriptError(
                "Simple error",
                null,
                -1,
                -1
        );

        assertEquals("Simple error", error.getMessage());
        assertNull(error.getTraceback());
        assertEquals(-1, error.getLine());
        assertEquals(-1, error.getColumn());

        String errorString = error.toString();
        assertTrue(errorString.contains("ScriptError"));
        assertTrue(errorString.contains("Simple error"));
        assertFalse(errorString.contains("line"));
    }
}
