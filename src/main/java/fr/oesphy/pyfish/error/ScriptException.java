package fr.oesphy.pyfish.error;

import fr.oesphy.pyfish.script.ScriptError;

/**
 * Thrown when a Python script fails and the failure must propagate to a Java
 * caller rather than being silently recorded.
 *
 * <p>The embedded {@link ScriptError} carries the structured error information
 * (file path, line, column, Python traceback) that was captured at the point of
 * failure. Use {@link ScriptError#fromException(String, Throwable)} to build one
 * from a {@link org.graalvm.polyglot.PolyglotException}.
 *
 * @see ScriptError
 */
public final class ScriptException extends RuntimeException {

    private final ScriptError scriptError;

    /**
     * Creates a {@code ScriptException} wrapping the given structured error.
     *
     * @param error the structured script error; must not be {@code null}
     */
    public ScriptException(ScriptError error) {
        super(error.message(), error.cause());
        this.scriptError = error;
    }

    /**
     * Creates a {@code ScriptException} wrapping the given structured error and
     * linking the original cause for stack-trace transparency.
     *
     * @param error the structured script error; must not be {@code null}
     * @param cause the original exception that triggered this error
     */
    public ScriptException(ScriptError error, Throwable cause) {
        super(error.message(), cause);
        this.scriptError = error;
    }

    /**
     * Returns the structured {@link ScriptError} that describes the failure.
     */
    public ScriptError getScriptError() {
        return scriptError;
    }
}
