package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A collection of {@link ScriptFile}s belonging to a single namespace.
 *
 * <p>One {@code ScriptPack} maps to one "source" of scripts — either a
 * namespace directory on disk (e.g. {@code pyfish/mymod/scripts/}) or a set of
 * resource-bundled scripts extracted from the mod JAR.
 *
 * <p>Use the static factories to create packs:
 * <ul>
 *   <li>{@link #discover(Path, String, ScriptType)} — walks a directory on disk
 *       and collects all {@code .py} files.</li>
 *   <li>{@link #fromCode(String, String, String, ScriptType)} — wraps a single
 *       in-memory source string (useful for resource-bundled scripts and tests).</li>
 * </ul>
 *
 * @see ScriptFile
 * @see ScriptManager
 */
public final class ScriptPack {

    private static final Logger LOGGER = PyFish.LOGGER;

    private final String          namespace;
    private final ScriptType      type;
    private final List<ScriptFile> files;

    // -------------------------------------------------------------------------
    // Construction (internal — use factories)
    // -------------------------------------------------------------------------

    private ScriptPack(String namespace, ScriptType type, List<ScriptFile> files) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.type      = Objects.requireNonNull(type,      "type");
        this.files     = Collections.unmodifiableList(new ArrayList<>(files));
    }

    // -------------------------------------------------------------------------
    // Static factories
    // -------------------------------------------------------------------------

    /**
     * Walks {@code scriptsRoot} recursively and builds a {@code ScriptPack} from
     * all {@code .py} files found.
     *
     * <p>Files are sorted lexicographically so that {@code init.py} (if present)
     * naturally sorts early. The relative path stored in each {@link ScriptFile}
     * is the path relative to {@code scriptsRoot}, with the {@code .py} suffix
     * stripped and directory separators normalised to {@code /}.
     *
     * <p>If {@code scriptsRoot} does not exist or is not a directory, an empty
     * pack is returned without logging an error.
     *
     * @param scriptsRoot the directory to walk
     * @param namespace   the namespace / mod-id for all scripts in the pack
     * @param type        which lifecycle phase the scripts belong to
     * @return a (possibly empty) {@code ScriptPack}
     */
    public static ScriptPack discover(Path scriptsRoot, String namespace, ScriptType type) {
        Objects.requireNonNull(scriptsRoot, "scriptsRoot");
        Objects.requireNonNull(namespace,   "namespace");
        Objects.requireNonNull(type,        "type");

        List<ScriptFile> files = new ArrayList<>();

        if (!Files.isDirectory(scriptsRoot)) {
            return new ScriptPack(namespace, type, files);
        }

        try (var stream = Files.walk(scriptsRoot)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".py"))
                  .sorted()
                  .forEach(p -> {
                      String relative = scriptsRoot.relativize(p)
                              .toString()
                              .replace('\\', '/')
                              .replaceAll("\\.py$", "");
                      try {
                          String source = Files.readString(p);
                          files.add(new ScriptFile(namespace, relative, source, type));
                      } catch (IOException e) {
                          LOGGER.error("[PyFish/ScriptPack] Cannot read script {}: {}", p, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            LOGGER.error("[PyFish/ScriptPack] Cannot walk scripts directory {}: {}", scriptsRoot, e.getMessage());
        }

        LOGGER.debug("[PyFish/ScriptPack] Discovered {} script(s) in {} (ns={}, type={})",
                files.size(), scriptsRoot, namespace, type);
        return new ScriptPack(namespace, type, files);
    }

    /**
     * Creates a {@code ScriptPack} from a single in-memory source string.
     *
     * <p>Useful for resource-bundled scripts loaded from the mod JAR's classpath
     * and for unit tests that supply Python source directly.
     *
     * @param namespace    the namespace / mod-id
     * @param relativePath path label (without {@code .py}), e.g. {@code "startup/init"}
     * @param source       the Python source text
     * @param type         which lifecycle phase the script belongs to
     * @return a {@code ScriptPack} containing exactly one {@link ScriptFile}
     */
    public static ScriptPack fromCode(String namespace, String relativePath,
                                       String source,    ScriptType type) {
        Objects.requireNonNull(namespace,    "namespace");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(source,       "source");
        Objects.requireNonNull(type,         "type");

        ScriptFile file = new ScriptFile(namespace, relativePath, source, type);
        return new ScriptPack(namespace, type, List.of(file));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The namespace / mod-id that owns this pack. */
    public String namespace() { return namespace; }

    /** The lifecycle phase all scripts in this pack participate in. */
    public ScriptType type()  { return type; }

    /** An unmodifiable, ordered list of {@link ScriptFile}s in this pack. */
    public List<ScriptFile> files() { return files; }

    /** Returns {@code true} if this pack contains no scripts. */
    public boolean isEmpty() { return files.isEmpty(); }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "ScriptPack{ns=" + namespace + ", type=" + type
                + ", files=" + files.size() + '}';
    }
}
