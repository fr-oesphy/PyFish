package fr.oesphy.pyfish.script;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single Python script file with its metadata and state.
 */
public class ScriptFile {
    private final Path path;
    private final ScriptType type;
    private final String namespace;
    private String source;
    private final Map<String, String> headerDirectives;
    private ScriptError lastError;
    private long lastModified;
    private boolean loaded;

    public ScriptFile(Path path, ScriptType type, String namespace) {
        this.path = path;
        this.type = type;
        this.namespace = namespace;
        this.headerDirectives = new HashMap<>();
        this.lastModified = -1;
        this.loaded = false;
    }

    public Path getPath() {
        return path;
    }

    public ScriptType getType() {
        return type;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, String> getHeaderDirectives() {
        return headerDirectives;
    }

    public ScriptError getLastError() {
        return lastError;
    }

    public void setLastError(ScriptError lastError) {
        this.lastError = lastError;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    /**
     * Gets the full identifier for this script, including namespace.
     * Format: "namespace:filename" or "filename" for global scripts.
     */
    public String getFullName() {
        if (namespace == null || namespace.isEmpty()) {
            return path.getFileName().toString();
        }
        return namespace + ":" + path.getFileName().toString();
    }

    /**
     * Gets the relative path within the namespace, suitable for logging.
     */
    public String getRelativePath() {
        if (namespace == null || namespace.isEmpty()) {
            return path.toString();
        }
        // Return path relative to pyfish/namespace/
        String pathStr = path.toString();
        String namespacePrefix = "pyfish" + java.io.File.separator + namespace + java.io.File.separator;
        // Normalize for both Windows and Unix paths
        String normalizedPathStr = pathStr.replace('\\', '/');
        String normalizedPrefix = namespacePrefix.replace('\\', '/');
        if (normalizedPathStr.startsWith(normalizedPrefix)) {
            return normalizedPathStr.substring(normalizedPrefix.length());
        }
        return pathStr;
    }

    /**
     * Represents an error that occurred during script execution.
     */
    public static class ScriptError {
        private final String message;
        private final String traceback;
        private final int line;
        private final int column;
        private final long timestamp;

        public ScriptError(String message, String traceback, int line, int column) {
            this.message = message;
            this.traceback = traceback;
            this.line = line;
            this.column = column;
            this.timestamp = System.currentTimeMillis();
        }

        public String getMessage() {
            return message;
        }

        public String getTraceback() {
            return traceback;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ScriptError: ").append(message);
            if (line > 0) {
                sb.append(" at line ").append(line);
                if (column > 0) {
                    sb.append(":").append(column);
                }
            }
            if (traceback != null && !traceback.isEmpty()) {
                sb.append("\n").append(traceback);
            }
            return sb.toString();
        }
    }
}
