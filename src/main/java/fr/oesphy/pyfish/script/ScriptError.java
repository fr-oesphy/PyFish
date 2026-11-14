package fr.oesphy.pyfish.script;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;

import java.time.Instant;

/**
 * Immutable record capturing a Python script error with structured location
 * information and the original Python traceback.
 *
 * <p>Use {@link #fromException(String, Throwable)} to build a {@code ScriptError}
 * from a {@link PolyglotException} or any other exception.  When the exception is
 * a {@code PolyglotException} whose source location is available the line and
 * column are extracted from {@link SourceSection}; otherwise they default to
 * {@code -1}.
 *
 * @param scriptPath      path of the script file (relative to the script root, or
 *                        a synthetic label such as {@code "<eval>"}); never null
 * @param line            1-based line number, or {@code -1} when unknown
 * @param column          1-based column number, or {@code -1} when unknown
 * @param message         human-readable error message; never null
 * @param pythonTraceback the Python traceback string extracted from the
 *                        {@link PolyglotException}, or {@code ""} when unavailable
 * @param timestamp       when the error was recorded; never null
 * @param cause           the original Java exception; may be {@code null}
 */
public record ScriptError(
        String scriptPath,
        int line,
        int column,
        String message,
        String pythonTraceback,
        Instant timestamp,
        Throwable cause) {

    // -------------------------------------------------------------------------
    // Compact canonical constructor – validation
    // -------------------------------------------------------------------------

    public ScriptError {
        if (scriptPath == null) throw new NullPointerException("scriptPath");
        if (message    == null) throw new NullPointerException("message");
        if (timestamp  == null) throw new NullPointerException("timestamp");
        if (pythonTraceback == null) pythonTraceback = "";
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    /**
     * Builds a {@code ScriptError} from any exception thrown during script
     * execution.
     *
     * <p>When {@code t} is a {@link PolyglotException}:
     * <ul>
     *   <li>Line and column are taken from
     *       {@link PolyglotException#getSourceLocation()} if present.</li>
     *   <li>The Python traceback is taken from
     *       {@link PolyglotException#getMessage()} (GraalPy includes the full
     *       Python traceback in the message).</li>
     * </ul>
     *
     * @param scriptPath the script file path or synthetic label
     * @param t          the exception; must not be {@code null}
     * @return a fully populated {@code ScriptError}
     */
    public static ScriptError fromException(String scriptPath, Throwable t) {
        int line   = -1;
        int column = -1;
        String traceback = "";

        Throwable root = unwrap(t);

        if (root instanceof PolyglotException pe) {
            SourceSection src = pe.getSourceLocation();
            if (src != null && src.isAvailable()) {
                line   = src.getStartLine();
                column = src.getStartColumn();
            }
            // GraalPy puts the full Python traceback (Traceback + frames + Error) in getMessage()
            traceback = pe.getMessage() != null ? pe.getMessage() : "";
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = root.getClass().getSimpleName();
        }

        return new ScriptError(
                scriptPath != null ? scriptPath : "<unknown>",
                line,
                column,
                message,
                traceback,
                Instant.now(),
                t);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the error has a known source location (line ≥ 1).
     */
    public boolean hasLocation() {
        return line >= 1;
    }

    /**
     * Formats a compact single-line summary suitable for logs and chat.
     *
     * <p>Example: {@code "mymod:greeting [line 7, col 3]: name 'undefined_var' is not defined"}
     */
    public String summary() {
        StringBuilder sb = new StringBuilder(scriptPath);
        if (hasLocation()) {
            sb.append(" [line ").append(line);
            if (column >= 1) sb.append(", col ").append(column);
            sb.append(']');
        }
        sb.append(": ").append(message);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Unwraps RuntimeException wrappers to reach the root PolyglotException. */
    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        int depth = 0;
        while (current instanceof RuntimeException && current.getCause() != null && depth++ < 10) {
            current = current.getCause();
        }
        return current;
    }
}
