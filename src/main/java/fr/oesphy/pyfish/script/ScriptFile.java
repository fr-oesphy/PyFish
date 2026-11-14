package fr.oesphy.pyfish.script;

import java.util.Objects;

/**
 * Represents a single Python {@code .py} file managed by PyFish.
 *
 * <p>A {@code ScriptFile} is immutable with respect to its identity fields
 * ({@link #namespace}, {@link #relativePath}, {@link #source}, {@link #type})
 * but mutable with respect to error state: exactly one error may be recorded
 * via {@link #recordError(ScriptError)} and cleared via {@link #clearError()}.
 *
 * <p>The {@link #qualifiedName()} is the canonical identifier used in logs, the
 * {@code /pyfish errors} output, and the error ring-buffer in
 * {@link ConsoleApi}: it has the form {@code namespace:relative/path} (without
 * the {@code .py} suffix).
 *
 * @see ScriptPack
 * @see ScriptManager
 */
public final class ScriptFile {

    private final String    namespace;
    private final String    relativePath;
    private final String    source;
    private final ScriptType type;

    /** The last recorded error; {@code null} if the script ran without error. */
    private volatile ScriptError lastError;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * @param namespace    mod / pack namespace (e.g. {@code "pyfish"} or {@code "mymod"})
     * @param relativePath path relative to the scripts root, without leading slash
     *                     and without the {@code .py} suffix
     *                     (e.g. {@code "startup/register_items"})
     * @param source       the full Python source text
     * @param type         which lifecycle phase this script belongs to
     */
    public ScriptFile(String namespace, String relativePath, String source, ScriptType type) {
        this.namespace    = Objects.requireNonNull(namespace,    "namespace");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.source       = Objects.requireNonNull(source,       "source");
        this.type         = Objects.requireNonNull(type,         "type");
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    /** The namespace / mod-id that owns this script. */
    public String namespace()    { return namespace; }

    /** The path relative to the scripts root, without {@code .py}. */
    public String relativePath() { return relativePath; }

    /** The full Python source text. */
    public String source()       { return source; }

    /** The lifecycle phase this script participates in. */
    public ScriptType type()     { return type; }

    /**
     * The canonical identifier for this script: {@code "<namespace>:<relativePath>"}.
     *
     * <p>Examples: {@code "pyfish:startup/register_items"},
     * {@code "mymod:on_chat"}.
     */
    public String qualifiedName() {
        return namespace + ":" + relativePath;
    }

    // -------------------------------------------------------------------------
    // Error state
    // -------------------------------------------------------------------------

    /**
     * Records an error that occurred during the last execution of this script.
     * Replaces any previously recorded error.
     *
     * @param error the error to record; must not be {@code null}
     */
    public void recordError(ScriptError error) {
        this.lastError = Objects.requireNonNull(error, "error");
    }

    /** Clears the last recorded error (call before re-running the script). */
    public void clearError() {
        this.lastError = null;
    }

    /** Returns {@code true} if the last execution recorded an error. */
    public boolean hasError() {
        return lastError != null;
    }

    /**
     * Returns the last recorded {@link ScriptError}, or {@code null} if the
     * script has never been run or ran successfully.
     */
    public ScriptError lastError() {
        return lastError;
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "ScriptFile{" + qualifiedName() + ", type=" + type + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScriptFile sf)) return false;
        return namespace.equals(sf.namespace)
                && relativePath.equals(sf.relativePath)
                && type == sf.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, relativePath, type);
    }
}
